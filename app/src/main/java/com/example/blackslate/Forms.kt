package com.example.blackslate

import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

private fun readPeople(raw: String): List<Person> {
    val array = JSONArray(raw)
    return (0 until array.length()).map { val p = array.getJSONObject(it); Person(p.getString("id"), p.getString("name"), p.getString("phone")) }
}
private fun writePeople(people: List<Person>): String = JSONArray().apply { people.forEach { put(JSONObject().apply { put("id", it.id); put("name", it.name); put("phone", it.phone) }) } }.toString()

@Composable fun ParticipantSheet(category: Category, cents: Long, currency: Currency, recent: List<Person>, dismiss: () -> Unit, save: (List<Share>) -> Unit) {
    val context = LocalContext.current
    val feedback = LocalFeedback.current
    var peopleRaw by rememberSaveable(category.id) { mutableStateOf("[]") }
    val people = readPeople(peopleRaw)
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var includeMe by rememberSaveable { mutableStateOf(true) }
    var error by rememberSaveable { mutableStateOf("") }
    fun add(person: Person) {
        if (people.any { it.id == person.id }) { error = "This person is already included."; return }
        val known = recent.firstOrNull { it.id == person.id } ?: person
        peopleRaw = writePeople(if (category.type == CategoryType.LENDING) listOf(known) else people + known)
        name = ""; phone = ""; error = ""; feedback()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.data?.let { uri -> readContact(context, uri)?.let { add(it) } ?: run { error = "Could not read that contact. You can enter a name below." } }
    }
    val shares = runCatching {
        if (category.type == CategoryType.LENDING) people.take(1).map { Share(it, cents) }
        else splitShares(cents, people, includeMe)
    }.getOrDefault(emptyList())
    Sheet(if (category.type == CategoryType.LENDING) "Lend " + money(cents, currency) else "Split " + money(cents, currency), dismiss) {
        Caption(if (category.type == CategoryType.LENDING) "Choose who you lent to. Repayments stay in their ledger." else "You paid the full amount. Split equally with the people below; their shares are money owed to you.")
        Action("Choose contact", Modifier.fillMaxWidth(), secondary = true) {
            runCatching { picker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) }
                .onFailure { error = "No contacts app found. Enter a name instead." }
        }
        Caption("Only the contact you select is read. No address-book access is needed.")
        OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("personName"))
        OutlinedTextField(phone, { phone = it }, label = { Text("Phone or UPI (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Action("Add person", Modifier.fillMaxWidth(), enabled = name.isNotBlank(), secondary = true) { add(newPerson(name, phone)) }
        if (recent.isNotEmpty()) {
            Caption("RECENT PEOPLE")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                recent.filterNot { person -> people.any { it.id == person.id } }.forEach { person ->
                    Action(person.name, secondary = true) { add(person) }
                }
            }
        }
        people.forEach { person ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(person.name)
                    Caption(money(shares.firstOrNull { it.person.id == person.id }?.cents ?: 0, currency) + " owes you")
                }
                Action("Remove", secondary = true) { peopleRaw = writePeople(people.filterNot { it.id == person.id }) }
            }
        }
        if (category.type == CategoryType.GROUP) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Include my share", Modifier.weight(1f))
                Switch(includeMe, { feedback(); includeMe = it })
            }
            Caption("Your share: " + money(cents - shares.sumOf { it.cents }, currency) + " · Any rounding difference stays with the payer first.")
        }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        Action(if (category.type == CategoryType.LENDING) "Save lending" else "Save group expense", Modifier.fillMaxWidth().testTag("saveShared"),
            enabled = people.isNotEmpty() && shares.isNotEmpty() && shares.all { it.cents > 0 }) { save(shares) }
    }
}

