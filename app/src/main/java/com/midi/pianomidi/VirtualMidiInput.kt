package com.midi.pianomidi

import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Virtual MIDI input simulator for testing without a physical MIDI device
 * Allows manual triggering of MIDI notes for testing the learning mode
 */
class VirtualMidiInput(
    private val callback: MidiInputCallback
) {
    companion object {
        private const val TAG = "VirtualMidiInput"
    }
    
    private val handler = Handler(Looper.getMainLooper())
    
    /**
     * Simulate a MIDI note on event
     */
    fun sendNoteOn(note: Int, velocity: Int = 100, channel: Int = 0) {
        Log.d(TAG, "Virtual MIDI: Note On - Note: $note, Velocity: $velocity, Channel: $channel")
        handler.post {
            callback.onNoteOn(note, velocity, channel)
            // Create a mock MIDI message
            val message = byteArrayOf(
                (0x90 or channel).toByte(), // Note On status byte
                note.toByte(),
                velocity.toByte()
            )
            callback.onMidiMessage(message, System.currentTimeMillis())
        }
    }
    
    /**
     * Simulate a MIDI note off event
     */
    fun sendNoteOff(note: Int, velocity: Int = 0, channel: Int = 0) {
        Log.d(TAG, "Virtual MIDI: Note Off - Note: $note, Velocity: $velocity, Channel: $channel")
        handler.post {
            callback.onNoteOff(note, velocity, channel)
            // Create a mock MIDI message
            val message = byteArrayOf(
                (0x80 or channel).toByte(), // Note Off status byte
                note.toByte(),
                velocity.toByte()
            )
            callback.onMidiMessage(message, System.currentTimeMillis())
        }
    }
    
    /**
     * Simulate playing a note (note on followed by note off after duration)
     */
    fun playNote(note: Int, durationMs: Long = 500, velocity: Int = 100, channel: Int = 0) {
        sendNoteOn(note, velocity, channel)
        handler.postDelayed({
            sendNoteOff(note, 0, channel)
        }, durationMs)
    }
}

