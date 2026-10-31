package com.example.blackslate

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.example.blackslate.ui.theme.BodyFont
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay

val LocalFeedback = staticCompositionLocalOf<() -> Unit> { {} }
/** The app's only button. [icon] puts a drawn glyph before the label; [compact] trims the side padding so short labels fit in a row. */
@Composable fun Action(label: String, modifier: Modifier = Modifier, enabled: Boolean = true, secondary: Boolean = false,
    icon: SlateIcon? = null, compact: Boolean = false, onClick: () -> Unit) {
    val pulse = LocalFeedback.current
    val padding = if (compact) PaddingValues(horizontal = 12.dp, vertical = 8.dp) else ButtonDefaults.ContentPadding
    val content: @Composable RowScope.() -> Unit = {
        if (icon != null) { SlateGlyph(icon, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(label)
    }
    if (secondary) OutlinedButton(onClick = { pulse(); onClick() }, enabled = enabled, modifier = modifier, shape = RoundedCornerShape(18.dp),
        contentPadding = padding, content = content)
    else Button(onClick = { pulse(); onClick() }, enabled = enabled, modifier = modifier, shape = RoundedCornerShape(18.dp),
        contentPadding = padding, content = content)
}
/** An outlined button that holds only a drawn glyph, such as the month stepper's chevrons. */
@Composable fun ActionIcon(icon: SlateIcon, description: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val pulse = LocalFeedback.current
    OutlinedButton(onClick = { pulse(); onClick() }, enabled = enabled, shape = RoundedCornerShape(18.dp), contentPadding = PaddingValues(0.dp),
        modifier = modifier.defaultMinSize(minWidth = 52.dp).semantics { contentDescription = description }) {
        SlateGlyph(icon, Modifier.size(20.dp))
    }
}
/** NType 82 has no rupee glyph, so Android would draw the symbol in an unrelated system face. Set it in Helvetica instead. */
fun String.withRupeeFace(): AnnotatedString = buildAnnotatedString {
    for (ch in this@withRupeeFace) {
        if (ch == '₹') withStyle(SpanStyle(fontFamily = BodyFont)) { append(ch) } else append(ch)
    }
}
/** Material's Switch, themed from the scheme so the off state is no longer Material's lavender. */
@Composable fun SlateSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, enabled: Boolean = true) {
    val c = MaterialTheme.colorScheme
    Switch(checked, onCheckedChange, enabled = enabled, colors = SwitchDefaults.colors(
        checkedThumbColor = c.onPrimary, checkedTrackColor = c.primary, checkedBorderColor = c.primary,
        uncheckedThumbColor = c.outline, uncheckedTrackColor = c.surfaceVariant, uncheckedBorderColor = c.outline))
}
@Composable fun SlateDivider() = HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .5f))
/** The one text field for labelled input: Material's outlined field, with the app's 12px corners and surface fill. */
@Composable fun SlateTextField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    label: @Composable (() -> Unit)? = null, singleLine: Boolean = false, keyboardOptions: KeyboardOptions = KeyboardOptions.Default) {
    val c = MaterialTheme.colorScheme
    OutlinedTextField(value, onValueChange, modifier, enabled = enabled, label = label, singleLine = singleLine, keyboardOptions = keyboardOptions,
        shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = c.surface, unfocusedContainerColor = c.surface, disabledContainerColor = c.surface,
            focusedBorderColor = c.primary, unfocusedBorderColor = c.outline, disabledBorderColor = c.outline.copy(alpha = .5f),
            focusedLabelColor = c.onSurfaceVariant, unfocusedLabelColor = c.onSurfaceVariant, cursorColor = c.onSurface))
}
@Composable fun SlateCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val pulse = LocalFeedback.current
    Card(
        modifier = modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable { pulse(); onClick() } else Modifier),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
    ) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content) }
}
@Composable fun Caption(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
@Composable fun PageHeader(title: String, back: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Action("Back", secondary = true, icon = SlateIcon.CHEVRON_LEFT, onClick = back)
        Spacer(Modifier.width(16.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium)
    }
}
@Composable fun SlateDropdownMenu(expanded: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss,
        modifier = Modifier.padding(vertical = 4.dp),
        shape = RoundedCornerShape(18.dp), containerColor = colors.surface,
        border = BorderStroke(1.dp, colors.outline), shadowElevation = 10.dp,
        content = content)
}

