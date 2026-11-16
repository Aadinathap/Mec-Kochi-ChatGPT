package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderCopy
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.DepartmentResource
import com.example.data.model.MecChannels
import com.example.data.model.MecDepartment
import com.example.data.storage.DownloadedPdfInfo
import com.example.data.storage.ResourcePdfDownloader
import com.example.ui.components.MecEmptyStateCard
import com.example.ui.components.MecLoadingState
import com.example.ui.components.MecTagPill
import com.example.ui.theme.departmentAccentColor
import com.example.ui.viewmodel.UiState

@Composable
fun DepartmentResourcesScreen(
    resourcesState: UiState<List<DepartmentResource>>,
    selectedDept: String,
    selectedSemester: String,
    selectedType: String,
    searchQuery: String,
    downloadingResourceIds: Set<String> = emptySet(),
    resourceDownloadProgress: Map<String, Float> = emptyMap(),
    downloadedResources: Map<String, DownloadedPdfInfo> = emptyMap(),
    onSelectDept: (String) -> Unit,
    onSelectSemester: (String) -> Unit,
    onSelectType: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onUpvote: (DepartmentResource) -> Unit,
    onDownloadResource: (DepartmentResource) -> Unit = {},
    onOpenDownloadedPdf: (DownloadedPdfInfo) -> Unit = {},
    onOpenAddResource: () -> Unit,
    modifier: Modifier = Modifier
) {
    val resources = (resourcesState as? UiState.Success)?.data.orEmpty()
    val focusManager = LocalFocusManager.current
    val hasActiveFilter = searchQuery.isNotBlank() ||
        selectedDept != "ALL" ||
        selectedSemester != "ALL" ||
        selectedType != "ALL"

    val deptCounts = remember(resources) {
        MecDepartment.entries.associate { it.code to resources.count { r -> r.department == it.code } }
    }

    val filtered = remember(resources, selectedDept, selectedSemester, selectedType, searchQuery) {
        resources.filter { res ->
            val deptMatch = selectedDept == "ALL" || res.department == selectedDept || res.department == "ALL"
            val semMatch = selectedSemester == "ALL" || res.semester == selectedSemester
            val typeMatch = selectedType == "ALL" || res.resourceType == selectedType
            val queryMatch = MecChannels.matchesResourceQuery(res, searchQuery)
            deptMatch && semMatch && typeMatch && queryMatch
        }
    }

    val groupedByDepartment = remember(filtered) {
        val deptOrder = MecDepartment.entries.map { it.code } + "ALL"
        filtered.groupBy { it.department }
            .toList()
            .sortedBy { (dept, _) ->
                val idx = deptOrder.indexOf(dept)
                if (idx == -1) 99 else idx
            }
    }

    Box(modifier = modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val columns = if (maxWidth >= 720.dp) 2 else 1

            if (resourcesState is UiState.Loading) {
                MecLoadingState()
            } else if (resourcesState is UiState.Error) {
                Column(modifier = Modifier.padding(16.dp)) {
                    MecEmptyStateCard(
                        icon = Icons.AutoMirrored.Rounded.MenuBook,
                        title = "Unable to load resources",
                        subtitle = resourcesState.message
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 92.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item(span = { GridItemSpan(columns) }) {
                        ResourcesHeroBanner()
                    }

                    item(span = { GridItemSpan(columns) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = onSearchQueryChange,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Search,
                                        contentDescription = "Search resources",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = { onSearchQueryChange("") },
                                            modifier = Modifier.testTag("clear_resource_search_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = "Clear search"
                                            )
                                        }
                                    }
                                },
                                placeholder = {
                                    Text("Search by title, category (PDF, Link), CSE, or CST301...")
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = { focusManager.clearFocus() }
                                ),
                                shape = RoundedCornerShape(18.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("resource_search_input")
                            )

                            if (hasActiveFilter) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${filtered.size} ${if (filtered.size == 1) "match" else "matches"} found",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.testTag("resource_search_count")
                                    )
                                    TextButton(
                                        onClick = {
                                            onSearchQueryChange("")
                                            onSelectDept("ALL")
                                            onSelectType("ALL")
                                            onSelectSemester("ALL")
                                        },
                                        modifier = Modifier.testTag("reset_resource_filters_button")
                                    ) {
                                        Text("Reset Filters")
                                    }
                                }
                            }
                        }
                    }

                    // Department Category Chips with Counts
                    item(span = { GridItemSpan(columns) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "DEPARTMENTS",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item {
                                    FilterChip(
                                        selected = selectedDept == "ALL",
                                        onClick = { onSelectDept("ALL") },
                                        label = { Text("All Depts (${resources.size})") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        modifier = Modifier.testTag("dept_filter_ALL")
                                    )
                                }
                                items(MecDepartment.entries) { dept ->
                                    val count = deptCounts[dept.code] ?: 0
                                    FilterChip(
                                        selected = selectedDept == dept.code,
                                        onClick = { onSelectDept(dept.code) },
                                        label = { Text("${dept.code} ($count)") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        modifier = Modifier.testTag("dept_filter_${dept.code}")
                                    )
                                }
                            }

                            Text(
                                text = "RESOURCE TYPE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(listOf("ALL") + MecChannels.resourceTypes) { resType ->
                                    FilterChip(
                                        selected = selectedType == resType,
                                        onClick = { onSelectType(resType) },
                                        leadingIcon = if (resType != "ALL") {
                                            {
                                                Icon(
                                                    imageVector = resourceTypeIcon(resType),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        } else null,
                                        label = { Text(if (resType == "ALL") "All Types" else resType) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.tertiary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onTertiary
                                        ),
                                        modifier = Modifier.testTag("type_filter_$resType")
                                    )
                                }
                            }

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(listOf("ALL") + MecChannels.semesters) { sem ->
                                    FilterChip(
                                        selected = selectedSemester == sem,
                                        onClick = { onSelectSemester(sem) },
                                        label = { Text(if (sem == "ALL") "All Semesters" else sem) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.secondary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onSecondary
                                        ),
                                        modifier = Modifier.testTag("sem_filter_$sem")
                                    )
                                }
                            }
                        }
                    }

                    if (filtered.isEmpty()) {
                        item(span = { GridItemSpan(columns) }) {
                            MecEmptyStateCard(
                                icon = Icons.AutoMirrored.Rounded.MenuBook,
                                title = "No matching resources",
                                subtitle = "Try clearing filters or share a PDF, link, or notes using the Share Resource button."
                            )
                        }
                    } else {
                        groupedByDepartment.forEach { (deptCode, deptResources) ->
                            val deptInfo = MecDepartment.entries.firstOrNull { it.code == deptCode }
                            item(
                                key = "header_$deptCode",
                                span = { GridItemSpan(columns) }
                            ) {
                                DepartmentSectionBanner(
                                    deptCode = deptCode,
                                    deptFullName = deptInfo?.fullName ?: "Campus-Wide Academic Resources",
                                    count = deptResources.size
                                )
                            }

                            items(deptResources, key = { it.id }) { resource ->
                                val resourceKey = resource.id.ifBlank { resource.title }
                                DepartmentResourceCard(
                                    resource = resource,
                                    isDownloading = resourceKey in downloadingResourceIds,
                                    downloadProgress = resourceDownloadProgress[resourceKey] ?: 0f,
                                    downloadedInfo = downloadedResources[resourceKey],
                                    onDownload = { onDownloadResource(resource) },
                                    onOpenDownloadedPdf = { info -> onOpenDownloadedPdf(info) },
                                    onUpvote = { onUpvote(resource) }
                                )
                            }
                        }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = onOpenAddResource,
            icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
            text = { Text("Share Resource", style = MaterialTheme.typography.labelLarge) },
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("share_resource_fab")
        )
    }
}

