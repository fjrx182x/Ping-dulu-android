package com.example.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.presentation.schedule.LayoutMode
import com.example.presentation.schedule.StatusFilter
import com.example.presentation.schedule.StatusFilterCounts
import com.example.ui.theme.PingDuluNavy
import com.example.ui.theme.PingDuluTheme

@Composable
fun StatusFilterPillRow(
    selectedFilter: StatusFilter,
    counts: StatusFilterCounts,
    layoutMode: LayoutMode,
    onFilterSelected: (StatusFilter) -> Unit,
    onLayoutModeSelected: (LayoutMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val extendedColors = PingDuluTheme.extendedColors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("status_filter_section"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "FILTER STATUS TIKET",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )

            // Segmented Grid 2-Kolom vs List 1-Kolom Toggle
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier.padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    LayoutToggleButton(
                        selected = layoutMode == LayoutMode.GRID_2_COLUMN,
                        icon = Icons.Rounded.GridView,
                        description = "Tampilan Grid 2-Kolom",
                        testTagName = "toggle_layout_grid",
                        onClick = { onLayoutModeSelected(LayoutMode.GRID_2_COLUMN) }
                    )
                    LayoutToggleButton(
                        selected = layoutMode == LayoutMode.LIST_1_COLUMN,
                        icon = Icons.AutoMirrored.Rounded.FormatListBulleted,
                        description = "Tampilan List 1-Kolom",
                        testTagName = "toggle_layout_list",
                        onClick = { onLayoutModeSelected(LayoutMode.LIST_1_COLUMN) }
                    )
                }
            }
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items = StatusFilter.entries, key = { it.name }) { filter ->
                val count = when (filter) {
                    StatusFilter.ALL -> counts.all
                    StatusFilter.BACKLOG -> counts.backlog
                    StatusFilter.IN_PROGRESS -> counts.inProgress
                    StatusFilter.DONE -> counts.done
                }
                val dotColor: Color? = when (filter) {
                    StatusFilter.ALL -> null
                    StatusFilter.BACKLOG -> extendedColors.pillBacklog
                    StatusFilter.IN_PROGRESS -> extendedColors.pillInProgress
                    StatusFilter.DONE -> extendedColors.pillDone
                }
                StatusFilterChip(
                    label = "${filter.label} ($count)",
                    dotColor = dotColor,
                    isSelected = filter == selectedFilter,
                    testTagName = "filter_pill_${filter.name.lowercase()}",
                    onClick = { onFilterSelected(filter) }
                )
            }
        }
    }
}

@Composable
private fun StatusFilterChip(
    label: String,
    dotColor: Color?,
    isSelected: Boolean,
    testTagName: String,
    onClick: () -> Unit
) {
    val containerColor = if (isSelected) PingDuluNavy else MaterialTheme.colorScheme.surface
    val contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
    val borderColor = if (isSelected) PingDuluNavy else MaterialTheme.colorScheme.outline

    Surface(
        onClick = onClick,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = 38.dp)
            .semantics {
                selected = isSelected
                contentDescription = "Filter $label"
            }
            .testTag(testTagName),
        shape = RoundedCornerShape(999.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = if (isSelected) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (dotColor != null) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                fontSize = 11.5.sp,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun LayoutToggleButton(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    testTagName: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(36.dp)
            .semantics {
                this.selected = selected
                contentDescription = description
            }
            .testTag(testTagName),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
        shadowElevation = if (selected) 1.dp else 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