@Composable fun SlateDropdownItem(label: String, selected: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    DropdownMenuItem(text = {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = if (selected) colors.secondary else colors.onSurface)
    }, onClick = onClick, trailingIcon = {
        if (selected) SlateGlyph(SlateIcon.SAVE, Modifier.size(18.dp), colors.secondary)
    }, modifier = Modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 18.dp))
}
@Composable fun Sheet(title: String, onDismiss: () -> Unit, showTitle: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), tonalElevation = 6.dp) {
            Column(Modifier.fillMaxWidth().heightIn(max = 660.dp).imePadding().verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (showTitle) Text(title.withRupeeFace(), style = MaterialTheme.typography.headlineMedium)
                content()
                Action("Close", Modifier.fillMaxWidth(), secondary = true, onClick = onDismiss)
            }
        }
    }
}
fun accentColor(accent: Accent): Color = when (accent) {
    Accent.RED -> Color(0xFFEF666B)
    Accent.TEAL -> Color(0xFF18AE87)
    Accent.GOLD -> Color(0xFFB68D50)
    Accent.SILVER -> Color(0xFF989DAA)
    Accent.BLUE -> Color(0xFF7F91BC)
}
enum class NoticeIcon { CHECK, TRASH }
data class Notice(
    val id: Long = System.nanoTime(),
    val text: String,
    val transactionId: String? = null,
    val icon: NoticeIcon = NoticeIcon.CHECK,
    val undoKey: String? = null,
    val amountText: String? = null,
    val targetText: String? = null
)

@Composable fun NoticeBar(notice: Notice?, motion: Boolean, onDismiss: () -> Unit, onEdit: (String) -> Unit, onUndo: (String) -> Unit, modifier: Modifier = Modifier) {
    val feedback = LocalFeedback.current
    val dark = MaterialTheme.colorScheme.background.red < .5f
    val cardTop = if (dark) Color(0xFF123229) else Color(0xFFE9F8F2)
    val cardBottom = if (dark) Color(0xFF0E2923) else Color(0xFFDDF2E8)
    val borderColor = if (dark) Color(0xFF245B49) else Color(0xFFA1DDC8)
    val accent = if (dark) Color(0xFF34D6A4) else Color(0xFF0A7D5F)
    val ringTrack = if (dark) Color(0xFF1D5A48) else Color(0xFFB9DDD0)
    val textColor = if (dark) Color(0xFFF1FBF6) else Color(0xFF102E27)
    val halo = Color(0xFF238D6F)
    val undoText = if (dark) Color(0xFF08291F) else Color.White
    val shape = RoundedCornerShape(22.dp)
    var displayed by remember { mutableStateOf<Notice?>(null) }
    var visible by remember { mutableStateOf(false) }
    var swipe by remember { mutableFloatStateOf(0f) }
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(notice?.id) {
        if (notice != null) {
            displayed = notice
            swipe = 0f
            visible = true
            progress = 1f
            if (notice.transactionId == null && notice.undoKey == null) {
                // Non-transaction notices stay lightweight; the timed progress treatment is
                // reserved for a successful transaction, where Undo/Edit are available.
                delay(3000)
            } else {
                val startedAt = android.os.SystemClock.uptimeMillis()
                while (true) {
                    delay(50)
                    val elapsed = android.os.SystemClock.uptimeMillis() - startedAt
                    progress = (1f - elapsed / 3000f).coerceIn(0f, 1f)
                    if (elapsed >= 3000L) break
                }
            }
            visible = false
            onDismiss()
        } else {
            visible = false
            progress = 0f
        }
    }

    AnimatedVisibility(visible, modifier = modifier, enter = slideInVertically(tween(if (motion) 220 else 0)) { it } + fadeIn(tween(if (motion) 220 else 0)),
        exit = slideOutVertically(tween(if (motion) 180 else 0)) { it } + fadeOut(tween(if (motion) 180 else 0))) {
        val current = displayed
        Box(
            modifier = Modifier.fillMaxWidth().offset { IntOffset(swipe.toInt(), 0) }
                .dropShadow(shape, Shadow(radius = 18.dp, color = halo.copy(alpha = if (dark) .24f else .26f),
                    offset = DpOffset(0.dp, 10.dp)))
                .dropShadow(shape, Shadow(radius = 3.dp, color = accent.copy(alpha = .12f), offset = DpOffset(0.dp, 2.dp)))
                .pointerInput(current?.id) {
                    detectHorizontalDragGestures(onHorizontalDrag = { change, amount -> change.consume(); swipe += amount },
                        onDragEnd = { if (kotlin.math.abs(swipe) > 80.dp.toPx()) { visible = false; onDismiss() } else swipe = 0f })
                }
                .testTag("snackbar")
                .semantics { liveRegion = LiveRegionMode.Polite }
        ) {
            Box(Modifier.fillMaxWidth().clip(shape)
                .background(Brush.verticalGradient(listOf(cardTop, cardBottom)))
                .border(1.dp, borderColor, shape)) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 70.dp).padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val dismissIcon = Modifier.size(44.dp).testTag("closeSnackbar")
                            .semantics { contentDescription = "Dismiss notification" }
                            .clickable { feedback(); visible = false; onDismiss() }
                        SnackbarStatusIcon(current?.icon ?: NoticeIcon.CHECK, accent, ringTrack, dismissIcon)
                        val amount = current?.amountText
                        val target = current?.targetText
                        val message = buildAnnotatedString {
                            if (amount != null && target != null) {
                                withStyle(MaterialTheme.typography.titleLarge.copy(fontSize = 23.sp).toSpanStyle()) { append(amount.withRupeeFace()) }
                                append("  ")
                                withStyle(MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp).toSpanStyle()) { append(target) }
                            } else append(current?.text.orEmpty())
                        }
                        Text(message, Modifier.weight(1f), color = textColor,
                            style = if (current?.icon == NoticeIcon.TRASH) MaterialTheme.typography.titleLarge.copy(fontSize = 23.sp, lineHeight = 28.sp)
                                else MaterialTheme.typography.bodyMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val undoId = current?.undoKey ?: current?.transactionId
                        current?.transactionId?.let { id ->
                            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                Surface(shape = CircleShape, color = if (dark) Color(0xFF1B3830) else Color(0xFFF2FBF7),
                                    border = BorderStroke(1.dp, if (dark) Color(0xFF2C6A56) else Color(0xFFA2DCC9)), modifier = Modifier.size(38.dp)) {}
                                IconAction(SlateIcon.EDIT, "Edit note", { visible = false; onDismiss(); onEdit(id) },
                                    modifier = Modifier.fillMaxSize(), showLabel = false, iconSize = 17.dp,
                                    tint = if (dark) Color(0xFFB3E5D5) else Color(0xFF17664F))
                            }
                        }
                        undoId?.let { id ->
                            Box(Modifier.width(72.dp).height(48.dp).clip(CircleShape)
                                .clickable { feedback(); visible = false; onDismiss(); onUndo(id) }, contentAlignment = Alignment.Center) {
                                Box(Modifier.fillMaxWidth().height(38.dp).background(accent, CircleShape), contentAlignment = Alignment.Center) {
                                    Text("Undo", color = undoText, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold))
                                }
                            }
                        }
                    }
                if (undoIdFor(current) != null) {
                    Canvas(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp).padding(horizontal = 20.dp).testTag("snackbarProgress")) {
                        drawLine(accent.copy(alpha = .12f), androidx.compose.ui.geometry.Offset.Zero,
                            androidx.compose.ui.geometry.Offset(size.width, 0f), 2.dp.toPx(), cap = StrokeCap.Round)
                        if (progress > 0f) drawLine(accent, androidx.compose.ui.geometry.Offset.Zero,
                            androidx.compose.ui.geometry.Offset(size.width * progress.coerceIn(0f, 1f), 0f), 2.dp.toPx(), cap = StrokeCap.Round)
                    }
                }
            }
        }
    }
}

