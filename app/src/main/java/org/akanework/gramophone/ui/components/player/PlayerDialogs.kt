/*
 *     Copyright (C) 2025 Akane Foundation
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Gramophone is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.akanework.gramophone.ui.components.player

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.components.player.PlayerUtilities.SPEED_MAX
import org.akanework.gramophone.ui.components.player.PlayerUtilities.SPEED_MIN
import org.akanework.gramophone.ui.components.player.PlayerUtilities.SPEED_STEPS
import org.akanework.gramophone.ui.components.player.PlayerUtilities.TIMER_MINUTES
import java.text.NumberFormat
import java.text.ParseException
import java.util.Date

enum class PlayerDialog { Timer, Speed }

class PlayerDialogCallbacks(
    val currentSpeed: () -> Float,
    val currentPitch: () -> Float,
    val setSpeedPitch: (Float, Float) -> Unit,
    val timerRemainingMs: () -> Long?,
    val timerEndOfSong: () -> Boolean,
    val setTimer: (durationMs: Int, eos: Boolean) -> Unit,
    val getBool: (key: String, def: Boolean) -> Boolean,
    val putBool: (key: String, value: Boolean) -> Unit,
)

@Composable
fun PlayerDialogs(
    dialog: PlayerDialog?,
    scheme: ColorScheme,
    callbacks: PlayerDialogCallbacks,
    onDismiss: () -> Unit,
) {
    if (dialog == null) return
    MaterialTheme(
        colorScheme = scheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
    ) {
        when (dialog) {
            PlayerDialog.Speed -> SpeedDialog(callbacks, onDismiss)
            PlayerDialog.Timer -> TimerDialog(callbacks, onDismiss)
        }
    }
}



@Composable
private fun SpeedDialog(cb: PlayerDialogCallbacks, onDismiss: () -> Unit) {
    val initialSpeed = remember { cb.currentSpeed() }
    val initialPitch = remember { cb.currentPitch() }
    val wantsLocked = remember { cb.getBool("playback_tempo_pitch_locked", true) }
    var tempo by remember { mutableFloatStateOf(initialSpeed.coerceIn(SPEED_MIN, SPEED_MAX)) }
    var pitch by remember { mutableFloatStateOf(initialPitch.coerceIn(SPEED_MIN, SPEED_MAX)) }
    var locked by remember { mutableStateOf(initialPitch == initialSpeed && wantsLocked) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.playback_speed)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.tempo_pitch_value, stringResource(R.string.tempo), tempo),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Slider(
                    value = tempo,
                    onValueChange = {
                        tempo = it
                        if (locked) pitch = it
                        cb.setSpeedPitch(tempo, pitch)
                    },
                    valueRange = SPEED_MIN..SPEED_MAX,
                    steps = SPEED_STEPS,
                )
                Text(
                    stringResource(R.string.tempo_pitch_value, stringResource(R.string.pitch), pitch),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Slider(
                    value = pitch,
                    enabled = !locked,
                    onValueChange = { pitch = it; cb.setSpeedPitch(tempo, pitch) },
                    valueRange = SPEED_MIN..SPEED_MAX,
                    steps = SPEED_STEPS,
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = locked,
                            onValueChange = {
                                locked = it
                                if (it) {
                                    pitch = tempo
                                    cb.setSpeedPitch(tempo, pitch)
                                }
                            },
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = locked, onCheckedChange = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.lock_tempo_pitch))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                cb.putBool("playback_tempo_pitch_locked", true)
                cb.setSpeedPitch(1f, 1f)
                onDismiss()
            }) { Text(stringResource(R.string.reset)) }
        },
        confirmButton = {
            Row {
                TextButton(onClick = {
                    cb.setSpeedPitch(initialSpeed, initialPitch)
                    if (wantsLocked != (initialPitch == initialSpeed && wantsLocked))
                        cb.putBool("playback_tempo_pitch_locked", false)
                    onDismiss()
                }) { Text(stringResource(android.R.string.cancel)) }
                TextButton(onClick = {
                    cb.putBool("playback_tempo_pitch_locked", locked)
                    onDismiss()
                }) { Text(stringResource(android.R.string.ok)) }
            }
        },
    )
}

@Composable
private fun TimerDialog(cb: PlayerDialogCallbacks, onDismiss: () -> Unit) {
    val remaining = remember { cb.timerRemainingMs() }
    val eosNow = remember { cb.timerEndOfSong() }
    if (remaining != null || eosNow) {
        TimerActiveDialog(cb, remaining, eosNow, onDismiss)
    } else {
        TimerPickDialog(cb, onDismiss)
    }
}

@Composable
private fun TimerActiveDialog(
    cb: PlayerDialogCallbacks,
    remaining: Long?,
    eosNow: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var eos by remember { mutableStateOf(eosNow) }
    val expiry = if (remaining != null)
        stringResource(
            R.string.timer_expiry,
            DateFormat.getTimeFormat(context).format(Date(System.currentTimeMillis() + remaining)),
        )
    else stringResource(R.string.timer_expiry_end_of_this_song)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.timer)) },
        text = {
            Column {
                Text(expiry)
                if (remaining != null) {
                    Spacer(Modifier.width(0.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .toggleable(value = eos, onValueChange = {
                                eos = it
                                cb.setTimer((cb.timerRemainingMs() ?: 0L).toInt(), it)
                            })
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = eos, onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.timer_eos))
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { cb.setTimer(0, false); onDismiss() }) {
                Text(stringResource(R.string.unset))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) }
        },
    )
}

@Composable
private fun TimerPickDialog(cb: PlayerDialogCallbacks, onDismiss: () -> Unit) {
    var waitEos by remember { mutableStateOf(cb.getBool("lastTimerEos", false)) }
    var showCustom by remember { mutableStateOf(false) }

    if (showCustom) {
        TimerCustomDialog(cb, waitEos, onDismiss)
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.timer)) },
        text = {
            Column {
                Column(
                    Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    TIMER_MINUTES.forEachIndexed { _, min ->
                        val label = if (min > 0)
                            pluralStringResource(R.plurals.minutes, min, min)
                        else stringResource(R.string.timer_end_of_this_song)
                        TimerRow(label) {
                            val duration = min * 60 * 1000
                            cb.setTimer(duration, duration == 0 || waitEos)
                            onDismiss()
                        }
                    }
                    TimerRow(stringResource(R.string.other)) { showCustom = true }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(value = waitEos, onValueChange = { waitEos = it })
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = waitEos, onCheckedChange = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.timer_eos))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@Composable
private fun TimerRow(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = false, onValueChange = { onClick() })
            .padding(vertical = 14.dp),
    )
}

@Composable
private fun TimerCustomDialog(cb: PlayerDialogCallbacks, initialEos: Boolean, onDismiss: () -> Unit) {
    var minutes by remember { mutableStateOf("") }
    var eos by remember { mutableStateOf(initialEos) }
    val valid = remember(minutes) {
        try {
            NumberFormat.getInstance().parse(minutes)!!.toFloat(); true
        } catch (_: ParseException) {
            false
        } catch (_: NullPointerException) {
            false
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.timer)) },
        text = {
            Column {
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { minutes = it },
                    label = { Text(stringResource(R.string.timer_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(value = eos, onValueChange = { eos = it })
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = eos, onCheckedChange = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.timer_eos))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    try {
                        val m = NumberFormat.getInstance().parse(minutes)!!.toFloat()
                        cb.setTimer((m * 60f * 1000f).toInt(), eos)
                    } catch (_: ParseException) {
                    }
                    onDismiss()
                },
            ) { Text(stringResource(android.R.string.ok)) }
        },
    )
}
