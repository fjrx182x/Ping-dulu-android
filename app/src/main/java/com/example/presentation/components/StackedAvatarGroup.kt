package com.example.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.domain.model.Assignee
import com.example.ui.theme.AvatarCobalt
import com.example.ui.theme.AvatarEmerald
import com.example.ui.theme.AvatarSky
import com.example.ui.theme.AvatarTeal
import com.example.ui.theme.AvatarViolet
import com.example.ui.theme.LeadStarAmber
import com.example.ui.theme.PingDuluFieldOpsTheme
import com.example.ui.theme.PingDuluTheme

fun avatarColorForAssignee(assignee: Assignee): Color {
    val normalized = assignee.name.trim().lowercase()
    if (normalized.startsWith("fajar")) return AvatarEmerald
    val palette = listOf(AvatarCobalt, AvatarSky, AvatarViolet, AvatarTeal, AvatarEmerald)
    val index = (assignee.id.hashCode() and Int.MAX_VALUE) % palette.size
    return palette[index]
}

@Composable
fun StackedAvatarGroup(
    assignees: List<Assignee>,
    modifier: Modifier = Modifier,
    maxVisible: Int = 3,
    avatarSize: Dp = 22.dp,
    ringColor: Color = MaterialTheme.colorScheme.surface,
    ringWidth: Dp = 1.5.dp,
    showLeadIndicator: Boolean = false
) {
    if (assignees.isEmpty()) return

    val safeMax = maxVisible.coerceAtLeast(1)
    val visibleAssignees = assignees.take(safeMax)
    val overflowCount = (assignees.size - safeMax).coerceAtLeast(0)
    val overlapStep = avatarSize * 0.68f
    val totalItems = visibleAssignees.size + if (overflowCount > 0) 1 else 0
    val totalWidth = avatarSize + overlapStep * (totalItems - 1)

    val namesDescription = assignees.joinToString(separator = ", ") { assignee ->
        if (assignee.isLead) "${assignee.name} (PIC Lead)" else assignee.name
    }

    Box(
        modifier = modifier
            .size(width = totalWidth, height = avatarSize)
            .semantics { contentDescription = "Teknisi bertugas: $namesDescription" }
            .testTag("stacked_avatar_group")
    ) {
        visibleAssignees.forEachIndexed { index, assignee ->
            val bgColor = avatarColorForAssignee(assignee)
            Box(
                modifier = Modifier
                    .offset(x = overlapStep * index)
                    .zIndex((totalItems - index).toFloat())
                    .size(avatarSize),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(avatarSize)
                        .clip(CircleShape)
                        .background(bgColor)
                        .border(width = ringWidth, color = ringColor, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = assignee.initial,
                        color = Color.White,
                        fontSize = (avatarSize.value * 0.42f).sp,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = (avatarSize.value * 0.46f).sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                if (showLeadIndicator && assignee.isLead) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size((avatarSize.value * 0.34f).dp.coerceAtLeast(6.dp))
                            .clip(CircleShape)
                            .background(LeadStarAmber)
                            .border(width = 1.dp, color = ringColor, shape = CircleShape)
                    )
                }
            }
        }

        if (overflowCount > 0) {
            Box(
                modifier = Modifier
                    .offset(x = overlapStep * visibleAssignees.size)
                    .zIndex(0f)
                    .size(avatarSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(width = ringWidth, color = ringColor, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+$overflowCount",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = (avatarSize.value * 0.36f).sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
fun AssigneeLeadChip(
    assignee: Assignee,
    modifier: Modifier = Modifier
) {
    val leadColors = PingDuluTheme.extendedColors.categoryNet
    Surface(
        modifier = modifier.testTag("assignee_chip_${assignee.id}"),
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(avatarColorForAssignee(assignee)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = assignee.initial,
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Text(
                text = assignee.name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (assignee.isLead) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(leadColors.container)
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "LEAD",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = leadColors.content,
                        letterSpacing = 0.4.sp
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StackedAvatarGroupPreview() {
    PingDuluFieldOpsTheme {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StackedAvatarGroup(
                assignees = listOf(
                    Assignee(3L, "Fajar", "admin", true),
                    Assignee(2L, "Budi Santoso", "technician", false),
                    Assignee(4L, "Siti Viewer", "viewer", false),
                    Assignee(5L, "Rudi", "technician", false)
                ),
                avatarSize = 26.dp,
                showLeadIndicator = true
            )
            AssigneeLeadChip(assignee = Assignee(3L, "Fajar", "admin", true))
        }
    }
}
