package com.slygames.prioritizer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slygames.prioritizer.data.list.SharedListInvite
import com.slygames.prioritizer.data.sync.SyncManager
import com.slygames.prioritizer.data.user.FriendProfile
import com.slygames.prioritizer.data.user.FriendRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs the Requests dialog: Friend Requests tab first, Shared Lists tab second (spec order).
 * Scoped to the "tasks" nav destination, so it stays alive (and [showDialog] keeps its value)
 * while the user navigates to other screens and back during the same session -- a fresh process
 * launch always creates a fresh instance, so state never survives a full app restart.
 */
@HiltViewModel
class RequestsViewModel
@Inject
constructor(private val friendRepository: FriendRepository, private val syncManager: SyncManager) : ViewModel() {

    private val _showDialog = MutableStateFlow(true)
    val showDialog: StateFlow<Boolean> = _showDialog

    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()

    fun dismissDialog() {
        _showDialog.value = false
    }

    fun reopenDialog() {
        _showDialog.value = true
    }

    val incomingFriendRequests: StateFlow<List<FriendProfile>> =
            friendRepository.getIncomingFriendRequests()
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val incomingSharedListInvites: StateFlow<List<SharedListInvite>> =
            syncManager.getIncomingSharedLists()
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingSentFriendRequests =
            friendRepository.getPendingSentFriendRequests()
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingSentListShares =
            syncManager.getPendingSentListShares()
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalPendingSentCount: StateFlow<Int> =
            combine(pendingSentFriendRequests, pendingSentListShares) { friends, lists -> friends.size + lists.size }
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun acceptFriendRequest(fromUid: String) {
        viewModelScope.launch {
            friendRepository.acceptFriendRequest(fromUid)
                    .onSuccess { name -> _events.emit("You're now friends with $name") }
                    .onFailure { _events.emit("Couldn't accept that request. Try again.") }
        }
    }

    fun declineFriendRequest(fromUid: String) {
        friendRepository.declineFriendRequest(fromUid)
    }

    fun acceptSharedListInvite(uuid: String) {
        viewModelScope.launch {
            syncManager.acceptSharedListInvite(uuid).onFailure {
                _events.emit("Invite not ready or expired. Ask the owner to re-share.")
            }
        }
    }

    fun declineSharedListInvite(uuid: String) {
        viewModelScope.launch { syncManager.declineSharedListInvite(uuid) }
    }
}