@Composable
private fun DepartmentSectionBanner(
    deptCode: String,
    deptFullName: String,
    count: Int
) {
    val accent = departmentAccentColor(deptCode)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(22.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(accent)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "$deptCode • $deptFullName",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        MecTagPill(text = "$count items", accentColor = accent)
    }
}

@Composable
private fun ResourcesHeroBanner() {
    Surface(
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(136.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_campus_study_hub),
                contentDescription = "MEC Department Study Hub",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF060B18).copy(alpha = 0.92f),
                                Color(0xFF060B18).copy(alpha = 0.48f)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(20.dp)
            ) {
                MecTagPill(
                    text = "KTU DIGITAL VAULT • PDFs & LINKS",
                    accentColor = Color(0xFF2DD4BF)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Department Resources",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
                Text(
                    text = "Categorized by MEC department & type (PDF, Link, Notes, PYQ)",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.82f)
                )
            }
        }
    }
}

@Composable
private fun DepartmentResourceCard(
    resource: DepartmentResource,
    isDownloading: Boolean,
    downloadProgress: Float,
    downloadedInfo: DownloadedPdfInfo?,
    onDownload: () -> Unit,
    onOpenDownloadedPdf: (DownloadedPdfInfo) -> Unit,
    onUpvote: () -> Unit
) {
    val deptColor = departmentAccentColor(resource.department)
    val typeAccent = resourceTypeAccent(resource.resourceType)
    val typeIcon = resourceTypeIcon(resource.resourceType)
    val uriHandler = LocalUriHandler.current
    val isWebUrl = ResourcePdfDownloader.isDownloadableUrl(resource.linkOrReference)
    val isPdf = ResourcePdfDownloader.isPdfResource(resource)
    val tagKey = resource.id.ifBlank { resource.title }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MecTagPill(text = resource.subjectCode, accentColor = deptColor)
                    MecTagPill(text = "${resource.department} • ${resource.semester}", accentColor = MaterialTheme.colorScheme.primary)
                }
                Surface(
                    color = typeAccent.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, typeAccent.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = typeIcon,
                            contentDescription = null,
                            tint = typeAccent,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = resource.resourceType,
                            style = MaterialTheme.typography.labelMedium,
                            color = typeAccent
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = resource.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = resource.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                onClick = {
                    if (isWebUrl) {
                        runCatching { uriHandler.openUri(resource.linkOrReference) }
                    }
                },
                enabled = isWebUrl,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (isWebUrl) Icons.Rounded.Link else Icons.Rounded.FolderCopy,
                            contentDescription = null,
                            tint = typeAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = resource.linkOrReference,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isWebUrl) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (isWebUrl) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = "Open resource link",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (isPdf) {
                Spacer(modifier = Modifier.height(10.dp))
                if (downloadedInfo != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("downloaded_badge_$tagKey")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Saved to ${downloadedInfo.displayLocation}: ${downloadedInfo.fileName}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalButton(
                                onClick = { onOpenDownloadedPdf(downloadedInfo) },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("open_pdf_resource_$tagKey")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PictureAsPdf,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.resource_open_pdf),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }

                            OutlinedButton(
                                onClick = onDownload,
                                enabled = !isDownloading,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("download_resource_$tagKey")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = stringResource(R.string.resource_download_again),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.resource_download_pdf),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilledTonalButton(
                            onClick = onDownload,
                            enabled = !isDownloading,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (isWebUrl) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                contentColor = if (isWebUrl) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("download_resource_$tagKey")
                        ) {
                            if (isDownloading) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${stringResource(R.string.resource_downloading_pdf)} ${(downloadProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.resource_download_pdf),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }

                        if (isDownloading) {
                            LinearProgressIndicator(
                                progress = { downloadProgress.coerceIn(0.05f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .testTag("download_progress_$tagKey")
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Shared by ${resource.uploaderName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    onClick = onUpvote,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("upvote_resource_${resource.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ThumbUp,
                            contentDescription = "Upvote resource",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${resource.upvotes} Helpful",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

private fun resourceTypeIcon(type: String): ImageVector = when (type) {
    "PDF" -> Icons.Rounded.PictureAsPdf
    "Link" -> Icons.Rounded.Link
    "PYQ" -> Icons.Rounded.Quiz
    "Lab Manual" -> Icons.Rounded.Science
    "Syllabus" -> Icons.AutoMirrored.Rounded.MenuBook
    "Slides" -> Icons.Rounded.Slideshow
    else -> Icons.Rounded.Description
}

@Composable
private fun resourceTypeAccent(type: String): Color = when (type) {
    "PDF" -> MaterialTheme.colorScheme.error
    "Link" -> MaterialTheme.colorScheme.primary
    "PYQ" -> MaterialTheme.colorScheme.tertiary
    "Lab Manual" -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.tertiary
}
