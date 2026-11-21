package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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
    val userId: Long = 0L,
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
    private var cachedUserId: Long = 0L

    @Volatile
    private var cachedServerBaseUrl: String = NetworkModule.DEFAULT_BASE_URL

    val sessionFlow: Flow<UserSessionPrefs> = dataStore.data.map { prefs ->
        val token = prefs[KEY_AUTH_TOKEN].orEmpty()
        val userId = prefs[KEY_USER_ID] ?: 0L
        cachedToken = token.ifBlank { null }
        cachedUserId = userId
        cachedServerBaseUrl = NetworkModule.DEFAULT_BASE_URL
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
            serverBaseUrl = NetworkModule.DEFAULT_BASE_URL,
            userId = userId,
            technicianName = prefs[KEY_TECH_NAME] ?: if (token.isBlank()) "" else "Teknisi",
            technicianRole = prefs[KEY_TECH_ROLE] ?: if (token.isBlank()) "" else "technician",
            themePreference = themePref,
            shiftStatus = shiftStatus,
            lastSyncedAtMillis = prefs[KEY_LAST_SYNCED_MILLIS] ?: 0L
        )
    }

    override fun getToken(): String? = cachedToken

    fun getUserId(): Long = cachedUserId

    fun getServerBaseUrl(): String = NetworkModule.DEFAULT_BASE_URL

    suspend fun warmUpTokenCache() {
        val prefs = dataStore.data.first()
        cachedToken = prefs[KEY_AUTH_TOKEN]?.takeIf { it.isNotBlank() }
        cachedUserId = prefs[KEY_USER_ID] ?: 0L
        cachedServerBaseUrl = NetworkModule.DEFAULT_BASE_URL
    }

    suspend fun saveAuthSession(
        token: String,
        technicianName: String,
        role: String,
        userId: Long = 0L
    ) {
        val trimmedToken = token.trim()
        cachedToken = trimmedToken.ifBlank { null }
        cachedUserId = userId
        dataStore.edit { prefs ->
            prefs[KEY_AUTH_TOKEN] = trimmedToken
            if (userId > 0L) prefs[KEY_USER_ID] = userId else prefs.remove(KEY_USER_ID)
            if (technicianName.isNotBlank()) prefs[KEY_TECH_NAME] = technicianName.trim()
            if (role.isNotBlank()) prefs[KEY_TECH_ROLE] = role.trim()
        }
    }

    suspend fun clearAuthToken() {
        cachedToken = null
        cachedUserId = 0L
        dataStore.edit { prefs ->
            prefs.remove(KEY_AUTH_TOKEN)
            prefs.remove(KEY_USER_ID)
            prefs.remove(KEY_TECH_NAME)
            prefs.remove(KEY_TECH_ROLE)
            prefs.remove(KEY_LAST_SYNCED_MILLIS)
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
        private val KEY_USER_ID = longPreferencesKey("technician_user_id")
        private val KEY_SERVER_URL = stringPreferencesKey("server_base_url")
        private val KEY_TECH_NAME = stringPreferencesKey("technician_name")
        private val KEY_TECH_ROLE = stringPreferencesKey("technician_role")
        private val KEY_THEME_PREF = stringPreferencesKey("theme_preference")
        private val KEY_SHIFT_STATUS = stringPreferencesKey("shift_status")
        private val KEY_LAST_SYNCED_MILLIS = longPreferencesKey("last_synced_at_millis")
    }
}
