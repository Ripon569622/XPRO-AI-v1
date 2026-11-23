package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import coil.compose.AsyncImage
import com.example.data.auth.signOut
import com.example.data.model.AiProvider
import com.example.data.model.GeminiModelOption
import com.example.data.model.OpenAiModelOption
import com.example.data.model.ThemePreference
import com.example.data.preferences.UserSettings
import com.example.data.remote.OpenAiClient
import com.example.ui.components.GlowingCard
import com.example.ui.theme.LocalXproColors
import com.example.ui.theme.XproSuccess
import com.example.ui.viewmodel.SettingsViewModel
import com.google.firebase.auth.FirebaseUser

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    user: FirebaseUser,
    settings: UserSettings,
    settingsViewModel: SettingsViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val xproColors = LocalXproColors.current
    val openAiConfigured = remember { OpenAiClient.isConfigured() }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Settings & Setup",
                style = MaterialTheme.typography.headlineMedium
            )

            // Account Card
            GlowingCard(accentBorder = true) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (user.photoUrl != null) {
                        AsyncImage(
                            model = user.photoUrl,
                            contentDescription = "User profile avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Person, contentDescription = null)
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = user.displayName ?: "XPRO AI User",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = user.email ?: "Signed in with Google",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Appearance (Dark / Light Mode)
            GlowingCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (xproColors.isDark) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                            contentDescription = null,
                            tint = xproColors.neonGlow
                        )
                        Text("Appearance Mode", style = MaterialTheme.typography.titleMedium)
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ThemePreference.entries.forEach { pref ->
                            FilterChip(
                                selected = settings.themePreference == pref,
                                onClick = { settingsViewModel.setTheme(pref) },
                                label = { Text(pref.label, maxLines = 1) },
                                modifier = Modifier.testTag("theme_option_${pref.name.lowercase()}")
                            )
                        }
                    }
                }
            }

            // Default AI Engine
            GlowingCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Default AI Provider", style = MaterialTheme.typography.titleMedium)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AiProvider.entries.forEach { provider ->
                            FilterChip(
                                selected = settings.preferredProvider == provider,
                                onClick = { settingsViewModel.setProvider(provider) },
                                label = { Text(provider.displayName, maxLines = 1) },
                                modifier = Modifier.testTag("settings_provider_${provider.name.lowercase()}")
                            )
                        }
                    }

                    Text("Default Gemini Model", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        GeminiModelOption.entries.forEach { model ->
                            FilterChip(
                                selected = settings.geminiModel == model,
                                onClick = { settingsViewModel.setGeminiModel(model) },
                                label = { Text(model.displayName.removePrefix("Gemini "), maxLines = 1) },
                                modifier = Modifier.testTag("settings_gemini_${model.name.lowercase()}")
                            )
                        }
                    }

                    Text("Default OpenAI Model", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OpenAiModelOption.entries.forEach { model ->
                            FilterChip(
                                selected = settings.openAiModel == model,
                                onClick = { settingsViewModel.setOpenAiModel(model) },
                                label = { Text(model.displayName, maxLines = 1) },
                                modifier = Modifier.testTag("settings_openai_${model.name.lowercase()}")
                            )
                        }
                    }
                }
            }

            // Voice Preferences
            GlowingCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Voice Auto-Speak", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Speak AI replies aloud automatically in Voice AI mode",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.autoSpeakVoice,
                        onCheckedChange = settingsViewModel::setAutoSpeakVoice,
                        modifier = Modifier.testTag("auto_speak_switch")
                    )
                }
            }

            // API Integration Status & Setup Guide
            GlowingCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Info, contentDescription = null, tint = xproColors.neonGlow)
                        Text("API & Setup Status", style = MaterialTheme.typography.titleMedium)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = XproSuccess, modifier = Modifier.size(18.dp))
                        Text(
                            text = "Google Gemini (Firebase AI Logic + App Check): Ready",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (openAiConfigured) Icons.Filled.CheckCircle else Icons.Filled.Key,
                            contentDescription = null,
                            tint = if (openAiConfigured) XproSuccess else MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (openAiConfigured) {
                                "OpenAI API (GPT-4o & DALL-E 3): Configured via Secrets"
                            } else {
                                "OpenAI API: Add OPENAI_API_KEY in AI Studio Secrets Panel to enable GPT-4o & DALL-E 3."
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Sign Out
            OutlinedButton(
                onClick = {
                    signOut(credentialManager = credentialManager, scope = scope)
                },
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("sign_out_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign Out")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
