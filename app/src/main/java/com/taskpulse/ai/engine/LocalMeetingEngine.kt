package com.taskpulse.ai.engine

import com.taskpulse.ai.models.ItemDiscussed
import com.taskpulse.ai.models.Meeting
import com.taskpulse.ai.models.Segment
import com.taskpulse.ai.models.TaskItem
import java.text.SimpleDateFormat
import java.util.*

object LocalMeetingEngine {

    fun processMeetingLocally(
        title: String,
        transcriptRaw: String,
        audioFilePath: String,
        language: String = "English",
        serverHost: String? = null
    ): Pair<Meeting, List<TaskItem>> {
        val meetingId = UUID.randomUUID().toString().substring(0, 8)
        val createdAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        val transcript = if (transcriptRaw.trim().isEmpty()) {
            "Audio meeting recorded locally on Android device."
        } else {
            transcriptRaw.trim()
        }

        val summary = generateLocalSummary(title, transcript)
        val itemsDiscussed = extractLocalItemsDiscussed(transcript)
        val tasks = extractLocalTasks(meetingId, transcript, language)

        val segments = listOf(
            Segment(start = "00:00", end = "End", speaker = "Speaker 1", text = transcript)
        )

        val meeting = Meeting(
            id = meetingId,
            title = title,
            language = language,
            createdAt = createdAt,
            timestamp = System.currentTimeMillis() / 1000.0,
            audioUrl = audioFilePath,
            audioFilename = audioFilePath.substringAfterLast('/'),
            transcript = transcript,
            segments = segments,
            summary = summary,
            itemsDiscussed = itemsDiscussed,
            taskCount = tasks.size
        )

        return Pair(meeting, tasks)
    }

    private fun generateLocalSummary(title: String, transcript: String): String {
        val sentences = transcript.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
        if (sentences.isEmpty()) {
            return "Local recorded meeting session '$title'. No distinct speech detected."
        }

        val keySentences = sentences.take(3).joinToString(" ")
        return "Meeting Session '$title' Overview: $keySentences"
    }

    private fun extractLocalItemsDiscussed(transcript: String): List<ItemDiscussed> {
        val items = mutableListOf<ItemDiscussed>()
        val sentences = transcript.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }

        if (sentences.isNotEmpty()) {
            items.add(ItemDiscussed("Key Discussion", sentences.first(), "Overview"))
        }

        val actionSentences = sentences.filter { s ->
            s.contains("will", true) || s.contains("need to", true) || s.contains("must", true) || s.contains("decide", true)
        }

        if (actionSentences.isNotEmpty()) {
            items.add(ItemDiscussed("Decisions & Next Steps", actionSentences.take(2).joinToString("; "), "Decisions"))
        } else {
            items.add(ItemDiscussed("General Notes", "Recorded meeting transcript saved locally on phone.", "Notes"))
        }

        return items
    }

    private fun extractLocalTasks(meetingId: String, transcript: String, language: String): List<TaskItem> {
        val tasks = mutableListOf<TaskItem>()
        val sentences = transcript.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }

        val taskTriggers = listOf("will", "need to", "must", "should", "action item", "todo", "assign", "follow up", "due", "prepare", "send", "fix", "update")

        var count = 1
        for (sentence in sentences) {
            val lower = sentence.lowercase()
            if (taskTriggers.any { lower.contains(it) }) {
                val priority = when {
                    lower.contains("urgent") || lower.contains("asap") || lower.contains("today") -> "High"
                    lower.contains("low") || lower.contains("next week") -> "Low"
                    else -> "Medium"
                }

                val assignee = when {
                    lower.contains("john") -> "John"
                    lower.contains("alex") -> "Alex"
                    lower.contains("sarah") -> "Sarah"
                    lower.contains("i will") || lower.contains("i'll") -> "Me"
                    else -> "Team"
                }

                val dueDate = when {
                    lower.contains("today") -> "Today"
                    lower.contains("tomorrow") -> "Tomorrow"
                    lower.contains("friday") -> "This Friday"
                    lower.contains("next week") -> "Next Week"
                    else -> "In 3 Days"
                }

                val cleanTitle = sentence.take(60).trim()

                tasks.add(
                    TaskItem(
                        id = UUID.randomUUID().toString().substring(0, 8),
                        meetingId = meetingId,
                        title = "Task $count: $cleanTitle",
                        description = sentence,
                        assignee = assignee,
                        priority = priority,
                        category = "Action Item",
                        dueDate = dueDate,
                        status = "todo",
                        language = language
                    )
                )
                count++
            }
        }

        if (tasks.isEmpty()) {
            tasks.add(
                TaskItem(
                    id = UUID.randomUUID().toString().substring(0, 8),
                    meetingId = meetingId,
                    title = "Review Recorded Audio",
                    description = "Listen to meeting recording and extract detailed notes.",
                    assignee = "Me",
                    priority = "Medium",
                    category = "Follow-up",
                    dueDate = "Tomorrow",
                    status = "todo",
                    language = language
                )
            )
        }

        return tasks
    }
}
