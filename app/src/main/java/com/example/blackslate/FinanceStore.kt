package com.example.blackslate

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CsvImportedTransaction(val transaction: Transaction, val categoryName: String, val spendingCategoryName: String? = null)
data class CsvImportPlan(val transactions: List<CsvImportedTransaction>, val duplicateCount: Int, val photoCount: Int)
data class CsvImportResult(val imported: Int, val skipped: Int, val categoriesAdded: Int)

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
        validateTransaction(transaction)
        require(snapshot.transactions.none { it.id == transaction.id }) { "Transaction is already saved." }
        save(snapshot.copy(transactions = listOf(transaction) + snapshot.transactions))
        return transaction
    }
    fun updateTransaction(transaction: Transaction) {
        require(snapshot.transactions.any { it.id == transaction.id }) { "Transaction no longer exists." }
        validateTransaction(transaction)
        save(snapshot.copy(transactions = snapshot.transactions.map { if (it.id == transaction.id) transaction else it }
            .sortedByDescending { it.createdAt }))
    }
    private fun validateTransaction(transaction: Transaction) {
        require(transaction.cents > 0) { "Amount must be greater than zero." }
        require(snapshot.categories.any { it.id == transaction.categoryId }) { "Choose a valid category." }
        transaction.spendingCategoryId?.let { id ->
            require(snapshot.categories.any { it.id == id && it.type == CategoryType.EXPENSE }) { "Choose a valid spending category." }
        }
        require(transaction.shares.all { it.cents > 0 && it.received <= it.cents }) { "A shared balance is invalid." }
        require(transaction.shares.map { it.person.id }.distinct().size == transaction.shares.size) { "A person can only be included once." }
        require(transaction.shares.sumOf { it.cents } <= transaction.cents) { "Shared amounts exceed the amount paid." }
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
    fun markReminderSent(personId: String, at: Long = System.currentTimeMillis()) {
        save(snapshot.copy(transactions = snapshot.transactions.map { transaction ->
            transaction.copy(shares = transaction.shares.map { share ->
                if (share.person.id == personId) share.copy(person = share.person.copy(lastReminderAt = at)) else share
            })
        }))
    }
    fun settlePerson(personId: String, currency: Currency, method: String) {
        save(snapshot.copy(transactions = settlePersonTransactions(snapshot.transactions, personId, currency, method)))
    }
    fun reorderCategories(ids: List<String>) {
        val existing = snapshot.categories.associateBy { it.id }
        require(ids.size == existing.size && ids.toSet() == existing.keys) { "Category order is out of date. Try again." }
        save(snapshot.copy(categories = ids.map { existing.getValue(it) }))
    }
    fun addCategory(name: String): Category {
        require(name.isNotBlank()) { "Enter a category name." }
        require(snapshot.categories.none { it.name.equals(name.trim(), true) }) { "This category already exists." }
        val palette = listOf(Accent.RED, Accent.BLUE, Accent.TEAL, Accent.GOLD, Accent.SILVER)
        val accent = palette[snapshot.categories.count { it.type == CategoryType.EXPENSE } % palette.size]
        val category = Category(java.util.UUID.randomUUID().toString(), name.trim(), CategoryType.EXPENSE, accent)
        save(snapshot.copy(categories = snapshot.categories + category))
        return category
    }
    fun renameCategory(category: Category, name: String) {
        val current = snapshot.categories.firstOrNull { it.id == category.id }
            ?: throw IllegalArgumentException("Category no longer exists.")
        val cleanName = name.trim()
        require(cleanName.isNotBlank()) { "Enter a category name." }
        require(snapshot.categories.none { it.id != current.id && it.name.equals(cleanName, true) }) { "This category already exists." }
        save(snapshot.copy(categories = snapshot.categories.map { if (it.id == current.id) it.copy(name = cleanName) else it }))
    }
    fun deleteCategory(category: Category, replacementId: String? = null) {
        val current = snapshot.categories.firstOrNull { it.id == category.id }
            ?: throw IllegalArgumentException("Category no longer exists.")
        require(current.type == CategoryType.EXPENSE) { "Lending and Group Expense are required categories." }
        require(snapshot.categories.any { it.id != current.id && it.type == CategoryType.EXPENSE }) {
            "Create another expense category first."
        }
        val used = snapshot.transactions.any { it.categoryId == current.id || it.spendingCategoryId == current.id }
        val replacement = replacementId?.let { id ->
            snapshot.categories.firstOrNull { it.id == id && it.id != current.id && it.type == CategoryType.EXPENSE }
                ?: throw IllegalArgumentException("Choose another expense category.")
        }
        require(!used || replacement != null) { "Choose where to move this category's transactions." }
        save(snapshot.copy(
            categories = snapshot.categories.filterNot { it.id == current.id },
            transactions = if (replacement == null) snapshot.transactions
                else reassignCategoryReferences(snapshot.transactions, current.id, replacement.id)
        ))
    }
    fun updateSettings(transform: (AppSettings) -> AppSettings) = save(snapshot.copy(settings = transform(snapshot.settings)))
    fun exportCsv(): File {
        exportDirectory.mkdirs()
        val file = File(exportDirectory, "black-slate-transactions.csv")
        file.writeText(buildString {
            appendLine("record,date,timestamp,transaction_id,amount,currency,category,category_type,note,person,phone,outstanding,location,place_name,spending_category,method,image_count")
            snapshot.transactions.forEach { tx ->
                val category = snapshot.categories.firstOrNull { it.id == tx.categoryId }?.name.orEmpty()
                val categoryType = snapshot.categories.firstOrNull { it.id == tx.categoryId }?.type ?: tx.kind
                val spendingCategory = snapshot.categories.firstOrNull { it.id == tx.spendingCategoryId }?.name.orEmpty()
                fun row(type: String, at: Long, cents: Long, person: Person?, outstanding: Long, method: String = "") {
                    appendLine(listOf(type, dateLabel(at), at.toString(), tx.id, BigDecimal(cents).movePointLeft(2).toPlainString(), tx.currency.name, category, categoryType.name, tx.note,
                        person?.name.orEmpty(), person?.phone.orEmpty(), BigDecimal(outstanding).movePointLeft(2).toPlainString(),
                        tx.location.orEmpty(), tx.placeName.orEmpty(), spendingCategory, method, tx.images.size.toString()).joinToString(",") { csvCell(it) })
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
    fun prepareCsvImport(csv: String): CsvImportPlan {
        val rows = parseCsv(csv)
        require(rows.size > 1) { "This CSV has no transaction records." }
        val columns = rows.first().mapIndexed { index, name -> name.trim().lowercase(Locale.ROOT) to index }.toMap()
        listOf("record", "transaction_id", "amount", "currency", "category").forEach { required ->
            require(columns.containsKey(required)) { "This is not a Black Slate export: missing $required." }
        }
        fun value(row: List<String>, name: String): String = columns[name]?.let { row.getOrElse(it) { "" } }.orEmpty().restoreCsvCell()
        val grouped = rows.drop(1).filter { row -> value(row, "transaction_id").isNotBlank() }
            .groupBy { row -> value(row, "transaction_id") }
        val imported = grouped.values.map { group ->
            val first = group.firstOrNull { value(it, "record").equals("transaction", true) }
                ?: throw IllegalArgumentException("A transaction row is missing from the CSV.")
            val name = value(first, "category").trim().ifBlank { throw IllegalArgumentException("A transaction has no category.") }
            val kind = value(first, "category_type").takeIf { it.isNotBlank() }?.let { enumOr(it, CategoryType.EXPENSE) }
                ?: legacyCategoryType(name)
            val cents = parseMoney(value(first, "amount")) ?: throw IllegalArgumentException("A transaction amount is invalid.")
            val currency = enumOr(value(first, "currency"), snapshot.settings.currency)
            val createdAt = parseCsvTime(value(first, "timestamp"), value(first, "date"))
            val shareRows = group.filter { value(it, "record").equals("share", true) }
            val repaymentRows = group.filter { value(it, "record").equals("repayment", true) }
            val shares = shareRows.map { shareRow ->
                val person = newPerson(value(shareRow, "person"), value(shareRow, "phone"))
                val shareCents = parseMoney(value(shareRow, "amount")) ?: throw IllegalArgumentException("A shared amount is invalid.")
                val repayments = repaymentRows.filter { payment ->
                    value(payment, "person").equals(person.name, true) && value(payment, "phone") == person.phone
                }.map { payment ->
                    val repayment = parseMoney(value(payment, "amount")) ?: throw IllegalArgumentException("A repayment amount is invalid.")
                    Repayment(repayment, parseCsvTime(value(payment, "timestamp"), value(payment, "date")), value(payment, "method").ifBlank { "Other" })
                }
                require(repayments.sumOf { it.cents } <= shareCents) { "A repayment exceeds its shared amount." }
                Share(person, shareCents, repayments)
            }
            require(shares.map { it.person.id }.distinct().size == shares.size) { "A transaction has duplicate people." }
            require(shares.sumOf { it.cents } <= cents) { "Shared amounts exceed the transaction total." }
            CsvImportedTransaction(
                Transaction(value(first, "transaction_id"), cents, "", kind, value(first, "note"), currency,
                    value(first, "location").ifBlank { null }, createdAt, shares, emptyList(), value(first, "place_name").ifBlank { null }),
                name,
                value(first, "spending_category").ifBlank { null }
            )
        }
        require(imported.isNotEmpty()) { "This CSV has no transaction records." }
        val existing = snapshot.transactions.map { it.id }.toSet()
        val photoCount = grouped.values.sumOf { group ->
            group.firstOrNull { value(it, "record").equals("transaction", true) }?.let { value(it, "image_count").toIntOrNull() ?: 0 } ?: 0
        }
        return CsvImportPlan(imported, imported.count { it.transaction.id in existing }, photoCount)
    }
    fun importCsv(plan: CsvImportPlan): CsvImportResult {
        val existing = snapshot.transactions.map { it.id }.toSet()
        val incoming = plan.transactions.filterNot { it.transaction.id in existing }
        if (incoming.isEmpty()) return CsvImportResult(0, plan.transactions.size, 0)
        val categories = snapshot.categories.toMutableList()
        var addedCategories = 0
        fun resolve(name: String, type: CategoryType): Category {
            val existingCategory = categories.firstOrNull { it.name.equals(name, true) }
            if (existingCategory != null) {
                require(existingCategory.type == type) { "Category '$name' has a different type on this device." }
                return existingCategory
            }
            val palette = listOf(Accent.RED, Accent.BLUE, Accent.TEAL, Accent.GOLD, Accent.SILVER)
            val accent = palette[categories.count { it.type == CategoryType.EXPENSE } % palette.size]
            return Category(java.util.UUID.randomUUID().toString(), name, type, accent).also { categories += it; addedCategories++ }
        }
        val restored = incoming.map { item ->
            val category = resolve(item.categoryName, item.transaction.kind)
            val spendingCategory = item.spendingCategoryName?.let { resolve(it, CategoryType.EXPENSE) }
            item.transaction.copy(categoryId = category.id, kind = category.type, spendingCategoryId = spendingCategory?.id)
        }
        save(snapshot.copy(categories = categories, transactions = (restored + snapshot.transactions).sortedByDescending { it.createdAt }))
        return CsvImportResult(restored.size, plan.transactions.size - restored.size, addedCategories)
    }
    private fun readSnapshot(): FinanceSnapshot {
        val raw = preferences.getString("snapshot", null) ?: return FinanceSnapshot()
        // Preserve unreadable data instead of silently replacing financial records.
        return decodeSnapshot(raw)
    }
}

private fun legacyCategoryType(name: String): CategoryType = when (name.trim().lowercase(Locale.ROOT)) {
    "lending" -> CategoryType.LENDING
    "group expense" -> CategoryType.GROUP
    else -> CategoryType.EXPENSE
}
private fun parseCsvTime(timestamp: String, formatted: String): Long {
    timestamp.trim().toLongOrNull()?.let { return it }
    val patterns = listOf("d MMM yyyy · h:mm a", "d MMM yyyy h:mm a")
    patterns.forEach { pattern ->
        listOf(Locale.getDefault(), Locale.US, Locale.UK).distinct().forEach { locale ->
            runCatching { SimpleDateFormat(pattern, locale).apply { isLenient = false }.parse(formatted.trim())?.time }
                .getOrNull()?.let { return it }
        }
    }
    throw IllegalArgumentException("A transaction date is invalid.")
}
private fun String.restoreCsvCell(): String = if (length > 1 && first() == '\'' && this[1] in listOf('=', '+', '-', '@', '\t', '\r')) drop(1) else this
private fun parseCsv(source: String): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    val row = mutableListOf<String>()
    val field = StringBuilder()
    var quoted = false
    var index = 0
    while (index < source.length) {
        when (val char = source[index]) {
            '"' -> if (quoted && source.getOrNull(index + 1) == '"') { field.append(char); index++ } else quoted = !quoted
            ',' -> if (quoted) field.append(char) else { row += field.toString(); field.clear() }
            '\n' -> if (quoted) field.append(char) else { row += field.toString(); rows += row.toList(); row.clear(); field.clear() }
            '\r' -> Unit
            else -> field.append(char)
        }
        index++
    }
    require(!quoted) { "The CSV contains an unfinished quoted value." }
    if (field.isNotEmpty() || row.isNotEmpty()) { row += field.toString(); rows += row.toList() }
    return rows.filter { it.any(String::isNotBlank) }
}

fun encodeSnapshot(snapshot: FinanceSnapshot): String = JSONObject().apply {
    put("version", 3)
    put("categories", JSONArray().apply {
        snapshot.categories.forEach { category -> put(JSONObject().apply {
            put("id", category.id); put("name", category.name); put("type", category.type.name)
            put("accent", category.accent.name); put("isDefault", category.isDefault)
        }) }
    })
    put("transactions", JSONArray().apply {
        snapshot.transactions.forEach { tx -> put(JSONObject().apply {
            put("id", tx.id); put("cents", tx.cents); put("categoryId", tx.categoryId); put("kind", tx.kind.name)
            put("note", tx.note); put("currency", tx.currency.name); put("location", tx.location); put("placeName", tx.placeName)
            put("spendingCategoryId", tx.spendingCategoryId); put("createdAt", tx.createdAt)
            put("images", JSONArray(tx.images))
            put("shares", JSONArray().apply { tx.shares.forEach { share -> put(JSONObject().apply {
                put("personId", share.person.id); put("name", share.person.name); put("phone", share.person.phone); put("lastReminderAt", share.person.lastReminderAt)
                put("cents", share.cents)
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
        put("categoryListView", s.categoryListView); put("saveLocation", s.saveLocation)
        put("allowPhotoAttachments", s.allowPhotoAttachments); put("monthlyBudgetCents", s.monthlyBudgetCents)
    })
}.toString()

fun decodeSnapshot(raw: String): FinanceSnapshot {
    val root = JSONObject(raw)
    val settingsJson = root.optJSONObject("settings") ?: JSONObject()
    val settings = AppSettings(
        settingsJson.optString("profileName", "Venkat C"),
        enumOr(settingsJson.optString("appearance"), Appearance.SILVER),
        enumOr(settingsJson.optString("currency"), Currency.INR),
        settingsJson.optBoolean("haptics", true), settingsJson.optBoolean("liquidPhysics", true), settingsJson.optBoolean("appLock", false),
        settingsJson.optBoolean("categoryListView", true), settingsJson.optBoolean("saveLocation", false),
        settingsJson.optBoolean("allowPhotoAttachments", true), settingsJson.nullableLong("monthlyBudgetCents")
    )
    val categories = root.optJSONArray("categories").objects().map {
        Category(it.getString("id"), it.getString("name"), enumOr(it.optString("type"), CategoryType.EXPENSE),
            enumOr(it.optString("accent"), Accent.SILVER), it.optBoolean("isDefault"))
    }.ifEmpty { FinanceSnapshot().categories }
    val transactions = root.optJSONArray("transactions").objects().map { tx ->
        val kind = enumOr(tx.optString("kind"), categories.firstOrNull { it.id == tx.getString("categoryId") }?.type ?: CategoryType.EXPENSE)
        val cents = if (tx.has("cents")) tx.getLong("cents") else BigDecimal(tx.getString("amount")).movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact()
        val shares = if (tx.has("shares")) tx.optJSONArray("shares").objects().map { share ->
            Share(Person(share.getString("personId"), share.getString("name"), share.optString("phone"), share.nullableLong("lastReminderAt")), share.getLong("cents"),
                share.optJSONArray("repayments").objects().map { Repayment(it.getLong("cents"), it.getLong("at"), it.optString("method", "Other")) })
        } else {
            // Old group records had one contact and no split. Preserve their entire receivable.
            if (kind == CategoryType.EXPENSE) emptyList() else listOf(
                Share(newPerson(tx.nullable("contactName") ?: "Unnamed person", tx.nullable("contactPhone").orEmpty()), cents)
            )
        }
        Transaction(tx.getString("id"), cents, tx.getString("categoryId"), kind, tx.optString("note"),
            enumOr(tx.optString("currency"), settings.currency), tx.nullable("location"), tx.getLong("createdAt"), shares,
            tx.optJSONArray("images")?.let { array -> (0 until array.length()).map { array.getString(it) } } ?: emptyList(),
            tx.nullable("placeName"), tx.nullable("spendingCategoryId"))
    }
    return FinanceSnapshot(categories, transactions, settings)
}
private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }
private fun JSONObject.nullable(key: String): String? = if (isNull(key)) null else optString(key).ifBlank { null }
private fun JSONObject.nullableLong(key: String): Long? = if (!has(key) || isNull(key)) null else optLong(key).takeIf { it > 0L }
private inline fun <reified T : Enum<T>> enumOr(value: String, fallback: T): T = runCatching { enumValueOf<T>(value) }.getOrDefault(fallback)
