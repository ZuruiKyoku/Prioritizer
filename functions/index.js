// Functions (Firestore v2 triggers + HTTPS onCall)
const { onDocumentCreated, onDocumentUpdated, onDocumentDeleted, onDocumentWritten } = require("firebase-functions/v2/firestore");
const { onCall, HttpsError } = require("firebase-functions/v2/https");

// Admin SDK
const admin = require("firebase-admin");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

admin.initializeApp();

/* ========================================================================== *
 *  FRIENDS & LIST-SHARE NOTIFICATIONS (existing)
 * ========================================================================== */

exports.sendFriendRequestNotification = onDocumentCreated(
  "friend_requests/{recipientUid}/incoming/{senderUid}",
  async (event) => {
    const snapshot = event.data;
    const { recipientUid } = event.params;
    if (!snapshot) return;

    const request = snapshot.data();
    const db = getFirestore();

    const recipientDoc = await db.collection("users").doc(recipientUid).get();
    const fcmToken = recipientDoc.get("fcmToken");
    if (!fcmToken) return;

    const payload = {
      notification: {
        title: "New Friend Request",
        body: `${request.displayName} sent you a friend request`,
        image: request.profileImageUrl || undefined,
      },
      data: {
        type: "friend_request",
        senderUid: String(request.senderUid || ""),
      },
      token: fcmToken,
    };

    try {
      await getMessaging().send(payload);
    } catch (err) {
      console.error("Failed to send friend request notification:", err);
    }
  }
);

exports.sendListSharedNotification = onDocumentCreated(
  "notifications/{userId}/items/{notifId}",
  async (event) => {
    const snapshot = event.data;
    const { userId } = event.params;
    if (!snapshot) return;

    const data = snapshot.data();
    // Accept both legacy and current types
    if (data.type !== "LIST_INVITE" && data.type !== "LIST_SHARED") return;

    // Don’t notify the actor (just in case)
    if (String(userId) === String(data.from)) return;

    const db = getFirestore();
    const userDoc = await db.collection("users").doc(userId).get();
    const fcmToken = userDoc.get("fcmToken");
    if (!fcmToken) return;

    const payload = {
      notification: {
        title: "A List Was Shared With You",
        body: `${data.fromName} shared "${data.listName}"`,
      },
      data: {
        type: data.type,
        listId: String(data.listId ?? ""),
        fromUid: String(data.from ?? ""),
        uuid: String(data.uuid ?? ""),
      },
      token: fcmToken,
    };

    try {
      await getMessaging().send(payload);
    } catch (err) {
      console.error("Failed to send list shared notification:", err);
    }
  }
);

exports.syncDisplayNameToFriends = onDocumentUpdated("users/{uid}", async (event) => {
  const uid = event.params.uid;
  const before = event.data?.before?.data();
  const after = event.data?.after?.data();
  if (!before || !after) return;

  const nameChanged = before.displayName !== after.displayName;
  const imageChanged = before.profileImageUrl !== after.profileImageUrl;
  if (!nameChanged && !imageChanged) return;

  const db = getFirestore();
  const friendDocs = await db.collection("friends").where("friendUid", "==", uid).get();

  if (friendDocs.empty) {
    console.log(`No friends found that reference ${uid}`);
    return;
  }

  const batch = db.batch();
  friendDocs.forEach((doc) => {
    const updateFields = {};
    if (nameChanged) updateFields.displayName = after.displayName;
    if (imageChanged) updateFields.profileImageUrl = after.profileImageUrl;
    batch.update(doc.ref, updateFields);
  });

  await batch.commit();
  console.log(`Updated ${friendDocs.size} friend document(s) for ${uid}`);
});

// Callable example (left as-is; feel free to swap to subcollection logic later if needed)
exports.addUserToSharedList = onCall(async (data, context) => {
  const requesterUid = context.auth?.uid;
  if (!requesterUid) {
    throw new HttpsError("unauthenticated", "User must be signed in.");
  }

  const sharedListId = data.sharedListId;
  const userToAdd = data.userToAdd || requesterUid;
  if (!sharedListId) {
    throw new HttpsError("invalid-argument", "Missing sharedListId.");
  }

  const db = getFirestore();
  const sharedListRef = db.collection("sharedLists").doc(sharedListId);
  const sharedListSnap = await sharedListRef.get();
  if (!sharedListSnap.exists) {
    throw new HttpsError("not-found", "Shared list not found.");
  }

  // Create/update a membership doc
  await sharedListRef
    .collection("sharedWith")
    .doc(userToAdd)
    .set({ addedAt: admin.firestore.FieldValue.serverTimestamp() });

  return { success: true, added: userToAdd };
});


