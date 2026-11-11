package dev.nocny.lessive

import android.app.Application
import androidx.annotation.StringRes
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.nocny.lessive.data.DataPack
import dev.nocny.lessive.data.DialProgram
import dev.nocny.lessive.data.Favorite
import dev.nocny.lessive.data.HistoryEntry
import dev.nocny.lessive.data.MachineProfile
import dev.nocny.lessive.data.Outcome
import dev.nocny.lessive.data.Store
import dev.nocny.lessive.data.WashSettings
import dev.nocny.lessive.nfc.NfcHub
import dev.nocny.lessive.nfc.NotAWasherTag
import dev.nocny.lessive.nfc.withWasher
import dev.nocny.lessive.notify.WashNotifier
import dev.nocny.lessive.protocol.Answers
import dev.nocny.lessive.protocol.M24srSession
import dev.nocny.lessive.protocol.ReadRequest
import dev.nocny.lessive.protocol.StartCycle
import dev.nocny.lessive.protocol.StatusFile
import dev.nocny.lessive.protocol.WasherException
import dev.nocny.lessive.protocol.toHex
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed interface LaunchState {
    data object Idle : LaunchState
    data class WaitingForTag(val title: String, val retrying: Boolean = false) : LaunchState
    data class Running(val title: String, val step: M24srSession.Step) : LaunchState
    data class Success(val title: String, val endsAt: Long?) : LaunchState
    data class Failed(val title: String, val headline: String, val detail: String, val locked: Boolean = false) : LaunchState
}

sealed interface ReadState {
    data object Idle : ReadState
    data object Waiting : ReadState
    data class Done(val status: StatusFile, val commandFile: ByteArray?) : ReadState
    data class Failed(val message: String) : ReadState
}

sealed interface DiagState {
    data object Idle : DiagState
    data class Running(val step: M24srSession.Step?) : DiagState
    data class Done(val version: String?, val counters: List<Int>) : DiagState
    data class Failed(val headline: String, val detail: String) : DiagState
}

class LessiveViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    val dataPack: DataPack? = DataPack.load(app)

    val profile = store.profile.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val favorites = store.favorites.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val history = store.history.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val lastSettings = store.lastSettings.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val onboarded = store.onboarded.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val writeBlocked = store.writeBlocked.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val nfc = NfcHub.availability

    private val _launch = MutableStateFlow<LaunchState>(LaunchState.Idle)
    val launch: StateFlow<LaunchState> = _launch.asStateFlow()

    private val _read = MutableStateFlow<ReadState>(ReadState.Idle)
    val read: StateFlow<ReadState> = _read.asStateFlow()

    private val _diag = MutableStateFlow<DiagState>(DiagState.Idle)
    val diag: StateFlow<DiagState> = _diag.asStateFlow()

    /** Raw APDU exchanges of the current session, for the "Journal" screen. */
    val journal = mutableStateListOf<String>()

    private var job: Job? = null

    private val _request = MutableStateFlow<Pair<String, WashSettings>?>(null)
    /** What the launch sheet is sending, for its header. */
    val request: StateFlow<Pair<String, WashSettings>?> = _request.asStateFlow()

    fun settingsFor(program: DialProgram, profile: MachineProfile): WashSettings {
        // Settings saved before programs had ids are ignored: they came from dial tests.
        val remembered = lastSettings.value.firstOrNull { it.programId == program.key }
        return remembered ?: WashSettings(
            selector = program.selector,
            temperature = program.temperatureDefault ?: program.forcedTemperature ?: minOf(3, profile.temperatureLabels.lastIndex),
            spin = program.spinDefault ?: program.forcedSpin ?: minOf(3, profile.spinLabels.lastIndex),
            soil = program.soilDefault ?: 1,
            programId = program.key,
        )
    }

    /**
     * Replaces a dial nobody has checked yet with the official definition from the data
     * pack, matched by appliance code (read from the tag) or by model name.
     */
    private fun adoptOfficialProfile(applianceCode: String? = null) {
        val current = profile.value ?: return
        val official = dataPack?.machineFor(current.model, applianceCode) ?: return
        if (current.verified && current.applianceCode == official.applianceCode) return
        if (current.verified && current.applianceCode == null && current.programs.none { it.name.startsWith("Position ") }) return
        viewModelScope.launch { store.saveProfile(official) }
    }

    init {
        viewModelScope.launch {
            store.profile.first()
            adoptOfficialProfile()
        }
    }

    /** [remember] is false for dial tests, whose settings say nothing about how you wash. */
    fun start(title: String, settings: WashSettings, remember: Boolean = true) {
        if (writeBlocked.value) return
        _request.value = title to settings
        job?.cancel()
        job = viewModelScope.launch {
            if (remember) store.rememberSettings(settings, lastSettings.value)
            val command = StartCycle(
                selector = settings.selector,
                temperature = settings.temperature,
                spin = settings.spin,
                soil = settings.soil,
                extraRinse = if (settings.extraRinse) 1 else 0,
                delayHalfHours = settings.delayHalfHours,
            )
            var retrying = false
            while (true) {
                _launch.value = LaunchState.WaitingForTag(title, retrying)
                try {
                    try {
                        withWasher(::log) { session ->
                            log("— START ${command.toRecord().toHex()}")
                            session.startCycle(command) { step -> _launch.value = LaunchState.Running(title, step) }
                        }
                    } catch (e: WasherException) {
                        if (!e.mayStillBeProcessed()) throw e
                        _launch.value = LaunchState.Running(title, M24srSession.Step.WaitingForMachine)
                        confirmAfterLoss(StartCycle.ACTION) ?: throw e
                    }
                    val duration = profile.value?.programFor(settings)?.durationFor(settings.soil)
                    val startAt = System.currentTimeMillis() + (settings.delayHalfHours ?: 0) * 30 * 60_000L
                    val endsAt = duration?.let { startAt + it * 60_000L }
                    if (endsAt != null) WashNotifier.started(getApplication(), title, startAt, endsAt)
                    _launch.value = LaunchState.Success(title, endsAt)
                    record(title, settings, Outcome.Started)
                    return@launch
                } catch (e: IOException) {
                    // Lost before anything was committed: nothing happened, just wait for the tag again.
                    retrying = true
                } catch (e: WasherException) {
                    fail(title, settings, e)
                    return@launch
                } catch (e: NotAWasherTag) {
                    _launch.value = LaunchState.Failed(title, text(R.string.not_machine_title), text(R.string.not_machine_text))
                    return@launch
                }
            }
        }
    }

    /**
     * Asks the machine for its cycle counters and software version: the full write →
     * GPO → acknowledgement chain, without starting anything.
     */
    fun diagnose() {
        if (writeBlocked.value) return
        job?.cancel()
        job = viewModelScope.launch {
            _diag.value = DiagState.Running(null)
            while (true) {
                try {
                    val result = try {
                        withWasher(::log) { session ->
                        log("— Diagnostic: cycle counters")
                        session.request(ReadRequest.ProgramCycleCounters) { _diag.value = DiagState.Running(it) }
                        }
                    } catch (e: WasherException) {
                        if (!e.mayStillBeProcessed()) throw e
                        _diag.value = DiagState.Running(M24srSession.Step.WaitingForMachine)
                        val record = confirmAfterLoss(ReadRequest.ProgramCycleCounters.action) ?: throw e
                        ReadRequest.ProgramCycleCounters.answer(record)
                    }.let { DiagState.Done(null, Answers.programCounters(it)) }
                    _diag.value = result
                    return@launch
                } catch (_: IOException) {
                    _diag.value = DiagState.Running(null)
                } catch (e: WasherException) {
                    val (headline, detail, _) = explain(e)
                    _diag.value = DiagState.Failed(headline, detail)
                    return@launch
                } catch (_: NotAWasherTag) {
                    _diag.value = DiagState.Failed(text(R.string.not_machine_title), text(R.string.not_machine_short))
                    return@launch
                }
            }
        }
    }

    /**
     * The command is on the tag and the machine was signalled: it may answer once it
     * gets the tag to itself, either by cutting our session or after we release it.
     */
    private fun WasherException.mayStillBeProcessed() =
        this is WasherException.LostAfterCommit || this is WasherException.NoAcknowledgement

    /**
     * Some machines take the tag over I²C as soon as the GPO goes low, which kills the
     * phone's RF session. The command is then being processed: reconnect when Android
     * rediscovers the tag and read the command file until the marker flips to 0x00.
     */
    private suspend fun confirmAfterLoss(action: Int): ByteArray? = withTimeoutOrNull(15_000) {
        log("— Session dropped after sending, reconnecting to read the answer")
        while (true) {
            val content = try {
                withWasher(::log) { it.readCommandFile() }
            } catch (_: IOException) {
                null
            } catch (_: WasherException) {
                null
            }
            if (content != null && content.size > 5 && content[4].toInt() == 0 && (content[5].toInt() and 0xFF) == action) {
                log("— Machine answer received after reconnecting")
                return@withTimeoutOrNull content
            }
            delay(400)
        }
        @Suppress("UNREACHABLE_CODE")
        null
    }

    fun resetDiag() {
        job?.cancel()
        _diag.value = DiagState.Idle
    }

    private suspend fun fail(title: String, settings: WashSettings, e: WasherException) {
        val (headline, detail, outcome) = explain(e)
        _launch.value = LaunchState.Failed(title, headline, detail, locked = e is WasherException.Locked)
        record(title, settings, outcome)
    }

    private suspend fun explain(e: WasherException): Triple<String, String, Outcome> =
        when (e) {
            is WasherException.Locked -> {
                store.setWriteBlocked(true)
                val left = e.retriesLeft?.let { app().resources.getQuantityString(R.plurals.attempts_left, it, it) }.orEmpty()
                Triple(text(R.string.locked_title), text(R.string.locked_text, left), Outcome.Refused)
            }
            is WasherException.Refused -> Triple(text(R.string.refused_title), text(R.string.refused_text, e.sw), Outcome.Refused)
            is WasherException.NoAcknowledgement -> Triple(text(R.string.no_answer_title), text(R.string.no_answer_text), Outcome.NoAnswer)
            is WasherException.LostAfterCommit -> Triple(text(R.string.lost_title), text(R.string.lost_text), Outcome.Interrupted)
            is WasherException.UnexpectedStatus -> Triple(text(R.string.unexpected_title), text(R.string.unexpected_text, e.step, e.sw), Outcome.Refused)
        }

    private suspend fun record(title: String, settings: WashSettings, outcome: Outcome) {
        store.addHistory(HistoryEntry(System.currentTimeMillis(), title, settings, outcome), history.value)
    }

    fun retry() {
        _request.value?.let { (title, settings) -> start(title, settings, remember = settings.programId.isNotEmpty()) }
    }

    fun dismissLaunch() {
        job?.cancel()
        _launch.value = LaunchState.Idle
    }

    fun readMachine() {
        job?.cancel()
        job = viewModelScope.launch {
            _read.value = ReadState.Waiting
            while (true) {
                try {
                    val result = withWasher(::log) { session ->
                        log("— Reading status")
                        val status = session.readStatus()
                        val command = runCatching { session.readCommandFile() }.getOrNull()
                        ReadState.Done(status, command)
                    }
                    _read.value = result
                    adoptOfficialProfile(result.status.applianceCode)
                    return@launch
                } catch (_: IOException) {
                    // Moved away mid-read: keep waiting.
                } catch (e: NotAWasherTag) {
                    _read.value = ReadState.Failed(text(R.string.not_machine_short))
                    return@launch
                } catch (e: WasherException) {
                    _read.value = ReadState.Failed(e.message ?: text(R.string.read_error))
                    return@launch
                }
            }
        }
    }

    fun cancelRead() {
        job?.cancel()
        _read.value = ReadState.Idle
    }

    fun saveProfile(profile: MachineProfile) = viewModelScope.launch { store.saveProfile(profile) }

    fun finishOnboarding(profile: MachineProfile) = viewModelScope.launch {
        store.saveProfile(profile)
        store.setOnboarded(true)
    }

    fun unblockWrites() = viewModelScope.launch { store.setWriteBlocked(false) }

    fun addFavorite(name: String, settings: WashSettings) = viewModelScope.launch {
        store.saveFavorites(favorites.value + Favorite(UUID.randomUUID().toString(), name, settings))
    }

    fun removeFavorite(id: String) = viewModelScope.launch {
        store.saveFavorites(favorites.value.filterNot { it.id == id })
    }

    fun clearJournal() = journal.clear()

    private fun app() = getApplication<Application>()

    private fun text(@StringRes id: Int, vararg args: Any) = app().getString(id, *args)

    private fun log(line: String) {
        android.util.Log.i("LessiveNfc", line)
        viewModelScope.launch {
            journal.add(line)
            if (journal.size > 400) journal.removeRange(0, journal.size - 400)
        }
    }
}
