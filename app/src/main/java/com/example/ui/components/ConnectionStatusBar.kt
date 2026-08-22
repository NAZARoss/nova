package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.network.p2p.P2PConnectionState
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusOnline
import com.example.ui.theme.StatusWaiting

@Composable
fun ConnectionStatusBar(
    connectionState: P2PConnectionState,
    modifier: Modifier = Modifier
) {
    val isSearching = connectionState == P2PConnectionState.SEARCHING || connectionState == P2PConnectionState.CONNECTING
    val isOffline = connectionState == P2PConnectionState.OFFLINE

    AnimatedVisibility(
        visible = isSearching || isOffline,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .background(
                    if (isOffline) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
                    else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                )
                .padding(vertical = 4.dp, horizontal = 16.dp)
                .testTag("connection_status_bar"),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(
                            if (isOffline) StatusError else StatusWaiting
                        )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isOffline) stringResource(R.string.waiting_status) else stringResource(R.string.connecting_status),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    color = if (isOffline) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}
