package com.lastcoffee.top.oppo_call_compose.feature.contacts

import android.content.Context
import android.icu.text.Transliterator
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lastcoffee.top.oppo_call_compose.R
import com.lastcoffee.top.oppo_call_compose.model.ContactMatch
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.launch

private val ContactGray = Color(0xFF808080)
private val ContactInk = Color(0xFF171717)
private val ContactDivider = Color(0xFFEEEEEE)
private val ContactGreen = Color(0xFF00C700)
private val ContactActionGreen = Color(0xFF009900)
private val ContactIndex = listOf("☆") + ('A'..'Z').map(Char::toString) + "#"
private val ContactTransliterator by lazy { Transliterator.getInstance("Han-Latin; Latin-ASCII") }

@Composable
fun ContactsScreen(
    onShowContact: (ContactMatch) -> Unit,
    startSearching: Boolean = false,
    onExitSearch: (() -> Unit)? = null,
    contacts: List<ContactMatch> = emptyList(),
    onSaveContact: (ContactMatch) -> Unit = {},
    newContactNumber: String? = null,
    existingContactNumber: String? = null,
    onContactRequestConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("contacts_home", Context.MODE_PRIVATE) }
    var searching by remember { mutableStateOf(startSearching) }
    var query by remember { mutableStateOf("") }
    var auxiliaryPage by remember { mutableStateOf<String?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showRecommendation by remember { mutableStateOf(!preferences.getBoolean("profile_hint_dismissed", false)) }
    var profileName by remember { mutableStateOf(preferences.getString("profile_name", "").orEmpty()) }
    var profileNumber by remember { mutableStateOf(preferences.getString("profile_number", "").orEmpty()) }
    var groups by remember { mutableStateOf(preferences.getStringSet("groups", emptySet()).orEmpty().sorted()) }
    var creatingGroup by remember { mutableStateOf(false) }
    var groupName by remember { mutableStateOf("") }
    var editorTitle by remember { mutableStateOf<String?>(null) }
    var editingName by remember { mutableStateOf("") }
    var editingNumber by remember { mutableStateOf("") }
    var editingLocation by remember { mutableStateOf("") }
    var editingProfile by remember { mutableStateOf(false) }
    var selectingExistingNumber by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val visibleContacts = remember(contacts, query) {
        val collator = Collator.getInstance(Locale.CHINA)
        contacts.filter {
            query.isBlank() || it.name.contains(query, ignoreCase = true) ||
                it.number.filterNot(Char::isWhitespace).contains(query.filterNot(Char::isWhitespace))
        }.sortedWith { a, b ->
            val sectionCompare = sectionSortKey(contactSection(a)).compareTo(sectionSortKey(contactSection(b)))
            if (sectionCompare != 0) sectionCompare else collator.compare(a.name, b.name)
        }.groupBy(::contactSection)
    }
    fun openEditor(number: String = "", contact: ContactMatch? = null, profile: Boolean = false) {
        editingName = if (profile) profileName else contact?.name.orEmpty()
        editingNumber = if (profile) profileNumber else number
        editingLocation = contact?.location.orEmpty()
        editingProfile = profile
        editorTitle = if (profile) "编辑我的名片" else if (contact != null) "编辑联系人" else "新建联系人"
        keyboardController?.hide()
    }
    fun leaveSearchOrPage() {
        when {
            selectingExistingNumber != null -> { selectingExistingNumber = null; query = ""; searching = false }
            auxiliaryPage != null -> auxiliaryPage = null
            startSearching && onExitSearch != null -> onExitSearch()
            else -> { searching = false; query = ""; keyboardController?.hide() }
        }
    }
    BackHandler(enabled = auxiliaryPage != null || searching || selectingExistingNumber != null) { leaveSearchOrPage() }
    LaunchedEffect(searching, query) { listState.scrollToItem(0) }
    LaunchedEffect(searching, auxiliaryPage) {
        if (searching && auxiliaryPage == null) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    LaunchedEffect(newContactNumber) {
        if (newContactNumber != null) { openEditor(newContactNumber); onContactRequestConsumed() }
    }
    LaunchedEffect(existingContactNumber) {
        if (existingContactNumber != null) {
            selectingExistingNumber = existingContactNumber
            auxiliaryPage = null
            searching = false
            query = ""
            onContactRequestConsumed()
        }
    }
    val choosingContact = selectingExistingNumber != null
    val showHomeHeader = !searching && !choosingContact && auxiliaryPage == null
    val sectionIndices = remember(visibleContacts, showHomeHeader, showRecommendation) {
        var row = if (showHomeHeader) 3 + (if (showRecommendation) 1 else 0) else 0
        buildMap {
            visibleContacts.forEach { (section, group) ->
                put(section, row)
                row += group.size + 1
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(52.dp).padding(start = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (auxiliaryPage != null || choosingContact || (searching && !startSearching)) {
                    IconButton(onClick = ::leaveSearchOrPage) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = ContactInk)
                    }
                }
                when {
                    auxiliaryPage != null || (choosingContact && !searching) -> {
                        Text(auxiliaryPage ?: "选择联系人", fontSize = 20.sp, color = ContactInk,
                            fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    }
                    searching -> OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("搜索联系人", color = ContactGray) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color(0xFFF3F3F3),
                            unfocusedContainerColor = Color(0xFFF3F3F3),
                            cursorColor = ContactActionGreen,
                        ),
                        modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    )
                    else -> Spacer(Modifier.weight(1f))
                }
                if (auxiliaryPage == null && !searching) {
                    IconButton(onClick = { searching = true }, modifier = Modifier.size(48.dp)) {
                        Image(painterResource(R.drawable.oppo_pb_ic_menu_search_normal), "搜索联系人", Modifier.size(24.dp))
                    }
                }
                if (searching) {
                    TextButton(onClick = {
                        if (choosingContact) { searching = false; query = ""; keyboardController?.hide() }
                        else leaveSearchOrPage()
                    }) { Text("取消", color = ContactActionGreen, fontSize = 16.sp) }
                } else if (auxiliaryPage == null && !choosingContact) {
                    Box {
                        IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(48.dp)) {
                            Image(painterResource(R.drawable.oppo_coui_toolbar_menu_icon_more_normal), "更多", Modifier.size(24.dp))
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }, containerColor = Color.White) {
                            listOf("新建联系人", "我的名片", "我的群组").forEach { title ->
                                DropdownMenuItem(text = { Text(title, color = ContactInk) }, onClick = {
                                    menuExpanded = false
                                    if (title == "新建联系人") openEditor() else auxiliaryPage = title
                                })
                            }
                        }
                    }
                }
            }
            if (auxiliaryPage != null) {
                Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    if (auxiliaryPage == "我的名片") {
                        Spacer(Modifier.height(28.dp))
                        Image(painterResource(R.drawable.contacts_home_my_profile), null,
                            modifier = Modifier.size(72.dp).align(Alignment.CenterHorizontally))
                        Text(profileName.ifBlank { "设置我的名片" }, fontSize = 24.sp, color = ContactInk,
                            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 20.dp))
                        if (profileNumber.isNotBlank()) Text(profileNumber, fontSize = 18.sp, color = ContactGray,
                            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp))
                        TextButton(onClick = { openEditor(profile = true) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Text(if (profileName.isBlank()) "去设置" else "编辑", color = ContactActionGreen, fontSize = 16.sp)
                        }
                    } else {
                        TextButton(onClick = { groupName = ""; creatingGroup = true }) {
                            Text("新建群组", color = ContactActionGreen, fontSize = 16.sp)
                        }
                        if (groups.isEmpty()) Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                            Text("暂无群组", color = ContactGray, fontSize = 16.sp)
                        }
                        groups.forEach { group ->
                            ContactShortcutRow(group, R.drawable.contacts_home_my_group)
                        }
                    }
                }
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
                    if (showHomeHeader) {
                        item(key = "title") {
                            Column(modifier = Modifier.padding(start = 16.dp, end = 36.dp, bottom = 12.dp)) {
                                Text("联系人", fontSize = 32.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold, color = ContactInk)
                                Text("${contacts.size} 个联系人", fontSize = 16.sp, lineHeight = 22.sp, color = ContactGray)
                            }
                        }
                        if (showRecommendation) item(key = "profile_hint") {
                            ContactProfileRecommendation(onDismiss = {
                                showRecommendation = false
                                preferences.edit().putBoolean("profile_hint_dismissed", true).apply()
                            }, onSetProfile = { openEditor(profile = true) })
                        }
                        item(key = "profile") { ContactShortcutRow("我的名片", R.drawable.contacts_home_my_profile) { auxiliaryPage = "我的名片" } }
                        item(key = "groups") { ContactShortcutRow("我的群组", R.drawable.contacts_home_my_group) { auxiliaryPage = "我的群组" } }
                    }
                    if (visibleContacts.isEmpty()) item {
                        Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            Text(if (query.isBlank()) "暂无联系人" else "没有找到联系人", color = ContactGray, fontSize = 16.sp)
                        }
                    }
                    visibleContacts.forEach { (section, sectionContacts) ->
                        item(key = "section_$section") {
                            Box(Modifier.fillMaxWidth().height(36.dp).padding(start = 16.dp), contentAlignment = Alignment.CenterStart) {
                                Text(section, fontSize = 16.sp, color = ContactInk)
                            }
                        }
                        items(sectionContacts, key = { "${it.name}_${it.number}" }) { contact ->
                            ContactListRow(contact, showNumber = searching || choosingContact) {
                                if (choosingContact) {
                                    openEditor(selectingExistingNumber.orEmpty(), contact)
                                    selectingExistingNumber = null
                                    searching = false
                                    query = ""
                                } else { keyboardController?.hide(); onShowContact(contact) }
                            }
                        }
                    }
                }
            }
        }
        if (showHomeHeader) {
            ContactAlphabetIndex(modifier = Modifier.align(Alignment.CenterEnd).offset(y = (-12).dp)) { section ->
                val target = if (section == "☆") 0 else sectionIndices[section]
                    ?: sectionIndices.entries.firstOrNull { sectionSortKey(it.key) >= sectionSortKey(section) }?.value
                    ?: sectionIndices.values.lastOrNull() ?: 0
                scope.launch { listState.scrollToItem(target) }
            }
            FloatingActionButton(
                onClick = { openEditor() },
                shape = CircleShape,
                containerColor = ContactGreen,
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 24.dp).size(56.dp),
            ) { Image(painterResource(R.drawable.contacts_home_add), "新建联系人", Modifier.size(32.dp)) }
        }
    }
    if (editorTitle != null) AlertDialog(
        onDismissRequest = { editorTitle = null },
        title = { Text(editorTitle!!, color = ContactInk) },
        containerColor = Color.White,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = editingName, onValueChange = { editingName = it },
                    label = { Text("姓名") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = editingNumber, onValueChange = { editingNumber = it },
                    label = { Text("电话号码") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = editingName.isNotBlank() && editingNumber.isNotBlank(), onClick = {
                if (editingProfile) {
                    profileName = editingName.trim()
                    profileNumber = editingNumber.trim()
                    showRecommendation = false
                    preferences.edit().putString("profile_name", profileName).putString("profile_number", profileNumber)
                        .putBoolean("profile_hint_dismissed", true).apply()
                } else onSaveContact(ContactMatch(editingName.trim(), editingNumber.trim(), editingLocation))
                editorTitle = null
            }) { Text("保存", color = if (editingName.isBlank() || editingNumber.isBlank()) ContactGray else ContactActionGreen) }
        },
        dismissButton = { TextButton(onClick = { editorTitle = null }) { Text("取消", color = ContactGray) } },
    )
    if (creatingGroup) AlertDialog(
        onDismissRequest = { creatingGroup = false },
        title = { Text("新建群组") }, containerColor = Color.White,
        text = { OutlinedTextField(value = groupName, onValueChange = { groupName = it }, label = { Text("群组名称") }, singleLine = true) },
        confirmButton = { TextButton(enabled = groupName.isNotBlank(), onClick = {
            groups = (groups + groupName.trim()).distinct().sorted()
            preferences.edit().putStringSet("groups", groups.toSet()).apply()
            creatingGroup = false
        }) { Text("保存", color = ContactActionGreen) } },
        dismissButton = { TextButton(onClick = { creatingGroup = false }) { Text("取消", color = ContactGray) } },
    )
}

