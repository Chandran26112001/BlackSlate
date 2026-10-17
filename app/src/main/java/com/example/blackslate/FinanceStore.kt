package com.example.blackslate

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.math.BigDecimal

class FinanceStore(context: Context, storageName: String = "black_slate_finance") {
    private val preferences = context.getSharedPreferences(storageName, Context.MODE_PRIVATE)
    private val exportDirectory = File(context.cacheDir, "exports")
    var snapshot by mutableStateOf(readSnapshot())
        private set

    private fun save(next: FinanceSnapshot) {
        check(preferences.edit().putString("snapshot", encodeSnapshot(next)).commit()) { "Storage unavailable. Please try again." }
        snapshot = next
    }
    fun log(transaction: Transaction): Transaction {
        require(transaction.cents > 0)
        require(snapshot.transactions.none { it.id == transaction.id }) { "Transaction is already saved." }
        require(transaction.shares.all { it.cents > 0 && it.received <= it.cents })
        require(transaction.shares.map { it.person.id }.distinct().size == transaction.shares.size)
        require(transaction.shares.sumOf { it.cents } <= transaction.cents)
        save(snapshot.copy(transactions = listOf(transaction) + snapshot.transactions))
        return transaction
    }
    fun updateNote(id: String, note: String) = save(snapshot.copy(transactions = snapshot.transactions.map {
        if (it.id == id) it.copy(note = note.trim()) else it
    }))
    fun deleteTransaction(id: String) = save(snapshot.copy(transactions = snapshot.transactions.filterNot { it.id == id }))
    fun repay(id: String, personId: String, cents: Long, method: String) {
        save(snapshot.copy(transactions = snapshot.transactions.map {
            if (it.id == id) recordRepayment(it, personId, cents, method) else it
        }))
    }
    fun settle(id: String, method: String) {
        val now = System.currentTimeMillis()
        save(snapshot.copy(transactions = snapshot.transactions.map { transaction ->
            if (transaction.id != id) transaction else transaction.copy(shares = transaction.shares.map {
                if (it.outstanding == 0L) it else it.copy(repayments = it.repayments + Repayment(it.outstanding, now, method))
            })
        }))
    }
    fun addCategory(name: String, type: CategoryType, accent: Accent) {
        require(name.isNotBlank()) { "Enter a category name." }
        require(snapshot.categories.none { it.name.equals(name.trim(), true) }) { "This category already exists." }
        save(snapshot.copy(categories = snapshot.categories + Category(java.util.UUID.randomUUID().toString(), name.trim(), type, accent)))
    }
    fun deleteCategory(category: Category) {
        require(!category.isDefault) { "Default categories are kept." }
        require(snapshot.transactions.none { it.categoryId == category.id }) { "This category has transactions and cannot be deleted." }
        save(snapshot.copy(categories = snapshot.categories.filterNot { it.id == category.id }))
    }
    fun updateSettings(transform: (AppSettings) -> AppSettings) = save(snapshot.copy(settings = transform(snapshot.settings)))
    fun exportCsv(): File {
        exportDirectory.mkdirs()
        val file = File(exportDirectory, "black-slate-transactions.csv")
        file.writeText(buildString {
            appendLine("record,date,transaction_id,amount,currency,category,note,person,phone,outstanding,location,method")
            snapshot.transactions.forEach { tx ->
                val category = snapshot.categories.firstOrNull { it.id == tx.categoryId }?.name.orEmpty()
                fun row(type: String, at: Long, cents: Long, person: Person?, outstanding: Long, method: String = "") {
                    appendLine(listOf(type, dateLabel(at), tx.id, BigDecimal(cents).movePointLeft(2).toPlainString(), tx.currency.name, category, tx.note,
                        person?.name.orEmpty(), person?.phone.orEmpty(), BigDecimal(outstanding).movePointLeft(2).toPlainString(),
                        tx.location.orEmpty(), method).joinToString(",") { csvCell(it) })
                }
                row("transaction", tx.createdAt, tx.cents, null, tx.outstanding)
                tx.shares.forEach { share ->
                    row("share", tx.createdAt, share.cents, share.person, share.outstanding)
                    share.repayments.forEach { row("repayment", it.at, it.cents, share.person, share.outstanding, it.method) }
                }
            }
        })
        return file
    }
    private fun readSnapshot(): FinanceSnapshot {
        val raw = preferences.getString("snapshot", null) ?: return FinanceSnapshot()
        // Preserve unreadable data instead of silently replacing financial records.
        return decodeSnapshot(raw)
    }
}

