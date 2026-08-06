package com.taskpulse.ai.models

import com.google.gson.annotations.SerializedName

data class Meeting(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("language") val language: String? = "English",
    @SerializedName("created_at") val createdAt: String? = "",
    @SerializedName("timestamp") val timestamp: Double? = 0.0,
    @SerializedName("audio_url") val audioUrl: String? = "",
    @SerializedName("audio_filename") val audioFilename: String? = "",
    @SerializedName("transcript") val transcript: String? = "",
    @SerializedName("segments") val segments: List<Segment>? = emptyList(),
    @SerializedName("summary") val summary: String? = "",
    @SerializedName("items_discussed") val itemsDiscussed: List<ItemDiscussed>? = emptyList(),
    @SerializedName("task_count") val taskCount: Int = 0
)

data class Segment(
    @SerializedName("start") val start: String? = "00:00",
    @SerializedName("end") val end: String? = "End",
    @SerializedName("speaker") val speaker: String? = "Speaker",
    @SerializedName("text") val text: String? = ""
)

data class ItemDiscussed(
    @SerializedName("topic") val topic: String? = "",
    @SerializedName("details") val details: String? = "",
    @SerializedName("category") val category: String? = "General"
)

data class TaskItem(
    @SerializedName("id") val id: String,
    @SerializedName("meeting_id") val meetingId: String? = "",
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String? = "",
    @SerializedName("assignee") val assignee: String? = "Unassigned",
    @SerializedName("priority") val priority: String? = "Medium",
    @SerializedName("category") val category: String? = "General",
    @SerializedName("due_date") val dueDate: String? = "N/A",
    @SerializedName("status") val status: String? = "todo",
    @SerializedName("language") val language: String? = "English"
)

data class BackgroundJob(
    @SerializedName("id") val id: String,
    @SerializedName("meeting_title") val meetingTitle: String? = "",
    @SerializedName("target_language") val targetLanguage: String? = "English",
    @SerializedName("stage") val stage: String? = "transcribing", // transcribing | analyzing | saving | completed | error
    @SerializedName("status_message") val statusMessage: String? = "",
    @SerializedName("progress") val progress: Int = 0,
    @SerializedName("started_at") val startedAt: Double? = 0.0,
    @SerializedName("finished_at") val finishedAt: Double? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("meeting_id") val meetingId: String? = null
)

data class StopWebResponse(
    @SerializedName("status") val status: String,
    @SerializedName("message") val message: String? = null,
    @SerializedName("job") val job: BackgroundJob? = null,
    @SerializedName("meeting") val meeting: Meeting? = null,
    @SerializedName("tasks") val tasks: List<TaskItem>? = emptyList()
)
