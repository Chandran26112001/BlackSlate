package com.example.blackslate

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class FinanceLogicTest {
    private val alice = newPerson("Alice")
    private val bob = newPerson("Bob")
    @Test fun moneyParsingIsExact() {
        assertEquals(29901L, parseMoney("299.01"))
        listOf("", "0", "-2", "1.001", "999999999", "NaN").forEach { assertNull(parseMoney(it)) }
    }
    @Test fun equalSplitIncludesPayerAndConservesEveryCent() {
        val shares = splitShares(10000, listOf(alice, bob), true)
        assertEquals(listOf(3333L, 3333L), shares.map { it.cents })
        val tx = Transaction(cents = 10000, categoryId = "group_expense", kind = CategoryType.GROUP, shares = shares)
        assertEquals(3334L, tx.personalCost)
        assertEquals(10000L, tx.personalCost + tx.outstanding)
    }
    @Test fun splitWithoutPayerConservesRemainder() {
        assertEquals(listOf(51L, 50L), splitShares(101, listOf(alice, bob), false).map { it.cents })
    }
    @Test(expected = IllegalArgumentException::class) fun duplicatePeopleRejected() { splitShares(100, listOf(alice, alice), true) }
    @Test(expected = IllegalArgumentException::class) fun tinySplitRejected() { splitShares(1, listOf(alice, bob), true) }
    @Test fun repaymentKeepsOriginalAndDatedHistory() {
        val tx = Transaction(cents = 5000, categoryId = "lending", kind = CategoryType.LENDING, shares = listOf(Share(alice, 5000)), createdAt = 100)
        val partial = recordRepayment(tx, alice.id, 2000, "Cash", 200)
        val settled = recordRepayment(partial, alice.id, 3000, "UPI", 300)
        assertEquals(5000L, settled.cents)
        assertEquals(100L, settled.createdAt)
        assertEquals(0L, settled.outstanding)
        assertEquals(0L, settled.personalCost)
        assertEquals(listOf(200L, 300L), settled.shares.single().repayments.map { it.at })
        assertEquals(5000L, tx.outstanding)
    }
    @Test(expected = IllegalArgumentException::class) fun overpaymentRejected() {
        recordRepayment(Transaction(cents = 100, categoryId = "lending", kind = CategoryType.LENDING, shares = listOf(Share(alice, 100))), alice.id, 101, "Cash")
    }
    @Test fun groupRepaymentDoesNotIncreasePersonalSpending() {
        val tx = Transaction(cents = 30000, categoryId = "group_expense", kind = CategoryType.GROUP, shares = splitShares(30000, listOf(alice, bob), true))
        assertEquals(10000L, recordRepayment(tx, alice.id, 10000, "UPI").personalCost)
    }
    @Test fun nearbyClusteringKeepsMissingLocationsSeparate() {
        fun tx(id: String, location: String?) = Transaction(id, 100, "food", CategoryType.EXPENSE, location = location)
        val groups = groupTransactions(listOf(tx("a", "12.0,77.0"), tx("b", "12.0001,77.0001"), tx("c", "13.0,78.0"), tx("d", null)), FinanceSnapshot().categories, Grouping.NEARBY)
        assertEquals(3, groups.size)
        assertEquals(2, groups["Area 1 · within 500 m"]!!.size)
        assertEquals(1, groups["No location"]!!.size)
    }
    @Test fun coordinatesValidated() {
        assertNull(locationCoordinates("nan,0")); assertNull(locationCoordinates("91,0"))
        assertEquals(12.0 to 77.0, locationCoordinates("12, 77"))
    }
    @Test fun monthWindowIsExclusiveAndHandlesYearBoundary() {
        val now = Calendar.getInstance().apply { set(2026, Calendar.JANUARY, 15, 10, 0, 0) }.timeInMillis
        val december = monthWindow(-1, now)
        assertEquals("December 2025", dateLabel(december.first, "MMMM yyyy"))
        assertEquals(monthWindow(0, now).first, december.second)
    }
    @Test fun identityStableAndWhitespaceNormalized() {
        assertEquals(newPerson("Alice  Smith").id, newPerson(" alice smith ").id)
        assertEquals(newPerson("A", "+91 123").id, newPerson("Another name", "+91123").id)
    }
    @Test fun csvEscapesQuotesAndFormulaInjection() {
        assertEquals("\"' =1+1\"", csvCell(" =1+1"))
        assertEquals("\"a\"\"b\"", csvCell("a\"b"))
    }
}
