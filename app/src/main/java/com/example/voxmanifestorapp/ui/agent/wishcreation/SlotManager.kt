package com.example.voxmanifestorapp.ui.agent.wishcreation

/** sets up a collection to run when a slot is selected, either through UI or voice. */
/**
private fun monitorSlotSelection() {
    scope.launch {
        var previousSlot = -1
        mainScreenState.selectedSlot.collect { newSlot ->
            // Update logic (same as the original function)
            Log.d("SLOT", "Agent - selected slot = ${newSlot}")

            // Only process selection changes, not deselections
            if (newSlot >= 0 && newSlot != previousSlot) {
                // Check microphone status
                val micActive = isMicrophoneActive(voiceManager)

                if (!micActive) {
                    logStatus(
                        "Microphone must be enabled to perform operations on wishes",
                        StatusColor.Pause
                    )
                } else if (currentConversation == null) {
                    // Process the manual selection only if mic is active and we're not in a conversation
                    commandMode.selectWishBySlot(newSlot)
                    updateConversationState(ConversationType.CommandMode)
                }
            }
            previousSlot = newSlot
        }
    }
}

*/