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
import com.lastcoffee.top.oppo_call_compose.data.LocalContactsStore
import com.lastcoffee.top.oppo_call_compose.data.PhoneExtrasStore
import com.lastcoffee.top.oppo_call_compose.data.PhoneAttributionStore
import com.lastcoffee.top.oppo_call_compose.data.mobileForLookup
import com.lastcoffee.top.oppo_call_compose.data.RingbackBadgeStore
import com.lastcoffee.top.oppo_call_compose.data.VoiceHangupSettingsStore
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
import com.lastcoffee.top.oppo_call_compose.feature.settings.CustomLocationsScreen
import com.lastcoffee.top.oppo_call_compose.feature.settings.RingbackSettingsScreen
import com.lastcoffee.top.oppo_call_compose.feature.settings.RingbackBadgeRulesScreen
import com.lastcoffee.top.oppo_call_compose.feature.settings.RingbackBadgeEditorScreen
import com.lastcoffee.top.oppo_call_compose.feature.settings.RingbackBadgeBindingsScreen
import com.lastcoffee.top.oppo_call_compose.feature.settings.AutoHangUpScreen
import com.lastcoffee.top.oppo_call_compose.feature.settings.CallPromptNumbersScreen
import com.lastcoffee.top.oppo_call_compose.feature.settings.VoiceHangupSettingsScreen
import com.lastcoffee.top.oppo_call_compose.data.CallPromptType
import com.lastcoffee.top.oppo_call_compose.model.CallAudioMode
import com.lastcoffee.top.oppo_call_compose.model.CallFlowConfiguration
import com.lastcoffee.top.oppo_call_compose.model.CallScreenVariant
import com.lastcoffee.top.oppo_call_compose.model.CallKind
import com.lastcoffee.top.oppo_call_compose.model.CallRecord
import com.lastcoffee.top.oppo_call_compose.model.ContactMatch
import com.lastcoffee.top.oppo_call_compose.ui.theme.OPPO_Call_ComposeTheme
import kotlinx.coroutines.launch

private data object HomeRoute
private data object SearchRoute
private data object SettingsRoute
private data object LocationsRoute
private data object RingbackSettingsRoute
private data object AutoHangUpRoute
private data object VoiceHangupSettingsRoute
private data class CallPromptNumbersRoute(val type: CallPromptType)
private data object RingbackBadgeRulesRoute
private data class RingbackBadgeEditorRoute(val ruleId: String)
private data object RingbackBadgeBindingsRoute
private data object AddCallRecordRoute
private data object CallModeSettingsRoute
private data object CallRulesRoute
private data object NumberPoolsRoute
private data class CallMediaRoute(val mode: CallAudioMode)
private data class CallDetailRoute(val record: CallRecord)
private data class ContactDetailRoute(val contact: ContactMatch)
private data class InCallRoute(val number: String, val mode: CallAudioMode, val initialStatusId: String, val configuration: CallFlowConfiguration, val simSlot: Int, val screenVariant: CallScreenVariant)