fun encodeSnapshot(snapshot: FinanceSnapshot): String = JSONObject().apply {
    put("version", 2)
    put("categories", JSONArray().apply {
        snapshot.categories.forEach { category -> put(JSONObject().apply {
            put("id", category.id); put("name", category.name); put("type", category.type.name)
            put("accent", category.accent.name); put("isDefault", category.isDefault)
        }) }
    })
    put("transactions", JSONArray().apply {
        snapshot.transactions.forEach { tx -> put(JSONObject().apply {
            put("id", tx.id); put("cents", tx.cents); put("categoryId", tx.categoryId); put("kind", tx.kind.name)
            put("note", tx.note); put("currency", tx.currency.name); put("location", tx.location); put("createdAt", tx.createdAt)
            put("shares", JSONArray().apply { tx.shares.forEach { share -> put(JSONObject().apply {
                put("personId", share.person.id); put("name", share.person.name); put("phone", share.person.phone); put("cents", share.cents)
                put("repayments", JSONArray().apply { share.repayments.forEach { payment -> put(JSONObject().apply {
                    put("cents", payment.cents); put("at", payment.at); put("method", payment.method)
                }) } })
            }) } })
        }) }
    })
    put("settings", JSONObject().apply {
        val s = snapshot.settings
        put("profileName", s.profileName); put("appearance", s.appearance.name); put("currency", s.currency.name)
        put("haptics", s.haptics); put("liquidPhysics", s.liquidPhysics); put("appLock", s.appLock)
    })
}.toString()

fun decodeSnapshot(raw: String): FinanceSnapshot {
    val root = JSONObject(raw)
    val settingsJson = root.optJSONObject("settings") ?: JSONObject()
    val settings = AppSettings(
        settingsJson.optString("profileName", "Venkat C"),
        enumOr(settingsJson.optString("appearance"), Appearance.MATTE),
        enumOr(settingsJson.optString("currency"), Currency.INR),
        settingsJson.optBoolean("haptics", true), settingsJson.optBoolean("liquidPhysics", true), settingsJson.optBoolean("appLock", false)
    )
    val categories = root.optJSONArray("categories").objects().map {
        Category(it.getString("id"), it.getString("name"), enumOr(it.optString("type"), CategoryType.EXPENSE),
            enumOr(it.optString("accent"), Accent.SILVER), it.optBoolean("isDefault"))
    }.ifEmpty { FinanceSnapshot().categories }
    val transactions = root.optJSONArray("transactions").objects().map { tx ->
        val kind = enumOr(tx.optString("kind"), categories.firstOrNull { it.id == tx.getString("categoryId") }?.type ?: CategoryType.EXPENSE)
        val cents = if (tx.has("cents")) tx.getLong("cents") else BigDecimal(tx.getString("amount")).movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact()
        val shares = if (tx.has("shares")) tx.optJSONArray("shares").objects().map { share ->
            Share(Person(share.getString("personId"), share.getString("name"), share.optString("phone")), share.getLong("cents"),
                share.optJSONArray("repayments").objects().map { Repayment(it.getLong("cents"), it.getLong("at"), it.optString("method", "Other")) })
        } else {
            // Old group records had one contact and no split. Preserve their entire receivable.
            if (kind == CategoryType.EXPENSE) emptyList() else listOf(
                Share(newPerson(tx.nullable("contactName") ?: "Unnamed person", tx.nullable("contactPhone").orEmpty()), cents)
            )
        }
        Transaction(tx.getString("id"), cents, tx.getString("categoryId"), kind, tx.optString("note"),
            enumOr(tx.optString("currency"), settings.currency), tx.nullable("location"), tx.getLong("createdAt"), shares)
    }
    return FinanceSnapshot(categories, transactions, settings)
}
private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }
private fun JSONObject.nullable(key: String): String? = if (isNull(key)) null else optString(key).ifBlank { null }
private inline fun <reified T : Enum<T>> enumOr(value: String, fallback: T): T = runCatching { enumValueOf<T>(value) }.getOrDefault(fallback)
