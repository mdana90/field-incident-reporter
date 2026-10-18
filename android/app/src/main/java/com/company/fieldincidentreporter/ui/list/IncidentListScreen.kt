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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.company.fieldincidentreporter.model.Incident
import com.company.fieldincidentreporter.data.offline.DeliveryState
import com.company.fieldincidentreporter.data.offline.PendingReport
import com.company.fieldincidentreporter.ui.imageUrl
import com.company.fieldincidentreporter.ui.incidentLocalDate
import com.company.fieldincidentreporter.ui.theme.Amber
import com.company.fieldincidentreporter.ui.theme.TealSoft

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncidentListScreen(
    onCreate: () -> Unit,
    onOpen: (String) -> Unit,
    viewModel: IncidentListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

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
        when {
            state.loading && state.incidents.isEmpty() && state.pending.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.error != null && state.incidents.isEmpty() && state.pending.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Text(state.error.orEmpty())
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = viewModel::refresh) { Text("Retry") }
                }
            }
            state.incidents.isEmpty() && state.pending.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Text("No reports yet", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text("Capture an issue to start your field reports.")
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onCreate) { Text("Report incident") }
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Column(modifier = Modifier.padding(bottom = 8.dp)) {
                        Text("Your field reports", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        if (state.error != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                                TextButton(onClick = viewModel::refresh) { Text("Retry") }
                            }
                        }
                    }
                }
                if (state.pending.isNotEmpty()) {
                    item { Text("Waiting reports", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    items(state.pending, key = { "pending-${it.id}" }) { report ->
                        PendingCard(
                            report = report,
                            onRetry = { viewModel.retry(report.id) },
                            onDiscard = { viewModel.discard(report.id) },
                        )
                    }
                }
                item { Text("Recent incidents", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                items(state.incidents, key = Incident::id) { incident ->
                    IncidentCard(incident = incident, onClick = { onOpen(incident.id) })
                }
            }
        }
    }
}

@Composable
private fun PendingCard(report: PendingReport, onRetry: () -> Unit, onDiscard: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = TealSoft),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(report.title, fontWeight = FontWeight.Bold)
            Text(report.location, style = MaterialTheme.typography.bodySmall)
            Text(
                when (report.state) {
                    DeliveryState.WAITING -> "Waiting to send"
                    DeliveryState.SENDING -> "Sending"
                    DeliveryState.NEEDS_ATTENTION -> "Needs attention"
                },
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
            )
            Row {
                if (report.state != DeliveryState.SENDING) TextButton(onClick = onRetry) { Text("Retry") }
                if (!report.attempted || report.state == DeliveryState.NEEDS_ATTENTION) {
                    TextButton(onClick = onDiscard) { Text("Discard") }
                }
            }
        }
    }
}

@Composable
private fun IncidentCard(incident: Incident, onClick: () -> Unit) {
    val context = LocalContext.current
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE3EAE9)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        AsyncImage(
            model = incident.photos.firstOrNull()?.url?.let(::imageUrl),
            contentDescription = "Photo of ${incident.title}",
            modifier = Modifier.fillMaxWidth().height(150.dp),
            contentScale = ContentScale.Crop,
        )
        Column(modifier = Modifier.padding(14.dp)) {
            Text(incident.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    incident.status.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase),
                    color = Amber,
                    modifier = Modifier.padding(end = 10.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    "${incident.severity.name.lowercase().replaceFirstChar(Char::uppercase)} severity",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.width(10.dp))
                Text(incidentLocalDate(incident.createdAt, context), style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(7.dp))
            Text("⌖  ${incident.location}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}
