package com.transcribecare.app.service

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Singleton wrapper around the LiteRT-LM [Engine] and [Conversation].
 *
 * Manages the full lifecycle of the on-device Gemma 4 E2B model:
 * initialization, message inference, conversation management, and resource cleanup.
 * Uses double-checked locking to ensure a single instance across the application.
 *
 * @param context Application context for model path and cache directory access.
 */
class GemmaEngineWrapper private constructor(
    private val context: Context
) {

    /**
     * Represents the initialization state of the engine.
     */
    sealed class InitState {
        /** Engine has not been initialized yet. */
        object Uninitialized : InitState()

        /** Engine initialization is in progress. */
        object Initializing : InitState()

        /** Engine is ready for inference. */
        object Ready : InitState()

        /** Engine initialization failed with the given [reason]. */
        data class Failed(val reason: String) : InitState()
    }

    private val _initState = MutableStateFlow<InitState>(InitState.Uninitialized)

    /** Observable state of the engine initialization lifecycle. */
    val initState: StateFlow<InitState> = _initState.asStateFlow()

    private var engine: Engine? = null
    private var conversation: Conversation? = null

    /**
     * Initializes the LiteRT-LM engine with the assembled model file.
     *
     * Performs model loading via [ModelFileLoader], creates an [EngineConfig] with
     * [Backend.CPU] and the app's cache directory, initializes the engine, and
     * creates an initial [Conversation] instance.
     *
     * This method is safe to call from any coroutine context as it switches
     * to [Dispatchers.IO] internally.
     *
     * @return [Result.success] if initialization completed, or [Result.failure]
     *         with the underlying exception if any step failed.
     */
    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        _initState.value = InitState.Initializing
        try {
            // Assemble model file from split assets
            val modelFile = ModelFileLoader.loadModel(context)
            val modelPath = modelFile.absolutePath

            // Create engine configuration
            val config = EngineConfig(
                modelPath = modelPath,
                backend = Backend.CPU(),
                cacheDir = context.cacheDir.absolutePath
            )

            // Initialize the engine
            val newEngine = Engine(config)
            newEngine.initialize()
            engine = newEngine

            // Create initial conversation
            conversation = newEngine.createConversation()

            _initState.value = InitState.Ready
            Log.d(TAG, "Engine initialized successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            val reason = e.message ?: "Unknown initialization error"
            _initState.value = InitState.Failed(reason)
            Log.e(TAG, "Engine initialization failed: $reason", e)
            Result.failure(e)
        }
    }

    /**
     * Sends a transcription prompt to the active [Conversation] and returns the response.
     *
     * This method is safe to call from any coroutine context as it switches
     * to [Dispatchers.IO] internally.
     *
     * @param prompt The text prompt to send to the model.
     * @return [Result.success] with the model's response string, or [Result.failure]
     *         if the conversation is unavailable or inference throws an exception.
     */
    suspend fun sendMessage(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val activeConversation = conversation
                ?: return@withContext Result.failure(
                    IllegalStateException("No active conversation. Engine may not be initialized.")
                )
            val response = activeConversation.sendMessage(prompt)
            Result.success(response.toString())
        } catch (e: Exception) {
            Log.e(TAG, "sendMessage failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Closes the current [Conversation] and creates a new one from the engine.
     *
     * Used for conversation recovery when the existing conversation becomes invalid.
     *
     * @return [Result.success] if a new conversation was created, or [Result.failure]
     *         if the engine is unavailable or conversation creation fails.
     */
    fun createNewConversation(): Result<Unit> {
        return try {
            val activeEngine = engine
                ?: return Result.failure(
                    IllegalStateException("Engine is not initialized.")
                )
            // Close existing conversation
            conversation?.close()
            conversation = null

            // Create a new conversation
            conversation = activeEngine.createConversation()
            Log.d(TAG, "New conversation created successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create new conversation: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Releases all engine resources: closes the conversation, closes the engine,
     * and nulls out the singleton instance.
     *
     * After calling this method, [getInstance] will create a fresh instance.
     */
    fun release() {
        try {
            conversation?.close()
            conversation = null
            engine?.close()
            engine = null
            _initState.value = InitState.Uninitialized
            Log.d(TAG, "Engine resources released")
        } catch (e: Exception) {
            Log.e(TAG, "Error during release: ${e.message}", e)
        }
        synchronized(lock) {
            instance = null
        }
    }

    companion object {
        private const val TAG = "GemmaEngineWrapper"
        private val lock = Any()

        @Volatile
        private var instance: GemmaEngineWrapper? = null

        /**
         * Returns the singleton [GemmaEngineWrapper] instance, creating it if necessary.
         *
         * Uses double-checked locking for thread-safe lazy initialization.
         *
         * @param context Context used for model path and cache directory access.
         *                The application context is extracted to avoid leaking activities.
         * @return The singleton [GemmaEngineWrapper] instance.
         */
        fun getInstance(context: Context): GemmaEngineWrapper {
            return instance ?: synchronized(lock) {
                instance ?: GemmaEngineWrapper(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
