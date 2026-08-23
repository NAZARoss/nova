package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.OnboardingStep

/**
 * 1. Warning Dialog with Red text: "ВНИМАНИЕ ЭТО ВАЖНО ПРОЧИТАТЬ" and "Ок" button.
 */
@Composable
fun OnboardingWarningDialog(
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* Force acknowledge */ },
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Warning",
                    tint = Color(0xFFE53935),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "ВНИМАНИЕ ЭТО ВАЖНО ПРОЧИТАТЬ",
                    color = Color(0xFFE53935),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
            }
        },
        text = {
            Column {
                Text(
                    text = "Пожалуйста, ознакомьтесь с условиями и возможностями искусственного интеллекта перед началом работы.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = Color(0xFFE53935).copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Данное приложение разработано для демонстрации и интерактивного взаимодействия с интеллектуальным помощником.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE53935),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("onboarding_warning_ok_button")
            ) {
                Text(text = "Ок", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    )
}

/**
 * 2. AI Capabilities modal showing the 4 listed points:
 * 1. Шуточные уведомления
 * 2. ИИ не может взаимодействовать с вашим телефоном
 * 3. Все данные никто не может просматривать (конфиденциальные)
 * 4. Ты покакал?
 */
@Composable
fun OnboardingAiCapabilitiesDialog(
    onContinueToTutorial: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* Force acknowledge */ },
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "AI",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Возможности и правила ИИ",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Ключевые особенности и принципы работы системы:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                CapabilityItem(
                    number = "1",
                    title = "Шуточные уведомления",
                    subtitle = "Интерактивные оповещения и забавные системные реакции.",
                    icon = Icons.Outlined.Celebration,
                    color = MaterialTheme.colorScheme.primary
                )

                CapabilityItem(
                    number = "2",
                    title = "ИИ не может взаимодействовать с вашим телефоном",
                    subtitle = "Система полностью изолирована и не имеет доступа к системным файлам.",
                    icon = Icons.Default.Smartphone,
                    color = MaterialTheme.colorScheme.secondary
                )

                CapabilityItem(
                    number = "3",
                    title = "Все данные никто не может просматривать",
                    subtitle = "Конфиденциальность: ваши диалоги хранятся локально.",
                    icon = Icons.Default.Security,
                    color = Color(0xFF2E7D32)
                )

                CapabilityItem(
                    number = "4",
                    title = "Ты покакал?",
                    subtitle = "Вопрос повышенной степени заботы и искусственного интеллекта.",
                    icon = Icons.Default.QuestionAnswer,
                    color = Color(0xFFD84315)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onContinueToTutorial,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("start_tutorial_button")
            ) {
                Icon(imageVector = Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Начать обучение", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun CapabilityItem(
    number: String,
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = number,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 3. Interactive Spotlight Overlay:
 * Dims the screen with a dark backdrop everywhere except the target element.
 * Points an animated arrow towards the highlighted element with explanation text below.
 * Tapping anywhere advances to the next step.
 */
@Composable
fun InteractiveSpotlightTutorialOverlay(
    step: OnboardingStep,
    onNextStep: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }

    // Floating bounce animation for arrow
    val infiniteTransition = rememberInfiniteTransition(label = "arrow_bounce")
    val arrowOffset by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offset"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onNextStep
            )
            .testTag("spotlight_tutorial_overlay")
    ) {
        when (step) {
            OnboardingStep.TUTORIAL_ROLE -> {
                // Step 1: Point to AI Role in top bar (Top-Center)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 70.dp, start = 20.dp, end = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Arrow pointing UP to AI Role
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = "Arrow Up",
                        tint = Color.White,
                        modifier = Modifier
                            .offset { IntOffset(0, arrowOffset.toInt()) }
                            .size(36.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    TutorialCard(
                        stepNumber = "Шаг 1 из 4",
                        title = "Кастомизация роли ИИ",
                        description = "Нажмите на бейдж роли в верхней панели, чтобы изменить характер или специализацию ИИ (например: Python разработчик, Саркастичный бот или Аниме-тян).",
                        icon = Icons.Default.Edit,
                        onNextClick = onNextStep,
                        onSkipClick = onSkip
                    )
                }
            }

            OnboardingStep.TUTORIAL_INPUT -> {
                // Step 2: Point to Chat Input Field at bottom
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 90.dp, start = 20.dp, end = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TutorialCard(
                        stepNumber = "Шаг 2 из 4",
                        title = "Поле ввода сообщений",
                        description = "Вводите ваши вопросы и общайтесь с помощником. Поддерживаются быстрые подсказки и отправка нажатием кнопки или клавиши Enter.",
                        icon = Icons.Default.QuestionAnswer,
                        onNextClick = onNextStep,
                        onSkipClick = onSkip
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Arrow pointing DOWN to Input Field
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = "Arrow Down",
                        tint = Color.White,
                        modifier = Modifier
                            .offset { IntOffset(0, arrowOffset.toInt()) }
                            .size(36.dp)
                    )
                }
            }

            OnboardingStep.TUTORIAL_SETTINGS -> {
                // Step 3: Point to Settings button (Top-Right)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 70.dp, start = 20.dp, end = 20.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Row(
                        modifier = Modifier.padding(end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Arrow Up",
                            tint = Color.White,
                            modifier = Modifier
                                .offset { IntOffset(0, arrowOffset.toInt()) }
                                .size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TutorialCard(
                        stepNumber = "Шаг 3 из 4",
                        title = "Настройки и удаленный сервер",
                        description = "В настройках можно указать URL-адрес сервера Google Colab / Cloudflare для связи через интернет, сменить тему или войти в режим администратора.",
                        icon = Icons.Default.Settings,
                        onNextClick = onNextStep,
                        onSkipClick = onSkip
                    )
                }
            }

            OnboardingStep.TUTORIAL_CLEAR -> {
                // Step 4: Point to Clear Chat button (Top-Right near settings)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 70.dp, start = 20.dp, end = 20.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Row(
                        modifier = Modifier.padding(end = 56.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Arrow Up",
                            tint = Color.White,
                            modifier = Modifier
                                .offset { IntOffset(0, arrowOffset.toInt()) }
                                .size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TutorialCard(
                        stepNumber = "Шаг 4 из 4",
                        title = "Очистка истории сообщений",
                        description = "Нажмите на значок корзины для быстрой и полной очистки всей локальной переписки на этом устройстве.",
                        icon = Icons.Default.CleaningServices,
                        isLastStep = true,
                        onNextClick = onNextStep,
                        onSkipClick = onSkip
                    )
                }
            }

            else -> { /* No spotlight */ }
        }

        // Tap anywhere banner at the very bottom
        Surface(
            color = Color.White.copy(alpha = 0.15f),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.TouchApp,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Кликните в любом месте экрана для перехода дальше",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun TutorialCard(
    stepNumber: String,
    title: String,
    description: String,
    icon: ImageVector,
    isLastStep: Boolean = false,
    onNextClick: () -> Unit,
    onSkipClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp,
        border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = stepNumber,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                TextButton(onClick = onSkipClick) {
                    Text(text = "Пропустить", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onNextClick,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (isLastStep) "Готово ✓" else "Далее →",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