@Composable
private fun ContactProfileRecommendation(onDismiss: () -> Unit, onSetProfile: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 36.dp, bottom = 16.dp)
        .background(Color(0xFFF5F5F5), RoundedCornerShape(12.dp)).padding(start = 14.dp, top = 10.dp, end = 12.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.PersonOutline, null, tint = ContactGray, modifier = Modifier.size(24.dp))
            Text("设置我的名片，支持一碰互传分享给朋友", fontSize = 16.sp, lineHeight = 24.sp,
                color = ContactGray, modifier = Modifier.padding(start = 6.dp))
        }
        Row(modifier = Modifier.align(Alignment.End)) {
            TextButton(onClick = onDismiss, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp), modifier = Modifier.height(30.dp)) {
                Text("忽略", fontSize = 16.sp, color = ContactActionGreen)
            }
            TextButton(onClick = onSetProfile, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp), modifier = Modifier.height(30.dp)) {
                Text("去设置", fontSize = 16.sp, color = ContactActionGreen)
            }
        }
    }
}

@Composable
private fun ContactShortcutRow(label: String, drawable: Int, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().height(60.dp)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(start = 16.dp, end = 36.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(drawable), null, Modifier.size(36.dp))
        Box(Modifier.padding(start = 16.dp).fillMaxSize(), contentAlignment = Alignment.CenterStart) {
            Text(label, fontSize = 18.sp, color = ContactInk, fontWeight = FontWeight.Medium)
            HorizontalDivider(Modifier.align(Alignment.BottomCenter), color = ContactDivider, thickness = 0.5.dp)
        }
    }
}

