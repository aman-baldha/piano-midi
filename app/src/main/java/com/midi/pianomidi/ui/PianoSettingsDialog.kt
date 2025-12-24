package com.midi.pianomidi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.midi.pianomidi.SuperrServiceManager
import com.midi.pianomidi.ui.theme.*

@Composable
fun PianoSettingsDialog(
    onDismiss: () -> Unit,
    superrServiceManager: SuperrServiceManager,
    initialSensitivity: Int = 50,
    initialTheme: Int = 0,
    initialTranspose: Int = 0,
    onSettingsChanged: (Int, Int, Int) -> Unit
) {
    var sensitivity by remember { mutableStateOf(initialSensitivity.toFloat()) }
    var currentTheme by remember { mutableStateOf(initialTheme) }
    var transpose by remember { mutableStateOf(initialTranspose.toFloat()) }

    val themes = listOf("Aurora", "Fire", "Matrix")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight()
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = NeonGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Piano Configuration",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Sensitivity Setting
                SettingsItem(title = "Key Sensitivity", value = "${sensitivity.toInt()}%") {
                    Slider(
                        value = sensitivity,
                        onValueChange = { 
                            sensitivity = it
                            superrServiceManager.setSensitivity(it.toInt())
                        },
                        valueRange = 0f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = NeonGreen,
                            activeTrackColor = NeonGreen,
                            inactiveTrackColor = Color(0xFF252525)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Theme Setting
                SettingsItem(title = "Visual Theme", value = themes[currentTheme]) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        themes.forEachIndexed { index, name ->
                            val isSelected = currentTheme == index
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { 
                                        currentTheme = index
                                        superrServiceManager.setTheme(index)
                                    },
                                color = if (isSelected) NeonGreen else Color(0xFF151C18),
                                contentColor = if (isSelected) Color.Black else TextSecondary
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Transpose Setting
                SettingsItem(title = "Transpose", value = if (transpose > 0) "+${transpose.toInt()}" else "${transpose.toInt()}") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = { 
                                if (transpose > -12) {
                                    transpose--
                                    superrServiceManager.setTranspose(transpose.toInt())
                                }
                            }
                        ) {
                            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Decrease", tint = NeonGreen)
                        }
                        
                        Slider(
                            value = transpose,
                            onValueChange = { 
                                transpose = it
                                superrServiceManager.setTranspose(it.toInt())
                            },
                            valueRange = -12f..12f,
                            steps = 23,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = NeonGreen,
                                activeTrackColor = NeonGreen,
                                inactiveTrackColor = Color(0xFF252525)
                            )
                        )

                        IconButton(
                            onClick = { 
                                if (transpose < 12) {
                                    transpose++
                                    superrServiceManager.setTranspose(transpose.toInt())
                                }
                            }
                        ) {
                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Increase", tint = NeonGreen)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Save/Apply Button
                Button(
                    onClick = { 
                        onSettingsChanged(sensitivity.toInt(), currentTheme, transpose.toInt())
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black)
                ) {
                    Text(
                        text = "Apply Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsItem(
    title: String,
    value: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = NeonGreen,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}
