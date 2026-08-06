# 📱 TaskPulse AI - Android Native Application

A native Android application built with Kotlin, Coroutines, Retrofit, and AudioRecord for real-time meeting recording, non-blocking background AI transcription, summary generation, and task extraction.

---

## 🌟 Key Features

1. **🎙️ Native High-Fidelity Audio Recording**:
   - Captures microphone audio using 16kHz PCM WAV encoding.
   - Real-time decibel volume gauge visualizer (`0% - 100%`).
   - Background foreground service compatibility.

2. **⚡ Non-Blocking Background Processing Integration**:
   - Immediately releases recording state after tapping **Stop & Process**.
   - Uploads binary PCM WAV chunks directly to TaskPulse AI server (`/api/record/stop_web`).
   - Polls real-time job progress (`transcribing`, `analyzing`, `saving`, `completed`).

3. **📊 AI Meeting Summaries & Action Task Board**:
   - View list of recorded meetings, dates, and AI-generated summaries.
   - Integrated action task board with priority badges and assignee tags.

4. **🌐 Adaptive Server Connection**:
   - Configurable host connection (`http://10.0.2.2:3000` for Android Emulator, or `http://192.168.X.X:3000` for physical devices on local Wi-Fi).

---

## 🛠️ Project Structure

```
android/
├── build.gradle               # Project build configuration
├── settings.gradle            # Settings and module inclusions
├── gradle.properties          # JVM & AndroidX settings
├── app/
│   ├── build.gradle           # Application dependencies (Retrofit, Material, Coroutines)
│   └── src/main/
│       ├── AndroidManifest.xml # Permissions (RECORD_AUDIO, INTERNET, FOREGROUND_SERVICE)
│       ├── java/com/taskpulse/ai/
│       │   ├── MainActivity.kt           # Main UI Controller & Job Poller
│       │   ├── api/
│       │   │   ├── ApiClient.kt          # Retrofit Client with dynamic URL updates
│       │   │   └── TaskPulseApiService.kt # Endpoints (/api/meetings, /api/tasks, /api/jobs)
│       │   ├── models/
│       │   │   └── Models.kt             # Data classes (Meeting, TaskItem, BackgroundJob)
│       │   ├── recorder/
│       │   │   └── AudioRecorderManager.kt # 16kHz PCM WAV AudioRecord Engine
│       │   └── adapter/
│       │       └── Adapters.kt           # RecyclerView Adapters for Meetings, Tasks & Jobs
│       └── res/
│           ├── layout/                   # Dark mode XML layouts
│           └── values/                   # Color palette & themes
```

---

## 🚀 How to Build & Run

### 1. Open in Android Studio
1. Open **Android Studio** (Iguana or newer).
2. Select **Open** and choose the `D:\dev\dev\python\tm\android` directory.
3. Allow Gradle to sync dependencies.

### 2. Connect to Local TaskPulse AI Server
1. Ensure your PC running TaskPulse AI server (`node server.js` or `python main.py`) is connected to the same Wi-Fi network.
2. Find your PC's IP address (e.g. `192.168.1.150`).
3. Enter `http://192.168.1.150:3000` in the **Server Host Connection** input on your phone and tap **Connect**.

### 3. Record & Process
1. Tap **🎙️ Record** to start recording meeting audio.
2. Observe live mic volume levels on the progress bar.
3. Tap **⏹️ Stop & Process** — recording stops instantly, uploads in background, and automatically opens **Jobs Monitor**!

---

## 📦 Pushing to Android Git Repository

When you receive your Android Git repository URL from the user, execute:

```bash
cd android
git init
git remote add origin <ANDROID_GIT_REPO_URL>
git add .
git commit -m "Initialize TaskPulse AI Android Native Application"
git push -u origin main
```
