package com.example.blackslate

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal

/*
 * Building blocks of the group-expense / lending sheet: a header with a live summary, one field to add people,
 * and one card that holds the split mode and everyone's share.
 */

@Composable fun ShareSheetHeader(title: String, summary: String, summaryIsError: Boolean, onClose: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val feedback = LocalFeedback.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title.withRupeeFace(), style = MaterialTheme.typography.headlineMedium)
            if (summary.isNotEmpty()) Text(summary, style = MaterialTheme.typography.bodyMedium, color = if (summaryIsError) colors.error else colors.onSurfaceVariant)
        }
        Box(Modifier.size(36.dp).clip(CircleShape).background(colors.surfaceVariant)
            .clickable(role = Role.Button) { feedback(); onClose() }.semantics { contentDescription = "Close" },
            contentAlignment = Alignment.Center) {
            SlateGlyph(SlateIcon.CLOSE, Modifier.size(18.dp), colors.onSurface)
        }
    }
}

/** One field for adding a person by name; Contacts sits inside it and turns into Add once something is typed. */
@Composable fun PersonField(name: String, change: (String) -> Unit, onAdd: () -> Unit, onContacts: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val hint = "Add by name"
    // Same size as the Contacts pill beside it (labelMedium, 13sp).
    val fieldText = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp, lineHeight = 20.sp)
    Surface(Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp), color = colors.surface,
        border = BorderStroke(1.dp, colors.outline)) {
        Row(Modifier.padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SlateGlyph(SlateIcon.PERSON_ADD, Modifier.size(20.dp), colors.onSurfaceVariant)
            BasicTextField(name, change, singleLine = true,
                textStyle = fieldText.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.onSurface), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onAdd() }),
                modifier = Modifier.weight(1f).testTag("personName").semantics { contentDescription = hint },
                decorationBox = { input ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (name.isEmpty()) Text(hint, color = colors.onSurfaceVariant, style = fieldText)
                        input()
                    }
                })
            if (name.isNotBlank()) FieldPill("Add", primary = true, onClick = onAdd)
            else FieldPill("Contacts", icon = SlateIcon.PERSON, onClick = onContacts)
        }
    }
}

@Composable private fun FieldPill(label: String, icon: SlateIcon? = null, primary: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val feedback = LocalFeedback.current
    Row(Modifier.height(32.dp).clip(RoundedCornerShape(8.dp)).background(if (primary) colors.primary else colors.surfaceVariant)
        .clickable(role = Role.Button) { feedback(); onClick() }.padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val ink = if (primary) colors.onPrimary else colors.onSurface
        if (icon != null) SlateGlyph(icon, Modifier.size(16.dp), ink)
        Text(label, style = MaterialTheme.typography.labelMedium, color = ink, maxLines = 1)
    }
}

/** A single row of chips that scrolls sideways, with a fade on the right edge while more is hidden. */
@Composable fun ScrollingChoices(choices: List<Pair<String, String>>, modifier: Modifier = Modifier, lead: String? = null,
    selected: String? = null, plus: Boolean = false, onClick: (String) -> Unit) {
    val scroll = rememberScrollState()
    val fade = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
    Row(modifier.fillMaxWidth()
        .drawWithContent {
            drawContent()
            if (scroll.canScrollForward) drawRect(Brush.horizontalGradient(listOf(Color.Transparent, fade), startX = size.width - 40.dp.toPx(), endX = size.width))
        }
        .horizontalScroll(scroll), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (lead != null) Caption(lead)
        choices.forEach { (id, label) ->
            val on = selected != null && (selected == id) || (selected == null && id.isEmpty() && !plus)
            CompactChoice(label, on, icon = if (plus) SlateIcon.PLUS else if (on) SlateIcon.SAVE else null) { onClick(id) }
        }
    }
}

/** The card that holds the split mode and everyone's share. */
@Composable fun SplitCard(content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(colors.surfaceVariant.copy(alpha = .4f))
        .border(1.dp, colors.outline.copy(alpha = .5f), shape).padding(12.dp), content = content)
}

@Composable fun SplitModeTabs(mode: SplitMode, change: (SplitMode) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val feedback = LocalFeedback.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surfaceVariant).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        SplitMode.entries.forEach { option ->
            val on = mode == option
            Box(Modifier.weight(1f).height(32.dp).clip(RoundedCornerShape(9.dp)).background(if (on) colors.primary else Color.Transparent)
                .clickable(role = Role.Tab) { feedback(); change(option) }.testTag("split_${option.name}"),
                contentAlignment = Alignment.Center) {
                Text(option.label, style = MaterialTheme.typography.labelMedium, maxLines = 1,
                    color = if (on) colors.onPrimary else colors.onSurfaceVariant)
            }
        }
    }
}

