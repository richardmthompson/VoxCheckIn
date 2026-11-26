package com.example.voxmanifestorapp.ui.basevox

import android.media.MediaRecorder
import android.media.MediaRecorder.AudioSource
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.voxmanifestorapp.ManifestorApplication
import com.example.voxmanifestorapp.data.RecognitionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.example.voxmanifestorapp.data.StatusColor

import com.example.voxmanifestorapp.ui.statuslog.StatusMessageService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine

/*  the Voice Managed View Model (VmVm) is the "voice hardware interface"
    - manages mechanics of voice recog (communicating between ui and repository)
    - emits raw text as it is recognised
    - handles microphone state & permissions
    - reports recog system status into logs
 */

open class VoiceManagedViewModel(
    // permission state is fed into viewmodel through factory, injected from activity->application layers.
    private val permissionState: StateFlow<Boolean>,
    private val voiceRecognizer: VoiceRecognitionRepository,
): ViewModel() {

    // provides the raw text converted from speech into text
    private val _rawTextState = MutableStateFlow<Pair<String, Long>>(Pair("", 0L))
    val rawTextState = _rawTextState.asStateFlow()

    /**
     * Read-only mic permission state for screens that gate conversation start on
     * it (e.g. SigilScreen waits for the grant before the opening TTS, so the
     * mic restart after speech never hits a permission-denied dead end).
     */
    val micPermissionState: StateFlow<Boolean> get() = permissionState

    // reflects the current state of voice recognition in the app, listening/inactive etc.
    val recognitionState : StateFlow<RecognitionState> = voiceRecognizer.recognitionState

    // this variable is used when we need to exit to request permission, then return.
    private val _pendingRecordingStart = MutableStateFlow(false)
    private val pendingRecordingStart = _pendingRecordingStart.asStateFlow()

    // timing jobs
    private var voiceCollectionJob: Job? = null
    // keepalive is because google kicks us out after 305sec of continuous streaming, so we close and re-open the connection at 300s
    private var keepAliveJob: Job? = null
    private val STREAM_TIMEOUT = 300000L // 300 seconds (5 minutes)

    private lateinit var statusService: StatusMessageService

    companion object {
        private const val TAG = "vox_manifestor"
    }

    init {
        Log.d("VoiceInit", "New VoiceManagedViewModel instance created with hash: ${hashCode()}")
    }

    fun setStatusService(service: StatusMessageService) {
        statusService = service
        // set up our status bar as recipient of status messages from our voice recog software
        voiceRecognizer.setLogger { statusMessage, color -> logStatus(statusMessage, color) }

        // set up state monitors for status bar
        startMonitoringStates()
    }

    private fun logStatus(message:String, color: StatusColor = StatusColor.Default) {
        Log.d(TAG, message)
        statusService.let { service -> service.addStatusMessage(message, color) }
    }

    // this is our self-launched 'init' function: it's launched after creation in the nav host
    private fun startMonitoringStates() {
        // launch a co-routine that monitors all our states and feeds them into the status bar for update.

        viewModelScope.launch {
            combine(
                permissionState,
                recognitionState
            ) { permission, recognition ->

                when (permission) {
                    true -> {
                        logStatus("Microphone permission granted", StatusColor.Go)

                        // also check if we're pending voice recog launch after permission checking loop
                        if (pendingRecordingStart.value) {
                            _pendingRecordingStart.value = false
                            startVoiceRecognition()
                        }

                    }
                    false -> logStatus("Microphone permission denied", StatusColor.Stop)
                }

                when(recognition) {
                    RecognitionState.Listen_Active -> logStatus("[vmodel.state] Listening...", StatusColor.Go)
                    RecognitionState.Listen_Pause -> logStatus("[vmodel.state] Paused...", StatusColor.Go)
                    RecognitionState.Inactive -> logStatus("[vmodel.state] Voice Recognition Inactive", StatusColor.Pause)
                    RecognitionState.Permission_Denied -> logStatus("[vmodel.state] Microphone permission DENIED", StatusColor.Stop)
                }

            }.collect()
        }
    }

    fun switchAudioSource(useBluetooth: Boolean) {
        try {
            val newSource = if (useBluetooth) {
                MediaRecorder.AudioSource.VOICE_COMMUNICATION  // Bluetooth mic
            } else {
                MediaRecorder.AudioSource.MIC  // Phone mic
            }

            // Stop current recording
            stopVoiceRecognition()

            // Set new source
            voiceRecognizer.setAudioSource(newSource)

            // Restart recording
            startVoiceRecognition()

        } catch (e: Exception) {
            logStatus("[VoiceModel] Failed to switch audio source: ${e.message}", StatusColor.Stop)
        }
    }

    /** Resets the raw text state for instances when we re-set the UI
     *
     */
    fun resetRawText() {
        _rawTextState.value = Pair("", 0L)
    }

    open fun startVoiceRecognition(newSource: AudioSource? = null) {
        logStatus("[vmodel.start] *** Launch...", StatusColor.Go)
        voiceRecognizer.startRecognition(newSource)

        //logStatus("[vmodel] *** Recording... collecting flow...", StatusColor.Go)
        voiceCollectionJob = viewModelScope.launch {
            voiceRecognizer.voiceFlow?.collect { voicePair ->
                _rawTextState.value = Pair(voicePair.first, voicePair.second)
                //logStatus("[VModel] *** Collected raw voice data, sending to genie")
            } // collect
        } // job / launch

        // Start the keepalive timer
        keepAliveJob?.cancel()
        keepAliveJob = viewModelScope.launch {
            delay(STREAM_TIMEOUT)
            logStatus("[vmodel.start] Stream timeout approaching, refreshing connection...")

            voiceCollectionJob?.cancel()
            voiceRecognizer.pauseRecording()
            delay(100)
            startVoiceRecognition()
        }

    } // fun

    open fun stopVoiceRecognition() {
        voiceCollectionJob?.cancel()
        keepAliveJob?.cancel()
        voiceRecognizer.pauseRecording()
        logStatus("[vmodel.stop] *** Voice recognition TERMINATED", StatusColor.Go)
    }


    fun handleMicrophoneClick(
        onRequestPermission: () -> Unit
    ) {
        if (!permissionState.value) {
            _pendingRecordingStart.value = true
            // this should loop back to the activity, request permissions, then back here
            // to the init function, which should start recog
            logStatus("[vmodel.mic] CLICK: Launching permission loop", StatusColor.Go)
            onRequestPermission()

        }
        else {
            when (recognitionState.value) {
                RecognitionState.Inactive -> {
                    logStatus("[vmodel.mic] CLICK: Starting recognition...", StatusColor.Go)
                    startVoiceRecognition()
                }
                RecognitionState.Listen_Active -> {
                    logStatus("[vmodel.mic] CLICK: Pausing recog...", StatusColor.Pause)
                    stopVoiceRecognition()
                }
                RecognitionState.Listen_Pause -> {
                    logStatus("[vmodel.mic] CLICK: Resuming recog...", StatusColor.Go)
                    startVoiceRecognition()
                }
                RecognitionState.Permission_Denied -> {
                    logStatus("[vmodel.mic] CLICK: Launching permission loop", StatusColor.Pause)
                    onRequestPermission
                }

            }
        }
    }

}

/*

1. `startRecognition()` is called which:
   - Initializes voiceRecorder
   - Starts recording process (but hasn't sent audio yet)

2. Back in ViewModel:
   - Starts collecting from `commandsFlow`
   - This triggers the `callbackFlow` code to run
   - Creates callback and sets up clientStream
   - We see the `onStart()` log

3. Then back to recording process which:
   - Now has an established clientStream
   - Starts reading audio data
   - Can successfully send audio through `clientStream.send(audioRequest)`
   - Loops this process

So the timing works because:
- Recording doesn't actually try to use clientStream until after the flow collection has set it up
- The recording process waits for the clientStream to be ready before starting to send audio

This explains why it worked in this order - there's an implicit synchronization happening between the recording process starting and the clientStream being established through the flow collection.

*/
