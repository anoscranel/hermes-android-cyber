package com.m57.hermescontrol.ui.chat.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.m57.hermescontrol.R

/**
 * Affordances of the per-message action bar (batch-8 F1). A null callback
 * HIDES that action, so callers build the row with only the buttons that
 * make sense for the message (e.g. a user bubble keeps just Revert).
 */
data class ChatMessageActions(
    val onSaveImage: (() -> Unit)? = null,
    val onFork: (() -> Unit)? = null,
    val onEdit: (() -> Unit)? = null,
    val onTts: (() -> Unit)? = null,
    val onRetry: (() -> Unit)? = null,
    val onRevert: (() -> Unit)? = null,
)

/**
 * Compact icon row rendered directly under a chat message bubble: every
 * affordance is a 28dp IconButton with a 16dp icon, ordered
 * image → fork → edit → tts → retry → revert per the batch-8 spec.
 * Server-capability honesty lives in the OWNERS of the callbacks
 * (ChatViewModel) — this composable is pure presentation.
 */
@Composable
fun ChatMessageActionsRow(
    actions: ChatMessageActions,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (actions.onSaveImage != null) {
            ActionButton(
                icon = Icons.Outlined.Image,
                contentDescRes = R.string.chat_msg_action_image,
                testTag = "msg_action_image",
                onClick = actions.onSaveImage,
            )
        }
        if (actions.onFork != null) {
            ActionButton(
                icon = Icons.AutoMirrored.Filled.CallSplit,
                contentDescRes = R.string.chat_msg_action_fork,
                testTag = "msg_action_fork",
                onClick = actions.onFork,
            )
        }
        if (actions.onEdit != null) {
            ActionButton(
                icon = Icons.Outlined.Edit,
                contentDescRes = R.string.chat_msg_action_edit,
                testTag = "msg_action_edit",
                onClick = actions.onEdit,
            )
        }
        if (actions.onTts != null) {
            ActionButton(
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescRes = R.string.chat_msg_action_tts,
                testTag = "msg_action_tts",
                onClick = actions.onTts,
            )
        }
        if (actions.onRetry != null) {
            ActionButton(
                icon = Icons.Outlined.Refresh,
                contentDescRes = R.string.chat_msg_action_retry,
                testTag = "msg_action_retry",
                onClick = actions.onRetry,
            )
        }
        if (actions.onRevert != null) {
            ActionButton(
                icon = Icons.AutoMirrored.Outlined.Undo,
                contentDescRes = R.string.chat_msg_action_revert,
                testTag = "msg_action_revert",
                onClick = actions.onRevert,
            )
        }
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    @StringRes contentDescRes: Int,
    testTag: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(28.dp).testTag(testTag),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(contentDescRes),
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
