package com.example.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BadgeColors
import com.example.ui.theme.LeadStarAmber
import com.example.ui.theme.P1Crimson
import com.example.ui.theme.PillProgressSky
import com.example.ui.theme.PingDuluExtendedColors
import com.example.ui.theme.PingDuluTheme
import com.example.ui.theme.StopwatchMint
import java.util.Locale

internal enum class LabelColorTone {
    URGENT_CRIMSON,
    NETWORK_INDIGO,
    SERVER_VIOLET,
    HARDWARE_AMBER,
    SECURITY_ROSE,
    ONBOARDING_TEAL,
    STATUS_MINT,
    GENERAL_SKY
}

internal fun resolveLabelColorTone(label: String): LabelColorTone {
    val normalized = label.trim().lowercase(Locale.ROOT)
    return when {
        normalized.contains("urgent") ||
            normalized.contains("kritis") ||
            normalized.contains("darurat") ||
            normalized.contains("down") ||
            normalized == "p1" -> LabelColorTone.URGENT_CRIMSON

        normalized.contains("wi-fi") ||
            normalized.contains("wifi") ||
            normalized.contains("wlan") ||
            normalized.contains("jaringan") ||
            normalized.contains("network") ||
            normalized.contains("fiber") ||
            normalized.contains("lan") ||
            normalized.contains("switch") ||
            normalized.contains("router") ||
            normalized.contains("mikrotik") -> LabelColorTone.NETWORK_INDIGO

        normalized.contains("server") ||
            normalized.contains("storage") ||
            normalized.contains("nas") ||
            normalized.contains("cloud") ||
            normalized.contains("database") ||
            normalized.contains("rack") ||
            normalized.contains("vm") -> LabelColorTone.SERVER_VIOLET

        normalized.contains("hardware") ||
            normalized.contains("printer") ||
            normalized.contains("laptop") ||
            normalized.contains("pc") ||
            normalized.contains("cctv") ||
            normalized.contains("ups") ||
            normalized.contains("kabel") -> LabelColorTone.HARDWARE_AMBER

        normalized.contains("keamanan") ||
            normalized.contains("security") ||
            normalized.contains("firewall") ||
            normalized.contains("vpn") ||
            normalized.contains("akses") -> LabelColorTone.SECURITY_ROSE

        normalized.contains("onboarding") ||
            normalized.contains("instalasi") ||
            normalized.contains("setup") ||
            normalized.contains("aplikasi") ||
            normalized.contains("email") -> LabelColorTone.ONBOARDING_TEAL

        normalized.contains("selesai") ||
            normalized.contains("maintenance") ||
            normalized.contains("normal") -> LabelColorTone.STATUS_MINT

        else -> {
            val fallbackTones = listOf(
                LabelColorTone.NETWORK_INDIGO,
                LabelColorTone.SERVER_VIOLET,
                LabelColorTone.HARDWARE_AMBER,
                LabelColorTone.SECURITY_ROSE,
                LabelColorTone.ONBOARDING_TEAL,
                LabelColorTone.GENERAL_SKY
            )
            fallbackTones[Math.floorMod(normalized.hashCode(), fallbackTones.size)]
        }
    }
}

fun resolveLabelBadgeColors(
    label: String,
    extendedColors: PingDuluExtendedColors
): BadgeColors = when (resolveLabelColorTone(label)) {
    LabelColorTone.URGENT_CRIMSON -> extendedColors.priorityP1
    LabelColorTone.NETWORK_INDIGO -> extendedColors.categoryNet
    LabelColorTone.SERVER_VIOLET -> extendedColors.categorySrv
    LabelColorTone.HARDWARE_AMBER -> extendedColors.categoryHrd
    LabelColorTone.SECURITY_ROSE -> extendedColors.categorySec
    LabelColorTone.ONBOARDING_TEAL -> extendedColors.categoryOnb
    LabelColorTone.STATUS_MINT -> extendedColors.syncOnline
    LabelColorTone.GENERAL_SKY -> extendedColors.priorityP3
}

private fun darkSurfaceDotColor(tone: LabelColorTone): Color = when (tone) {
    LabelColorTone.URGENT_CRIMSON -> P1Crimson
    LabelColorTone.HARDWARE_AMBER -> LeadStarAmber
    LabelColorTone.STATUS_MINT, LabelColorTone.ONBOARDING_TEAL -> StopwatchMint
    else -> PillProgressSky
}

@Composable
fun TaskLabelChip(
    label: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onDarkSurface: Boolean = false
) {
    val cleanLabel = label.trim()
    if (cleanLabel.isEmpty()) return

    val extendedColors = PingDuluTheme.extendedColors
    val tone = resolveLabelColorTone(cleanLabel)
    val badgeColors = resolveLabelBadgeColors(cleanLabel, extendedColors)
    val containerColor = if (onDarkSurface) Color.White.copy(alpha = 0.15f) else badgeColors.container
    val borderColor = if (onDarkSurface) Color.White.copy(alpha = 0.28f) else badgeColors.border
    val contentColor = if (onDarkSurface) Color.White else badgeColors.content
    val dotColor = if (onDarkSurface) darkSurfaceDotColor(tone) else badgeColors.content

    val tagSuffix = cleanLabel.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "_")

    Surface(
        modifier = modifier
            .semantics { contentDescription = "Label tiket $cleanLabel" }
            .testTag("task_label_chip_$tagSuffix"),
        shape = RoundedCornerShape(6.dp),
        color = containerColor,
        border = BorderStroke(width = 0.8.dp, color = borderColor)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 5.5.dp else 8.dp,
                vertical = if (compact) 2.dp else 3.5.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(if (compact) 5.dp else 6.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(
                text = cleanLabel,
                fontSize = if (compact) 9.5.sp else 11.sp,
                lineHeight = if (compact) 12.sp else 14.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false
            )
        }
    }
}

@Composable
fun TaskLabelRow(
    labels: List<String>,
    modifier: Modifier = Modifier,
    maxVisible: Int = 2,
    compact: Boolean = true,
    onDarkSurface: Boolean = false
) {
    val cleaned = labels.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    if (cleaned.isEmpty()) return

    val visible = cleaned.take(maxVisible.coerceAtLeast(1))
    val overflowCount = (cleaned.size - visible.size).coerceAtLeast(0)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        visible.forEach { label ->
            TaskLabelChip(
                label = label,
                compact = compact,
                onDarkSurface = onDarkSurface
            )
        }
        if (overflowCount > 0) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (onDarkSurface) {
                    Color.White.copy(alpha = 0.14f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                border = BorderStroke(
                    width = 0.8.dp,
                    color = if (onDarkSurface) {
                        Color.White.copy(alpha = 0.24f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    }
                )
            ) {
                Text(
                    text = "+$overflowCount",
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (onDarkSurface) {
                        Color.White
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskLabelFlowRow(
    labels: List<String>,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val cleaned = labels.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    if (cleaned.isEmpty()) return

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        cleaned.forEach { label ->
            TaskLabelChip(
                label = label,
                compact = compact
            )
        }
    }
}
