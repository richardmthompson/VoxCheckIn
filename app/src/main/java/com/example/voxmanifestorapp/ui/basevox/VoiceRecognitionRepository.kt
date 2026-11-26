package com.example.voxmanifestorapp.ui.basevox

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.MediaRecorder.AudioSource
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.voxmanifestorapp.data.RecognitionState
import com.example.voxmanifestorapp.data.StatusColor
import com.google.api.gax.rpc.ClientStream
import com.google.api.gax.rpc.ResponseObserver
import com.google.api.gax.rpc.StreamController
import com.google.cloud.speech.v1.RecognitionConfig
import com.google.cloud.speech.v1.SpeechClient
import com.google.cloud.speech.v1.SpeechContext
import com.google.cloud.speech.v1.StreamingRecognitionConfig
import com.google.cloud.speech.v1.StreamingRecognizeRequest
import com.google.cloud.speech.v1.StreamingRecognizeResponse
import com.google.protobuf.ByteString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface VoiceRecognitionRepository {
    var voiceFlow: Flow<Pair<String, Long>>?
    val recognitionState: StateFlow<RecognitionState>
    // sets the audio source to device mic or active bluetooth headset.
    fun setAudioSource(source: Int)
    fun startRecognition(newSource: AudioSource? = null)
    fun startRecording()
    fun resumeRecording()
    fun pauseRecording()
    fun release()
    fun setLogger(logStatus: (String, StatusColor) -> Unit)
}

