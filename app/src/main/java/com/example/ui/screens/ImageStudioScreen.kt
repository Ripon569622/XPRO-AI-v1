package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AiProvider
import com.example.data.model.ImageAspectOption
import com.example.data.remote.OpenAiClient
import com.example.ui.components.GlowingCard
import com.example.ui.components.PulseLoadingOrb
import com.example.ui.theme.LocalXproColors
import com.example.ui.viewmodel.ImageGenUiState
import com.example.ui.viewmodel.ImageStudioViewModel
import com.example.util.BitmapUtils
import kotlinx.coroutines.launch

@Composable
fun ImageStudioScreen(
    viewModel: ImageStudioViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedAspect by viewModel.selectedAspect.collectAsStateWithLifecycle()
    val selectedProvider by viewModel.selectedProvider.collectAsStateWithLifecycle()
    val referenceBitmap by viewModel.referenceBitmap.collectAsStateWithLifecycle()

    var prompt by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val xproColors = LocalXproColors.current

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            runCatching {
                BitmapUtils.decodeSoftwareBitmap(context, uri)
            }.onSuccess { bmp ->
                viewModel.setReferenceBitmap(bmp)
            }.onFailure { err ->
                scope.launch {
                    snackbarHostState.showSnackbar(
                        err.localizedMessage ?: "Could not load selected photo."
                    )
                }
            }
        }
    }

    val quickPrompts = listOf(
        "Neon cybernetic owl perched over a rain-slicked futuristic skyline, ultra detailed",
        "Minimalist crystalline orbital station floating above a deep violet gas giant",
        "Isometric 3D holographic microchip city glowing in electric cyan and violet",
        "Abstract liquid chrome wave sculpture in dark studio lighting"
    )

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "AI Image Studio",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "Generate or remix high-resolution visuals with Gemini Nano Banana 2.1 or OpenAI DALL-E 3.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Provider Chips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    val openAiReady = remember { OpenAiClient.isConfigured() }
                    FilterChip(
                        selected = selectedProvider == AiProvider.GEMINI,
                        onClick = { viewModel.setProvider(AiProvider.GEMINI) },
                        label = { Text("Gemini Nano Banana 2.1") },
                        leadingIcon = {
                            Icon(Icons.Filled.AutoAwesome, null, modifier = Modifier.size(16.dp))
                        },
                        modifier = Modifier.testTag("studio_provider_gemini")
                    )
                    FilterChip(
                        selected = selectedProvider == AiProvider.OPENAI,
                        onClick = { viewModel.setProvider(AiProvider.OPENAI) },
                        label = {
                            Text(if (openAiReady) "OpenAI DALL-E 3" else "DALL-E 3 (Key needed)")
                        },
                        leadingIcon = {
                            Icon(Icons.Filled.Bolt, null, modifier = Modifier.size(16.dp))
                        },
                        modifier = Modifier.testTag("studio_provider_openai")
                    )
                }

                // Aspect Ratio Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Aspect:",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ImageAspectOption.entries.forEach { option ->
                        FilterChip(
                            selected = selectedAspect == option,
                            onClick = { viewModel.setAspect(option) },
                            label = { Text(option.label) },
                            modifier = Modifier.testTag("aspect_chip_${option.label}")
                        )
                    }
                }

                // Prompt Input
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Describe the image you want to create or edit") },
                    placeholder = { Text("e.g., Futuristic neon obsidian sports car on wet asphalt") },
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("image_prompt_input")
                )

                // Reference photo preview (for Gemini image-to-image editing)
                if (referenceBitmap != null) {
                    GlowingCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Image(
                                    bitmap = referenceBitmap!!.asImageBitmap(),
                                    contentDescription = "Reference image",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                                Column {
                                    Text("Reference Photo Attached", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "Gemini will remix or edit this image",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(
                                onClick = { viewModel.setReferenceBitmap(null) },
                                modifier = Modifier.testTag("clear_ref_image_button")
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove reference image")
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (selectedProvider == AiProvider.GEMINI) {
                        OutlinedButton(
                            onClick = {
                                photoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .height(50.dp)
                                .testTag("pick_reference_image_button")
                        ) {
                            Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Edit Photo")
                        }
                    }

                    Button(
                        onClick = { viewModel.generateImage(prompt) },
                        enabled = prompt.isNotBlank() && uiState !is ImageGenUiState.Loading,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = xproColors.neonGlow,
                            contentColor = Color(0xFF06040C)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("generate_image_button")
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generate Artwork", fontWeight = FontWeight.Bold)
                    }
                }

                // Inspiration prompt chips
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    quickPrompts.forEachIndexed { idx, sample ->
                        Surface(
                            onClick = { prompt = sample },
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, xproColors.cardBorder),
                            modifier = Modifier.testTag("sample_prompt_$idx")
                        ) {
                            Text(
                                text = sample.take(32) + "...",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                // Output Canvas
                GlowingCard(
                    accentBorder = uiState is ImageGenUiState.Success,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    when (val state = uiState) {
                        is ImageGenUiState.Idle -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(260.dp)
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Filled.AutoAwesome,
                                        contentDescription = null,
                                        tint = xproColors.neonGlow,
                                        modifier = Modifier.size(44.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Ready to synthesize",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "Enter a prompt above and tap Generate Artwork",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        is ImageGenUiState.Loading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(300.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                PulseLoadingOrb(label = "Synthesizing high-res artwork (up to 60s)...")
                            }
                        }

                        is ImageGenUiState.Error -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Generation Failed",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        is ImageGenUiState.Success -> {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Image(
                                    bitmap = state.bitmap.asImageBitmap(),
                                    contentDescription = state.prompt,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(
                                            state.bitmap.width.toFloat() / state.bitmap.height.coerceAtLeast(1)
                                        )
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color.Black)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            val result = viewModel.saveBitmapToGallery(context, state.bitmap)
                                            scope.launch {
                                                snackbarHostState.showSnackbar(
                                                    result.getOrElse { it.localizedMessage ?: "Save failed" }
                                                )
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("save_generated_image_button")
                                    ) {
                                        Icon(Icons.Filled.Download, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Save")
                                    }
                                    Button(
                                        onClick = {
                                            viewModel.shareBitmap(context, state.bitmap, state.prompt)
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = xproColors.violetGlow,
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("share_generated_image_button")
                                    ) {
                                        Icon(Icons.Filled.Share, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Share")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
