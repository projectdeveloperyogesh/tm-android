package com.taskpulse.ai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.taskpulse.ai.adapter.JobAdapter
import com.taskpulse.ai.adapter.MeetingAdapter
import com.taskpulse.ai.adapter.TaskAdapter
import com.taskpulse.ai.api.ApiClient
import com.taskpulse.ai.databinding.ActivityMainBinding
import com.taskpulse.ai.engine.LocalMeetingEngine
import com.taskpulse.ai.models.BackgroundJob
import com.taskpulse.ai.models.Meeting
import com.taskpulse.ai.models.TaskItem
import com.taskpulse.ai.recorder.AudioRecorderManager
import com.taskpulse.ai.storage.LocalDataManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var recorderManager: AudioRecorderManager
    private lateinit var localDataManager: LocalDataManager

    private var speechRecognizer: SpeechRecognizer? = null
    private val liveTranscriptBuilder = StringBuilder()

    private var meetingAdapter = MeetingAdapter(
        emptyList(),
        onMeetingClick = { meeting -> showMeetingDetailsDialog(meeting) },
        onPlayClick = { meeting -> playMeetingAudio(meeting) },
        onDeleteClick = { meeting -> confirmDeleteMeeting(meeting) }
    )
    private var taskAdapter = TaskAdapter(emptyList())
    private var jobAdapter = JobAdapter(emptyList())
    private var aiLogAdapter = com.taskpulse.ai.adapter.AiLogAdapter(emptyList()) { log -> showAiLogDetailDialog(log) }

    private var currentTab = 0 // 0: Meetings, 1: Tasks, 2: Jobs, 3: AI Logs Audit
    private var isRecording = false
    private var secondsElapsed = 0
    private val timerHandler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null

    private var isStandaloneMode = true
    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        recorderManager = AudioRecorderManager(this)
        localDataManager = LocalDataManager(this)

        setupUI()
        checkPermissions()
        initSpeechRecognizer()
        loadLocalData()
    }

    private fun setupUI() {
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = meetingAdapter

        binding.editYogeshChatEndpoint.setText(localDataManager.getYogeshChatEndpoint())

        binding.btnConnectServer.setOnClickListener {
            val host = binding.editServerHost.text.toString().trim()
            val ycEndpoint = binding.editYogeshChatEndpoint.text.toString().trim()
            if (ycEndpoint.isNotEmpty()) {
                localDataManager.saveYogeshChatEndpoint(ycEndpoint)
            }
            if (host.isNotEmpty()) {
                ApiClient.updateBaseUrl(host)
                isStandaloneMode = false
                Toast.makeText(this, "Connected Remote: ${ApiClient.getBaseUrl()}\nYogesh AI API: ${localDataManager.getYogeshChatEndpoint()}", Toast.LENGTH_SHORT).show()
            } else {
                isStandaloneMode = true
                Toast.makeText(this, "Configuration Saved!\nYogesh AI API: ${localDataManager.getYogeshChatEndpoint()}", Toast.LENGTH_SHORT).show()
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
            loadLocalData()
            binding.swipeRefresh.isRefreshing = false
        }

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentTab = tab?.position ?: 0
                when (currentTab) {
                    0 -> binding.recyclerView.adapter = meetingAdapter
                    1 -> binding.recyclerView.adapter = taskAdapter
                    2 -> binding.recyclerView.adapter = jobAdapter
                    3 -> binding.recyclerView.adapter = aiLogAdapter
                }
                loadLocalData()
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

    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    runOnUiThread {
                        binding.textStatusPill.text = "🎙️ Listening..."
                        binding.textStatusPill.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.accent_emerald))
                    }
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    if (isRecording) restartSpeechRecognition()
                }
                override fun onError(error: Int) {
                    val errorMsg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                        SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                        SpeechRecognizer.ERROR_CLIENT -> "Client error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "No permission"
                        SpeechRecognizer.ERROR_SERVER -> "Server error"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                        else -> "Error code: $error"
                    }
                    Log.w("SpeechRecognizer", "Error: $errorMsg ($error)")
                    if (isRecording) {
                        runOnUiThread {
                            binding.textStatusPill.text = "🎙️ Re-listening... ($errorMsg)"
                        }
                        restartSpeechRecognition()
                    }
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        liveTranscriptBuilder.append(matches[0]).append(" ")
                        runOnUiThread {
                            val preview = liveTranscriptBuilder.toString()
                            val displayText = if (preview.length > 60) "...${preview.takeLast(60)}" else preview
                            binding.textStatusPill.text = "📝 $displayText"
                        }
                    }
                    if (isRecording) restartSpeechRecognition()
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        Log.d("SpeechRecognizer", "Partial: ${matches[0]}")
                        runOnUiThread {
                            binding.textStatusPill.text = "🎙️ ${matches[0]}"
                        }
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        } else {
            Log.w("SpeechRecognizer", "SpeechRecognizer is NOT available on this device")
        }
    }

    private fun startSpeechRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to start speech recognition: ${e.message}")
        }
    }

    private fun restartSpeechRecognition() {
        // Add a 500ms delay before restarting to prevent rapid restart crashes
        timerHandler.postDelayed({
            if (isRecording) {
                try {
                    speechRecognizer?.stopListening()
                } catch (e: Exception) {
                    Log.w("MainActivity", "StopListening error: ${e.message}")
                }
                startSpeechRecognition()
            }
        }, 500)
    }

    private fun startRecording() {
        liveTranscriptBuilder.clear()
        val file = recorderManager.startRecording { level ->
            binding.progressMicLevel.progress = level
            binding.textMicPercent.text = "$level%"
        }

        if (file == null) {
            Toast.makeText(this, "Failed to initialize microphone", Toast.LENGTH_SHORT).show()
            return
        }

        startSpeechRecognition()

        isRecording = true
        binding.btnStartRecord.isEnabled = false
        binding.btnStopRecord.isEnabled = true
        binding.textStatusPill.text = "🎙️ Recording & Transcribing On-Device"
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
        try { speechRecognizer?.stopListening() } catch (e: Exception) {}
        isRecording = false

        binding.btnStartRecord.isEnabled = true
        binding.btnStopRecord.isEnabled = false
        binding.progressMicLevel.progress = 0
        binding.textMicPercent.text = "0%"

        val title = binding.editMeetingTitle.text.toString().ifEmpty { "Meeting Session ${System.currentTimeMillis() % 10000}" }
        val audioPath = recordedFile?.absolutePath ?: "N/A"
        var transcript = liveTranscriptBuilder.toString().trim()

        if (transcript.isEmpty()) {
            transcript = "[Speech-to-text unavailable — review audio recording at: ${recordedFile?.name ?: "N/A"}]"
        }

        if (!isStandaloneMode && recordedFile != null && recordedFile.exists()) {
            binding.textStatusPill.text = "Uploading to Server (${ApiClient.getBaseUrl()})..."
            binding.textStatusPill.setTextColor(ContextCompat.getColor(this, R.color.accent_cyan))

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val requestFile = recordedFile.asRequestBody("audio/wav".toMediaTypeOrNull())
                    val bodyPart = MultipartBody.Part.createFormData("file", recordedFile.name, requestFile)
                    val titleBody = title.toRequestBody("text/plain".toMediaTypeOrNull())
                    val langBody = "English".toRequestBody("text/plain".toMediaTypeOrNull())
                    val transBody = transcript.toRequestBody("text/plain".toMediaTypeOrNull())

                    val response = ApiClient.service.uploadAndroidRecording(bodyPart, titleBody, langBody, transBody)

                    withContext(Dispatchers.Main) {
                        if (response.isSuccessful && response.body()?.meeting != null) {
                            val serverMeeting = response.body()!!.meeting!!
                            val serverTasks = response.body()!!.tasks ?: emptyList()

                            localDataManager.saveMeeting(serverMeeting)
                            localDataManager.saveTasks(serverTasks)

                            binding.textStatusPill.text = "Standby"
                            binding.textTimer.text = "00:00:00"
                            binding.editMeetingTitle.text.clear()

                            Toast.makeText(this@MainActivity, "Server AI Transcription Complete!\nSummary & ${serverTasks.size} tasks generated.", Toast.LENGTH_LONG).show()

                            binding.tabLayout.getTabAt(0)?.select()
                            loadLocalData()
                        } else {
                            Log.w("MainActivity", "Server response unsuccessful: ${response.code()}. Falling back locally.")
                            processLocallyFallback(title, transcript, audioPath)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Server upload failed: ${e.message}")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Server connection notice (${e.message}). Processed locally.", Toast.LENGTH_SHORT).show()
                        processLocallyFallback(title, transcript, audioPath)
                    }
                }
            }
        } else {
            processLocallyFallback(title, transcript, audioPath)
        }
    }

    private fun processLocallyFallback(title: String, transcript: String, audioPath: String) {
        binding.textStatusPill.text = "Processing On-Device AI Intelligence..."
        binding.textStatusPill.setTextColor(ContextCompat.getColor(this, R.color.text_muted))

        val (meeting, tasks) = LocalMeetingEngine.processMeetingLocally(
            title = title,
            transcriptRaw = transcript,
            audioFilePath = audioPath,
            serverHost = ApiClient.getBaseUrl(),
            yogeshChatEndpoint = localDataManager.getYogeshChatEndpoint()
        )

        localDataManager.saveMeeting(meeting)
        localDataManager.saveTasks(tasks)

        binding.textStatusPill.text = "Standby"
        binding.textTimer.text = "00:00:00"
        binding.editMeetingTitle.text.clear()

        Toast.makeText(this, "Meeting saved locally on phone!\nSummary & ${tasks.size} Action Tasks generated.", Toast.LENGTH_LONG).show()

        val mockJob = BackgroundJob(
            id = "job_" + UUID.randomUUID().toString().substring(0, 8),
            meetingTitle = title,
            targetLanguage = "English",
            stage = "completed",
            statusMessage = "Meeting processing completed & saved locally on phone!",
            progress = 100,
            finishedAt = System.currentTimeMillis() / 1000.0,
            meetingId = meeting.id
        )
        jobAdapter.updateData(listOf(mockJob))

        binding.tabLayout.getTabAt(0)?.select()
        loadLocalData()
    }

    // ========================
    // Play Meeting Audio
    // ========================
    private fun playMeetingAudio(meeting: Meeting) {
        val audioPath = meeting.audioUrl
        if (audioPath.isNullOrEmpty() || audioPath == "N/A") {
            Toast.makeText(this, "No audio recording found for this meeting.", Toast.LENGTH_SHORT).show()
            return
        }

        val playSource: String
        val isRemoteUrl = audioPath.startsWith("http://") || audioPath.startsWith("https://") || audioPath.startsWith("/recordings/") || audioPath.startsWith("/uploads/")

        if (isRemoteUrl) {
            playSource = if (audioPath.startsWith("http://") || audioPath.startsWith("https://")) {
                audioPath
            } else {
                "${ApiClient.getBaseUrl()}${audioPath.removePrefix("/")}"
            }
        } else {
            val audioFile = File(audioPath)
            if (!audioFile.exists()) {
                Toast.makeText(this, "Audio file not found:\n${audioFile.name}", Toast.LENGTH_SHORT).show()
                return
            }
            playSource = audioFile.absolutePath
        }

        // Stop any currently playing audio
        stopAudioPlayback()

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(playSource)
                prepareAsync()
                setOnPreparedListener { mp ->
                    mp.start()
                    Toast.makeText(this@MainActivity, "▶ Playing: ${meeting.title}", Toast.LENGTH_SHORT).show()
                }
                setOnCompletionListener {
                    Toast.makeText(this@MainActivity, "⏹ Playback finished", Toast.LENGTH_SHORT).show()
                    stopAudioPlayback()
                }
                setOnErrorListener { _, what, extra ->
                    Toast.makeText(this@MainActivity, "Error playing audio ($what, $extra)", Toast.LENGTH_SHORT).show()
                    true
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error setting up audio player: ${e.message}")
            Toast.makeText(this, "Error playing audio: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopAudioPlayback() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            // ignore
        }
        mediaPlayer = null
    }

    // ========================
    // Delete Meeting with Confirmation
    // ========================
    private fun confirmDeleteMeeting(meeting: Meeting) {
        AlertDialog.Builder(this)
            .setTitle("Delete Meeting")
            .setMessage("Are you sure you want to delete:\n\n\"${meeting.title}\"?\n\nThis will also delete associated tasks and the audio recording.")
            .setPositiveButton("Delete") { _, _ ->
                // Stop playback if playing this meeting's audio
                stopAudioPlayback()

                // Delete audio file from disk
                val audioPath = meeting.audioUrl
                if (!audioPath.isNullOrEmpty() && audioPath != "N/A") {
                    try {
                        val audioFile = File(audioPath)
                        if (audioFile.exists()) {
                            audioFile.delete()
                        }
                    } catch (e: Exception) {
                        Log.w("MainActivity", "Could not delete audio file: ${e.message}")
                    }
                }

                // Delete from local storage
                localDataManager.deleteMeeting(meeting.id)

                // Delete from remote server if connected
                if (!isStandaloneMode) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            ApiClient.service.deleteMeeting(meeting.id)
                        } catch (e: Exception) {
                            Log.w("MainActivity", "Remote delete notice: ${e.message}")
                        }
                    }
                }

                loadLocalData()
                Toast.makeText(this, "Meeting \"${meeting.title}\" deleted.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadLocalData() {
        when (currentTab) {
            0 -> {
                val localMeetings = localDataManager.getMeetings()
                meetingAdapter.updateData(localMeetings)

                if (!isStandaloneMode) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val res = ApiClient.service.getMeetings()
                            if (res.isSuccessful && res.body() != null) {
                                val remoteMeetings = res.body()!!
                                val mergedMap = LinkedHashMap<String, Meeting>()
                                localMeetings.forEach { m -> mergedMap[m.id] = m }
                                remoteMeetings.forEach { m -> if (!mergedMap.containsKey(m.id)) mergedMap[m.id] = m }
                                withContext(Dispatchers.Main) {
                                    meetingAdapter.updateData(mergedMap.values.toList())
                                }
                            }
                        } catch (e: Exception) {
                            Log.w("MainActivity", "Remote meetings fetch notice: ${e.message}")
                        }
                    }
                }
            }
            1 -> {
                val localTasks = localDataManager.getTasks()
                taskAdapter.updateData(localTasks)

                if (!isStandaloneMode) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val res = ApiClient.service.getTasks()
                            if (res.isSuccessful && res.body() != null) {
                                val remoteTasks = res.body()!!
                                val mergedMap = LinkedHashMap<String, TaskItem>()
                                localTasks.forEach { t -> mergedMap[t.id] = t }
                                remoteTasks.forEach { t -> if (!mergedMap.containsKey(t.id)) mergedMap[t.id] = t }
                                withContext(Dispatchers.Main) {
                                    taskAdapter.updateData(mergedMap.values.toList())
                                }
                            }
                        } catch (e: Exception) {
                            Log.w("MainActivity", "Remote tasks fetch notice: ${e.message}")
                        }
                    }
                }
            }
            2 -> {
                if (!isStandaloneMode) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val res = ApiClient.service.getJobs()
                            if (res.isSuccessful && res.body() != null) {
                                withContext(Dispatchers.Main) {
                                    jobAdapter.updateData(res.body()!!)
                                }
                            }
                        } catch (e: Exception) {
                            Log.w("MainActivity", "Remote jobs fetch notice: ${e.message}")
                        }
                    }
                }
            }
            3 -> {
                val configuredEndpoint = localDataManager.getYogeshChatEndpoint()
                if (!isStandaloneMode) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val res = ApiClient.service.getAiLogs()
                            if (res.isSuccessful && res.body() != null) {
                                withContext(Dispatchers.Main) {
                                    aiLogAdapter.updateData(res.body()!!)
                                }
                            }
                        } catch (e: Exception) {
                            Log.w("MainActivity", "Remote AI Logs fetch notice: ${e.message}")
                        }
                    }
                } else {
                    val localLogs = localDataManager.getMeetings().map { m ->
                        com.taskpulse.ai.models.AiLog(
                            id = m.id,
                            timestamp = m.createdAt,
                            provider = "Yogesh Chat Engine",
                            endpoint = configuredEndpoint,
                            httpMethod = "POST",
                            meetingTitle = m.title,
                            targetLanguage = m.language,
                            prompt = m.prompt ?: "Analyze meeting transcript for '${m.title}'.",
                            responseRaw = m.responseRaw ?: m.summary,
                            durationMs = 1200,
                            status = "success",
                            curlCommand = m.curlCommand ?: "curl -X POST \"$configuredEndpoint\""
                        )
                    }
                    aiLogAdapter.updateData(localLogs)
                }
            }
        }
    }

    private fun showMeetingDetailsDialog(meeting: Meeting) {
        val tasks = localDataManager.getTasks().filter { it.meetingId == meeting.id }
        val taskText = if (tasks.isNotEmpty()) {
            tasks.joinToString("\n") { "• [${it.priority}] ${it.title} (${it.assignee})" }
        } else {
            "No action tasks extracted."
        }

        val formattedReport = """
            🤖 AI CHAT ASSISTANT - MEETING INTELLIGENCE REPORT
            =========================================================
            📌 Meeting Title : ${meeting.title}
            📅 Date Recorded : ${meeting.createdAt}
            🗣️ Language      : ${meeting.language}
            🎵 Audio File    : ${meeting.audioFilename ?: "N/A"}
            =========================================================
            
            📝 EXECUTIVE SUMMARY:
            ${meeting.summary}
            
            ✅ ACTION TASKS:
            $taskText
            
            ⚡ EXECUTABLE cURL COMMAND:
            ${meeting.curlCommand ?: "N/A"}
            
            💬 TRANSCRIPT:
            ${meeting.transcript}
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle(meeting.title)
            .setMessage(formattedReport)
            .setPositiveButton("Close", null)
            .setNeutralButton("Copy Full Description") { _, _ ->
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("AI Assistant Description", formattedReport)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Copied Full Formatted Description to Clipboard!", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Delete") { _, _ ->
                confirmDeleteMeeting(meeting)
            }
            .show()
    }

    private fun showAiLogDetailDialog(log: com.taskpulse.ai.models.AiLog) {
        val detailMsg = """
            🔗 TARGET ENDPOINT:
            ${log.httpMethod} ${log.endpoint}
            
            ⚡ EXECUTABLE cURL COMMAND:
            ${log.curlCommand}
            
            💬 PROMPT SENT TO AI ENGINE:
            ${log.prompt}
            
            📥 RAW AI RESPONSE OUTPUT:
            ${log.responseRaw}
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("${log.provider} • ${log.meetingTitle}")
            .setMessage(detailMsg)
            .setPositiveButton("Close", null)
            .setNeutralButton("Copy cURL") { _, _ ->
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("cURL Command", log.curlCommand ?: "")
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Copied cURL Command to Clipboard!", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        stopAudioPlayback()
    }
}
