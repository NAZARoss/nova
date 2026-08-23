package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.PrankCommands
import com.example.ui.components.AITypingIndicator
import com.example.ui.components.ChatMessageBubble
import com.example.ui.components.ConnectionStatusBar
import com.example.ui.components.InteractiveSpotlightTutorialOverlay
import com.example.ui.components.OnboardingAiCapabilitiesDialog
import com.example.ui.components.OnboardingWarningDialog
import com.example.ui.components.UserChatTopBar
import com.example.util.FlashlightHelper
import com.example.viewmodel.OnboardingStep
import com.example.viewmodel.UserChatViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UserChatScreen(
    viewModel: UserChatViewModel,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isWaitingForReply by viewModel.isWaitingForReply.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val colabServerUrl by viewModel.colabServerUrl.collectAsStateWithLifecycle()
    val colabStatus by viewModel.colabConnectionStatus.collectAsStateWithLifecycle()
    val aiRole by viewModel.aiRole.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()

    // Blinking effect state
    var isBlinkWhite by remember { mutableStateOf(false) }

    // Hardware Flashlight controller
    val flashlightHelper = remember { FlashlightHelper(context) }
    DisposableEffect(Unit) {
        onDispose {
            flashlightHelper.setTorch(false)
        }
    }

    // Listen to incoming Prank events from Admin
    LaunchedEffect(Unit) {
        viewModel.prankEvents.collect { prankType ->
            when (prankType) {
                PrankCommands.TYPE_BLINK -> {
                    repeat(8) {
                        isBlinkWhite = true
                        delay(80)
                        isBlinkWhite = false
                        delay(80)
                    }
                }
                PrankCommands.TYPE_FLASHLIGHT_ON -> {
                    flashlightHelper.setTorch(true)
                }
                PrankCommands.TYPE_FLASHLIGHT_OFF -> {
                    flashlightHelper.setTorch(false)
                }
                PrankCommands.TYPE_BLOOD_RED_ON -> {
                    viewModel.setBloodRedMode(true)
                }
                PrankCommands.TYPE_BLOOD_RED_OFF -> {
                    viewModel.setBloodRedMode(false)
                }
            }
        }
    }

    // Auto-scroll on new message or typing state change
    LaunchedEffect(messages.size, isWaitingForReply) {
        val totalCount = messages.size + if (isWaitingForReply) 1 else 0
        if (totalCount > 0) {
            listState.animateScrollToItem(totalCount - 1)
        }
    }

    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val todayTime = timeFormat.format(Date())

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                Column {
                    UserChatTopBar(
                        onClearChatClick = { viewModel.openClearDialog() },
                        onSettingsClick = onNavigateToSettings,
                        onRoleClick = { viewModel.openRoleEditDialog() },
                        aiRole = aiRole,
                        isColabConfigured = colabServerUrl.isNotBlank(),
                        colabStatus = colabStatus
                    )
                    if (colabServerUrl.isBlank()) {
                        ConnectionStatusBar(connectionState = connectionState)
                    }
                }
            },
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = uiState.inputText,
                            onValueChange = { viewModel.onInputTextChanged(it) },
                            enabled = !isWaitingForReply,
                            placeholder = {
                                Text(
                                    text = if (isWaitingForReply) stringResource(R.string.generating_response) else stringResource(R.string.input_hint),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isWaitingForReply) 0.8f else 0.6f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_input_field"),
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            ),
                            maxLines = 4,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = { if (!isWaitingForReply) viewModel.sendMessage() }
                            )
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        FilledIconButton(
                            onClick = { viewModel.sendMessage() },
                            enabled = uiState.inputText.isNotBlank() && !isWaitingForReply,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("send_message_button"),
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = if (uiState.inputText.isNotBlank() && !isWaitingForReply) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Clean Minimal Version Footer
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "VERSION 1.0.4-BUILD_STABLE",
                            style = MaterialTheme.typography.labelSmall,
                            letterSpacing = 1.5.sp,
                            fontSize = 8.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                if (messages.isEmpty() && !isWaitingForReply) {
                    EmptyChatHero(
                        onSelectPrompt = { prompt ->
                            viewModel.selectPromptChip(prompt)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(top = 14.dp, bottom = 16.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("messages_list")
                    ) {
                        // Minimal date timestamp badge
                        item(key = "date_header") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                    )
                                ) {
                                    Text(
                                        text = "TODAY, $todayTime",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = 1.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        items(messages, key = { it.id }) { message ->
                            ChatMessageBubble(message = message)
                        }

                        if (isWaitingForReply) {
                            item(key = "generating_indicator") {
                                AITypingIndicator()
                            }
                        }
                    }
                }
            }
        }

        // Blood Red Overlay effect
        if (uiState.isBloodRedActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF8B0000).copy(alpha = 0.75f))
            )
        }

        // White Flash / Blink Overlay effect
        if (isBlinkWhite) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
            )
        }

        // Interactive Spotlight Tutorial Overlay
        if (uiState.onboardingStep in listOf(
                OnboardingStep.TUTORIAL_ROLE,
                OnboardingStep.TUTORIAL_INPUT,
                OnboardingStep.TUTORIAL_SETTINGS,
                OnboardingStep.TUTORIAL_CLEAR
            )
        ) {
            InteractiveSpotlightTutorialOverlay(
                step = uiState.onboardingStep,
                onNextStep = { viewModel.advanceOnboarding() },
                onSkip = { viewModel.skipOnboarding() }
            )
        }
    }

    // Step 1: Warning Dialog with Red Text ("ВНИМАНИЕ ЭТО ВАЖНО ПРОЧИТАТЬ")
    if (uiState.onboardingStep == OnboardingStep.WARNING) {
        OnboardingWarningDialog(
            onConfirm = { viewModel.advanceOnboarding() }
        )
    }

    // Step 2: AI Capabilities modal
    if (uiState.onboardingStep == OnboardingStep.AI_CAPABILITIES) {
        OnboardingAiCapabilitiesDialog(
            onContinueToTutorial = { viewModel.advanceOnboarding() }
        )
    }

    if (uiState.showClearDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissClearDialog() },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text(text = "Clear conversation?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    text = "This will permanently delete your local chat history on this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmClearChat() },
                    modifier = Modifier.testTag("confirm_clear_button")
                ) {
                    Text(text = "Clear", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissClearDialog() }) {
                    Text(text = "Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    // AI Role Customization Dialog
    if (uiState.showRoleEditDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissRoleEditDialog() },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = {
                Text(
                    text = "Customize AI Role",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Specify the role/persona for the AI assistant in this chat. This is transmitted to the AI in real-time.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = uiState.roleInputText,
                        onValueChange = { viewModel.onRoleInputChanged(it) },
                        placeholder = { Text("e.g. Sarcastic Robot, Python Tutor, Pirate Captain...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Quick presets:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Nova Assistant", "Python Guru", "Sarcastic AI", "Anime Girl", "Senior Tech Lead").forEach { preset ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                                modifier = Modifier.clickable { viewModel.onRoleInputChanged(preset) }
                            ) {
                                Text(
                                    text = preset,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.saveAiRole() },
                    modifier = Modifier.testTag("save_ai_role_button")
                ) {
                    Text(text = "Save Role", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissRoleEditDialog() }) {
                    Text(text = "Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

@Composable
private fun EmptyChatHero(
    onSelectPrompt: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(horizontal = 24.dp)
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = "AI Assistant",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = stringResource(R.string.user_greeting_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = stringResource(R.string.user_greeting_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        val prompts = listOf(
            Triple(Icons.Default.Lightbulb, "Explain quantum physics in simple terms", "Science"),
            Triple(Icons.Default.Psychology, "Brainstorm 5 innovative startup concepts", "Creativity"),
            Triple(Icons.Default.RocketLaunch, "Help me write a concise professional email", "Productivity"),
            Triple(Icons.Default.ChatBubbleOutline, "What are the best habits for focus?", "Lifestyle")
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            prompts.forEach { (icon, text, category) ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelectPrompt(text) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = category,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}
