package com.example.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.example.presentation.schedule.DayHorizonModel
import com.example.presentation.schedule.DayIndicatorType
import com.example.ui.theme.P1Crimson
import com.example.ui.theme.P2Amber
import com.example.ui.theme.PillDoneMint
import com.example.ui.theme.PingDuluNavy

@Composable
fun Horizon7DayStrip(
    days: List<DayHorizonModel>,
    selectedEpochDay: Long,
    horizonSpanLabel: String,
    onDaySelected: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("horizon_7day_strip"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.CalendarMonth,
                    contentDescription = "Kalender 7 hari",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Jadwal 7 Hari Bergulir",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            Text(
                text = horizonSpanLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 2.dp)
        ) {
            items(items = days, key = { it.epochDay }) { day ->
                DayHorizonChip(
                    day = day,
                    isSelected = day.epochDay == selectedEpochDay,
                    onClick = { onDaySelected(day.epochDay) }
                )
            }
        }
    }
}

@Composable
private fun DayHorizonChip(
    day: DayHorizonModel,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val dotColor = when (day.indicatorType) {
        DayIndicatorType.CRITICAL_P1 -> P1Crimson
        DayIndicatorType.HIGH_P2 -> P2Amber
        DayIndicatorType.ALL_DONE -> PillDoneMint
        DayIndicatorType.NORMAL -> MaterialTheme.colorScheme.secondary
        DayIndicatorType.NONE -> if (isSelected) {
            Color.White.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.outline
        }
    }

    val containerColor = if (isSelected) PingDuluNavy else MaterialTheme.colorScheme.surface
    val borderColor = if (isSelected) PingDuluNavy else MaterialTheme.colorScheme.outline
    val dayNameColor = if (isSelected) {
        Color.White.copy(alpha = 0.80f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val dateNumberColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface

    Surface(
        onClick = onClick,
        modifier = Modifier
            .widthIn(min = 56.dp)
            .heightIn(min = 76.dp)
            .semantics {
                selected = isSelected
                contentDescription = "${day.fullDateLabel}, ${day.taskCount} tiket"
            }
            .testTag("day_chip_${day.epochDay}"),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(width = 1.dp, color = borderColor),
        shadowElevation = if (isSelected) 4.dp else 0.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = day.dayShortName,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                letterSpacing = 0.6.sp,
                color = dayNameColor,
                maxLines = 1,
                softWrap = false
            )

            Text(
                text = day.dateNumber.toString(),
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = dateNumberColor,
                maxLines = 1,
                softWrap = false
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Text(
                    text = day.taskCount.toString(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) Color.White.copy(alpha = 0.90f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}
