package com.example.blackslate

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay

@Composable fun CategoryOrderList(categories: List<Category>, back: () -> Unit, add: () -> Unit,
    saveOrder: (List<String>) -> Unit, content: @Composable RowScope.(Category) -> Unit) {
    var ordered by remember(categories) { mutableStateOf(categories) }
    val listState = rememberLazyListState()
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val feedback = LocalFeedback.current
    val density = LocalDensity.current
    val currentOrder by rememberUpdatedState(ordered)
    val savedOrder by rememberUpdatedState(categories)
    val commit by rememberUpdatedState(saveOrder)
    fun moveAtPointer() {
        val id = draggingId ?: return
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull {
            it.key != id && ordered.any { category -> category.id == it.key } && dragY >= it.offset && dragY <= it.offset + it.size
        } ?: return
        val from = ordered.indexOfFirst { it.id == id }
        val to = ordered.indexOfFirst { it.id == target.key }
        if (listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }?.index != from + 3) return
        if (from >= 0 && to >= 0 && from != to) {
            ordered = ordered.toMutableList().apply { add(to, removeAt(from)) }
            feedback()
        }
    }
    val move by rememberUpdatedState(::moveAtPointer)
    LaunchedEffect(draggingId) {
        while (draggingId != null) {
            val edge = with(density) { 64.dp.toPx() }
            val speed = with(density) { 10.dp.toPx() }
            val info = listState.layoutInfo
            val delta = when {
                dragY < info.viewportStartOffset + edge -> -speed
                dragY > info.viewportEndOffset - edge -> speed
                else -> 0f
            }
            if (delta != 0f) { listState.scrollBy(delta); move() }
            delay(16)
        }
    }
    LazyColumn(Modifier.fillMaxSize().testTag("categoryOrderList"), state = listState,
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item(key = "__header__") { PageHeader("Categories", back) }
        item(key = "__add__") { Action("+ New category", Modifier.fillMaxWidth(), onClick = add) }
        item(key = "__hint__") { Caption("Drag the handle to reorder") }
        items(ordered, key = { it.id }) { category ->
            val dragged = draggingId == category.id
            val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == category.id }
            SlateCard(Modifier.zIndex(if (dragged) 1f else 0f).graphicsLayer {
                translationY = if (dragged && info != null) dragY - info.offset - info.size / 2f else 0f
                scaleX = if (dragged) 1.02f else 1f
                scaleY = if (dragged) 1.02f else 1f
            }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val ink = MaterialTheme.colorScheme.onSurfaceVariant
                    Canvas(Modifier.width(28.dp).height(48.dp).testTag("reorder_${category.id}").semantics {
                        contentDescription = "Reorder ${category.name}"
                        customActions = listOf(-1 to "Move up", 1 to "Move down").map { (step, label) ->
                            CustomAccessibilityAction(label) {
                                val from = currentOrder.indexOfFirst { it.id == category.id }
                                val to = from + step
                                if (to in currentOrder.indices) {
                                    ordered = currentOrder.toMutableList().apply { add(to, removeAt(from)) }
                                    commit(ordered.map { it.id }); true
                                } else false
                            }
                        }
                    }.pointerInput(category.id) {
                        detectDragGestures(onDragStart = {
                            listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == category.id }?.let {
                                draggingId = category.id; dragY = it.offset + it.size / 2f; feedback()
                            }
                        }, onDrag = { change, delta ->
                            change.consume(); dragY += delta.y; move()
                        }, onDragEnd = { draggingId = null; commit(currentOrder.map { it.id }) },
                            onDragCancel = { draggingId = null; ordered = savedOrder })
                    }) {
                        repeat(3) { row -> repeat(2) { column ->
                            drawCircle(ink, radius = 1.6.dp.toPx(),
                                center = Offset(size.width * (.35f + column * .3f), size.height * (.35f + row * .15f)))
                        } }
                    }
                    content(category)
                }
            }
        }
    }
}
