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
enum class Grouping(val label: String) { DAY("Day"), MONTH("Month"), CATEGORY("Category"), PERSON("Person"), NEARBY("Nearby") }
fun groupTransactions(transactions: List<Transaction>, categories: List<Category>, grouping: Grouping): Map<String, List<Transaction>> {
    val centers = mutableListOf<Pair<Double, Double>>()
    return transactions.sortedByDescending { it.createdAt }.groupBy { tx ->
        when (grouping) {
            Grouping.DAY -> dateLabel(tx.createdAt, "d MMM yyyy")
            Grouping.MONTH -> dateLabel(tx.createdAt, "MMMM yyyy")
            Grouping.CATEGORY -> categories.firstOrNull { it.id == tx.categoryId }?.name ?: "Uncategorized"
            Grouping.PERSON -> if (tx.shares.isEmpty()) "Only me" else tx.shares.map { it.person.name }.sorted().joinToString(" + ")
            Grouping.NEARBY -> {
                val point = locationCoordinates(tx.location)
                if (point == null) "No location" else {
                    var index = centers.indexOfFirst { distanceMeters(it, point) <= 500 }
                    if (index == -1) { centers.add(point); index = centers.lastIndex }
                    "Area " + (index + 1) + " · within 500 m"
                }
            }
        }
    }
}
