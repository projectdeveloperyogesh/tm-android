# 📱 TaskPulse AI - Android Native Application

A native Android application built with Kotlin, Coroutines, Retrofit, and AudioRecord for real-time meeting recording, non-blocking background AI transcription, summary generation, and task extraction.

---

## 🌟 Key Features

1. **📱 100% Standalone On-Device Operation**:
   - Zero external server dependency!
   - Performs audio recording, speech transcription, AI summary generation, and task extraction 100% locally on your Android phone.

2. **🎙️ Native High-Fidelity Audio & On-Device Speech Recognition**:
   - Captures microphone audio using 16kHz PCM WAV encoding.
   - Converts live spoken meeting speech into text on-device using Android's native SpeechRecognizer.
   - Real-time decibel volume gauge visualizer (`0% - 100%`).

3. **🧠 On-Device Local AI Intelligence Engine (`LocalMeetingEngine`)**:
   - Automatically generates meeting summaries, key discussion topics, and action tasks locally from speech.
   - Categorizes priorities (`High`, `Medium`, `Low`), assigns team members, and sets due dates.

4. **💾 On-Device Data Persistence (`LocalDataManager`)**:
   - Saves all meetings, transcripts, summaries, and action tasks in local phone storage (`meetings.json` & `tasks.json`).
   - View, review, search, or delete meetings anytime completely offline.

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
