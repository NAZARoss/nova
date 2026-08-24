package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.network.colab.ColabConnectionStatus
import com.example.ui.theme.StatusActiveGreen
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusWaiting
import com.example.viewmodel.AdminViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminSettingsScreen(
    adminViewModel: AdminViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val isServerRunning by adminViewModel.isServerRunning.collectAsStateWithLifecycle()
    val connectedClients by adminViewModel.connectedClients.collectAsStateWithLifecycle()
    val colabUrl by adminViewModel.colabServerUrl.collectAsStateWithLifecycle()
    val colabStatus by adminViewModel.colabConnectionStatus.collectAsStateWithLifecycle()

    var colabUrlInput by remember(colabUrl) { mutableStateOf(colabUrl) }
    var isTestingColab by remember { mutableStateOf(false) }
    var colabTestMessage by remember { mutableStateOf<String?>(null) }

    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin Node Configuration", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("admin_settings_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Cloud Colab Server Section
            Text(
                text = "Cloud Server (Colab / Cloudflare Tunnel)",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Cloud,
                                contentDescription = "Colab Server",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Colab REST Backend",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (colabUrl.isNotBlank()) "Internet Tunnel" else "Disabled (Local only)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Status Badge
                        Surface(
                            shape = CircleShape,
                            color = when (colabStatus) {
                                ColabConnectionStatus.CONNECTED -> StatusActiveGreen.copy(alpha = 0.15f)
                                ColabConnectionStatus.CONNECTING -> StatusWaiting.copy(alpha = 0.15f)
                                ColabConnectionStatus.ERROR -> StatusError.copy(alpha = 0.15f)
                                ColabConnectionStatus.NOT_CONFIGURED -> MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when (colabStatus) {
                                                ColabConnectionStatus.CONNECTED -> StatusActiveGreen
                                                ColabConnectionStatus.CONNECTING -> StatusWaiting
                                                ColabConnectionStatus.ERROR -> StatusError
                                                ColabConnectionStatus.NOT_CONFIGURED -> MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = when (colabStatus) {
                                        ColabConnectionStatus.CONNECTED -> "Online"
                                        ColabConnectionStatus.CONNECTING -> "Connecting"
                                        ColabConnectionStatus.ERROR -> "Error"
                                        ColabConnectionStatus.NOT_CONFIGURED -> "Disabled"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = when (colabStatus) {
                                        ColabConnectionStatus.CONNECTED -> StatusActiveGreen
                                        ColabConnectionStatus.CONNECTING -> StatusWaiting
                                        ColabConnectionStatus.ERROR -> StatusError
                                        ColabConnectionStatus.NOT_CONFIGURED -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Connects the admin dashboard to the Flask backend running in Colab so you can receive and respond to user messages from anywhere.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = colabUrlInput,
                        onValueChange = { colabUrlInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Cloudflare Tunnel URL") },
                        placeholder = { Text("https://xxx.trycloudflare.com") },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    val clipText = clipboardManager.getText()?.text
                                    if (!clipText.isNullOrBlank()) {
                                        colabUrlInput = clipText.trim()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentPaste,
                                    contentDescription = "Paste",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isTestingColab = true
                                    colabTestMessage = null
                                    val (ok, err) = adminViewModel.testColabConnection(colabUrlInput)
                                    isTestingColab = false
                                    colabTestMessage = if (ok) "Server reachable!" else "Error: $err"
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isTestingColab && colabUrlInput.isNotBlank()
                        ) {
                            if (isTestingColab) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Testing...")
                            } else {
                                Icon(imageVector = Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Test")
                            }
                        }

                        Button(
                            onClick = {
                                adminViewModel.setColabServerUrl(colabUrlInput)
                                Toast.makeText(context, "Colab URL saved", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isTestingColab
                        ) {
                            Icon(imageVector = Icons.Outlined.CloudDone, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save")
                        }
                    }

                    if (colabUrl.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = {
                                colabUrlInput = ""
                                adminViewModel.clearColabServerUrl()
                                Toast.makeText(context, "Colab server cleared", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(imageVector = Icons.Outlined.CloudOff, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Disconnect Colab Server")
                        }
                    }

                    if (colabTestMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = colabTestMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (colabTestMessage?.contains("reachable") == true) StatusActiveGreen else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Security PIN Settings
            Text(
                text = "Security & PIN",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Key,
                            contentDescription = "PIN",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Change Master PIN",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = oldPin,
                        onValueChange = { if (it.length <= 4) oldPin = it },
                        label = { Text("Current PIN") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { if (it.length <= 4) newPin = it },
                        label = { Text("New 4-digit PIN") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = { if (it.length <= 4) confirmPin = it },
                        label = { Text("Confirm New PIN") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            if (newPin.length != 4) {
                                Toast.makeText(context, "PIN must be 4 digits", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (newPin != confirmPin) {
                                Toast.makeText(context, "New PINs do not match", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val success = adminViewModel.changePin(oldPin, newPin)
                            if (success) {
                                Toast.makeText(context, "PIN changed successfully!", Toast.LENGTH_SHORT).show()
                                oldPin = ""
                                newPin = ""
                                confirmPin = ""
                            } else {
                                Toast.makeText(context, "Current PIN is incorrect", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Update PIN")
                    }
                }
            }
        }
    }
}