@Composable fun TransactionSheet(tx: Transaction, snapshot: FinanceSnapshot, dismiss: () -> Unit, updateNote: (String) -> Unit,
    repay: (String, Long, String) -> Unit, settle: (String) -> Unit, delete: () -> Unit, map: () -> Unit) {
    var note by rememberSaveable(tx.id) { mutableStateOf(tx.note) }
    var repayPerson by rememberSaveable(tx.id) { mutableStateOf<String?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmSettle by rememberSaveable { mutableStateOf(false) }
    Sheet(snapshot.categories.firstOrNull { it.id == tx.categoryId }?.name ?: "Transaction", dismiss) {
        Text(money(tx.cents, tx.currency), style = MaterialTheme.typography.displayMedium)
        Caption(dateLabel(tx.createdAt))
        Caption(when (tx.kind) {
            CategoryType.EXPENSE -> "Personal expense"
            CategoryType.LENDING -> "Money lent · not counted as spending"
            CategoryType.GROUP -> "Group expense · your share " + money(tx.personalCost, tx.currency)
        })
        OutlinedTextField(note, { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth().testTag("editNote"))
        Action("Save note", enabled = note != tx.note, secondary = true) { updateNote(note) }
        if (locationCoordinates(tx.location) != null) Action("View on Google Maps", Modifier.fillMaxWidth(), secondary = true, onClick = map)
        else Caption("Location was unavailable for this transaction.")
        tx.shares.forEach { share ->
            HorizontalDivider()
            Text(share.person.name, style = MaterialTheme.typography.titleLarge)
            if (share.person.phone.isNotBlank()) Caption(share.person.phone)
            Text(if (share.outstanding == 0L) "Settled ✓" else money(share.outstanding, tx.currency) + " still owed",
                color = MaterialTheme.colorScheme.secondary)
            Caption("Originally " + money(share.cents, tx.currency) + " · " + dateLabel(tx.createdAt, "d MMM yyyy"))
            share.repayments.forEach { payment ->
                Caption("Received " + money(payment.cents, tx.currency) + " · " + dateLabel(payment.at) + " · " + payment.method)
            }
            if (share.outstanding > 0) Action("Record repayment · " + share.person.name, Modifier.fillMaxWidth(), secondary = true) { repayPerson = share.person.id }
        }
        if (tx.outstanding > 0) Action(if (tx.kind == CategoryType.LENDING) "Mark lending as received" else "Settle remaining group balance", Modifier.fillMaxWidth()) { confirmSettle = true }
        Action("Delete transaction", Modifier.fillMaxWidth(), secondary = true) { confirmDelete = true }
    }
    tx.shares.firstOrNull { it.person.id == repayPerson }?.let { share ->
        RepaymentSheet(share, tx.currency, { repayPerson = null }) { cents, method -> repay(share.person.id, cents, method); repayPerson = null }
    }
    if (confirmSettle) {
        var method by rememberSaveable { mutableStateOf("UPI") }
        Sheet("Confirm money received", { confirmSettle = false }) {
            Text("Record " + money(tx.outstanding, tx.currency) + " received from all remaining people? This keeps the original expense and adds dated repayments.")
            MethodChoice(method) { method = it }
            Action("Confirm settlement", Modifier.fillMaxWidth()) { settle(method); confirmSettle = false }
        }
    }
    if (confirmDelete) Sheet("Delete this record?", { confirmDelete = false }) {
        Text("This removes the transaction and its repayment history. This cannot be undone.")
        Action("Delete permanently", Modifier.fillMaxWidth()) { delete(); confirmDelete = false }
    }
}
@Composable private fun MethodChoice(method: String, change: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("UPI", "Cash", "Bank", "Other").forEach { option -> Action(option, secondary = method != option) { change(option) } }
    }
}
@Composable private fun RepaymentSheet(share: Share, currency: Currency, dismiss: () -> Unit, save: (Long, String) -> Unit) {
    var amount by rememberSaveable { mutableStateOf(java.math.BigDecimal(share.outstanding).movePointLeft(2).toPlainString()) }
    var method by rememberSaveable { mutableStateOf("UPI") }
    val cents = parseMoney(amount)
    Sheet("Received from " + share.person.name, dismiss) {
        Caption("Outstanding: " + money(share.outstanding, currency) + ". Enter all or part of this balance.")
        OutlinedTextField(amount, { amount = it }, singleLine = true, label = { Text("Amount received") }, modifier = Modifier.fillMaxWidth().testTag("repaymentAmount"),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
        MethodChoice(method) { method = it }
        if (cents != null && cents > share.outstanding) Text("Amount exceeds the remaining balance.", color = MaterialTheme.colorScheme.error)
        Action("Record received", Modifier.fillMaxWidth(), enabled = cents != null && cents <= share.outstanding) { cents?.let { save(it, method) } }
    }
}
@Composable fun SettingsScreen(settings: AppSettings, back: () -> Unit, update: ((AppSettings) -> AppSettings) -> Unit, categories: () -> Unit, export: () -> Unit, lock: (Boolean) -> Unit, testHaptic: () -> Unit) {
    val feedback = LocalFeedback.current
    var profile by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PageHeader("Settings", back) }
        item { SlateCard(onClick = { profile = true }) { Text(settings.profileName, style = MaterialTheme.typography.headlineMedium); Caption("Personal profile · stored on this device") } }
        item {
            Caption("APPEARANCE")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Appearance.entries.forEach { mode -> Action(when (mode) { Appearance.MATTE -> "Matte Black"; Appearance.SILVER -> "Silver"; Appearance.SYSTEM -> "System" }, secondary = settings.appearance != mode) { update { it.copy(appearance = mode) } } }
            }
        }
        item { SlateCard {
            Text("Currency", style = MaterialTheme.typography.titleLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Currency.entries.forEach { currency -> Action(currency.name, secondary = settings.currency != currency) { update { it.copy(currency = currency) } } }
            }
            Caption("Applies to new records. Existing amounts retain their original currency; no conversion is performed.")
        } }
        item { SlateCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Haptic feedback", Modifier.weight(1f))
                Switch(settings.haptics, { checked -> update { it.copy(haptics = checked) }; if (checked) testHaptic() })
            }
            Caption("Tactile feedback for keys, dragging, buttons and saving. Device vibration settings still apply.")
            Action("Test haptics", secondary = true, enabled = settings.haptics, onClick = testHaptic)
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Motion effects", Modifier.weight(1f))
                Switch(settings.liquidPhysics, { feedback(); update { s -> s.copy(liquidPhysics = it) } })
            }
        } }
        item { Action("Manage categories", Modifier.fillMaxWidth(), secondary = true, onClick = categories) }
        item { SlateCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("App lock", Modifier.weight(1f))
                Switch(settings.appLock, { feedback(); lock(it) })
            }
            Caption("Uses your Android device PIN or password. Screenshots are blocked while app lock is enabled.")
            Action("Export data (CSV)", Modifier.fillMaxWidth(), secondary = true, onClick = export)
            Caption("Export includes contacts, locations and repayments. Share only with people you trust.")
        } }
        item { Caption("Black Slate · local-first. No account, server or bank connection."); Spacer(Modifier.height(12.dp)) }
    }
    if (profile) {
        var name by rememberSaveable { mutableStateOf(settings.profileName) }
        Sheet("Your profile", { profile = false }) {
            OutlinedTextField(name, { name = it }, label = { Text("Display name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Action("Save profile", enabled = name.isNotBlank()) { update { it.copy(profileName = name.trim()) }; profile = false }
        }
    }
}
@Composable fun CategoriesScreen(snapshot: FinanceSnapshot, back: () -> Unit, add: () -> Unit, delete: (Category) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PageHeader("Categories", back) }
        item { Action("+ New category", Modifier.fillMaxWidth(), onClick = add) }
        snapshot.categories.forEach { category ->
            item {
                SlateCard {
                    Text(category.name, style = MaterialTheme.typography.headlineMedium)
                    val count = snapshot.transactions.count { it.categoryId == category.id }
                    Caption(category.type.name.lowercase().replaceFirstChar { it.uppercase() } + " · " + count + " entries")
                    if (!category.isDefault && count == 0) Action("Delete unused category", secondary = true) { delete(category) }
                }
            }
        }
    }
}
@Composable fun CategorySheet(dismiss: () -> Unit, save: (String, CategoryType, Accent) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(CategoryType.EXPENSE) }
    var accent by rememberSaveable { mutableStateOf(Accent.RED) }
    Sheet("New category", dismiss) {
        OutlinedTextField(name, { name = it }, label = { Text("Category name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Caption("TYPE")
        Column {
            CategoryType.entries.forEach { option -> Action(option.name.lowercase().replaceFirstChar { it.uppercase() }, secondary = type != option) { type = option } }
        }
        Caption("ACCENT")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Accent.entries.forEach { color -> Action(color.name.lowercase(), secondary = accent != color) { accent = color } }
        }
        Action("Create category", Modifier.fillMaxWidth(), enabled = name.isNotBlank()) { save(name, type, accent) }
    }
}
