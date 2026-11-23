package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.remote.NetworkModule
import com.example.data.remote.TokenProvider
import com.example.domain.model.formatDepartmentDisplay
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
    val technicianUsername: String = "",
    val technicianName: String = "Fajar",
    val technicianRole: String = "admin",
    val avatarUrl: String? = null,
    val department: String? = null,
    val subDepartment: String? = null,
    val isActive: Int = 1,
    val themePreference: ThemePreference = ThemePreference.LIGHT,
    val shiftStatus: ShiftStatus = ShiftStatus.ON_DUTY,
    val lastSyncedAtMillis: Long = 0L
) {
    val isUserActive: Boolean
        get() = isActive == 1

    val departmentDisplay: String?
        get() = formatDepartmentDisplay(department, subDepartment)
}

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
            technicianUsername = prefs[KEY_USERNAME].orEmpty(),
            technicianName = prefs[KEY_TECH_NAME] ?: if (token.isBlank()) "" else "Teknisi",
            technicianRole = prefs[KEY_TECH_ROLE] ?: if (token.isBlank()) "" else "technician",
            avatarUrl = prefs[KEY_AVATAR_URL]?.takeIf { it.isNotBlank() },
            department = prefs[KEY_DEPARTMENT]?.takeIf { it.isNotBlank() },
            subDepartment = prefs[KEY_SUB_DEPARTMENT]?.takeIf { it.isNotBlank() },
            isActive = prefs[KEY_IS_ACTIVE] ?: 1,
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
        userId: Long = 0L,
        username: String = "",
        avatarUrl: String? = null,
        department: String? = null,
        subDepartment: String? = null,
        isActive: Int = 1
    ) {
        val trimmedToken = token.trim()
        cachedToken = trimmedToken.ifBlank { null }
        cachedUserId = userId
        dataStore.edit { prefs ->
            prefs[KEY_AUTH_TOKEN] = trimmedToken
            if (userId > 0L) prefs[KEY_USER_ID] = userId else prefs.remove(KEY_USER_ID)
            if (username.isNotBlank()) prefs[KEY_USERNAME] = username.trim() else prefs.remove(KEY_USERNAME)
            if (technicianName.isNotBlank()) prefs[KEY_TECH_NAME] = technicianName.trim()
            if (role.isNotBlank()) prefs[KEY_TECH_ROLE] = role.trim()
            val cleanAvatar = avatarUrl?.trim()?.takeIf { it.isNotEmpty() }
            if (cleanAvatar != null) prefs[KEY_AVATAR_URL] = cleanAvatar else prefs.remove(KEY_AVATAR_URL)
            val cleanDept = department?.trim()?.takeIf { it.isNotEmpty() }
            if (cleanDept != null) prefs[KEY_DEPARTMENT] = cleanDept else prefs.remove(KEY_DEPARTMENT)
            val cleanSubDept = subDepartment?.trim()?.takeIf { it.isNotEmpty() }
            if (cleanSubDept != null) prefs[KEY_SUB_DEPARTMENT] = cleanSubDept else prefs.remove(KEY_SUB_DEPARTMENT)
            prefs[KEY_IS_ACTIVE] = isActive
        }
    }

    suspend fun updateUserProfile(
        userId: Long? = null,
        username: String? = null,
        displayName: String? = null,
        role: String? = null,
        avatarUrl: String? = null,
        clearAvatarIfNull: Boolean = false,
        department: String? = null,
        subDepartment: String? = null,
        isActive: Int? = null
    ) {
        if (userId != null && userId > 0L) {
            cachedUserId = userId
        }
        dataStore.edit { prefs ->
            if (userId != null && userId > 0L) prefs[KEY_USER_ID] = userId
            if (!username.isNullOrBlank()) prefs[KEY_USERNAME] = username.trim()
            if (!displayName.isNullOrBlank()) prefs[KEY_TECH_NAME] = displayName.trim()
            if (!role.isNullOrBlank()) prefs[KEY_TECH_ROLE] = role.trim()
            val cleanAvatar = avatarUrl?.trim()?.takeIf { it.isNotEmpty() }
            if (cleanAvatar != null) {
                prefs[KEY_AVATAR_URL] = cleanAvatar
            } else if (clearAvatarIfNull) {
                prefs.remove(KEY_AVATAR_URL)
            }
            if (department != null) {
                val clean = department.trim()
                if (clean.isNotEmpty()) prefs[KEY_DEPARTMENT] = clean else prefs.remove(KEY_DEPARTMENT)
            }
            if (subDepartment != null) {
                val clean = subDepartment.trim()
                if (clean.isNotEmpty()) prefs[KEY_SUB_DEPARTMENT] = clean else prefs.remove(KEY_SUB_DEPARTMENT)
            }
            if (isActive != null) {
                prefs[KEY_IS_ACTIVE] = isActive
            }
        }
    }

    suspend fun updateAvatarUrl(avatarUrl: String?) {
        dataStore.edit { prefs ->
            val clean = avatarUrl?.trim()?.takeIf { it.isNotEmpty() }
            if (clean != null) {
                prefs[KEY_AVATAR_URL] = clean
            } else {
                prefs.remove(KEY_AVATAR_URL)
            }
        }
    }

    suspend fun clearAuthToken() {
        cachedToken = null
        cachedUserId = 0L
        dataStore.edit { prefs ->
            prefs.remove(KEY_AUTH_TOKEN)
            prefs.remove(KEY_USER_ID)
            prefs.remove(KEY_USERNAME)
            prefs.remove(KEY_TECH_NAME)
            prefs.remove(KEY_TECH_ROLE)
            prefs.remove(KEY_AVATAR_URL)
            prefs.remove(KEY_DEPARTMENT)
            prefs.remove(KEY_SUB_DEPARTMENT)
            prefs.remove(KEY_IS_ACTIVE)
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
        private val KEY_USERNAME = stringPreferencesKey("technician_username")
        private val KEY_SERVER_URL = stringPreferencesKey("server_base_url")
        private val KEY_TECH_NAME = stringPreferencesKey("technician_name")
        private val KEY_TECH_ROLE = stringPreferencesKey("technician_role")
        private val KEY_AVATAR_URL = stringPreferencesKey("technician_avatar_url")
        private val KEY_DEPARTMENT = stringPreferencesKey("technician_department")
        private val KEY_SUB_DEPARTMENT = stringPreferencesKey("technician_sub_department")
        private val KEY_IS_ACTIVE = intPreferencesKey("technician_is_active")
        private val KEY_THEME_PREF = stringPreferencesKey("theme_preference")
        private val KEY_SHIFT_STATUS = stringPreferencesKey("shift_status")
        private val KEY_LAST_SYNCED_MILLIS = longPreferencesKey("last_synced_at_millis")
    }
}
