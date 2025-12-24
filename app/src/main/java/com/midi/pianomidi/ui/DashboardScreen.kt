package com.midi.pianomidi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midi.pianomidi.MidiConnectionCallback
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.alpha
import com.midi.pianomidi.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import com.midi.pianomidi.MidiConnectionManager
import com.midi.pianomidi.ui.theme.*

/**
 * Main Dashboard Screen - Entry point for the app
 * Displays connection status and feature cards
 */
@Composable
fun DashboardScreen(
    midiConnectionManager: MidiConnectionManager,
    onNavigateToLearning: () -> Unit = {},
    onNavigateToFreePlay: () -> Unit = {},
    onNavigateToSongLibrary: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onManageConnection: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    
    // Responsive padding based on screen width
    val horizontalPadding = remember(screenWidth) {
        when {
            screenWidth < 400.dp -> 16.dp
            screenWidth < 600.dp -> 20.dp
            else -> 24.dp
        }
    }
    
    // Observe MIDI connection state
    var isConnected by remember { mutableStateOf(midiConnectionManager.isConnected()) }
    val currentDevice = remember { mutableStateOf(midiConnectionManager.getCurrentDevice()) }
    
    // Update connection state reactively
    LaunchedEffect(Unit) {
        midiConnectionManager.setConnectionCallback(object : MidiConnectionCallback {
            override fun onConnected(device: com.midi.pianomidi.MidiDeviceWrapper) {
                isConnected = true
                currentDevice.value = device
            }
            
            override fun onDisconnected(device: com.midi.pianomidi.MidiDeviceWrapper) {
                isConnected = false
                currentDevice.value = null
            }
            
            override fun onConnectionError(error: String) {
                isConnected = false
                currentDevice.value = null
            }
            
            override fun onReconnecting(device: com.midi.pianomidi.MidiDeviceWrapper) {}
            override fun onReconnected(device: com.midi.pianomidi.MidiDeviceWrapper) {
                isConnected = true
                currentDevice.value = device
            }
        })
        
        // Check initial state
        isConnected = midiConnectionManager.isConnected()
        currentDevice.value = midiConnectionManager.getCurrentDevice()
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        
        // Right Side Main Content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = horizontalPadding, vertical = 20.dp)
        ) {
            // Top App Bar with Dashboard title
            DashboardTopBar(
                isConnected = isConnected,
                deviceName = currentDevice.value?.name ?: "MIDI Piano",
                onSettingsClick = onNavigateToSettings,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Subtitle
            Text(
                text = "Pick a mode to start playing",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    color = TextSecondary,
                    letterSpacing = 0.2.sp
                ),
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Main Content - Landscape-optimized horizontal layout with larger cards
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Left Side - Status Card (takes 45% width, larger)
                StatusCard(
                    isConnected = isConnected,
                    deviceName = currentDevice.value?.name ?: "No Device",
                    onManageConnection = onManageConnection,
                    modifier = Modifier
                        .weight(0.45f)
                        .fillMaxHeight()
                )
                
                // Right Side - Feature Cards (restacked)
                Column(
                    modifier = Modifier
                        .weight(0.55f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Free Play Card
                    FeatureCard(
                        title = "Free Play",
                        description = "Jam without limits on the on-screen keys.",
                        icon = Icons.Default.Add,
                        iconColor = NeonGreen,
                        buttonText = "Open Keys",
                        buttonIcon = Icons.Default.PlayArrow,
                        enabled = true,
                        onClick = onNavigateToFreePlay,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        backgroundImageResId = R.drawable.bg_free_play
                    )
                    
                    // Song Library Card
                    FeatureCard(
                        title = "Song Library",
                        description = "Browse classical & pop hits.",
                        icon = Icons.Default.List,
                        iconColor = NeonGreen,
                        buttonText = "Browse",
                        buttonIcon = null,
                        enabled = true,
                        onClick = onNavigateToSongLibrary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        backgroundImageResId = R.drawable.bg_song_library
                    )
                }
            }
        }
    }
}

/**
 * Top App Bar Component
 * Shows logo, Bluetooth connection status, and settings icon
 */
@Composable
fun DashboardTopBar(
    isConnected: Boolean,
    deviceName: String,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: App Logo + Dashboard Title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(NeonGreen)
                    .shadow(
                        elevation = 4.dp,
                        shape = RoundedCornerShape(24.dp),
                        spotColor = NeonGreen.copy(alpha = 0.4f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "App Logo",
                    tint = Color.Black,
                    modifier = Modifier.size(28.dp)
                )
            }
            
            Text(
                text = "Dashboard",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    letterSpacing = (-0.5).sp
                )
            )
        }
        
        // Center: Bluetooth Status Pill
        Surface(
            modifier = Modifier.padding(horizontal = 16.dp),
            shape = RoundedCornerShape(20.dp),
            color = if (isConnected) NeonGreen else StatusInactive,
            contentColor = Color.Black,
            shadowElevation = 3.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Bluetooth icon - using Settings icon as placeholder
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color.Black
                )
                Text(
                    text = if (isConnected) {
                        val displayName = if (deviceName.length > 20) deviceName.take(17) + "..." else deviceName
                        "Connected to $displayName"
                    } else "Not Connected",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.1.sp
                    ),
                    color = Color.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        
        // Right: Settings Icon
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = TextPrimary,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

