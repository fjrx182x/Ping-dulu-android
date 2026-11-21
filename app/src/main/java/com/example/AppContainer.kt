package com.example

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.AppPreferences
import com.example.data.remote.NetworkModule
import com.example.data.remote.PingDuluApiService
import com.example.data.remote.SseEventClient
import com.example.data.repository.TaskRepositoryImpl
import com.example.domain.repository.TaskRepository
import com.example.util.AndroidNetworkConnectivityMonitor
import com.example.util.ConnectivityMonitor
import com.example.util.NotificationHelper
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient

/**
 * Application-scoped dependency container avoiding Activity memory leaks
 * across configuration changes and cleanly decoupling Repository from ViewModel.
 */
class AppContainer private constructor(appContext: Context) {

    val appDatabase: AppDatabase = AppDatabase.getInstance(appContext)
    val appPreferences: AppPreferences = AppPreferences(appContext)
    val connectivityMonitor: ConnectivityMonitor = AndroidNetworkConnectivityMonitor(appContext)
    val notificationHelper: NotificationHelper = NotificationHelper(appContext).apply {
        ensureChannelCreated()
    }
    val moshi: Moshi = NetworkModule.createMoshi()
    val okHttpClient: OkHttpClient = NetworkModule.createOkHttpClient(appPreferences)
    val sseEventClient: SseEventClient = SseEventClient(okHttpClient)

    private val apiService: PingDuluApiService = NetworkModule.createApiService(
        baseUrl = PingDuluApiService.FIXED_BASE_URL,
        okHttpClient = okHttpClient,
        moshi = moshi
    )

    fun resolveApiService(): PingDuluApiService = apiService

    val taskRepository: TaskRepository = TaskRepositoryImpl(
        taskDao = appDatabase.taskDao(),
        apiServiceProvider = ::resolveApiService,
        appPreferences = appPreferences,
        database = appDatabase
    )

    companion object {
        @Volatile
        private var instance: AppContainer? = null

        fun getInstance(context: Context): AppContainer {
            return instance ?: synchronized(this) {
                instance ?: AppContainer(context.applicationContext).also { instance = it }
            }
        }
    }
}
