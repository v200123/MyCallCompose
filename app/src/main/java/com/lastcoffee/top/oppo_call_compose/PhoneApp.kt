package com.lastcoffee.top.oppo_call_compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.lastcoffee.top.oppo_call_compose.data.ManualCallStore
import com.lastcoffee.top.oppo_call_compose.data.demoCalls
import com.lastcoffee.top.oppo_call_compose.feature.call.CallDetailScreen
import com.lastcoffee.top.oppo_call_compose.feature.call.InCallScreen
import com.lastcoffee.top.oppo_call_compose.feature.contacts.ContactsScreen
import com.lastcoffee.top.oppo_call_compose.feature.home.PhoneHomeScreen
import com.lastcoffee.top.oppo_call_compose.feature.settings.AddCallRecordScreen
import com.lastcoffee.top.oppo_call_compose.feature.settings.SettingsScreen
import com.lastcoffee.top.oppo_call_compose.model.CallKind
import com.lastcoffee.top.oppo_call_compose.model.CallRecord
import com.lastcoffee.top.oppo_call_compose.model.ContactMatch
import com.lastcoffee.top.oppo_call_compose.ui.theme.OPPO_Call_ComposeTheme

private data object HomeRoute
private data object SearchRoute
private data object SettingsRoute
private data object AddCallRecordRoute
private data class CallDetailRoute(val record: CallRecord)
private data class ContactDetailRoute(val contact: ContactMatch)
private data class InCallRoute(val number: String)

@Composable
fun OppoPhoneApp(onDial: (String) -> Unit = {}) {
    val context = LocalContext.current
    val manualCallStore = remember(context) { ManualCallStore(context) }
    val manualCalls by manualCallStore.calls.collectAsState(initial = emptyList())
    val hiddenDemoIndexes by manualCallStore.hiddenDemoIndexes.collectAsState(initial = emptyList())
    LaunchedEffect(manualCallStore) { manualCallStore.importLegacyRecords() }
    val records = manualCalls.map { it.toCallRecord() } + demoCalls.mapIndexedNotNull { index, record ->
        if (index in hiddenDemoIndexes) null else record.copy(demoIndex = index)
    }
    val backStack = remember { mutableStateListOf<Any>(HomeRoute) }
    var pendingDialNumber by remember { mutableStateOf<String?>(null) }
    val activeCall = backStack.lastOrNull() as? InCallRoute

    if (activeCall != null) {
        // Keep the navigation history intact, but remove NavDisplay from the
        // composition while the call window is transparent so the previous page
        // cannot show through in place of the system wallpaper.
        InCallScreen(
            number = activeCall.number,
            onCallFinished = { call ->
                manualCallStore.add(call)
                backStack.removeLastOrNull()
            },
        )
    } else {
        NavDisplay(
            backStack = backStack,
            onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
            entryProvider = entryProvider {
                entry<HomeRoute> {
                    PhoneHomeScreen(
                        records = records,
                        onShowDetail = { backStack.add(CallDetailRoute(it)) },
                        onShowContact = { backStack.add(ContactDetailRoute(it)) },
                        onStartCall = { backStack.add(InCallRoute(it)) },
                        onOpenSearch = { backStack.add(SearchRoute) },
                        onOpenSettings = { backStack.add(SettingsRoute) },
                        pendingDialNumber = pendingDialNumber,
                        onDialRequestConsumed = { pendingDialNumber = null },
                        onClearCalls = {
                            try {
                                manualCallStore.clearAll(demoCalls.size)
                                true
                            } catch (_: Exception) {
                                false
                            }
                        },
                        onDial = onDial,
                    )
                }
                entry<SearchRoute> {
                    Surface(color = Color.White, modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier.fillMaxSize()
                                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
                        ) {
                            ContactsScreen(
                                onShowContact = { backStack.add(ContactDetailRoute(it)) },
                                startSearching = true,
                                onExitSearch = { backStack.removeLastOrNull() },
                            )
                        }
                    }
                }
                entry<SettingsRoute> {
                    SettingsScreen(
                        onBack = { backStack.removeLastOrNull() },
                        onAddCallRecord = { backStack.add(AddCallRecordRoute) },
                    )
                }
                entry<AddCallRecordRoute> {
                    AddCallRecordScreen(
                        manualCount = manualCalls.size,
                        onBack = { backStack.removeLastOrNull() },
                        onSave = { newCall ->
                            try {
                                manualCallStore.add(newCall)
                                backStack.removeLastOrNull()
                                true
                            } catch (_: Exception) {
                                false
                            }
                        },
                    )
                }
                entry<CallDetailRoute> { route ->
                    CallDetailScreen(
                        record = route.record,
                        allRecords = records,
                        onBack = { backStack.removeLastOrNull() },
                        onDial = { number -> backStack.add(InCallRoute(number)) },
                        onEditNumber = { number ->
                            while (backStack.size > 1) backStack.removeLastOrNull()
                            pendingDialNumber = number
                        },
                        onDelete = { record ->
                            try {
                                manualCallStore.delete(record)
                                backStack.removeLastOrNull()
                                true
                            } catch (_: Exception) {
                                false
                            }
                        },
                    )
                }
                entry<ContactDetailRoute> { route ->
                    CallDetailScreen(
                        record = CallRecord(route.contact.number, route.contact.location, "", CallKind.OUTGOING),
                        allRecords = records,
                        onBack = { backStack.removeLastOrNull() },
                        onDial = { number -> backStack.add(InCallRoute(number)) },
                        fromContact = true,
                    )
                }
            },
        )
    }
}

@Preview(showBackground = true, widthDp = 540, heightDp = 1176)
@Composable
private fun OppoPhonePreview() {
    OPPO_Call_ComposeTheme(darkTheme = false, dynamicColor = false) { OppoPhoneApp() }
}
