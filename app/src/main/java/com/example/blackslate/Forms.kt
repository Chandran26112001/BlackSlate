package com.example.blackslate

import android.content.Intent
import android.app.DatePickerDialog
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

private fun readPeople(raw: String): List<Person> {
    val array = JSONArray(raw)
    return (0 until array.length()).map { val p = array.getJSONObject(it); Person(p.getString("id"), p.getString("name"), p.getString("phone")) }
}
private fun writePeople(people: List<Person>): String = JSONArray().apply { people.forEach { put(JSONObject().apply { put("id", it.id); put("name", it.name); put("phone", it.phone) }) } }.toString()

@Composable fun ParticipantSheet(category: Category, cents: Long, currency: Currency, recent: List<Person>, expenseCategories: List<Category>,
    dismiss: () -> Unit, save: (List<Share>, String?) -> Unit) {
    val context = LocalContext.current
    val feedback = LocalFeedback.current
    var peopleRaw by rememberSaveable(category.id) { mutableStateOf("[]") }
    val people = readPeople(peopleRaw)
    var name by rememberSaveable { mutableStateOf("") }
    var includeMe by rememberSaveable { mutableStateOf(true) }
    var spendingCategoryId by rememberSaveable(category.id) { mutableStateOf<String?>(null) }
    var error by rememberSaveable { mutableStateOf("") }
    var splitMode by rememberSaveable { mutableStateOf(SplitMode.EQUAL) }
    var splitValues by rememberSaveable { mutableStateOf(mapOf<String, String>()) }
    fun add(person: Person) {
        if (people.any { it.id == person.id }) { error = "This person is already included."; return }
        val known = recent.firstOrNull { it.id == person.id } ?: person
        peopleRaw = writePeople(if (category.type == CategoryType.LENDING) listOf(known) else people + known)
        name = ""; error = ""; feedback()
    }
    val contactPicker = rememberLauncherForActivityResult(PickContactPhone()) { uri ->
        uri?.let { selected ->
            readContact(context, selected)?.let(::add) ?: run { error = "That contact could not be read. Enter a name below instead." }
        }
    }
    val splitResult = runCatching {
        if (category.type == CategoryType.LENDING) people.take(1).map { Share(it, cents) }
        else groupShares(cents, people, includeMe, splitMode, splitValues)
    }
    val shares = splitResult.getOrDefault(emptyList())
    val availableRecent = recent.filterNot { candidate -> people.any { it.id == candidate.id } }
    Sheet(if (category.type == CategoryType.LENDING) "Lend " + money(cents, currency) else "Split " + money(cents, currency), dismiss) {
        Action("Choose contact", Modifier.fillMaxWidth(), secondary = true) {
            contactPicker.launch(null)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Caption("RECENT PEOPLE")
            if (availableRecent.isEmpty()) Caption("No recent people yet")
            else FlowChoices(availableRecent.map { it.id to it.name }) { id ->
                availableRecent.firstOrNull { it.id == id }?.let(::add)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactInput(name, { name = it }, "Name", Modifier.weight(1f), inputModifier = Modifier.testTag("personName"))
            Action("Add person", enabled = name.isNotBlank(), secondary = true) { add(newPerson(name)) }
        }
        if (people.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Caption("ADDED PEOPLE")
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    people.forEach { person ->
                        CompactChoice(person.name + " · " + money(shares.firstOrNull { it.person.id == person.id }?.cents ?: 0, currency) + " ×",
                            selected = true) { peopleRaw = writePeople(people.filterNot { it.id == person.id }) }
                    }
                }
            }
        }
        if (category.type == CategoryType.GROUP) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Include my share")
                    if (shares.isNotEmpty()) Caption("Your share · " + money(cents - shares.sumOf { it.cents }, currency))
                }
                SlateSwitch(includeMe, { feedback(); includeMe = it })
            }
            if (people.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GroupSplitControls(cents, currency, people, includeMe, splitMode, { mode ->
                    splitMode = mode
                    splitValues = if (mode == SplitMode.AMOUNTS) shares.associate { it.person.id to java.math.BigDecimal(it.cents).movePointLeft(2).toPlainString() } +
                        (SELF_SPLIT_ID to java.math.BigDecimal(cents - shares.sumOf { it.cents }).movePointLeft(2).toPlainString()) else emptyMap()
                }, splitValues, { id, value -> splitValues = splitValues + (id to value) })
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Caption("SPENDING CATEGORY · OPTIONAL")
                FlowChoices(listOf("" to "None") + expenseCategories.map { it.id to it.name }, spendingCategoryId) { id ->
                    spendingCategoryId = id.ifEmpty { null }
                }
            }
        }
        if (category.type == CategoryType.GROUP && people.isNotEmpty() && splitResult.isFailure)
            Caption(splitResult.exceptionOrNull()?.message ?: "Check the split.")
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Action(if (category.type == CategoryType.LENDING) "Save lending" else "Save group expense", Modifier.fillMaxWidth().testTag("saveShared"),
            enabled = people.isNotEmpty() && shares.isNotEmpty() && shares.all { it.cents > 0 }) { save(shares, spendingCategoryId) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun FlowChoices(choices: List<Pair<String, String>>, selected: String? = null, onClick: (String) -> Unit) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        choices.forEach { (id, label) ->
            CompactChoice(label, selected == id || (selected == null && id.isEmpty())) { onClick(id) }
        }
    }
}

