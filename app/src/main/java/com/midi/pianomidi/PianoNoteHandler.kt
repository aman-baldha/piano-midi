package com.midi.pianomidi

import android.media.midi.MidiDevice
import android.media.midi.MidiReceiver
import android.util.Log

/**
 * Centralized Piano Note Handler - Single Point of Truth for Note Processing
 * 
 * This class ensures all note events (from virtual keyboard or physical device)
 * are processed consistently through a single pipeline.
 */
class PianoNoteHandler(
    private val soundPlayer: PianoSoundPlayer,
    private val midiConnectionManager: MidiConnectionManager
) {
    companion object {
        private const val TAG = "PianoNoteHandler"
    }
    
    private var deviceInputPort: android.media.midi.MidiInputPort? = null
    
    init {
        // Setup input port when device is connected
        setupInputPort()
    }
    
    /**
     * Setup MIDI input port for sending notes to device
     */
    private fun setupInputPort() {
        try {
            val device = midiConnectionManager.getActiveMidiDevice()
            device?.let { midiDevice ->
                val inputPortCount = midiDevice.info.inputPortCount
                if (inputPortCount > 0) {
                    deviceInputPort = midiDevice.openInputPort(0)
                    Log.d(TAG, "MIDI input port opened for note sending")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up input port: ${e.message}", e)
            deviceInputPort = null
        }
    }
    
    /**
     * Process a note-on event
     * This is the single entry point for all note-on events
     */
    fun onNoteOn(note: Int, velocity: Int, channel: Int = 0) {
        Log.d(TAG, "Note On: note=$note, velocity=$velocity, channel=$channel")
        
        // 1. Play sound through sound player
        soundPlayer.playNote(note, velocity)
        
        // 2. Send MIDI note to connected device (if connected)
        sendMidiNoteOn(note, velocity, channel)
    }
    
    /**
     * Process a note-off event
     * This is the single entry point for all note-off events
     */
    fun onNoteOff(note: Int, velocity: Int, channel: Int = 0) {
        Log.d(TAG, "Note Off: note=$note, velocity=$velocity, channel=$channel")
        
        // Send MIDI note-off to connected device (if connected)
        sendMidiNoteOff(note, velocity, channel)
    }
    
    /**
     * Send MIDI note-on to connected device
     */
    private fun sendMidiNoteOn(note: Int, velocity: Int, channel: Int) {
        if (!midiConnectionManager.isConnected()) {
            return
        }
        
        // Re-setup input port if needed
        if (deviceInputPort == null) {
            setupInputPort()
        }
        
        try {
            deviceInputPort?.let { port ->
                val sender = object : MidiReceiver() {
                    override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
                        port.onSend(msg, offset, count, timestamp)
                    }
                }
                
                val noteOnMessage = byteArrayOf(
                    (0x90 or channel).toByte(), // Note On, channel
                    note.toByte(),
                    velocity.coerceIn(0, 127).toByte()
                )
                sender.onSend(noteOnMessage, 0, noteOnMessage.size, System.nanoTime())
                Log.d(TAG, "Sent MIDI note-on to device: note=$note")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending MIDI note-on: ${e.message}", e)
        }
    }
    
    /**
     * Send MIDI note-off to connected device
     */
    private fun sendMidiNoteOff(note: Int, velocity: Int, channel: Int) {
        if (!midiConnectionManager.isConnected()) {
            return
        }
        
        // Re-setup input port if needed
        if (deviceInputPort == null) {
            setupInputPort()
        }
        
        try {
            deviceInputPort?.let { port ->
                val sender = object : MidiReceiver() {
                    override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
                        port.onSend(msg, offset, count, timestamp)
                    }
                }
                
                val noteOffMessage = byteArrayOf(
                    (0x80 or channel).toByte(), // Note Off, channel
                    note.toByte(),
                    velocity.coerceIn(0, 127).toByte()
                )
                sender.onSend(noteOffMessage, 0, noteOffMessage.size, System.nanoTime())
                Log.d(TAG, "Sent MIDI note-off to device: note=$note")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending MIDI note-off: ${e.message}", e)
        }
    }
    
    /**
     * Refresh input port connection (call when device connects/disconnects)
     */
    fun refreshConnection() {
        deviceInputPort?.close()
        deviceInputPort = null
        setupInputPort()
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        deviceInputPort?.close()
        deviceInputPort = null
    }
}

