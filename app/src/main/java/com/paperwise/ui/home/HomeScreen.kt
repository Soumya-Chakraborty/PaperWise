package com.paperwise.ui.home

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.paperwise.data.local.entity.PdfDocument
import java.text.SimpleDateFormat
import java.util.*

private val AcrobatWorkspace = Color(0xFFF2F2F2)
private val AcrobatTopBar = Color(0xFF1F1F1F)
private val AcrobatWorkspaceDark = Color(0xFF1A1A1A)
private val AcrobatTopBarLight = Color(0xFF2A2A2A)

/**
 * Home screen showing recent PDFs and file browser.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(
    onNavigateToPdf: (String, String?) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val requiresAllFilesAccess = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    val requiresLegacyStoragePermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q
    val isDarkTheme = isSystemInDarkTheme()
    val topBarColor = if (isDarkTheme) AcrobatTopBar else AcrobatTopBarLight
    val workspaceColor = if (isDarkTheme) AcrobatWorkspaceDark else AcrobatWorkspace
    var hasRequestedPermission by rememberSaveable { mutableStateOf(false) }
    var hasAllFilesAccess by rememberSaveable {
        mutableStateOf(!requiresAllFilesAccess || Environment.isExternalStorageManager())
    }

    val permissionState = rememberPermissionState(
        permission = Manifest.permission.READ_EXTERNAL_STORAGE,
        onPermissionResult = { isGranted ->
            if (isGranted) {
                // When permission is granted, load all PDF files from device storage
                viewModel.loadAllPdfFiles()
            } else {
                // If permission denied, load recent documents from database
                viewModel.loadRecentDocuments()
            }
        }
    )

    DisposableEffect(lifecycleOwner, requiresAllFilesAccess) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && requiresAllFilesAccess) {
                hasAllFilesAccess = Environment.isExternalStorageManager()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // Bootstrap loading/permission once and react to grant state changes.
    LaunchedEffect(
        requiresAllFilesAccess,
        hasAllFilesAccess,
        requiresLegacyStoragePermission,
        permissionState.status.isGranted,
        hasRequestedPermission
    ) {
        if (requiresAllFilesAccess && !hasAllFilesAccess) {
            viewModel.loadRecentDocuments()
        } else if (requiresLegacyStoragePermission && !permissionState.status.isGranted) {
            if (!hasRequestedPermission) {
                hasRequestedPermission = true
                permissionState.launchPermissionRequest()
            } else {
                viewModel.loadRecentDocuments()
            }
        } else {
            viewModel.loadAllPdfFiles()
        }
    }
    
    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Some providers do not grant persistable permissions.
            }
            onNavigateToPdf(uri.toString(), resolveDisplayName(context, uri))
        }
    }
    val openFilePicker = { filePickerLauncher.launch(arrayOf("application/pdf")) }
    val openAllFilesAccessSettings = {
        val intent = Intent(
            "android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION",
            "package:${context.packageName}".toUri()
        )
        val fallbackIntent = Intent("android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION")
        runCatching { context.startActivity(intent) }
            .onFailure { context.startActivity(fallbackIntent) }
    }
    
    Scaffold(
        containerColor = workspaceColor,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = topBarColor,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                title = {
                    Column {
                        Text("PaperWise")
                        Text(
                            text = when (uiState) {
                                is HomeUiState.Success -> "${(uiState as HomeUiState.Success).documents.size} documents"
                                is HomeUiState.Empty -> "No saved documents"
                                else -> "Your PDF workspace"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFD3D3D3)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            when {
                                requiresAllFilesAccess && !hasAllFilesAccess -> openAllFilesAccessSettings()
                                !requiresLegacyStoragePermission || permissionState.status.isGranted -> {
                                    viewModel.loadAllPdfFiles()
                                }
                                else -> permissionState.launchPermissionRequest()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                modifier = Modifier.navigationBarsPadding(),
                onClick = {
                    when {
                        requiresAllFilesAccess && !hasAllFilesAccess -> openAllFilesAccessSettings()
                        !requiresLegacyStoragePermission || permissionState.status.isGranted -> {
                            openFilePicker()
                        }
                        else -> permissionState.launchPermissionRequest()
                    }
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open PDF")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search bar
            SearchBar(
                query = searchQuery,
                onQueryChange = viewModel::onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
            
            val hasStorageAccess = when {
                requiresAllFilesAccess -> hasAllFilesAccess
                requiresLegacyStoragePermission -> permissionState.status.isGranted
                else -> true
            }

            if (!hasStorageAccess) {
                PermissionRequestCard(
                    title = if (requiresAllFilesAccess) {
                        "All Files Access Required"
                    } else {
                        "Storage Permission Required"
                    },
                    description = if (requiresAllFilesAccess) {
                        "Allow all files access to scan PDFs across device storage, or open PDFs manually."
                    } else {
                        "Allow storage permission to scan PDFs on your device, or pick files manually."
                    },
                    actionLabel = if (requiresAllFilesAccess || permissionState.status.shouldShowRationale) {
                        "Open Settings"
                    } else {
                        "Grant Permission"
                    },
                    onRequestPermission = {
                        if (requiresAllFilesAccess) {
                            openAllFilesAccessSettings()
                        } else {
                            permissionState.launchPermissionRequest()
                        }
                    },
                    onOpenSettings = {
                        if (requiresAllFilesAccess) {
                            openAllFilesAccessSettings()
                        } else {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        }
                    },
                    shouldShowRationale = permissionState.status.shouldShowRationale
                )
            } else {
                // Content
                when (uiState) {
                    is HomeUiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    is HomeUiState.Empty -> {
                        EmptyState(
                            onOpenFile = openFilePicker
                        )
                    }
                    is HomeUiState.Success -> {
                        val documents = (uiState as HomeUiState.Success).documents
                        Text(
                            text = "Recent PDFs",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        DocumentGrid(
                            documents = documents,
                            onDocumentClick = onNavigateToPdf,
                            onDeleteDocument = viewModel::deleteDocument
                        )
                    }
                    is HomeUiState.Error -> {
                        val error = uiState as HomeUiState.Error
                        ErrorState(
                            message = error.message,
                            onRetry = { viewModel.loadAllPdfFiles() },
                            onOpenFile = openFilePicker
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text("Search by file name") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear")
                }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.large
    )
}

@Composable
private fun PermissionRequestCard(
    title: String,
    description: String,
    actionLabel: String,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    shouldShowRationale: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = if (shouldShowRationale) onOpenSettings else onRequestPermission
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun EmptyState(
    onOpenFile: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No Recent Files",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Open a PDF to start reading.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onOpenFile) {
                Icon(Icons.Default.Add, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open PDF")
            }
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    onOpenFile: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Could Not Load PDFs",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onRetry) {
                    Text("Retry")
                }
                Button(onClick = onOpenFile) {
                    Text("Pick PDF")
                }
            }
        }
    }
}

@Composable
private fun DocumentGrid(
    documents: List<PdfDocument>,
    onDocumentClick: (String, String?) -> Unit,
    onDeleteDocument: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(documents, key = { it.filePath }) { document ->
            DocumentCard(
                document = document,
                onClick = { onDocumentClick(document.filePath, document.fileName) },
                onDelete = { onDeleteDocument(document.filePath) }
            )
        }
    }
}

private fun resolveDisplayName(context: android.content.Context, uri: Uri): String? {
    return runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameColumn != -1 && cursor.moveToFirst()) cursor.getString(nameColumn) else null
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentCard(
    document: PdfDocument,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
                
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                showDeleteDialog = true
                                showMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, null) }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = document.fileName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            Text(
                text = "${document.pageCount} pages",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Text(
                text = "Opened ${dateFormat.format(Date(document.lastOpenedTimestamp))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete from recent") },
            text = {
                Text("Remove \"${document.fileName}\" from your recent list?")
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete")
                }
            }
        )
    }
}
