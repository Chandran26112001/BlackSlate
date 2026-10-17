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
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            snapshot.transactions.map { it.currency }.plus(snapshot.settings.currency).distinct().forEach { option ->
                Action(option.name, secondary = currency != option) { currency = option }
            }
        }
        if (ledger) LedgerScreen(snapshot, currency, openPerson) else SpendingScreen(snapshot, currency, openTransaction)
    }
}
@Composable private fun SpendingScreen(snapshot: FinanceSnapshot, currency: Currency, open: (String) -> Unit) {
    var monthOffset by rememberSaveable { mutableIntStateOf(0) }
    var allTime by rememberSaveable { mutableStateOf(false) }
    var group by rememberSaveable { mutableStateOf(Grouping.DAY) }
    var search by rememberSaveable { mutableStateOf("") }
    var includeLending by rememberSaveable { mutableStateOf(false) }
    val window = monthWindow(monthOffset)
    val period = snapshot.transactions.filter { it.currency == currency && (allTime || it.createdAt >= window.first && it.createdAt < window.second) }
    val spending = period.sumOf { it.personalCost }
    val filtered = period.filter { tx ->
        (includeLending || tx.kind != CategoryType.LENDING) &&
            (search.isBlank() || listOf(tx.note, snapshot.categories.firstOrNull { it.id == tx.categoryId }?.name.orEmpty(),
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
            Caption("YOUR SPENDING · personal expenses + your group share")
            Caption("Lending and other people's shares are excluded. Repayments do not change your spending.")
            if (!allTime && previousTotal > 0) Caption(
                money(kotlin.math.abs(spending - previousTotal), currency) + (if (spending > previousTotal) " more" else " less") + " than the previous month")
        }
        item {
            SlateCard {
                Text(if (allTime) "Spending by category" else "Your spending by week", style = MaterialTheme.typography.titleLarge)
                if (spending == 0L) Caption("No personal spending in this period yet.")
                else {
                    val bars = if (allTime) period.filter { it.personalCost > 0 }.groupBy { it.categoryId }.map { (id, txs) ->
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
                    Caption("Each bar shows actual spending for its label. Longer bar = higher spending.")
                }
            }
        }
        item {
            OutlinedTextField(search, { search = it }, label = { Text("Search notes, people or categories") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(includeLending, { includeLending = it })
                Caption("Include lending in history")
            }
            Caption("GROUP HISTORY BY")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Grouping.entries.forEach { option -> Action(option.label, secondary = group != option) { group = option } }
            }
            if (group == Grouping.NEARBY) Caption("Nearby groups use a 500 m radius from a saved location, not a street address. Open a transaction for its map.")
            if (group == Grouping.PERSON) Caption("Grouped by the people involved together. Open Peer ledger for one person's complete history.")
            if (search.isNotBlank()) Caption("Search filters the history below; the spending total above stays for the whole period.")
        }
        if (groups.isEmpty()) item { SlateCard { Text("Nothing here yet", style = MaterialTheme.typography.headlineMedium); Caption("Log an expense or try another period or search.") } }
        groups.forEach { (label, transactions) ->
            item { Text(label, style = MaterialTheme.typography.titleLarge); Caption("${transactions.size} records · your spending " + money(transactions.sumOf { it.personalCost }, currency)) }
            items(transactions, key = { it.id }) { TransactionCard(it, snapshot, open) }
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
@Composable fun PersonScreen(snapshot: FinanceSnapshot, personId: String, back: () -> Unit, open: (String) -> Unit) {
    val transactions = snapshot.transactions.filter { tx -> tx.shares.any { it.person.id == personId } }.sortedByDescending { it.createdAt }
    val person = transactions.flatMap { it.shares }.firstOrNull { it.person.id == personId }?.person
    LazyColumn(Modifier.fillMaxSize().testTag("personHistory"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PageHeader("Person ledger", back) }
        item {
            Text(person?.name ?: "Person", style = MaterialTheme.typography.displayMedium)
            if (person?.phone?.isNotBlank() == true) Caption(person.phone)
        }
        transactions.groupBy { it.currency }.forEach { (currency, txs) ->
            item {
                SlateCard {
                    Caption("OWES YOU · " + currency.name)
                    Text(money(txs.sumOf { tx -> tx.shares.first { it.person.id == personId }.outstanding }, currency), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.secondary)
                    Caption("Received so far: " + money(txs.sumOf { tx -> tx.shares.first { it.person.id == personId }.received }, currency))
                }
            }
        }
        item { Caption("LENDING & GROUP HISTORY · newest first") }
        items(transactions, key = { it.id }) { tx ->
            val share = tx.shares.first { it.person.id == personId }
            SlateCard(onClick = { open(tx.id) }) {
                Text((if (tx.kind == CategoryType.LENDING) "Lent" else "Group share") + " · " + money(share.cents, tx.currency), style = MaterialTheme.typography.titleLarge)
                Caption(dateLabel(tx.createdAt) + if (tx.note.isNotBlank()) " · " + tx.note else "")
                Text(if (share.outstanding == 0L) "Settled ✓" else money(share.outstanding, tx.currency) + " remaining", color = MaterialTheme.colorScheme.secondary)
                share.repayments.forEach { payment -> Caption("Received " + money(payment.cents, tx.currency) + " · " + dateLabel(payment.at) + " · " + payment.method) }
                Caption("Tap to view details or record repayment ›")
            }
        }
    }
}
@Composable private fun TransactionCard(tx: Transaction, snapshot: FinanceSnapshot, open: (String) -> Unit) {
    SlateCard(onClick = { open(tx.id) }) {
        Row {
            Text(snapshot.categories.firstOrNull { it.id == tx.categoryId }?.name ?: "Transaction", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            Text(money(tx.cents, tx.currency))
        }
        Caption(dateLabel(tx.createdAt) + if (tx.note.isBlank()) "" else " · " + tx.note)
        if (tx.shares.isNotEmpty()) Caption(tx.shares.joinToString(", ") { it.person.name } + " · " + (if (tx.outstanding == 0L) "Settled" else money(tx.outstanding, tx.currency) + " outstanding"))
        if (tx.kind == CategoryType.GROUP) Caption("You paid " + money(tx.cents, tx.currency) + " · your spending " + money(tx.personalCost, tx.currency))
        if (locationCoordinates(tx.location) != null) Caption("Location saved · tap for map")
    }
}
