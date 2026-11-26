package com.example.voxmanifestorapp.data

import android.util.Log
import androidx.room.TypeConverter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

class MetadataTypeConverter {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @TypeConverter
    fun fromMetadataMap(metadata: Map<String, JsonElement>?): String? {
        return metadata?.let { map ->
            try {
                if (map.isEmpty()) {
                    null
                } else {
                    json.encodeToString(kotlinx.serialization.serializer<Map<String, JsonElement>>(), map)
                }
            } catch (e: Exception) {
                Log.e("MetadataTypeConverter", "Failed to serialize metadata: ${e.message}", e)
                null
            }
        }
    }

    @TypeConverter
    fun toMetadataMap(metadataJson: String?): Map<String, JsonElement>? {
        return metadataJson?.let { jsonString ->
            try {
                if (jsonString.isBlank()) {
                    emptyMap()
                } else {
                    json.decodeFromString<Map<String, JsonElement>>(jsonString)
                }
            } catch (e: Exception) {
                Log.w("MetadataTypeConverter", "Failed to parse metadata from database: ${e.message}")
                Log.w("MetadataTypeConverter", "Problematic JSON: ${jsonString.take(200)}...")

                emptyMap()
            }
        }
    }
}