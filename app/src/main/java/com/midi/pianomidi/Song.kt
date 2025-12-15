package com.midi.pianomidi

/**
 * Data class representing a single musical note in a song
 * 
 * @param midiNote MIDI note number (0-127). Middle C (C4) = 60
 * @param duration Duration of the note in milliseconds
 * @param timing Start time of the note in milliseconds from the beginning of the song
 */
data class Note(
    val midiNote: Int,
    val duration: Long,
    val timing: Long
) {
    init {
        require(midiNote in 0..127) { "MIDI note must be between 0 and 127" }
        require(duration > 0) { "Duration must be positive" }
        require(timing >= 0) { "Timing must be non-negative" }
    }
}

/**
 * Data class representing a complete song
 * 
 * @param title The title of the song
 * @param tempo Tempo in beats per minute (BPM)
 * @param notes List of notes in the song, ordered by timing
 */
data class Song(
    val title: String,
    val tempo: Int,
    val notes: List<Note>
) {
    init {
        require(tempo > 0) { "Tempo must be positive" }
        require(notes.isNotEmpty()) { "Song must contain at least one note" }
    }
    
    /**
     * Get the total duration of the song in milliseconds
     */
    fun getTotalDuration(): Long {
        return notes.maxOfOrNull { it.timing + it.duration } ?: 0L
    }
    
    /**
     * Get notes that should be played at a specific time
     */
    fun getNotesAtTime(timeMs: Long): List<Note> {
        return notes.filter { note ->
            timeMs >= note.timing && timeMs < note.timing + note.duration
        }
    }
}

/**
 * Sample song: "Twinkle Twinkle Little Star"
 * 
 * MIDI Note Reference (C4 = Middle C = 60):
 * - C4 = 60
 * - D4 = 62
 * - E4 = 64
 * - F4 = 65
 * - G4 = 67
 * - A4 = 69
 * 
 * Tempo: 120 BPM (500ms per quarter note)
 */
object TwinkleTwinkleLittleStar {
    
    fun createSong(): Song {
        val tempo = 120 // BPM
        val quarterNote = 500L // milliseconds at 120 BPM
        val halfNote = quarterNote * 2
        val wholeNote = quarterNote * 4
        
        val notes = mutableListOf<Note>()
        var currentTime = 0L
        
        // Helper function to add a note
        fun addNote(midiNote: Int, duration: Long) {
            notes.add(Note(midiNote, duration, currentTime))
            currentTime += duration
        }
        
        // Verse 1: "Twinkle twinkle little star"
        // C C G G A A G
        addNote(60, quarterNote) // C4
        addNote(60, quarterNote) // C4
        addNote(67, quarterNote) // G4
        addNote(67, quarterNote) // G4
        addNote(69, quarterNote) // A4
        addNote(69, quarterNote) // A4
        addNote(67, halfNote)    // G4
        
        // Verse 2: "How I wonder what you are"
        // F F E E D D C
        addNote(65, quarterNote) // F4
        addNote(65, quarterNote) // F4
        addNote(64, quarterNote) // E4
        addNote(64, quarterNote) // E4
        addNote(62, quarterNote) // D4
        addNote(62, quarterNote) // D4
        addNote(60, halfNote)    // C4
        
        // Verse 3: "Up above the world so high"
        // G G F F E E D
        addNote(67, quarterNote) // G4
        addNote(67, quarterNote) // G4
        addNote(65, quarterNote) // F4
        addNote(65, quarterNote) // F4
        addNote(64, quarterNote) // E4
        addNote(64, quarterNote) // E4
        addNote(62, halfNote)    // D4
        
        // Verse 4: "Like a diamond in the sky"
        // G G F F E E D
        addNote(67, quarterNote) // G4
        addNote(67, quarterNote) // G4
        addNote(65, quarterNote) // F4
        addNote(65, quarterNote) // F4
        addNote(64, quarterNote) // E4
        addNote(64, quarterNote) // E4
        addNote(62, halfNote)    // D4
        
        // Verse 5: "Twinkle twinkle little star"
        // C C G G A A G
        addNote(60, quarterNote) // C4
        addNote(60, quarterNote) // C4
        addNote(67, quarterNote) // G4
        addNote(67, quarterNote) // G4
        addNote(69, quarterNote) // A4
        addNote(69, quarterNote) // A4
        addNote(67, halfNote)    // G4
        
        // Verse 6: "How I wonder what you are"
        // F F E E D D C
        addNote(65, quarterNote) // F4
        addNote(65, quarterNote) // F4
        addNote(64, quarterNote) // E4
        addNote(64, quarterNote) // E4
        addNote(62, quarterNote) // D4
        addNote(62, quarterNote) // D4
        addNote(60, wholeNote)   // C4 (final note, held longer)
        
        return Song(
            title = "Twinkle Twinkle Little Star",
            tempo = tempo,
            notes = notes
        )
    }
}

