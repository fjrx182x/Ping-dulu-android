package com.example.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.tools.CbnSpeedGaugeMath
import com.example.domain.tools.SpeedTestPhase
import com.example.ui.theme.CbnCobaltBlue
import com.example.ui.theme.CbnCobaltLight
import com.example.ui.theme.CbnDeepSlate
import com.example.ui.theme.CbnSlateBorder
import com.example.ui.theme.CbnSlateMuted
import com.example.ui.theme.CbnSparklineCyan
import com.example.ui.theme.CbnTrackSlate
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.PillDoneMint
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Patch Ke-7 CBN / Ookla Style 266° Canvas Speedometer Gauge.
 * - Start angle 137° (bottom-left), Total sweep 266° clockwise, Peak 270° = 200 Mbps (12 o'clock)
 * - Dual-layer `StrokeCap.Butt` arc (dark slate track + Electric Cobalt Blue active arc)
 * - 9 non-linear scale labels (`0, 10, 50, 100, 200, 300, 500, 750, 1g`)
 * - Tapered gradient needle without center donut + soft motion shadow, smoothly damped via `Animatable`
 */
@Composable
internal fun CbnSpeedometerGauge(
    speedMbps: Double,
    phase: SpeedTestPhase,
    statusMessage: String,
    modifier: Modifier = Modifier
) {
    val targetSweep = remember(speedMbps) {
        CbnSpeedGaugeMath.speedToSweepAngle(speedMbps.toFloat())
    }
    val animatedSweep = remember { Animatable(0f) }

    LaunchedEffect(targetSweep) {
        animatedSweep.animateTo(
            targetValue = targetSweep,
            animationSpec = spring(
                dampingRatio = 0.82f,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    val textMeasurer = rememberTextMeasurer()
    val tickLabelStyle = remember {
        TextStyle(
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = CbnSlateMuted
        )
    }

    val displaySpeedText = remember(speedMbps) {
        String.format(Locale.US, "%.2f", speedMbps.coerceAtLeast(0.0))
    }
    val contextualLabel = remember(phase, statusMessage) {
        when (phase) {
            SpeedTestPhase.IDLE -> "Siap Menguji"
            SpeedTestPhase.PING -> "Mengukur Ping..."
            SpeedTestPhase.JITTER -> "Mengukur Jitter..."
            SpeedTestPhase.DOWNLOAD -> "Mengukur Unduh..."
            SpeedTestPhase.UPLOAD -> "Mengukur Unggah..."
            SpeedTestPhase.COMPLETED -> "Pengujian Selesai"
            SpeedTestPhase.ERROR -> "Pengujian Gagal"
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("speedtest_cbn_gauge"),
        shape = RoundedCornerShape(22.dp),
        color = CbnDeepSlate,
        border = BorderStroke(1.dp, CbnSlateBorder.copy(alpha = 0.85f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(268.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .semantics {
                    contentDescription = "Gauge Speedtest $displaySpeedText Mbps, status $contextualLabel"
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidthPx = 19.dp.toPx()
                val diameter = minOf(size.width * 0.84f, size.height * 0.90f)
                val radius = diameter / 2f
                val center = Offset(size.width / 2f, size.height * 0.54f)
                val topLeft = Offset(center.x - radius, center.y - radius)
                val arcSize = Size(diameter, diameter)

                // 1. Background Slate Track (266° with flat StrokeCap.Butt)
                drawArc(
                    color = CbnTrackSlate,
                    startAngle = CbnSpeedGaugeMath.START_ANGLE_DEGREES,
                    sweepAngle = CbnSpeedGaugeMath.TOTAL_SWEEP_DEGREES,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Butt)
                )

                // 2. Active Electric Cobalt Blue Track synchronized to needle sweep
                val currentSweep = animatedSweep.value.coerceIn(0f, CbnSpeedGaugeMath.TOTAL_SWEEP_DEGREES)
                if (currentSweep > 0.5f) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            colorStops = arrayOf(
                                0.0f to CbnCobaltBlue,
                                0.5f to CbnCobaltLight,
                                1.0f to CbnSparklineCyan
                            ),
                            center = center
                        ),
                        startAngle = CbnSpeedGaugeMath.START_ANGLE_DEGREES,
                        sweepAngle = currentSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Butt)
                    )
                }

                // 3. Nine Non-Linear Ticks & Labels (0, 10, 50, 100, 200, 300, 500, 750, 1g)
                val labelRadius = radius - strokeWidthPx - 14.dp.toPx()
                val tickInnerR = radius - (strokeWidthPx * 0.55f)
                val tickOuterR = radius + (strokeWidthPx * 0.50f)

                CbnSpeedGaugeMath.TICK_LABELS.forEachIndexed { index, label ->
                    val angleDeg = CbnSpeedGaugeMath.tickCanvasAngle(index)
                    val angleRad = (angleDeg * PI / 180.0)
                    val cosA = cos(angleRad).toFloat()
                    val sinA = sin(angleRad).toFloat()

                    val isReached = (angleDeg - CbnSpeedGaugeMath.START_ANGLE_DEGREES) <= currentSweep + 0.5f
                    drawLine(
                        color = if (isReached) Color.White.copy(alpha = 0.85f) else CbnSlateBorder,
                        start = Offset(center.x + tickInnerR * cosA, center.y + tickInnerR * sinA),
                        end = Offset(center.x + tickOuterR * cosA, center.y + tickOuterR * sinA),
                        strokeWidth = 1.5.dp.toPx()
                    )

                    val measured = textMeasurer.measure(
                        text = label,
                        style = tickLabelStyle.copy(
                            color = if (isReached) Color.White else CbnSlateMuted
                        )
                    )
                    val lx = center.x + labelRadius * cosA - (measured.size.width / 2f)
                    val ly = center.y + labelRadius * sinA - (measured.size.height / 2f)
                    drawText(
                        textLayoutResult = measured,
                        topLeft = Offset(lx, ly)
                    )
                }

                // 4. Tapered Gradient Needle (No Center Donut) + Soft Motion Shadow
                val needleAngleDeg = CbnSpeedGaugeMath.START_ANGLE_DEGREES + currentSweep
                val needleRad = (needleAngleDeg * PI / 180.0)
                val cosN = cos(needleRad).toFloat()
                val sinN = sin(needleRad).toFloat()
                val perpX = -sinN
                val perpY = cosN

                val tipLength = radius - strokeWidthPx * 0.12f
                val baseOffset = 14.dp.toPx()
                val baseHalfWidth = 5.5.dp.toPx()
                val tipHalfWidth = 1.2.dp.toPx()

                val baseCenter = Offset(center.x + baseOffset * cosN, center.y + baseOffset * sinN)
                val tipCenter = Offset(center.x + tipLength * cosN, center.y + tipLength * sinN)

                // Motion shadow slightly trailing the needle
                val shadowRad = ((needleAngleDeg - 3.5f) * PI / 180.0)
                val cosS = cos(shadowRad).toFloat()
                val sinS = sin(shadowRad).toFloat()
                val shadowTip = Offset(center.x + tipLength * cosS, center.y + tipLength * sinS)
                val shadowPath = Path().apply {
                    moveTo(baseCenter.x + perpX * baseHalfWidth, baseCenter.y + perpY * baseHalfWidth)
                    lineTo(shadowTip.x, shadowTip.y)
                    lineTo(baseCenter.x - perpX * baseHalfWidth, baseCenter.y - perpY * baseHalfWidth)
                    close()
                }
                drawPath(
                    path = shadowPath,
                    color = CbnCobaltBlue.copy(alpha = 0.24f)
                )

                val needlePath = Path().apply {
                    moveTo(baseCenter.x + perpX * baseHalfWidth, baseCenter.y + perpY * baseHalfWidth)
                    lineTo(tipCenter.x + perpX * tipHalfWidth, tipCenter.y + perpY * tipHalfWidth)
                    lineTo(tipCenter.x - perpX * tipHalfWidth, tipCenter.y - perpY * tipHalfWidth)
                    lineTo(baseCenter.x - perpX * baseHalfWidth, baseCenter.y - perpY * baseHalfWidth)
                    close()
                }
                drawPath(
                    path = needlePath,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            CbnSlateMuted.copy(alpha = 0.45f),
                            Color.White
                        ),
                        start = baseCenter,
                        end = tipCenter
                    )
                )
            }

            // Center-bottom digital readout (Giant 44sp Mbps + unit + contextual status)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = displaySpeedText,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier.testTag("speedtest_gauge_readout")
                )
                Text(
                    text = "Mbps",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = CbnSlateMuted
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = when (phase) {
                        SpeedTestPhase.COMPLETED -> PillDoneMint.copy(alpha = 0.18f)
                        SpeedTestPhase.ERROR -> Color(0xFFEF4444).copy(alpha = 0.18f)
                        SpeedTestPhase.IDLE -> CbnTrackSlate
                        else -> CbnCobaltBlue.copy(alpha = 0.22f)
                    },
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = contextualLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (phase) {
                            SpeedTestPhase.COMPLETED -> PillDoneMint
                            SpeedTestPhase.ERROR -> Color(0xFFFCA5A5)
                            SpeedTestPhase.IDLE -> CbnSlateMuted
                            else -> CbnSparklineCyan
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

/**
 * Compact live mini sparkline area chart for Download & Upload cards.
 * Renders a dashed slate baseline when idle/empty and a filled Cobalt Blue area curve with crisp outline during/after measurement.
 */
@Composable
internal fun SpeedSparklineChart(
    samples: List<Float>,
    accentColor: Color = CbnCobaltBlue,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
    ) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        // Baseline dashed guide
        val baselineY = h - 3.dp.toPx()
        drawLine(
            color = CbnSlateBorder.copy(alpha = 0.7f),
            start = Offset(0f, baselineY),
            end = Offset(w, baselineY),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
        )

        if (samples.isEmpty()) return@Canvas

        val effectiveSamples = if (samples.size == 1) listOf(samples[0], samples[0]) else samples
        val maxVal = (effectiveSamples.maxOrNull() ?: 1f).coerceAtLeast(1f) * 1.12f
        val topPad = 3.dp.toPx()
        val usableHeight = (baselineY - topPad).coerceAtLeast(4f)
        val stepX = w / (effectiveSamples.size - 1).coerceAtLeast(1)

        val linePath = Path()
        val fillPath = Path()

        effectiveSamples.forEachIndexed { index, raw ->
            val x = index * stepX
            val ratio = (raw / maxVal).coerceIn(0f, 1f)
            val y = baselineY - (ratio * usableHeight)
            if (index == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, baselineY)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(w, baselineY)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    accentColor.copy(alpha = 0.85f),
                    accentColor.copy(alpha = 0.14f)
                ),
                startY = topPad,
                endY = baselineY
            )
        )
        drawPath(
            path = linePath,
            color = accentColor,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

/**
 * 4-stage horizontal progress bar: Ping -> Jitter -> Download -> Upload.
 * Active circle pulses Cobalt Blue; completed stages show emerald/blue checkmarks.
 */
@Composable
internal fun SpeedTestStepProgressBar(
    phase: SpeedTestPhase,
    modifier: Modifier = Modifier
) {
    val steps = remember {
        listOf(
            "Ping" to 0,
            "Jitter" to 1,
            "Download" to 2,
            "Upload" to 3
        )
    }
    val currentStepIdx = when (phase) {
        SpeedTestPhase.IDLE, SpeedTestPhase.ERROR -> -1
        SpeedTestPhase.PING -> 0
        SpeedTestPhase.JITTER -> 1
        SpeedTestPhase.DOWNLOAD -> 2
        SpeedTestPhase.UPLOAD -> 3
        SpeedTestPhase.COMPLETED -> 4
    }

    val pulseTransition = rememberInfiniteTransition(label = "step_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "step_pulse_alpha"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("speedtest_step_bar"),
        shape = RoundedCornerShape(14.dp),
        color = CbnDeepSlate,
        border = BorderStroke(1.dp, CbnSlateBorder.copy(alpha = 0.7f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            steps.forEachIndexed { index, (label, stepOrder) ->
                val isCompleted = currentStepIdx > stepOrder
                val isActive = currentStepIdx == stepOrder

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isCompleted -> PillDoneMint
                                    isActive -> CbnCobaltBlue.copy(alpha = pulseAlpha)
                                    else -> CbnTrackSlate
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCompleted) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "$label selesai",
                                tint = CbnDeepSlate,
                                modifier = Modifier.size(13.dp)
                            )
                        } else {
                            Text(
                                text = "${index + 1}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isActive) Color.White else CbnSlateMuted
                            )
                        }
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isActive || isCompleted) FontWeight.Bold else FontWeight.Medium,
                        color = when {
                            isCompleted -> PillDoneMint
                            isActive -> Color.White
                            else -> CbnSlateMuted
                        },
                        maxLines = 1
                    )
                }

                if (index < steps.lastIndex) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(
                                if (currentStepIdx > stepOrder) PillDoneMint else CbnSlateBorder
                            )
                    )
                }
            }
        }
    }
}
