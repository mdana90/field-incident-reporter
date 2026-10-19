package com.company.fieldincidentreporter.ui.list

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.company.fieldincidentreporter.model.DraftReport
import com.company.fieldincidentreporter.model.DraftState
import com.company.fieldincidentreporter.ui.label
import com.company.fieldincidentreporter.ui.theme.Amber
import com.company.fieldincidentreporter.ui.theme.TealSoft

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncidentListScreen(
    onCreate: () -> Unit,
    onOpen: (String) -> Unit,
    onOpenDraft: (String) -> Unit,
    viewModel: IncidentListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<DraftReport?>(null) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    deleting?.let { draft ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete saved draft?") },
            text = { Text(if (draft.state == DraftState.UNCONFIRMED) "The server may already have received this report. Delete this device's draft and local photos?" else "This unsent report and its local photos will be removed from this device.") },
            confirmButton = { TextButton(onClick = { viewModel.deleteDraft(draft.id); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Incidents", fontWeight = FontWeight.Bold) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreate,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
            ) { Text("+  Report incident") }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { viewModel.refresh(fromPull = true) },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when {
                    state.loading && !state.refreshing && state.isEmpty -> item {
                        Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                    state.error != null && state.isEmpty -> item {
                        Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                                Text(state.error.orEmpty())
                                Spacer(Modifier.height(8.dp))
                                Button(onClick = { viewModel.refresh() }) { Text("Retry") }
                            }
                        }
                    }
                    state.isEmpty -> item {
                        Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                                Text("No reports yet", style = MaterialTheme.typography.headlineSmall)
                                Spacer(Modifier.height(8.dp))
                                Text("Capture an issue to start your field reports.")
                                if (state.draftError != null) Text(state.draftError.orEmpty(), color = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.height(16.dp))
                                Button(onClick = onCreate) { Text("Report incident") }
                            }
                        }
                    }
                    else -> {
                        item {
                            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                                Text("Your field reports", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                if (state.error != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                                        TextButton(onClick = { viewModel.refresh() }) { Text("Retry") }
                                    }
                                }
                                if (state.draftError != null) Text(state.draftError.orEmpty(), color = MaterialTheme.colorScheme.error)
                            }
                        }
                        if (state.drafts.isNotEmpty()) {
                            item { Text("Saved drafts · manual send required", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                            items(state.drafts, key = { "draft-${it.id}" }) { report ->
                                DraftCard(
                                    report = report,
                                    onOpen = { onOpenDraft(report.id) },
                                    onDelete = { deleting = report },
                                )
                            }
                        }
                        item { Text("Submitted incidents", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                        items(state.incidents, key = { it.incident.id }) { item ->
                            IncidentCard(item = item, onClick = { onOpen(item.incident.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DraftCard(report: DraftReport, onOpen: () -> Unit, onDelete: () -> Unit) {
    Card(
        onClick = onOpen,
        colors = CardDefaults.cardColors(containerColor = TealSoft),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(report.input.title, fontWeight = FontWeight.Bold)
            Text(report.input.location, style = MaterialTheme.typography.bodySmall)
            Text(
                if (report.state == DraftState.UNCONFIRMED) "Submission not confirmed · tap to retry" else "Saved on device · tap to send",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
            )
            Row {
                TextButton(onClick = onOpen) { Text("Open") }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

@Composable
private fun IncidentCard(item: IncidentListItem, onClick: () -> Unit) {
    val incident = item.incident
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE3EAE9)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        AsyncImage(
            model = incident.photos.firstOrNull()?.url,
            contentDescription = "Photo of ${incident.title}",
            modifier = Modifier.fillMaxWidth().height(150.dp),
            contentScale = ContentScale.Crop,
        )
        Column(modifier = Modifier.padding(14.dp)) {
            Text(incident.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Submitted", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    incident.status.label,
                    color = Amber,
                    modifier = Modifier.padding(end = 10.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    "${incident.severity.label} severity",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.width(10.dp))
                Text(item.reportedOn, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(7.dp))
            Text("⌖  ${incident.location}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}
