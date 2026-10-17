package com.example.blackslate

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@Composable fun HomeCanvas(
    snapshot: FinanceSnapshot, amount: String, note: String, busy: Boolean,
    onAmount: (String) -> Unit, onNote: (String) -> Unit, onCategory: (Category) -> Unit,
    onChoose: () -> Unit, onSettings: () -> Unit, onNewCategory: () -> Unit
) {
    val feedback = LocalFeedback.current
    val currentChoose by rememberUpdatedState(onCategory)
    val currentAmount by rememberUpdatedState(amount)
    val currentBusy by rememberUpdatedState(busy)
    var origin by remember { mutableStateOf(Offset.Zero) }
    var amountRect by remember { mutableStateOf(Rect.Zero) }
    val targets = remember { mutableStateMapOf<String, Rect>() }
    var dragging by remember { mutableStateOf(false) }
    var point by remember { mutableStateOf(Offset.Zero) }
    var hovered by remember { mutableStateOf<String?>(null) }
    var listRect by remember { mutableStateOf(Rect.Zero) }
    val listState = rememberLazyListState()
    val categories by rememberUpdatedState(snapshot.categories)
    LaunchedEffect(dragging) {
        while (dragging) {
            if (listRect != Rect.Zero) {
                val delta = when {
                    point.y > listRect.bottom - 50 -> 12f
                    point.y < listRect.top + 50 -> -12f
                    else -> 0f
                }
                if (delta != 0f) listState.scrollBy(delta)
                val next = targets.entries.firstOrNull { it.value.contains(point) && listRect.contains(point) }?.key
                if (next != hovered && next != null) feedback()
                hovered = next
            }
            delay(16)
        }
    }
    Box(Modifier.fillMaxSize().testTag("homeCanvas").onGloballyPositioned { origin = it.boundsInRoot().topLeft }.pointerInput(Unit) {
        detectDragGesturesAfterLongPress(
            onDragStart = { start ->
                if (!currentBusy && parseMoney(currentAmount) != null && amountRect.contains(start + origin)) {
                    dragging = true; point = start + origin; feedback()
                }
            },
            onDrag = { change, delta ->
                if (dragging) {
                    change.consume()
                    point += delta
                    val next = targets.entries.firstOrNull { it.value.contains(point) }?.key
                    if (next != hovered && next != null) feedback()
                    hovered = next
                }
            },
            onDragEnd = {
                if (dragging) {
                    val selected = categories.firstOrNull { it.id == hovered }
                    dragging = false; hovered = null; targets.clear()
                    selected?.let { feedback(); currentChoose(it) }
                }
            },
            onDragCancel = { dragging = false; hovered = null; targets.clear() }
        )
    }) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("◈  Black Slate", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
                Action("Settings", secondary = true, onClick = onSettings)
            }
            Box(Modifier.fillMaxWidth().height(155.dp).onGloballyPositioned { amountRect = it.boundsInRoot() }
                .testTag("amountDrag").alpha(if (dragging) 0.18f else 1f), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(snapshot.settings.currency.symbol, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 18.dp, end = 6.dp))
                    Text(amount.ifBlank { "0" }, style = MaterialTheme.typography.displayLarge.copy(fontSize = if (amount.length > 7) 48.sp else 72.sp),
                        maxLines = 1, color = if (amount.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground)
                }
            }
            if (dragging) {
                Caption("Slide onto a category · release to log", Modifier.align(Alignment.CenterHorizontally))
                LazyColumn(Modifier.weight(1f).testTag("dropCategories").onGloballyPositioned { listRect = it.boundsInRoot() }, state = listState, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(snapshot.categories, key = { _, c -> c.id }) { index, category ->
                        DisposableEffect(category.id) { onDispose { targets.remove(category.id) } }
                        val selected = hovered == category.id
                        val total = snapshot.transactions.filter { it.categoryId == category.id && it.currency == snapshot.settings.currency }.sumOf { if (category.type == CategoryType.EXPENSE) it.cents else it.outstanding }
                        Surface(shape = RoundedCornerShape(26.dp), shadowElevation = if (selected) 12.dp else 3.dp,
                            color = if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = .3f)),
                            modifier = Modifier.fillMaxWidth().testTag("drop_"+category.id).onGloballyPositioned { targets[category.id] = it.boundsInRoot() }) {
                            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.width(3.dp).height(48.dp).background(accentColor(category.accent), RoundedCornerShape(3.dp)))
                                Text("%02d".format(index + 1), Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Column(Modifier.weight(1f)) {
                                    Text(category.name, style = MaterialTheme.typography.headlineMedium)
                                    Caption((if (category.type == CategoryType.EXPENSE) "Spent · " else "Outstanding · ") + money(total, snapshot.settings.currency))
                                }
                                if (selected) Text("Release ↓", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    item { Action("+ New category", Modifier.fillMaxWidth(), secondary = true, onClick = onNewCategory) }
                }
            } else {
                OutlinedTextField(note, onNote, label = { Text("Note (optional)") }, singleLine = true,
                    shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().testTag("homeNote"))
                Caption(if (busy) "Saving with current location…" else "Hold & drag the amount to log it", Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.weight(1f))
                listOf(listOf("1","2","3"), listOf("4","5","6"), listOf("7","8","9"), listOf(".","0","⌫")).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { key ->
                            Surface(shape = RoundedCornerShape(20.dp), color = if (key == "⌫") MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                                shadowElevation = 3.dp, modifier = Modifier.weight(1f).height(54.dp).testTag("key_$key")
                                    .clickable(enabled = !busy) {
                                        feedback()
                                        val next = when (key) {
                                            "⌫" -> amount.dropLast(1)
                                            "." -> if (amount.contains('.')) amount else amount.ifBlank { "0" } + "."
                                            else -> if (amount == "0") key else amount + key
                                        }
                                        if (next.length <= 11 && (next.substringAfter('.', "").length <= 2) && next.substringBefore('.').length <= 8) onAmount(next)
                                    }) {
                                Box(contentAlignment = Alignment.Center) { Text(key, style = MaterialTheme.typography.headlineMedium) }
                            }
                        }
                    }
                }
                Action(if (busy) "Saving…" else "Choose category", Modifier.fillMaxWidth().testTag("chooseCategory"),
                    enabled = !busy, secondary = true, onClick = onChoose)
            }
        }
        if (dragging) {
            Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface,
                shadowElevation = 18.dp, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.offset { IntOffset((point.x - origin.x - 100.dp.toPx()).roundToInt(), (point.y - origin.y - 115.dp.toPx()).roundToInt()) }.width(200.dp)) {
                Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("AMOUNT", style = MaterialTheme.typography.labelSmall)
                    Text(snapshot.settings.currency.symbol + amount, style = MaterialTheme.typography.displayMedium, maxLines = 1)
                }
            }
        }
    }
}
