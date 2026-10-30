package com.example.blackslate

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import java.io.File
import com.example.blackslate.ui.theme.BlackSlateTheme

class UiRefinementTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var store: FinanceStore
    private lateinit var storage: String
    private val alice = newPerson("Alice")
    private val bob = newPerson("Bob")
    @Before fun setup() { storage = "ui_refinement_" + System.nanoTime(); store = FinanceStore(context, storage) }
    @After fun cleanup() { context.getSharedPreferences(storage, Context.MODE_PRIVATE).edit().clear().commit() }
    private fun tap(label: String) {
        val node = compose.onNodeWithText(label)
        runCatching { node.performScrollTo() }
        node.performClick()
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val folder = File(context.getExternalFilesDir(null), "verification").apply { mkdirs() }
        File(folder, name + ".png").outputStream().use { output ->
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
        }
    }
    @Test fun noteDoneAndBackRestoreTheNumberPad() {
        compose.setContent { BlackSlateApp(store) }
        compose.onNodeWithContentDescription("Add note").performClick().performTextInput("Lunch")
        compose.onNodeWithTag("key_1").assertDoesNotExist()
        capture("note-keyboard")
        compose.onNodeWithContentDescription("Add note").performImeAction()
        compose.onNodeWithTag("key_1").assertIsDisplayed()
        compose.onNodeWithContentDescription("Add note").performClick()
        compose.onNodeWithTag("key_1").assertDoesNotExist()
        Espresso.pressBack()
        compose.waitUntil(4000) { compose.onAllNodesWithTag("key_1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("key_1").assertIsDisplayed()
    }
    @Test fun partsAndExactSplitsCanBeSaved() {
        var saved = emptyList<Share>()
        compose.setContent { BlackSlateTheme(Appearance.SILVER) {
            ParticipantSheet(FinanceSnapshot().categories.last(), 30000, Currency.INR, listOf(alice, bob),
                listOf(FinanceSnapshot().categories.first()), {}, { shares, _ -> saved = shares })
        } }
        tap("Alice"); tap("Bob"); tap("Parts")
        compose.onNodeWithTag("splitValue_${alice.id}").performScrollTo().performTextReplacement("2")
        capture("parts-split")
        tap("Save group expense")
        compose.runOnIdle { assertEquals(listOf(15000L, 7500L), saved.map { it.cents }) }
        tap("Amounts")
        compose.onNodeWithTag("splitValue_$SELF_SPLIT_ID").performScrollTo().performTextReplacement("50")
        compose.onNodeWithTag("splitValue_${alice.id}").performScrollTo().performTextReplacement("100")
        compose.onNodeWithTag("splitValue_${bob.id}").performScrollTo().performTextReplacement("150")
        tap("Save group expense")
        compose.runOnIdle { assertEquals(listOf(10000L, 15000L), saved.map { it.cents }) }
    }
    @Test fun settleAllRecordsOnlyThisPersonsBalance() {
        store.log(Transaction(cents = 5000, categoryId = "lending", kind = CategoryType.LENDING, shares = listOf(Share(alice, 5000))))
        store.log(Transaction(cents = 9000, categoryId = "group_expense", kind = CategoryType.GROUP,
            shares = listOf(Share(alice, 3000), Share(bob, 3000))))
        compose.setContent { BlackSlateTheme(Appearance.SILVER) {
            PersonScreen(store.snapshot, alice.id, {}, {}, { currency, method -> store.settlePerson(alice.id, currency, method) }, { _, _, _ -> })
        } }
        tap("Settle all"); tap("Confirm received")
        compose.runOnIdle {
            assertEquals(0L, store.snapshot.transactions.flatMap { it.shares }.filter { it.person.id == alice.id }.sumOf { it.outstanding })
            assertEquals(3000L, store.snapshot.transactions.flatMap { it.shares }.single { it.person.id == bob.id }.outstanding)
            assertEquals(store.snapshot, FinanceStore(context, storage).snapshot)
        }
    }
    @Test fun editorKeepsExactSharesAndLocationWithoutLocationInputs() {
        val tx = Transaction(cents = 30000, categoryId = "group_expense", kind = CategoryType.GROUP,
            shares = listOf(Share(alice, 10000), Share(bob, 15000)), location = "12,77", placeName = "Home")
        var saved: Transaction? = null
        compose.setContent { BlackSlateTheme(Appearance.SILVER) {
            TransactionSheet(tx, FinanceSnapshot(transactions = listOf(tx)), {}, updateNote = {}, updateTransaction = { saved = it },
                repay = { _, _, _ -> }, settle = {}, delete = {}, map = {})
        } }
        compose.onNodeWithContentDescription("Edit transaction").performClick()
        compose.onNodeWithText("Map coordinates · optional").assertDoesNotExist()
        compose.onNodeWithText("Area name · optional").assertDoesNotExist()
        compose.onNodeWithText("Category · Group Expense").assertDoesNotExist()
        tap("Save changes")
        compose.runOnIdle { assertEquals(tx, saved) }
    }
    @Test fun draggingCategoriesPersistsTheirOrder() {
        compose.setContent { BlackSlateTheme(Appearance.SILVER) {
            CategoriesScreen(store.snapshot, {}, {}, { _, _ -> true }, { _, _ -> true }, { store.reorderCategories(it) })
        } }
        val source = compose.onNodeWithTag("reorder_food")
        val target = compose.onNodeWithTag("reorder_lending")
        val distance = target.fetchSemanticsNode().boundsInRoot.center.y - source.fetchSemanticsNode().boundsInRoot.center.y
        source.performTouchInput { down(center); moveBy(Offset(0f, 30f), delayMillis = 100) }
        compose.waitForIdle()
        source.performTouchInput { moveBy(Offset(0f, distance - 30f), delayMillis = 300) }
        compose.waitForIdle()
        source.performTouchInput { up() }
        compose.runOnIdle {
            assertEquals(listOf("lending", "food", "group_expense"), store.snapshot.categories.map { it.id })
            assertEquals(store.snapshot.categories, FinanceStore(context, storage).snapshot.categories)
        }
    }
    @Test fun contactSearchOpensFocused() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName, android.Manifest.permission.READ_CONTACTS)
        compose.setContent { BlackSlateTheme(Appearance.SILVER) { ContactSearchSheet({}, {}) } }
        compose.onNodeWithTag("contactSearch").assertIsFocused().performTextInput("Search test")
        capture("contact-search")
    }
    @Test fun largeSettlementAmountsRenderInsideTheCard() {
        val tx = Transaction(cents = 9999999999, categoryId = "group_expense", kind = CategoryType.GROUP,
            note = "Dinner and a shared trip", shares = listOf(Share(alice, 853867)))
        val card = SettlementCardRenderer.render(context, alice, mapOf(Currency.INR to 853867), listOf(tx), Appearance.SILVER)
        assertNotNull(card)
        val output = File(context.getExternalFilesDir(null), "verification").apply { mkdirs() }
        card!!.copyTo(File(output, "settlement-card.png"), overwrite = true)
    }
}
