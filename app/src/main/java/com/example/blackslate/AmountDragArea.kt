package com.example.blackslate

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.example.blackslate.ui.theme.BodyFont
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable fun HomeCanvas(
    snapshot: FinanceSnapshot, amount: String, note: String, photoCount: Int, busy: Boolean,
    onAmount: (String) -> Unit, onNote: (String) -> Unit, onCategory: (Category) -> Unit,
    onChoose: () -> Unit, onSettings: () -> Unit, onNewCategory: () -> Unit,
    onCamera: () -> Unit, onGallery: () -> Unit, onClearPhotos: () -> Unit,
    onDragging: (Boolean) -> Unit, onDiscard: () -> Unit,
    monthlySpending: Long, monthlyBudget: Long?, logOnly: Boolean = false
) {
    val feedback = LocalFeedback.current
    val density = LocalDensity.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val imeVisible = WindowInsets.isImeVisible
    var noteFocused by remember { mutableStateOf(false) }
    var noteImeSeen by remember { mutableStateOf(false) }
    fun finishNote() { keyboard?.hide(); focus.clearFocus(); noteFocused = false; noteImeSeen = false }
    LaunchedEffect(noteFocused, imeVisible) {
        if (noteFocused && imeVisible) noteImeSeen = true
        else if (noteFocused && noteImeSeen) finishNote()
    }
    BackHandler(noteFocused && !imeVisible) { finishNote() }
    val chooseCategory by rememberUpdatedState(onCategory)
    val createCategory by rememberUpdatedState(onNewCategory)
    val discardDraft by rememberUpdatedState(onDiscard)
    val reportDragging by rememberUpdatedState(onDragging)
    val currentAmount by rememberUpdatedState(amount)
    val currentBusy by rememberUpdatedState(busy)
    val categories by rememberUpdatedState(snapshot.categories)
    var origin by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var amountRect by remember { mutableStateOf(Rect.Zero) }
    var listRect by remember { mutableStateOf(Rect.Zero) }
    val targets = remember { mutableStateMapOf<String, Rect>() }
    val listState = rememberLazyListState()
    var dragging by remember { mutableStateOf(false) }
    var point by remember { mutableStateOf(Offset.Zero) }
    var hovered by remember { mutableStateOf<String?>(null) }
    var photoMenu by remember { mutableStateOf(false) }
    var hintedForDraft by rememberSaveable { mutableStateOf(false) }
    val amountNudge = remember { Animatable(0f) }
    LaunchedEffect(amount, busy, dragging, snapshot.settings.liquidPhysics) {
        amountNudge.snapTo(0f)
        if (amount.isBlank()) {
            hintedForDraft = false
        } else if (!hintedForDraft && !busy && !dragging && snapshot.settings.liquidPhysics && parseMoney(amount) != null) {
            delay(850)
            amountNudge.animateTo(-2.5f, tween(80))
            amountNudge.animateTo(2.5f, tween(105))
            amountNudge.animateTo(-1.2f, tween(85))
            amountNudge.animateTo(0f, tween(110))
            hintedForDraft = true
        }
    }
    LaunchedEffect(dragging, snapshot.settings.categoryListView) {
        while (dragging && snapshot.settings.categoryListView) {
            if (listRect != Rect.Zero) {
                val edge = with(density) { 48.dp.toPx() }
                val delta = when {
                    point.y > listRect.bottom - edge -> with(density) { 12.dp.toPx() }
                    point.y < listRect.top + edge -> with(density) { -12.dp.toPx() }
                    else -> 0f
                }
                if (delta != 0f) listState.scrollBy(delta)
                val next = targets.entries.firstOrNull { it.value.contains(point) }?.key
                if (next != hovered && next != null) feedback()
                hovered = next
            }
            delay(16)
        }
    }
    Box(Modifier.fillMaxSize().testTag("homeCanvas").onGloballyPositioned {
        origin = it.boundsInRoot().topLeft
        canvasSize = it.size
    }.pointerInput(Unit) {
        detectDragGestures(
            onDragStart = { start ->
                if (!currentBusy && parseMoney(currentAmount) != null && amountRect.contains(start + origin)) {
                    hintedForDraft = true
                    dragging = true
                    reportDragging(true)
                    point = start + origin
                    targets.clear()
                    feedback()
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
                    val selectedId = targets.entries.firstOrNull { it.value.contains(point) }?.key ?: hovered
                    dragging = false
                    reportDragging(false)
                    hovered = null
                    targets.clear()
                    if (selectedId == "__discard__") { feedback(); discardDraft() }
                    else if (selectedId == "__new__") createCategory()
                    else categories.firstOrNull { it.id == selectedId }?.let { feedback(); chooseCategory(it) }
                }
            },
            onDragCancel = { dragging = false; reportDragging(false); hovered = null; targets.clear() }
        )
    }) {
        if (dragging) {
            val dropItems = snapshot.categories.map { it.id to it.name } + ("__new__" to "+ New category")
            Column(Modifier.fillMaxSize().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth().height(86.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AmountTicket(snapshot.settings.currency.symbol + amount, Modifier.weight(.75f).fillMaxHeight())
                    DropTile("__discard__", "Discard", hovered == "__discard__", Modifier.weight(.25f).fillMaxHeight(), targets, compact = true,
                        destructive = true)
                }
                if (snapshot.settings.categoryListView) {
                    LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("dropCategories")
                        .onGloballyPositioned { listRect = it.boundsInRoot() }, state = listState,
                        contentPadding = PaddingValues(vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(dropItems, key = { it.first }) { (id, label) ->
                            DropTile(id, label, hovered == id, Modifier.fillMaxWidth().height(92.dp), targets)
                        }
                    }
                } else {
                    Box(Modifier.weight(1f).fillMaxWidth().testTag("dropCategories")) {
                        val count = dropItems.size
                        val columns = when { count <= 2 -> 1; count <= 4 -> 2; count <= 9 -> 3; else -> 4 }
                        val rows = (count + columns - 1) / columns
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            repeat(rows) { row ->
                                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    repeat(columns) { column ->
                                        val item = dropItems.getOrNull(row * columns + column)
                                        if (item == null) Spacer(Modifier.weight(1f))
                                        else DropTile(item.first, item.second, hovered == item.first,
                                            Modifier.weight(1f).fillMaxHeight(), targets, compact = count > 9)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        SlateGlyph(SlateIcon.BRAND, Modifier.size(22.dp), MaterialTheme.colorScheme.onBackground)
                        Spacer(Modifier.width(10.dp))
                        Text("Black Slate", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
                    }
                    if (!logOnly) Action("Settings", secondary = true, onClick = onSettings)
                }
                Spacer(Modifier.height(18.dp))
                Surface(shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth().height(146.dp).onGloballyPositioned { amountRect = it.boundsInRoot() }
                        .graphicsLayer { translationX = with(density) { amountNudge.value.dp.toPx() } }.testTag("amountDrag")) {
                    Box(contentAlignment = Alignment.Center) {
                        val compact = amount.length > 7
                        Row(verticalAlignment = Alignment.Top) {
                            // The symbol is set in Helvetica (NType 82 has no rupee) and seated against the digits' cap height,
                            // so it stays aligned when a long amount drops to the smaller size.
                            Text(snapshot.settings.currency.symbol, style = MaterialTheme.typography.headlineMedium.copy(fontFamily = BodyFont),
                                modifier = Modifier.padding(top = if (compact) 6.dp else 9.dp, end = 6.dp))
                            Text(amount.ifBlank { "0" }, style = MaterialTheme.typography.displayLarge.copy(
                                fontSize = if (compact) 48.sp else 72.sp, lineHeight = if (compact) 54.sp else 76.sp),
                                maxLines = 1, color = if (amount.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    val colors = MaterialTheme.colorScheme
                    val noteShape = RoundedCornerShape(12.dp)
                    Box(Modifier.width(210.dp).height(50.dp).border(1.dp, colors.outline.copy(alpha = .58f), noteShape)
                        .testTag("homeNote")) {
                        BasicTextField(note, onNote, singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                            cursorBrush = SolidColor(colors.onSurface),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { finishNote() }),
                            modifier = Modifier.fillMaxSize().onFocusChanged {
                                noteFocused = it.isFocused
                                if (!it.isFocused) noteImeSeen = false
                            }.semantics { contentDescription = "Add note" },
                            decorationBox = { innerTextField ->
                                Box(Modifier.fillMaxSize().padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                                    if (note.isEmpty()) Text("Add note", style = MaterialTheme.typography.bodyLarge,
                                        color = colors.onSurfaceVariant)
                                    innerTextField()
                                }
                            })
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(50.dp)) {
                        IconAction(SlateIcon.CAMERA, if (photoCount == 0) "Photos" else "Photos · $photoCount attached", { photoMenu = true },
                            Modifier.size(50.dp).border(1.dp, colors.outline.copy(alpha = .58f), noteShape)
                                .testTag("addPhotos"), showLabel = false, iconSize = 25.dp)
                        if (photoCount > 0) Surface(shape = RoundedCornerShape(10.dp), color = colors.secondary,
                            contentColor = colors.onSecondary,
                            modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp).height(20.dp).widthIn(min = 20.dp)
                                .testTag("photoCountBadge")) {
                            Box(Modifier.padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                                Text(if (photoCount > 99) "99+" else photoCount.toString(), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        SlateDropdownMenu(photoMenu, onDismiss = { photoMenu = false }) {
                            SlateDropdownItem("Take photo") { photoMenu = false; onCamera() }
                            SlateDropdownItem("Choose photos") { photoMenu = false; onGallery() }
                            if (photoCount > 0) SlateDropdownItem("Remove photos") { photoMenu = false; onClearPhotos() }
                        }
                    }
                }
                Caption(if (busy) "Saving…" else "Drag amount to a category", Modifier.align(Alignment.CenterHorizontally))
                monthlyBudget?.let { budget ->
                    BudgetProgress(monthlySpending, budget, snapshot.settings.currency, Modifier.fillMaxWidth())
                }
                Spacer(Modifier.weight(1f))
                if (!noteFocused) {
                listOf(listOf("1","2","3"), listOf("4","5","6"), listOf("7","8","9"), listOf(".","0","⌫")).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { key ->
                            Surface(shape = RoundedCornerShape(20.dp),
                                color = if (key == "⌫") MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                                contentColor = if (key == "⌫") MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                shadowElevation = 3.dp, modifier = Modifier.weight(1f).height(54.dp).testTag("key_$key")
                                    .semantics { if (key == "⌫") contentDescription = "Delete" }
                                    .clickable(enabled = !busy) {
                                        feedback()
                                        val next = when (key) {
                                            "⌫" -> amount.dropLast(1)
                                            "." -> if (amount.contains('.')) amount else amount.ifBlank { "0" } + "."
                                            else -> if (amount == "0") key else amount + key
                                        }
                                        if (next.length <= 11 && next.substringAfter('.', "").length <= 2 &&
                                            next.substringBefore('.').length <= 8) onAmount(next)
                                    }) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (key == "⌫") SlateGlyph(SlateIcon.BACKSPACE, Modifier.size(30.dp), MaterialTheme.colorScheme.onSurface)
                                    else Text(key, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }
                Action("Choose category", Modifier.fillMaxWidth().testTag("chooseCategory"),
                    enabled = !busy, secondary = true, onClick = onChoose)
                }
            }
        }
    }
}

@Composable private fun DropTile(id: String, label: String, highlighted: Boolean,
    modifier: Modifier, targets: MutableMap<String, Rect>, compact: Boolean = false, destructive: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    if (destructive) {
        TicketDiscardTarget(id, highlighted, modifier, targets)
        return
    }
    Surface(shape = RoundedCornerShape(28.dp),
        color = if (highlighted) scheme.secondaryContainer else scheme.surfaceVariant,
        contentColor = if (highlighted) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
        border = BorderStroke(if (highlighted) 2.dp else 1.dp,
            if (highlighted) scheme.secondary else scheme.outline.copy(alpha = .38f)),
        shadowElevation = if (highlighted) 14.dp else 1.dp,
        modifier = modifier.testTag("drop_" + id).semantics { selected = highlighted }
            .onGloballyPositioned { targets[id] = it.boundsInRoot() }) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)) {
            Text(label, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleLarge,
                color = if (highlighted) scheme.onSecondaryContainer else scheme.onSurfaceVariant)
        }
    }
}

@Composable private fun AmountTicket(value: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(24.dp), color = scheme.surface, contentColor = scheme.onSurface,
        shadowElevation = 2.dp, border = BorderStroke(1.dp, scheme.outline), modifier = modifier) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.Center) {
            Caption("AMOUNT")
            Text(value.withRupeeFace(), style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable private fun TicketDiscardTarget(id: String, highlighted: Boolean, modifier: Modifier, targets: MutableMap<String, Rect>) {
    val scheme = MaterialTheme.colorScheme
    val lidAngle by animateFloatAsState(if (highlighted) -28f else 0f, tween(160), label = "discard-bin-lid")
    val shape = RoundedCornerShape(24.dp)
    // Composite the hover tint once so the icon never reveals a separate light backing layer.
    val background = if (highlighted) scheme.error.copy(alpha = .24f).compositeOver(scheme.surfaceVariant) else scheme.surfaceVariant
    Box(
        modifier = modifier.testTag("drop_" + id).semantics { selected = highlighted }
            .onGloballyPositioned { targets[id] = it.boundsInRoot() }
            .shadow(if (highlighted) 6.dp else 1.dp, shape)
            .clip(shape).background(background)
            .border(if (highlighted) 2.dp else 1.dp, if (highlighted) scheme.error else scheme.outline.copy(alpha = .38f), shape)
    ) {
        Canvas(Modifier.fillMaxSize()) {
                val ink = if (highlighted) scheme.error else scheme.onSurfaceVariant
                val w = 30.dp.toPx()
                val h = w
                val left = (size.width - w) / 2f
                val top = (size.height - h) / 2f
                val stroke = w * .08f
                drawRoundRect(ink, topLeft = Offset(left + w * .27f, top + h * .35f), size = Size(w * .46f, h * .49f),
                    cornerRadius = CornerRadius(w * .04f), style = Stroke(stroke))
                rotate(lidAngle, pivot = Offset(left + w * .18f, top + h * .29f)) {
                    drawLine(ink, Offset(left + w * .14f, top + h * .29f), Offset(left + w * .86f, top + h * .29f), stroke)
                    drawLine(ink, Offset(left + w * .41f, top + h * .16f), Offset(left + w * .59f, top + h * .16f), stroke)
                }
            }
    }
}

@Composable private fun BudgetProgress(spent: Long, budget: Long, currency: Currency, modifier: Modifier = Modifier) {
    if (budget <= 0L) return
    val over = (spent - budget).coerceAtLeast(0L)
    val remaining = (budget - spent).coerceAtLeast(0L)
    val color = if (over > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Caption(if (over > 0) money(over, currency) + " over this month's budget" else money(spent, currency) + " / " + money(budget, currency) + " · " + money(remaining, currency) + " left")
        LinearProgressIndicator(progress = { (spent.toFloat() / budget).coerceIn(0f, 1f) }, color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth().height(5.dp))
    }
}
