package com.m57.hermescontrol.ui.chat.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.m57.hermescontrol.R
import com.m57.hermescontrol.ui.chat.ChatMessage

/**
 * Fork dialog for the per-message action bar (batch-8 F1), OpenWebUI-style.
 *
 * AVAILABILITY HONESTY: `session.branch` / `session.branch_whole` take only
 * `session_id` + `name` — the gateway has NO from-index anchor, so "Fork from
 * this message" cannot branch at a point and falls back to branching the
 * whole conversation; the dialog says so under the radio option. Both paths
 * go through the one confirmed RPC slot ([onFork], wired to
 * ChatViewModel.forkActiveSession), which switches to the new session the
 * moment the branch result lands.
 */
@Composable
fun MessageForkDialog(
    conversationTitle: String,
    onDismiss: () -> Unit,
    onFork: (name: String?) -> Unit,
) {
    var forkFromHere by rememberSaveable { mutableStateOf(true) }
    var forkName by rememberSaveable {
        mutableStateOf(
            "Fork of ${conversationTitle.takeIf { it.isNotBlank() } ?: "conversation"}",
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_msg_fork_title)) },
        text = {
            Column {
                ForkOption(
                    selected = forkFromHere,
                    title = stringResource(R.string.chat_msg_fork_from_here),
                    note = stringResource(R.string.chat_msg_fork_from_here_note),
                    onClick = { forkFromHere = true },
                )
                Spacer(modifier = Modifier.height(4.dp))
                ForkOption(
                    selected = !forkFromHere,
                    title = stringResource(R.string.chat_msg_fork_whole),
                    note = null,
                    onClick = { forkFromHere = false },
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = forkName,
                    onValueChange = { forkName = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.chat_msg_fork_name_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onFork(forkName.trim().ifBlank { null }) }) {
                Text(stringResource(R.string.chat_msg_action_fork))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

@Composable
private fun ForkOption(
    selected: Boolean,
    title: String,
    note: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            modifier = Modifier.size(36.dp),
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (note != null) {
                Text(
                    text = note,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Edit dialog for the per-message action bar (batch-8 F1). Saves into the
 * ViewModel's UI-only display override map — the gateway has NO message.edit
 * RPC, so this is render-side-only, resets on session reload, and the dialog
 * says so. [current] is the effective (possibly already-overridden) message —
 * it preseeds the text fields; unchanged-vs-server diffing happens in
 * ChatViewModel.setMessageDisplayOverride.
 */
@Composable
fun MessageEditDialog(
    current: ChatMessage,
    onDismiss: () -> Unit,
    onSave: (reasoning: String, content: String) -> Unit,
) {
    var reasoningText by rememberSaveable { mutableStateOf(current.reasoningText) }
    var contentText by rememberSaveable { mutableStateOf(current.content) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_msg_edit_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.chat_msg_edit_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = reasoningText,
                    onValueChange = { reasoningText = it },
                    label = { Text(stringResource(R.string.chat_msg_edit_thinking_label)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = contentText,
                    onValueChange = { contentText = it },
                    label = { Text(stringResource(R.string.chat_msg_edit_response_label)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(reasoningText, contentText) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}
