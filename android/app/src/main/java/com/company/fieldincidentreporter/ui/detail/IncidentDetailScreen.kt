package com.company.fieldincidentreporter.ui.detail

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.company.fieldincidentreporter.model.Incident
import com.company.fieldincidentreporter.ui.imageUrl
import com.company.fieldincidentreporter.ui.theme.Amber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncidentDetailScreen(
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: IncidentDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    LaunchedEffect(state.deleted) { if (state.deleted) onDeleted() }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete \"${state.incident?.title.orEmpty()}\"?") },
            text = { Text("This report will be removed from the incident list.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete() }) { Text("Delete report", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Incident details") },
                navigationIcon = { TextButton(onClick = onBack) { Text("‹ Back") } },
            )
        },
        bottomBar = {
            if (state.incident != null && !state.deleted) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(onClick = onEdit, enabled = !state.deleting, modifier = Modifier.weight(1f)) { Text("Edit report") }
                    OutlinedButton(onClick = { confirmDelete = true }, enabled = !state.deleting) {
                        Text(if (state.deleting) "Deleting…" else "Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
    ) { padding ->
        when {
            state.loading && state.incident == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.incident == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Text(state.error ?: "Report unavailable")
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = viewModel::refresh) { Text("Retry") }
                }
            }
            else -> DetailContent(
                incident = requireNotNull(state.incident),
                error = state.error,
                onRetry = viewModel::refresh,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun DetailContent(incident: Incident, error: String?, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.foundation.lazy.LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(incident.photos, key = { it.id }) { photo ->
                    AsyncImage(
                        model = imageUrl(photo.url),
                        contentDescription = "Photo of ${incident.title}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.width(320.dp).height(220.dp),
                    )
                }
            }
        }
        item {
            Column(modifier = Modifier.padding(18.dp)) {
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry) { Text("Retry") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(incident.status.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase), color = Amber)
                    Text("${incident.severity.name.lowercase().replaceFirstChar(Char::uppercase)} severity", color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(8.dp))
                Text(incident.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Reported ${incident.createdAt.take(10)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(24.dp))
                Text("LOCATION", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Text(incident.location, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(24.dp))
                Text("DESCRIPTION", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Text(incident.description, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
