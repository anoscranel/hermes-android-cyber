package com.m57.hermescontrol.ui.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.m57.hermescontrol.R

/**
 * Direction-aware floating scroll action (issue #682, batch-5B B5), docked at
 * the bottom start of the message list. Down state: when the reader is NOT at
 * the bottom, tapping resumes bottom-follow and clears the pending (unseen)
 * update count, which renders as a badge. Up state: when the reader IS at the
 * bottom but history exists above, tapping jumps to the top of the
 * conversation (index 0); the badge only belongs to the down state.
 *
 * @param show whether the FAB is visible at all (either direction state).
 * @param scrollsUp true for the up state; defaults to false so existing
 *   down-state-only callers keep their behavior.
 * @param pendingCount number of tail updates that arrived while follow was
 *   paused — rendered as a badge on the down state only.
 * @param onScrollToBottom invoked on down-state tap; should call
 *   [ChatScrollController.resumeFollowing].
 * @param onScrollToTop invoked on up-state tap; should call
 *   [ChatScrollController.jumpToTop].
 */
@Composable
fun BoxScope.ChatScrollToBottomFab(
    show: Boolean,
    pendingCount: Int,
    onScrollToBottom: () -> Unit,
    scrollsUp: Boolean = false,
    onScrollToTop: () -> Unit = {},
) {
    AnimatedVisibility(
        visible = show,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
        modifier =
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 16.dp),
    ) {
        BadgedBox(
            badge = {
                if (!scrollsUp && pendingCount > 0) {
                    Badge {
                        val label = if (pendingCount > 99) "99+" else pendingCount.toString()
                        Text(label)
                    }
                }
            },
        ) {
            FloatingActionButton(
                onClick = if (scrollsUp) onScrollToTop else onScrollToBottom,
                modifier = Modifier.size(40.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Icon(
                    imageVector =
                        if (scrollsUp) {
                            Icons.Filled.KeyboardArrowUp
                        } else {
                            Icons.Filled.KeyboardArrowDown
                        },
                    contentDescription =
                        stringResource(
                            if (scrollsUp) {
                                R.string.content_desc_scroll_to_top
                            } else {
                                R.string.content_desc_scroll_to_bottom
                            },
                        ),
                )
            }
        }
    }
}
