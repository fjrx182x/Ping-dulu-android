package com.example.data.remote

import com.example.data.remote.dto.AddCommentRequestDto
import com.example.data.remote.dto.CalendarAgendaResponseDto
import com.example.data.remote.dto.ChecklistStepToggleDto
import com.example.data.remote.dto.ClaimTaskResponseDto
import com.example.data.remote.dto.CreateTaskRequestDto
import com.example.data.remote.dto.CreateTaskResponseDto
import com.example.data.remote.dto.HolidayResponseDto
import com.example.data.remote.dto.LoginRequestDto
import com.example.data.remote.dto.LoginResponseDto
import com.example.data.remote.dto.StageUpdateDto
import com.example.data.remote.dto.TaskPatchRequestDto
import com.example.data.remote.dto.TaskResponseDto
import com.example.data.remote.dto.TimerActionRequestDto
import com.example.data.remote.dto.WorkTaskResponseDto
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface PingDuluApiService {

    @POST("api/auth/login")
    suspend fun login(
        @Body body: LoginRequestDto
    ): Response<LoginResponseDto>

    @GET("api/users")
    suspend fun getUsersRaw(): Response<ResponseBody>

    @GET("api/labels")
    suspend fun getLabelsRaw(): Response<ResponseBody>

    @GET("api/tasks")
    suspend fun getTasks(): Response<TaskResponseDto>

    @GET("api/tasks/{id}")
    suspend fun getTaskDetailRaw(
        @Path("id") taskId: Long
    ): Response<ResponseBody>

    @POST("api/tasks")
    suspend fun createTask(
        @Body body: CreateTaskRequestDto
    ): Response<CreateTaskResponseDto>

    @PATCH("api/tasks/{id}")
    suspend fun patchTask(
        @Path("id") id: Long,
        @Body body: TaskPatchRequestDto
    ): Response<ResponseBody>

    @POST("api/tasks/{id}/stage")
    suspend fun updateStage(
        @Path("id") id: Long,
        @Body body: StageUpdateDto
    ): Response<Unit>

    @POST("api/tasks/{id}/timer")
    suspend fun updateTimer(
        @Path("id") id: Long,
        @Body body: TimerActionRequestDto
    ): Response<Unit>

    @PATCH("api/tasks/{id}/checklist/{stepId}")
    suspend fun toggleChecklistStep(
        @Path("id") id: Long,
        @Path("stepId") stepId: Long,
        @Body body: ChecklistStepToggleDto
    ): Response<Unit>

    @GET("api/tasks/{id}/attachments")
    suspend fun getAttachmentsRaw(
        @Path("id") taskId: Long
    ): Response<ResponseBody>

    @Multipart
    @POST("api/tasks/{id}/attachments")
    suspend fun uploadAttachment(
        @Path("id") taskId: Long,
        @Part file: MultipartBody.Part
    ): Response<ResponseBody>

    @GET("api/tasks/{id}/comments")
    suspend fun getCommentsRaw(
        @Path("id") taskId: Long
    ): Response<ResponseBody>

    @POST("api/tasks/{id}/comments")
    suspend fun postComment(
        @Path("id") taskId: Long,
        @Body body: AddCommentRequestDto
    ): Response<ResponseBody>

    @GET("api/tasks/{id}/activities")
    suspend fun getActivitiesRaw(
        @Path("id") taskId: Long
    ): Response<ResponseBody>

    @GET("api/tasks/{id}/activity")
    suspend fun getActivitySingularRaw(
        @Path("id") taskId: Long
    ): Response<ResponseBody>

    @GET("api/tasks/{id}/logs")
    suspend fun getTaskLogsRaw(
        @Path("id") taskId: Long
    ): Response<ResponseBody>

    // Patch Ke-2: Kerjakan & Ambil Task (no @Body so OkHttp never sends Content-Type: application/json on empty body)
    @POST("api/tasks/{id}/work")
    suspend fun workTask(
        @Path("id") taskId: Long
    ): Response<WorkTaskResponseDto>

    @POST("api/tasks/{id}/claim")
    suspend fun claimTask(
        @Path("id") taskId: Long
    ): Response<ClaimTaskResponseDto>

    @DELETE("api/tasks/{id}")
    suspend fun deleteTask(
        @Path("id") taskId: Long
    ): Response<ResponseBody>

    // Patch Ke-2: Kalender & Hari Libur Nasional Indonesia (SKB 3 Menteri)
    @GET("api/holidays")
    suspend fun getHolidays(
        @Query("year") year: Int
    ): Response<HolidayResponseDto>

    @GET("api/calendar")
    suspend fun getCalendar(
        @Query("year") year: Int,
        @Query("month") month: Int
    ): Response<CalendarAgendaResponseDto>

    companion object {
        const val FIXED_BASE_URL = "https://ping.fjrx.my.id/"
    }
}
