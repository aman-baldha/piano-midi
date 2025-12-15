package com.midi.pianomidi.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight

/**
 * Dialog to prompt user to enable Bluetooth
 */
@Composable
fun BluetoothEnableDialog(
    onEnableClick: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Bluetooth Required",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "Bluetooth is required to connect to MIDI devices. " +
                        "Please enable Bluetooth to continue."
            )
        },
        confirmButton = {
            TextButton(onClick = onEnableClick) {
                Text("Enable Bluetooth")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

