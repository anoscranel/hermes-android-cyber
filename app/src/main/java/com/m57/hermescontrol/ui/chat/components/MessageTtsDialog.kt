package com.m57.hermescontrol.ui.chat.components

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.m57.hermescontrol.R

/**
 * Device TextToSpeech owner for the per-message TTS popover (batch-8 F1).
 * Fully client-side: one lazily-created [TextToSpeech] engine (initialized on
 * first Play), speech rate + mute applied per utterance, and an explicit
 * [shutdown] the composing screen calls on dispose.
 *
 * Volume: the slider drives the device's media stream volume
 * ([AudioManager.STREAM_MUSIC]) — the simplest reliable mapping, since the
 * engine always renders onto the media stream; Mute is a per-utterance
 * param (KEY_PARAM_VOLUME = 0) so it can never leave the device silenced.
 */
class MessageTtsController(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var tts: TextToSpeech? = null
    private var engineReady = false

    var speaking by mutableStateOf(false)
        private set

    var muted by mutableStateOf(false)
        private set

    var rate by mutableFloatStateOf(1f)
        private set

    var volumeFraction by mutableFloatStateOf(currentVolumeFraction())
        private set

    /** Text queued while the (async-started) engine finishes initializing. */
    private var pendingAfterInit: String? = null

    private fun startUtterance(text: String) {
        val engine = tts ?: return
        engine.setSpeechRate(rate)
        val params =
            Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, if (muted) 0f else 1f)
            }
        speaking =
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, MESSAGE_UTTERANCE_ID) ==
            TextToSpeech.SUCCESS
    }

    private fun createEngine(): TextToSpeech =
        TextToSpeech(appContext) { status ->
            engineReady = status == TextToSpeech.SUCCESS
            if (engineReady) {
                val engine = tts
                engine?.setSpeechRate(rate)
                engine?.setOnUtteranceProgressListener(
                    object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) = Unit

                        override fun onDone(utteranceId: String?) {
                            speaking = false
                        }

                        override fun onError(utteranceId: String?) {
                            speaking = false
                        }
                    },
                )
                val queued = pendingAfterInit
                pendingAfterInit = null
                queued?.let { startUtterance(it) }
            }
        }

    /**
     * Speaks [text]. The engine is created lazily on this first call; if the
     * async init is still running, the text is queued and spoken the moment
     * the engine reports ready, so the first Play tap is never dropped.
     */
    fun play(text: String) {
        if (text.isBlank()) return
        val engine = tts
        if (engine == null) {
            pendingAfterInit = text
            tts = createEngine()
        } else if (engineReady) {
            startUtterance(text)
        } else {
            pendingAfterInit = text
        }
    }

    fun stop() {
        tts?.stop()
        speaking = false
    }

    fun toggleMute() {
        muted = !muted
    }

    fun updateRate(value: Float) {
        val clamped = value.coerceIn(MIN_SPEECH_RATE, MAX_SPEECH_RATE)
        rate = clamped
        tts?.setSpeechRate(clamped)
    }

    fun updateVolumeFraction(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = (clamped * max).toInt().coerceIn(0, max)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        volumeFraction = clamped
    }

    private fun currentVolumeFraction(): Float {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) return 0f
        return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
    }

    /** Release the engine; safe to call repeatedly. */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        engineReady = false
        speaking = false
    }

    private companion object {
        const val MESSAGE_UTTERANCE_ID = "chat-msg-tts"
        const val MIN_SPEECH_RATE = 0.5f
        const val MAX_SPEECH_RATE = 2.0f
    }
}

/**
 * Single shared [MessageTtsController] for the chat screen: created with the
 * screen, stopped with the screen lifecycle (ON_STOP), shut down when the
 * screen leaves composition (batch-8 F1 — the engine never outlives the chat).
 */
@Composable
fun rememberMessageTtsController(): MessageTtsController {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember(context) { MessageTtsController(context) }
    DisposableEffect(lifecycleOwner, controller) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) controller.stop()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.shutdown()
        }
    }
    return controller
}

/**
 * The per-message TTS popover (batch-8 F1): Play/Stop, mute toggle, speech-
 * rate slider (0.5×–2.0×) and a media-volume slider, all driving the shared
 * [MessageTtsController]. Dismissing stops playback.
 */
@Composable
fun MessageTtsDialog(
    text: String,
    controller: MessageTtsController,
    onDismiss: () -> Unit,
) {
    val dismiss = {
        controller.stop()
        onDismiss()
    }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.chat_msg_tts_title)) },
        text = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    IconButton(
                        onClick = {
                            if (controller.speaking) {
                                controller.stop()
                            } else {
                                controller.play(text)
                            }
                        },
                        modifier = Modifier.testTag("tts_play_stop"),
                    ) {
                        Icon(
                            imageVector =
                                if (controller.speaking) {
                                    Icons.Filled.Stop
                                } else {
                                    Icons.Filled.PlayArrow
                                },
                            contentDescription =
                                stringResource(
                                    if (controller.speaking) {
                                        R.string.chat_msg_tts_stop
                                    } else {
                                        R.string.chat_msg_tts_play
                                    },
                                ),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        text = stringResource(R.string.chat_msg_tts_speed, controller.rate),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { controller.toggleMute() },
                        modifier = Modifier.testTag("tts_mute"),
                    ) {
                        Icon(
                            imageVector =
                                if (controller.muted) {
                                    Icons.AutoMirrored.Filled.VolumeOff
                                } else {
                                    Icons.AutoMirrored.Filled.VolumeUp
                                },
                            contentDescription =
                                stringResource(
                                    if (controller.muted) {
                                        R.string.chat_msg_tts_unmute
                                    } else {
                                        R.string.chat_msg_tts_mute
                                    },
                                ),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Slider(
                    value = controller.rate,
                    onValueChange = { controller.updateRate(it) },
                    valueRange = 0.5f..2.0f,
                    modifier = Modifier.fillMaxWidth().testTag("tts_speed"),
                )
                Text(
                    text = stringResource(R.string.chat_msg_tts_volume),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = controller.volumeFraction,
                    onValueChange = { controller.updateVolumeFraction(it) },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth().testTag("tts_volume"),
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = dismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}
