package com.example.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.Priority
import com.example.ui.theme.PingDuluFieldOpsTheme
import com.example.ui.theme.PingDuluTheme
import com.example.ui.theme.PlusJakartaSansFontFamily

@Composable
fun PriorityBadge(
    priority: Priority,
    modifier: Modifier = Modifier,
    expanded: Boolean = false
) {
    val badgeColors = PingDuluTheme.extendedColors.colorsForPriority(priority)
    val labelText = if (expanded) priority.displayLabel else priority.shortCode
    val cornerRadius = if (expanded) 999.dp else 5.dp

    Surface(
        modifier = modifier
            .semantics { contentDescription = "Prioritas ${priority.displayLabel}" }
            .testTag("priority_badge_${priority.shortCode.lowercase()}"),
        shape = RoundedCornerShape(cornerRadius),
        color = badgeColors.container,
        border = BorderStroke(width = 1.dp, color = badgeColors.border)
    ) {
        Text(
            text = labelText,
            modifier = Modifier.padding(
                horizontal = if (expanded) 8.dp else 6.dp,
                vertical = if (expanded) 3.dp else 2.dp
            ),
            color = badgeColors.content,
            fontFamily = PlusJakartaSansFontFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = if (expanded) 10.sp else 9.sp,
            lineHeight = 12.sp,
            letterSpacing = 0.4.sp,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PriorityBadgePreview() {
    PingDuluFieldOpsTheme {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PriorityBadge(priority = Priority.P1_CRITICAL)
            PriorityBadge(priority = Priority.P2_HIGH)
            PriorityBadge(priority = Priority.P3_NORMAL)
            PriorityBadge(priority = Priority.P1_CRITICAL, expanded = true)
        }
    }
}
