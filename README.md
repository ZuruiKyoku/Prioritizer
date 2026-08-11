# Prioritizer

**Focus on what matters most.** Prioritizer is a native Android task manager built around a
single idea: every task gets a priority from **1 (minimal)** to **10 (critical)**, and the app
keeps your list sorted by that priority automatically. Lists can be shared with friends and stay
in sync in real time.

## Tech stack

- **Kotlin** + **Jetpack Compose** (Material 3)
- **Room** — local, offline-first database (single source of truth for the UI)
- **Firebase** — Authentication, Firestore, Storage, Cloud Messaging, Cloud Functions
- **Hilt** — dependency injection
- **Coroutines / Flow** — reactive data layer
- MVVM + repository pattern: `UI → ViewModel → Repository/SyncManager → Room + Firestore`

## Requirements

| Tool | Version |
|---|---|
| Android Studio | Narwhal 2025.1.1 Patch 1 (or newer) |
| JDK | 17 (a bundled JBR 17/21 from Android Studio works) |
| Gradle | 8.5 (via the included wrapper — just use `./gradlew`) |
| Android SDK | `compileSdk`/`targetSdk` 34–35, `minSdk` 24 |

> **Note on JDK 25+:** if `java -version` on your `JAVA_HOME` reports 25 or newer, Gradle 8.5
> won't run under it. Point `JAVA_HOME` at a JDK 17 or 21 install instead (Android Studio ships
> one under `Android Studio/jbr`).

## Getting started

```bash
git clone <this-repo>
cd Prioritizer
```

### 1. Firebase project setup

This app needs its own Firebase project — the checked-in `app/google-services.json` is tied to
the original developer's project and **will not work for a fresh clone** against a different
Firebase project.

1. Create a project at [console.firebase.google.com](https://console.firebase.google.com).
2. Add an Android app with package name **`com.slygames.prioritizer`**.
3. Download the generated `google-services.json` and place it at:
   ```
   app/google-services.json
   ```
4. In the Firebase console, enable:
   - **Authentication** → Sign-in method → **Email/Password** and **Google**.
   - **Firestore Database** → create in production mode, any region.
   - **Storage** → default bucket is fine.
   - **Cloud Messaging** — no extra setup needed beyond the app being registered.
5. For Google Sign-In, add your app's SHA-1 (debug + release) fingerprints under Project
   Settings → Your apps, then re-download `google-services.json`.
6. Copy the **Web client ID** Firebase generates for Google Sign-In into
   `app/src/main/res/values/strings.xml` as `default_web_client_id` (already present as a
   placeholder key — Android Studio's Firebase Assistant can also wire this up for you).

### 2. Firestore security rules

Deploy the rules in [`firestore.rules`](firestore.rules):

```bash
firebase deploy --only firestore:rules
```

These rules enforce:
- Users can only read/write their own `users/{uid}` profile and `users/{uid}/lists/**` data.
- Shared lists (`sharedLists/{uuid}`) are readable/writable only by the owner or an accepted
  member (`sharedLists/{uuid}/sharedWith/{uid}`).
- Friend and friend-request documents are only visible to the two parties involved.

### 3. Cloud Functions (push notifications)

The functions in [`functions/index.js`](functions/index.js) send FCM notifications for friend
requests and shared-list activity (task added/completed/deleted/priority-changed, membership
changes). Deploy with:

```bash
cd functions
npm install
firebase deploy --only functions
```

Functions are optional for local development — the app works fully without them, you just won't
get push notifications.

### 4. Build & run

```bash
./gradlew assembleDebug
```

Or open the project in Android Studio and run the `app` configuration on a device/emulator
running API 24+.

### 5. Tests

```bash
./gradlew testDebugUnitTest
```

Unit tests live under `app/src/test` and cover priority validation/clamping, sort ordering
(priority descending, ties broken by creation time — exercised against a real in-memory Room
database via Robolectric, not a hand-rolled comparator), task completion, list CRUD, input
validation, and duplicate-import prevention for shared lists.

## Architecture

```
Jetpack Compose UI
        │
        ▼
   ViewModels (@HiltViewModel — one per feature: Auth, Task, Friends, Requests, Profile, Settings)
        │
        ▼
  Repositories (Room-backed: TaskRepository · Firestore-backed: AuthRepository, ProfileRepository,
                FriendRepository)
        │
        ▼
     SyncManager  ◄──── the single owner of Firestore listeners & shared-list mirrors
        │                (app-scoped coroutine scope, not tied to any screen's lifecycle)
        ▼
  Room (local)  ◄──sync──►  Firestore (cloud)
```

Key decisions worth knowing about if you're extending this:

- **Task identity is a client-generated UUID** (`TaskEntity.uuid`), not the local Room row id.
  The Room id is a per-device autoincrement counter — using it as a Firestore document key would
  let two devices independently mint the same key for two different tasks and silently overwrite
  one with the other. The uuid is what's used as the Firestore document key for synced tasks.
- **`SyncManager` is the only class that touches `FirebaseFirestore` for task/list/sharing
  operations.** It owns a `SupervisorJob`-backed coroutine scope so shared-list mirror listeners
  keep running across navigation instead of being torn down when a screen closes.
- **Room is the UI's only data source.** ViewModels never read Firestore directly; `SyncManager`
  writes results into Room, and Compose observes Room via `Flow`.
- Default sort is `priority DESC, createdAt ASC`, enforced in the Room `@Query` itself so it can't
  drift from what's actually stored.

## Project layout

```
app/src/main/java/com/slygames/prioritizer/
├── data/
│   ├── task/         TaskEntity, TaskDao, TaskRepository, TaskFilter
│   ├── list/         TaskListEntity, SharedListInvite
│   ├── user/         UserProfile, ProfileRepository, FriendRepository, UserPreferences
│   ├── auth/         AuthRepository, AuthError
│   ├── sync/         SyncManager
│   └── AppDatabase.kt
├── di/               Hilt modules
├── ui/               Compose screens, dialogs, reusable components, theme
├── viewmodel/        AuthViewModel, TaskViewModel, FriendsViewModel, RequestsViewModel,
│                     ProfileViewModel, SettingsViewModel
└── util/             InputValidation, image/friend-code helpers
```

## Known follow-ups

- The `.prioritizer` export/import format and the "share list via file" flow predate the uuid
  identity change above; imported tasks get a fresh local uuid, which is correct for private
  lists but isn't wired into the shared-list identity model.
- `ListManagerDialog`'s "manage sharing" checklist still calls Firestore directly for
  adding/removing members instead of going through `SyncManager` — functionally fine, but worth
  folding in for consistency.
- No Compose UI tests yet (only unit tests). The multi-account real-time sync scenario in the
  original spec (two accounts, live task edits reflecting across devices) needs to be verified
  manually against a live Firebase project — it isn't something a unit test suite can exercise.