private fun undoIdFor(notice: Notice?): String? = notice?.undoKey ?: notice?.transactionId

@Composable private fun SnackbarStatusIcon(icon: NoticeIcon, color: Color, track: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.minDimension
        val ringStroke = w * .058f
        val ringInset = w * .095f
        drawCircle(track, radius = w * .405f, style = Stroke(ringStroke))
        drawArc(color, startAngle = -90f, sweepAngle = 226f, useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(ringInset, ringInset),
            size = androidx.compose.ui.geometry.Size(w - 2 * ringInset, w - 2 * ringInset),
            style = Stroke(ringStroke, cap = StrokeCap.Round))
        if (icon == NoticeIcon.CHECK) {
            val stroke = w * .055f
            drawLine(color, androidx.compose.ui.geometry.Offset(w * .34f, w * .52f),
                androidx.compose.ui.geometry.Offset(w * .46f, w * .64f), stroke, cap = StrokeCap.Round)
            drawLine(color, androidx.compose.ui.geometry.Offset(w * .46f, w * .64f),
                androidx.compose.ui.geometry.Offset(w * .67f, w * .40f), stroke, cap = StrokeCap.Round)
        } else {
            val stroke = w * .034f
            val bin = Path().apply {
                moveTo(w * .39f, w * .40f); lineTo(w * .41f, w * .64f)
                lineTo(w * .59f, w * .64f); lineTo(w * .61f, w * .40f)
            }
            drawPath(bin, color, style = Stroke(stroke, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            drawLine(color, androidx.compose.ui.geometry.Offset(w * .36f, w * .37f),
                androidx.compose.ui.geometry.Offset(w * .64f, w * .37f), stroke, cap = StrokeCap.Round)
            drawRoundRect(color, topLeft = androidx.compose.ui.geometry.Offset(w * .455f, w * .315f),
                size = androidx.compose.ui.geometry.Size(w * .09f, w * .055f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .012f), style = Stroke(stroke))
            for (x in listOf(.47f, .53f)) drawLine(color, androidx.compose.ui.geometry.Offset(w * x, w * .435f),
                androidx.compose.ui.geometry.Offset(w * x, w * .575f), stroke, cap = StrokeCap.Round)
        }
    }
}

@Composable fun DisclosureGlyph(expanded: Boolean, modifier: Modifier = Modifier) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, tween(180), label = "group-disclosure")
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier.size(24.dp).graphicsLayer { rotationZ = rotation }) {
        val stroke = size.width * .09f
        drawLine(ink, androidx.compose.ui.geometry.Offset(size.width * .27f, size.height * .41f),
            androidx.compose.ui.geometry.Offset(size.width * .5f, size.height * .64f), stroke, cap = StrokeCap.Round)
        drawLine(ink, androidx.compose.ui.geometry.Offset(size.width * .5f, size.height * .64f),
            androidx.compose.ui.geometry.Offset(size.width * .73f, size.height * .41f), stroke, cap = StrokeCap.Round)
    }
}

