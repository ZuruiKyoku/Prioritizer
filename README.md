# Prioritizer (Beta)
**Mobile Task Optimization & Priority Management**

### 📌 Overview
Prioritizer is a native Android application designed to streamline task management through an intelligent urgency-based sorting system. Built with **Kotlin** and **Jetpack Compose**, it utilizes an **offline-first** architecture to ensure seamless performance regardless of network connectivity, while synchronizing data across devices via the cloud.

### 🛠 Technical Pillars
* **Platform:** Android (Kotlin)
* **UI/UX Design:** Modern, declarative UI built with **Jetpack Compose** and **Material3**, featuring strict state management and responsive layouts.
* **Architecture:** robust **MVVM** architecture following **Clean Architecture** principles.
    *   **Reactive Data Flow:** Utilizes **StateFlow** and **Coroutines** for efficient, unidirectional data flow (UDF).
    *   **Data Layer:** Implements the **Repository Pattern** to arbitrate between a local **Room Database** (single source of truth) and **Firebase Firestore** for real-time cloud sync.
*   **Backend Services:** leveraged **Firebase** ecosystem (Auth, Functions, Messaging) for serverless backend logic and push notifications.

### 🚀 Development Status
* **Current Phase:** Beta / Pre-Release
* **Active Tasks:** Final polish for Google Play Store submission, including privacy compliance and edge-case handling for shared lists.
* **Next Milestone:** v1.0 Public Launch
