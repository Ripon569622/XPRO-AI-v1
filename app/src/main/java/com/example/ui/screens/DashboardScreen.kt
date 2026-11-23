package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AiMode
import com.example.data.model.AiProvider
import com.example.data.model.Conversation
import com.example.data.model.UiState
import com.example.ui.components.GlowingCard
import com.example.ui.components.XproLogoMark
import com.example.ui.theme.LocalXproColors
import com.example.ui.theme.XproElectricViolet
import com.example.ui.theme.XproNeonCyan

private data class ToolTile(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color,
    val tag: String,
    val onClick: () -> Unit
)

@Composable
fun DashboardScreen(
    userName: String,
    conversationsState: UiState<List<Conversation>>,
    activeProvider: AiProvider,
    onLaunchMode: (AiMode) -> Unit,
    onOpenImageStudio: () -> Unit,
    onOpenConversation: (Conversation) -> Unit,
    onViewAllHistory: () -> Unit
) {
    val xproColors = LocalXproColors.current

    val tools = listOf(
        ToolTile("AI Chat", "Multi-turn neural chat", Icons.AutoMirrored.Filled.Chat, XproNeonCyan, "tile_chat") {
            onLaunchMode(AiMode.CHAT)
        },
        ToolTile("Voice AI", "Speak & listen live", Icons.Filled.GraphicEq, XproElectricViolet, "tile_voice") {
            onLaunchMode(AiMode.VOICE)
        },
        ToolTile("Image AI", "Generate & edit art", Icons.Filled.Brush, Color(0xFFD946EF), "tile_image") {
            onOpenImageStudio()
        },
        ToolTile("Code Copilot", "Write & debug code", Icons.Filled.Code, Color(0xFF38BDF8), "tile_coding") {
            onLaunchMode(AiMode.CODING)
        },
        ToolTile("Translator", "Multi-language neural", Icons.Filled.Translate, Color(0xFF10B981), "tile_translator") {
            onLaunchMode(AiMode.TRANSLATOR)
        },
        ToolTile("Writing Pro", "Essays, copy & polish", Icons.Filled.Create, Color(0xFFF59E0B), "tile_writing") {
            onLaunchMode(AiMode.WRITING)
        },
        ToolTile("Math Solver", "Step-by-step STEM", Icons.Filled.Calculate, Color(0xFF818CF8), "tile_math") {
            onLaunchMode(AiMode.MATH)
        },
        ToolTile("Study Lab", "Quizzes & flashcards", Icons.Filled.School, Color(0xFF2DD4BF), "tile_study") {
            onLaunchMode(AiMode.STUDY)
        }
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                // Hero Command Card
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(
                        1.dp,
                        Brush.linearGradient(listOf(xproColors.neonGlow, xproColors.violetGlow))
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(196.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_hero_banner),
                            contentDescription = "XPRO AI Command Center",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color(0xE606040C),
                                            Color(0x990B0718),
                                            Color(0x5506040C)
                                        )
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    XproLogoMark(size = 38.dp)
                                    Column {
                                        Text(
                                            text = "Welcome back, ${userName.substringBefore(" ")}",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Engine: ${activeProvider.displayName}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = XproNeonCyan
                                        )
                                    }
                                }
                            }

                            Column {
                                Text(
                                    text = "Your AI. Your Power. Your Future.",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = { onLaunchMode(AiMode.CHAT) },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = XproNeonCyan,
                                        contentColor = Color(0xFF06040C)
                                    ),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("hero_new_chat_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Start New AI Session", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "AI Modules",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // 2-column responsive tool grid
            val rows = tools.chunked(2)
            items(rows) { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    pair.forEach { tile ->
                        GlowingCard(
                            onClick = tile.onClick,
                            modifier = Modifier
                                .weight(1f)
                                .testTag(tile.tag)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(tile.accent.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = tile.icon,
                                        contentDescription = tile.title,
                                        tint = tile.accent,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = tile.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = tile.subtitle,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Cloud Threads",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    TextButton(
                        onClick = onViewAllHistory,
                        modifier = Modifier.testTag("dashboard_view_all_history")
                    ) {
                        Text("View All")
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            when (conversationsState) {
                is UiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = xproColors.neonGlow)
                        }
                    }
                }

                is UiState.Error -> {
                    item {
                        GlowingCard {
                            Text(
                                text = conversationsState.message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }

                is UiState.Success -> {
                    val recent = conversationsState.data.take(4)
                    if (recent.isEmpty()) {
                        item {
                            GlowingCard {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "No saved conversations yet",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Pick any AI module above to start a cloud-synced session.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(recent, key = { it.id }) { conv ->
                            AnimatedVisibility(visible = true, enter = fadeIn()) {
                                GlowingCard(
                                    onClick = { onOpenConversation(conv) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("recent_conv_${conv.id}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(xproColors.violetGlow.copy(alpha = 0.18f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Chat,
                                                contentDescription = null,
                                                tint = xproColors.neonGlow,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = conv.title,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                if (conv.pinned) {
                                                    Icon(
                                                        imageVector = Icons.Filled.PushPin,
                                                        contentDescription = "Pinned",
                                                        tint = xproColors.neonGlow,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = conv.lastMessagePreview.ifBlank { conv.aiMode.displayName },
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Text(
                                                text = conv.aiMode.displayName,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}
