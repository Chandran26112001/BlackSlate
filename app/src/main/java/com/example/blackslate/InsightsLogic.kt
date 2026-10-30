package com.example.blackslate

import java.util.Calendar
import kotlin.math.*

fun monthWindow(offset: Int, now: Long = System.currentTimeMillis()): Pair<Long, Long> {
    val c = Calendar.getInstance().apply {
        timeInMillis = now; set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0); add(Calendar.MONTH, offset)
    }
    val start = c.timeInMillis
    c.add(Calendar.MONTH, 1)
    return start to c.timeInMillis
}
fun distanceMeters(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
    val lat = Math.toRadians(b.first - a.first)
    val lon = Math.toRadians(b.second - a.second)
    val h = sin(lat / 2).pow(2) + cos(Math.toRadians(a.first)) * cos(Math.toRadians(b.first)) * sin(lon / 2).pow(2)
    return 6371000 * 2 * atan2(sqrt(h.coerceIn(0.0, 1.0)), sqrt((1 - h).coerceIn(0.0, 1.0)))
}
enum class Grouping(val label: String) { ALL("All"), DAY("Day"), MONTH("Month"), CATEGORY("Category"), PERSON("Person"), NEARBY("Nearby") }
fun groupTransactions(transactions: List<Transaction>, categories: List<Category>, grouping: Grouping): Map<String, List<Transaction>> {
    if (grouping == Grouping.ALL) return mapOf("All" to transactions.sortedByDescending { it.createdAt })
    if (grouping == Grouping.PERSON) {
        val result = linkedMapOf<String, MutableList<Transaction>>()
        transactions.sortedByDescending { it.createdAt }.forEach { tx ->
            val names = tx.shares.map { it.person.name }.distinct() +
                if (tx.personalCost > 0) listOf("Me") else emptyList()
            names.forEach { name -> result.getOrPut(name) { mutableListOf() }.add(tx) }
        }
        return result
    }
    data class NearbyCluster(val center: Pair<Double, Double>, val names: MutableList<String> = mutableListOf())
    val centers = mutableListOf<NearbyCluster>()
    return transactions.sortedByDescending { it.createdAt }.groupBy { tx ->
        when (grouping) {
            Grouping.ALL -> "All"
            Grouping.DAY -> dateLabel(tx.createdAt, "d MMM yyyy")
            Grouping.MONTH -> dateLabel(tx.createdAt, "MMMM yyyy")
            Grouping.CATEGORY -> categories.firstOrNull { it.id == tx.spendingCategoryIdOrPrimary() }?.name ?: "Uncategorized"
            Grouping.PERSON -> if (tx.shares.isEmpty()) "Only me" else tx.shares.map { it.person.name }.sorted().joinToString(" + ")
            Grouping.NEARBY -> {
                val point = locationCoordinates(tx.location)
                if (point == null) "No location" else {
                    var index = centers.indexOfFirst { distanceMeters(it.center, point) <= 500 }
                    if (index == -1) { centers.add(NearbyCluster(point)); index = centers.lastIndex }
                    tx.placeName?.trim()?.takeIf { it.isNotEmpty() }?.let { name ->
                        if (name !in centers[index].names) centers[index].names += name
                    }
                    centers[index].names.firstOrNull() ?: if (index == 0) "Unnamed nearby place" else "Unnamed nearby place " + (index + 1)
                }
            }
        }
    }
}
