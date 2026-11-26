package com.example.voxmanifestorapp.ui.agent.utilities

import android.util.Log

/**
 * Utility functions for cleaning up text formatting for TTS synthesis.
 * Removes markdown formatting and other elements that don't work well with speech synthesis.
 */
object TextFormatting {
    
    private const val TAG = "TextFormatting"
    
    /**
     * Cleans up text for TTS synthesis by removing markdown formatting and other speech-unfriendly elements.
     * 
     * @param text The raw text that may contain markdown formatting
     * @return Cleaned text suitable for TTS synthesis
     */
    fun cleanForTTS(text: String): String {
        if (text.isBlank()) return text
        
        var cleaned = text
        
        // Remove markdown bold formatting (**text** -> text)
        cleaned = cleaned.replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
        
        // Remove markdown italic formatting (*text* -> text)
        cleaned = cleaned.replace(Regex("\\*([^*]+)\\*"), "$1")
        
        // Remove markdown code formatting (`text` -> text)
        cleaned = cleaned.replace(Regex("`([^`]+)`"), "$1")
        
        // Remove markdown headers (# Header -> Header)
        cleaned = cleaned.replace(Regex("^#{1,6}\\s+"), "")
        
        // Remove markdown list markers (- item -> item)
        cleaned = cleaned.replace(Regex("^[-*+]\\s+"), "")
        
        // Remove markdown numbered lists (1. item -> item)
        cleaned = cleaned.replace(Regex("^\\d+\\.\\s+"), "")
        
        // Remove markdown blockquotes (> text -> text)
        cleaned = cleaned.replace(Regex("^>\\s+"), "")
        
        // Remove markdown horizontal rules (--- or ***)
        cleaned = cleaned.replace(Regex("^[-*_]{3,}$"), "")
        
        // Remove markdown links ([text](url) -> text)
        cleaned = cleaned.replace(Regex("\\[([^\\]]+)\\]\\([^)]+\\)"), "$1")
        
        // Remove markdown images (![alt](url) -> alt)
        cleaned = cleaned.replace(Regex("!\\[([^\\]]+)\\]\\([^)]+\\)"), "$1")
        
        // Clean up excessive whitespace
        cleaned = cleaned.replace(Regex("\\n\\s*\\n\\s*\\n"), "\n\n") // Multiple newlines to double newlines
        cleaned = cleaned.replace(Regex("\\s+"), " ") // Multiple spaces to single space
        
        // Clean up leading/trailing whitespace
        cleaned = cleaned.trim()
        
        // Log if significant changes were made
        if (text != cleaned) {
            Log.d(TAG, "Cleaned TTS text - removed markdown formatting")
            Log.d(TAG, "Original length: ${text.length}, Cleaned length: ${cleaned.length}")
        }
        
        return cleaned
    }
    
    /**
     * Cleans up text for display in UI (removes markdown but preserves some formatting).
     * 
     * @param text The raw text that may contain markdown formatting
     * @return Cleaned text suitable for UI display
     */
    fun cleanForDisplay(text: String): String {
        if (text.isBlank()) return text
        
        var cleaned = text
        
        // Remove markdown bold formatting (**text** -> text)
        cleaned = cleaned.replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
        
        // Remove markdown italic formatting (*text* -> text)
        cleaned = cleaned.replace(Regex("\\*([^*]+)\\*"), "$1")
        
        // Remove markdown code formatting (`text` -> text)
        cleaned = cleaned.replace(Regex("`([^`]+)`"), "$1")
        
        // Remove markdown links ([text](url) -> text)
        cleaned = cleaned.replace(Regex("\\[([^\\]]+)\\]\\([^)]+\\)"), "$1")
        
        // Remove markdown images (![alt](url) -> alt)
        cleaned = cleaned.replace(Regex("!\\[([^\\]]+)\\]\\([^)]+\\)"), "$1")
        
        // Clean up excessive whitespace
        cleaned = cleaned.replace(Regex("\\s+"), " ")
        cleaned = cleaned.trim()
        
        return cleaned
    }
    
    /**
     * Checks if text contains markdown formatting that needs cleaning.
     * 
     * @param text The text to check
     * @return True if markdown formatting is detected
     */
    fun containsMarkdown(text: String): Boolean {
        return text.contains("**") || 
               text.contains("*") || 
               text.contains("`") || 
               text.contains("#") || 
               text.contains("[") || 
               text.contains("![")
    }
} 