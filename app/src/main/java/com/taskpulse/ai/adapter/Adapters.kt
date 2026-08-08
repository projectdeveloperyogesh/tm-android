package com.taskpulse.ai.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.taskpulse.ai.R
import com.taskpulse.ai.models.BackgroundJob
import com.taskpulse.ai.models.Meeting
import com.taskpulse.ai.models.TaskItem

class MeetingAdapter(
    private var meetings: List<Meeting>,
    private val onMeetingClick: (Meeting) -> Unit,
    private val onPlayClick: (Meeting) -> Unit = {},
    private val onDeleteClick: (Meeting) -> Unit = {}
) : RecyclerView.Adapter<MeetingAdapter.MeetingViewHolder>() {

    class MeetingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleText: TextView = itemView.findViewById(R.id.textTitle)
        val dateText: TextView = itemView.findViewById(R.id.textDate)
        val summaryText: TextView = itemView.findViewById(R.id.textSummary)
        val tasksBadge: TextView = itemView.findViewById(R.id.textTaskBadge)
        val btnPlay: Button = itemView.findViewById(R.id.btnPlayAudio)
        val btnDelete: Button = itemView.findViewById(R.id.btnDeleteMeeting)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MeetingViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_meeting, parent, false)
        return MeetingViewHolder(view)
    }

    override fun onBindViewHolder(holder: MeetingViewHolder, position: Int) {
        val meeting = meetings[position]
        holder.titleText.text = meeting.title
        holder.dateText.text = meeting.createdAt ?: ""
        holder.summaryText.text = meeting.summary ?: "No summary available."
        holder.tasksBadge.text = "${meeting.taskCount} Tasks"
        holder.itemView.setOnClickListener { onMeetingClick(meeting) }
        holder.btnPlay.setOnClickListener { onPlayClick(meeting) }
        holder.btnDelete.setOnClickListener { onDeleteClick(meeting) }
    }

    override fun getItemCount(): Int = meetings.size

    fun updateData(newMeetings: List<Meeting>) {
        meetings = newMeetings
        notifyDataSetChanged()
    }
}

class TaskAdapter(
    private var tasks: List<TaskItem>
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleText: TextView = itemView.findViewById(R.id.textTaskTitle)
        val descText: TextView = itemView.findViewById(R.id.textTaskDesc)
        val priorityText: TextView = itemView.findViewById(R.id.textPriority)
        val assigneeText: TextView = itemView.findViewById(R.id.textAssignee)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = tasks[position]
        holder.titleText.text = task.title
        holder.descText.text = task.description ?: "N/A"
        holder.priorityText.text = task.priority ?: "Medium"
        holder.assigneeText.text = "Assigned: ${task.assignee ?: "Unassigned"}"
    }

    override fun getItemCount(): Int = tasks.size

    fun updateData(newTasks: List<TaskItem>) {
        tasks = newTasks
        notifyDataSetChanged()
    }
}

class JobAdapter(
    private var jobs: List<BackgroundJob>
) : RecyclerView.Adapter<JobAdapter.JobViewHolder>() {

    class JobViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleText: TextView = itemView.findViewById(R.id.textJobTitle)
        val stageBadge: TextView = itemView.findViewById(R.id.textJobStage)
        val msgText: TextView = itemView.findViewById(R.id.textJobMessage)
        val progressBar: ProgressBar = itemView.findViewById(R.id.progressJob)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): JobViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_job, parent, false)
        return JobViewHolder(view)
    }

    override fun onBindViewHolder(holder: JobViewHolder, position: Int) {
        val job = jobs[position]
        holder.titleText.text = job.meetingTitle ?: "Meeting Session"
        holder.stageBadge.text = (job.stage ?: "PROCESSING").uppercase()
        holder.msgText.text = job.statusMessage ?: ""
        holder.progressBar.progress = job.progress
    }

    override fun getItemCount(): Int = jobs.size

    fun updateData(newJobs: List<BackgroundJob>) {
        jobs = newJobs
        notifyDataSetChanged()
    }
}

class AiLogAdapter(
    private var logs: List<com.taskpulse.ai.models.AiLog>,
    private val onLogClick: (com.taskpulse.ai.models.AiLog) -> Unit
) : RecyclerView.Adapter<AiLogAdapter.AiLogViewHolder>() {

    class AiLogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleText: TextView = itemView.findViewById(R.id.textJobTitle)
        val stageBadge: TextView = itemView.findViewById(R.id.textJobStage)
        val msgText: TextView = itemView.findViewById(R.id.textJobMessage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AiLogViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_job, parent, false)
        return AiLogViewHolder(view)
    }

    override fun onBindViewHolder(holder: AiLogViewHolder, position: Int) {
        val log = logs[position]
        holder.titleText.text = "${log.provider ?: "AI Engine"} • ${log.meetingTitle ?: "Session"}"
        holder.stageBadge.text = (log.status ?: "SUCCESS").uppercase()
        holder.msgText.text = "Target: ${log.endpoint ?: "http://localhost:3005/api/v1/ai/chat"} (${log.durationMs ?: 0} ms)"
        holder.itemView.setOnClickListener { onLogClick(log) }
    }

    override fun getItemCount(): Int = logs.size

    fun updateData(newLogs: List<com.taskpulse.ai.models.AiLog>) {
        logs = newLogs
        notifyDataSetChanged()
    }
}
