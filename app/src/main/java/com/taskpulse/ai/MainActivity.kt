package com.taskpulse.ai

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.taskpulse.ai.adapter.JobAdapter
import com.taskpulse.ai.adapter.MeetingAdapter
import com.taskpulse.ai.adapter.TaskAdapter
import com.taskpulse.ai.api.ApiClient
import com.taskpulse.ai.databinding.ActivityMainBinding
import com.taskpulse.ai.models.BackgroundJob
import com.taskpulse.ai.models.Meeting
import com.taskpulse.ai.models.TaskItem
import com.taskpulse.ai.recorder.AudioRecorderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var recorderManager: AudioRecorderManager

    private var meetingAdapter = MeetingAdapter(emptyList()) { meeting -> showMeetingDetails(meeting) }
    private var taskAdapter = TaskAdapter(emptyList())
    private var jobAdapter = JobAdapter(emptyList())

    private var currentTab = 0 // 0: Meetings, 1: Tasks, 2: Jobs
    private var isRecording = false
    private var secondsElapsed = 0
    private val timerHandler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null

    private val processedJobIds = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        recorderManager = AudioRecorderManager(this)

        setupUI()
        checkPermissions()
        startJobsPolling()
        loadData()
    }

    private fun setupUI() {
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = meetingAdapter

        binding.btnConnectServer.setOnClickListener {
            val host = binding.editServerHost.text.toString().trim()
            if (host.isNotEmpty()) {
                ApiClient.updateBaseUrl(host)
                Toast.makeText(this, "Connected to: ${ApiClient.getBaseUrl()}", Toast.LENGTH_SHORT).show()
                loadData()
            }
        }

        binding.btnStartRecord.setOnClickListener {
            if (!isRecording) startRecording()
        }

        binding.btnStopRecord.setOnClickListener {
            if (isRecording) stopRecordingAndProcess()
        }

        binding.btnJobsMonitor.setOnClickListener {
            binding.tabLayout.getTabAt(2)?.select()
        }

        binding.swipeRefresh.setOnRefreshListener {
            loadData()
            binding.swipeRefresh.isRefreshing = false
        }

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentTab = tab?.position ?: 0
                when (currentTab) {
                    0 -> binding.recyclerView.adapter = meetingAdapter
                    1 -> binding.recyclerView.adapter = taskAdapter
                    2 -> binding.recyclerView.adapter = jobAdapter
                }
                loadData()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE
        )
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 101)
        }
    }

    private fun startRecording() {
        val file = recorderManager.startRecording { level ->
            binding.progressMicLevel.progress = level
            binding.textMicPercent.text = "$level%"
        }

        if (file == null) {
            Toast.makeText(this, "Failed to initialize microphone", Toast.LENGTH_SHORT).show()
            return
        }

        isRecording = true
        binding.btnStartRecord.isEnabled = false
        binding.btnStopRecord.isEnabled = true
        binding.textStatusPill.text = "Recording Live"
        binding.textStatusPill.setTextColor(ContextCompat.getColor(this, R.color.accent_emerald))

        secondsElapsed = 0
        timerRunnable = object : Runnable {
            override fun run() {
                secondsElapsed++
                val hrs = String.format("%02d", secondsElapsed / 3600)
                val mins = String.format("%02d", (secondsElapsed % 3600) / 60)
                val scs = String.format("%02d", secondsElapsed % 60)
                binding.textTimer.text = "$hrs:$mins:$scs"
                timerHandler.postDelayed(this, 1000)
            }
        }
        timerHandler.post(timerRunnable!!)
    }

    private fun stopRecordingAndProcess() {
        timerRunnable?.let { timerHandler.removeCallbacks(it) }
        val recordedFile = recorderManager.stopRecording()
        isRecording = false

        binding.btnStartRecord.isEnabled = true
        binding.btnStopRecord.isEnabled = false
        binding.textStatusPill.text = "Uploading to Background Engine..."
        binding.progressMicLevel.progress = 0
        binding.textMicPercent.text = "0%"

        if (recordedFile == null || !recordedFile.exists()) {
            Toast.makeText(this, "No recorded audio found", Toast.LENGTH_SHORT).show()
            binding.textStatusPill.text = "Standby"
            return
        }

        val title = binding.editMeetingTitle.text.toString().ifEmpty { "Android Live Meeting" }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val requestFile = recordedFile.asRequestBody("audio/wav".toMediaTypeOrNull())
                val body = MultipartBody.Part.createFormData("file", recordedFile.name, requestFile)
                val titleBody = title.toRequestBody("text/plain".toMediaTypeOrNull())
                val langBody = "English".toRequestBody("text/plain".toMediaTypeOrNull())

                val response = ApiClient.service.uploadRecordedMeeting(body, titleBody, langBody)
                withContext(Dispatchers.Main) {
                    binding.textStatusPill.text = "Standby"
                    binding.textTimer.text = "00:00:00"
                    if (response.isSuccessful) {
                        Toast.makeText(this@MainActivity, "Recording dispatched to background processing!", Toast.LENGTH_LONG).show()
                        binding.tabLayout.getTabAt(2)?.select()
                        loadData()
                    } else {
                        Toast.makeText(this@MainActivity, "Upload failed: ${response.message()}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.textStatusPill.text = "Standby"
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun startJobsPolling() {
        lifecycleScope.launch(Dispatchers.IO) {
            while (true) {
                try {
                    val response = ApiClient.service.getJobs()
                    if (response.isSuccessful && response.body() != null) {
                        val jobs = response.body()!!
                        var hasNewCompletion = false
                        for (job in jobs) {
                            if (job.stage == "completed" && !processedJobIds.contains(job.id)) {
                                processedJobIds.add(job.id)
                                hasNewCompletion = true
                            }
                        }
                        withContext(Dispatchers.Main) {
                            jobAdapter.updateData(jobs)
                            if (hasNewCompletion) {
                                Toast.makeText(this@MainActivity, "🎉 Meeting processing completed & saved!", Toast.LENGTH_SHORT).show()
                                loadMeetings()
                                loadTasks()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Jobs poll error: ${e.message}")
                }
                delay(2000)
            }
        }
    }

    private fun loadData() {
        when (currentTab) {
            0 -> loadMeetings()
            1 -> loadTasks()
            2 -> fetchJobsOnce()
        }
    }

    private fun loadMeetings() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val res = ApiClient.service.getMeetings()
                if (res.isSuccessful && res.body() != null) {
                    withContext(Dispatchers.Main) {
                        meetingAdapter.updateData(res.body()!!)
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error loading meetings: ${e.message}")
            }
        }
    }

    private fun loadTasks() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val res = ApiClient.service.getTasks()
                if (res.isSuccessful && res.body() != null) {
                    withContext(Dispatchers.Main) {
                        taskAdapter.updateData(res.body()!!)
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error loading tasks: ${e.message}")
            }
        }
    }

    private fun fetchJobsOnce() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val res = ApiClient.service.getJobs()
                if (res.isSuccessful && res.body() != null) {
                    withContext(Dispatchers.Main) {
                        jobAdapter.updateData(res.body()!!)
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error fetching jobs: ${e.message}")
            }
        }
    }

    private fun showMeetingDetails(meeting: Meeting) {
        Toast.makeText(this, "Session: ${meeting.title}\nTasks Extracted: ${meeting.taskCount}", Toast.LENGTH_LONG).show()
    }
}
