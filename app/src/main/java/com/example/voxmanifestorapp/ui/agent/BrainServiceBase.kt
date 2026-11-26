package com.example.voxmanifestorapp.ui.agent

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.net.UnknownHostException

/**
 * Custom exception for network connectivity issues
 */
class NetworkException(message: String) : Exception(message) {
    companion object {
        const val DEFAULT_MESSAGE = "Network connectivity issue detected"
    }
}

abstract class BrainServiceBase(private val apiKey: String) {
    protected val TAG = "BrainService"
    protected val model by lazy { createModel("gemini-3.5-flash") }

    protected fun createModel(modelName: String): GenerativeModel =
        GenerativeModel(modelName = modelName, apiKey = apiKey)

    protected fun logStatus(message: String) {
        Log.d(TAG, message)
    }

    /**
     * Handles network errors consistently across brain service calls.
     */
    protected suspend fun <T> withNetworkErrorHandling(block: suspend () -> Result<T>): Result<T> {
        return try {
            block()
        } catch (e: CancellationException) {
            // Cancellation is teardown; callers must not run failure recovery for it.
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "ERROR CATCH: Error in brain service call: ${e.message}")

            val isNetworkError = e.message?.contains("network", ignoreCase = true) == true ||
                e.message?.contains("connect", ignoreCase = true) == true ||
                e.message?.contains("unavailable", ignoreCase = true) == true ||
                e.message?.contains("unexpected", ignoreCase = true) == true ||
                e is UnknownHostException ||
                e is IOException

            if (isNetworkError) {
                Log.e(TAG, "ERROR CATCH: Error is a network error: ${e.message}")
                Log.d(TAG, "withNetworkErrorHandling: NetworkException back up the chain... ERROR: ${e::class.simpleName} - ${e.message}", e)
                Result.failure(NetworkException(NetworkException.DEFAULT_MESSAGE))
            } else {
                Result.failure(e)
            }
        }
    }

    protected fun cleanJsonResponse(rawResponse: String): String {
        return try {
            val noMarkdown = rawResponse
                .replace("```json", "")
                .replace("```", "")
                .trim()

            val startIndex = noMarkdown.indexOf("{")
            val endIndex = noMarkdown.lastIndexOf("}") + 1

            if (startIndex == -1 || endIndex == 0) {
                throw IllegalArgumentException("Could not find valid JSON brackets")
            }

            noMarkdown
                .substring(startIndex, endIndex)
                .lines()
                .map { it.trim() }
                .joinToString("\n")
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning JSON: ${e.message}")
            throw e
        }
    }
}
