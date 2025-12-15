package com.midi.pianomidi

import android.media.midi.MidiDevice
import android.media.midi.MidiReceiver
import android.util.Log

/**
 * Controller for RGB lights on MIDI pianos
 * Sends MIDI messages to control RGB lighting on connected MIDI devices
 */
class MidiRgbController(private val midiDevice: MidiDevice) {
    
    companion object {
        private const val TAG = "MidiRgbController"
        // MIDI CC numbers commonly used for RGB control
        // These may vary by manufacturer - adjust as needed
        private const val CC_RED = 16
        private const val CC_GREEN = 17
        private const val CC_BLUE = 18
        private const val CC_BRIGHTNESS = 19
        
        // RGB color values for different states
        private const val COLOR_BLUE = 127 // Full blue for suggested note
        private const val COLOR_GREEN = 127 // Full green for correct note
        private const val COLOR_RED = 127 // Full red for incorrect note
        private const val COLOR_OFF = 0
    }
    
    // Store input port to send messages TO the device
    // Device's INPUT ports receive data FROM us
    private var deviceInputPort: android.media.midi.MidiInputPort? = null
    
    init {
        try {
            // Open device's input port (this receives data FROM us)
            val inputPortCount = midiDevice.info.inputPortCount
            if (inputPortCount > 0) {
                deviceInputPort = midiDevice.openInputPort(0)
                Log.d(TAG, "MIDI input port opened for RGB control")
            } else {
                Log.w(TAG, "Device has no input ports for RGB control")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup RGB control: ${e.message}", e)
        }
    }
    
    /**
     * Highlight a key with blue color (suggested note)
     */
    fun highlightKeyBlue(midiNote: Int) {
        setKeyColor(midiNote, 0, 0, COLOR_BLUE, 100)
        Log.d(TAG, "Highlighting key $midiNote with blue")
    }
    
    /**
     * Highlight a key with green color (correct note)
     */
    fun highlightKeyGreen(midiNote: Int) {
        setKeyColor(midiNote, 0, COLOR_GREEN, 0, 100)
        Log.d(TAG, "Highlighting key $midiNote with green")
    }
    
    /**
     * Highlight a key with red color (incorrect note)
     */
    fun highlightKeyRed(midiNote: Int) {
        setKeyColor(midiNote, COLOR_RED, 0, 0, 100)
        Log.d(TAG, "Highlighting key $midiNote with red")
    }
    
    /**
     * Turn off a key's RGB light
     */
    fun turnOffKey(midiNote: Int) {
        setKeyColor(midiNote, COLOR_OFF, COLOR_OFF, COLOR_OFF, 0)
        Log.d(TAG, "Turning off key $midiNote")
    }
    
    /**
     * Set RGB color for a specific key
     * Uses MIDI CC messages to control RGB values
     * Note: MidiInputPort extends MidiReceiver and receives data
     * To send TO it, we use its onSend method indirectly through a connected receiver
     * Actually, we need to send messages through a receiver connected to the input port
     */
    private fun setKeyColor(midiNote: Int, red: Int, green: Int, blue: Int, brightness: Int) {
        try {
            deviceInputPort?.let { port ->
                // Create a sender receiver that will send messages to the device
                // MidiInputPort is a receiver, so we connect a sender to it
                val sender = object : MidiReceiver() {
                    override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
                        // Send the message to the device's input port
                        port.onSend(msg, offset, count, timestamp)
                    }
                }
                
                // Method 1: Send Note On with velocity representing color/brightness
                // Some RGB pianos use Note On velocity for color intensity
                val noteOnMessage = byteArrayOf(
                    (0x90 or 0).toByte(), // Note On, channel 0
                    midiNote.toByte(),
                    brightness.coerceIn(0, 127).toByte() // Velocity/brightness
                )
                sender.onSend(noteOnMessage, 0, noteOnMessage.size, System.nanoTime())
                
                // Method 2: Send Control Change messages for RGB
                // Send red component
                val ccRed = byteArrayOf(
                    (0xB0 or 0).toByte(), // CC, channel 0
                    CC_RED.toByte(),
                    red.coerceIn(0, 127).toByte()
                )
                sender.onSend(ccRed, 0, ccRed.size, System.nanoTime())
                
                // Send green component
                val ccGreen = byteArrayOf(
                    (0xB0 or 0).toByte(),
                    CC_GREEN.toByte(),
                    green.coerceIn(0, 127).toByte()
                )
                sender.onSend(ccGreen, 0, ccGreen.size, System.nanoTime())
                
                // Send blue component
                val ccBlue = byteArrayOf(
                    (0xB0 or 0).toByte(),
                    CC_BLUE.toByte(),
                    blue.coerceIn(0, 127).toByte()
                )
                sender.onSend(ccBlue, 0, ccBlue.size, System.nanoTime())
                
                // Send brightness
                val ccBrightness = byteArrayOf(
                    (0xB0 or 0).toByte(),
                    CC_BRIGHTNESS.toByte(),
                    brightness.coerceIn(0, 127).toByte()
                )
                sender.onSend(ccBrightness, 0, ccBrightness.size, System.nanoTime())
                
            } ?: run {
                Log.w(TAG, "Device input port not available for RGB control")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending RGB control message: ${e.message}", e)
        }
    }
    
    /**
     * Turn off all RGB lights
     */
    fun turnOffAll() {
        // Turn off all keys from C4 to C6 (60-84)
        for (note in 60..84) {
            turnOffKey(note)
        }
        Log.d(TAG, "Turned off all RGB lights")
    }
    
    /**
     * Release resources
     */
    fun release() {
        turnOffAll()
        deviceInputPort?.close()
        deviceInputPort = null
        Log.d(TAG, "MidiRgbController released")
    }
}