/* ========================================================================== *
 *  SHARED LIST HELPERS
 * ========================================================================== */

// --- Utility: Fetch a shared list's name, owner UID, and all member UIDs ---
// Reads the parent list document for owner/name, and queries the
// `sharedWith` subcollection to return all member user IDs.
// Returns: { listName: string, ownerId: string|null, memberUids: string[] }
async function getSharedListMetaAndMembers(uuid) {
  const db = getFirestore();
  const listRef = db.collection("sharedLists").doc(uuid);
  const listSnap = await listRef.get();
  if (!listSnap.exists) return null;

  const listData = listSnap.data() || {};
  const listName = listData.name || "Shared List";
  const ownerId = listData.ownerId || listData.createdBy || null;

  // Members live at: sharedLists/{uuid}/sharedWith/{uid}
  const membersSnap = await db.collection(`sharedLists/${uuid}/sharedWith`).get();
  const memberUids = membersSnap.docs.map((d) => d.id);

  return { listName, ownerId, memberUids };
}

async function getFcmTokensForUids(uids = []) {
  const db = getFirestore();
  const unique = Array.from(new Set(uids)).filter(Boolean);
  if (!unique.length) return [];

  const snaps = await Promise.all(unique.map((uid) => db.collection("users").doc(uid).get()));
  return snaps
    .map((doc) => doc.get("fcmToken"))
    .filter((t) => typeof t === "string" && t.length > 0);
}

async function sendMulticastToUids(uids, notification, data = {}) {
  const tokens = await getFcmTokensForUids(uids);
  if (!tokens.length) return { sent: 0, reason: "no_tokens" };
  const res = await getMessaging().sendEachForMulticast({ tokens, notification, data });
  return { sent: res.successCount || 0 };
}

/* ========================================================================== *
 *  SHARED LIST EVENT NOTIFICATIONS
 * ========================================================================== */

// Notify when a shared task is ADDED (skip the actor)
exports.notifyOnTaskAdded = onDocumentCreated(
  "sharedLists/{uuid}/tasks/{taskId}",
  async (event) => {
    const snap = event.data;
    if (!snap) return;

    const { uuid, taskId } = event.params;
    const task = snap.data() || {};

    const meta = await getSharedListMetaAndMembers(uuid);
    if (!meta) return;

    const actorUid = task.createdBy || task.updatedBy || task.actorUid || null;

    // Audience: all members + owner, excluding the actor
    const audience = new Set(meta.memberUids);
    if (meta.ownerId) audience.add(meta.ownerId);
    if (actorUid) audience.delete(actorUid);

    const audienceList = Array.from(audience);
    if (!audienceList.length) return;

    const title = "New Task Added";
    const body = task.title
      ? `“${task.title}” added to ${meta.listName}`
      : `A task was added to ${meta.listName}`;

    await sendMulticastToUids(audienceList, { title, body }, {
      type: "TASK_ADDED",
      listUuid: uuid,
      listName: meta.listName,
      taskId: String(taskId || ""),
    });
  }
);

// Notify when a shared task is toggled done/undone (skip the actor)
exports.notifyOnTaskDoneToggled = onDocumentUpdated(
  "sharedLists/{uuid}/tasks/{taskId}",
  async (event) => {
    const before = event.data?.before?.data() || {};
    const after = event.data?.after?.data() || {};
    const { uuid, taskId } = event.params;

    const beforeDone = !!(before.done ?? before.isDone);
    const afterDone = !!(after.done ?? after.isDone);

    if (beforeDone === afterDone) return; // only when it flips

    const meta = await getSharedListMetaAndMembers(uuid);
    if (!meta) return;

    const actorUid =
      after.updatedBy ||
      after.completedBy ||
      after.actorUid ||
      before.updatedBy ||
      null;

    const audience = new Set(meta.memberUids);
    if (meta.ownerId) audience.add(meta.ownerId);
    if (actorUid) audience.delete(actorUid);

    const audienceList = Array.from(audience);
    if (!audienceList.length) return;

    const title = afterDone ? "Task Completed" : "Task Marked Incomplete";
    const taskTitle =
      after.title || after.name || after.description ||
      before.title || before.name || before.description || "Task";

    const body = afterDone
      ? `“${taskTitle}” was completed in ${meta.listName}`
      : `“${taskTitle}” was marked incomplete in ${meta.listName}`;

    await sendMulticastToUids(audienceList, { title, body }, {
      type: afterDone ? "TASK_COMPLETED" : "TASK_MARKED_INCOMPLETE",
      listUuid: uuid,
      listName: meta.listName,
      taskId: String(taskId || ""),
    });
  }
);

