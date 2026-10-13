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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.lastcoffee.top.oppo_call_compose.feature.settings.CallMediaFiles
import com.lastcoffee.top.oppo_call_compose.feature.settings.CallMediaLibraryScreen
import com.lastcoffee.top.oppo_call_compose.feature.settings.CallModeSettingsScreen
import com.lastcoffee.top.oppo_call_compose.feature.settings.CallNumberPoolsScreen
import com.lastcoffee.top.oppo_call_compose.feature.settings.CallRulesScreen
import com.lastcoffee.top.oppo_call_compose.feature.settings.SettingsScreen
import com.lastcoffee.top.oppo_call_compose.model.CallAudioMode
import com.lastcoffee.top.oppo_call_compose.model.CallFlowConfiguration
import com.lastcoffee.top.oppo_call_compose.model.CallKind
import com.lastcoffee.top.oppo_call_compose.model.CallRecord
import com.lastcoffee.top.oppo_call_compose.model.ContactMatch
import com.lastcoffee.top.oppo_call_compose.ui.theme.OPPO_Call_ComposeTheme
import kotlinx.coroutines.launch

private data object HomeRoute
private data object SearchRoute
private data object SettingsRoute
private data object AddCallRecordRoute
private data object CallModeSettingsRoute
private data object CallRulesRoute
private data object NumberPoolsRoute
private data class CallMediaRoute(val mode: CallAudioMode)
private data class CallDetailRoute(val record: CallRecord)
private data class ContactDetailRoute(val contact: ContactMatch)
private data class InCallRoute(val number: String, val mode: CallAudioMode, val initialStatusId: String)

@Composable
fun OppoPhoneApp(onDial: (String) -> Unit = {}) {
    val context = LocalContext.current
    val manualCallStore = remember(context) { ManualCallStore(context) }
    val manualCalls by manualCallStore.calls.collectAsState(initial = emptyList())
    val hiddenDemoIndexes by manualCallStore.hiddenDemoIndexes.collectAsState(initial = emptyList())
    val callConfiguration by manualCallStore.callConfiguration.collectAsState(initial = CallFlowConfiguration())
    val mediaEntries by manualCallStore.mediaEntries.collectAsState(initial = emptyList())
    val numberPools by manualCallStore.numberPools.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val backStack = remember { mutableStateListOf<Any>(HomeRoute) }
    LaunchedEffect(manualCallStore) { manualCallStore.importLegacyRecords(); manualCallStore.configuration() }
    val startSimulatedCall: (String) -> Unit = { number ->
        scope.launch {
            val setup = manualCallStore.prepareCall(number)
            backStack.add(InCallRoute(number, setup.mode, setup.initialStatusId))
        }
    }
    val records = manualCalls.map { it.toCallRecord() } + demoCalls.mapIndexedNotNull { index, record ->
        if (index in hiddenDemoIndexes) null else record.copy(demoIndex = index)
    }
    var pendingDialNumber by remember { mutableStateOf<String?>(null) }
    val activeCall = backStack.lastOrNull() as? InCallRoute

    if (activeCall != null) {
        // Keep the navigation history intact, but remove NavDisplay from the
        // composition while the call window is transparent so the previous page
        // cannot show through in place of the system wallpaper.
        InCallScreen(
            number = activeCall.number,
            mode = activeCall.mode,
            initialStatusId = activeCall.initialStatusId,
            configuration = callConfiguration,
            mediaEntries = mediaEntries,
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
                        onStartCall = startSimulatedCall,
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
                        onOpenCallRules = { backStack.add(CallModeSettingsRoute) },
                    )
                }
                entry<CallModeSettingsRoute> {
                    CallModeSettingsScreen(
                        config = callConfiguration,
                        onBack = { backStack.removeLastOrNull() },
                        onSelectMode = { mode -> scope.launch { manualCallStore.saveConfiguration(callConfiguration.copy(selectedAudioMode = mode)) } },
                        onOpenRules = { backStack.add(CallRulesRoute) },
                        onOpenPools = { backStack.add(NumberPoolsRoute) },
                        onOpenLibrary = { backStack.add(CallMediaRoute(it)) },
                    )
                }
                entry<CallRulesRoute> {
                    CallRulesScreen(callConfiguration, mediaEntries = mediaEntries, onAddMedia = { entry -> manualCallStore.addMedia(entry) }, onBack = { backStack.removeLastOrNull() }, onSave = { updated -> scope.launch { manualCallStore.saveConfiguration(updated) } })
                }
                entry<CallMediaRoute> { route ->
                    CallMediaLibraryScreen(
                        mode = route.mode, entries = mediaEntries, scope = scope,
                        onBack = { backStack.removeLastOrNull() },
                        onAdd = { entry -> manualCallStore.addMedia(entry) },
                        onUpdate = { entry -> manualCallStore.addMedia(entry) },
                        onDelete = { entry -> manualCallStore.deleteMedia(entry.id)?.let { removed -> CallMediaFiles.delete(removed.filePath); CallMediaFiles.delete(removed.iconPath) } },
                    )
                }
                entry<NumberPoolsRoute> {
                    CallNumberPoolsScreen(numberPools, onBack = { backStack.removeLastOrNull() }, onChangeMode = { number, mode -> scope.launch { manualCallStore.updateNumberMode(number, mode) } })
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
                        onDial = startSimulatedCall,
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
                        onDial = startSimulatedCall,
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
