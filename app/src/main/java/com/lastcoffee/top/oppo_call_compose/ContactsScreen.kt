package com.lastcoffee.top.oppo_call_compose

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val ContactGray = Color(0xFF858585)
private val ContactDivider = Color(0xFFEEEEEE)
private val ContactGreen = Color(0xFF20AD45)

@Composable
fun ContactsScreen(
    onShowContact: (ContactMatch) -> Unit,
    startSearching: Boolean = false,
    onExitSearch: (() -> Unit)? = null,
) {
    var searching by remember { mutableStateOf(startSearching) }
    var query by remember { mutableStateOf("") }
    var auxiliaryPage by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val contacts = remember(query) {
        demoContacts.map { it to contactSection(it) }
            .sortedWith(compareBy<Pair<ContactMatch, String>> { it.second }.thenBy { it.first.name })
            .filter { (contact, _) ->
                query.isBlank() || contact.name.contains(query, ignoreCase = true) || contact.number.contains(query)
            }
    }
    fun leaveSearch() {
        if (auxiliaryPage != null) auxiliaryPage = null else if (startSearching && onExitSearch != null) {
            onExitSearch()
        } else {
            searching = false
            query = ""
        }
    }
    BackHandler(enabled = auxiliaryPage != null || searching) { leaveSearch() }
    LaunchedEffect(searching, query) { listState.scrollToItem(0) }
    LaunchedEffect(searching) {
        if (searching) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (auxiliaryPage != null || (searching && !startSearching)) {
                IconButton(onClick = ::leaveSearch) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            }
            when {
                auxiliaryPage != null -> Text(auxiliaryPage!!, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                searching -> OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("搜索联系人") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    shape = RoundedCornerShape(28.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color(0xFFF3F4F6),
                        unfocusedContainerColor = Color(0xFFF3F4F6),
                    ),
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                )
                else -> {
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = { searching = true }) {
                        Icon(Icons.Filled.Search, contentDescription = "搜索联系人", modifier = Modifier.size(24.dp))
                    }
                }
            }
            if (searching && startSearching) {
                TextButton(onClick = ::leaveSearch) { Text("取消", color = ContactGreen) }
            }
        }

        if (auxiliaryPage != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无内容", color = ContactGray, fontSize = 16.sp)
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    if (!searching) item {
                        Text("联系人", fontSize = 30.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 18.dp))
                    }
                    if (!searching) {
                        item { ContactShortcutRow("我的名片", Icons.Filled.Person) { auxiliaryPage = "我的名片" } }
                        item { ContactShortcutRow("我的群组", Icons.Filled.Groups) { auxiliaryPage = "我的群组" } }
                    }
                    if (contacts.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                                Text("没有找到联系人", color = ContactGray, fontSize = 16.sp)
                            }
                        }
                    }
                    itemsIndexed(contacts) { index, (contact, section) ->
                        Column {
                            if (index == 0 || contacts[index - 1].second != section) {
                                Text(section, fontSize = 14.sp, color = ContactGray,
                                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp))
                            }
                            ContactListRow(contact) { onShowContact(contact) }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
                if (!searching && contacts.isNotEmpty()) {
                    val sectionIndices = contacts.mapIndexed { index, pair -> pair.second to index }
                        .distinctBy { it.first }
                    Column(
                        modifier = Modifier.align(Alignment.CenterEnd).width(36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        sectionIndices.forEach { (section, index) ->
                            Text(section, fontSize = 11.sp, color = ContactGreen,
                                modifier = Modifier.clickable { scope.launch { listState.animateScrollToItem(index + 3) } }
                                    .padding(horizontal = 8.dp, vertical = 2.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactShortcutRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(58.dp).clickable(onClick = onClick)
            .padding(start = 16.dp, end = 36.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(36.dp).background(ContactGreen, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.padding(start = 16.dp).fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
            Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f).padding(top = 17.dp))
            HorizontalDivider(color = ContactDivider)
        }
    }
}

@Composable
private fun ContactListRow(contact: ContactMatch, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp).clickable(onClick = onClick)
            .padding(start = 16.dp, end = 36.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(R.drawable.ic_contacts_default_photo), contentDescription = null, modifier = Modifier.size(36.dp))
        Column(modifier = Modifier.padding(start = 16.dp).fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(contact.name, fontSize = 16.sp, color = Color(0xFF171717), maxLines = 1)
                Text(contact.number, fontSize = 14.sp, color = ContactGray, maxLines = 1)
            }
            HorizontalDivider(color = ContactDivider)
        }
    }
}

private fun contactSection(contact: ContactMatch): String = when (contact.name) {
    "川哥" -> "C"
    "地坪漆" -> "D"
    "高春，华" -> "G"
    "黄立静" -> "H"
    "张女士" -> "Z"
    else -> contact.name.firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'Z' }?.toString() ?: "#"
}
