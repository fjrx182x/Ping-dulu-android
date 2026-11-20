package com.example.data.remote

import com.example.data.remote.dto.ChecklistSyncDto
import com.example.data.remote.dto.CreateTaskRequestDto
import com.example.data.remote.dto.LoginRequestDto
import com.example.data.remote.dto.LoginResponseDto
import com.example.data.remote.dto.StageUpdateDto
import com.example.data.remote.dto.TaskDto
import com.example.data.remote.dto.TaskResponseDto
import com.example.data.remote.dto.TimerUpdateDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface PingDuluApiService {

    @POST("api/auth/login")
    suspend fun login(
        @Body body: LoginRequestDto
    ): Response<LoginResponseDto>

    @GET("api/tasks")
    suspend fun getTasks(): Response<TaskResponseDto>

    @POST("api/tasks")
    suspend fun createTask(
        @Body body: CreateTaskRequestDto
    ): Response<TaskDto>

    @PATCH("api/tasks/{id}/stage")
    suspend fun updateStage(
        @Path("id") id: Long,
        @Body body: StageUpdateDto
    ): Response<Unit>

    @POST("api/tasks/{id}/timer")
    suspend fun updateTimer(
        @Path("id") id: Long,
        @Body body: TimerUpdateDto
    ): Response<Unit>

    @PUT("api/tasks/{id}/checklist")
    suspend fun syncChecklist(
        @Path("id") id: Long,
        @Body body: ChecklistSyncDto
    ): Response<Unit>
}
