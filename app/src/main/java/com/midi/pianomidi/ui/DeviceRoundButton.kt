package com.midi.pianomidi.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midi.pianomidi.MidiDeviceWrapper
import com.midi.pianomidi.ui.theme.*

/**
 * Device Round Button Component
 * Circular button showing device name, displayed when devices are found
 */
@Composable
fun DeviceRoundButton(
    device: MidiDeviceWrapper,
    onClick: () -> Unit,
    isConnecting: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clickable(enabled = !isConnecting, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Round button with device icon/initial
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            NeonGreen,
                            NeonGreenDark
                        )
                    ),
                    shape = RoundedCornerShape(40.dp)
                )
                .then(
                    if (!isConnecting) {
                        Modifier.shadow(
                            elevation = 8.dp,
                            shape = RoundedCornerShape(40.dp),
                            spotColor = NeonGreen.copy(alpha = 0.5f)
                        )
                    } else {
                        Modifier.alpha(0.6f)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = Color.Black,
                    strokeWidth = 3.dp
                )
            } else {
                // Show first letter of device name or icon
                Text(
                    text = device.name.take(1).uppercase(),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                )
            }
        }
        
        // Device name (truncated if too long)
        Text(
            text = device.name.take(12) + if (device.name.length > 12) "..." else "",
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            ),
            maxLines = 1
        )
        
        // Device type badge
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = when (device.type) {
                com.midi.pianomidi.DeviceType.BLUETOOTH -> NeonGreen.copy(alpha = 0.2f)
                com.midi.pianomidi.DeviceType.USB -> OrangeAccent.copy(alpha = 0.2f)
                else -> DarkSurfaceVariant
            }
        ) {
            Text(
                text = device.type.name,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (device.type) {
                        com.midi.pianomidi.DeviceType.BLUETOOTH -> NeonGreen
                        com.midi.pianomidi.DeviceType.USB -> OrangeAccent
                        else -> TextSecondary
                    }
                )
            )
        }
    }
}