@Composable
fun OppoPhoneApp(onDial: (String) -> Unit = {}, onCheckForUpdate: () -> Unit = {}) {
    val context = LocalContext.current
    val manualCallStore = remember(context) { ManualCallStore(context) }
    val contactsStore = remember(context) { LocalContactsStore(context) }
    val contacts by contactsStore.contacts.collectAsState()
    val extrasStore = remember(context) { PhoneExtrasStore(context) }
    val extras by extrasStore.settings.collectAsState()
    val attributionStore = remember(context) { PhoneAttributionStore(context) }
    val attributions by attributionStore.locations.collectAsState()
    val locationFor: (String) -> String? = { number ->
        extras.locationFor(number) ?: mobileForLookup(number)?.take(7)?.let(attributions::get)
    }
    val badgeStore = remember(context) { RingbackBadgeStore(context) }
    val badgeSettings by badgeStore.settings.collectAsState()
    val manualCalls by manualCallStore.calls.collectAsState(initial = emptyList())
    val callConfiguration by manualCallStore.callConfiguration.collectAsState(initial = CallFlowConfiguration())
    val numberPools by manualCallStore.numberPools.collectAsState(initial = emptyList())
    val mediaEntries by manualCallStore.mediaEntries.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val backStack = remember { mutableStateListOf<Any>(HomeRoute) }
    LaunchedEffect(manualCallStore) { manualCallStore.importLegacyRecords(); manualCallStore.configuration() }
    LaunchedEffect(extras, manualCallStore) { manualCallStore.ensureConfiguredNumbers(extras.configuredNumbers()) }
    val startSimulatedCall: (String, Int) -> Unit = { number, simSlot ->
        scope.launch {
            val setup = manualCallStore.prepareCall(number, extras.screenVariantFor(number))
            backStack.add(InCallRoute(number, setup.mode, setup.initialStatusId, setup.configuration, simSlot, setup.screenVariant))
        }
    }
    val records = manualCalls.map { it.toCallRecord() }
    LaunchedEffect(records.map { it.number to it.description }, extras.locations) {
        records.asSequence()
            .map { mobileForLookup(it.number) ?: it.number }
            .distinct()
            .forEach { attributionStore.request(it, extras) }
    }
    var pendingDialNumber by remember { mutableStateOf<String?>(null) }
    val activeCall = backStack.lastOrNull() as? InCallRoute
    val visibleNumber = when (val route = backStack.lastOrNull()) {
        is InCallRoute -> route.number
        is CallDetailRoute -> mobileForLookup(route.record.number) ?: route.record.description
        is ContactDetailRoute -> route.contact.number
        else -> null
    }
    LaunchedEffect(visibleNumber, extras.locations) {
        visibleNumber?.let { attributionStore.request(it, extras) }
    }

    if (activeCall != null) {
        // Keep the navigation history intact, but remove NavDisplay from the
        // composition while the call window is transparent so the previous page
        // cannot show through in place of the system wallpaper.
        InCallScreen(
            number = activeCall.number,
            mode = activeCall.mode,
            initialStatusId = activeCall.initialStatusId,
            configuration = activeCall.configuration,
            mediaEntries = mediaEntries,
            extras = extras,
            resolvedLocation = locationFor(activeCall.number),
            simSlot = activeCall.simSlot,
            screenVariant = activeCall.screenVariant,
            resolvedContact = contacts.firstOrNull { contact ->
                com.lastcoffee.top.oppo_call_compose.data.normalizePhoneNumber(contact.number) ==
                    com.lastcoffee.top.oppo_call_compose.data.normalizePhoneNumber(activeCall.number)
            },
            badgeRule = badgeSettings.ruleFor(activeCall.number),
            onRingbackStarted = { badgeStore.assignRuleForPhone(it) },
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
                        locationFor = locationFor,
                        onLookupLocation = { number -> scope.launch { attributionStore.request(number, extras) } },
                        onShowDetail = { backStack.add(CallDetailRoute(it)) },
                        onShowContact = { backStack.add(ContactDetailRoute(it)) },
                        onStartCall = startSimulatedCall,
                        onOpenSearch = { backStack.add(SearchRoute) },
                        onOpenSettings = { backStack.add(SettingsRoute) },
                        pendingDialNumber = pendingDialNumber,
                        onDialRequestConsumed = { pendingDialNumber = null },
                        onClearCalls = {
                            try {
                                manualCallStore.clearAll()
                                true
                            } catch (_: Exception) {
                                false
                            }
                        },
                        onDial = onDial,
                        contacts = contacts,
                        dialpadToneEnabled = extras.dialpadToneEnabled,
                        dualSimDisplayEnabled = extras.dualSimDisplayEnabled,
                        onSaveContact = contactsStore::save,
                        onDeleteRecords = { selected ->
                            try { manualCallStore.deleteRecords(selected); true } catch (_: Exception) { false }
                        },
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
                                contacts = contacts,
                                onSaveContact = contactsStore::save,
                                onExitSearch = { backStack.removeLastOrNull() },
                            )
                        }
                    }
                }
                entry<SettingsRoute> {
                    SettingsScreen(
                        calls = manualCallStore,
                        extras = extrasStore,
                        badges = badgeStore,
                        onBack = { backStack.removeLastOrNull() },
                        onAddCallRecord = { backStack.add(AddCallRecordRoute) },
                        onOpenCallRules = { backStack.add(CallModeSettingsRoute) },
                        onOpenLocations = { backStack.add(LocationsRoute) },
                        onOpenRingback = { backStack.add(RingbackSettingsRoute) },
                        onOpenAutoHangUp = { backStack.add(AutoHangUpRoute) },
                        onOpenVoiceHangup = { backStack.add(VoiceHangupSettingsRoute) },
                        onOpenPromptNumbers = { backStack.add(CallPromptNumbersRoute(it)) },
                        onCheckForUpdate = onCheckForUpdate,
                    )
                }
                entry<AutoHangUpRoute> {
                    AutoHangUpScreen(extras, extrasStore, numberPools,
                        onOpenPools = { backStack.add(NumberPoolsRoute) }, onBack = { backStack.removeLastOrNull() })
                }
                entry<VoiceHangupSettingsRoute> {
                    VoiceHangupSettingsScreen(VoiceHangupSettingsStore(context)) { backStack.removeLastOrNull() }
                }
                entry<CallPromptNumbersRoute> { route ->
                    CallPromptNumbersScreen(route.type, extras, extrasStore, callConfiguration,
                        onBack = { backStack.removeLastOrNull() })
                }
                entry<LocationsRoute> {
                    CustomLocationsScreen(extras, extrasStore, onBack = { backStack.removeLastOrNull() })
                }
                entry<RingbackSettingsRoute> {
                    RingbackSettingsScreen(extras, extrasStore, mediaEntries,
                        onOpenLibrary = { backStack.add(CallMediaRoute(CallAudioMode.RINGBACK)) },
                        onOpenAudioMode = { backStack.add(CallModeSettingsRoute) },
                        onOpenBadgeRules = { backStack.add(RingbackBadgeRulesRoute) },
                        onBack = { backStack.removeLastOrNull() })
                }
                entry<RingbackBadgeRulesRoute> {
                    RingbackBadgeRulesScreen(badgeSettings, badgeStore,
                        onOpenEditor = { backStack.add(RingbackBadgeEditorRoute(it)) },
                        onOpenBindings = { backStack.add(RingbackBadgeBindingsRoute) },
                        onBack = { backStack.removeLastOrNull() })
                }
                entry<RingbackBadgeEditorRoute> { route ->
                    badgeSettings.rules.firstOrNull { it.id == route.ruleId }?.let { rule ->
                        RingbackBadgeEditorScreen(rule, badgeStore, onBack = { backStack.removeLastOrNull() })
                    }
                }
                entry<RingbackBadgeBindingsRoute> {
                    RingbackBadgeBindingsScreen(badgeSettings, badgeStore, onBack = { backStack.removeLastOrNull() })
                }
                entry<CallModeSettingsRoute> {
                    CallModeSettingsScreen(
                        config = callConfiguration,
                        onBack = { backStack.removeLastOrNull() },
                        onSelectMode = { mode -> scope.launch { manualCallStore.saveConfiguration(callConfiguration.copy(selectedAudioMode = mode)) } },
                        onOpenRules = { backStack.add(CallRulesRoute) },
                        onOpenLibrary = { backStack.add(CallMediaRoute(it)) },
                    )
                }
                entry<CallRulesRoute> {
                    CallRulesScreen(callConfiguration, extras, mediaEntries = mediaEntries, onAddMedia = { entry -> manualCallStore.addMedia(entry) }, onBack = { backStack.removeLastOrNull() }, onSave = { variant, updated -> scope.launch { manualCallStore.saveVariantConfiguration(variant, updated) } })
                }
                entry<CallMediaRoute> { route ->
                    CallMediaLibraryScreen(
                        mode = route.mode, entries = mediaEntries, scope = scope,
                        onBack = { backStack.removeLastOrNull() },
                        onAdd = { entry -> manualCallStore.addMedia(entry) },
                        onUpdate = { entry -> manualCallStore.addMedia(entry) },
                        onDelete = { entry -> manualCallStore.deleteMedia(entry.id)?.let { removed ->
                            extrasStore.removeMedia(removed.id)
                            CallMediaFiles.delete(context, removed.filePath)
                            CallMediaFiles.delete(context, removed.iconPath)
                        } },
                    )
                }
                entry<NumberPoolsRoute> {
                    CallNumberPoolsScreen(numberPools, callConfiguration, extras, extrasStore,
                        onBack = { backStack.removeLastOrNull() },
                        onAddNumber = { number -> scope.launch { manualCallStore.addNumberToPool(number) } },
                        onChangeMode = { number, mode -> scope.launch { manualCallStore.updateNumberMode(number, mode) } })
                }
                entry<AddCallRecordRoute> {
                    AddCallRecordScreen(
                        onBack = { backStack.removeLastOrNull() },
                        onSave = { newCall, hasAvatar, avatarPath ->
                            try {
                                if (newCall.attribution == null && newCall.operator == null) {
                                    attributionStore.request(newCall.number, extrasStore.settings.value)
                                }
                                val savedPlace = extrasStore.settings.value.customLocationMatch(newCall.number)?.location
                                val lookupParts = attributionStore.locationFor(newCall.number)?.split(' ')?.filter(String::isNotBlank).orEmpty()
                                val resolvedCall = newCall.copy(
                                    attribution = newCall.attribution ?: savedPlace?.let { place ->
                                        listOf(place.province, place.city.takeUnless { it == place.province }.orEmpty())
                                            .filter(String::isNotBlank).joinToString("").ifBlank { null }
                                    } ?: if (lookupParts.size >= 2) lookupParts.dropLast(1).joinToString("") else lookupParts.firstOrNull(),
                                    operator = newCall.operator ?: savedPlace?.carrier?.ifBlank { null } ?: lookupParts.lastOrNull()?.takeIf { lookupParts.size >= 2 },
                                )
                                manualCallStore.add(resolvedCall)
                                val previousAvatarPath = extrasStore.settings.value.avatarPhotoFor(newCall.number)
                                extrasStore.setCallRecordAvatar(newCall.number, hasAvatar, avatarPath)
                                if (previousAvatarPath != avatarPath) CallMediaFiles.delete(context, previousAvatarPath)
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
                        contacts = contacts,
                        customLocation = locationFor(route.record.number) ?: locationFor(route.record.description),
                        onBack = { backStack.removeLastOrNull() },
                        onDial = { number -> startSimulatedCall(number, 1) },
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
                        contacts = contacts,
                        customLocation = locationFor(route.contact.number),
                        onBack = { backStack.removeLastOrNull() },
                        onDial = { number -> startSimulatedCall(number, 1) },
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
