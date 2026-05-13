package com.transcribecare.app.service

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Singleton wrapper around the LiteRT-LM [Engine].
 *
 * Manages the full lifecycle of the on-device Gemma 4 E2B model:
 * initialization, message inference, and resource cleanup.
 * Uses double-checked locking to ensure a single instance across the application.
 *
 * Each inference call creates a short-lived conversation seeded with the preceding
 * [MAX_HISTORY_SIZE] prompt/response exchanges. This gives the model awareness of
 * recent transcription context (improving coherence across chunks) while bounding
 * context window usage to prevent overflow during long recording sessions.
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
    private val mutex = Mutex()

    /**
     * Rolling history of recent transcription results for conversation context.
     *
     * Stores the model's response text from the last [MAX_HISTORY_SIZE] inference
     * calls. This text is prepended to the current prompt so the model has
     * awareness of recent transcription without replaying full audio prompts.
     */
    private val conversationHistory = ArrayDeque<String>()

    companion object {
        private const val TAG = "GemmaEngineWrapper"
        private const val MAX_HISTORY_SIZE = 2
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

    /**
     * Initializes the LiteRT-LM engine with the assembled model file.
     *
     * Performs model loading via [ModelFileLoader], creates an [EngineConfig] with
     * [Backend.GPU] (falling back to CPU) and the app's cache directory,
     * initializes the engine, and creates an initial [Conversation] instance.
     *
     * This method is safe to call from any coroutine context as it switches
     * to [Dispatchers.IO] internally.
     *
     * @return [Result.success] if initialization completed, or [Result.failure]
     *         with the underlying exception if any step failed.
     */
    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val currentState = _initState.value
            if (currentState is InitState.Ready || currentState is InitState.Initializing) {
                return@withLock Result.success(Unit)
            }

            _initState.value = InitState.Initializing
            try {
                // Assemble model file from split assets
                val modelFile = ModelFileLoader.loadModel(context)
                val modelPath = modelFile.absolutePath

                // Create engine configuration.
                // For backend:
                // Using GPU as the main backend can be more stable and faster for multimodal models.
                // LiteRT-LM will fall back to CPU if GPU is unavailable or unsupported for specific ops.
                // Explicitly setting audioBackend to CPU can help stabilize audio preprocessing.
                //
                // For audioBackend:
                // Tried NPU. Saw it wasn't allowed. Tried GPU. "Failed to create engine:
                // INVALID_ARGUMENT: Audio backend constraint mismatch. Model requires one
                // of [cpu] but Audio backend is GPU
                val config = EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.GPU(),
                    audioBackend = Backend.CPU(),
                    cacheDir = context.cacheDir.absolutePath
                )

                // Initialize the engine
                val newEngine = Engine(config)
                newEngine.initialize()
                
                // Verify the engine can create conversations by doing a test creation
                val testConversation = newEngine.createConversation()
                testConversation.close()
                
                engine = newEngine

                _initState.value = InitState.Ready
                Log.d(TAG, "Engine initialized successfully with GPU backend and CPU audio")
                Result.success(Unit)
            } catch (e: Exception) {
                val reason = e.message ?: "Unknown initialization error"
                _initState.value = InitState.Failed(reason)
                Log.e(TAG, "Engine initialization failed: $reason", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Sends a transcription prompt with short conversation history as text context.
     *
     * Creates a fresh conversation and sends a single combined prompt that includes
     * the recent transcription history (as a text prefix) alongside the current audio.
     * This avoids multiple round-trips to the model while still providing short-term
     * context for improved coherence across chunks.
     *
     * The conversation is closed after use to prevent unbounded context growth
     * during long recording sessions.
     *
     * This method is safe to call from any coroutine context as it switches
     * to [Dispatchers.IO] internally.
     *
     * @param prompt The transcription prompt to send to the model.
     * @return [Result.success] with the model's response string, or [Result.failure]
     *         if the engine is unavailable or inference throws an exception.
     */
    suspend fun sendMessage(prompt: Contents): Result<String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val activeEngine = engine
                    ?: return@withLock Result.failure(
                        IllegalStateException("Engine is not initialized.")
                    )
                
                if (_initState.value !is InitState.Ready) {
                    return@withLock Result.failure(
                        IllegalStateException("Engine is not in Ready state.")
                    )
                }

                // Build a combined prompt: inject history context as a text prefix,
                // then include all content items from the current prompt.
                val combinedPrompt = buildCombinedPrompt(prompt)

                val conversation = activeEngine.createConversation()
                try {
                    val response = conversation.sendMessage(combinedPrompt)
                    
                    // Extract text from the response message parts
                    val responseText = response.contents.contents
                        .filterIsInstance<Content.Text>()
                        .joinToString("") { it.text }
                        .trim()

                    // Update rolling history with the transcription result
                    if (responseText.isNotEmpty()) {
                        conversationHistory.addLast(responseText)
                        while (conversationHistory.size > MAX_HISTORY_SIZE) {
                            conversationHistory.removeFirst()
                        }
                    }
                    
                    Result.success(responseText)
                } finally {
                    conversation.close()
                }
            } catch (e: Exception) {
                Log.e(TAG, "sendMessage failed: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Builds a combined prompt that prepends recent transcription history as text context
     * before the current prompt's content items.
     *
     * If there is no history, returns the original prompt unchanged. Otherwise, inserts
     * a context text block before the existing content so the model can reference what
     * was recently transcribed without re-processing prior audio.
     *
     * @param currentPrompt The current inference prompt containing text instructions and audio.
     * @return A [Contents] with history context prepended, or the original if no history exists.
     */
    private fun buildCombinedPrompt(currentPrompt: Contents): Contents {
        if (conversationHistory.isEmpty()) return currentPrompt

        val historyContext = conversationHistory.joinToString(" ") { it }
        // Note: Adding conversation history makes sense, but it can also break transcription for whatever reason
//        val contextPrefix = Content.Text(
//            "This is an ongoing conversation. Previously transcribed text included the following: \"$historyContext\"\n\n"
//        )

        // Prepend the history context before the existing prompt content items
        val existingContents = currentPrompt.contents
//        val combinedContents = mutableListOf<Content>(contextPrefix)
        val combinedContents = mutableListOf<Content>()
        combinedContents.addAll(existingContents)

        return Contents.of(*combinedContents.toTypedArray())
    }

    /**
     * Verifies the engine is healthy by attempting to create and close a test conversation.
     * Also clears the conversation history to provide a clean slate after recovery.
     *
     * @return [Result.success] if the engine is healthy, or [Result.failure]
     *         if the engine is unavailable or conversation creation fails.
     */
    suspend fun createNewConversation(): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val activeEngine = engine
                    ?: return@withLock Result.failure(
                        IllegalStateException("Engine is not initialized.")
                    )
                // Verify engine health by creating and immediately closing a conversation
                val testConversation = activeEngine.createConversation()
                testConversation.close()
                // Clear history so recovery starts with a clean context
                conversationHistory.clear()
                Log.d(TAG, "Engine health check passed, conversation history cleared")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Engine health check failed: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Clears the conversation history buffer.
     *
     * Should be called when starting a new recording session to prevent context
     * from a previous session bleeding into the new one.
     */
    fun clearHistory() {
        conversationHistory.clear()
        Log.d(TAG, "Conversation history cleared")
    }

    /**
     * Releases all engine resources: closes the engine and nulls out the singleton instance.
     *
     * After calling this method, [getInstance] will create a fresh instance.
     */
    fun release() {
        synchronized(lock) {
            instance = null
            
            // Release resources asynchronously. 
            // We use a dedicated thread to ensure cleanup happens even if the app is shutting down.
            Thread {
                kotlin.runCatching {
                    conversationHistory.clear()
                    engine?.close()
                    engine = null
                    _initState.value = InitState.Uninitialized
                    Log.d(TAG, "Engine resources released")
                }.onFailure { e ->
                    Log.e(TAG, "Error during release: ${e.message}", e)
                }
            }.start()
        }
    }

}
