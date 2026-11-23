package com.example.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.example.data.model.AiProvider
import com.example.data.model.GeminiModelOption
import com.example.data.model.OpenAiModelOption
import com.example.data.remote.OpenAiClient
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalXproColors
import com.example.ui.theme.XproNeonCyan
import com.example.ui.theme.XproElectricViolet

fun shareTextContent(context: Context, text: String, subject: String = "XPRO AI Response") {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share via"))
}

@Composable
fun XproAmbientBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val xproColors = LocalXproColors.current
    val bgColor = MaterialTheme.colorScheme.background
    val glow1 = xproColors.violetGlow.copy(alpha = if (xproColors.isDark) 0.16f else 0.08f)
    val glow2 = xproColors.neonGlow.copy(alpha = if (xproColors.isDark) 0.12f else 0.07f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glow1, Color.Transparent),
                    center = Offset(size.width * 0.15f, size.height * 0.12f),
                    radius = size.maxDimension * 0.55f
                ),
                center = Offset(size.width * 0.15f, size.height * 0.12f),
                radius = size.maxDimension * 0.55f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glow2, Color.Transparent),
                    center = Offset(size.width * 0.88f, size.height * 0.78f),
                    radius = size.maxDimension * 0.5f
                ),
                center = Offset(size.width * 0.88f, size.height * 0.78f),
                radius = size.maxDimension * 0.5f
            )
        }
        content()
    }
}

@Composable
fun XproLogoMark(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp
) {
    val xproColors = LocalXproColors.current
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF140B2E),
                        Color(0xFF0A0618)
                    )
                )
            )
            .border(
                width = 1.2.dp,
                brush = Brush.linearGradient(
                    listOf(xproColors.neonGlow, xproColors.violetGlow)
                ),
                shape = RoundedCornerShape(size * 0.28f)
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.56f)) {
            val w = this.size.width
            val h = this.size.height
            val stroke = w * 0.18f
            val brush = Brush.linearGradient(listOf(XproNeonCyan, XproElectricViolet))
            drawLine(
                brush = brush,
                start = Offset(w * 0.15f, h * 0.15f),
                end = Offset(w * 0.85f, h * 0.85f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            drawLine(
                brush = Brush.linearGradient(listOf(XproElectricViolet, XproNeonCyan)),
                start = Offset(w * 0.85f, h * 0.15f),
                end = Offset(w * 0.15f, h * 0.85f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun GlowingCard(
    modifier: Modifier = Modifier,
    accentBorder: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val xproColors = LocalXproColors.current
    val shape = MaterialTheme.shapes.medium
    val borderBrush = if (accentBorder) {
        Brush.linearGradient(listOf(xproColors.neonGlow, xproColors.violetGlow))
    } else {
        Brush.linearGradient(
            listOf(
                xproColors.cardBorder,
                xproColors.cardBorder.copy(alpha = 0.5f)
            )
        )
    }

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (xproColors.isDark) 0.88f else 0.95f),
            tonalElevation = 2.dp,
            border = BorderStroke(1.dp, borderBrush)
        ) {
            Column(content = content)
        }
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (xproColors.isDark) 0.88f else 0.95f),
            tonalElevation = 2.dp,
            border = BorderStroke(1.dp, borderBrush)
        ) {
            Column(content = content)
        }
    }
}

@Composable
fun PulseLoadingOrb(
    label: String,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "xpro_orb")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_rotation"
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_pulse"
    )
    val xproColors = LocalXproColors.current

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(72.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = (size.minDimension / 2f) * 0.82f * pulse
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(
                            xproColors.neonGlow.copy(alpha = 0.35f),
                            xproColors.violetGlow.copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    ),
                    radius = radius
                )
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(
                            xproColors.neonGlow,
                            xproColors.violetGlow,
                            Color.Transparent,
                            xproColors.neonGlow
                        )
                    ),
                    startAngle = rotation,
                    sweepAngle = 240f,
                    useCenter = false,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = xproColors.neonGlow,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private sealed interface MessageSegment {
    data class PlainText(val text: String) : MessageSegment
    data class CodeBlock(val language: String, val code: String) : MessageSegment
}

