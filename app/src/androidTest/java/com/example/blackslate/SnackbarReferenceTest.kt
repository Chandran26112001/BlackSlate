package com.example.blackslate

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.blackslate.ui.theme.BlackSlateTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SnackbarReferenceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun referenceVariantsKeepTheirWordingAndActions() {
        var show by mutableStateOf(false)
        val undoKeys = mutableListOf<String>()
        val editKeys = mutableListOf<String>()
        val success = Notice(text = "₹23 to cat1", transactionId = "transaction", amountText = "₹23", targetText = "to cat1")
        val discard = Notice(text = "Draft discarded", icon = NoticeIcon.TRASH, undoKey = "draft")
        compose.setContent {
            Column(Modifier.fillMaxSize()) {
                for (appearance in listOf(Appearance.SILVER, Appearance.MATTE)) {
                    BlackSlateTheme(appearance) {
                        Column(Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.background)
                            .padding(horizontal = 16.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(35.dp)) {
                            Text(if (appearance == Appearance.SILVER) "LIGHT" else "DARK", color = MaterialTheme.colorScheme.onBackground)
                            NoticeBar(if (show) success else null, false, {}, { editKeys.add(it) }, { undoKeys.add(it) })
                            NoticeBar(if (show) discard else null, false, {}, {}, { undoKeys.add(it) })
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { show = true }
        compose.mainClock.advanceTimeBy(48)
        compose.onAllNodesWithText("Draft discarded").assertCountEquals(2)
        compose.onNodeWithText("Amount and note cleared").assertDoesNotExist()
        compose.onAllNodesWithText("₹23  to cat1").assertCountEquals(2)
        compose.onAllNodesWithTag("snackbarProgress").assertCountEquals(4)
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.getExternalFilesDir(null), "verification").apply { mkdirs() }
        File(folder, "snackbar-reference-variants.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        compose.onAllNodesWithText("Undo")[1].performClick()
        compose.onAllNodesWithContentDescription("Edit note")[0].performClick()
        compose.runOnUiThread {
            assertEquals(listOf("draft"), undoKeys)
            assertEquals(listOf("transaction"), editKeys)
        }
        compose.mainClock.autoAdvance = true
        compose.waitUntil(5000) { compose.onAllNodesWithTag("snackbar").fetchSemanticsNodes().isEmpty() }
    }
}
