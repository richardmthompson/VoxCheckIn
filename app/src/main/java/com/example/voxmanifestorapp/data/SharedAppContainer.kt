package com.example.voxmanifestorapp.data

import android.content.Context
import com.example.voxmanifestorapp.ManifestorApplication
import com.example.voxmanifestorapp.R
import com.example.voxmanifestorapp.ui.basevox.HOSTNAME
import com.example.voxmanifestorapp.ui.basevox.PORT
import com.example.voxmanifestorapp.ui.basevox.TextToSpeechGoogleApiRepository
import com.example.voxmanifestorapp.ui.basevox.TextToSpeechRepository
import com.example.voxmanifestorapp.ui.basevox.VoiceManagedViewModel
import com.example.voxmanifestorapp.ui.basevox.VoiceRecognitionGoogleApiRepository
import com.example.voxmanifestorapp.ui.basevox.VoiceRecognitionRepository
import com.example.voxmanifestorapp.ui.statuslog.StatusMessageService
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.speech.v1.SpeechClient
import com.google.cloud.speech.v1.stub.GrpcSpeechStub
import com.google.cloud.speech.v1.stub.SpeechStubSettings
import com.google.cloud.texttospeech.v1.TextToSpeechClient
import com.google.cloud.texttospeech.v1.TextToSpeechSettings

interface SharedAppContainer {
    val speechClient: SpeechClient
    val voiceRecognitionRepository: VoiceRecognitionRepository
    val ttsClient: TextToSpeechClient
    val textToSpeechRepository: TextToSpeechRepository
    val statusMessageService: StatusMessageService
    val voiceManagedViewModel: VoiceManagedViewModel
}

abstract class SharedAppDataContainer(private val context: Context) : SharedAppContainer {
    private fun readCredentials(): ServiceAccountCredentials =
        context.resources.openRawResource(R.raw.creds).use {
            ServiceAccountCredentials.fromStream(it)
        }

    override val speechClient: SpeechClient by lazy {
        val stubSettings = SpeechStubSettings.newBuilder().apply {
            credentialsProvider = FixedCredentialsProvider.create(readCredentials())
            endpoint = "$HOSTNAME:$PORT"
        }.build()

        SpeechClient.create(GrpcSpeechStub.create(stubSettings))
    }

    override val voiceRecognitionRepository: VoiceRecognitionRepository by lazy {
        VoiceRecognitionGoogleApiRepository(speechClient, context)
    }

    override val ttsClient: TextToSpeechClient by lazy {
        val settings = TextToSpeechSettings.newBuilder()
            .setCredentialsProvider(FixedCredentialsProvider.create(readCredentials()))
            .build()

        TextToSpeechClient.create(settings)
    }

    override val textToSpeechRepository: TextToSpeechRepository by lazy {
        TextToSpeechGoogleApiRepository(ttsClient)
    }

    override val statusMessageService: StatusMessageService by lazy {
        StatusMessageService()
    }

    override val voiceManagedViewModel: VoiceManagedViewModel by lazy {
        VoiceManagedViewModel(
            permissionState = (context.applicationContext as ManifestorApplication).permissionState,
            voiceRecognizer = voiceRecognitionRepository,
        )
    }
}
