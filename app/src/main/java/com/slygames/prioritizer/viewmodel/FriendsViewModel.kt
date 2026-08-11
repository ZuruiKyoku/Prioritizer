package com.slygames.prioritizer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.slygames.prioritizer.data.user.FriendProfile
import com.slygames.prioritizer.data.user.FriendRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Backs the Friends screen: the accepted friends list, adding by code, and removal. Incoming/
 * outgoing *requests* live in [RequestsViewModel] instead -- kept separate because the Requests
 * dialog and the Friends tab have different lifetimes/screens (spec: keep ViewModels focused). */
@HiltViewModel
class FriendsViewModel @Inject constructor(private val friendRepository: FriendRepository) : ViewModel() {

    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()

    val friends: StateFlow<List<FriendProfile>> =
            friendRepository.getFriendList().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addFriend(code: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val (success, error) = friendRepository.sendFriendRequest(code)
            onResult(success, error)
        }
    }

    fun removeFriend(friend: FriendProfile) {
        viewModelScope.launch {
            val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
            friendRepository.removeFriend(myUid, friend)
        }
    }
}
