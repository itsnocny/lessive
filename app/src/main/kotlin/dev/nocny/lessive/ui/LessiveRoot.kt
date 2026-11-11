package dev.nocny.lessive.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.LocalLaundryService
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material.icons.rounded.Help
import androidx.compose.material.icons.rounded.LocalLaundryService
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.nocny.lessive.LaunchState
import dev.nocny.lessive.LessiveViewModel
import dev.nocny.lessive.R
import dev.nocny.lessive.data.Habits
import dev.nocny.lessive.data.localized
import dev.nocny.lessive.data.WashSettings
import dev.nocny.lessive.ui.components.describe
import dev.nocny.lessive.ui.screens.AdvisorScreen
import dev.nocny.lessive.ui.screens.DialEditorScreen
import dev.nocny.lessive.ui.screens.HelpScreen
import dev.nocny.lessive.ui.screens.HomeScreen
import dev.nocny.lessive.ui.screens.LaunchSheet
import dev.nocny.lessive.ui.screens.MachineScreen
import dev.nocny.lessive.ui.screens.OnboardingScreen
import dev.nocny.lessive.ui.screens.ProgramScreen
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
sealed interface Route {
    @Serializable data class Program(val key: String, val suggested: WashSettings? = null) : Route
    @Serializable data object Advisor : Route
    @Serializable data object DialEditor : Route
}

private enum class Tab(@param:StringRes val label: Int, val selected: ImageVector, val unselected: ImageVector) {
    Wash(R.string.tab_wash, Icons.Rounded.LocalLaundryService, Icons.Outlined.LocalLaundryService),
    Machine(R.string.tab_machine, Icons.Rounded.Nfc, Icons.Outlined.Nfc),
    Help(R.string.tab_help, Icons.Rounded.Help, Icons.Outlined.HelpOutline),
}

private val routeSaver = listSaver<MutableList<Route>, String>(
    save = { stack -> stack.map { Json.encodeToString(Route.serializer(), it) } },
    restore = { saved -> saved.map { Json.decodeFromString(Route.serializer(), it) }.toMutableStateList() },
)

