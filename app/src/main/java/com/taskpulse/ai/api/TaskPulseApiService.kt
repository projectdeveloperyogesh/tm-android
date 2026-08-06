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
}
