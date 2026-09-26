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
package org.akanework.gramophone.ui.components.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/*
 * The app's dialogs and snackbars, shown from anywhere through the host's state: an action
 * asks for one, the root composition draws it.
 */

sealed interface AppDialog {
    /** Some text and an OK button. */
    class Message(
        val title: String?,
        val message: String,
        val icon: ImageVector? = null,
    ) : AppDialog

    /** A yes or no question. [onConfirm] runs on yes. */
    class Confirm(
        val title: String,
        val message: String,
        val confirmText: String,
        val onConfirm: () -> Unit,
    ) : AppDialog

    /** A spinner that only its opener can take down, through [AppDialogHostState.dismissIf]. */
    class Progress(val title: String) : AppDialog

    /** One of several things to pick, by index. */
    class Choice(
        val title: String,
        val icon: ImageVector?,
        val items: List<String>,
        val onPick: (Int) -> Unit,
    ) : AppDialog

    /**
     * A line of text to enter. [validate] is asked on every change and answers with the
     * message to show under the field, or null when the text is fine. A blank text is never
     * confirmed.
     */
    class TextInput(
        val title: String,
        val initial: String,
        val hint: String,
        val validate: suspend (String) -> String?,
        val onConfirm: (String) -> Unit,
    ) : AppDialog
}

/** The dialog host of the screen. Outside the app root (previews) a host nobody draws. */
val LocalAppDialogs = staticCompositionLocalOf { AppDialogHostState() }

@Stable
class AppDialogHostState {
    var current by mutableStateOf<AppDialog?>(null)
        private set
    val snackbarHostState = SnackbarHostState()

    fun show(dialog: AppDialog) {
        current = dialog
    }

    fun dismiss() {
        current = null
    }

    /** Takes [dialog] down only while it is still the one showing. */
    fun dismissIf(dialog: AppDialog) {
        if (current === dialog) current = null
    }

    suspend fun snackbar(message: String, action: String? = null): SnackbarResult =
        snackbarHostState.showSnackbar(message, actionLabel = action, duration = SnackbarDuration.Long)
}

@Composable
fun AppDialogHost(state: AppDialogHostState) {
    when (val dialog = state.current) {
        null -> {}
        is AppDialog.Message -> AlertDialog(
            onDismissRequest = { state.dismiss() },
            icon = dialog.icon?.let { { Icon(it, contentDescription = null) } },
            title = dialog.title?.let { { Text(it) } },
            text = { Text(dialog.message) },
            confirmButton = {
                TextButton(onClick = { state.dismiss() }) { Text(stringResource(android.R.string.ok)) }
            },
        )
        is AppDialog.Confirm -> AlertDialog(
            onDismissRequest = { state.dismiss() },
            title = { Text(dialog.title) },
            text = { Text(dialog.message) },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.dismiss()
                        dialog.onConfirm()
                    },
                ) { Text(dialog.confirmText) }
            },
            dismissButton = {
                TextButton(onClick = { state.dismiss() }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
        is AppDialog.Progress -> AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            title = { Text(dialog.title) },
            text = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(Modifier.padding(vertical = 8.dp))
                }
            },
            confirmButton = {},
        )
        is AppDialog.Choice -> AlertDialog(
            onDismissRequest = { state.dismiss() },
            icon = dialog.icon?.let { { Icon(it, contentDescription = null) } },
            title = { Text(dialog.title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    dialog.items.forEachIndexed { index, item ->
                        TextButton(
                            onClick = {
                                state.dismiss()
                                dialog.onPick(index)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(item, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { state.dismiss() }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
        is AppDialog.TextInput -> TextInputDialog(dialog, onDismiss = { state.dismiss() })
    }
}

@Composable
private fun TextInputDialog(dialog: AppDialog.TextInput, onDismiss: () -> Unit) {
    var text by rememberSaveable(dialog) { mutableStateOf(dialog.initial) }
    var error by remember(dialog) { mutableStateOf<String?>(null) }
    var checked by remember(dialog) { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(dialog, text) {
        checked = false
        error = dialog.validate(text)
        checked = true
    }
    LaunchedEffect(dialog) { focusRequester.requestFocus() }
    val canConfirm = checked && error == null && text.isNotBlank()
    val confirm = {
        if (canConfirm) {
            onDismiss()
            dialog.onConfirm(text)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialog.title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(dialog.hint) },
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { confirm() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
        },
        confirmButton = {
            TextButton(onClick = confirm, enabled = canConfirm) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}