@Composable
fun LessiveRoot(vm: LessiveViewModel = viewModel()) {
    val onboarded by vm.onboarded.collectAsStateWithLifecycle()
    val profileOrNull by vm.profile.collectAsStateWithLifecycle()
    val launch by vm.launch.collectAsStateWithLifecycle()
    val read by vm.read.collectAsStateWithLifecycle()
    val diag by vm.diag.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val writeBlocked by vm.writeBlocked.collectAsStateWithLifecycle()
    val nfc by vm.nfc.collectAsStateWithLifecycle()
    val request by vm.request.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Localized here rather than in the view model, which outlives a language change.
    val locale = LocalConfiguration.current.locales[0]
    val profile = remember(profileOrNull, locale) { profileOrNull?.localized(vm.dataPack, context) }
    if (onboarded == null || profile == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
        return
    }
    if (onboarded == false) {
        OnboardingScreen(profile, read, vm::readMachine, vm::cancelRead) { vm.finishOnboarding(it) }
        return
    }

    val habits = remember(history, profile) { Habits(history, profile) }
    val stack = rememberSaveable(saver = routeSaver) { mutableStateListOf() }
    var tab by rememberSaveable { mutableStateOf(Tab.Wash) }
    BackHandler(enabled = stack.isNotEmpty()) { stack.removeAt(stack.lastIndex) }
    BackHandler(enabled = stack.isEmpty() && tab != Tab.Wash) { tab = Tab.Wash }

    AnimatedContent(
        targetState = stack.toList(),
        contentKey = { it.lastOrNull() },
        transitionSpec = {
            val forward = targetState.size >= initialState.size
            ContentTransform(
                slideInHorizontally { if (forward) it / 4 else -it / 4 } + fadeIn(),
                slideOutHorizontally { if (forward) -it / 4 else it / 4 } + fadeOut(),
            )
        },
        label = "nav",
    ) { routes ->
        when (val route = routes.lastOrNull()) {
            null -> Scaffold(
                bottomBar = {
                    ShortNavigationBar {
                        Tab.entries.forEach { t ->
                            ShortNavigationBarItem(
                                selected = tab == t,
                                onClick = { tab = t },
                                icon = { Icon(if (tab == t) t.selected else t.unselected, null) },
                                label = { Text(stringResource(t.label)) },
                            )
                        }
                    }
                },
            ) { padding ->
                AnimatedContent(tab, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tab") { current ->
                    when (current) {
                        Tab.Wash -> HomeScreen(
                            profile = profile,
                            favorites = favorites,
                            habits = habits,
                            nfc = nfc,
                            writeBlocked = writeBlocked,
                            canAdvise = profile.programs.any { it.catalogKey != null },
                            outerPadding = padding,
                            onOpenProgram = { stack.add(Route.Program(it.key)) },
                            onStart = vm::start,
                            onRemoveFavorite = { vm.removeFavorite(it.id) },
                            onEditDial = { stack.add(Route.DialEditor) },
                            onAdvise = { stack.add(Route.Advisor) },
                            onOpenMachine = { tab = Tab.Machine },
                        )
                        Tab.Machine -> MachineScreen(
                            profile = profile,
                            habits = habits,
                            read = read,
                            diag = diag,
                            onDiagnose = vm::diagnose,
                            onResetDiag = vm::resetDiag,
                            history = history,
                            journal = vm.journal,
                            writeBlocked = writeBlocked,
                            outerPadding = padding,
                            onStart = vm::start,
                            onOpenHelp = { tab = Tab.Help },
                            onRead = vm::readMachine,
                            onCancelRead = vm::cancelRead,
                            onEditDial = { stack.add(Route.DialEditor) },
                            onUnblock = { vm.unblockWrites() },
                            onClearJournal = vm::clearJournal,
                        )
                        Tab.Help -> HelpScreen(padding)
                    }
                }
                // Covers the status bar so scrolled content does not show under the clock.
                Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(MaterialTheme.colorScheme.surface))
            }
            is Route.Program -> {
                val program = profile.program(route.key)
                if (program == null) {
                    // The position was deleted from the dial editor.
                    LaunchedEffect(route) { stack.remove(route) }
                } else {
                    ProgramScreen(
                        program = program,
                        profile = profile,
                        initial = route.suggested ?: vm.settingsFor(program, profile),
                        catalog = { vm.dataPack?.program(it.catalogKey) },
                        habits = habits,
                        writeBlocked = writeBlocked,
                        onBack = { stack.remove(route) },
                        onStart = vm::start,
                        onSaveFavorite = { name, settings -> vm.addFavorite(name, settings) },
                    )
                }
            }
            Route.Advisor -> AdvisorScreen(
                profile = profile,
                writeBlocked = writeBlocked,
                onBack = { stack.remove(route) },
                onStart = { vm.start(it.program.name, it.settings) },
                onAdjust = { stack.add(Route.Program(it.program.key, it.settings)) },
            )
            Route.DialEditor -> DialEditorScreen(
                initial = profile,
                dataPack = vm.dataPack,
                writeBlocked = writeBlocked,
                onBack = { stack.remove(route) },
                onSave = vm::saveProfile,
                onTry = { program ->
                    vm.start(
                        context.getString(R.string.dial_test, program.selector),
                        WashSettings(
                            selector = program.selector,
                            temperature = program.temperatureDefault ?: 0,
                            spin = program.spinDefault ?: 0,
                            soil = program.soilDefault ?: 0,
                            programId = program.key,
                        ),
                        remember = false,
                    )
                },
            )
        }
    }

    // Asked when the first wash is sent, since notifications are only used for the end-of-wash reminder.
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(launch is LaunchState.WaitingForTag) {
        if (launch is LaunchState.WaitingForTag && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val sending = request?.let { (_, settings) -> profile.programFor(settings) to context.describe(profile, settings) }
    LaunchSheet(launch, sending?.first, sending?.second, onDismiss = vm::dismissLaunch, onRetry = vm::retry)
}