private fun parseMessageSegments(raw: String, isStreaming: Boolean = false): List<MessageSegment> {
    val segments = mutableListOf<MessageSegment>()
    val regex = Regex("```([a-zA-Z0-9_+#.-]*)\\n([\\s\\S]*?)```")
    var lastIndex = 0
    for (match in regex.findAll(raw)) {
        if (match.range.first > lastIndex) {
            val plain = raw.substring(lastIndex, match.range.first).trim()
            if (plain.isNotEmpty()) segments.add(MessageSegment.PlainText(plain))
        }
        val lang = match.groupValues[1].ifBlank { "code" }
        val code = match.groupValues[2].trimEnd()
        segments.add(MessageSegment.CodeBlock(lang, code))
        lastIndex = match.range.last + 1
    }
    if (lastIndex < raw.length) {
        val tailRaw = raw.substring(lastIndex)
        val openFenceMatch = if (isStreaming) {
            Regex("```([a-zA-Z0-9_+#.-]*)\\n([\\s\\S]*)$").find(tailRaw)
        } else null
        if (openFenceMatch != null) {
            val plainBefore = tailRaw.substring(0, openFenceMatch.range.first).trim()
            if (plainBefore.isNotEmpty()) segments.add(MessageSegment.PlainText(plainBefore))
            val lang = openFenceMatch.groupValues[1].ifBlank { "code" }
            val partialCode = openFenceMatch.groupValues[2]
            segments.add(MessageSegment.CodeBlock(lang, partialCode))
        } else {
            val tail = tailRaw.trim()
            if (tail.isNotEmpty()) segments.add(MessageSegment.PlainText(tail))
        }
    }
    if (segments.isEmpty()) {
        segments.add(MessageSegment.PlainText(raw))
    }
    return segments
}

