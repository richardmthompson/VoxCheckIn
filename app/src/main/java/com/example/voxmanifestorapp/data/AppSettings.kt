package com.example.voxmanifestorapp.data

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** App settings determined not to be high priority at this juncture */

/*
// Core settings data structures
data class AppSettings(
    val conceptScreen: ConceptScreenSettings = ConceptScreenSettings()
    // Ready for future settings groups
)

data class ConceptScreenSettings(
    val isConversationHistoryMinimized: Boolean = false,
    val isStatusLogMinimized: Boolean = false
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class UserPreferencesRepository(private val context: Context) {

    private val dataStore: DataStore<Preferences> = provideDataStore(context)

    private fun provideDataStore(context: Context): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("settings") }
        )
    }

    companion object {
        private val CONVERSATION_HISTORY_MINIMIZED = booleanPreferencesKey("conversation_history_minimized")
        private val STATUS_LOG_MINIMIZED = booleanPreferencesKey("status_log_minimized")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data
        .map { preferences ->
            AppSettings(
                conceptScreen = ConceptScreenSettings(
                    isConversationHistoryMinimized = preferences[CONVERSATION_HISTORY_MINIMIZED] ?: false,
                    isStatusLogMinimized = preferences[STATUS_LOG_MINIMIZED] ?: false
                )
            )
        }

    suspend fun updateConceptScreenSettings(update: ConceptScreenSettings.() -> ConceptScreenSettings) {
        context.dataStore.edit { preferences ->
            val current = settingsFlow.first().conceptScreen
            val new = update(current)
            preferences[CONVERSATION_HISTORY_MINIMIZED] = new.isConversationHistoryMinimized
            preferences[STATUS_LOG_MINIMIZED] = new.isStatusLogMinimized
        }
    }

}

// Create a CompositionLocal instance
val LocalPreferences = compositionLocalOf<UserPreferencesRepository> {
    error("No UserPreferencesRepository provided")
}

 */

/*
// In ConceptScreen.kt
@Composable
fun ConceptScreen(/* existing params */) {
    val preferences = LocalPreferences.current
    val settings by preferences.settingsFlow.collectAsState(initial = AppSettings())

    // Use settings.conceptScreen to control UI state:
    ExpandableCard(
        title = "Conversation History",
        isExpanded = !settings.conceptScreen.isConversationHistoryMinimized,
        onExpandChanged = { isExpanded ->
            scope.launch {
                preferences.updateConceptScreenSettings {
                    copy(isConversationHistoryMinimized = !isExpanded)
                }
            }
        }
    ) {
        // Conversation history content
    }
    // ... rest of screen
}

 */