class VoiceRecognitionGoogleApiRepository(
    private val speechClient: SpeechClient,
    private val context: Context
): VoiceRecognitionRepository {

    // set up a flag across all threads to prevent multiple release attempts.
    @Volatile
    private var hasBeenReleased = false
    private var externalScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var voiceRecorder: AudioRecord? = null
    private var clientStream: ClientStream<StreamingRecognizeRequest>? = null
    private var currentJob: Job? = null
    override var voiceFlow: Flow<Pair<String, Long>>? = createNewVoiceFlow()

    // set up status logger callback variable - called from voice model to send logs there
    private var statusLogger: (String, StatusColor) -> Unit = { msg, _ -> Log.d(TAG, msg) }

    // initiate recognition state as a state variable here, which will be shared with voice viewmodel
    // so that the UI can maintain reporting of current state of voice recog
    private val _recognitionState = MutableStateFlow<RecognitionState>(RecognitionState.Inactive)
    override val recognitionState: StateFlow<RecognitionState> = _recognitionState.asStateFlow()

    // dynamically updated audio source for switching between android mic and bluetooth devices
    private var selectedAudioSource: Int = MediaRecorder.AudioSource.MIC

    // sets up our logging callback, so we can gather status messages in the voice managed view model
    // takes String as input, runs logging function with the message as output.
    override fun setLogger(logViewModelCallBack: (String, StatusColor) -> Unit) {
        this.statusLogger = logViewModelCallBack
    }

    // helper function to make the color component optional in this class
    private fun logStatus(message: String, color: StatusColor = StatusColor.Default) {
        statusLogger.invoke(message, color)
    }

    // network error flags and vars
    @Volatile
    private var hasNetworkError = false

    // configures the streaming recognition process
    // adds speech contexts (expected phrases) from our VoiceCommandEntity
    private val startRequest = StreamingRecognizeRequest.newBuilder()
        .setStreamingConfig(
            StreamingRecognitionConfig.newBuilder()
                .setConfig(
                    RecognitionConfig.newBuilder()
                        /*
                        .addSpeechContexts(
                            SpeechContext.newBuilder()
                                .addAllPhrases(VoiceCommandEntity.getAllCommands())
                        )
                         */
                        // other basic settings
                        .setLanguageCode(LANGUAGE_CODE)
                        .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                        .setSampleRateHertz(SAMPLE_RATE)
                        .build()
                )
                // allow interim results and look for more than single utterances
                // good for continuous recognition
                .setInterimResults(true)
                .setSingleUtterance(false)
                .build()
        ).build()

    private fun createNewVoiceFlow() = callbackFlow {

        // re-implementing asynchronous callback interface for recognising streaming audio
        // we define what should happen when recognition starts, creates a result, etc.
        // response observer is a callback interface to the streaming process (hidden to us)
        val callback = object : ResponseObserver<StreamingRecognizeResponse> {

            // called when speech recog starts
            override fun onStart(controller: StreamController?) {
                //logStatus("[recog.client] Starting stream controller...")
            }

            // called each time a piece of speech is recog'd
            override fun onResponse(response: StreamingRecognizeResponse?) {
                //logStatus("[recog.client] Response Observer received ${response != null}", StatusColor.Go)

                response?.let {
                    //logStatus("[recog.client] Raw response: $response. Now processing.", StatusColor.Go)

                    var text: String? = null
                    var isFinal = false
                    // are there any results?
                    if (response.resultsCount > 0) {
                        // get the first result and check if it's final
                        val result = response.getResults(0)
                        isFinal = result.isFinal

                        //logStatus("[recog.client] Result found (isFinal: $isFinal)")

                        // check for alternatives and save them
                        if (result.alternativesCount > 0) {
                            val alternative = result.getAlternatives(0)
                            text = alternative.transcript
                            // hide this for now as it adds a lot of noise into the status bar
                            //logStatus("[SpeechClient] Alt Transcript: $text")
                        } else {
                            logStatus("[recog.client] No results in response", StatusColor.Pause)
                        }
                    }
                    // if result is final, emit the result.
                    if (isFinal) text?.let {
                        //logStatus( "[SpeechClient] Final result found")

                        // emit the processed voice command to the flow
                        // trysend accounts for the potential of loss of flow in streaming situations
                        //logStatus("[SpeechClient] Emitting Result...", StatusColor.Pause)

                        /* send the result back to the voice model */

                        trySend(Pair(text, System.currentTimeMillis()))

                    }
                }
            }

            override fun onError(t: Throwable?) {
                // Check if this is a network connectivity issue
                val isNetworkError = t?.let {
                    it is java.net.UnknownHostException ||
                            it.message?.contains("Unable to resolve host") == true ||
                            it.message?.contains("UNAVAILABLE") == true ||
                            it.message?.contains("Network") == true ||
                            it.cause is java.net.ConnectException
                } ?: false

                if (isNetworkError) {
                    hasNetworkError = true
                    logStatus(
                        "[recog.client] Network error: No internet connection. Voice recognition disabled.",
                        StatusColor.Stop
                    )
                } else {
                    logStatus("[recog.client] onError: ${t?.message}")
                }

                // Set state to inactive which will update the UI
                _recognitionState.value = RecognitionState.Inactive

                // Properly clean up resources
                cleanUpResources()

                close()
            }

            // when recognition's done
            override fun onComplete() {
                //logStatus("[recog.client] onComplete")
                close()
            }

        }

        //logStatus("[Flow] Callback created, about to create stream...")

        try {
            // we use clientStream to send audio to the API for decoding
            // splitcall splits the RPC into two streams: sending and receiving
            //logStatus("[recog.client] *** Trying to set up clientStream with callback...", StatusColor.Pause)
            clientStream = speechClient.streamingRecognizeCallable().splitCall(callback)
            //logStatus("[recog.client] *** Success! Sending startRequest...", StatusColor.Go)
            // sets up the initial configuration of the recognition session
            clientStream?.send(startRequest)

        } catch (e: Exception) {
            logStatus("[recog.client] Error setting up stream: ${e.message}", StatusColor.Stop)
            _recognitionState.value = RecognitionState.Inactive
        }


        awaitClose {
            // only release resources if we aren't already in the process of doing so...
            if (!hasBeenReleased) {
                //logStatus("[recog.client] Flow closing, performing auto cleanup...")
                cleanUpResources()
            }
        }

    }

    // simply modifies the internal selected audio source variable
    override fun setAudioSource(source: Int) {
        selectedAudioSource = source
        logStatus("Audio source changed to: ${if (source == MediaRecorder.AudioSource.VOICE_COMMUNICATION) "Bluetooth" else "Phone Mic"}")
    }

    /* we've set up configuration and callback settings, now for the actual methods... */

    // this is the initial call from the voice view model once the mic is clicked.
    // we initialise the recorder and then start recording
    override fun startRecognition(newSource: MediaRecorder.AudioSource?) {
        // implementation for starting audio capture and sending to api
        // involves setting up an audiorecord and sending to client
        // run initialisation (checking permissions and setting up voice recorder)
        //logStatus("[start] About to initialize voice recorder...")
        if (initVoiceRecorder()) {
            //logStatus("[recog.recog] Voice Recorder init success.  Attempting to record audio...")
            // check for success with permissions before attempting the recording process
            startRecording()
        }
    }

    private fun initVoiceRecorder(): Boolean {

        //logStatus("[recog.init] Initializing...")

        // if we already have set up a voice recorder, bypass setting it up again
        if (voiceRecorder != null) {
            logStatus("[recog.init] Recorder already exists. No init required.", StatusColor.Pause)
            cleanupRecorder()
        }

        val sampleRate = SAMPLE_RATE
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {

            // set the recognition state accordingly and return without continuing
            _recognitionState.value = RecognitionState.Permission_Denied

            logStatus("[recog.init] !!! Permission denied, cancelling init...", StatusColor.Stop)
            return false
        }

        try {

            //logStatus("[init] *** Try voiceRecorder create...", StatusColor.Pause)
            voiceRecorder = AudioRecord(
                selectedAudioSource,
                sampleRate,
                channelConfig,
                audioFormat,
                minBufferSize
            )

            if (voiceRecorder?.state != AudioRecord.STATE_INITIALIZED) {
                logStatus ("[recog.init] Failed to initialize AudioRecorder device", StatusColor.Stop)
                voiceRecorder?.release()
                voiceRecorder = null
                return false
            }

            // we must have initialized recorder successfully, so log it as such
            // logStatus("[recog.init] *** Success.", StatusColor.Go)

            _recognitionState.value = RecognitionState.Listen_Active
            return true

        } catch (e: SecurityException) {
            logStatus("[recog.init] !!! Security exception permission denied ${e.message}", StatusColor.Stop)
            _recognitionState.value = RecognitionState.Permission_Denied
            return false
        }
    }

    override fun startRecording() {
        //logStatus("[recog.record] *** Attempting to start recording...", StatusColor.Pause)

        externalScope.launch {
            //logStatus("[recog.record] Voice recorder: ${voiceRecorder?.state ?: "null"}")

            try {
                val buffer = ByteArray(BUFFER_SIZE)

                voiceRecorder?.let { recorder ->
                    //logStatus("[recog.record] starting to record for recog...", StatusColor.Go)
                    recorder.startRecording()

                    // add debug logging to detect audio input
                    var lastBytesRead = 0
                    var consecutiveZeros = 0

                    // enclose the recognition process inside a job whose existence depends on our recognition state

                    recognitionState.collect { state ->
                        logStatus("[recog] Recog state is $state", StatusColor.Pause)
                        when (state) {
                            RecognitionState.Listen_Active -> {
                                currentJob = launch {
                                    while (true) {
                                        val bytesRead = withContext(Dispatchers.IO) {
                                            recorder.read(buffer, 0, buffer.size)
                                        }

                                        if (bytesRead > 0) {

                                            // check if we're getting nonzero data
                                            val hasNonZero = buffer.take(bytesRead).any { it != 0.toByte() }
                                            if (hasNonZero) {
                                                consecutiveZeros = 0
                                                if (bytesRead != lastBytesRead) {
                                                    //logStatus("[recog] Reading audio: $bytesRead bytes with data", StatusColor.Go)
                                                    lastBytesRead = bytesRead
                                                }
                                            } else {
                                                consecutiveZeros++
                                                if (consecutiveZeros % 100 == 0) {
                                                    logStatus("[recog.record] Warning: $consecutiveZeros consecutive silent buffers", StatusColor.Pause)
                                                }
                                            }

                                            try {
                                                val audioRequest = StreamingRecognizeRequest.newBuilder()
                                                    .setAudioContent(ByteString.copyFrom(buffer, 0, bytesRead))
                                                    .build()
                                                //logStatus("[recog] Request built, clientStream is ${clientStream != null}")
                                                clientStream?.let { stream ->
                                                    //logStatus("[recog] About to send audio request...")
                                                    stream.send(audioRequest)
                                                    //logStatus("[recog] Audio request sent successfully")
                                                } ?: logStatus("[recog.record] ClientStream is null!", StatusColor.Stop)
                                            } catch (e: Exception) {
                                                // if it's another error, other than out of range
                                                logStatus("[recog.record] Error sending audio: ${e.message}", StatusColor.Stop)
                                                throw e
                                            }
                                        }
                                    }
                                } // launch audio job
                            }
                            else -> {
                                // cancel the job if recognition state has changed
                                currentJob?.cancel()
                                currentJob = null
                            }
                        }
                    }
                }
            } finally {
                withContext(Dispatchers.IO) {
                    logStatus("[recog.record] Recording stopped (finally), cleaning up resources...", StatusColor.Pause)
                    _recognitionState.value = RecognitionState.Inactive
                    cleanUpResources()
                }
            }
        }
    }

    private fun resetScope() {
        externalScope.cancel()
        externalScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    }

    override fun pauseRecording() {
        _recognitionState.value = RecognitionState.Listen_Pause

        resetScope()
        cleanUpResources()
        currentJob?.cancel()

        //logStatus("[recog.pause] Paused recording", StatusColor.Pause)
    }

    override fun resumeRecording() {
        logStatus("[recog.resume] Resuming recording...")
        voiceFlow = createNewVoiceFlow()

        startRecognition()

        //startRecording()

        _recognitionState.value = RecognitionState.Listen_Active
    }

    override fun release() {
        if (hasBeenReleased) {
            logStatus("[recog.release] Already released, skipping.", StatusColor.Pause)
        }
        //logStatus("[recog.release] Starting release process...", StatusColor.Pause)

        cleanUpResources()
    }

    private fun cleanupRecorder() {
        // safe cleanup of voice recorder
        voiceRecorder?.let { recorder ->
            try {
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    //logStatus("[cleanup] 1. Stopped recorder")
                    recorder.stop()
                }
            } catch (e: IllegalStateException) {
                logStatus("[recog.cleanup] Cleanup: problem stopping voice recorder",StatusColor.Stop)
            }
            try {
                recorder.release()
                //logStatus("[cleanup] 2. released recorder")
            } catch (e: Exception) {
                logStatus("[recog.cleanup] Cleanup: problem releasing recorder", StatusColor.Stop)
            }
        }
        voiceRecorder = null
    }

    private fun cleanupStream() {
        clientStream?.let { stream ->
            try {
                stream.closeSend()
                //logStatus("[cleanup] 3. closed client stream")
            } catch (e: IllegalStateException) {
                // stream was already closed, that's fine
                logStatus("[recog.cleanup] Trying to close an already closed stream, exiting gracefully", StatusColor.Stop)
            }
        }

        clientStream = null
    }

    private fun cleanUpResources() {
        if (hasBeenReleased) return

        hasBeenReleased = true
        //logStatus("[cleanup] Cleaning up audio Resources")

        resetScope()
        currentJob?.cancel()

        cleanupRecorder()

        cleanupStream()

        _recognitionState.value = RecognitionState.Listen_Pause
        hasBeenReleased = false
        //logStatus("[recog.cleanup] Resources released...", StatusColor.Pause)
    }

    companion object {
        private const val TAG = "VoiceRecognitionRepo"
        private const val BUFFER_SIZE = 1024
        private const val SAMPLE_RATE = 16000
        private const val LANGUAGE_CODE = "en-US"
    }

}