@Composable
fun TypingIndicatorBubble(
    providerLabel: String,
    modifier: Modifier = Modifier
) {
    val xproColors = LocalXproColors.current
    val transition = rememberInfiniteTransition(label = "typing_indicator")

    val dot1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, delayMillis = 0, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "typing_dot_1"
    )
    val dot2 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, delayMillis = 160, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "typing_dot_2"
    )
    val dot3 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, delayMillis = 320, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "typing_dot_3"
    )
    val borderPulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "typing_border_pulse"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("typing_indicator_bubble"),
        contentAlignment = Alignment.CenterStart
    ) {
        Surface(
            modifier = Modifier.widthIn(min = 190.dp, max = 320.dp),
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = 4.dp,
                bottomEnd = 18.dp
            ),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        xproColors.neonGlow.copy(alpha = borderPulse),
                        xproColors.violetGlow.copy(alpha = borderPulse * 0.85f)
                    )
                )
            )
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .alpha(borderPulse)
                            .clip(CircleShape)
                            .background(xproColors.neonGlow)
                    )
                    Text(
                        text = "XPRO AI • $providerLabel",
                        style = MaterialTheme.typography.labelSmall,
                        color = xproColors.neonGlow
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val dots = listOf(dot1, dot2, dot3)
                        dots.forEachIndexed { index, progress ->
                            val dotColor = if (index == 1) xproColors.violetGlow else xproColors.neonGlow
                            Box(
                                modifier = Modifier
                                    .offset(y = (-5f * progress).dp)
                                    .size((7f + 2f * progress).dp)
                                    .alpha(0.35f + 0.65f * progress)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                        }
                    }
                    Text(
                        text = "typing...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun FormattedMessageBubble(
    content: String,
    isFromUser: Boolean,
    providerLabel: String,
    isStreaming: Boolean = false,
    onSpeakClick: (() -> Unit)? = null,
    onCopied: () -> Unit = {}
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val xproColors = LocalXproColors.current
    var justCopied by remember(content) { mutableStateOf(false) }

    LaunchedEffect(justCopied) {
        if (justCopied) {
            delay(2000L)
            justCopied = false
        }
    }

    // Smooth character-by-character streaming interpolation
    var revealedLength by remember {
        mutableIntStateOf(if (isStreaming) 0 else content.length)
    }

    LaunchedEffect(content, isStreaming) {
        if (!isStreaming) {
            revealedLength = content.length
            return@LaunchedEffect
        }
        if (revealedLength > content.length) {
            revealedLength = content.length
        }
        while (revealedLength < content.length) {
            val remaining = content.length - revealedLength
            val step = when {
                remaining > 160 -> (remaining / 8).coerceAtLeast(10)
                remaining > 60 -> 5
                remaining > 20 -> 2
                else -> 1
            }
            revealedLength = (revealedLength + step).coerceAtMost(content.length)
            delay(16L)
        }
    }

    val displayedContent = if (isStreaming) content.take(revealedLength) else content

    val cursorAlpha = if (isStreaming) {
        val streamTransition = rememberInfiniteTransition(label = "stream_cursor")
        val alpha by streamTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 450, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "cursor_blink"
        )
        alpha
    } else 0f

    val alignment = if (isFromUser) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleBrush = if (isFromUser) {
        Brush.linearGradient(
            listOf(
                xproColors.violetGlow.copy(alpha = if (xproColors.isDark) 0.92f else 0.96f),
                xproColors.neonGlow.copy(alpha = if (xproColors.isDark) 0.72f else 0.85f)
            )
        )
    } else null

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = alignment
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth(if (isFromUser) 0.84f else 0.94f),
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isFromUser) 18.dp else 4.dp,
                bottomEnd = if (isFromUser) 4.dp else 18.dp
            ),
            color = if (isFromUser) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant,
            border = if (isFromUser) null else BorderStroke(
                1.dp,
                if (isStreaming) xproColors.neonGlow.copy(alpha = 0.5f) else xproColors.cardBorder
            )
        ) {
            Box(
                modifier = if (bubbleBrush != null) {
                    Modifier.background(bubbleBrush)
                } else Modifier
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    if (!isFromUser) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .alpha(if (isStreaming) cursorAlpha else 1f)
                                        .clip(CircleShape)
                                        .background(xproColors.neonGlow)
                                )
                                Text(
                                    text = "XPRO AI • $providerLabel",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = xproColors.neonGlow
                                )
                            }
                            if (isStreaming) {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = xproColors.violetGlow.copy(alpha = 0.16f),
                                    modifier = Modifier
                                        .alpha(0.55f + 0.45f * cursorAlpha)
                                        .testTag("streaming_indicator_badge")
                                ) {
                                    Text(
                                        text = "STREAMING",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = xproColors.violetGlow,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    val segments = remember(displayedContent, isStreaming) {
                        parseMessageSegments(displayedContent, isStreaming = isStreaming)
                    }
                    SelectionContainer {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            segments.forEachIndexed { index, segment ->
                                val isLastSegment = index == segments.lastIndex
                                when (segment) {
                                    is MessageSegment.PlainText -> {
                                        val styledText = if (isStreaming && isLastSegment) {
                                            buildAnnotatedString {
                                                append(segment.text)
                                                withStyle(
                                                    SpanStyle(
                                                        color = xproColors.neonGlow.copy(alpha = cursorAlpha),
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                ) {
                                                    append(" ▍")
                                                }
                                            }
                                        } else {
                                            AnnotatedString(segment.text)
                                        }
                                        Text(
                                            text = styledText,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = if (isFromUser) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    is MessageSegment.CodeBlock -> {
                                        val styledCode = if (isStreaming && isLastSegment) {
                                            buildAnnotatedString {
                                                append(segment.code)
                                                withStyle(
                                                    SpanStyle(
                                                        color = XproNeonCyan.copy(alpha = cursorAlpha),
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                ) {
                                                    append(" ▍")
                                                }
                                            }
                                        } else {
                                            AnnotatedString(segment.code)
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = xproColors.codeBackground,
                                            border = BorderStroke(
                                                1.dp,
                                                xproColors.neonGlow.copy(alpha = 0.35f)
                                            )
                                        ) {
                                            Column {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(Color(0xFF17102E))
                                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = segment.language.uppercase(),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = XproNeonCyan
                                                    )
                                                    Surface(
                                                        onClick = {
                                                            clipboard.setText(AnnotatedString(segment.code))
                                                            onCopied()
                                                        },
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = Color(0xFF261C47),
                                                        modifier = Modifier.testTag("copy_code_button")
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(
                                                                horizontal = 8.dp,
                                                                vertical = 4.dp
                                                            ),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Filled.ContentCopy,
                                                                contentDescription = "Copy code",
                                                                tint = XproNeonCyan,
                                                                modifier = Modifier.size(13.dp)
                                                            )
                                                            Text(
                                                                text = "Copy Code",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = Color.White
                                                            )
                                                        }
                                                    }
                                                }
                                                Text(
                                                    text = styledCode,
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = Color(0xFFE9E4FF),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .horizontalScroll(rememberScrollState())
                                                        .padding(12.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (!isFromUser && !isStreaming) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (onSpeakClick != null) {
                                IconButton(
                                    onClick = onSpeakClick,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("speak_response_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = "Read aloud",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(content))
                                    justCopied = true
                                    onCopied()
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("copy_response_button")
                            ) {
                                Icon(
                                    imageVector = if (justCopied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                                    contentDescription = "Copy response",
                                    tint = if (justCopied) xproColors.neonGlow else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { shareTextContent(context, content) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("share_response_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Share,
                                    contentDescription = "Share response",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProviderModelSelector(
    provider: AiProvider,
    geminiModel: GeminiModelOption,
    openAiModel: OpenAiModelOption,
    onProviderChange: (AiProvider) -> Unit,
    onGeminiModelChange: (GeminiModelOption) -> Unit,
    onOpenAiModelChange: (OpenAiModelOption) -> Unit,
    modifier: Modifier = Modifier
) {
    var modelMenuExpanded by remember { mutableStateOf(false) }
    val xproColors = LocalXproColors.current
    val openAiReady = remember { OpenAiClient.isConfigured() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AiProvider.entries.forEach { item ->
            val selected = item == provider
            FilterChip(
                selected = selected,
                onClick = { onProviderChange(item) },
                label = {
                    val suffix = if (item == AiProvider.OPENAI && !openAiReady) " (Key needed)" else ""
                    Text("${item.displayName}$suffix")
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (item == AiProvider.GEMINI) Icons.Filled.AutoAwesome else Icons.Filled.Bolt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = xproColors.neonGlow.copy(alpha = 0.18f),
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                    selectedLeadingIconColor = xproColors.neonGlow
                ),
                modifier = Modifier.testTag("provider_chip_${item.name.lowercase()}")
            )
        }

        Box {
            val activeModelLabel = if (provider == AiProvider.GEMINI) {
                geminiModel.displayName
            } else {
                openAiModel.displayName
            }
            FilterChip(
                selected = true,
                onClick = { modelMenuExpanded = true },
                label = { Text(activeModelLabel, fontWeight = FontWeight.SemiBold) },
                modifier = Modifier.testTag("model_selector_chip")
            )
            DropdownMenu(
                expanded = modelMenuExpanded,
                onDismissRequest = { modelMenuExpanded = false }
            ) {
                if (provider == AiProvider.GEMINI) {
                    GeminiModelOption.entries.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(option.displayName, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        option.description,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                onGeminiModelChange(option)
                                modelMenuExpanded = false
                            }
                        )
                    }
                } else {
                    OpenAiModelOption.entries.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(option.displayName, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        option.description,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                onOpenAiModelChange(option)
                                modelMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VoiceDictationBanner(
    rmsLevel: Float,
    languageLabel: String,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val xproColors = LocalXproColors.current
    val transition = rememberInfiniteTransition(label = "voice_wave")
    val wavePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "voice_wave_phase"
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, xproColors.neonGlow.copy(alpha = 0.65f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .testTag("voice_dictation_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(xproColors.neonGlow.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = null,
                        tint = xproColors.neonGlow,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Live 5-bar RMS audio waveform
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    val factors = listOf(0.55f, 0.85f, 1.0f, 0.8f, 0.5f)
                    factors.forEachIndexed { idx, factor ->
                        val wave = 0.5f + 0.5f * kotlin.math.sin(wavePhase + idx * 0.9f)
                        val barHeight = (6f + 16f * rmsLevel.coerceIn(0.15f, 1f) * factor * (0.55f + 0.45f * wave)).dp
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(barHeight)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (idx % 2 == 0) xproColors.neonGlow else xproColors.violetGlow
                                )
                        )
                    }
                }

                Column {
                    Text(
                        text = "Listening... Speak your query",
                        style = MaterialTheme.typography.labelLarge,
                        color = xproColors.neonGlow
                    )
                    Text(
                        text = "Voice-to-text ($languageLabel)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("cancel_voice_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Cancel voice input",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onStop,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("stop_voice_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Stop,
                        contentDescription = "Finish speaking",
                        tint = xproColors.neonGlow
                    )
                }
            }
        }
    }
}

