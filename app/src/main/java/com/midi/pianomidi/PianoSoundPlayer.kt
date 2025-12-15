package com.midi.pianomidi

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Piano sound player using SoundPool for low-latency audio playback
 */
class PianoSoundPlayer(private val context: Context) {
    
    companion object {
        private const val TAG = "PianoSoundPlayer"
        private const val MAX_STREAMS = 10
    }
    
    private var soundPool: SoundPool? = null
    private val noteSoundMap = mutableMapOf<Int, Int>() // MIDI note -> Sound ID
    
    init {
        initializeSoundPool()
        loadPianoSounds()
    }
    
    /**
     * Initialize SoundPool for low-latency audio
     */
    private fun initializeSoundPool() {
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            
            soundPool = SoundPool.Builder()
                .setMaxStreams(MAX_STREAMS)
                .setAudioAttributes(audioAttributes)
                .build()
            
            Log.d(TAG, "SoundPool initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize SoundPool: ${e.message}", e)
        }
    }
    
    /**
     * Load piano sounds for MIDI notes
     * Note: In a real implementation, you would load actual piano sound files
     * For now, we'll generate synthetic tones or use system sounds
     */
    private fun loadPianoSounds() {
        // Note: This is a placeholder. In a real app, you would:
        // 1. Have piano sound files (WAV/OGG) for each note in res/raw/
        // 2. Load them using soundPool.load(context, R.raw.piano_c4, 1)
        // 3. Store the sound IDs in noteSoundMap
        
        // For now, we'll use a tone generator approach or system beep
        // This requires additional implementation with AudioTrack or ToneGenerator
        Log.d(TAG, "Piano sounds loading (placeholder - needs actual sound files)")
    }
    
    /**
     * Play a piano note sound
     */
    fun playNote(midiNote: Int, velocity: Int = 100) {
        try {
            val soundId = noteSoundMap[midiNote]
            if (soundId != null && soundId > 0) {
                // Normalize velocity to 0.0-1.0 range
                val volume = (velocity / 127.0f).coerceIn(0.0f, 1.0f)
                soundPool?.play(soundId, volume, volume, 1, 0, 1.0f)
            } else {
                // Fallback: Generate a simple tone
                playSyntheticNote(midiNote, velocity)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing note: ${e.message}", e)
        }
    }
    
    /**
     * Play a synthetic note using AudioTrack for better sound quality
     */
    private fun playSyntheticNote(midiNote: Int, velocity: Int) {
        try {
            // Calculate frequency from MIDI note number
            // Frequency = 440 * 2^((midiNote - 69) / 12)
            val frequency = 440.0 * Math.pow(2.0, (midiNote - 69) / 12.0)
            
            // Generate a simple sine wave tone
            val sampleRate = 44100
            val duration = 0.3 // seconds
            val numSamples = (sampleRate * duration).toInt()
            val samples = ShortArray(numSamples)
            
            for (i in samples.indices) {
                val time = i.toDouble() / sampleRate
                // Generate sine wave with envelope (fade out)
                val amplitude = Math.sin(2 * Math.PI * frequency * time)
                val envelope = 1.0 - (time / duration) // Fade out
                samples[i] = (amplitude * envelope * Short.MAX_VALUE * (velocity / 127.0)).toInt().toShort()
            }
            
            // Play using AudioTrack
            val audioTrack = android.media.AudioTrack(
                android.media.AudioManager.STREAM_MUSIC,
                sampleRate,
                android.media.AudioFormat.CHANNEL_OUT_MONO,
                android.media.AudioFormat.ENCODING_PCM_16BIT,
                numSamples * 2, // 2 bytes per sample
                android.media.AudioTrack.MODE_STATIC
            )
            
            audioTrack.write(samples, 0, samples.size)
            audioTrack.play()
            
            // Stop and release after duration
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                audioTrack.stop()
                audioTrack.release()
            }, (duration * 1000).toLong())
            
        } catch (e: Exception) {
            Log.w(TAG, "Could not generate synthetic note: ${e.message}")
            // Fallback to system beep
            try {
                android.media.ToneGenerator(
                    android.media.AudioManager.STREAM_MUSIC,
                    50
                ).startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 100)
            } catch (ex: Exception) {
                Log.e(TAG, "Could not play fallback tone: ${ex.message}")
            }
        }
    }
    
    /**
     * Stop all sounds
     */
    fun stopAll() {
        soundPool?.autoPause()
    }
    
    /**
     * Release resources
     */
    fun release() {
        soundPool?.release()
        soundPool = null
        noteSoundMap.clear()
        Log.d(TAG, "PianoSoundPlayer released")
    }
}

