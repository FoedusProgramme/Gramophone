package org.akanework.gramophone

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.akanework.gramophone.logic.ui.BaseActivity
import org.akanework.gramophone.ui.GramophoneTheme
import org.koin.android.ext.android.inject
import uk.akane.libphonograph.reader.FlowReader

class Reflections : BaseActivity() {
    private val reader: FlowReader by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GramophoneTheme {
                var node by remember { mutableStateOf<ReflectionNode?>(null) }
                LaunchedEffect(Unit) {
                    node = withContext(Dispatchers.Default) {
                        reader.refresh()
                        ReflectionNode(reader, null, false)
                    }
                }
                // Back goes to the parent page before leaving the screen.
                BackHandler(enabled = node?.parent != null) { node = node?.parent }
                node?.let { current -> ReflectionList(current) { node = it } }
            }
        }
    }
}

@Composable
private fun ReflectionList(node: ReflectionNode, onOpen: (ReflectionNode) -> Unit) {
    val listState = remember(node) { LazyListState() }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
        contentPadding = WindowInsets.safeDrawing.asPaddingValues(),
    ) {
        itemsIndexed(node.entries) { _, entry ->
            // Styled like the platform's simple_list_item_1: a 48dp row of body text.
            Text(
                text = entry.label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = entry.open != null) { entry.open?.invoke()?.let(onOpen) }
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}
