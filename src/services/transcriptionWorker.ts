

class OllamaTranscriptionWorker {
    private modelId = 'gemma4:e2b';
    private baseUrl = 'http://localhost:11434';

    async checkStatus() {
        try {
            const response = await fetch(`${this.baseUrl}/api/tags`);
            if (response.ok) return true;
        } catch (e) {
            return false;
        }
        return false;
    }

    async transcribe(audioData: Float32Array) {
        try {
            const isUp = await this.checkStatus();
            if (!isUp) throw new Error("Ollama is not responding at http://localhost:11434. Please check if it's running and CORS is enabled.");

            const response = await fetch(`${this.baseUrl}/api/generate`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    model: this.modelId,
                    prompt: 'Transcribe the following audio accurately.',
                    audio: [this.toBase64(audioData)],
                    stream: false
                })
            });

            if (!response.ok) {
                const errData = await response.json().catch(() => ({ error: response.statusText }));
                throw new Error(`Ollama Error (${response.status}): ${errData.error || response.statusText}`);
            }
            
            const data = await response.json();
            return data.response;
        } catch (error) {
            console.error('Ollama Transcription Error:', error);
            throw error;
        }
    }

    private toBase64(audioData: Float32Array) {
        // Simple conversion for the sake of the worker
        const buffer = new ArrayBuffer(audioData.length * 2);
        const view = new DataView(buffer);
        for (let i = 0; i < audioData.length; i++) {
            const s = Math.max(-1, Math.min(1, audioData[i]));
            view.setInt16(i * 2, s < 0 ? s * 0x8000 : s * 0x7FFF, true);
        }
        const bytes = new Uint8Array(buffer);
        let binary = '';
        const len = bytes.byteLength;
        for (let i = 0; i < len; i++) {
            binary += String.fromCharCode(bytes[i]);
        }
        return btoa(binary);
    }
}

const worker = new OllamaTranscriptionWorker();

self.onmessage = async (e) => {
    const { type, audioData } = e.data;
    
    if (type === 'init') {
        const isUp = await worker.checkStatus();
        if (isUp) {
            self.postMessage({ type: 'ready' });
        } else {
            // Still report ready but handle the connection later
            self.postMessage({ type: 'ready' });
        }
    } else if (type === 'transcribe') {
        try {
            const text = await worker.transcribe(audioData);
            self.postMessage({ type: 'result', text });
        } catch (error: any) {
            self.postMessage({ type: 'error', error: error.message });
        }
    }
};
