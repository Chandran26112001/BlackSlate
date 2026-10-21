package com.example.blackslate

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.geometry.Offset
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
    @Test fun newExpenseCategoryLogsCurrentAmount() {
        start()
        listOf("1", "0", "0").forEach { compose.onNodeWithTag("key_" + it).performClick() }
        tap("Choose category")
        tap("+ New category")
        compose.onNodeWithTag("newCategoryName").performTextInput("Groceries")
        tap("Create expense category")
        compose.waitUntil(12000) { store.snapshot.transactions.size == 1 }
        compose.runOnIdle {
            val category = store.snapshot.categories.single { it.name == "Groceries" }
            assertEquals(CategoryType.EXPENSE, category.type)
            assertEquals(category.id, store.snapshot.transactions.single().categoryId)
            assertEquals(10000L, store.snapshot.transactions.single().cents)
        }
    }
    @Test fun categoryDeletionRequiresReplacementAndKeepsHistory() {
        val food = store.snapshot.categories.first { it.id == "food" }
        val groceries = store.addCategory("Groceries")
        val original = Transaction(cents = 25000, categoryId = food.id, kind = CategoryType.EXPENSE, note = "Dinner")
        val shared = Transaction(cents = 50000, categoryId = "group_expense", kind = CategoryType.GROUP,
            spendingCategoryId = food.id, shares = listOf(Share(newPerson("Alice"), 25000)))
        store.log(original)
        store.log(shared)
        store.renameCategory(food, "Meals")
        assertEquals("Meals", store.snapshot.categories.first { it.id == food.id }.name)
        assertTrue(runCatching { store.deleteCategory(food) }.isFailure)
        store.deleteCategory(food, groceries.id)
        val reopened = FinanceStore(context, storageName).snapshot
        assertTrue(reopened.categories.none { it.id == food.id })
        assertEquals(groceries.id, reopened.transactions.first { it.id == original.id }.categoryId)
        val keptShared = reopened.transactions.first { it.id == shared.id }
        assertEquals("group_expense", keptShared.categoryId)
        assertEquals(groceries.id, keptShared.spendingCategoryId)
        assertEquals(25000L, keptShared.outstanding)
        assertEquals("Dinner", reopened.transactions.first { it.id == original.id }.note)
        assertTrue(runCatching { store.deleteCategory(store.snapshot.categories.first { it.id == "lending" }, groceries.id) }.isFailure)
    }
    @Test fun directDragToFoodLogsWithoutHolding() {
        start()
        listOf("1", "0", "0").forEach { compose.onNodeWithTag("key_" + it).performClick() }
        val home = compose.onNodeWithTag("homeCanvas")
        val homeBounds = home.fetchSemanticsNode().boundsInRoot
        val start = compose.onNodeWithTag("amountDrag").fetchSemanticsNode().boundsInRoot.center - homeBounds.topLeft
        home.performTouchInput {
            down(start)
            moveBy(Offset(0f, 36f))
            advanceEventTime(96)
        }
        compose.onNodeWithTag("dropCategories").assertIsDisplayed()
        val food = compose.onNodeWithTag("drop_food").fetchSemanticsNode().boundsInRoot.center - homeBounds.topLeft
        home.performTouchInput {
            moveTo(food)
            advanceEventTime(96)
        }
        compose.onNodeWithTag("drop_food").assertIsSelected()
        home.performTouchInput {
            up()
        }
        compose.waitUntil(12000) { store.snapshot.transactions.isNotEmpty() }
        compose.runOnIdle {
            assertEquals("food", store.snapshot.transactions.single().categoryId)
            assertEquals(10000L, store.snapshot.transactions.single().cents)
        }
    }
    @Test fun importedPhotosSurviveStoreRoundTrip() {
        val cameraFile = PhotoStorage.cameraFile(context)
        android.graphics.Bitmap.createBitmap(4, 4, android.graphics.Bitmap.Config.ARGB_8888).useBitmap { bitmap ->
            cameraFile.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        val uri = PhotoStorage.cameraUri(context, cameraFile).toString()
        val result = kotlinx.coroutines.runBlocking { PhotoStorage.importSelected(context, listOf(uri, uri)) }
        try {
            assertEquals(2, result.paths.size)
            assertEquals(0, result.skipped)
            assertNotNull(PhotoStorage.bitmap(context, result.paths.first()))
            val tx = Transaction(cents = 100, categoryId = "food", kind = CategoryType.EXPENSE, images = result.paths)
            store.log(tx)
            assertEquals(2, FinanceStore(context, storageName).snapshot.transactions.single().images.size)
            assertTrue(store.exportCsv().readText().contains("image_count"))
        } finally {
            PhotoStorage.delete(context, result.paths)
            PhotoStorage.discardCameraDrafts(context, listOf(uri))
        }
    }
    @Test fun csvRestorePreservesTransactionsPeopleRepaymentsAndLocation() {
        val travel = store.addCategory("Travel")
        val alice = newPerson("Alice", "+91 555")
        val lending = Transaction(cents = 12500, categoryId = "lending", kind = CategoryType.LENDING, note = "Airport cash",
            location = "12.9716,77.5946", createdAt = 1_700_000_000_000L, shares = listOf(Share(alice, 12500, listOf(Repayment(2500, 1_700_000_001_000L, "UPI")))))
        val expense = Transaction(cents = 9999, categoryId = travel.id, kind = CategoryType.EXPENSE, note = "Weekend train", createdAt = 1_700_000_002_000L)
        store.log(lending); store.log(expense)
        val csv = store.exportCsv().readText()
        val targetName = storageName + "_restored"
        val target = FinanceStore(context, targetName)
        try {
            val plan = target.prepareCsvImport(csv)
            assertEquals(2, plan.transactions.size)
            assertEquals(0, plan.duplicateCount)
            assertEquals(2, target.importCsv(plan).imported)
            val restored = target.snapshot
            assertEquals(2, restored.transactions.size)
            val restoredLending = restored.transactions.single { it.id == lending.id }
            assertEquals(lending.note, restoredLending.note)
            assertEquals(lending.location, restoredLending.location)
            assertEquals(10000L, restoredLending.outstanding)
            assertEquals("Alice", restoredLending.shares.single().person.name)
            assertEquals("UPI", restoredLending.shares.single().repayments.single().method)
            assertEquals(CategoryType.EXPENSE, restored.categories.single { it.name == "Travel" }.type)
        } finally {
            context.getSharedPreferences(targetName, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
    @Test fun assistantLogModeHidesInsightsAndSettings() {
        compose.setContent { BlackSlateApp(store, assistantLogOnly = true) }
        compose.onNodeWithTag("amountDrag").assertIsDisplayed()
        compose.onNodeWithText("↗  Insights").assertDoesNotExist()
        compose.onNodeWithText("Settings").assertDoesNotExist()
    }
}
private inline fun android.graphics.Bitmap.useBitmap(block: (android.graphics.Bitmap) -> Unit) {
    try { block(this) } finally { recycle() }
}