/**
 * Status Card Component
 * Shows MIDI connection status with visual feedback
 */
@Composable
fun StatusCard(
    isConnected: Boolean,
    deviceName: String,
    onManageConnection: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(28.dp),
                spotColor = if (isConnected) NeonGreen.copy(alpha = 0.4f) else Color.Transparent
            )
            .border(
                width = if (isConnected) 1.5.dp else 1.dp,
                color = if (isConnected) NeonGreen.copy(alpha = 0.6f) else DarkSurfaceVariant,
                shape = RoundedCornerShape(28.dp)
            ),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) {
                // Subtle green gradient background when connected
                Color(0xFF0F1F18)
            } else {
                DarkSurface
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Status Icon
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(
                                if (isConnected) NeonGreen else StatusInactive
                            )
                            .shadow(
                                elevation = 6.dp,
                                shape = RoundedCornerShape(28.dp),
                                spotColor = if (isConnected) NeonGreen.copy(alpha = 0.5f) else Color.Transparent
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isConnected) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isConnected) "Piano Connected" else "Piano Not Connected",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                letterSpacing = (-0.3).sp
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isConnected) {
                                "Your MIDI piano is paired and ready. Enjoy interactive feedback and light-up keys."
                            } else {
                                "Connect a MIDI piano to unlock interactive features."
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 13.sp,
                                color = TextSecondary,
                                lineHeight = 18.sp,
                                letterSpacing = 0.1.sp
                            ),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Manage Connection Button
            Button(
                onClick = onManageConnection,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DarkSurfaceVariant,
                    contentColor = TextPrimary
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 3.dp,
                    pressedElevation = 5.dp
                ),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Manage Connection",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Feature Card Component
 * Reusable card for dashboard features with optional background image
 */
@Composable
fun FeatureCard(
    title: String,
    description: String,
    icon: ImageVector,
    iconColor: Color,
    buttonText: String,
    buttonIcon: ImageVector?,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    backgroundImageResId: Int? = null // Resource ID for background image
) {
    Card(
        modifier = modifier
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = if (enabled) iconColor.copy(alpha = 0.3f) else Color.Transparent
            )
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent // Transparent to show background image
        )
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Background Image - Only load if resource exists and is valid
            if (backgroundImageResId != null && backgroundImageResId != 0) {
                // Background image with better positioning
                Image(
                    painter = painterResource(id = backgroundImageResId),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .matchParentSize()
                        .alpha(0.35f), // Subtle visibility
                    contentScale = ContentScale.Crop
                )
                
                // Gradient overlay for better text readability and depth
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    DarkSurface.copy(alpha = 0.65f),
                                    DarkSurface.copy(alpha = 0.8f),
                                    DarkSurface.copy(alpha = 0.85f)
                                ),
                                startY = 0f,
                                endY = Float.POSITIVE_INFINITY
                            )
                        )
                )
            } else {
                // No background image - use subtle gradient for depth
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    DarkSurface,
                                    DarkSurfaceVariant.copy(alpha = 0.3f)
                                )
                            )
                        )
                )
            }
            
            // Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = if (compact) 20.dp else 24.dp, vertical = if (compact) 18.dp else 24.dp)
            ) {
                // Top Row: Icon + Title + Button (all on same row, no overlap)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Icon + Title
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp) // Add end padding to prevent overlap
                    ) {
                        // Icon Circle
                        Box(
                            modifier = Modifier
                                .size(if (compact) 40.dp else 48.dp)
                                .clip(RoundedCornerShape(if (compact) 20.dp else 24.dp))
                                .background(iconColor.copy(alpha = 0.25f))
                                .shadow(
                                    elevation = 3.dp,
                                    shape = RoundedCornerShape(if (compact) 20.dp else 24.dp),
                                    spotColor = iconColor.copy(alpha = 0.3f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = iconColor,
                                modifier = Modifier.size(if (compact) 20.dp else 24.dp)
                            )
                        }
                        
                        // Title - ensure it doesn't overlap with button, add proper padding
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = if (compact) 16.sp else 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                letterSpacing = (-0.2).sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = true)
                                .padding(end = 8.dp) // Add end padding for text
                        )
                    }
                    
                    // Right: Button (fixed width, won't overlap) - ensure it doesn't shrink
                    TextButton(
                        onClick = onClick,
                        enabled = enabled,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = if (enabled) TextPrimary else TextTertiary
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .wrapContentWidth() // Ensure button doesn't shrink
                    ) {
                        if (buttonIcon != null) {
                            Icon(
                                imageVector = buttonIcon,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = buttonText,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.2.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                // Description below (only if not compact)
                if (!compact) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 16.sp,
                            letterSpacing = 0.1.sp
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 60.dp) // Align with title (icon width + spacing)
                    )
                }
            }
            
            // Show description below for compact cards
            if (compact) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp,
                        letterSpacing = 0.1.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 60.dp) // Align with title (icon width + spacing)
                )
            }
            } // Close Column
        } // Close Box
    } // Close Card


