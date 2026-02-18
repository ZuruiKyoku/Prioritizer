package com.slygames.prioritizer.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.slygames.prioritizer.data.list.SharedListInvite
import com.slygames.prioritizer.data.user.FriendProfile

@Composable
fun FriendRequestItem(
    friend: FriendProfile,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!friend.profileImageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = friend.profileImageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = friend.displayName,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { onDecline(friend.uid) },
                modifier = Modifier.weight(1f)
            ) {
                Text("Decline")
            }
            Button(
                onClick = { onAccept(friend.uid) },
                modifier = Modifier.weight(1f)
            ) {
                Text("Accept")
            }
        }
    }
}

@Composable
fun SharedListInviteItem(
    invite: SharedListInvite,
    onAcceptSharedList: (SharedListInvite) -> Unit,
    onDeclineSharedList: (SharedListInvite) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp)
    ) {
        Text(
            text = invite.listName,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { onDeclineSharedList(invite) },
                modifier = Modifier.weight(1f)
            ) {
                Text("Decline")
            }
            Button(
                onClick = { onAcceptSharedList(invite) },
                modifier = Modifier.weight(1f)
            ) {
                Text("Accept")
            }
        }
    }
}