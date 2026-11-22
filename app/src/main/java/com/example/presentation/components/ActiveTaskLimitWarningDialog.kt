package com.example.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.model.ActiveTaskLimitWarning
import com.example.ui.theme.CbnSlateMuted
import com.example.ui.theme.KalemAmberBright
import com.example.ui.theme.KalemAmberGlow
import com.example.ui.theme.KalemAmberSoftText
import com.example.ui.theme.KalemDarkCard
import com.example.ui.theme.KalemDarkInner

/**
 * Patch Ke-7 Amber Glow "Kalem Dulu!" Dialog.
 * Visually aligned with PingDulu Web Dashboard's `KalemDuluModal.tsx`:
 * - Deep slate card (`#0F172A`) with glowing Amber border (`#F59E0B`)
 * - Hand-stop badge (✋), "Kalem Dulu!" header, and "BATAS MAKSIMAL 5 TASK AKTIF" pill
 * - Highlighted box displaying the exact technician or delegation limit copy
 * - High-contrast Amber "Siap, Mengerti" dismissal button (min 48dp touch target)
 */
@Composable
fun ActiveTaskLimitWarningDialog(
    warning: ActiveTaskLimitWarning,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = true)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("active_task_limit_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = KalemDarkCard,
            tonalElevation = 8.dp,
            shadowElevation = 16.dp,
            border = BorderStroke(
                width = 1.5.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        KalemAmberGlow,
                        KalemAmberBright.copy(alpha = 0.7f),
                        KalemAmberGlow
                    )
                )
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                KalemAmberGlow.copy(alpha = 0.12f),
                                Color.Transparent,
                                Color.Transparent
                            )
                        )
                    )
                    .padding(horizontal = 22.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Amber Glow Hand Badge (✋)
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    KalemAmberGlow.copy(alpha = 0.34f),
                                    KalemAmberGlow.copy(alpha = 0.14f)
                                )
                            )
                        )
                        .border(1.5.dp, KalemAmberGlow.copy(alpha = 0.75f), CircleShape)
                        .semantics { contentDescription = "Peringatan batas maksimal 5 task aktif" },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✋",
                        fontSize = 30.sp
                    )
                }

                // Title & Limit Pill Badge
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Kalem Dulu!",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = KalemAmberGlow.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, KalemAmberGlow.copy(alpha = 0.55f))
                    ) {
                        Text(
                            text = "BATAS MAKSIMAL ${warning.limit} TASK AKTIF",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp,
                            color = KalemAmberBright
                        )
                    }
                }

                // Exact Server/Policy Warning Message Box
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = KalemDarkInner,
                    border = BorderStroke(1.dp, KalemAmberGlow.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = warning.message,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = KalemAmberSoftText,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("active_task_limit_message")
                        )
                        warning.detailSubtitle?.takeIf { it.isNotBlank() }?.let { detail ->
                            Text(
                                text = detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = CbnSlateMuted,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Workload Guidance Copy
                Text(
                    text = "Fokus tuntaskan minimal 1 tiket aktif (Antrean/Proses) terlebih dahulu agar SLA lapangan tetap terjaga dan bebas overload.",
                    style = MaterialTheme.typography.bodySmall,
                    color = CbnSlateMuted,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Primary Amber Dismiss Button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("active_task_limit_dismiss_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KalemAmberGlow,
                        contentColor = KalemDarkCard
                    )
                ) {
                    Text(
                        text = "Siap, Mengerti",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}
