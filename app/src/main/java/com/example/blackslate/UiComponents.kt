package com.example.blackslate

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
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
@Composable fun Sheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), tonalElevation = 6.dp) {
            Column(Modifier.fillMaxWidth().heightIn(max = 660.dp).imePadding().verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, style = MaterialTheme.typography.headlineMedium)
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
data class Notice(val id: Long = System.nanoTime(), val text: String, val transactionId: String? = null)

@Composable fun NoticeBar(notice: Notice?, motion: Boolean, onDismiss: () -> Unit, onEdit: (String) -> Unit, onUndo: (String) -> Unit, modifier: Modifier = Modifier) {
    val feedback = LocalFeedback.current
    var displayed by remember { mutableStateOf<Notice?>(null) }
    var visible by remember { mutableStateOf(false) }
    var swipe by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(notice?.id) {
        if (notice != null) {
            displayed = notice
            swipe = 0f
            visible = true
            delay(3000)
            visible = false
            onDismiss()
        } else visible = false
    }
    AnimatedVisibility(visible, modifier = modifier, enter = slideInVertically(tween(if (motion) 220 else 0)) { it } + fadeIn(tween(if (motion) 220 else 0)),
        exit = slideOutVertically(tween(if (motion) 180 else 0)) { it } + fadeOut(tween(if (motion) 180 else 0))) {
        val current = displayed
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = RoundedCornerShape(22.dp),
            shadowElevation = 12.dp,
            modifier = Modifier.testTag("snackbar").offset { IntOffset(swipe.toInt(), 0) }.pointerInput(current?.id) {
                detectHorizontalDragGestures(onHorizontalDrag = { change, amount -> change.consume(); swipe += amount },
                    onDragEnd = { if (kotlin.math.abs(swipe) > 80.dp.toPx()) { visible = false; onDismiss() } else swipe = 0f })
            }
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(current?.text.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { feedback(); visible = false; onDismiss() }, modifier = Modifier.testTag("closeSnackbar")) { Text("✕") }
                }
                current?.transactionId?.let { id ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick = { feedback(); onDismiss(); onEdit(id) }) { Text("Edit") }
                        TextButton(onClick = { feedback(); onDismiss(); onUndo(id) }) { Text("Undo") }
                    }
                }
            }
        }
    }
}
