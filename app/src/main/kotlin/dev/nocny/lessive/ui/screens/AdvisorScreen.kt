package dev.nocny.lessive.ui.screens

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.nocny.lessive.R
import dev.nocny.lessive.data.DialProgram
import dev.nocny.lessive.data.MachineProfile
import dev.nocny.lessive.data.WashSettings
import dev.nocny.lessive.ui.components.ProgramAvatar
import dev.nocny.lessive.ui.components.describe

private enum class Fabric { Cotton, Synthetic, Mixed, Jeans, Wool }
private enum class Colour { White, Coloured, Dark }
private enum class Soil { Light, Normal, Heavy }

private class Answer(@param:StringRes val label: Int, val icon: @Composable () -> Painter)

private val Questions: List<Pair<Int, Map<Enum<*>, Answer>>> = listOf(
    R.string.q_fabric to mapOf(
        Fabric.Cotton to Answer(R.string.a_cotton) { painterResource(R.drawable.ic_prog_cotton) },
        Fabric.Synthetic to Answer(R.string.a_synthetic) { painterResource(R.drawable.ic_prog_flask) },
        Fabric.Mixed to Answer(R.string.a_mixed) { painterResource(R.drawable.ic_prog_colours) },
        Fabric.Jeans to Answer(R.string.a_jeans) { painterResource(R.drawable.ic_prog_jeans) },
        Fabric.Wool to Answer(R.string.a_wool) { painterResource(R.drawable.ic_prog_butterfly) },
    ),
    R.string.q_colour to mapOf(
        Colour.White to Answer(R.string.a_white) { rememberVectorPainter(Icons.Rounded.AutoAwesome) },
        Colour.Coloured to Answer(R.string.a_coloured) { painterResource(R.drawable.ic_prog_colours) },
        Colour.Dark to Answer(R.string.a_dark) { rememberVectorPainter(Icons.Rounded.DarkMode) },
    ),
    R.string.q_soil to mapOf(
        Soil.Light to Answer(R.string.a_worn) { rememberVectorPainter(Icons.Rounded.Check) },
        Soil.Normal to Answer(R.string.a_normal) { painterResource(R.drawable.ic_prog_cotton) },
        Soil.Heavy to Answer(R.string.a_heavy) { rememberVectorPainter(Icons.Rounded.WaterDrop) },
    ),
)

private val AnswersSaver = listSaver<SnapshotStateList<Int>, Int>(save = { it.toList() }, restore = { it.toMutableStateList() })

class Advice(val program: DialProgram, val settings: WashSettings, @param:StringRes val why: Int)

/** Catalog keys tried in order; the first one present on the dial wins. */
private fun candidates(fabric: Fabric, colour: Colour, soil: Soil): List<Pair<String, Int>> = when (fabric) {
    Fabric.Wool -> listOf(
        "HANDWASH_WOOL" to R.string.r_wool,
        "DELICATES" to R.string.r_delicates,
    )
    Fabric.Jeans -> listOf("JEANS" to R.string.r_jeans)
    Fabric.Mixed -> listOf("MIX_AND_COLOUR" to R.string.r_mix)
    Fabric.Synthetic ->
        if (soil == Soil.Light) listOf("RAPID_30" to R.string.r_light_synthetic, "SYNTHETICS" to R.string.r_synthetic_short)
        else listOf("SYNTHETICS" to R.string.r_synthetic)
    Fabric.Cotton -> when {
        soil == Soil.Heavy && colour == Colour.White -> listOf("COTTONS" to R.string.r_white_heavy)
        soil == Soil.Light -> listOf("PERFECT_COTTON" to R.string.r_perfect_cotton, "ECO_40_60" to R.string.r_eco_short)
        else -> listOf("ECO_40_60" to R.string.r_eco, "COTTONS" to R.string.r_cotton)
    }
} + listOf("MIX_AND_COLOUR" to R.string.r_fallback)

