package com.example.voxmanifestorapp.ui.statuslog

import com.example.voxmanifestorapp.data.StatusColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class StatusMessageService () {
    private val _statusMessages = MutableStateFlow<List<Pair<String, StatusColor>>>(emptyList())
    val statusMessages = _statusMessages.asStateFlow()

    // size of status bar buffer in lines
    val STATUS_LENGTH = 200

    fun addStatusMessage(message: String, color: StatusColor = StatusColor.Default) {
        _statusMessages.value = statusMessages.value + Pair(message, color)
        if (_statusMessages.value.size > STATUS_LENGTH) {
            _statusMessages.value = _statusMessages.value.drop(1)
        }
    }

}
