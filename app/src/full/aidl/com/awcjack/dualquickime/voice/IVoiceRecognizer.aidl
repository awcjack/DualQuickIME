// Cross-process API to the Qwen3-ASR recognizer. The recognizer holds ~700 MB
// of INT8 weights resident the moment it loads; running it in the IME process
// risks LMK killing the keyboard mid-utterance. Hosting it in a separate
// :voice process insulates the IME from that memory peak.
//
// Audio crosses the boundary as little-endian int16 PCM packed into a byte[]
// to halve the Binder transaction size — a 30 s 16 kHz mono float buffer is
// ~1.92 MB, which exceeds the ~1 MB transaction cap; the same buffer packed
// as int16 bytes is ~960 KB and fits comfortably. AIDL itself doesn't
// support `short`, so we use byte[] and unpack on the service side.
package com.awcjack.dualquickime.voice;

interface IVoiceRecognizer {
    /**
     * Load the Qwen3-ASR recognizer with weights from the given absolute path
     * and the standard 4-thread, 80-token, greedy-decode tuning. Idempotent:
     * returns true if a recognizer is already loaded. Returns false on failure
     * (missing files, bad config, OOM).
     */
    boolean initialize(String modelDir, int numThreads, int maxNewTokens);

    /**
     * Block until the given 16 kHz mono int16 PCM segment is fully decoded;
     * return the trimmed transcription. `pcm16le` carries little-endian
     * int16 samples (2 bytes per sample). Returns empty string if the
     * recognizer is not loaded or if decode throws.
     */
    String transcribe(in byte[] pcm16le);

    /**
     * Release the recognizer's native resources so the :voice process can drop
     * back to a few MB. Safe to call when nothing is loaded. The service
     * itself stays alive for the next initialize() call.
     */
    void releaseModel();

    /** Whether a recognizer is currently loaded in this service. */
    boolean isLoaded();
}
