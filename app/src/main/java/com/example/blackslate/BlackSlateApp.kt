package com.example.blackslate

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import com.example.blackslate.ui.theme.BlackSlateTheme

@Composable fun BlackSlateApp(store: FinanceStore, onLockChange: ((Boolean) -> Unit)? = null) {
    val context = LocalContext.current
    val view = LocalView.current
    val snapshot = store.snapshot
    val settings = snapshot.settings
    val feedback = remember { TouchFeedback(context) { store.snapshot.settings.haptics } }
    var route by rememberSaveable { mutableStateOf("home") }
    var ledger by rememberSaveable { mutableStateOf(false) }
    var personId by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var choose by rememberSaveable { mutableStateOf(false) }
    var categoryDraft by rememberSaveable { mutableStateOf<String?>(null) }
    var newCategory by rememberSaveable { mutableStateOf(false) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    var locationReady by rememberSaveable { mutableStateOf(false) }
    var notice by remember { mutableStateOf<Notice?>(null) }
    fun notify(text: String, id: String? = null) { notice = Notice(text = text, transactionId = id) }
    fun safely(action: () -> Unit) { runCatching(action).onFailure { notify(it.message ?: "Unable to save. Please try again.") } }
    fun back() { feedback.pulse(); route = when (route) { "categories" -> "settings"; "person" -> "insights"; else -> "home" } }
    BackHandler(enabled = route != "home" && detailId == null && categoryDraft == null && !choose && !newCategory) { back() }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { locationReady = true }
    fun submit(tx: Transaction) {
        pending = encodeSnapshot(FinanceSnapshot(transactions = listOf(tx)))
        categoryDraft = null
        choose = false
        locationReady = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!locationReady) permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    fun begin(category: Category) {
        if (pending != null) return
        val cents = parseMoney(amount)
        if (cents == null) { notify("Enter an amount greater than zero first."); return }
        choose = false
        if (category.type == CategoryType.EXPENSE) submit(Transaction(cents = cents, categoryId = category.id, kind = category.type, note = note.trim(), currency = settings.currency))
        else categoryDraft = category.id
    }
    DisposableEffect(pending, locationReady) {
        val raw = pending
        val cancel = if (raw != null && locationReady) {
            val tx = decodeSnapshot(raw).transactions.single()
            captureLocation(context) { location ->
                safely {
                    if (store.snapshot.transactions.none { it.id == tx.id }) store.log(tx.copy(location = location))
                    amount = ""; note = ""
                    feedback.pulse(true)
                    notify(if (location == null) "Saved · location unavailable" else "Saved · " + dateLabel(tx.createdAt, "h:mm a"), tx.id)
                }
                pending = null
                locationReady = false
            }
        } else ({})
        onDispose { cancel() }
    }
    fun export() = safely {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", store.exportCsv())
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Export Black Slate"))
    }
    BlackSlateTheme(settings.appearance) {
        val light = MaterialTheme.colorScheme.background.luminance() > .5f
        SideEffect {
            (context as? Activity)?.window?.let {
                WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = light
                WindowCompat.getInsetsController(it, view).isAppearanceLightNavigationBars = light
            }
        }
        CompositionLocalProvider(LocalFeedback provides { feedback.pulse() }) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surfaceVariant)))) {
                Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Box(Modifier.weight(1f)) {
                        when (route) {
                            "home" -> HomeCanvas(snapshot, amount, note, pending != null, { amount = it }, { note = it }, ::begin,
                                { if (parseMoney(amount) == null) notify("Enter an amount greater than zero first.") else choose = true },
                                { route = "settings" }, { newCategory = true })
                            "settings" -> SettingsScreen(snapshot.settings, { back() }, { transform -> safely { store.updateSettings(transform) } },
                                { route = "categories" }, ::export, { enabled ->
                                    val manager = context.getSystemService(android.app.KeyguardManager::class.java)
                                    if (enabled && !manager.isDeviceSecure) notify("Set a device PIN or password in Android settings first.")
                                    else safely { if (onLockChange != null) onLockChange(enabled) else store.updateSettings { it.copy(appLock = enabled) } }
                                }, { feedback.pulse(true) })
                            "categories" -> CategoriesScreen(snapshot, { back() }, { newCategory = true }, { category -> safely { store.deleteCategory(category) } })
                            "insights" -> InsightsScreen(snapshot, ledger, { ledger = it }, { personId = it; route = "person" }, { detailId = it })
                            "person" -> PersonScreen(snapshot, personId, { back() }, { detailId = it })
                        }
                    }
                    if (route == "home" || route == "insights") {
                        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Action("◈  Log", Modifier.weight(1f), secondary = route != "home", onClick = { route = "home" })
                            Action("↗  Insights", Modifier.weight(1f), secondary = route != "insights", onClick = { route = "insights" })
                        }
                    }
                }
                NoticeBar(notice, settings.liquidPhysics, { notice = null }, { detailId = it },
                    { id -> safely { store.deleteTransaction(id); notify("Transaction removed") } },
                    Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp))
                if (choose) Sheet("Log " + money(parseMoney(amount) ?: 0, settings.currency), { choose = false }) {
                    snapshot.categories.forEach { category -> Action(category.name, Modifier.fillMaxWidth(), secondary = true, onClick = { begin(category) }) }
                    Action("+ New category", Modifier.fillMaxWidth(), onClick = { choose = false; newCategory = true })
                }
                snapshot.categories.firstOrNull { it.id == categoryDraft }?.let { category ->
                    ParticipantSheet(category, parseMoney(amount) ?: 0, settings.currency, snapshot.transactions.flatMap { it.shares }.map { it.person }.distinctBy { it.id },
                        { categoryDraft = null }, { shares ->
                            submit(Transaction(cents = parseMoney(amount) ?: 0, categoryId = category.id, kind = category.type, note = note.trim(), currency = settings.currency, shares = shares))
                        })
                }
                if (newCategory) CategorySheet({ newCategory = false }) { name, type, accent ->
                    safely { store.addCategory(name, type, accent); newCategory = false; notify("Category created") }
                }
                snapshot.transactions.firstOrNull { it.id == detailId }?.let { tx ->
                    TransactionSheet(tx, snapshot, { detailId = null },
                        { edited -> safely { store.updateNote(tx.id, edited); notify("Note updated") } },
                        { id, cents, method -> safely { store.repay(tx.id, id, cents, method); feedback.pulse(true); notify("Repayment recorded") } },
                        { method -> safely { store.settle(tx.id, method); feedback.pulse(true); notify("Transaction settled") } },
                        { safely { store.deleteTransaction(tx.id); detailId = null; notify("Transaction deleted") } },
                        { if (!openMap(context, tx.location.orEmpty())) notify("No map application is available.") })
                }
            }
        }
    }
}
