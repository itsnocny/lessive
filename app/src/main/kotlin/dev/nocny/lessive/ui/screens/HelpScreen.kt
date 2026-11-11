package dev.nocny.lessive.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.nocny.lessive.R
import dev.nocny.lessive.data.Faults

private val Tips = listOf(R.string.tip_selector, R.string.tip_chip, R.string.tip_still, R.string.tip_read_first)

@Composable
fun HelpScreen(outerPadding: PaddingValues) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var query by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val faults = Faults.filter {
        query.isBlank() || query.trim().lowercase().let { q -> q in it.code.lowercase() || q in context.getString(it.title).lowercase() }
    }
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection).padding(bottom = outerPadding.calculateBottomPadding()),
        contentWindowInsets = WindowInsets(0),
        topBar = { LargeFlexibleTopAppBar(title = { Text(stringResource(R.string.tab_help)) }, subtitle = { Text(stringResource(R.string.help_subtitle)) }, scrollBehavior = scroll) },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 24.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer), shape = MaterialTheme.shapes.extraLarge) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.tips_title), style = MaterialTheme.typography.titleMediumEmphasized)
                        Tips.forEachIndexed { i, tip ->
                            Row {
                                Text("${i + 1}", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.width(12.dp))
                                Text(stringResource(tip), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    placeholder = { Text(stringResource(R.string.help_search_hint)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            itemsIndexed(faults, key = { _, f -> f.code }) { index, fault ->
                var open by remember { mutableStateOf(false) }
                SegmentedListItem(
                    onClick = { open = !open },
                    shapes = ListItemDefaults.segmentedShapes(index, faults.size),
                    modifier = Modifier.animateContentSize(),
                    leadingContent = { Text(fault.code, style = MaterialTheme.typography.titleLargeEmphasized, color = MaterialTheme.colorScheme.primary) },
                    supportingContent = if (open) {
                        {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                                (fault.tips + R.string.fault_call_service).forEach {
                                    Text("• " + stringResource(it), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    } else null,
                ) { Text(stringResource(fault.title), style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}
