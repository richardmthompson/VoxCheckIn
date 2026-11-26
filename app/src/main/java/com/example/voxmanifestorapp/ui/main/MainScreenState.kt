package com.example.voxmanifestorapp.ui.main

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

// MainScreenState.kt
class MainScreenState {
    // when no slot is selected, value = -1.  Otherwise slot is index of the current slot (0->4)
    private val _selectedSlot = MutableStateFlow<Int>(-1)
    val selectedSlot = _selectedSlot.asStateFlow()

    fun setSelectedSlot(slot: Int) {
        // If clicking the already selected slot, deselect it and set slot value to -1
        if (_selectedSlot.value == slot) {
            _selectedSlot.value = -1
        } else {
            _selectedSlot.value = slot
        }
    }

    fun clearSelection() {
        _selectedSlot.value = -1
    }
}
