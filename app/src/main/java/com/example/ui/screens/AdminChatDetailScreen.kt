package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Message
import com.example.data.model.MessageSender
import com.example.viewmodel.AdminViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminChatDetailScreen(
    userId: String,
    adminViewModel: AdminViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val messages by adminViewModel.selectedUserMessages.collectAsStateWithLifecycle()
    val replyText by adminViewModel.replyInputText.collectAsStateWithLifecycle()
    val allUsers by adminViewModel.allUsers.collectAsStateWithLifecycle()
    val flashlightStates by adminViewModel.userFlashlightStates.collectAsStateWithLifecycle()
    val bloodRedStates by adminViewModel.userBloodRedStates.collectAsStateWithLifecycle()

    val currentUser = allUsers.find { it.id == userId }
    val userRole = currentUser?.selectedAiRole ?: "Nova Assistant"

    var showPrankPanel by remember { mutableStateOf(false) }
    var showBrowserDialog by remember { mutableStateOf(false) }
    var browserQueryInput by remember { mutableStateOf("") }

    val isFlashlightOn = flashlightStates[userId] ?: false
    val isBloodRedOn = bloodRedStates[userId] ?: false

    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val quickTemplates = listOf(
        "Hello! How can I assist you with your question today?",
        "I have analyzed this for you. Here are the key points:",
        "Certainly! Let's break this down step-by-step:",
        "Everything looks good. Is there anything else you'd like to explore?"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "User #${userId.takeLast(4)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = userRole,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Direct Neural P2P / Colab Stream",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            adminViewModel.clearSelectedChat()
                            onBackClick()
                        },
                        modifier = Modifier.testTag("admin_chat_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    // Prank Button to expand fun controls
                    FilledTonalButton(
                        onClick = { showPrankPanel = !showPrankPanel },
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("admin_prank_menu_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Celebration,
                            contentDescription = "Pranks",
                            modifier = Modifier.size(16.dp),
                            tint = if (showPrankPanel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (showPrankPanel) "Hide Pranks" else "Pranks",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Collapsible Prank Controls Panel
                    AnimatedVisibility(
                        visible = showPrankPanel,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Sensors,
                                        contentDescription = "Effects",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "TRIGGER REAL-TIME PRANKS ON CLIENT SCREEN",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                 Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    // 1. Blink screen quickly
                                    ElevatedButton(
                                        onClick = { adminViewModel.triggerBlink(userId) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("prank_blink_btn"),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Visibility,
                                            contentDescription = "Blink",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Blink", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    // 2. Toggle Flashlight
                                    ElevatedButton(
                                        onClick = { adminViewModel.toggleFlashlight(userId) },
                                        colors = ButtonDefaults.elevatedButtonColors(
                                            containerColor = if (isFlashlightOn) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("prank_flashlight_btn"),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isFlashlightOn) Icons.Outlined.FlashlightOff else Icons.Default.FlashlightOn,
                                            contentDescription = "Flashlight",
                                            tint = if (isFlashlightOn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isFlashlightOn) "Flash OFF" else "Flash ON",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isFlashlightOn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    // 3. Blood Red Mode
                                    ElevatedButton(
                                        onClick = { adminViewModel.toggleBloodRed(userId) },
                                        colors = ButtonDefaults.elevatedButtonColors(
                                            containerColor = if (isBloodRedOn) Color(0xFF8B0000) else MaterialTheme.colorScheme.surface
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("prank_blood_red_btn"),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.InvertColors,
                                            contentDescription = "Blood Red",
                                            tint = if (isBloodRedOn) Color.White else Color(0xFF8B0000),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isBloodRedOn) "Red OFF" else "Blood Red",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isBloodRedOn) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    // 4. Open Camera (Front)
                                    ElevatedButton(
                                        onClick = { adminViewModel.openFrontCamera(userId) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("prank_camera_btn"),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CameraAlt,
                                            contentDescription = "Front Camera",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Front Camera", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    // 5. Open Browser (Chrome)
                                    ElevatedButton(
                                        onClick = {
                                            browserQueryInput = ""
                                            showBrowserDialog = true
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("prank_browser_btn"),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Language,
                                            contentDescription = "Open Browser",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.secondary
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Open Chrome", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // Quick reply template chips
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(quickTemplates) { template ->
                            FilterChip(
                                selected = false,
                                onClick = { adminViewModel.setReplyTemplate(template) },
                                label = {
                                    Text(
                                        text = template.take(28) + "...",
                                        fontSize = 12.sp
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = replyText,
                            onValueChange = { adminViewModel.onReplyInputChanged(it) },
                            placeholder = { Text("Reply as '$userRole'...") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("admin_reply_input"),
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ),
                            maxLines = 4,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = { adminViewModel.sendReply(userId = userId) }
                            )
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        FilledIconButton(
                            onClick = { adminViewModel.sendReply(userId = userId) },
                            enabled = replyText.isNotBlank(),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("admin_send_reply_button"),
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send Reply",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        },
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            items(messages, key = { it.id }) { msg ->
                val isAdmin = msg.sender == MessageSender.AI_ADMIN
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                val formattedTime = timeFormat.format(Date(msg.timestamp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = if (isAdmin) Arrangement.End else Arrangement.Start
                ) {
                    if (!isAdmin) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "User",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                    }

                    Column(
                        modifier = Modifier.widthIn(max = 290.dp),
                        horizontalAlignment = if (isAdmin) Alignment.End else Alignment.Start
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isAdmin) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            tonalElevation = 1.dp
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                Text(
                                    text = if (isAdmin) "You (as $userRole)" else "User #${userId.takeLast(4)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isAdmin) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = msg.text,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isAdmin) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = formattedTime,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.align(Alignment.End)
                                )
                            }
                        }
                    }

                    if (isAdmin) {
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Admin / AI",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showBrowserDialog) {
        val suggestions = listOf("котики", "мемы 2026", "как взломать пентагон", "youtube.com", "google.com")

        AlertDialog(
            onDismissRequest = { showBrowserDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "Launch Chrome / Search",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Enter a search query or URL to immediately launch Google Chrome on User #${userId.takeLast(4)}'s phone:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = browserQueryInput,
                        onValueChange = { browserQueryInput = it },
                        placeholder = { Text("e.g. funny memes, cute cats, https://...") },
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("browser_query_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Text(
                        text = "Quick suggestions:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(suggestions) { suggestion ->
                            SuggestionChip(
                                onClick = { browserQueryInput = suggestion },
                                label = { Text(suggestion, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        if (browserQueryInput.isNotBlank()) {
                            adminViewModel.openBrowser(userId, browserQueryInput)
                            showBrowserDialog = false
                            browserQueryInput = ""
                        }
                    },
                    enabled = browserQueryInput.isNotBlank(),
                    modifier = Modifier.testTag("confirm_open_browser_btn")
                ) {
                    Text("Launch in Chrome")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showBrowserDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
