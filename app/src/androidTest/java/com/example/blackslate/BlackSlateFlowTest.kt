package com.example.blackslate

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso
import org.junit.*
import org.junit.Assert.*

class BlackSlateFlowTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var context: Context
    private lateinit var store: FinanceStore
    private lateinit var storageName: String
    @Before fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        storageName = "black_slate_test_" + System.nanoTime()
        store = FinanceStore(context, storageName)
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName, android.Manifest.permission.ACCESS_COARSE_LOCATION)
    }
    @After fun cleanup() { context.getSharedPreferences(storageName, Context.MODE_PRIVATE).edit().clear().commit() }
    private fun start() { compose.setContent { BlackSlateApp(store) } }
    private fun tap(text: String) {
        val node = compose.onNodeWithText(text, useUnmergedTree = false)
        runCatching { node.performScrollTo() }
        node.performClick()
    }

    @Test fun persistenceMigrationAndCsvKeepRepayments() {
        val legacy = """{"categories":[{"id":"lending","name":"Lending","type":"LENDING","accent":"TEAL","isDefault":true}],"transactions":[{"id":"old","amount":42.35,"categoryId":"lending","note":"old record","contactName":"Alice","contactPhone":"123","location":"12,77","createdAt":100}],"settings":{"currency":"INR"}}"""
        val migrated = decodeSnapshot(legacy)
        assertEquals(4235L, migrated.transactions.single().cents)
        assertEquals(4235L, migrated.transactions.single().outstanding)
        assertEquals(migrated, decodeSnapshot(encodeSnapshot(migrated)))
        val tx = migrated.transactions.single()
        store.log(tx)
        store.repay(tx.id, tx.shares.single().person.id, 1000, "Cash")
        store.updateSettings { it.copy(currency = Currency.USD) }
        val reopened = FinanceStore(context, storageName)
        assertEquals(3235L, reopened.snapshot.transactions.single().outstanding)
        assertEquals(Currency.INR, reopened.snapshot.transactions.single().currency)
        val csv = reopened.exportCsv().readText()
        assertTrue(csv.contains("repayment")); assertTrue(csv.contains("Cash")); assertTrue(csv.contains("Alice"))
        reopened.settle(tx.id, "UPI")
        reopened.settle(tx.id, "UPI")
        assertEquals(2, reopened.snapshot.transactions.single().shares.single().repayments.size)
    }
    @Test fun backNavigationAndSilverPersist() {
        start()
        tap("Settings")
        tap("Silver")
        compose.runOnIdle { assertEquals(Appearance.SILVER, store.snapshot.settings.appearance) }
        compose.onNodeWithText("Manage categories").performScrollTo().performClick()
        compose.onNodeWithText("Categories").assertIsDisplayed()
        Espresso.pressBack()
        compose.onNodeWithText("Settings").assertIsDisplayed()
        Espresso.pressBack()
        compose.onNodeWithTag("amountDrag").assertIsDisplayed()
        compose.onNodeWithTag("dropCategories").assertDoesNotExist()
    }
    @Test fun groupWithTwoManualPeopleThenPartialAndFullRepayment() {
        start()
        listOf("3", "0", "0").forEach { compose.onNodeWithTag("key_"+it).performClick() }
        tap("Choose category")
        tap("Group Expense")
        compose.onNodeWithTag("personName").performTextInput("Alice")
        tap("Add person")
        compose.onNodeWithTag("personName").performScrollTo().performTextInput("Bob")
        tap("Add person")
        tap("Save group expense")
        compose.waitUntil(12000) { store.snapshot.transactions.size == 1 }
        compose.runOnIdle {
            val tx = store.snapshot.transactions.single()
            assertEquals(2, tx.shares.size); assertEquals(10000L, tx.personalCost); assertEquals(20000L, tx.outstanding)
        }
        tap("↗  Insights")
        tap("Peer ledger")
        compose.onNodeWithTag("person_Alice").performScrollTo().performClick()
        compose.onNodeWithText("Group share · ₹100").performScrollTo().performClick()
        tap("Record repayment · Alice")
        compose.onNodeWithTag("repaymentAmount").performTextReplacement("40")
        tap("Record received")
        compose.runOnIdle { assertEquals(16000L, store.snapshot.transactions.single().outstanding) }
        tap("Settle remaining group balance")
        tap("Confirm settlement")
        compose.runOnIdle {
            val tx = store.snapshot.transactions.single()
            assertEquals(0L, tx.outstanding)
            assertEquals(2, tx.shares.first { it.person.name == "Alice" }.repayments.size)
            assertEquals(10000L, tx.personalCost)
        }
        tap("Close")
        compose.onNodeWithText("Settled ✓").assertIsDisplayed()
    }
    @Test fun snackbarClosesAndExpires() {
        start()
        tap("Choose category")
        compose.onNodeWithTag("snackbar").assertIsDisplayed()
        compose.onNodeWithTag("closeSnackbar").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("snackbar").assertDoesNotExist()
        tap("Choose category")
        compose.onNodeWithTag("snackbar").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(3400)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithTag("snackbar").assertDoesNotExist()
    }
}
