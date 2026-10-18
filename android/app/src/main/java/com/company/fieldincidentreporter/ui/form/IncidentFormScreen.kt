package com.company.fieldincidentreporter.ui.form

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.company.fieldincidentreporter.model.Severity
import com.company.fieldincidentreporter.ui.imageUrl

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncidentFormScreen(
    onExit: () -> Unit,
    onSaved: (String) -> Unit,
    onQueued: () -> Unit,
    viewModel: IncidentFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val editing = viewModel.editId != null
    var pendingCameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var severityOpen by remember { mutableStateOf(false) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) viewModel.addPhoto(Uri.parse(uri))
        pendingCameraUri = null
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(3)) { uris ->
        uris.take(3 - state.photos.size).forEach(viewModel::addPhoto)
    }

    fun requestExit() {
        if (state.dirty) confirmDiscard = true else onExit()
    }

    BackHandler(enabled = state.saving || state.dirty) { if (!state.saving) requestExit() }
    LaunchedEffect(state.savedId) { state.savedId?.let(onSaved) }
    LaunchedEffect(state.queued) { if (state.queued) onQueued() }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            text = { Text("Your unsaved report details will be lost.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onExit() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editing) "Edit incident" else "Report incident") },
                navigationIcon = { TextButton(onClick = ::requestExit, enabled = !state.saving) { Text("‹ Back") } },
            )
        },
        bottomBar = {
            Button(
                onClick = viewModel::save,
                enabled = !state.saving && !state.loading,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            ) {
                if (state.saving) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(if (editing) "Save changes" else "Submit report")
            }
        },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (editing && state.error != null && state.photos.isEmpty() && !state.dirty) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error.orEmpty())
                    Button(onClick = viewModel::retryLoad) { Text("Retry") }
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    if (editing) "Update the details and save your changes." else "Capture the issue so the maintenance team can find and assess it.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.error != null) Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)

                Text("Photos *", fontWeight = FontWeight.Bold)
                Text("Add 1 to 3 photos. A wider view and a close-up can help.", style = MaterialTheme.typography.bodySmall)
                if (state.photos.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.photos, key = FormPhoto::key) { photo ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                AsyncImage(
                                    model = photo.localFile ?: photo.url?.let(::imageUrl),
                                    contentDescription = "Selected incident photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.width(100.dp).height(90.dp),
                                )
                                TextButton(onClick = { viewModel.removePhoto(photo.key) }, enabled = !state.saving) { Text("Remove") }
                            }
                        }
                    }
                }
                if (state.photos.size < 3) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = !state.saving, onClick = {
                            try {
                                val uri = viewModel.newCameraUri()
                                pendingCameraUri = uri.toString()
                                camera.launch(uri)
                            } catch (failure: Exception) {
                                viewModel.showError(failure.message ?: "Could not open the camera")
                            }
                        }) { Text("Take photo") }
                        OutlinedButton(enabled = !state.saving, onClick = {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) { Text("Choose photos") }
                    }
                }
                FieldError(state.fieldErrors["photos"])

                OutlinedTextField(
                    value = state.title, onValueChange = viewModel::title,
                    label = { Text("Title *") }, isError = state.fieldErrors.containsKey("title"),
                    singleLine = true, enabled = !state.saving, modifier = Modifier.fillMaxWidth(),
                )
                FieldError(state.fieldErrors["title"])
                OutlinedTextField(
                    value = state.description, onValueChange = viewModel::description,
                    label = { Text("Description *") }, isError = state.fieldErrors.containsKey("description"),
                    minLines = 3, enabled = !state.saving, modifier = Modifier.fillMaxWidth(),
                )
                FieldError(state.fieldErrors["description"])

                ExposedDropdownMenuBox(expanded = severityOpen, onExpandedChange = { severityOpen = it }) {
                    OutlinedTextField(
                        value = state.severity?.name?.lowercase()?.replaceFirstChar(Char::uppercase).orEmpty(),
                        onValueChange = {}, readOnly = true,
                        enabled = !state.saving,
                        label = { Text("Severity *") },
                        isError = state.fieldErrors.containsKey("severity"),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(severityOpen) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(expanded = severityOpen, onDismissRequest = { severityOpen = false }) {
                        Severity.entries.forEach { severity ->
                            DropdownMenuItem(
                                text = { Text(severity.name.lowercase().replaceFirstChar(Char::uppercase)) },
                                onClick = { viewModel.severity(severity); severityOpen = false },
                            )
                        }
                    }
                }
                FieldError(state.fieldErrors["severity"])
                Text("Low · minor or cosmetic   •   Medium · functional issue", style = MaterialTheme.typography.bodySmall)
                Text("High · serious hazard   •   Critical · immediate danger", style = MaterialTheme.typography.bodySmall)

                OutlinedTextField(
                    value = state.location, onValueChange = viewModel::location,
                    label = { Text("Location *") },
                    placeholder = { Text("Building A, Floor 2, Room 204") },
                    isError = state.fieldErrors.containsKey("location"),
                    singleLine = true, enabled = !state.saving, modifier = Modifier.fillMaxWidth(),
                )
                FieldError(state.fieldErrors["location"])
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun FieldError(error: String?) {
    if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}
