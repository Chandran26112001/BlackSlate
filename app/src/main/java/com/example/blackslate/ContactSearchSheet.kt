package com.example.blackslate

import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable fun ContactSearchSheet(dismiss: () -> Unit, choose: (Person) -> Unit) {
    val context = LocalContext.current
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var search by rememberSaveable { mutableStateOf("") }
    var contacts by remember { mutableStateOf<List<Person>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        focus.requestFocus()
        keyboard?.show()
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val found = mutableListOf<Person>()
                context.contentResolver.query(Phone.CONTENT_URI,
                    arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER), null, null, Phone.DISPLAY_NAME + " COLLATE NOCASE")?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(0).orEmpty().trim()
                        if (name.isNotBlank()) found += newPerson(name, cursor.getString(1).orEmpty())
                    }
                }
                found.distinctBy { it.id }
            }
        }
        contacts = result.getOrDefault(emptyList())
        failed = result.isFailure
        loading = false
    }
    Dialog(onDismissRequest = dismiss) {
        Surface(shape = RoundedCornerShape(28.dp), tonalElevation = 6.dp) {
            Column(Modifier.fillMaxWidth().heightIn(max = 600.dp).imePadding().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Choose contact", style = MaterialTheme.typography.headlineMedium)
                CompactInput(search, { search = it }, "Search contacts", Modifier.fillMaxWidth(),
                    inputModifier = Modifier.focusRequester(focus).testTag("contactSearch"))
                val visible = contacts.filter { it.name.contains(search, true) || it.phone.contains(search) }
                LazyColumn(Modifier.weight(1f, fill = false).fillMaxWidth()) {
                    if (loading) item { Caption("Loading contacts…") }
                    else if (failed) item { Caption("Contacts unavailable. You can enter a name instead.") }
                    else if (visible.isEmpty()) item { Caption("No matching contacts") }
                    items(visible, key = { it.id }) { person ->
                        Column(Modifier.fillMaxWidth().clickable { keyboard?.hide(); choose(person) }.padding(vertical = 12.dp)) {
                            Text(person.name)
                            Caption(person.phone)
                        }
                    }
                }
                Action("Close", Modifier.fillMaxWidth(), secondary = true, onClick = dismiss)
            }
        }
    }
}