private fun advise(profile: MachineProfile, fabric: Fabric, colour: Colour, soil: Soil): Advice? {
    val (program, why) = candidates(fabric, colour, soil).firstNotNullOfOrNull { (key, why) ->
        profile.programs.firstOrNull { it.catalogKey?.contains(key) == true && !it.locked }?.let { it to why }
    } ?: return null

    val labels = profile.temperatureLabels
    val max = program.temperatureMax ?: labels.lastIndex
    val wanted = when {
        fabric == Fabric.Cotton && soil == Soil.Heavy && colour == Colour.White -> labels.indexOf("60°")
        colour == Colour.Dark -> labels.indexOf("30°")
        colour == Colour.Coloured -> labels.indexOf("40°")
        else -> -1
    }
    val default = program.temperatureDefault ?: minOf(3, max)
    val temperature = when {
        wanted < 0 -> default
        colour == Colour.White -> wanted.coerceAtMost(max)
        else -> minOf(default, wanted)
    }
    val lightest = program.soilMin ?: 1
    val heaviest = program.soilMax ?: lightest
    val soilLevel = when (soil) {
        Soil.Light -> lightest
        Soil.Normal -> (lightest + heaviest) / 2
        Soil.Heavy -> heaviest
    }
    return Advice(
        program = program,
        settings = WashSettings(
            selector = program.selector,
            temperature = temperature,
            spin = program.spinDefault ?: minOf(2, profile.spinLabels.lastIndex),
            soil = soilLevel,
            programId = program.key,
        ),
        why = why,
    )
}

@Composable
fun AdvisorScreen(
    profile: MachineProfile,
    writeBlocked: Boolean,
    onBack: () -> Unit,
    onStart: (Advice) -> Unit,
    onAdjust: (Advice) -> Unit,
) {
    val answers = rememberSaveable(saver = AnswersSaver) { mutableStateListOf() }
    val step = answers.size
    val progress by animateFloatAsState(step / Questions.size.toFloat(), label = "progress")
    val back: () -> Unit = {
        if (answers.isEmpty()) onBack() else answers.removeAt(answers.lastIndex)
    }
    BackHandler(onBack = back)

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = back) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(if (answers.isEmpty()) R.string.action_back else R.string.previous_question))
                }
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.advisor_title), style = MaterialTheme.typography.titleMediumEmphasized)
            }
            LinearProgressIndicator(
                progress = { progress },
                color = MaterialTheme.colorScheme.tertiary,
                strokeCap = StrokeCap.Round,
                modifier = Modifier.fillMaxWidth().height(8.dp),
            )

            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val forward = targetState > initialState
                    (slideInHorizontally { if (forward) it / 3 else -it / 3 } + fadeIn()) togetherWith fadeOut()
                },
                label = "question",
            ) { index ->
                if (index < Questions.size) {
                    Question(index) { answers.add(it) }
                } else {
                    val advice = advise(
                        profile,
                        Fabric.entries[answers[0]],
                        Colour.entries[answers[1]],
                        Soil.entries[answers[2]],
                    )
                    Result(advice, profile, writeBlocked, onStart, onAdjust, onRestart = { answers.clear() })
                }
            }
        }
    }
}

@Composable
private fun Question(index: Int, onPick: (Int) -> Unit) {
    val (title, answers) = Questions[index]
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.question_n, index + 1, Questions.size), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
        Text(stringResource(title), style = MaterialTheme.typography.headlineMediumEmphasized, modifier = Modifier.padding(bottom = 8.dp))
        answers.entries.forEach { (value, answer) ->
            Card(
                onClick = { onPick(value.ordinal) },
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.heightIn(min = 72.dp).padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(44.dp).background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(answer.icon(), null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(24.dp)) }
                    Spacer(Modifier.width(16.dp))
                    Text(stringResource(answer.label), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun Result(
    advice: Advice?,
    profile: MachineProfile,
    writeBlocked: Boolean,
    onStart: (Advice) -> Unit,
    onAdjust: (Advice) -> Unit,
    onRestart: () -> Unit,
) {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (advice == null) {
            Text(stringResource(R.string.no_match_title), style = MaterialTheme.typography.headlineSmallEmphasized)
            Text(
                stringResource(R.string.no_match_text),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onRestart) { Text(stringResource(R.string.action_start_over)) }
            return@Column
        }
        Text(stringResource(R.string.we_suggest), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
        val colors = MaterialTheme.colorScheme
        Card(
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = colors.primaryContainer, contentColor = colors.onPrimaryContainer),
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ProgramAvatar(advice.program, size = 72.dp, container = colors.primary, content = colors.onPrimary)
                Text(advice.program.name, style = MaterialTheme.typography.headlineLargeEmphasized)
                Text(LocalContext.current.describe(profile, advice.settings), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(advice.why), style = MaterialTheme.typography.bodyLarge)
                Button(
                    onClick = { onStart(advice) },
                    enabled = !writeBlocked,
                    modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight),
                    contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.action_launch), style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { onAdjust(advice) }, modifier = Modifier.weight(1f).height(48.dp)) { Text(stringResource(R.string.action_adjust)) }
            FilledTonalButton(onClick = onRestart, modifier = Modifier.weight(1f).height(48.dp)) { Text(stringResource(R.string.action_start_over)) }
        }
    }
}
