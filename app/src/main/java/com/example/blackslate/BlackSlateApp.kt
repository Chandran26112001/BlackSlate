package com.example.blackslate

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.blackslate.ui.theme.BlackSlateTheme

private const val DRAFT_UNDO_KEY = "__draft__"
private data class DraftUndo(val amount: String, val note: String)

@Composable fun BlackSlateApp(store: FinanceStore, onLockChange: ((Boolean) -> Unit)? = null, assistantLogOnly: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val snapshot = store.snapshot
    val settings = snapshot.settings
    val thisMonth = monthWindow(0)
    val monthlySpending = snapshot.transactions.filter {
        it.currency == settings.currency && it.createdAt in thisMonth.first until thisMonth.second
    }.sumOf { it.personalCost }
    val feedback = remember { TouchFeedback(context) { store.snapshot.settings.haptics } }
    var route by rememberSaveable { mutableStateOf("home") }
    var ledger by rememberSaveable { mutableStateOf(false) }
    var draggingHome by remember { mutableStateOf(false) }
    var personId by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var choose by rememberSaveable { mutableStateOf(false) }
    var categoryDraft by rememberSaveable { mutableStateOf<String?>(null) }
    var newCategory by rememberSaveable { mutableStateOf(false) }
    var createForAmount by rememberSaveable { mutableStateOf(false) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var editNoteId by rememberSaveable { mutableStateOf<String?>(null) }
    var draftPhotoRaw by rememberSaveable { mutableStateOf("[]") }
    var cameraTempPath by rememberSaveable { mutableStateOf<String?>(null) }
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    var locationReady by rememberSaveable { mutableStateOf(false) }
    var notice by remember { mutableStateOf<Notice?>(null) }
    var discardedDraft by remember { mutableStateOf<DraftUndo?>(null) }
    var pendingImport by remember { mutableStateOf<CsvImportPlan?>(null) }
    var assistantEnabled by remember { mutableStateOf(isBlackSlateAssistant(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) assistantEnabled = isBlackSlateAssistant(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    fun draftPhotos(): List<String> = JSONArray(draftPhotoRaw).let { array -> (0 until array.length()).map { array.getString(it) } }
    fun addPhotos(uris: List<String>) {
        val all = (draftPhotos() + uris).distinct()
        val combined = all.take(12)
        draftPhotoRaw = JSONArray(combined).toString()
        if (all.size > 12) notice = Notice(text = "Up to 12 photos per transaction")
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { selected ->
        addPhotos(selected.map { it.toString() })
        val kept = draftPhotos()
        selected.filter { it.toString() in kept }.forEach { uri ->
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
    }
    val csvImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader -> store.prepareCsvImport(reader.readText()) }
                        ?: throw IllegalArgumentException("Unable to read this file.")
                }
            }
            result.onSuccess { pendingImport = it }.onFailure { notice = Notice(text = it.message ?: "Could not read this CSV.") }
        }
    }
    val assistantRole = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        assistantEnabled = isBlackSlateAssistant(context)
        notice = Notice(text = if (assistantEnabled) "Power-button launch enabled" else "Black Slate was not selected as Assistant")
    }
    fun openAssistantSettings(): Boolean = listOf(Settings.ACTION_VOICE_INPUT_SETTINGS,
        Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).any { action ->
        runCatching { context.startActivity(Intent(action)); true }.getOrDefault(false)
    }
    fun enableAssistant() {
        if (openAssistantSettings()) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            notice = Notice(text = "Assistant settings are unavailable on this device.")
            return
        }
        val manager = context.getSystemService(RoleManager::class.java)
        if (manager == null || !manager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
            notice = Notice(text = "Assistant selection is unavailable on this device.")
        } else if (manager.isRoleHeld(RoleManager.ROLE_ASSISTANT)) {
            assistantEnabled = true
        } else assistantRole.launch(manager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT))
    }
    fun manageAssistant() {
        if (!openAssistantSettings()) notice = Notice(text = "Assistant settings are unavailable on this device.")
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        cameraTempPath?.let { path ->
            val uri = PhotoStorage.cameraUri(context, File(path)).toString()
            if (captured) addPhotos(listOf(uri))
            else PhotoStorage.discardCameraDrafts(context, listOf(uri))
        }
        cameraTempPath = null
    }
    fun takePhoto() = runCatching {
        val file = PhotoStorage.cameraFile(context)
        cameraTempPath = file.absolutePath
        camera.launch(PhotoStorage.cameraUri(context, file))
    }.onFailure {
        cameraTempPath?.let { path ->
            runCatching { PhotoStorage.discardCameraDrafts(context, listOf(PhotoStorage.cameraUri(context, File(path)).toString())) }
        }
        cameraTempPath = null
        notice = Notice(text = "Camera unavailable")
    }.let { Unit }
    fun notify(text: String, id: String? = null) { notice = Notice(text = text, transactionId = id) }
    fun safely(action: () -> Unit) { runCatching(action).onFailure { notify(it.message ?: "Unable to save. Please try again.") } }
    fun back() { feedback.pulse(); route = when (route) { "categories" -> "settings"; "person" -> "insights"; else -> "home" } }
    BackHandler(enabled = route != "home" && detailId == null && categoryDraft == null && !choose && !newCategory) { back() }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { locationReady = true }
    fun submit(tx: Transaction) {
        keyboard?.hide()
        focus.clearFocus()
        pending = encodeSnapshot(FinanceSnapshot(transactions = listOf(tx)))
        categoryDraft = null
        choose = false
        if (!settings.saveLocation) {
            locationReady = true
        } else {
            locationReady = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (!locationReady) permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }
    fun begin(category: Category) {
        if (pending != null) return
        val cents = parseMoney(amount)
        if (cents == null) { notify("Enter an amount greater than zero first."); return }
        choose = false
        if (category.type == CategoryType.EXPENSE) submit(Transaction(cents = cents, categoryId = category.id, kind = category.type, note = note.trim(), currency = settings.currency))
        else categoryDraft = category.id
    }
    DisposableEffect(pending, locationReady, settings.saveLocation) {
        val raw = pending
        val cancel: () -> Unit = if (raw != null && locationReady) {
            val tx = decodeSnapshot(raw).transactions.single()
            fun persist(location: String?) {
                scope.launch {
                    var imported = ImportedPhotos(emptyList(), 0)
                    var saved = false
                    var cancelled = false
                    try {
                        val sources = draftPhotos()
                        imported = PhotoStorage.importSelected(context, sources)
                        val placeName = if (settings.saveLocation && location != null) withContext(Dispatchers.IO) {
                            areaNameForLocation(context, location)
                        } else null
                        if (store.snapshot.transactions.none { it.id == tx.id })
                            store.log(tx.copy(location = location, placeName = placeName, images = imported.paths))
                        saved = true
                        PhotoStorage.discardCameraDrafts(context, sources)
                        PhotoStorage.releaseDraftGrants(context, sources)
                        amount = ""; note = ""; draftPhotoRaw = "[]"
                        feedback.pulse(true)
                        val category = store.snapshot.categories.firstOrNull { it.id == tx.categoryId }?.name ?: "category"
                        val value = money(tx.cents, tx.currency)
                        val target = "to $category" + if (imported.skipped > 0) " · photos skipped" else ""
                        notice = Notice(text = "$value $target", transactionId = tx.id, amountText = value, targetText = target)
                    } catch (cancellation: CancellationException) {
                        cancelled = true
                        throw cancellation
                    } catch (error: Exception) {
                        notify(error.message ?: "Could not save transaction")
                    } finally {
                        if (!saved) PhotoStorage.delete(context, imported.paths)
                        if (!cancelled) {
                            pending = null
                            locationReady = false
                        }
                    }
                }
            }
            if (settings.saveLocation) captureLocation(context, ::persist) else {
                persist(null)
                val noCancellation: () -> Unit = {}
                noCancellation
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
        CompositionLocalProvider(LocalFeedback provides { feedback.pulse() },
            LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surfaceVariant)))) {
                Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Box(Modifier.weight(1f)) {
                        when (route) {
                            "home" -> HomeCanvas(snapshot, amount, note, draftPhotos().size, pending != null,
                                { amount = it }, { note = it }, ::begin,
                                { if (parseMoney(amount) == null) notify("Enter an amount greater than zero first.") else choose = true },
                                { if (assistantLogOnly) notify("Unlock your phone to open settings") else route = "settings" }, { createForAmount = true; newCategory = true },
                                { if (settings.allowPhotoAttachments) takePhoto() else notify("Photo attachments are disabled in Privacy") }, { if (settings.allowPhotoAttachments) gallery.launch(arrayOf("image/*")) else notify("Photo attachments are disabled in Privacy") }, {
                                    PhotoStorage.discardCameraDrafts(context, draftPhotos())
                                    PhotoStorage.releaseDraftGrants(context, draftPhotos())
                                    draftPhotoRaw = "[]"
                                }, { draggingHome = it }, {
                                    discardedDraft = DraftUndo(amount = amount, note = note)
                                    PhotoStorage.discardCameraDrafts(context, draftPhotos())
                                    PhotoStorage.releaseDraftGrants(context, draftPhotos())
                                    amount = ""; note = ""; draftPhotoRaw = "[]"; choose = false; categoryDraft = null
                                    notice = Notice(text = "Draft discarded", icon = NoticeIcon.TRASH, undoKey = DRAFT_UNDO_KEY)
                                }, monthlySpending, settings.monthlyBudgetCents, assistantLogOnly)
                            "settings" -> SettingsScreen(snapshot.settings, { back() }, { transform -> safely { store.updateSettings(transform) } },
                                { route = "categories" }, ::export, { csvImport.launch(arrayOf("text/csv", "text/*", "application/csv")) }, assistantEnabled, ::enableAssistant, ::manageAssistant, { enabled ->
                                    val manager = context.getSystemService(android.app.KeyguardManager::class.java)
                                    if (enabled && !manager.isDeviceSecure) notify("Set a device PIN or password in Android settings first.")
                                    else safely { if (onLockChange != null) onLockChange(enabled) else store.updateSettings { it.copy(appLock = enabled) } }
                                }, { feedback.pulse(true) })
                            "categories" -> CategoriesScreen(snapshot, { back() }, { createForAmount = false; newCategory = true },
                                { category, name ->
                                    runCatching { store.renameCategory(category, name) }
                                        .onSuccess { notify("Category updated") }
                                        .onFailure { notify(it.message ?: "Could not update category") }.isSuccess
                                }, { category, replacementId ->
                                    runCatching { store.deleteCategory(category, replacementId) }
                                        .onSuccess { notify("Category deleted") }
                                        .onFailure { notify(it.message ?: "Could not delete category") }.isSuccess
                                }, { ids -> safely { store.reorderCategories(ids) } })
                            "insights" -> InsightsScreen(snapshot, ledger, { ledger = it }, { personId = it; route = "person" }, { detailId = it })
                            "person" -> PersonScreen(snapshot, personId, { back() }, { detailId = it }, { currency, method ->
                                safely { store.settlePerson(personId, currency, method); feedback.pulse(true); notify("All transactions settled") }
                            }) { person, balances, transactions ->
                                if (shareSettlementCard(context, person, balances, transactions, settings.appearance)) {
                                    safely { store.markReminderSent(person.id) }
                                    notify("Settlement card ready to share")
                                } else notify("There is no balance to share.")
                            }
                        }
                    }
                    if (!assistantLogOnly && (route == "home" || route == "insights") && !draggingHome) {
                        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Action("◈  Log", Modifier.weight(1f), secondary = route != "home", onClick = { route = "home" })
                            Action("↗  Insights", Modifier.weight(1f), secondary = route != "insights", onClick = { route = "insights" })
                        }
                    }
                }
                NoticeBar(notice, settings.liquidPhysics, { notice = null }, { id ->
                    if (assistantLogOnly) notify("Notes can be edited after unlock") else { editNoteId = id; detailId = id }
                },
                    { id ->
                        if (id == DRAFT_UNDO_KEY) {
                            discardedDraft?.let { draft ->
                                amount = draft.amount
                                note = draft.note
                            }
                            discardedDraft = null
                        } else safely {
                            val images = store.snapshot.transactions.firstOrNull { it.id == id }?.images.orEmpty()
                            store.deleteTransaction(id)
                            PhotoStorage.delete(context, images)
                            notify("Transaction removed")
                        }
                    },
                    Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 16.dp, end = 16.dp,
                        bottom = if (route == "home" || route == "insights") 80.dp else 16.dp))
                if (choose) Sheet("Log " + money(parseMoney(amount) ?: 0, settings.currency), { choose = false }) {
                    snapshot.categories.forEach { category -> Action(category.name, Modifier.fillMaxWidth(), secondary = true, onClick = { begin(category) }) }
                    Action("+ New category", Modifier.fillMaxWidth(), onClick = {
                        choose = false; createForAmount = true; newCategory = true
                    })
                }
                snapshot.categories.firstOrNull { it.id == categoryDraft }?.let { category ->
                    ParticipantSheet(category, parseMoney(amount) ?: 0, settings.currency, snapshot.transactions.flatMap { it.shares }.map { it.person }.distinctBy { it.id },
                        snapshot.categories.filter { it.type == CategoryType.EXPENSE }, { categoryDraft = null }, { shares, spendingCategoryId ->
                            submit(Transaction(cents = parseMoney(amount) ?: 0, categoryId = category.id, kind = category.type, note = note.trim(), currency = settings.currency,
                                shares = shares, spendingCategoryId = spendingCategoryId))
                        })
                }
                if (newCategory) CategorySheet({ newCategory = false; createForAmount = false }) { name ->
                    safely {
                        val category = store.addCategory(name)
                        val shouldLog = createForAmount && parseMoney(amount) != null
                        newCategory = false
                        createForAmount = false
                        if (shouldLog) begin(category) else notify("Category created")
                    }
                }
                pendingImport?.let { plan ->
                    Sheet("Restore from CSV", { pendingImport = null }) {
                        Caption("${plan.transactions.size} transaction${if (plan.transactions.size == 1) "" else "s"} found. Existing transaction IDs are skipped, so current records stay safe.")
                        if (plan.duplicateCount > 0) Caption("${plan.duplicateCount} already on this device")
                        if (plan.photoCount > 0) Caption("${plan.photoCount} photo${if (plan.photoCount == 1) "" else "s"} cannot be restored from CSV.")
                        Action("Import ${plan.transactions.size - plan.duplicateCount} transactions", Modifier.fillMaxWidth(),
                            enabled = plan.transactions.size > plan.duplicateCount) {
                            safely {
                                val result = store.importCsv(plan)
                                pendingImport = null
                                notify("Imported ${result.imported} transactions" + if (result.categoriesAdded > 0) " · ${result.categoriesAdded} categories added" else "")
                            }
                        }
                    }
                }
                snapshot.transactions.firstOrNull { it.id == detailId }?.let { tx ->
                    TransactionSheet(tx, snapshot, { detailId = null; editNoteId = null }, focusNote = editNoteId == tx.id,
                        { edited -> safely { store.updateNote(tx.id, edited); notify("Note updated") } },
                        { edited -> safely { store.updateTransaction(edited); notify("Transaction updated") } },
                        { id, cents, method -> safely { store.repay(tx.id, id, cents, method); feedback.pulse(true); notify("Repayment recorded") } },
                        { method -> safely { store.settle(tx.id, method); feedback.pulse(true); notify("Transaction settled") } },
                        { safely { store.deleteTransaction(tx.id); PhotoStorage.delete(context, tx.images); detailId = null; notify("Transaction deleted") } },
                        { if (!openMap(context, tx.location.orEmpty())) notify("No map application is available.") })
                }
            }
        }
    }
}
