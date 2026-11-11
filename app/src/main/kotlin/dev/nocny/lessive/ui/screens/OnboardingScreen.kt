package dev.nocny.lessive.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalLaundryService
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.nocny.lessive.R
import dev.nocny.lessive.ReadState
import dev.nocny.lessive.data.MachineProfile

@Composable
fun OnboardingScreen(
    profile: MachineProfile,
    read: ReadState,
    onRead: () -> Unit,
    onCancelRead: () -> Unit,
    onFinish: (MachineProfile) -> Unit,
) {
    var model by rememberSaveable { mutableStateOf(profile.model) }
    Surface(Modifier.fillMaxSize()) { Column(
        Modifier.fillMaxSize().safeDrawingPadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier.size(96.dp).background(MaterialTheme.colorScheme.primaryContainer, MaterialShapes.Cookie9Sided.toShape()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.LocalLaundryService, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displayMediumEmphasized)
        Text(
            stringResource(R.string.onboarding_text),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = model,
            onValueChange = { model = it },
            label = { Text(stringResource(R.string.machine_model)) },
            supportingText = { Text(stringResource(R.string.model_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        MachineReadCard(read, onRead, onCancelRead)
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { onFinish(profile.copy(model = model.trim().ifBlank { profile.model })) },
            modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight),
        ) {
            Text(stringResource(if (read is ReadState.Done) R.string.action_continue else R.string.action_skip), style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
        }
    } }
}
