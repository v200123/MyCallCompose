package com.lastcoffee.top.oppo_call_compose.feature.call

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Audio from the simulated call uses the call volume and communication output route. */
internal val callAudioAttributes: AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
    .build()

@Suppress("DEPRECATION")
internal class CallAudioRoute(context: Context) {
    private val manager = context.applicationContext.getSystemService(AudioManager::class.java)
    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private var previousMode = AudioManager.MODE_NORMAL
    private var previousSpeaker = false
    private var active = false
    private var onSpeakerChanged: ((Boolean) -> Unit)? = null
    private var deviceListener: AudioManager.OnCommunicationDeviceChangedListener? = null

    fun start(speakerEnabled: Boolean, onChanged: (Boolean) -> Unit) {
        if (active) return
        previousMode = manager.mode
        previousSpeaker = manager.isSpeakerphoneOn
        onSpeakerChanged = onChanged
        active = true
        manager.mode = AudioManager.MODE_IN_COMMUNICATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val listener = AudioManager.OnCommunicationDeviceChangedListener { reportRoute() }
            deviceListener = listener
            manager.addOnCommunicationDeviceChangedListener(mainExecutor, listener)
        }
        setSpeakerEnabled(speakerEnabled)
    }

    fun setSpeakerEnabled(enabled: Boolean): Boolean {
        if (!active) return false
        val succeeded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val devices = manager.availableCommunicationDevices
            if (enabled) {
                devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    ?.let(manager::setCommunicationDevice) ?: false
            } else {
                // Let Android pick a connected headset first. If it still chooses the
                // loudspeaker, explicitly request the built-in earpiece when present.
                manager.clearCommunicationDevice()
                val current = manager.communicationDevice
                if (current != null && current.type != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) true
                else devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
                    ?.let(manager::setCommunicationDevice) ?: false
            }
        } else {
            manager.isSpeakerphoneOn = enabled
            manager.isSpeakerphoneOn == enabled
        }
        reportRoute()
        return succeeded
    }

    fun stop() {
        if (!active) return
        active = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            deviceListener?.let(manager::removeOnCommunicationDeviceChangedListener)
            deviceListener = null
            manager.clearCommunicationDevice()
        } else {
            manager.isSpeakerphoneOn = previousSpeaker
        }
        if (manager.mode == AudioManager.MODE_IN_COMMUNICATION) manager.mode = previousMode
        onSpeakerChanged = null
    }

    private fun reportRoute() {
        if (!active) return
        val speaker = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            manager.communicationDevice?.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        } else {
            manager.isSpeakerphoneOn
        }
        onSpeakerChanged?.invoke(speaker)
    }
}