// Notify when a shared task is deleted (skip the actor)
exports.notifyOnTaskDeleted = onDocumentDeleted(
  "sharedLists/{uuid}/tasks/{taskId}",
  async (event) => {
    const db = getFirestore();
    const { uuid, taskId } = event.params;

    const snap = event.data;
    const task = snap?.data() || {};
    const taskTitle =
      task.title || task.name || task.description || `Task #${taskId}`;

    // Skip bulk "clear completed" deletes (already implemented)
    try {
      const opRef = db.collection("sharedLists").doc(uuid).collection("ops").doc("clearCompleted");
      const opSnap = await opRef.get();
      if (opSnap.exists) {
        const op = opSnap.data() || {};
        const ids = Array.isArray(op.taskIds) ? op.taskIds.map(String) : [];
        const startedAt = op.startedAt?.toDate?.();
        const fresh = startedAt && Date.now() - startedAt.getTime() < 2 * 60 * 1000;
        const idMatches = ids.includes(String(taskId)) || (task.id != null && ids.includes(String(task.id)));
        if (fresh && idMatches) {
          return;
        }
      }
    } catch (_) { }

    // Audience (owner + members)
    const meta = await getSharedListMetaAndMembers(uuid);
    if (!meta) return;

    const actorUid = task.deletedBy || task.updatedBy || task.actorUid || null;

    const audience = new Set(meta.memberUids);
    if (meta.ownerId) audience.add(meta.ownerId);
    if (actorUid) audience.delete(actorUid);

    const audienceList = Array.from(audience);
    if (!audienceList.length) return;

    await sendMulticastToUids(
      audienceList,
      { title: "Task Deleted", body: `“${taskTitle}” was deleted from ${meta.listName}` },
      {
        type: "TASK_DELETED",
        listUuid: uuid,
        listName: meta.listName,
        taskId: String(taskId || ""),
        taskName: taskTitle,
      }
    );
  }
);

// Notify members when the OWNER deletes the shared list (exclude the owner)
exports.notifyOnSharedListDeleted = onDocumentDeleted(
  "sharedLists/{uuid}",
  async (event) => {
    const { uuid } = event.params;

    // Deleted snapshot data (if provided by platform)
    const deleted = event.data?.data ? event.data.data() : (event.data || {});
    const name = deleted?.name || "a shared list";
    const ownerId = deleted?.ownerId || deleted?.createdBy || null;

    // Even though the parent doc is deleted, the subcollection can still be read here.
    const db = getFirestore();
    const membersSnap = await db.collection(`sharedLists/${uuid}/sharedWith`).get();
    const memberUids = membersSnap.docs.map((d) => d.id);

    const audience = memberUids.filter((u) => u && u !== ownerId);
    if (!audience.length) return;

    const title = "Shared List Deleted";
    const body = `${name} was deleted by the owner`;

    await sendMulticastToUids(audience, { title, body }, {
      type: "LIST_DELETED",
      listUuid: uuid,
      listName: name,
    });
  }
);

