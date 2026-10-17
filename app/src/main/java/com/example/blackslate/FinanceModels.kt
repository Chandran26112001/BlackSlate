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
data class Person(val id: String, val name: String, val phone: String = "")
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
    val shares: List<Share> = emptyList()
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
    val appLock: Boolean = false
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
fun csvCell(value: String): String {
    val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@', '\t', '\r')) "'$value" else value
    return "\"" + safe.replace("\"", "\"\"") + "\""
}

