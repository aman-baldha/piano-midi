package com.midi.pianomidi

import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Enum representing the playback state of the learning mode
 */
enum class LearningState {
    IDLE,       // Not started
    PLAYING,    // Currently playing/learning
    PAUSED,     // Paused
    COMPLETED   // Song completed
}

/**
 * Data class representing the current learning progress
 */
data class LearningProgress(
    val currentNoteIndex: Int,
    val totalNotes: Int,
    val completedNotes: Int,
    val currentNote: Note?,
    val nextNote: Note?,
    val isCorrect: Boolean = false,
    val state: LearningState
) {
    val progressPercentage: Float
        get() = if (totalNotes > 0) completedNotes.toFloat() / totalNotes else 0f
}

/**
 * Callback interface for learning mode events
 */
interface LearningModeCallback {
    /**
     * Called when the current note changes
     */
    fun onNoteChanged(currentNote: Note?, nextNote: Note?)
    
    /**
     * Called when a correct note is played
     */
    fun onCorrectNotePlayed(note: Note)
    
    /**
     * Called when an incorrect note is played
     */
    fun onIncorrectNotePlayed(playedNote: Int, expectedNote: Int)
    
    /**
     * Called when progress updates
     */
    fun onProgressUpdated(progress: LearningProgress)
    
    /**
     * Called when the song is completed
     */
    fun onSongCompleted()
    
    /**
     * Called when the state changes
     */
    fun onStateChanged(state: LearningState)
}

/**
 * Controller class for managing song learning mode.
 * Handles note validation, progress tracking, and playback control.
 */