// Notify the OWNER when a MEMBER leaves the list (voluntary leave = status update)
exports.notifyOwnerWhenMemberLeaves = onDocumentUpdated(
  "sharedLists/{uuid}/sharedWith/{uid}",
  async (event) => {
    const { uuid, uid } = event.params;
    const before = event.data?.before?.data() || {};
    const after = event.data?.after?.data() || {};

    // Only notify if status changed to 'removed'
    if (before.status === "removed" || after.status !== "removed") return;

    const db = getFirestore();
    const listSnap = await db.collection("sharedLists").doc(uuid).get();
    if (!listSnap.exists) return;

    const listData = listSnap.data() || {};
    const ownerId = listData.ownerId || listData.createdBy || null;
    if (!ownerId) return;

    // Try to show the leaver's display name
    let leaverName = null;
    try {
      const leaverDoc = await db.collection("users").doc(uid).get();
      leaverName = leaverDoc.get("displayName") || null;
    } catch (_) { }

    const title = "Member Left Your List";
    const body = leaverName
      ? `${leaverName} left ${listData.name || "your shared list"}`
      : `A member left ${listData.name || "your shared list"}`;

    await sendMulticastToUids([ownerId], { title, body }, {
      type: "MEMBER_LEFT",
      listUuid: uuid,
      listName: listData.name || "",
      leaverUid: uid,
    });
  }
);

// Notify the OWNER when a MEMBER is added to sharedWith (i.e., joins the list)
// Handles both new joins (create) and re-joins (update from 'removed' -> 'pending'/'accepted')
exports.notifyOwnerWhenMemberJoins = onDocumentWritten(
  "sharedLists/{uuid}/sharedWith/{uid}",
  async (event) => {
    const { uuid, uid } = event.params;
    const after = event.data?.after?.data(); // If create or update, this exists
    const before = event.data?.before?.data(); // If update or delete, this exists

    // If deleted, ignore (handled by notifyOwnerWhenMemberLeaves or just ignoring)
    if (!after) return;

    // Check if meaningful join:
    // 1. New doc (before is undefined/null)
    // 2. Status changed from 'removed' to something else (re-join)
    // 3. (Optional) Status changed from 'pending' to 'accepted' (if you want to notify on accept)

    const isNew = !before;
    const wasRemoved = before && before.status === "removed";
    const isNowJoined = after.status !== "removed"; // Assuming pending/accepted means joined

    if (!isNowJoined) return; // They are still removed or just updated timestamp while removed

    // If it's an update, only notify if they were previously removed
    if (!isNew && !wasRemoved) return;

    const db = getFirestore();

    // Get list meta to find owner + name
    const listSnap = await db.collection("sharedLists").doc(uuid).get();
    if (!listSnap.exists) return;
    const listData = listSnap.data() || {};
    const ownerId = listData.ownerId || listData.createdBy || null;
    if (!ownerId) return;

    // Resolve member's display name for a nicer message
    let memberName = uid;
    try {
      const userDoc = await db.collection("users").doc(uid).get();
      memberName = userDoc.get("displayName") || memberName;
    } catch (_) { }

    const title = wasRemoved ? "Member Re-joined" : "New Member Joined";
    const body = `${memberName} joined ${listData.name || "your shared list"}`;

    // Reuse your existing FCM helper to notify the owner
    await sendMulticastToUids([ownerId], { title, body }, {
      type: "MEMBER_JOINED",
      listUuid: uuid,
      listName: listData.name || "",
      memberUid: uid,
    });
  }
);

// cleanup: When a member is removed from a shared list, delete the reference in users/{uid}/lists
exports.cleanupUserListRefOnRemoval = onDocumentWritten(
  "sharedLists/{uuid}/sharedWith/{uid}",
  async (event) => {
    const { uuid, uid } = event.params;
    const after = event.data?.after?.data();
    const before = event.data?.before?.data();

    // Check if removed:
    // 1. Document deleted (after is null)
    // 2. Status changed to 'removed'
    const isDeleted = !after;
    const isRemovedStatus = after && after.status === "removed";

    // If not deleted and not removed status, do nothing
    if (!isDeleted && !isRemovedStatus) return;

    // Double check it wasn't ALREADY removed/deleted (idempotency)
    // If before was already removed/null, we might have already run.
    // But if before was "pending" or "accepted", and now is "removed", we run.
    if (!isDeleted && before && before.status === "removed") return;

    // Perform cleanup
    const db = getFirestore();
    console.log(`Cleaning up list ref for user ${uid} (list ${uuid})`);

    try {
      // The user stores references in users/{uid}/lists
      // The doc might be keyed by 'uuid' or 'refUuid' = uuid.
      // Usually the doc ID is random or the same as the shared list ID.
      // We search for it.
      const userListsRef = db.collection("users").doc(uid).collection("lists");

      // Query by 'uuid' matches
      const byUuid = await userListsRef.where("uuid", "==", uuid).get();
      // Query by 'refUuid' matches (legacy/mirror logic)
      const byRefUuid = await userListsRef.where("refUuid", "==", uuid).get();

      const batch = db.batch();
      let deleteCount = 0;

      byUuid.forEach((doc) => {
        batch.delete(doc.ref);
        deleteCount++;
      });
      byRefUuid.forEach((doc) => {
        // Avoid deleting same doc twice if queries overlap
        // (Firestore batch.delete handles same ref ok, but let's be safe)
        // Note: Firestore query results are disjoint unless we merge them manually? 
        // Actually, a doc has either uuid OR refUuid usually, or both? 
        // Just delete by ref.
        batch.delete(doc.ref);
        deleteCount++;
      });

      if (deleteCount > 0) {
        await batch.commit();
        console.log(`Deleted ${deleteCount} list references for user ${uid}`);
      } else {
        console.log(`No local list references found for user ${uid} to delete.`);
      }

    } catch (err) {
      console.error("Failed to cleanup user list reference:", err);
    }
  }
);

