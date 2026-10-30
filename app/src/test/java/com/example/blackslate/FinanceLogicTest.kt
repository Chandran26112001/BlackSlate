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
    @Test fun partsSplitSupportsDecimalsAndConservesRounding() {
        val values = mapOf(SELF_SPLIT_ID to "1", alice.id to "2", bob.id to "1")
        val shares = groupShares(30000, listOf(alice, bob), true, SplitMode.PARTS, values)
        assertEquals(listOf(15000L, 7500L), shares.map { it.cents })
        assertEquals(listOf(67L, 34L), groupShares(101, listOf(alice, bob), false,
            SplitMode.PARTS, mapOf(alice.id to "1", bob.id to "0.5")).map { it.cents })
        (3L..200L).forEach { amount ->
            val result = groupShares(amount, listOf(alice, bob), false, SplitMode.PARTS, mapOf(alice.id to "2", bob.id to "1"))
            assertEquals(amount, result.sumOf { it.cents })
        }
    }
    @Test fun exactAmountsMustBalanceAndKeepPersonalSpending() {
        val shares = groupShares(30000, listOf(alice, bob), true, SplitMode.AMOUNTS,
            mapOf(SELF_SPLIT_ID to "50", alice.id to "100", bob.id to "150"))
        assertEquals(listOf(10000L, 15000L), shares.map { it.cents })
        assertTrue(runCatching { groupShares(30000, listOf(alice, bob), false, SplitMode.AMOUNTS,
            mapOf(alice.id to "100", bob.id to "150")) }.isFailure)
        assertTrue(runCatching { groupShares(30000, listOf(alice), false, SplitMode.PARTS, mapOf(alice.id to "0")) }.isFailure)
    }
    @Test fun settleAllPreservesOtherPeopleCurrenciesAndEarlierRepayments() {
        val earlier = Repayment(1000, 10, "Cash")
        val loan = Transaction(cents = 5000, categoryId = "lending", kind = CategoryType.LENDING,
            shares = listOf(Share(alice, 5000, listOf(earlier))))
        val group = Transaction(cents = 9000, categoryId = "group_expense", kind = CategoryType.GROUP,
            shares = listOf(Share(alice, 3000), Share(bob, 3000)))
        val usd = loan.copy(id = "usd", currency = Currency.USD)
        val settled = settlePersonTransactions(listOf(loan, group, usd), alice.id, Currency.INR, "UPI", 20)
        assertEquals(listOf(earlier, Repayment(4000, 20, "UPI")), settled[0].shares.single().repayments)
        assertEquals(0L, settled[1].shares.first().outstanding)
        assertEquals(group.shares.last(), settled[1].shares.last())
        assertEquals(group.personalCost, settled[1].personalCost)
        assertEquals(usd, settled[2])
        assertEquals(settled, settlePersonTransactions(settled, alice.id, Currency.INR, "UPI", 30))
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
        assertEquals(2, groups["Unnamed nearby place"]!!.size)
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
    @Test fun sharedExpenseCarriesItsFoodContextAndReminderState() {
        val reminded = alice.copy(lastReminderAt = 1234L)
        val transaction = Transaction(cents = 12000, categoryId = "group_expense", kind = CategoryType.GROUP,
            location = "12.9716,77.5946", placeName = "Indiranagar", spendingCategoryId = "food", shares = listOf(Share(reminded, 6000)))
        assertEquals("food", transaction.spendingCategoryIdOrPrimary())
        assertEquals("Indiranagar", transaction.placeName)
        assertEquals(1234L, transaction.shares.single().person.lastReminderAt)
    }
    @Test fun movingCategoryPreservesTransactionsAndSharedHistory() {
        val repayment = Repayment(200L, 123L, "Cash")
        val expense = Transaction(cents = 500L, categoryId = "food", kind = CategoryType.EXPENSE, note = "Lunch")
        val group = Transaction(cents = 1000L, categoryId = "group_expense", kind = CategoryType.GROUP,
            spendingCategoryId = "food", shares = listOf(Share(alice, 500L, listOf(repayment))))
        val moved = reassignCategoryReferences(listOf(expense, group), "food", "groceries")
        assertEquals("groceries", moved[0].categoryId)
        assertEquals("groceries", moved[1].spendingCategoryId)
        assertEquals("group_expense", moved[1].categoryId)
        assertEquals("Lunch", moved[0].note)
        assertEquals(listOf(repayment), moved[1].shares.single().repayments)
        assertEquals(listOf(expense.id, group.id), moved.map { it.id })
    }
    @Test fun csvEscapesQuotesAndFormulaInjection() {
        assertEquals("\"' =1+1\"", csvCell(" =1+1"))
        assertEquals("\"a\"\"b\"", csvCell("a\"b"))
    }
}
