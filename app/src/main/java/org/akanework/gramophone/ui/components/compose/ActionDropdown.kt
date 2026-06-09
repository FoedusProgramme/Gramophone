
package org.akanework.gramophone.ui.components.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ActionDropdown(
    actions: List<DropdownItem>,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
    extraContent: @Composable (() -> Unit)? = null,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clickable(
                onClick = {
                    menuExpanded = true
                }
            )
    ) {

        if (extraContent == null) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = null,
                modifier = modifier
                    .padding(4.dp),
            )
        } else {
            extraContent()
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            modifier = Modifier.widthIn(min = 172.dp)
        ) {
            actions.forEach { item ->
                Column {
                    if (item.secondaryDropdown != null) {
                        ActionDropdown(
                            actions = item.secondaryDropdown,
                            onDismiss = { menuExpanded = false },
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(16.dp))
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth()
                            ) {
//                                item.leadingIcon?.invoke()
                                Icon(Icons.Rounded.ChevronLeft, null)
                                Text(
                                    text = item.title,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Normal
                                )
                            }
                        }
                    } else {
                        DropdownMenuItem(
                            leadingIcon = item.leadingIcon,
                            text = {
                                Text(
                                    text = item.title,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Normal
                                )
                            },
                            onClick = {
                                item.action()
                                menuExpanded = false
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}

data class DropdownItem(
    val title: String,
    val leadingIcon: @Composable (() -> Unit)?,
    val action: () -> Unit,
    val secondaryDropdown: List<DropdownItem>? = null
)
