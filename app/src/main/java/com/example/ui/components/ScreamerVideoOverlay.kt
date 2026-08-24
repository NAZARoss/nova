package com.example.ui.components

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.util.Log
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun ScreamerVideoOverlay(
    videoUrl: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    var previousVolume by remember { mutableStateOf(-1) }
    var isBuffering by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        audioManager?.let { am ->
            try {
                previousVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, maxVol, 0)
            } catch (e: Exception) {
                Log.e("ScreamerVideoOverlay", "Failed to set audio volume: ${e.message}")
            }
        }

        onDispose {
            if (previousVolume >= 0) {
                try {
                    audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, previousVolume, 0)
                } catch (e: Exception) {
                    Log.e("ScreamerVideoOverlay", "Failed to restore audio volume: ${e.message}")
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("screamer_video_overlay"),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    setVideoURI(Uri.parse(videoUrl))
                    setOnPreparedListener { mp ->
                        isBuffering = false
                        try {
                            mp.setVolume(1.0f, 1.0f)
                        } catch (e: Exception) {
                            Log.e("ScreamerVideoOverlay", "Volume set error: ${e.message}")
                        }
                        start()
                    }
                    setOnCompletionListener {
                        onDismiss()
                    }
                    setOnErrorListener { _, what, extra ->
                        Log.e("ScreamerVideoOverlay", "Video error: what=$what, extra=$extra")
                        isBuffering = false
                        onDismiss()
                        true
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(
            visible = isBuffering,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            CircularProgressIndicator(
                color = Color.Red,
                modifier = Modifier.size(48.dp)
            )
        }

        // Close button on top-right corner
        IconButton(
            onClick = onDismiss,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.Black.copy(alpha = 0.5f),
                contentColor = Color.White
            ),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(16.dp)
                .testTag("close_screamer_btn")
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close Screamer",
                tint = Color.White
            )
        }
    }
}
