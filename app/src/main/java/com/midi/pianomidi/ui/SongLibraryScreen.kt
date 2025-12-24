package com.midi.pianomidi.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midi.pianomidi.KeyHighlightColor
import com.midi.pianomidi.R
import com.midi.pianomidi.Song
import com.midi.pianomidi.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongLibraryScreen(
    onNavigateBack: () -> Unit,
    onSongSelect: (Song) -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // MIDI and Particle State (similar to FreePlayScreen)
    var currentNote by remember { mutableStateOf<Int?>(null) }
    var highlightedNotes by remember { mutableStateOf<Map<Int, KeyHighlightColor>>(emptyMap()) }
    var particles by remember { mutableStateOf<List<ColorParticle>>(emptyList()) }
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
    var activeNotes by remember { mutableStateOf<Map<Int, com.midi.pianomidi.NoteVisualizerState>>(emptyMap()) }
    val currentOctaveStart = 48 // Fixed C3 for Song Library as well

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val density = configuration.densityDpi / 160f
    val screenWidthPx = remember { configuration.screenWidthDp * density }
    val screenHeightPx = remember { configuration.screenHeightDp * density }

    val midiConnectionManager = remember { com.midi.pianomidi.MidiConnectionManager.getInstance(context) }
    val soundPlayer = remember { com.midi.pianomidi.PianoSoundPlayer(context) }
    val noteHandler = remember { com.midi.pianomidi.PianoNoteHandler(soundPlayer, midiConnectionManager) }

    // Particle/Time Update Loop
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(16)
            val now = System.currentTimeMillis()
            currentTime = now
            
            particles = particles.mapNotNull { particle ->
                val elapsed = now - particle.startTime
                if (elapsed >= 0) {
                    val distance = particle.velocity * (elapsed / 16f)
                    val newY = particle.y - distance
                    val newAlpha = particle.alpha * (1f - elapsed / 2000f)
                    if (newY > -100f && newAlpha > 0.1f) {
                        particle.copy(y = newY, alpha = newAlpha.coerceIn(0f, 1f))
                    } else null
                } else particle
            }
        }
    }

    // MIDI Input Callback
    LaunchedEffect(Unit) {
        midiConnectionManager.setInputCallback(object : com.midi.pianomidi.MidiInputCallback {
            override fun onNoteOn(note: Int, velocity: Int, channel: Int) {
                noteHandler.onNoteOn(note, velocity, channel)
                currentNote = note
                val noteColor = NeonGreen // Simple for library
                
                activeNotes = activeNotes + (note to com.midi.pianomidi.NoteVisualizerState(
                    note = note, velocity = velocity, startTime = System.currentTimeMillis(), color = noteColor
                ))
                
                val barWidth = screenWidthPx / 25f
                val particleX = (note % 25) * barWidth + barWidth / 2
                val now = System.currentTimeMillis()
                val newParticles = (0 until 10).map { i ->
                    ColorParticle(
                        x = particleX, y = screenHeightPx, color = noteColor,
                        velocity = 2f + (velocity / 127f) * 3f, size = 4f + (velocity / 127f) * 6f,
                        alpha = 0.8f - (i * 0.05f), startTime = now + (i * 10L)
                    )
                }
                particles = particles + newParticles
            }
            override fun onNoteOff(note: Int, velocity: Int, channel: Int) {
                noteHandler.onNoteOff(note, velocity, channel)
                if (currentNote == note) currentNote = null
                coroutineScope.launch {
                    kotlinx.coroutines.delay(4000)
                    activeNotes = activeNotes - note
                }
            }
            override fun onMidiMessage(message: ByteArray, timestamp: Long) {}
        })
    }
    
    DisposableEffect(Unit) {
        onDispose {
            noteHandler.cleanup()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Background Grid Pattern (copied from FreePlayScreen for consistency)
        com.midi.pianomidi.ui.GridBackgroundPattern()
        
        // Upward flowing color particles
        com.midi.pianomidi.ui.ColorFlowParticles(
            particles = particles,
            currentTime = currentTime,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Main Content (Scrollable List)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(scrollState)
            ) {
            // Back navigation and Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowLeft,
                    contentDescription = "Back",
                    tint = TextSecondary,
                    modifier = Modifier.clickable { onNavigateBack() }.size(24.dp)
                )
                Text(
                    text = "DASHBOARD",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = TextSecondary,
                        letterSpacing = 1.1.sp
                    )
                )
            }
            
            Text(
                text = "Song Library",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                ),
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Search Bar
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .clip(RoundedCornerShape(12.dp)),
                placeholder = { 
                    Text("Search songs, artists, or genres...", color = TextSecondary) 
                },
                leadingIcon = { 
                    Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) 
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF151C18),
                    unfocusedContainerColor = Color(0xFF151C18),
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                singleLine = true
            )

            // Filters
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterPill("All", isActive = true)
                FilterPillWithIcon("Difficulty", Icons.Default.KeyboardArrowDown)
                FilterPillWithIcon("Genre", Icons.Default.KeyboardArrowDown)
                FilterPillWithIcon("Favorites", Icons.Default.Star)
                FilterPillWithIcon("New", Icons.Default.KeyboardArrowDown)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Continue Learning Section
            SectionHeader(title = "Continue Learning", icon = Icons.Default.PlayArrow)
            FeaturedSongCard(
                title = "Fur Elise",
                artist = "Ludwig van Beethoven",
                lessonInfo = "Lesson 3 of 5",
                progress = 0.6f,
                onClick = { /* TODO */ }
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Hot Right Now Section
            SectionHeader(title = "Hot Right Now")
            
            val filteredSongs = remember(searchQuery) {
                if (searchQuery.isEmpty()) {
                    listOf("Eliata", "Nuvole Bianche", "River Flows In You", "Experience", "Clair de Lune")
                } else {
                    listOf("Eliata", "Nuvole Bianche", "River Flows In You", "Experience", "Clair de Lune").filter {
                        it.contains(searchQuery, ignoreCase = true)
                    }
                }
            }

            if (filteredSongs.isEmpty() && searchQuery.isNotEmpty()) {
                // Tune Generation Box
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text("Not found? Let's generate it!", color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text("I can create a MIDI sequence for \"$searchQuery\"", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { 
                                val generatedNotes = List(20) { i ->
                                    com.midi.pianomidi.Note(60 + (i % 12), 500L, i * 600L)
                                }
                                onSongSelect(com.midi.pianomidi.Song(searchQuery, 120, generatedNotes))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black)
                        ) {
                            Text("Generate & Play")
                        }
                    }
                }
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(filteredSongs) { songTitle ->
                    SongCard(
                        title = songTitle,
                        difficulty = if (songTitle == "Eliata") "HARD" else "MEDIUM",
                        imageRes = if (songTitle == "Eliata") R.drawable.bg_learn_piano else R.drawable.bg_song_library,
                        onClick = {
                            // Mock song selection
                            val notes = List(20) { i ->
                                com.midi.pianomidi.Note(60 + (i % 12), 500L, i * 600L)
                            }
                            onSongSelect(com.midi.pianomidi.Song(songTitle, 120, notes))
                        }
                    )
                }
            }
            }
            
            // Piano Keyboard at the bottom
            PianoKeyboard(
                currentNote = currentNote,
                highlightedNotes = highlightedNotes,
                octaveStart = currentOctaveStart,
                onNoteClick = { note ->
                    val velocity = 100
                    highlightedNotes = highlightedNotes + (note to KeyHighlightColor.YELLOW)
                    coroutineScope.launch {
                        kotlinx.coroutines.delay(300)
                        highlightedNotes = highlightedNotes - note
                    }
                    currentNote = note
                    noteHandler.onNoteOn(note, velocity, 0)
                    
                    // Particle Logic for click
                    val noteColor = NeonGreen
                    activeNotes = activeNotes + (note to com.midi.pianomidi.NoteVisualizerState(
                        note = note, velocity = velocity, startTime = System.currentTimeMillis(), color = noteColor
                    ))
                    val barWidth = screenWidthPx / 25f
                    val particleX = (note % 25) * barWidth + barWidth / 2
                    val now = System.currentTimeMillis()
                    val newParticles = (0 until 10).map { i ->
                        ColorParticle(
                            x = particleX, y = screenHeightPx, color = noteColor,
                            velocity = 2f, size = 10f, alpha = 0.8f, startTime = now + (i * 10L)
                        )
                    }
                    particles = particles + newParticles
                    
                    coroutineScope.launch {
                        kotlinx.coroutines.delay(500)
                        noteHandler.onNoteOff(note, 0, 0)
                        if (currentNote == note) currentNote = null
                    }
                    coroutineScope.launch {
                        kotlinx.coroutines.delay(4000)
                        activeNotes = activeNotes - note
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
fun FilterPill(text: String, isActive: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isActive) NeonGreen else Color(0xFF151C18),
        contentColor = if (isActive) Color.Black else TextSecondary,
        modifier = Modifier.clickable { }
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
fun FilterPillWithIcon(text: String, icon: ImageVector) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF151C18),
        contentColor = TextSecondary,
        modifier = Modifier.border(1.dp, Color(0xFF252525), RoundedCornerShape(20.dp)).clickable { }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            )
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun SectionHeader(title: String, icon: ImageVector? = null) {
    Row(
        modifier = Modifier.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(24.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        )
    }
}

@Composable
fun FeaturedSongCard(
    title: String,
    artist: String,
    lessonInfo: String,
    progress: Float,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151C18))
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                Image(
                    painter = painterResource(id = R.drawable.bg_learn_piano),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                // In Progress Label
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(NeonGreen))
                        Text("IN PROGRESS", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            
            Column(
                modifier = Modifier.weight(1f).padding(24.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(title, style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary))
                    Text(artist, color = TextSecondary, style = MaterialTheme.typography.titleMedium)
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Text(lessonInfo, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(12.dp).clip(RoundedCornerShape(6.dp)),
                        color = NeonGreen,
                        trackColor = Color(0xFF252525)
                    )
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Resume", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                    }
                    
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = Color(0xFF252525)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SongCard(title: String, difficulty: String, imageRes: Int, onClick: () -> Unit = {}) {
    Card(
        modifier = Modifier
            .width(220.dp)
            .height(280.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = imageRes),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            // Difficulty Label
            Surface(
                color = Color.Black.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
            ) {
                Text(
                    text = difficulty,
                    color = if (difficulty == "HARD") Color(0xFFFF4444) else NeonGreen,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
            
            // Title Overlay
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                        )
                    )
                    .padding(16.dp)
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}
