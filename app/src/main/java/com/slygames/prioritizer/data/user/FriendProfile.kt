package com.slygames.prioritizer.data.user

import com.google.firebase.firestore.PropertyName

data class FriendProfile(
    @get:PropertyName("friendUid") @set:PropertyName("friendUid")
    var uid: String = "",

    var displayName: String = "",
    val profileImageUrl: String? = null,
    var userId: String = ""
)