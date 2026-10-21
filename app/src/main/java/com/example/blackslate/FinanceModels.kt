package com.example.blackslate

import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class CategoryType { EXPENSE, LENDING, GROUP }
enum class Accent { RED, TEAL, GOLD, SILVER, BLUE }
enum class Appearance { MATTE, SILVER, SYSTEM }
enum class Currency(val symbol: String, val label: String) {
    INR("₹", "Indian rupee"), USD("$", "US dollar"), EUR("€", "Euro"), GBP("£", "Pound sterling")
}
data class Category(val id: String, val name: String, val type: CategoryType, val accent: Accent, val isDefault: Boolean = false)
data class Person(
    val id: String,
    val name: String,
    val phone: String = "",
    /** The last time a respectful settlement reminder was intentionally shared. */
    val lastReminderAt: Long? = null
)
data class Repayment(val cents: Long, val at: Long, val method: String)
data class Share(val person: Person, val cents: Long, val repayments: List<Repayment> = emptyList()) {
    val received: Long get() = repayments.sumOf { it.cents }
    val outstanding: Long get() = (cents - received).coerceAtLeast(0)
}
data class Transaction(
    val id: String = UUID.randomUUID().toString(),
    val cents: Long,
    val categoryId: String,
    val kind: CategoryType,
    val note: String = "",
    val currency: Currency = Currency.INR,
    val location: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val shares: List<Share> = emptyList(),
    val images: List<String> = emptyList(),
    /** A user-controlled area label, such as Indiranagar or Koramangala. */
    val placeName: String? = null,
    /** A normal expense category attached to a shared Group Expense. */
    val spendingCategoryId: String? = null
) {
    val outstanding: Long get() = shares.sumOf { it.outstanding }
    val personalCost: Long get() = if (kind == CategoryType.LENDING) 0 else cents - shares.sumOf { it.cents }
}
data class AppSettings(
    val profileName: String = "Venkat C",
    val appearance: Appearance = Appearance.MATTE,
    val currency: Currency = Currency.INR,
    val haptics: Boolean = true,
    val liquidPhysics: Boolean = true,
    val appLock: Boolean = false,
    val categoryListView: Boolean = false,
    /** Location is deliberately off until the owner chooses to save it. */
    val saveLocation: Boolean = false,
    /** Images are attached only after an explicit camera/gallery action. */
    val allowPhotoAttachments: Boolean = true,
    /** A single monthly, out-of-pocket spending guardrail. */
    val monthlyBudgetCents: Long? = null
)
data class FinanceSnapshot(
    val categories: List<Category> = listOf(
        Category("food", "Food", CategoryType.EXPENSE, Accent.RED, true),
        Category("lending", "Lending", CategoryType.LENDING, Accent.TEAL, true),
        Category("group_expense", "Group Expense", CategoryType.GROUP, Accent.GOLD, true)
    ),
    val transactions: List<Transaction> = emptyList(),
    val settings: AppSettings = AppSettings()
)
fun parseMoney(value: String): Long? = runCatching {
    val decimal = BigDecimal(value.trim())
    if (decimal.scale() > 2 || decimal <= BigDecimal.ZERO || decimal > BigDecimal("99999999.99")) null
    else decimal.movePointRight(2).longValueExact()
}.getOrNull()

fun splitShares(cents: Long, people: List<Person>, includeMe: Boolean): List<Share> {
    require(cents > 0 && people.isNotEmpty())
    require(people.map { it.id }.distinct().size == people.size)
    val count = people.size + if (includeMe) 1 else 0
    require(cents >= count) { "Amount is too small to split." }
    val each = cents / count
    val remainder = cents % count
    return people.mapIndexed { index, person ->
        val position = index + if (includeMe) 1 else 0
        Share(person, each + if (position < remainder) 1 else 0)
    }
}

enum class SplitMode(val label: String) { EQUAL("Equal"), PARTS("Parts"), AMOUNTS("Amounts") }
const val SELF_SPLIT_ID = "__self__"