// Notify when a task's PRIORITY changes (include X -> Y and task name)
exports.notifyOnTaskPriorityChanged = onDocumentUpdated(
  "sharedLists/{uuid}/tasks/{taskId}",
  async (event) => {
    const { uuid, taskId } = event.params;
    const before = event.data?.before?.data() || {};
    const after = event.data?.after?.data() || {};

    const oldP = before.priority;
    const newP = after.priority;
    if (oldP == null || newP == null || oldP === newP) return;

    const taskTitle =
      after.title ||
      after.name ||
      after.description ||
      before.title ||
      before.name ||
      before.description ||
      `Task #${taskId}`;

    const meta = await getSharedListMetaAndMembers(uuid);
    if (!meta) return;

    // Optional: exclude actor if you store updatedBy
    const actorUid = after.updatedBy || before.updatedBy || null;
    const audience = new Set(meta.memberUids);
    if (meta.ownerId) audience.add(meta.ownerId);
    if (actorUid) audience.delete(actorUid);

    const audienceList = Array.from(audience);
    if (!audienceList.length) return;

    await sendMulticastToUids(
      audienceList,
      {
        title: "Task Priority Changed",
        body: `“${taskTitle}”: ${oldP} → ${newP} in ${meta.listName}`,
      },
      {
        type: "TASK_PRIORITY_CHANGED",
        listUuid: uuid,
        listName: meta.listName,
        taskId: String(taskId || ""),
        taskName: taskTitle,
        oldPriority: String(oldP),
        newPriority: String(newP),
        note: `Priority changed from ${oldP} -> ${newP}`,
      }
    );
  }
);

// Notify when someone clears the completed tasks (single summary push)
exports.notifyOnClearCompleted = onDocumentCreated(
  "sharedLists/{uuid}/ops/clearCompleted",
  async (event) => {
    const db = getFirestore();
    const { uuid } = event.params;
    const op = event.data?.data() || {};

    // Who did it? (client should set actorUid; we'll look up their name)
    const actorUid = op.actorUid || op.userId || null;

    // List meta + audience
    const meta = await getSharedListMetaAndMembers(uuid);
    if (!meta) return;

    // Resolve actor display name
    let actorName = "Someone";
    if (actorUid) {
      try {
        const userDoc = await db.collection("users").doc(actorUid).get();
        actorName = userDoc.get("displayName") || actorName;
      } catch (_) { }
    }

    // Audience: owner + members (optionally exclude actor)
    const audience = new Set(meta.memberUids);
    if (meta.ownerId) audience.add(meta.ownerId);
    if (actorUid) audience.delete(actorUid);

    const audienceList = Array.from(audience);
    if (!audienceList.length) return;

    await sendMulticastToUids(
      audienceList,
      {
        title: "Completed Tasks Cleared",
        body: `${actorName} cleared the completed tasks in ${meta.listName}`,
      },
      {
        type: "CLEAR_COMPLETED",
        listUuid: uuid,
        listName: meta.listName,
        actorUid: actorUid || "",
      }
    );
  }
);