class LearningModeController(
    private val song: Song,
    private val midiConnectionManager: MidiConnectionManager,
    private val callback: LearningModeCallback? = null
) : MidiInputCallback {
    
    companion object {
        private const val TAG = "LearningModeController"
        private const val FEEDBACK_DURATION_MS = 300L // Duration to show feedback
    }
    
    // State management
    private var currentState: LearningState = LearningState.IDLE
    private var currentNoteIndex: Int = 0
    private var completedNotes: Int = 0
    private var isWaitingForNote: Boolean = false
    
    // Timing and handlers
    private val handler = Handler(Looper.getMainLooper())
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private var feedbackJob: Job? = null
    
    // Sound feedback (optional - can be null if audio feedback not needed)
    private var soundPool: SoundPool? = null
    private var correctNoteSoundId: Int = 0
    private var incorrectNoteSoundId: Int = 0
    
    init {
        // Initialize sound pool for audio feedback
        initializeSoundPool()
        
        // Register this controller as MIDI input callback
        midiConnectionManager.setInputCallback(this)
    }
    
    /**
     * Initialize SoundPool for audio feedback
     */
    private fun initializeSoundPool() {
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            
            soundPool = SoundPool.Builder()
                .setMaxStreams(2)
                .setAudioAttributes(audioAttributes)
                .build()
            
            // Note: You would need to add sound files to res/raw/ for these to work
            // For now, we'll use system sounds or leave them as 0 (no sound)
            // correctNoteSoundId = soundPool?.load(context, R.raw.correct_note, 1) ?: 0
            // incorrectNoteSoundId = soundPool?.load(context, R.raw.incorrect_note, 1) ?: 0
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize SoundPool: ${e.message}")
            soundPool = null
        }
    }
    
    /**
     * Start the learning mode
     */
    fun start() {
        if (currentState == LearningState.COMPLETED) {
            reset()
        }
        
        if (currentState == LearningState.IDLE || currentState == LearningState.PAUSED) {
            currentState = LearningState.PLAYING
            currentNoteIndex = completedNotes
            isWaitingForNote = true
            
            updateCurrentNote()
            notifyStateChanged()
            Log.d(TAG, "Learning mode started")
        }
    }
    
    /**
     * Pause the learning mode
     */
    fun pause() {
        if (currentState == LearningState.PLAYING) {
            currentState = LearningState.PAUSED
            isWaitingForNote = false
            feedbackJob?.cancel()
            notifyStateChanged()
            Log.d(TAG, "Learning mode paused")
        }
    }
    
    /**
     * Resume the learning mode
     */
    fun resume() {
        if (currentState == LearningState.PAUSED) {
            currentState = LearningState.PLAYING
            isWaitingForNote = true
            updateCurrentNote()
            notifyStateChanged()
            Log.d(TAG, "Learning mode resumed")
        }
    }
    
    /**
     * Reset the learning mode to the beginning
     */
    fun reset() {
        currentState = LearningState.IDLE
        currentNoteIndex = 0
        completedNotes = 0
        isWaitingForNote = false
        feedbackJob?.cancel()
        
        updateCurrentNote()
        notifyStateChanged()
        notifyProgressUpdated()
        Log.d(TAG, "Learning mode reset")
    }
    
    /**
     * Get the current learning progress
     */
    fun getProgress(): LearningProgress {
        return LearningProgress(
            currentNoteIndex = currentNoteIndex,
            totalNotes = song.notes.size,
            completedNotes = completedNotes,
            currentNote = getCurrentNote(),
            nextNote = getNextNote(),
            state = currentState
        )
    }
    
    /**
     * Get the current note that should be played
     */
    fun getCurrentNote(): Note? {
        return if (currentNoteIndex < song.notes.size) {
            song.notes[currentNoteIndex]
        } else {
            null
        }
    }
    
    /**
     * Get the next note after the current one
     */
    fun getNextNote(): Note? {
        return if (currentNoteIndex + 1 < song.notes.size) {
            song.notes[currentNoteIndex + 1]
        } else {
            null
        }
    }
    
    /**
     * Check if the learning mode is active (playing or paused)
     */
    fun isActive(): Boolean {
        return currentState == LearningState.PLAYING || currentState == LearningState.PAUSED
    }
    
    /**
     * Check if the learning mode is playing
     */
    fun isPlaying(): Boolean {
        return currentState == LearningState.PLAYING
    }
    
    /**
     * Check if the song is completed
     */
    fun isCompleted(): Boolean {
        return currentState == LearningState.COMPLETED
    }
    
    /**
     * Handle MIDI note on event
     */
    override fun onNoteOn(note: Int, velocity: Int, channel: Int) {
        if (currentState != LearningState.PLAYING || !isWaitingForNote) {
            return
        }
        
        val expectedNote = getCurrentNote()
        if (expectedNote == null) {
            return
        }
        
        val isCorrect = note == expectedNote.midiNote
        
        if (isCorrect) {
            handleCorrectNote(expectedNote)
        } else {
            handleIncorrectNote(note, expectedNote.midiNote)
        }
    }
    
    /**
     * Handle MIDI note off event (not used for learning mode)
     */
    override fun onNoteOff(note: Int, velocity: Int, channel: Int) {
        // Note off events are not used in learning mode
        // We only care about note on events
    }
    
    /**
     * Handle raw MIDI message (not used for learning mode)
     */
    override fun onMidiMessage(message: ByteArray, timestamp: Long) {
        // Raw messages are handled by onNoteOn/onNoteOff
    }
    
    /**
     * Handle when the correct note is played
     */
    private fun handleCorrectNote(note: Note) {
        Log.d(TAG, "Correct note played: ${note.midiNote}")
        
        // Play success sound
        playFeedbackSound(true)
        
        // Update progress
        completedNotes++
        currentNoteIndex++
        isWaitingForNote = false
        
        // Notify callbacks
        callback?.onCorrectNotePlayed(note)
        notifyProgressUpdated()
        
        // Check if song is completed
        if (currentNoteIndex >= song.notes.size) {
            completeSong()
        } else {
            // Show visual feedback and advance to next note
            showFeedbackAndAdvance(true)
        }
    }
    
    /**
     * Handle when an incorrect note is played
     */
    private fun handleIncorrectNote(playedNote: Int, expectedNote: Int) {
        Log.d(TAG, "Incorrect note played: $playedNote (expected: $expectedNote)")
        
        // Play error sound
        playFeedbackSound(false)
        
        // Notify callback
        callback?.onIncorrectNotePlayed(playedNote, expectedNote)
        
        // Show visual feedback (but don't advance)
        showFeedbackAndAdvance(false)
    }
    
    /**
     * Show visual feedback and optionally advance to next note
     */
    private fun showFeedbackAndAdvance(isCorrect: Boolean) {
        // Cancel any existing feedback job
        feedbackJob?.cancel()
        
        // Create a temporary progress with feedback state
        val progressWithFeedback = getProgress().copy(isCorrect = isCorrect)
        callback?.onProgressUpdated(progressWithFeedback)
        
        // After feedback duration, update to next note if correct
        feedbackJob = coroutineScope.launch {
            delay(FEEDBACK_DURATION_MS)
            
            if (isCorrect && currentState == LearningState.PLAYING) {
                updateCurrentNote()
                isWaitingForNote = true
            } else if (!isCorrect && currentState == LearningState.PLAYING) {
                // Reset feedback state
                callback?.onProgressUpdated(getProgress())
                isWaitingForNote = true
            }
        }
    }
    
    /**
     * Update the current note display
     */
    private fun updateCurrentNote() {
        val currentNote = getCurrentNote()
        val nextNote = getNextNote()
        callback?.onNoteChanged(currentNote, nextNote)
        notifyProgressUpdated()
    }
    
    /**
     * Complete the song
     */
    private fun completeSong() {
        currentState = LearningState.COMPLETED
        isWaitingForNote = false
        feedbackJob?.cancel()
        
        notifyStateChanged()
        callback?.onSongCompleted()
        Log.d(TAG, "Song completed!")
    }
    
    /**
     * Play feedback sound
     */
    private fun playFeedbackSound(isCorrect: Boolean) {
        try {
            val soundId = if (isCorrect) correctNoteSoundId else incorrectNoteSoundId
            if (soundId > 0) {
                soundPool?.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to play feedback sound: ${e.message}")
        }
    }
    
    /**
     * Notify callback of state change
     */
    private fun notifyStateChanged() {
        callback?.onStateChanged(currentState)
    }
    
    /**
     * Notify callback of progress update
     */
    private fun notifyProgressUpdated() {
        callback?.onProgressUpdated(getProgress())
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        pause()
        feedbackJob?.cancel()
        soundPool?.release()
        soundPool = null
        Log.d(TAG, "LearningModeController cleaned up")
    }
}