/** Allocates every paise, including rounding remainders, without floating-point arithmetic. */
fun groupShares(cents: Long, people: List<Person>, includeMe: Boolean, mode: SplitMode,
    values: Map<String, String>): List<Share> {
    if (mode == SplitMode.EQUAL) return splitShares(cents, people, includeMe)
    require(cents > 0 && people.isNotEmpty()) { "Add a person first." }
    require(people.map { it.id }.distinct().size == people.size) { "A person can only be included once." }
    val ids = (if (includeMe) listOf(SELF_SPLIT_ID) else emptyList()) + people.map { it.id }
    val amounts = if (mode == SplitMode.AMOUNTS) {
        ids.map { id ->
            val value = runCatching { BigDecimal(values[id].orEmpty().trim()) }.getOrNull()
            require(value != null && value.scale() <= 2 && value >= BigDecimal.ZERO && value <= BigDecimal("99999999.99")) {
                "Enter an amount for each person."
            }
            value!!.movePointRight(2).longValueExact()
        }.also { require(it.sum() == cents) { "Shares must add up to the total." } }
    } else {
        val parts = ids.map { id ->
            val value = runCatching { BigDecimal(values[id] ?: "1") }.getOrNull()
            require(value != null && value > BigDecimal.ZERO && value <= BigDecimal("10000") && value.scale() <= 2) {
                "Use positive parts with up to 2 decimals."
            }
            value!!
        }
        val total = parts.fold(BigDecimal.ZERO, BigDecimal::add)
        val exact = parts.map { BigDecimal(cents).multiply(it).divide(total, 12, java.math.RoundingMode.DOWN) }
        val allocated = exact.map { it.toLong() }.toMutableList()
        val remaining = (cents - allocated.sum()).toInt()
        val order = exact.indices.sortedByDescending { exact[it].subtract(BigDecimal(allocated[it])) }
        repeat(remaining) { allocated[order[it]]++ }
        allocated
    }
    return people.mapIndexed { index, person ->
        val amount = amounts[index + if (includeMe) 1 else 0]
        require(amount > 0) { "Each person's share must be at least 0.01." }
        Share(person, amount)
    }
}

fun settlePersonTransactions(transactions: List<Transaction>, personId: String, currency: Currency,
    method: String, at: Long = System.currentTimeMillis()): List<Transaction> = transactions.map { tx ->
    if (tx.currency != currency || tx.kind == CategoryType.EXPENSE) tx else tx.copy(shares = tx.shares.map { share ->
        if (share.person.id != personId || share.outstanding == 0L) share
        else share.copy(repayments = share.repayments + Repayment(share.outstanding, at, method))
    })
}
fun recordRepayment(transaction: Transaction, personId: String, cents: Long, method: String, at: Long = System.currentTimeMillis()): Transaction {
    val share = transaction.shares.first { it.person.id == personId }
    require(cents in 1..share.outstanding) { "Repayment must not exceed the balance." }
    return transaction.copy(shares = transaction.shares.map {
        if (it.person.id == personId) it.copy(repayments = it.repayments + Repayment(cents, at, method)) else it
    })
}
fun newPerson(name: String, phone: String = ""): Person {
    val clean = name.trim().replace(Regex("\\s+"), " ")
    require(clean.isNotBlank())
    val digits = phone.filter { it.isDigit() }
    val identity = when {
        phone.contains('@') -> "upi:${phone.trim().lowercase(Locale.ROOT)}"
        digits.isNotBlank() -> "phone:$digits"
        else -> "name:${clean.lowercase(Locale.ROOT)}"
    }
    return Person(UUID.nameUUIDFromBytes(identity.toByteArray(Charsets.UTF_8)).toString(), clean, phone.trim())
}
fun money(cents: Long, currency: Currency): String = currency.symbol + java.text.NumberFormat.getNumberInstance(
    if (currency == Currency.INR) Locale.forLanguageTag("en-IN") else Locale.US
).apply { maximumFractionDigits = 2; minimumFractionDigits = if (cents % 100L == 0L) 0 else 2 }
    .format(BigDecimal(cents).movePointLeft(2))
fun dateLabel(time: Long, pattern: String = "d MMM yyyy · h:mm a"): String =
    SimpleDateFormat(pattern, Locale.getDefault()).format(Date(time))
fun locationCoordinates(location: String?): Pair<Double, Double>? {
    val parts = location?.split(",") ?: return null
    if (parts.size != 2) return null
    val lat = parts[0].trim().toDoubleOrNull() ?: return null
    val lon = parts[1].trim().toDoubleOrNull() ?: return null
    return if (lat in -90.0..90.0 && lon in -180.0..180.0) lat to lon else null
}
fun Transaction.spendingCategoryIdOrPrimary(): String = spendingCategoryId ?: categoryId
fun reassignCategoryReferences(transactions: List<Transaction>, fromId: String, toId: String): List<Transaction> =
    transactions.map { transaction ->
        transaction.copy(
            categoryId = if (transaction.categoryId == fromId) toId else transaction.categoryId,
            spendingCategoryId = if (transaction.spendingCategoryId == fromId) toId else transaction.spendingCategoryId
        )
    }
fun csvCell(value: String): String {
    val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@', '\t', '\r')) "'$value" else value
    return "\"" + safe.replace("\"", "\"\"") + "\""
}

