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
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay

val LocalFeedback = staticCompositionLocalOf<() -> Unit> { {} }
@Composable fun Action(label: String, modifier: Modifier = Modifier, enabled: Boolean = true, secondary: Boolean = false, onClick: () -> Unit) {
    val pulse = LocalFeedback.current
    if (secondary) OutlinedButton(onClick = { pulse(); onClick() }, enabled = enabled, modifier = modifier, shape = RoundedCornerShape(18.dp)) { Text(label) }
    else Button(onClick = { pulse(); onClick() }, enabled = enabled, modifier = modifier, shape = RoundedCornerShape(18.dp)) { Text(label) }
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
        Action("‹ Back", secondary = true, onClick = back)
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
        if (selected) Text("✓", color = colors.secondary, style = MaterialTheme.typography.labelLarge)
    }, modifier = Modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 18.dp))
}
@Composable fun Sheet(title: String, onDismiss: () -> Unit, showTitle: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), tonalElevation = 6.dp) {
            Column(Modifier.fillMaxWidth().heightIn(max = 660.dp).imePadding().verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (showTitle) Text(title, style = MaterialTheme.typography.headlineMedium)
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
                                withStyle(MaterialTheme.typography.titleLarge.copy(fontSize = 23.sp).toSpanStyle()) { append(amount) }
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

enum class SlateIcon { CAMERA, LOCATION, TRASH, EDIT, SAVE }
@Composable fun IconAction(icon: SlateIcon, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, showLabel: Boolean = true,
    enabled: Boolean = true, iconSize: Dp = 20.dp, tint: Color = MaterialTheme.colorScheme.onSurface) {
    val feedback = LocalFeedback.current
    val ink = tint
    Row(modifier.semantics { contentDescription = label }.clickable(enabled = enabled) { feedback(); onClick() }.padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (showLabel) Arrangement.spacedBy(5.dp) else Arrangement.Center) {
        Canvas(Modifier.size(iconSize)) {
            val w = size.width
            val h = size.height
            when (icon) {
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
        if (showLabel) Text(label, color = ink, style = MaterialTheme.typography.labelMedium)
    }
}
