package com.taskpulse.ai.api

import com.taskpulse.ai.models.BackgroundJob
import com.taskpulse.ai.models.Meeting
import com.taskpulse.ai.models.StopWebResponse
import com.taskpulse.ai.models.TaskItem
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface TaskPulseApiService {

    @GET("api/meetings")
    suspend fun getMeetings(): Response<List<Meeting>>

    @GET("api/meetings/{id}")
    suspend fun getMeetingDetails(@Path("id") id: String): Response<Meeting>

    @GET("api/tasks")
    suspend fun getTasks(): Response<List<TaskItem>>

    @GET("api/jobs")
    suspend fun getJobs(): Response<List<BackgroundJob>>

    @Multipart
    @POST("api/record/stop_web")
    suspend fun uploadRecordedMeeting(
        @Part file: MultipartBody.Part,
        @Part("meeting_title") meetingTitle: RequestBody,
        @Part("target_language") targetLanguage: RequestBody
    ): Response<StopWebResponse>

    @Multipart
    @POST("api/upload")
    suspend fun uploadMediaFile(
        @Part file: MultipartBody.Part,
        @Part("meeting_title") meetingTitle: RequestBody,
        @Part("target_language") targetLanguage: RequestBody
    ): Response<StopWebResponse>

    @Multipart
    @POST("api/android/upload")
    suspend fun uploadAndroidRecording(
        @Part file: MultipartBody.Part,
        @Part("meeting_title") meetingTitle: RequestBody,
        @Part("target_language") targetLanguage: RequestBody,
        @Part("live_transcript") liveTranscript: RequestBody
    ): Response<StopWebResponse>

    @GET("api/ai/logs")
    suspend fun getAiLogs(): Response<List<com.taskpulse.ai.models.AiLog>>

    @DELETE("api/ai/logs")
    suspend fun clearAiLogs(): Response<Void>

    @POST("api/meetings/{id}/reanalyze")
    suspend fun reanalyzeMeeting(
        @Path("id") id: String,
        @Body body: Map<String, String>
    ): Response<StopWebResponse>

    @DELETE("api/meetings/{id}")
    suspend fun deleteMeeting(@Path("id") id: String): Response<Void>

    @PUT("api/tasks/{id}")
    suspend fun updateTask(
        @Path("id") id: String,
        @Body task: TaskItem
    ): Response<TaskItem>

    @DELETE("api/tasks/{id}")
    suspend fun deleteTask(@Path("id") id: String): Response<Void>
}
