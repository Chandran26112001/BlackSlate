package com.example.blackslate

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Settings, ordered by why you open them: your setup (budget, categories, power button, currency) shows live state up top,
 * then look and feel, capture and privacy, and data. Every row is an icon, a label, an optional one-line subtitle and
 * either a switch or a chevron.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun SettingsScreen(settings: AppSettings, back: () -> Unit, update: ((AppSettings) -> AppSettings) -> Unit, categories: () -> Unit, export: () -> Unit, import: () -> Unit,
    assistantEnabled: Boolean, enableAssistant: () -> Unit, manageAssistant: () -> Unit, lock: (Boolean) -> Unit, testHaptic: () -> Unit,
    categoryList: List<Category>, monthlySpending: Long) {
    val feedback = LocalFeedback.current
    var profile by rememberSaveable { mutableStateOf(false) }
    var budgetEditor by rememberSaveable { mutableStateOf(false) }
    var quickOpen by rememberSaveable { mutableStateOf(false) }
    var currencyPicker by rememberSaveable { mutableStateOf(false) }
    val budget = settings.monthlyBudgetCents?.takeIf { it > 0L }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { PageHeader("Settings", back) }
        item { ProfileCard(settings.profileName) { profile = true } }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Caption("YOUR SETUP")
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SetupTile(SlateIcon.WALLET, "MONTHLY BUDGET", if (budget == null) "Not set" else money(budget, settings.currency),
                        if (budget == null) "Set a monthly limit" else money(monthlySpending, settings.currency) + " spent · " + (monthlySpending * 100 / budget) + "%",
                        Modifier.weight(1f).fillMaxHeight().testTag("settingsBudget"), extra = {
                            if (budget != null) {
                                val colors = MaterialTheme.colorScheme
                                Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(colors.surfaceVariant)) {
                                    Box(Modifier.fillMaxWidth((monthlySpending.toFloat() / budget).coerceIn(0f, 1f)).fillMaxHeight()
                                        .background(if (monthlySpending > budget) colors.error else colors.secondary))
                                }
                            }
                        }) { budgetEditor = true }
                    SetupTile(SlateIcon.GRID, "CATEGORIES", categoryList.size.toString(), "Reorder, rename, delete",
                        Modifier.weight(1f).fillMaxHeight().testTag("settingsCategories").semantics { contentDescription = "Manage categories" },
                        extra = {
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                categoryList.take(6).forEach { Box(Modifier.size(8.dp).clip(CircleShape).background(accentColor(it.accent))) }
                            }
                        }, onClick = categories)
                }
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SetupTile(SlateIcon.POWER, "POWER BUTTON", if (assistantEnabled) "On" else "Off", if (assistantEnabled) "Hold power to open" else "Tap to set up",
                        Modifier.weight(1f).fillMaxHeight().testTag("settingsQuickOpen")) { quickOpen = true }
                    SetupTile(SlateIcon.CASH, "CURRENCY", settings.currency.name + " " + settings.currency.symbol, "New entries only",
                        Modifier.weight(1f).fillMaxHeight().testTag("settingsCurrency")) { currencyPicker = true }
                }
            }
        }
        item {
            SettingsGroup("LOOK & FEEL") {
                Appearance.entries.let { modes ->
                    Box(Modifier.padding(vertical = 12.dp)) {
                        SlateSegments(modes.map { appearanceLabel(it) }, modes.indexOf(settings.appearance)) { update { s -> s.copy(appearance = modes[it]) } }
                    }
                }
                SlateDivider()
                SwitchRow(SlateIcon.HAPTIC, "Haptic feedback", "Touch vibration on taps", settings.haptics) { checked ->
                    update { it.copy(haptics = checked) }; if (checked) testHaptic()
                }
                SlateDivider()
                SwitchRow(SlateIcon.MOTION, "Motion effects", null, settings.liquidPhysics) { feedback(); update { s -> s.copy(liquidPhysics = it) } }
                SlateDivider()
                SwitchRow(SlateIcon.LIST, "Category list", "Scrollable list instead of a grid", settings.categoryListView) { checked ->
                    feedback(); update { s -> s.copy(categoryListView = checked) }
                }
            }
        }
        item {
            SettingsGroup("CAPTURE & PRIVACY") {
                SwitchRow(SlateIcon.LOCATION, "Save transaction location", "Off by default · only asked while saving", settings.saveLocation) { checked ->
                    feedback(); update { it.copy(saveLocation = checked) }
                }
                SlateDivider()
                SwitchRow(SlateIcon.CAMERA, "Photo attachments", "Added only when you tap the camera", settings.allowPhotoAttachments) { checked ->
                    feedback(); update { it.copy(allowPhotoAttachments = checked) }
                }
                SlateDivider()
                SwitchRow(SlateIcon.LOCK, "App lock", "Uses your device PIN or password", settings.appLock) { feedback(); lock(it) }
            }
        }
        item {
            SettingsGroup("DATA") {
                SettingRow(SlateIcon.DOWNLOAD, "Export data (CSV)", "Every transaction, contact and location", Modifier.clickable(role = Role.Button) { feedback(); export() }) { Chevron() }
                SlateDivider()
                SettingRow(SlateIcon.UPLOAD, "Restore data (CSV)", "Merges in · photos stay in the app", Modifier.clickable(role = Role.Button) { feedback(); import() }) { Chevron() }
            }
        }
        item { Caption("Black Slate · local-first. No account, server or bank connection."); Spacer(Modifier.height(12.dp)) }
    }
    if (profile) {
        var name by rememberSaveable { mutableStateOf(settings.profileName) }
        Sheet("Your profile", { profile = false }) {
            SlateTextField(name, { name = it }, label = { Text("Display name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Action("Save profile", Modifier.fillMaxWidth(), enabled = name.isNotBlank()) {
                update { it.copy(profileName = name.trim()) }
                profile = false
            }
        }
    }
    if (budgetEditor) {
        var amount by rememberSaveable { mutableStateOf(settings.monthlyBudgetCents?.let { java.math.BigDecimal(it).movePointLeft(2).toPlainString() }.orEmpty()) }
        val cents = parseMoney(amount)
        Sheet("Monthly budget", { budgetEditor = false }) {
            Caption("Black Slate measures out-of-pocket spending, including your share of a group expense.")
            SlateTextField(amount, { amount = it }, label = { Text("Budget") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            if (amount.isNotBlank() && cents == null) Text("Enter a positive amount with up to two decimal places.", color = MaterialTheme.colorScheme.error)
            Action("Save budget", Modifier.fillMaxWidth(), enabled = cents != null) { update { it.copy(monthlyBudgetCents = cents) }; budgetEditor = false }
            if (settings.monthlyBudgetCents != null) Action("Remove budget", Modifier.fillMaxWidth(), secondary = true) { update { it.copy(monthlyBudgetCents = null) }; budgetEditor = false }
        }
    }
    if (currencyPicker) Sheet("Currency", { currencyPicker = false }) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Currency.entries.forEach { currency ->
                Action(currency.name, secondary = settings.currency != currency, compact = true) { update { it.copy(currency = currency) } }
            }
        }
        Caption("New entries only · past values keep their currency")
    }
    if (quickOpen) Sheet("Quick open", { quickOpen = false }) {
        Caption("Reach Black Slate without hunting for the icon")
        SettingsCard {
            SettingRow(SlateIcon.POWER, "Power button", "Hold it to open Black Slate",
                Modifier.toggleable(assistantEnabled, role = Role.Switch) { enabled -> if (enabled) enableAssistant() else manageAssistant() }) {
                SlateSwitch(assistantEnabled, null)
            }
            Column(Modifier.padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val colors = MaterialTheme.colorScheme
                Text(if (assistantEnabled) "Black Slate is your default digital assistant. It never listens or reads the screen."
                    else "Optional. Choose Black Slate as your default digital assistant, then hold power to open it. This replaces Gemini or Google Assistant on the power button. Black Slate never listens or reads the screen in this mode.",
                    style = MaterialTheme.typography.bodySmall, color = if (assistantEnabled) colors.onSecondaryContainer else colors.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(if (assistantEnabled) colors.secondaryContainer else colors.surfaceVariant.copy(alpha = .5f)).padding(horizontal = 12.dp, vertical = 10.dp))
                Action(if (assistantEnabled) "Change default assistant" else "Set up power button", Modifier.fillMaxWidth(), secondary = true, compact = true,
                    onClick = if (assistantEnabled) manageAssistant else enableAssistant)
            }
            SlateDivider()
            SettingRow(SlateIcon.APP_ICON, "App icon", "Long-press it, then tap Open Black Slate") {
                Text("ALWAYS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 8.dp, vertical = 3.dp))
            }
        }
        Caption("Turning the power button off hands it back to your previous assistant.")
    }
}

private fun appearanceLabel(mode: Appearance) = when (mode) { Appearance.MATTE -> "Matte Black"; Appearance.SILVER -> "Silver"; Appearance.SYSTEM -> "System" }

@Composable private fun ProfileCard(name: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val feedback = LocalFeedback.current
    val shape = RoundedCornerShape(24.dp)
    Row(Modifier.fillMaxWidth().clip(shape).background(colors.surface).border(1.dp, colors.outline.copy(alpha = .5f), shape)
        .clickable(role = Role.Button) { feedback(); onClick() }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(colors.primary), contentAlignment = Alignment.Center) {
            Text(name.trim().firstOrNull()?.uppercase() ?: "?", color = colors.onPrimary,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Medium))
        }
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Caption("Personal profile · stored on this device")
        }
        Box(Modifier.size(36.dp).clip(CircleShape).background(colors.surfaceVariant).semantics { contentDescription = "Edit profile" }, contentAlignment = Alignment.Center) {
            SlateGlyph(SlateIcon.EDIT, Modifier.size(18.dp), colors.onSurface)
        }
    }
}