@Composable
private fun ContactListRow(contact: ContactMatch, showNumber: Boolean, onClick: () -> Unit) {
    val avatarColors = listOf(Color(0xFF47BD50), Color(0xFFEB6B62), Color(0xFFE8B329), Color(0xFF5C92E8), Color(0xFF9D7AD2))
    val avatarColor = avatarColors[(contact.name.hashCode() and Int.MAX_VALUE) % avatarColors.size]
    Row(Modifier.fillMaxWidth().height(60.dp).clickable(onClick = onClick).padding(start = 16.dp, end = 36.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).background(avatarColor, CircleShape), contentAlignment = Alignment.Center) {
            Text(contact.name.trim().take(1).uppercase(), color = Color.White, fontSize = 18.sp)
        }
        Box(Modifier.padding(start = 16.dp).fillMaxSize(), contentAlignment = Alignment.CenterStart) {
            Column {
                Text(contact.name, fontSize = 18.sp, color = ContactInk, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (showNumber) Text(contact.number, fontSize = 14.sp, color = ContactGray, maxLines = 1)
            }
            HorizontalDivider(Modifier.align(Alignment.BottomCenter), color = ContactDivider, thickness = 0.5.dp)
        }
    }
}

@Composable
private fun ContactAlphabetIndex(modifier: Modifier = Modifier, onSection: (String) -> Unit) {
    val currentOnSection by rememberUpdatedState(onSection)
    Column(modifier.width(36.dp).height(462.dp).pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            fun pick(y: Float) = currentOnSection(ContactIndex[(y / size.height * ContactIndex.size).toInt().coerceIn(ContactIndex.indices)])
            pick(down.position.y)
            down.consume()
            do {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change != null && change.pressed) { pick(change.position.y); change.consume() }
            } while (event.changes.any { it.pressed })
        }
    }, horizontalAlignment = Alignment.CenterHorizontally) {
        ContactIndex.forEach { section ->
            Box(Modifier.weight(1f).fillMaxWidth().semantics { contentDescription = "跳转到 $section" }
                .clickable { onSection(section) }, contentAlignment = Alignment.Center) {
                Text(section, fontSize = 12.sp, lineHeight = 15.sp, color = ContactGray)
            }
        }
    }
}

private fun contactSection(contact: ContactMatch): String {
    val first = ContactTransliterator.transliterate(contact.name.trim()).firstOrNull()?.uppercaseChar()
    return first?.takeIf { it in 'A'..'Z' }?.toString() ?: "#"
}

private fun sectionSortKey(section: String): String = if (section == "#") "{" else section
