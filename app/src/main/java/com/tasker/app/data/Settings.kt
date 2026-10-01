package com.tasker.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
data class CustomTheme(
    val name: String = "My theme",
    val dark: Boolean = false,
    val primary: Int = 0xFFE5484D.toInt(),
    val background: Int = 0xFFFFFFFF.toInt(),
    val surface: Int = 0xFFF7F7F7.toInt(),
    val text: Int = 0xFF1F1F1F.toInt(),
    val accent: Int = 0xFF3B82F6.toInt(),
)

object SyncMode {
    const val OFF = 0
    const val FILE = 1
    const val FIREBASE = 2
}

@Serializable
data class Settings(
    val themeId: String = "paper",
    val followSystem: Boolean = false,
    val lightThemeId: String = "paper",
    val darkThemeId: String = "graphite",
    val customTheme: CustomTheme = CustomTheme(),
    /** "" = system, otherwise a BCP-47 tag such as "ar" or "he". */
    val language: String = "",
    val forceRtl: Boolean = false,
    /** java.time.DayOfWeek value: 1 = Monday, 6 = Saturday, 7 = Sunday. */
    val weekStart: Int = 1,
    val startScreen: String = "today",
    val nearlyDueAlerts: Boolean = true,
    val nearlyDueLeadMin: Int = 30,
    val overdueAlerts: Boolean = true,
    val morningSummary: Boolean = true,
    val summaryHour: Int = 8,
    val defaultReminderMin: Int = 0,
    val showCompleted: Boolean = false,
    val syncMode: Int = SyncMode.OFF,
    val syncFileUri: String = "",
    val firebaseApiKey: String = "",
    val firebaseAppId: String = "",
    val firebaseProjectId: String = "",
    val firebaseEmail: String = "",
    val firebaseWatermark: Long = 0,
    val notifyOnSync: Boolean = true,
    val lastSync: Long = 0,
    val lastSyncError: String = "",
    val onboarded: Boolean = false,
)

private val Context.dataStore by preferencesDataStore("tasker_settings")
private val KEY = stringPreferencesKey("settings")

class SettingsStore(private val context: Context) {
    val flow: Flow<Settings> = context.dataStore.data.map { prefs ->
        prefs[KEY]?.let { runCatching { AppJson.decodeFromString<Settings>(it) }.getOrNull() } ?: Settings()
    }

    suspend fun get(): Settings = flow.first()

    suspend fun update(transform: (Settings) -> Settings) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY]?.let { runCatching { AppJson.decodeFromString<Settings>(it) }.getOrNull() } ?: Settings()
            prefs[KEY] = AppJson.encodeToString(transform(current))
        }
    }
}
