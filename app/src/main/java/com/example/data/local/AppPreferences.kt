package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.remote.NetworkModule
import com.example.data.remote.TokenProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.pingDuluDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "pingdulu_field_ops_prefs"
)

enum class ThemePreference {
    SYSTEM,
    LIGHT,
    DARK
}

enum class ShiftStatus(val label: String) {
    ON_DUTY("On Duty"),
    STANDBY("Standby"),
    BREAK("Istirahat")
}

data class UserSessionPrefs(
    val authToken: String = "",
    val serverBaseUrl: String = NetworkModule.DEFAULT_BASE_URL,
    val technicianName: String = "Fajar",
    val technicianRole: String = "admin",
    val themePreference: ThemePreference = ThemePreference.LIGHT,
    val shiftStatus: ShiftStatus = ShiftStatus.ON_DUTY,
    val lastSyncedAtMillis: Long = 0L
)

class AppPreferences(context: Context) : TokenProvider {

    private val dataStore = context.applicationContext.pingDuluDataStore

    @Volatile
    private var cachedToken: String? = null

    @Volatile
    private var cachedServerBaseUrl: String = NetworkModule.DEFAULT_BASE_URL

    val sessionFlow: Flow<UserSessionPrefs> = dataStore.data.map { prefs ->
        val token = prefs[KEY_AUTH_TOKEN].orEmpty()
        cachedToken = token.ifBlank { null }
        val resolvedUrl = NetworkModule.normalizeBaseUrl(
            prefs[KEY_SERVER_URL] ?: NetworkModule.DEFAULT_BASE_URL
        )
        cachedServerBaseUrl = resolvedUrl
        val themeRaw = prefs[KEY_THEME_PREF]
        val themePref = when (themeRaw) {
            "DARK" -> ThemePreference.DARK
            "SYSTEM" -> ThemePreference.SYSTEM
            else -> ThemePreference.LIGHT
        }
        val shiftRaw = prefs[KEY_SHIFT_STATUS]
        val shiftStatus = when (shiftRaw) {
            ShiftStatus.STANDBY.name -> ShiftStatus.STANDBY
            ShiftStatus.BREAK.name -> ShiftStatus.BREAK
            else -> ShiftStatus.ON_DUTY
        }
        UserSessionPrefs(
            authToken = token,
            serverBaseUrl = resolvedUrl,
            technicianName = prefs[KEY_TECH_NAME] ?: "Fajar",
            technicianRole = prefs[KEY_TECH_ROLE] ?: "admin",
            themePreference = themePref,
            shiftStatus = shiftStatus,
            lastSyncedAtMillis = prefs[KEY_LAST_SYNCED_MILLIS] ?: 0L
        )
    }

    override fun getToken(): String? = cachedToken

    fun getServerBaseUrl(): String = cachedServerBaseUrl

    suspend fun warmUpTokenCache() {
        val prefs = dataStore.data.first()
        cachedToken = prefs[KEY_AUTH_TOKEN]?.takeIf { it.isNotBlank() }
        cachedServerBaseUrl = NetworkModule.normalizeBaseUrl(
            prefs[KEY_SERVER_URL] ?: NetworkModule.DEFAULT_BASE_URL
        )
    }

    suspend fun saveAuthSession(token: String, technicianName: String, role: String) {
        val trimmedToken = token.trim()
        cachedToken = trimmedToken.ifBlank { null }
        dataStore.edit { prefs ->
            prefs[KEY_AUTH_TOKEN] = trimmedToken
            if (technicianName.isNotBlank()) prefs[KEY_TECH_NAME] = technicianName.trim()
            if (role.isNotBlank()) prefs[KEY_TECH_ROLE] = role.trim()
        }
    }

    suspend fun clearAuthToken() {
        cachedToken = null
        dataStore.edit { prefs ->
            prefs.remove(KEY_AUTH_TOKEN)
        }
    }

    suspend fun updateServerBaseUrl(url: String) {
        val normalized = NetworkModule.normalizeBaseUrl(url)
        cachedServerBaseUrl = normalized
        dataStore.edit { prefs ->
            prefs[KEY_SERVER_URL] = normalized
        }
    }

    suspend fun setThemePreference(preference: ThemePreference) {
        dataStore.edit { prefs ->
            prefs[KEY_THEME_PREF] = preference.name
        }
    }

    suspend fun setShiftStatus(status: ShiftStatus) {
        dataStore.edit { prefs ->
            prefs[KEY_SHIFT_STATUS] = status.name
        }
    }

    suspend fun updateLastSyncedAt(timestampMillis: Long = System.currentTimeMillis()) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_SYNCED_MILLIS] = timestampMillis
        }
    }

    companion object {
        private val KEY_AUTH_TOKEN = stringPreferencesKey("auth_token")
        private val KEY_SERVER_URL = stringPreferencesKey("server_base_url")
        private val KEY_TECH_NAME = stringPreferencesKey("technician_name")
        private val KEY_TECH_ROLE = stringPreferencesKey("technician_role")
        private val KEY_THEME_PREF = stringPreferencesKey("theme_preference")
        private val KEY_SHIFT_STATUS = stringPreferencesKey("shift_status")
        private val KEY_LAST_SYNCED_MILLIS = longPreferencesKey("last_synced_at_millis")
    }
}
