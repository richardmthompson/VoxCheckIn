package com.example.voxmanifestorapp.ui.utils

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SoundPlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private val TAG = "SoundPlayer"

    suspend fun playSound(resourceId: Int) = withContext(Dispatchers.IO) {
        try {
            // Release any existing media player
            releaseMediaPlayer()

            // Create and play new sound
            mediaPlayer = MediaPlayer.create(context, resourceId)
            mediaPlayer?.setOnCompletionListener { mp ->
                mp.release()
                mediaPlayer = null  // Important: nullify the reference after release
            }
            mediaPlayer?.start()
        } catch (e: Exception) {
            // Log error but don't crash
            Log.e(TAG, "Error playing sound: ${e.message}")
        }
    }


    fun releaseMediaPlayer() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
            mediaPlayer = null
        }
    }

}
