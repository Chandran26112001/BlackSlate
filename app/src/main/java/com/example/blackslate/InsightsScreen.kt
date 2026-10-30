package com.example.blackslate

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.util.Calendar

@Composable fun InsightsScreen(snapshot: FinanceSnapshot, ledger: Boolean, selectLedger: (Boolean) -> Unit, openPerson: (String) -> Unit, openTransaction: (String) -> Unit) {
    var currency by rememberSaveable(snapshot.settings.currency) { mutableStateOf(snapshot.settings.currency) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Action("My spending", Modifier.weight(1f), secondary = ledger) { selectLedger(false) }
            Action("Peer ledger", Modifier.weight(1f), secondary = !ledger) { selectLedger(true) }
        }
        val currencies = snapshot.transactions.map { it.currency }.plus(snapshot.settings.currency).distinct()
        if (currencies.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            currencies.forEach { option -> Action(option.symbol, secondary = currency != option) { currency = option } }
        }
        if (ledger) LedgerScreen(snapshot, currency, openPerson) else SpendingScreen(snapshot, currency, openTransaction)
    }
}
@Composable private fun SpendingScreen(snapshot: FinanceSnapshot, currency: Currency, open: (String) -> Unit) {
    var monthOffset by rememberSaveable { mutableIntStateOf(0) }
    var allTime by rememberSaveable { mutableStateOf(false) }
    var group by rememberSaveable { mutableStateOf(Grouping.ALL) }
    var search by rememberSaveable { mutableStateOf("") }
    var includeLending by rememberSaveable { mutableStateOf(false) }
    var showCategoryChart by rememberSaveable { mutableStateOf(false) }
    val expandedHistory = remember { mutableStateMapOf<String, Boolean>() }
    val window = monthWindow(monthOffset)
    val period = snapshot.transactions.filter { it.currency == currency && (allTime || it.createdAt >= window.first && it.createdAt < window.second) }
    val spending = period.sumOf { it.personalCost }
    val filtered = period.filter { tx ->
        (includeLending || tx.kind != CategoryType.LENDING) &&
            (search.isBlank() || listOf(tx.note, snapshot.categories.firstOrNull { it.id == tx.categoryId }?.name.orEmpty(),
                tx.spendingCategoryId?.let { id -> snapshot.categories.firstOrNull { it.id == id }?.name }.orEmpty(),
                tx.shares.joinToString(" ") { it.person.name }).any { it.contains(search, ignoreCase = true) })
    }
    val groups = groupTransactions(filtered, snapshot.categories, group)
    val previous = monthWindow(monthOffset - 1)
    val previousTotal = snapshot.transactions.filter { it.currency == currency && it.createdAt >= previous.first && it.createdAt < previous.second }.sumOf { it.personalCost }
    LazyColumn(Modifier.fillMaxSize().testTag("spendingList"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Action("‹", secondary = true, enabled = !allTime) { monthOffset-- }
                Text(if (allTime) "All time" else dateLabel(window.first, "MMMM yyyy"), Modifier.weight(1f).padding(horizontal = 10.dp), style = MaterialTheme.typography.titleLarge)
                Action("›", secondary = true, enabled = !allTime && monthOffset < 0) { monthOffset++ }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Action("Month", secondary = allTime) { allTime = false }
                Action("All time", secondary = !allTime) { allTime = true }
            }
        }
        item {
            Text(money(spending, currency), style = MaterialTheme.typography.displayMedium)
            Caption("YOUR SPENDING · expenses + your group share")
            if (!allTime && previousTotal > 0) Caption(
                money(kotlin.math.abs(spending - previousTotal), currency) + (if (spending > previousTotal) " more" else " less") + " than the previous month")
            if (!allTime) snapshot.settings.monthlyBudgetCents?.takeIf { it > 0L }?.let { budget ->
                val over = (spending - budget).coerceAtLeast(0L)
                Caption(if (over > 0) money(over, currency) + " over your monthly budget" else money(spending, currency) + " / " + money(budget, currency) + " · " + money(budget - spending, currency) + " left")
                LinearProgressIndicator(progress = { (spending.toFloat() / budget).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = if (over > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
        item {
            SlateCard {
                if (allTime) Row(Modifier.fillMaxWidth().clickable { showCategoryChart = !showCategoryChart },
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("By category", style = MaterialTheme.typography.titleLarge)
                    DisclosureGlyph(showCategoryChart)
                } else Text("Spending by week", style = MaterialTheme.typography.titleLarge)
                if (spending == 0L) Caption("No personal spending in this period yet.")
                else if (!allTime || showCategoryChart) {
                    val bars = if (allTime) period.filter { it.personalCost > 0 }.groupBy { it.spendingCategoryIdOrPrimary() }.map { (id, txs) ->
                        (snapshot.categories.firstOrNull { it.id == id }?.name ?: "Other") to txs.sumOf { it.personalCost }
                    }.sortedByDescending { it.second }
                    else {
                        val days = Calendar.getInstance().apply { timeInMillis = window.first }.getActualMaximum(Calendar.DAY_OF_MONTH)
                        (1..days step 7).map { start ->
                            val end = (start + 6).coerceAtMost(days)
                            val sum = period.filter {
                                val day = Calendar.getInstance().apply { timeInMillis = it.createdAt }.get(Calendar.DAY_OF_MONTH)
                                day in start..end
                            }.sumOf { it.personalCost }
                            ("${dateLabel(window.first, "MMM")} $start–$end") to sum
                        }
                    }
                    val max = bars.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1L
                    bars.forEach { (label, value) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Caption(label); Text(money(value, currency), style = MaterialTheme.typography.bodyMedium) }
                        LinearProgressIndicator(progress = { value.toFloat() / max }, modifier = Modifier.fillMaxWidth().height(6.dp), color = MaterialTheme.colorScheme.secondary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }
        item {
            OutlinedTextField(search, { search = it }, label = { Text("Search notes, people or categories") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(includeLending, { includeLending = it })
                Caption("Include lending in history")
            }
            Caption(if (group == Grouping.ALL) "HISTORY · NEWEST FIRST" else "HISTORY · TAP A GROUP TO EXPAND")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Grouping.entries.forEach { option -> Action(option.label, secondary = group != option) { group = option } }
            }
            if (group == Grouping.NEARBY) Caption("Nearby · named areas, with locations within 500 m grouped together")
            if (group == Grouping.PERSON) Caption("Shared expenses appear for each person; total counts your share once.")
            if (search.isNotBlank()) Caption("Search filters history, not the total.")
        }
        if (filtered.isEmpty()) item { SlateCard { Text("Nothing here yet", style = MaterialTheme.typography.headlineMedium); Caption("Log an expense or try another period or search.") } }
        if (group == Grouping.ALL) {
            items(filtered.sortedByDescending { it.createdAt }, key = { it.id }) { TransactionCard(it, snapshot, open) }
        } else groups.forEach { (label, transactions) ->
            item {
                val groupAmount = if (group == Grouping.PERSON && label != "Me") transactions.sumOf { tx ->
                    tx.shares.filter { it.person.name == label }.sumOf { it.cents }
                } else transactions.sumOf { it.personalCost }
                val key = group.name + "::" + label
                val expanded = expandedHistory[key] == true
                SlateCard(onClick = { expandedHistory[key] = !expanded }) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = MaterialTheme.typography.titleLarge)
                        DisclosureGlyph(expanded)
                    }
                    Caption((if (group == Grouping.PERSON && label != "Me") "Original shares · " else "Your spending · ") + money(groupAmount, currency) + " · " + transactions.size + " entries")
                    if (expanded) {
                        HorizontalDivider()
                        transactions.forEach { TransactionCard(it, snapshot, open) }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}
@Composable private fun LedgerScreen(snapshot: FinanceSnapshot, currency: Currency, open: (String) -> Unit) {
    var showSettled by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    val transactions = snapshot.transactions.filter { it.currency == currency }
    val shares = transactions.flatMap { it.shares }
    val people = shares.map { it.person }.distinctBy { it.id }.sortedByDescending { person -> shares.filter { it.person.id == person.id }.sumOf { it.outstanding } }
    LazyColumn(Modifier.fillMaxSize().testTag("ledgerList"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { SlateCard {
            Caption("THEY OWE YOU")
            Text(money(shares.sumOf { it.outstanding }, currency), style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.secondary)
            Caption("Remaining lending + unpaid group shares. Settled amounts are excluded.")
        } }
        item {
            OutlinedTextField(search, { search = it }, label = { Text("Find a person") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(showSettled, { showSettled = it }); Text("Show settled people") }
        }
        val visible = people.filter { p -> p.name.contains(search, true) && (showSettled || shares.any { it.person.id == p.id && it.outstanding > 0 }) }
        if (visible.isEmpty()) item { SlateCard { Text("All clear", style = MaterialTheme.typography.headlineMedium); Caption("No matching outstanding balances. Settled records are retained in history.") } }
        items(visible, key = { it.id }) { person ->
            val outstanding = shares.filter { it.person.id == person.id }.sumOf { it.outstanding }
            SlateCard(Modifier.testTag("person_"+person.name), onClick = { open(person.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(person.name, style = MaterialTheme.typography.titleLarge)
                        Caption("${transactions.count { tx -> tx.shares.any { it.person.id == person.id } }} transactions · View history ›")
                    }
                    Text(if (outstanding == 0L) "Settled ✓" else money(outstanding, currency), color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}
@Composable fun PersonScreen(snapshot: FinanceSnapshot, personId: String, back: () -> Unit, open: (String) -> Unit,
    settleAll: (Currency, String) -> Unit,
    shareSettlement: (Person, Map<Currency, Long>, List<Transaction>) -> Unit) {
    var settleCurrency by rememberSaveable { mutableStateOf<Currency?>(null) }
    val transactions = snapshot.transactions.filter { tx -> tx.shares.any { it.person.id == personId } }.sortedByDescending { it.createdAt }
    val person = transactions.flatMap { it.shares }.firstOrNull { it.person.id == personId }?.person
    val balances = transactions.groupBy { it.currency }.mapValues { (_, txs) -> txs.sumOf { tx -> tx.shares.first { it.person.id == personId }.outstanding } }
    val lastReminder = transactions.flatMap { it.shares }.filter { it.person.id == personId }.mapNotNull { it.person.lastReminderAt }.maxOrNull()
    LazyColumn(Modifier.fillMaxSize().testTag("personHistory"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PageHeader("Person ledger", back) }
        item {
            Text(person?.name ?: "Person", style = MaterialTheme.typography.displayMedium)
        }
        if (person != null && balances.values.any { it > 0L }) item {
            SlateCard {
                Text("Settle in real life", style = MaterialTheme.typography.titleLarge)
                Caption(lastReminder?.let { "Last reminder sent " + dateLabel(it) } ?: "No reminder sent yet")
                Action("Share settlement card", Modifier.fillMaxWidth()) { shareSettlement(person, balances, transactions) }
                Caption("WhatsApp opens when available; Black Slate never takes a payment.")
            }
        }
        transactions.groupBy { it.currency }.forEach { (currency, txs) ->
            item {
                SlateCard {
                    Caption("OWES YOU" + if (currency == Currency.INR) "" else " · " + currency.symbol)
                    Text(money(txs.sumOf { tx -> tx.shares.first { it.person.id == personId }.outstanding }, currency), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.secondary)
                    Caption("Received so far: " + money(txs.sumOf { tx -> tx.shares.first { it.person.id == personId }.received }, currency))
                    if ((balances[currency] ?: 0L) > 0L)
                        Action("Settle all", Modifier.fillMaxWidth().testTag("settleAll_${currency.name}")) { settleCurrency = currency }
                }
            }
        }
        item { Caption("LENDING & GROUP HISTORY · newest first") }
        items(transactions, key = { it.id }) { tx ->
            val share = tx.shares.first { it.person.id == personId }
            SlateCard(onClick = { open(tx.id) }) {
                Text((if (tx.kind == CategoryType.LENDING) "Lent" else "Group share") + " · " + money(share.cents, tx.currency), style = MaterialTheme.typography.titleLarge)
                Caption(dateLabel(tx.createdAt) + if (tx.note.isNotBlank()) " · " + tx.note else "")
                Text(if (tx.kind == CategoryType.LENDING) {
                    when { share.outstanding == 0L -> "Received ✓"; share.received > 0 -> "Partly received"; else -> "Awaiting repayment" }
                } else if (share.outstanding == 0L) "Settled ✓" else money(share.outstanding, tx.currency) + " remaining", color = MaterialTheme.colorScheme.secondary)
                share.repayments.forEach { payment -> Caption("Received " + money(payment.cents, tx.currency) + " · " + dateLabel(payment.at) + " · " + payment.method) }
            }
        }
    }
    settleCurrency?.let { currency ->
        var method by rememberSaveable { mutableStateOf("UPI") }
        val count = transactions.count { it.currency == currency && it.shares.any { s -> s.person.id == personId && s.outstanding > 0 } }
        Sheet("Settle all with " + (person?.name ?: "this person"), { settleCurrency = null }) {
            Text(money(balances[currency] ?: 0L, currency), style = MaterialTheme.typography.headlineLarge)
            Caption("Mark $count pending transaction${if (count == 1) "" else "s"} as received.")
            MethodChoice(method) { method = it }
            Action("Confirm received", Modifier.fillMaxWidth()) { settleAll(currency, method); settleCurrency = null }
        }
    }
}
@Composable private fun TransactionCard(tx: Transaction, snapshot: FinanceSnapshot, open: (String) -> Unit) {
    SlateCard(onClick = { open(tx.id) }) {
        Row {
            val primary = snapshot.categories.firstOrNull { it.id == tx.categoryId }?.name ?: "Transaction"
            val spendingCategory = tx.spendingCategoryId?.let { id -> snapshot.categories.firstOrNull { it.id == id }?.name }
            Text(primary + if (spendingCategory != null && spendingCategory != primary) " · " + spendingCategory else "", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            Text(money(tx.cents, tx.currency))
        }
        Caption(dateLabel(tx.createdAt) + if (tx.note.isBlank()) "" else " · " + tx.note)
        if (tx.shares.isNotEmpty()) Caption(tx.shares.joinToString(", ") { it.person.name } + " · " +
            (if (tx.outstanding == 0L) "Settled" else if (tx.kind == CategoryType.LENDING) "Awaiting repayment" else money(tx.outstanding, tx.currency) + " outstanding"))
        if (tx.kind == CategoryType.GROUP) Caption("You paid " + money(tx.cents, tx.currency) + " · your spending " + money(tx.personalCost, tx.currency))
        if (locationCoordinates(tx.location) != null) Caption((tx.placeName ?: "Saved location") + " · tap for map")
    }
}