private fun initialsOf(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

/**
 * One person in the split. [isMe] rows toggle "include my share" when tapped; other rows carry a remove badge.
 * The control after the name follows the split mode: nothing for Equal, a stepper for Parts, an amount field for Amounts.
 */
@Composable fun ShareRow(id: String, name: String, isMe: Boolean, included: Boolean, mode: SplitMode, symbol: String,
    amount: String, value: String, changeValue: (String) -> Unit, caption: String? = null,
    onToggleMe: (Boolean) -> Unit = {}, onRemove: () -> Unit = {}) {
    val colors = MaterialTheme.colorScheme
    val feedback = LocalFeedback.current
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.weight(1f).then(if (isMe) Modifier.toggleable(included, role = Role.Checkbox) { feedback(); onToggleMe(it) } else Modifier)
            .alpha(if (included) 1f else .5f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(36.dp)) {
                Box(Modifier.fillMaxSize().clip(CircleShape).background(if (isMe) colors.primary else colors.surfaceVariant), contentAlignment = Alignment.Center) {
                    Text(if (isMe) "ME" else initialsOf(name), color = if (isMe) colors.onPrimary else colors.onSurface,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium))
                }
                if (isMe) {
                    if (included) Box(Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp).size(16.dp).clip(CircleShape).background(colors.secondary),
                        contentAlignment = Alignment.Center) { SlateGlyph(SlateIcon.SAVE, Modifier.size(10.dp), colors.onPrimary) }
                } else Box(Modifier.align(Alignment.TopEnd).offset(x = 10.dp, y = (-10).dp).size(28.dp)
                    .clickable(role = Role.Button) { feedback(); onRemove() }.semantics { contentDescription = "Remove $name" },
                    contentAlignment = Alignment.Center) {
                    Box(Modifier.size(16.dp).clip(CircleShape).background(colors.surface).border(1.dp, colors.outline, CircleShape),
                        contentAlignment = Alignment.Center) { SlateGlyph(SlateIcon.CLOSE, Modifier.size(9.dp), colors.onSurfaceVariant) }
                }
            }
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                // Parts mode has no room for an amount beside the stepper, so the amount sits under the name.
                val line = caption ?: if (included && mode == SplitMode.PARTS) amount else null
                if (line != null) Text(line.withRupeeFace(), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
        if (included) when (mode) {
            SplitMode.PARTS -> PartsStepper(id, value, changeValue)
            SplitMode.AMOUNTS -> AmountInput(id, symbol, value, changeValue)
            SplitMode.EQUAL -> Text(amount.withRupeeFace(), style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp), maxLines = 1)
        } else Text("—", color = colors.onSurfaceVariant, modifier = Modifier.alpha(.5f))
    }
}

@Composable private fun PartsStepper(id: String, value: String, change: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    fun bump(delta: Int) {
        val next = (value.toBigDecimalOrNull() ?: BigDecimal.ONE) + BigDecimal(delta)
        if (next.signum() > 0) change(next.stripTrailingZeros().toPlainString())
    }
    val shape = RoundedCornerShape(16.dp)
    Row(Modifier.clip(shape).background(colors.surface).border(1.dp, colors.outline, shape).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        StepButton(SlateIcon.MINUS, "Fewer parts") { bump(-1) }
        BasicTextField(value, change, singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium),
            cursorBrush = SolidColor(colors.onSurface), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.width(32.dp).testTag("splitValue_$id").semantics { contentDescription = "Parts" })
        StepButton(SlateIcon.PLUS, "More parts") { bump(1) }
    }
}

@Composable private fun StepButton(icon: SlateIcon, description: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val feedback = LocalFeedback.current
    Box(Modifier.size(24.dp).clip(CircleShape).background(colors.surfaceVariant).clickable(role = Role.Button) { feedback(); onClick() }
        .semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        SlateGlyph(icon, Modifier.size(12.dp), colors.onSurface)
    }
}

@Composable private fun AmountInput(id: String, symbol: String, value: String, change: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    val style = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface, fontWeight = FontWeight.Medium)
    Row(Modifier.width(104.dp).height(36.dp).clip(shape).background(colors.surface).border(1.dp, colors.outline, shape).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(symbol.withRupeeFace(), style = style.copy(color = colors.onSurfaceVariant))
        BasicTextField(value, change, singleLine = true, textStyle = style.copy(textAlign = TextAlign.End),
            cursorBrush = SolidColor(colors.onSurface), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f).testTag("splitValue_$id").semantics { contentDescription = "Amount for $symbol" },
            decorationBox = { input ->
                Box(contentAlignment = Alignment.CenterEnd) {
                    if (value.isEmpty()) Text("0.00", style = style.copy(color = colors.onSurfaceVariant, textAlign = TextAlign.End))
                    input()
                }
            })
    }
}

@Composable fun EmptyShareHint(text: String) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().drawBehind {
        drawRoundRect(colors.outline, cornerRadius = CornerRadius(12.dp.toPx()),
            style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))))
    }.padding(14.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
    }
}
