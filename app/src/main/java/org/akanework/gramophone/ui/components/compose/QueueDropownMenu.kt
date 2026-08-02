package org.akanework.gramophone.ui.components.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import org.akanework.gramophone.BuildConfig
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.MultiQueueObject
import org.akanework.gramophone.ui.fragments.compose.MqState


@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun QueueDropdownMenu(
    mqState: MqState,
    mq: MultiQueueObject,
    isPinned: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val id = mq.id
    var showDialog by remember { mutableStateOf(false) }

    var textValue by remember { mutableStateOf(mq.title) }
    var error by remember { mutableStateOf(false) }

    if (showDialog) {
        LaunchedEffect(Unit) {
            snapshotFlow { textValue }.debounce { 300L }.collectLatest {
                if (textValue == mq.title) return@collectLatest
                if (!mqState.renameQueue(id, textValue, true)) {
                    error = true
                }
            }
        }

        AlertDialog(
            onDismissRequest = {
                showDialog = false
            },
            title = { Text(stringResource(R.string.rename)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = textValue,
                        onValueChange = {
                            textValue = it
                            error = false
                        },
                        isError = error,
                        supportingText = { if (error) Text(stringResource(R.string.spk_encoding_invalid)) },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (textValue == mq.title || mqState.renameQueue(id, textValue, false)) {
                            showDialog = false
                        } else {
                            error = true
                        }
                    },
                    enabled = !error
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                    },
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    ActionDropdown(
        enabled = enabled,
        actions = listOf(
            DropdownItem(
                title = stringResource(R.string.add_to_queue),
                leadingIcon = null,
                action = {
                    mqState.addToQueue(id)
                },
            ),
            DropdownItem(
                title = stringResource(R.string.play_next),
                leadingIcon = null,
                action = {
                    mqState.playNext(id)
                },
            ),
//            DropdownItem(
//                title = stringResource(R.string.add_to_playlist),
//                leadingIcon = null,
//                action = {
//                    mqState.addToPlaylist(index)
//                },
//            ),
            DropdownItem(
                title = stringResource(R.string.rename),
                leadingIcon = null,
                action = {
                    showDialog = true
                },
            ),
            DropdownItem(
                title = stringResource(
                    if (isPinned) R.string.mq_pin_queue else R.string.mq_unpin_queue
                ),
                leadingIcon = null,
                action = {
                    mqState.togglePin()
                },
            ),
            DropdownItem(
                title = stringResource(R.string.edit_mode),
                leadingIcon = null,
                action = {
                    mqState.isEditAllowed = !mqState.isEditAllowed
                },
            ),
        ) + if (BuildConfig.DEBUG) listOf(
            DropdownItem(
                title = "DEBUG: Age 2hrs",
                leadingIcon = null,
                action = {
                    mqState.age()
                },
            ),
        ) else emptyList(),
        modifier = modifier
    )
}