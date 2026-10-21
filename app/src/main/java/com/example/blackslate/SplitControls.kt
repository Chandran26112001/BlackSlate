package com.example.blackslate

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable fun CompactInput(value: String, change: (String) -> Unit, hint: String,
    modifier: Modifier = Modifier, inputModifier: Modifier = Modifier, keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier.height(48.dp), shape = RoundedCornerShape(12.dp), color = colors.surface,
        border = BorderStroke(1.dp, colors.outline)) {
        BasicTextField(value, change, singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.onSurface), keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
            modifier = Modifier.fillMaxSize().then(inputModifier).semantics { contentDescription = hint }, decorationBox = { input ->
                Box(Modifier.fillMaxSize().padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(hint, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                    input()
                }
            })
    }
}

@Composable fun GroupSplitControls(cents: Long, currency: Currency, people: List<Person>, includeMe: Boolean,
    mode: SplitMode, changeMode: (SplitMode) -> Unit, values: Map<String, String>, changeValue: (String, String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val feedback = LocalFeedback.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SplitMode.entries.forEach { option ->
            Surface(onClick = { feedback(); changeMode(option) },
                modifier = Modifier.weight(1f).height(42.dp).testTag("split_${option.name}"),
                shape = RoundedCornerShape(16.dp),
                color = if (mode == option) colors.primary else androidx.compose.ui.graphics.Color.Transparent,
                contentColor = if (mode == option) colors.onPrimary else colors.onSurfaceVariant,
                border = if (mode == option) null else BorderStroke(1.dp, colors.outline)) {
                Box(Modifier.padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                    Text(option.label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }
        }
    }
    if (mode != SplitMode.EQUAL) {
        val participants = (if (includeMe) listOf(SELF_SPLIT_ID to "You") else emptyList()) + people.map { it.id to it.name }
        participants.forEach { (id, name) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(name, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                CompactInput(values[id] ?: if (mode == SplitMode.PARTS) "1" else "", { changeValue(id, it) },
                    if (mode == SplitMode.PARTS) "Parts" else currency.symbol,
                    Modifier.width(108.dp), inputModifier = Modifier.testTag("splitValue_$id"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
        }
        if (mode == SplitMode.AMOUNTS) {
            val entered = participants.sumOf { (id, _) -> parseMoney(values[id].orEmpty()) ?: 0L }
            val difference = cents - entered
            Caption(when {
                difference > 0 -> money(difference, currency) + " left to assign"
                difference < 0 -> money(-difference, currency) + " over total"
                else -> "Total matches · " + money(cents, currency)
            })
        } else Caption("For example, 2 : 1 gives one person twice the share.")
    }
}