@Composable private fun CompactChoice(label: String, selected: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(12.dp),
        color = if (selected) colors.secondaryContainer else colors.surfaceVariant,
        contentColor = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
        border = BorderStroke(1.dp, if (selected) colors.secondary else colors.outline),
        modifier = Modifier.height(34.dp).clickable(onClick = onClick)) {
        Box(Modifier.padding(horizontal = 11.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

@Composable fun TransactionSheet(tx: Transaction, snapshot: FinanceSnapshot, dismiss: () -> Unit, focusNote: Boolean = false, updateNote: (String) -> Unit,
    updateTransaction: (Transaction) -> Unit, repay: (String, Long, String) -> Unit, settle: (String) -> Unit, delete: () -> Unit, map: () -> Unit) {
    var note by rememberSaveable(tx.id) { mutableStateOf(tx.note) }
    var repayPerson by rememberSaveable(tx.id) { mutableStateOf<String?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmSettle by rememberSaveable { mutableStateOf(false) }
    var photoIndex by rememberSaveable(tx.id) { mutableStateOf<Int?>(null) }
    var editing by rememberSaveable(tx.id) { mutableStateOf(false) }
    val context = LocalContext.current
    val noteFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(focusNote, tx.id) {
        if (focusNote) {
            withFrameNanos { }
            noteFocus.requestFocus()
            keyboard?.show()
        }
    }
    if (editing) {
        TransactionEditorSheet(tx, snapshot, { editing = false }) { edited ->
            updateTransaction(edited)
            editing = false
        }
        return
    }
    Sheet("", dismiss, showTitle = false) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val primary = snapshot.categories.firstOrNull { it.id == tx.categoryId }?.name ?: "Transaction"
            val spending = tx.spendingCategoryId?.let { id -> snapshot.categories.firstOrNull { it.id == id }?.name }
            Text(primary + if (spending != null && spending != primary) " · " + spending else "",
                Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TransactionTool(SlateIcon.EDIT, "Edit transaction") { editing = true }
                if (locationCoordinates(tx.location) != null) TransactionTool(SlateIcon.LOCATION, "Open location", onClick = map)
                TransactionTool(SlateIcon.TRASH, "Delete transaction", destructive = true) { confirmDelete = true }
            }
        }
        Text(money(tx.cents, tx.currency).withRupeeFace(), style = MaterialTheme.typography.displayMedium)
        Caption(dateLabel(tx.createdAt) + (tx.placeName?.let { " · " + it } ?: ""))
        if (tx.kind == CategoryType.GROUP) Caption("Your share · " + money(tx.personalCost, tx.currency))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val colors = MaterialTheme.colorScheme
            Surface(
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(12.dp),
                color = colors.surface,
                border = BorderStroke(1.dp, colors.outline)
            ) {
                BasicTextField(
                    value = note,
                    onValueChange = { note = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                    cursorBrush = SolidColor(colors.onSurface),
                    modifier = Modifier.fillMaxSize().focusRequester(noteFocus).testTag("editNote")
                        .semantics { contentDescription = "Note" },
                    decorationBox = { innerTextField ->
                        Box(Modifier.fillMaxSize().padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                            if (note.isEmpty()) Text("Note", style = MaterialTheme.typography.bodyLarge,
                                color = colors.onSurfaceVariant)
                            innerTextField()
                        }
                    }
                )
            }
            TransactionTool(SlateIcon.SAVE, "Save note", enabled = note != tx.note, size = 50.dp) { updateNote(note) }
        }
        if (tx.images.isNotEmpty()) {
            Caption("PHOTOS · " + tx.images.size)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tx.images.forEachIndexed { index, path ->
                    val bitmap = remember(path) { PhotoStorage.bitmap(context, path, 240) }
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(80.dp).clickable { photoIndex = index }.testTag("photo_" + index)) {
                        if (bitmap != null) Image(bitmap, "Photo " + (index + 1), contentScale = ContentScale.Crop)
                        else Box(contentAlignment = Alignment.Center) { Text("Unavailable", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
        tx.shares.forEach { share ->
            SlateDivider()
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(share.person.name, style = MaterialTheme.typography.titleLarge)
                Text(if (tx.kind == CategoryType.LENDING) {
                    when { share.outstanding == 0L -> "Received ✓"; share.received > 0 -> "Partly received"; else -> "Awaiting repayment" }
                } else if (share.outstanding == 0L) "Settled ✓" else money(share.outstanding, tx.currency) + " still owed",
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.secondary)
                if (tx.kind == CategoryType.GROUP && share.received > 0) Caption("Share · " + money(share.cents, tx.currency))
                share.repayments.forEach { payment ->
                    Caption("Received " + money(payment.cents, tx.currency) + " · " + dateLabel(payment.at) + " · " + payment.method)
                }
                if (tx.kind == CategoryType.GROUP && share.outstanding > 0) {
                    Spacer(Modifier.height(4.dp))
                    Action("Record repayment · " + share.person.name, Modifier.fillMaxWidth(), secondary = true) { repayPerson = share.person.id }
                }
            }
        }
        if (tx.outstanding > 0) Action(if (tx.kind == CategoryType.LENDING) "Mark lending as received" else "Settle remaining group balance", Modifier.fillMaxWidth()) { confirmSettle = true }
    }
    photoIndex?.let { index ->
        val path = tx.images.getOrNull(index)
        if (path != null) Dialog(onDismissRequest = { photoIndex = null }) {
            Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val bitmap = remember(path) { PhotoStorage.bitmap(context, path) }
                    if (bitmap != null) Image(bitmap, "Transaction photo " + (index + 1),
                        Modifier.fillMaxWidth().heightIn(max = 580.dp), contentScale = ContentScale.Fit)
                    else Text("Photo unavailable")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ActionIcon(SlateIcon.CHEVRON_LEFT, "Previous photo", enabled = index > 0) { photoIndex = index - 1 }
                        Text((index + 1).toString() + " / " + tx.images.size, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        ActionIcon(SlateIcon.CHEVRON_RIGHT, "Next photo", enabled = index < tx.images.lastIndex) { photoIndex = index + 1 }
                    }
                    Action("Close photo", secondary = true) { photoIndex = null }
                }
            }
        }
    }
    tx.shares.firstOrNull { it.person.id == repayPerson }?.let { share ->
        RepaymentSheet(share, tx.currency, { repayPerson = null }) { cents, method -> repay(share.person.id, cents, method); repayPerson = null }
    }
    if (confirmSettle) {
        var method by rememberSaveable { mutableStateOf("UPI") }
        Sheet("Confirm money received", { confirmSettle = false }) {
            Text("Mark " + money(tx.outstanding, tx.currency) + " as received?")
            MethodChoice(method) { method = it }
            Action("Confirm settlement", Modifier.fillMaxWidth()) { settle(method); confirmSettle = false }
        }
    }
    if (confirmDelete) Sheet("Delete this record?", { confirmDelete = false }) {
        Text("This removes the transaction and its repayment history. This cannot be undone.")
        Action("Delete permanently", Modifier.fillMaxWidth()) { delete(); confirmDelete = false }
    }
}

@Composable private fun TransactionTool(icon: SlateIcon, label: String, enabled: Boolean = true, destructive: Boolean = false,
    size: androidx.compose.ui.unit.Dp = 42.dp, onClick: () -> Unit) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (destructive) MaterialTheme.colorScheme.errorContainer.copy(alpha = .42f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f),
        border = BorderStroke(1.dp, if (destructive) MaterialTheme.colorScheme.error.copy(alpha = .48f) else MaterialTheme.colorScheme.outline.copy(alpha = .72f)),
        modifier = Modifier.size(size)
    ) {
        IconAction(icon, label, onClick, Modifier.fillMaxSize(), showLabel = false, enabled = enabled,
            iconSize = if (size > 42.dp) 24.dp else 19.dp,
            tint = if (enabled) color else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .38f))
    }
}

