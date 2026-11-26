package com.example.voxmanifestorapp.ui.basevox

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.example.voxmanifestorapp.data.StatusColor
import com.google.cloud.texttospeech.v1.AudioConfig
import com.google.cloud.texttospeech.v1.AudioEncoding
import com.google.cloud.texttospeech.v1.SynthesisInput
import com.google.cloud.texttospeech.v1.TextToSpeechClient
import com.google.cloud.texttospeech.v1.VoiceSelectionParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

data class AudioInfo(
    val audioTrack: AudioTrack,
    val totalFrames: Long
)

interface TextToSpeechRepository {
    suspend fun speak(text: String): AudioInfo

    /**
     * Speaks [text] and suspends until playback has fully finished. Callers get a
     * plain suspend point with no Android audio types — keeps ViewModels
     * JVM-unit-testable.
     */
    suspend fun speakBlocking(text: String)

    fun release()
    fun setLogger(logStatus: (String, StatusColor) -> Unit)
}

class TextToSpeechGoogleApiRepository(
    private val ttsClient: TextToSpeechClient
) : TextToSpeechRepository {

    // Written on Dispatchers.IO (speak) and the main thread (release) — @Volatile
    // so the IO write loop promptly sees a teardown's null-out.
    @Volatile
    private var audioTrack: AudioTrack? = null
    private var statusLogger: (String, StatusColor) -> Unit = { msg, _ -> Log.d(TAG, msg)}

    companion object {
        private const val TAG = "TTSRepository"
        private const val SAMPLE_RATE = 24000
    }

    override fun setLogger(logStatus: (String, StatusColor) -> Unit) {
        this.statusLogger = logStatus
    }

    private fun logStatus(message: String, color: StatusColor = StatusColor.Default) {
        statusLogger(message, color)
    }

    override suspend fun speak(text: String): AudioInfo = withContext(Dispatchers.IO) {
        try {
            logStatus("[TTS] Synthesizing speech for: $text")

            // time for a new voice
            val voice = VoiceSelectionParams.newBuilder()
                .setLanguageCode("en-US") // Still specify the language code
                // Set the specific Chirp 3 HD voice name
                .setName("en-US-Chirp3-HD-Zephyr")
                // Do NOT set SsmlGender when specifying a name
                .build()

            val audioConfig = AudioConfig.newBuilder()
                .setAudioEncoding(AudioEncoding.LINEAR16)
                .setSampleRateHertz(SAMPLE_RATE)
                .build()

            val input = SynthesisInput.newBuilder()
                .setText(text)
                .build()

            val response = ttsClient.synthesizeSpeech(input, voice, audioConfig)
            val audioData = response.audioContent.toByteArray()

            // The synthesis call above is blocking and cannot observe cancellation.
            // If the caller was torn down meanwhile (abandon/confirm released the
            // repository), bail out HERE — before creating and starting a new
            // AudioTrack that nothing would ever stop.
            coroutineContext.ensureActive()

            val bufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .build()

            // Torn down between the synthesis check and track creation? Kill the
            // orphan before it ever plays.
            try {
                coroutineContext.ensureActive()
            } catch (e: Exception) {
                audioTrack?.release()
                audioTrack = null
                throw e
            }

            audioTrack?.play()
            // Streaming write in chunks: a single write() of the full utterance
            // blocks until almost all of it has PLAYED, so a cancelled turn
            // would keep speaking for seconds. Chunking observes cancellation
            // within one buffer (~tens of ms).
            var offset = 0
            while (offset < audioData.size) {
                coroutineContext.ensureActive()
                val written = audioTrack?.write(
                    audioData, offset, minOf(bufferSize, audioData.size - offset)
                ) ?: break
                if (written <= 0) break
                offset += written
            }

            logStatus("[TTS] Speech synthesis complete", StatusColor.Pause)

            // Calculate total frames and return AudioInfo for monitoring
            val totalFrames = audioData.size / 2L  // LINEAR16 MONO: 2 bytes per frame
            
            return@withContext AudioInfo(
                audioTrack = audioTrack!!,
                totalFrames = totalFrames
            )

        } catch (e: Exception) {
            logStatus("[TTS] Error synthesising speech: ${e.message}", StatusColor.Stop)
            throw e
        }
    }

    override suspend fun speakBlocking(text: String) {
        try {
            val audioInfo = speak(text)
            withContext(Dispatchers.Default) {
                while (audioInfo.audioTrack.playbackHeadPosition < audioInfo.totalFrames &&
                    audioInfo.audioTrack.playState == AudioTrack.PLAYSTATE_PLAYING
                ) {
                    delay(100)
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Teardown cancelled the speaking turn. Any track that slipped past
            // the pre-playback cancellation check (or is mid-playback in the
            // poll loop) must stop NOW — release() is idempotent.
            release()
            throw e
        }
    }

    override fun release() {
        audioTrack?.let { track ->
            try {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.stop()
                }
                track.release()
                logStatus("[TTS] AudioTrack released")
            } catch (e: Exception) {
                logStatus("[TTS] Error releasing AudioTrack: ${e.message}", StatusColor.Stop)
            }
        }
        audioTrack = null
    }
}
