package com.taskpulse.ai.storage

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.taskpulse.ai.models.Meeting
import com.taskpulse.ai.models.TaskItem
import java.io.File

class LocalDataManager(private val context: Context) {

    private val gson = Gson()
    private val dataDir = File(context.filesDir, "data").apply { if (!exists()) mkdirs() }
    private val meetingsFile = File(dataDir, "meetings.json")
    private val tasksFile = File(dataDir, "tasks.json")

    fun getMeetings(): List<Meeting> {
        return try {
            if (!meetingsFile.exists()) return emptyList()
            val json = meetingsFile.readText()
            val type = object : TypeToken<List<Meeting>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveMeeting(meeting: Meeting) {
        val list = getMeetings().toMutableList()
        list.removeAll { it.id == meeting.id }
        list.add(0, meeting)
        val json = gson.toJson(list)
        meetingsFile.writeText(json)
    }

    fun getTasks(): List<TaskItem> {
        return try {
            if (!tasksFile.exists()) return emptyList()
            val json = tasksFile.readText()
            val type = object : TypeToken<List<TaskItem>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveTasks(newTasks: List<TaskItem>) {
        val list = getTasks().toMutableList()
        newTasks.forEach { task ->
            list.removeAll { it.id == task.id }
            list.add(0, task)
        }
        val json = gson.toJson(list)
        tasksFile.writeText(json)
    }

    fun deleteMeeting(meetingId: String) {
        val meetings = getMeetings().filter { it.id != meetingId }
        meetingsFile.writeText(gson.toJson(meetings))

        val tasks = getTasks().filter { it.meetingId != meetingId }
        tasksFile.writeText(gson.toJson(tasks))
    }
}