enum class SlateIcon { CAMERA, LOCATION, TRASH, EDIT, SAVE, CHEVRON_LEFT, CHEVRON_RIGHT, ARROW_UP_RIGHT, BRAND, BACKSPACE }
@Composable fun IconAction(icon: SlateIcon, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, showLabel: Boolean = true,
    enabled: Boolean = true, iconSize: Dp = 20.dp, tint: Color = MaterialTheme.colorScheme.onSurface) {
    val feedback = LocalFeedback.current
    val ink = tint
    Row(modifier.semantics { contentDescription = label }.clickable(enabled = enabled) { feedback(); onClick() }.padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (showLabel) Arrangement.spacedBy(5.dp) else Arrangement.Center) {
        SlateGlyph(icon, Modifier.size(iconSize), ink)
        if (showLabel) Text(label, color = ink, style = MaterialTheme.typography.labelMedium)
    }
}
/** The app's drawn line icons: round caps and joins, a stroke of about 7 to 9 percent of the size. */
@Composable fun SlateGlyph(icon: SlateIcon, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    val ink = tint
    Canvas(modifier) {
            val w = size.width
            val h = size.height
            when (icon) {
                SlateIcon.CHEVRON_LEFT, SlateIcon.CHEVRON_RIGHT -> {
                    val flip = icon == SlateIcon.CHEVRON_RIGHT
                    fun x(v: Float) = w * (if (flip) 1f - v else v)
                    val chevron = Path().apply { moveTo(x(.62f), h * .22f); lineTo(x(.36f), h * .5f); lineTo(x(.62f), h * .78f) }
                    drawPath(chevron, ink, style = Stroke(w * .1f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                }
                SlateIcon.ARROW_UP_RIGHT -> {
                    drawLine(ink, androidx.compose.ui.geometry.Offset(w * .24f, h * .76f), androidx.compose.ui.geometry.Offset(w * .74f, h * .26f), w * .09f, cap = StrokeCap.Round)
                    val head = Path().apply { moveTo(w * .34f, h * .26f); lineTo(w * .74f, h * .26f); lineTo(w * .74f, h * .66f) }
                    drawPath(head, ink, style = Stroke(w * .09f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                }
                SlateIcon.BRAND -> {
                    val outer = Path().apply { moveTo(w * .5f, h * .08f); lineTo(w * .92f, h * .5f); lineTo(w * .5f, h * .92f); lineTo(w * .08f, h * .5f); close() }
                    drawPath(outer, ink, style = Stroke(w * .085f, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                    val inner = Path().apply { moveTo(w * .5f, h * .32f); lineTo(w * .68f, h * .5f); lineTo(w * .5f, h * .68f); lineTo(w * .32f, h * .5f); close() }
                    drawPath(inner, ink)
                }
                SlateIcon.BACKSPACE -> {
                    val tag = Path().apply { moveTo(w * .1f, h * .5f); lineTo(w * .32f, h * .22f); lineTo(w * .9f, h * .22f); lineTo(w * .9f, h * .78f); lineTo(w * .32f, h * .78f); close() }
                    drawPath(tag, ink, style = Stroke(w * .07f, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                    drawLine(ink, androidx.compose.ui.geometry.Offset(w * .5f, h * .4f), androidx.compose.ui.geometry.Offset(w * .7f, h * .6f), w * .07f, cap = StrokeCap.Round)
                    drawLine(ink, androidx.compose.ui.geometry.Offset(w * .7f, h * .4f), androidx.compose.ui.geometry.Offset(w * .5f, h * .6f), w * .07f, cap = StrokeCap.Round)
                }
                SlateIcon.CAMERA -> {
                    drawRoundRect(ink, topLeft = androidx.compose.ui.geometry.Offset(w * .08f, h * .28f),
                        size = androidx.compose.ui.geometry.Size(w * .84f, h * .61f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .09f), style = Stroke(w * .075f))
                    drawCircle(ink, radius = w * .17f, center = androidx.compose.ui.geometry.Offset(w * .5f, h * .59f), style = Stroke(w * .075f))
                    drawLine(ink, androidx.compose.ui.geometry.Offset(w * .26f, h * .29f), androidx.compose.ui.geometry.Offset(w * .36f, h * .16f), w * .08f)
                    drawLine(ink, androidx.compose.ui.geometry.Offset(w * .36f, h * .16f), androidx.compose.ui.geometry.Offset(w * .62f, h * .16f), w * .08f)
                }
                SlateIcon.LOCATION -> {
                    val pin = Path().apply {
                        moveTo(w * .5f, h * .92f)
                        cubicTo(w * .39f, h * .76f, w * .18f, h * .57f, w * .18f, h * .41f)
                        cubicTo(w * .18f, h * .22f, w * .32f, h * .09f, w * .5f, h * .09f)
                        cubicTo(w * .68f, h * .09f, w * .82f, h * .22f, w * .82f, h * .41f)
                        cubicTo(w * .82f, h * .57f, w * .61f, h * .76f, w * .5f, h * .92f)
                        close()
                    }
                    drawPath(pin, ink, style = Stroke(w * .075f, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                    drawCircle(ink, radius = w * .10f, center = androidx.compose.ui.geometry.Offset(w * .5f, h * .4f))
                }
                SlateIcon.TRASH -> {
                    drawRoundRect(ink, topLeft = androidx.compose.ui.geometry.Offset(w * .26f, h * .25f),
                        size = androidx.compose.ui.geometry.Size(w * .48f, h * .61f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .04f), style = Stroke(w * .075f))
                    drawLine(ink, androidx.compose.ui.geometry.Offset(w * .15f, h * .22f), androidx.compose.ui.geometry.Offset(w * .85f, h * .22f), w * .08f)
                    drawLine(ink, androidx.compose.ui.geometry.Offset(w * .4f, h * .1f), androidx.compose.ui.geometry.Offset(w * .6f, h * .1f), w * .08f)
                }
                SlateIcon.EDIT -> {
                    val pencil = Path().apply {
                        moveTo(w * .17f, h * .84f)
                        lineTo(w * .22f, h * .65f)
                        lineTo(w * .65f, h * .22f)
                        cubicTo(w * .69f, h * .18f, w * .74f, h * .18f, w * .78f, h * .22f)
                        lineTo(w * .83f, h * .27f)
                        cubicTo(w * .87f, h * .31f, w * .87f, h * .36f, w * .83f, h * .40f)
                        lineTo(w * .40f, h * .83f)
                        close()
                    }
                    drawPath(pencil, ink, style = Stroke(w * .07f, cap = StrokeCap.Round,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round))
                    drawLine(ink, androidx.compose.ui.geometry.Offset(w * .22f, h * .65f),
                        androidx.compose.ui.geometry.Offset(w * .40f, h * .83f), w * .07f)
                    drawLine(ink, androidx.compose.ui.geometry.Offset(w * .62f, h * .25f),
                        androidx.compose.ui.geometry.Offset(w * .80f, h * .43f), w * .07f)
                }
                SlateIcon.SAVE -> {
                    drawLine(ink, androidx.compose.ui.geometry.Offset(w * .22f, h * .52f), androidx.compose.ui.geometry.Offset(w * .43f, h * .74f), w * .11f, cap = StrokeCap.Round)
                    drawLine(ink, androidx.compose.ui.geometry.Offset(w * .43f, h * .74f), androidx.compose.ui.geometry.Offset(w * .8f, h * .27f), w * .11f, cap = StrokeCap.Round)
                }
            }
        }
}