@Composable private fun TransactionEditorSheet(tx: Transaction, snapshot: FinanceSnapshot, dismiss: () -> Unit, save: (Transaction) -> Unit) {
    val context = LocalContext.current
    var amount by rememberSaveable(tx.id) { mutableStateOf(java.math.BigDecimal(tx.cents).movePointLeft(2).toPlainString()) }
    var categoryId by rememberSaveable(tx.id) { mutableStateOf(tx.categoryId) }
    var spendingCategoryId by rememberSaveable(tx.id) { mutableStateOf(tx.spendingCategoryId) }
    var createdAt by rememberSaveable(tx.id) { mutableLongStateOf(tx.createdAt) }
    var peopleText by rememberSaveable(tx.id) { mutableStateOf(tx.shares.joinToString("\n") { it.person.name }) }
    var includeMe by rememberSaveable(tx.id) { mutableStateOf(tx.kind == CategoryType.GROUP && tx.personalCost > 0L) }
    var splitMode by rememberSaveable(tx.id) { mutableStateOf(SplitMode.AMOUNTS) }
    var splitValues by rememberSaveable(tx.id) { mutableStateOf(
        tx.shares.associate { it.person.id to java.math.BigDecimal(it.cents).movePointLeft(2).toPlainString() } +
            (SELF_SPLIT_ID to java.math.BigDecimal(tx.personalCost).movePointLeft(2).toPlainString())) }
    var categoryMenu by rememberSaveable { mutableStateOf(false) }
    var spendingMenu by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf("") }
    val hasRepayments = tx.shares.any { it.repayments.isNotEmpty() }
    val selected = snapshot.categories.firstOrNull { it.id == categoryId } ?: snapshot.categories.first { it.id == tx.categoryId }
    val categoryChoices = snapshot.categories.filter { it.type == tx.kind }
    val expenseCategories = snapshot.categories.filter { it.type == CategoryType.EXPENSE }
    val cents = parseMoney(amount)
    val existingPeople = tx.shares.associateBy { it.person.name.trim().lowercase() }.mapValues { it.value.person }
    val people = peopleText.lines().flatMap { it.split(",") }.map { it.trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase() }
        .map { raw -> existingPeople[raw.lowercase()] ?: newPerson(raw) }
    val proposedShares = runCatching {
        when (selected.type) {
            CategoryType.EXPENSE -> emptyList()
            CategoryType.LENDING -> listOf(Share(people.single(), cents ?: 0L))
            CategoryType.GROUP -> groupShares(cents ?: 0L, people, includeMe, splitMode, splitValues)
        }
    }.getOrDefault(emptyList())
    fun chooseDate() {
        val calendar = Calendar.getInstance().apply { timeInMillis = createdAt }
        DatePickerDialog(context, { _, year, month, day ->
            calendar.set(Calendar.YEAR, year); calendar.set(Calendar.MONTH, month); calendar.set(Calendar.DAY_OF_MONTH, day)
            createdAt = calendar.timeInMillis
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }
    Sheet("Edit transaction", dismiss) {
        SlateTextField(amount, { amount = it; error = "" }, label = { Text("Amount") }, singleLine = true, enabled = !hasRepayments,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        if (tx.kind == CategoryType.EXPENSE) Box {
            Action("Category · " + selected.name, Modifier.fillMaxWidth(), secondary = true) { categoryMenu = true }
            SlateDropdownMenu(categoryMenu, { categoryMenu = false }) {
                categoryChoices.forEach { category -> SlateDropdownItem(category.name, categoryId == category.id) {
                    categoryId = category.id; categoryMenu = false
                } }
            }
        }
        Action("Date · " + dateLabel(createdAt, "d MMM yyyy"), Modifier.fillMaxWidth(), secondary = true, onClick = ::chooseDate)
        if (selected.type == CategoryType.GROUP) {
            Box {
                val label = spendingCategoryId?.let { id -> expenseCategories.firstOrNull { it.id == id }?.name } ?: "None"
                Action("Spending category · " + label, Modifier.fillMaxWidth(), secondary = true) { spendingMenu = true }
                SlateDropdownMenu(spendingMenu, { spendingMenu = false }) {
                    SlateDropdownItem("None", spendingCategoryId == null) { spendingCategoryId = null; spendingMenu = false }
                    expenseCategories.forEach { category -> SlateDropdownItem(category.name, spendingCategoryId == category.id) {
                        spendingCategoryId = category.id; spendingMenu = false
                    } }
                }
            }
        }
        if (selected.type != CategoryType.EXPENSE) {
            SlateTextField(peopleText, { peopleText = it; error = "" }, label = { Text(if (selected.type == CategoryType.LENDING) "Person" else "People · one per line") },
                enabled = !hasRepayments, modifier = Modifier.fillMaxWidth())
            if (selected.type == CategoryType.GROUP) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Include my share", Modifier.weight(1f)); SlateSwitch(includeMe, { includeMe = it }, enabled = !hasRepayments) }
                if (!hasRepayments && people.isNotEmpty()) GroupSplitControls(cents ?: 0L, tx.currency, people, includeMe,
                    splitMode, { mode ->
                        splitMode = mode
                        if (mode == SplitMode.PARTS) splitValues = emptyMap()
                    }, splitValues, { id, value -> splitValues = splitValues + (id to value) })
            }
        }
        if (hasRepayments) Caption("Amount and people are locked after a repayment, so the settlement history stays accurate.")
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        Action("Save changes", Modifier.fillMaxWidth()) {
            when {
                cents == null -> error = "Enter a valid amount."
                selected.type != CategoryType.EXPENSE && people.isEmpty() -> error = "Add at least one person."
                selected.type == CategoryType.LENDING && people.size != 1 -> error = "Lending needs exactly one person."
                !hasRepayments && selected.type != CategoryType.EXPENSE && proposedShares.isEmpty() -> error = "Check each share and make sure the split matches the total."
                else -> {
                    val shares = if (hasRepayments) tx.shares else proposedShares
                    save(tx.copy(cents = cents, categoryId = selected.id, kind = selected.type, createdAt = createdAt, shares = shares,
                        spendingCategoryId = if (selected.type == CategoryType.GROUP) spendingCategoryId else null))
                }
            }
        }
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable fun MethodChoice(method: String, change: (String) -> Unit) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("UPI", "Cash", "Bank", "Other").forEach { option -> Action(option, secondary = method != option, compact = true) { change(option) } }
    }
}
@Composable private fun RepaymentSheet(share: Share, currency: Currency, dismiss: () -> Unit, save: (Long, String) -> Unit) {
    var amount by rememberSaveable { mutableStateOf(java.math.BigDecimal(share.outstanding).movePointLeft(2).toPlainString()) }
    var method by rememberSaveable { mutableStateOf("UPI") }
    val cents = parseMoney(amount)
    Sheet("Received from " + share.person.name, dismiss) {
        Caption("Outstanding: " + money(share.outstanding, currency) + ". Enter all or part of this balance.")
        SlateTextField(amount, { amount = it }, singleLine = true, label = { Text("Amount received") }, modifier = Modifier.fillMaxWidth().testTag("repaymentAmount"),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
        MethodChoice(method) { method = it }
        if (cents != null && cents > share.outstanding) Text("Amount exceeds the remaining balance.", color = MaterialTheme.colorScheme.error)
        Action("Record received", Modifier.fillMaxWidth(), enabled = cents != null && cents <= share.outstanding) { cents?.let { save(it, method) } }
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable fun SettingsScreen(settings: AppSettings, back: () -> Unit, update: ((AppSettings) -> AppSettings) -> Unit, categories: () -> Unit, export: () -> Unit, import: () -> Unit,
    assistantEnabled: Boolean, enableAssistant: () -> Unit, manageAssistant: () -> Unit, lock: (Boolean) -> Unit, testHaptic: () -> Unit) {
    val feedback = LocalFeedback.current
    var profile by rememberSaveable { mutableStateOf(false) }
    var budgetEditor by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PageHeader("Settings", back) }
        item { SlateCard(onClick = { profile = true }) { Text(settings.profileName, style = MaterialTheme.typography.headlineMedium); Caption("Personal profile · stored on this device") } }
        item {
            Caption("APPEARANCE")
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Appearance.entries.forEach { mode -> Action(when (mode) { Appearance.MATTE -> "Matte Black"; Appearance.SILVER -> "Silver"; Appearance.SYSTEM -> "System" }, secondary = settings.appearance != mode) { update { it.copy(appearance = mode) } } }
            }
        }
        item { SlateCard {
            Text("Currency", style = MaterialTheme.typography.titleLarge)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Currency.entries.forEach { currency -> Action(currency.name, secondary = settings.currency != currency, compact = true) { update { it.copy(currency = currency) } } }
            }
            Caption("New entries only · past values keep their currency")
        } }
        item { SlateCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Haptic feedback", Modifier.weight(1f))
                SlateSwitch(settings.haptics, { checked -> update { it.copy(haptics = checked) }; if (checked) testHaptic() })
            }
            Caption("Uses your device's touch vibration")
            Action("Test haptics", secondary = true, enabled = settings.haptics, onClick = testHaptic)
            SlateDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Motion effects", Modifier.weight(1f))
                SlateSwitch(settings.liquidPhysics, { feedback(); update { s -> s.copy(liquidPhysics = it) } })
            }
            SlateDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Scrollable category list", Modifier.weight(1f))
                SlateSwitch(settings.categoryListView, { checked -> feedback(); update { s -> s.copy(categoryListView = checked) } })
            }
        } }
        item { SlateCard {
            Text("Monthly budget", style = MaterialTheme.typography.titleLarge)
            val budget = settings.monthlyBudgetCents
            Caption(if (budget == null) "No spending guardrail set" else money(budget, settings.currency) + " each month")
            Action(if (budget == null) "Set budget" else "Edit budget", Modifier.fillMaxWidth(), secondary = true) { budgetEditor = true }
        } }
        item { SlateCard {
            Text("Privacy", style = MaterialTheme.typography.titleLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Save transaction location", Modifier.weight(1f))
                SlateSwitch(settings.saveLocation, { checked -> feedback(); update { it.copy(saveLocation = checked) } })
            }
            Caption("Off by default. When on, Black Slate asks Android for a pin only while saving and names the area using the device geocoder.")
            SlateDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Allow photo attachments", Modifier.weight(1f))
                SlateSwitch(settings.allowPhotoAttachments, { checked -> feedback(); update { it.copy(allowPhotoAttachments = checked) } })
            }
            Caption("Photos are only added when you tap the camera. Existing photos remain local until their transaction is deleted.")
        } }
        item { Action("Manage categories", Modifier.fillMaxWidth(), secondary = true, onClick = categories) }
        item { SlateCard {
            Text("Quick open", style = MaterialTheme.typography.titleLarge)
            Caption("Long-press the Black Slate app icon, then tap Open Black Slate.")
        } }
        item { SlateCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Power-button launch", Modifier.weight(1f))
                SlateSwitch(assistantEnabled, { enabled -> if (enabled) enableAssistant() else manageAssistant() })
            }
            Caption(if (assistantEnabled) "Black Slate is your default digital assistant. Hold power to open it."
                else "Optional · choose Black Slate as your default digital assistant, then hold power to open it. This replaces Gemini or Google Assistant on the power button.")
            Caption("Black Slate never listens or reads the screen in this mode.")
            Action(if (assistantEnabled) "Change default assistant" else "Enable power-button launch", Modifier.fillMaxWidth(), secondary = true,
                onClick = if (assistantEnabled) manageAssistant else enableAssistant)
        } }
        item { SlateCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("App lock", Modifier.weight(1f))
                SlateSwitch(settings.appLock, { feedback(); lock(it) })
            }
            Caption("Uses your device PIN or password")
            Action("Export data (CSV)", Modifier.fillMaxWidth(), secondary = true, onClick = export)
            Action("Restore data (CSV)", Modifier.fillMaxWidth(), secondary = true, onClick = import)
            Caption("Restore merges transactions, contacts and locations · photos stay in the app")
        } }
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
        var budget by rememberSaveable { mutableStateOf(settings.monthlyBudgetCents?.let { java.math.BigDecimal(it).movePointLeft(2).toPlainString() }.orEmpty()) }
        val cents = parseMoney(budget)
        Sheet("Monthly budget", { budgetEditor = false }) {
            Caption("Black Slate measures out-of-pocket spending, including your share of a group expense.")
            SlateTextField(budget, { budget = it }, label = { Text("Budget") }, singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            if (budget.isNotBlank() && cents == null) Text("Enter a positive amount with up to two decimal places.", color = MaterialTheme.colorScheme.error)
            Action("Save budget", Modifier.fillMaxWidth(), enabled = cents != null) { update { it.copy(monthlyBudgetCents = cents) }; budgetEditor = false }
            if (settings.monthlyBudgetCents != null) Action("Remove budget", Modifier.fillMaxWidth(), secondary = true) { update { it.copy(monthlyBudgetCents = null) }; budgetEditor = false }
        }
    }
}
@Composable fun CategoriesScreen(snapshot: FinanceSnapshot, back: () -> Unit, add: () -> Unit,
    rename: (Category, String) -> Boolean, delete: (Category, String?) -> Boolean, reorder: (List<String>) -> Unit) {
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    var replacementId by rememberSaveable { mutableStateOf<String?>(null) }
    CategoryOrderList(snapshot.categories, back, add, reorder) { category ->
                    val count = snapshot.transactions.count { it.categoryId == category.id || it.spendingCategoryId == category.id }
                        Column(Modifier.weight(1f)) {
                            Text(category.name, style = MaterialTheme.typography.headlineMedium, maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Caption(category.type.name.lowercase().replaceFirstChar { it.uppercase() } + " · " + count + " entries")
                        }
                        TransactionTool(SlateIcon.EDIT, "Edit " + category.name) { editingId = category.id }
                        if (category.type == CategoryType.EXPENSE) {
                            TransactionTool(SlateIcon.TRASH, "Delete " + category.name, destructive = true) {
                                deletingId = category.id
                                replacementId = null
                            }
                        }
    }
    snapshot.categories.firstOrNull { it.id == editingId }?.let { category ->
        CategorySheet({ editingId = null }, category) { name ->
            if (rename(category, name)) editingId = null
        }
    }
    snapshot.categories.firstOrNull { it.id == deletingId }?.let { category ->
        val count = snapshot.transactions.count { it.categoryId == category.id || it.spendingCategoryId == category.id }
        val alternatives = snapshot.categories.filter { it.id != category.id && it.type == CategoryType.EXPENSE }
        Sheet("Delete " + category.name + "?", { deletingId = null; replacementId = null }) {
            if (count > 0) {
                Text("$count transaction${if (count == 1) "" else "s"} use this category.",
                    color = MaterialTheme.colorScheme.error)
                Caption("Choose where to move them. Amounts and repayment history stay unchanged.")
            } else Caption("This category has no transactions.")
            if (alternatives.isEmpty()) {
                Text("Create another expense category before deleting this one.")
                Action("Create category", Modifier.fillMaxWidth(), secondary = true) {
                    deletingId = null
                    add()
                }
            } else {
                if (count > 0) {
                    alternatives.forEach { alternative ->
                        Action(alternative.name, Modifier.fillMaxWidth(), secondary = replacementId != alternative.id) {
                            replacementId = alternative.id
                        }
                    }
                }
                Action(if (count > 0) "Move transactions and delete" else "Delete category",
                    Modifier.fillMaxWidth(), enabled = count == 0 || replacementId != null) {
                    if (delete(category, replacementId)) {
                        deletingId = null
                        replacementId = null
                    }
                }
            }
        }
    }
}
@Composable fun CategorySheet(dismiss: () -> Unit, category: Category? = null, save: (String) -> Unit) {
    var name by rememberSaveable(category?.id) { mutableStateOf(category?.name.orEmpty()) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    if (category == null) LaunchedEffect(Unit) {
        withFrameNanos { }
        focus.requestFocus()
        keyboard?.show()
    }
    Sheet(if (category == null) "New category" else "Edit category", dismiss) {
        SlateTextField(name, { name = it }, label = { Text("Category name") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("newCategoryName"))
        Action(if (category == null) "Create expense category" else "Save category", Modifier.fillMaxWidth(),
            enabled = name.isNotBlank()) { save(name) }
    }
}