/** A live summary of one key feature: tap to open it. */
@Composable private fun SetupTile(icon: SlateIcon, label: String, value: String, sub: String, modifier: Modifier = Modifier,
    extra: @Composable ColumnScope.() -> Unit = {}, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val feedback = LocalFeedback.current
    val shape = RoundedCornerShape(20.dp)
    Column(modifier.clip(shape).background(colors.surface).border(1.dp, colors.outline.copy(alpha = .5f), shape)
        .clickable(role = Role.Button) { feedback(); onClick() }.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(colors.surfaceVariant), contentAlignment = Alignment.Center) {
                SlateGlyph(icon, Modifier.size(18.dp), colors.onSurface)
            }
            Spacer(Modifier.weight(1f))
            SlateGlyph(SlateIcon.CHEVRON_RIGHT, Modifier.size(16.dp), colors.onSurfaceVariant)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1)
            Text(value.withRupeeFace(), style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub.withRupeeFace(), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 2)
        }
        extra()
    }
}

@Composable private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Caption(title); SettingsCard(content) }
}

@Composable private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(colors.surface).border(1.dp, colors.outline.copy(alpha = .5f), shape).padding(horizontal = 16.dp), content = content)
}

@Composable private fun SettingRow(icon: SlateIcon, label: String, sub: String?, modifier: Modifier = Modifier, trailing: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(colors.surfaceVariant), contentAlignment = Alignment.Center) {
            SlateGlyph(icon, Modifier.size(20.dp), colors.onSurface)
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium))
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        trailing()
    }
}

@Composable private fun SwitchRow(icon: SlateIcon, label: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(icon, label, sub, Modifier.toggleable(checked, role = Role.Switch, onValueChange = onChange)) { SlateSwitch(checked, null) }
}

@Composable private fun Chevron() = SlateGlyph(SlateIcon.CHEVRON_RIGHT, Modifier.size(18.dp), MaterialTheme.colorScheme.onSurfaceVariant)

/** A row of mutually exclusive choices, such as the theme. */
@Composable private fun SlateSegments(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val feedback = LocalFeedback.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surfaceVariant).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEachIndexed { index, label ->
            val on = index == selected
            Box(Modifier.weight(1f).height(32.dp).clip(RoundedCornerShape(9.dp)).background(if (on) colors.primary else androidx.compose.ui.graphics.Color.Transparent)
                .clickable(role = Role.Tab) { feedback(); onSelect(index) }, contentAlignment = Alignment.Center) {
                Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, color = if (on) colors.onPrimary else colors.onSurfaceVariant)
            }
        }
    }
}
