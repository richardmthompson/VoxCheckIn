package com.example.voxmanifestorapp.data

import androidx.compose.ui.graphics.Color

// used by the concept view model to load data and emit this information so agent can pick it up
enum class DataLoadingState {
    Loading,
    Ready,
    Error
}

enum class RecognitionState {
    Inactive,
    Listen_Active,
    Listen_Pause,
    Permission_Denied
}

enum class StatusColor(val color: Color) {
    Default(Color.White),
    Go(Color.Green),
    Pause(Color(0xFFFFBF00)),
    Stop(Color.Red)
}

sealed class ConversationState {
    object CheckingManifestations: ConversationState()
    data class AwaitingDescription(val manifestationNumber: Int) : ConversationState()
    object Complete : ConversationState()
}
