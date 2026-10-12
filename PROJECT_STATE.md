# PROJECT_STATE — Prioritizer

Last reviewed: **2026-10-12 (America/New_York)**. Continuity schema: **1**.

This is a maintained project handoff, not an assertion that every historical chat or runtime behavior has been verified. Recheck Git and source at session start. Architecture and feature notes below describe the audited local project unless explicitly labeled remote default.

## End goal

An offline-first Android task manager with automatic 1–10 priority ordering, personal lists, shared lists, friends and real-time synchronization.

## Architecture

Kotlin/Compose, Room, Hilt and reactive repositories; Firebase Auth/Firestore/Storage/Messaging/Functions.

## Decisions and workflow

- Room is the local UI source of truth; preserve list ownership and sharing semantics.
- Keep personal task data when updating the app; do not uninstall to solve signing problems without approval.
- Treat old worktree fixes as historical until confirmed in current architecture.
- Preserve existing acceptance gates and uncommitted work. The 2026-10-12 authorization covers continuity documentation branches only, not merging features, deploying, or pushing unrelated code.

## Current status

- Local working branch at audit: `main`; code snapshot `a249b39b9ef422ee63edeed64599216ae6946b81`.
- Pre-existing working-tree changes: **24** entries. These were not staged, committed or discarded by continuity setup.
- Live GitHub default: `main` at `eed85158c2eb68757424f0399891dfc6408e3d40`.
- Continuity documentation was prepared from the reviewed `main` baseline. This documentation-only delivery includes no feature-branch implementation.
- Repository: https://github.com/ZuruiKyoku/Prioritizer (public).
- Existing changed paths: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/java/com/slygames/prioritizer/MainActivity.kt`, `app/src/main/java/com/slygames/prioritizer/data/AppDatabase.kt`, `app/src/main/java/com/slygames/prioritizer/data/list/TaskListEntity.kt`, `app/src/main/java/com/slygames/prioritizer/data/sync/SyncManager.kt`, `app/src/main/java/com/slygames/prioritizer/data/task/TaskDao.kt`, `app/src/main/java/com/slygames/prioritizer/data/task/TaskRepository.kt`, `app/src/main/java/com/slygames/prioritizer/data/user/FriendRepository.kt`, `app/src/main/java/com/slygames/prioritizer/data/user/ProfileRepository.kt`, `app/src/main/java/com/slygames/prioritizer/ui/TaskListScreen.kt`, `app/src/main/java/com/slygames/prioritizer/ui/components/ListManagerItem.kt`, `app/src/main/java/com/slygames/prioritizer/ui/dialogs/ListManagerDialog.kt`, `app/src/main/java/com/slygames/prioritizer/ui/dialogs/ShareListWithFriendDialog.kt`, `app/src/main/java/com/slygames/prioritizer/ui/profile/FriendsTab.kt`, `app/src/main/java/com/slygames/prioritizer/viewmodel/RequestsViewModel.kt`, `app/src/main/java/com/slygames/prioritizer/viewmodel/TaskViewModel.kt`, `firestore.rules`, `gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.properties`, `storage.rules`, `firebase-rules-audit.local.md`, `prioritizer-after.png`, `prioritizer-before.png`.

## Completed work

These are source/history findings; historical test reports are labeled and are not fresh runtime verification.

- GitHub main remains at eed8515; local main contains a later architecture rewrite and notification/sync fixes through a249b39.
- Many further local source/rules changes exist. Recent chat reports Google sign-in and private-list persistence fixes, not a clean pushed release.

## Known issues and gaps

- Local main is materially ahead of GitHub and dirty; only documentation is being pushed now.
- Store-readiness and backend-rule questions from earlier audits require a fresh focused review, not a blanket compliance claim.
- 5 associated local transcript(s) located. Initial requirements, recent handoffs and relevant narrative excerpts were reviewed against repository evidence. The entire history has not been exhaustively reviewed; older decisions may remain.
- ChatGPT discovery returned a bounded recent list, not every project conversation. Some retrieved assistant messages contain only content-reference placeholders; attachments and unavailable responses were not reconstructed.

## Roadmap

- Review and separately checkpoint local architecture, authentication, sync and rule changes.
- Verify private-list restore, shared-list permissions, offline changes and notification taps on the intended device/build.
- Reconcile the missing historical context before treating this document as a complete replacement for old chats.

## Next steps

1. Read this file and applicable `AGENTS.md`; inspect the current branch, working tree and remote refs.
2. Review and separately checkpoint local architecture, authentication, sync and rule changes.
3. Perform the project's required checks for any code change, obtain existing acceptance where required, and update this handoff with actual results.

## Verification and evidence

- This session verified repository identity, local Git metadata/tracked paths, live remote refs where configured, and inspected documentation/source evidence. It changed documentation only.
- No application build, unit-test suite, device test, deployment check or live-service test is implied by this audit. Prior successful checks do not validate later uncommitted edits.
- Evidence labels: **source present** is static evidence; **historical report** is a past session claim; **verified now** requires an explicitly recorded current check; **planned/unknown** is not implemented.

- `app/src/main/java/com/slygames/prioritizer/data/AppDatabase.kt` — present on remote default; local content differs.
- `app/src/main/java/com/slygames/prioritizer/data/sync/SyncManager.kt` — local/feature evidence; absent from reviewed remote default.
- `firestore.rules` — present on remote default; local content differs.
- `storage.rules` — local/feature evidence; absent from reviewed remote default.

### History sources and coverage

5 associated local transcript(s) located. Initial requirements, recent handoffs and relevant narrative excerpts were reviewed against repository evidence. The entire history has not been exhaustively reviewed; older decisions may remain.

- Review Google Play compliance — `01a0d3c2-953c-7f22-9b0a-be73621e7818` (56 narrative messages available).
- install this on my phone — `01a0f8ea-c632-7ad3-af81-2b664a5004cc` (40 narrative messages available).
- Fix dead ACCEPT_SHARED_LIST notification-tap handling — `01a0f8ea-cabf-7f22-9d9c-ce7033a7cf3d` (16 narrative messages available).
- Investigate CubicBezierEasing crash on Compose transition — `01a0f8ea-cc85-7302-8e98-cc45a4af26a9` (6 narrative messages available).
- Prioritizer Android application — `01a0f8ea-ccb1-7820-94d1-84b9f8f48b02` (287 narrative messages available).

## Session handoff

- Last task (2026-10-12): deliver the continuity documentation to the existing remote default branch `main`, as explicitly authorized by the user. Application changes and feature acceptance remain separate.
- Documentation scope: preserve all pre-existing code, assets, branches, indexes and acceptance gates. A documentation branch does not back up uncommitted application work.
- Publication evidence: original documentation commit `b5771e01d998d3f53e3358ae1b2706e44982db02`; delivery targets `main` by normal fast-forward push. Confirm inclusion with Git ancestry; the external continuity report records the post-push remote SHA. Original local checkouts are not pulled or switched during delivery.
- Next owner: follow Next steps above, fill historical gaps, and replace stale status with dated evidence after meaningful work.
- Archive readiness: incomplete history and any local-only implementation still need reconciliation; do not infer that archiving all project chats is lossless.
