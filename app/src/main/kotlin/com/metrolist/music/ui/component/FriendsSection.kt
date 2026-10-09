package com.metrolist.music.ui.component

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.R
import com.metrolist.music.listentogether.ListenTogetherManager

@Composable
fun FriendsSection(
    manager: ListenTogetherManager,
    onJoinRoom: (String) -> Unit
) {
    val friendCode by manager.friendCode.collectAsStateWithLifecycle()
    val friends by manager.friends.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showAddFriendDialog by remember { mutableStateOf(false) }
    
    if (showAddFriendDialog) {
        AddFriendDialog(
            onDismiss = { showAddFriendDialog = false },
            onAddFriend = { code -> 
                manager.addFriend(code)
                showAddFriendDialog = false
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Friend Code Display
            Text(
                text = "Your Friend Code to Share:",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = friendCode ?: "Loading...",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 6.sp,
                textAlign = TextAlign.Center,
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            FilledTonalButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("Friend Code", friendCode ?: "")
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
                },
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.content_copy),
                    contentDescription = stringResource(R.string.copy_code),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Copy Friend Code")
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(20.dp))

            // Friends List
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Friends",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            if (friends.isEmpty()) {
                Text(
                    text = "No friends added yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                friends.forEach { friend ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(RoundedCornerShape(50))
                                .background(if (friend.isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        )
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (friend.username.isEmpty()) "Unknown" else friend.username,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (friend.activeRoomCode.isNotEmpty()) {
                                Text(
                                    text = "In a room",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Text(
                                    text = if (friend.isOnline) "Online" else "Offline",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }

                        if (friend.activeRoomCode.isNotEmpty()) {
                            Button(
                                onClick = { onJoinRoom(friend.activeRoomCode) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Join")
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        IconButton(
                            onClick = { manager.removeFriend(friend.friendCode) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.close),
                                contentDescription = "Remove Friend",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = { showAddFriendDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.add),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Friend")
            }
        }
    }
}

@Composable
fun AddFriendDialog(onDismiss: () -> Unit, onAddFriend: (String) -> Unit) {
    var friendCodeInput by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Friend") },
        text = {
            OutlinedTextField(
                value = friendCodeInput,
                onValueChange = { raw ->
                    friendCodeInput = raw.uppercase().filter { it.isLetterOrDigit() }.take(9)
                },
                label = { Text("Friend Code") },
                placeholder = { Text("XXX-XXX-XXX") },
                singleLine = true,
                visualTransformation = { text ->
                    val trimmed = text.text
                    var out = ""
                    for (i in trimmed.indices) {
                        out += trimmed[i]
                        if (i == 2 || i == 5) out += "-"
                    }
                    val offsetMapping = object : OffsetMapping {
                        override fun originalToTransformed(offset: Int): Int {
                            if (offset <= 2) return offset
                            if (offset <= 5) return offset + 1
                            return offset + 2
                        }
                        override fun transformedToOriginal(offset: Int): Int {
                            if (offset <= 3) return offset
                            if (offset <= 7) return offset - 1
                            return offset - 2
                        }
                    }
                    TransformedText(AnnotatedString(out), offsetMapping)
                }
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val formatted = friendCodeInput.chunked(3).joinToString("-")
                    if (formatted.length == 11) {
                        onAddFriend(formatted)
                    }
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}
