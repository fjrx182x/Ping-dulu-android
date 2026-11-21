package com.example.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Cable
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.tools.EthernetCableSpec
import com.example.domain.tools.PoeBudgetStatus
import com.example.domain.tools.PoeStandardSpec
import com.example.domain.tools.Rj45PinSpec
import com.example.domain.tools.Rj45PoeReference
import com.example.domain.tools.Rj45WiringMode
import com.example.domain.tools.WireColorCode
import com.example.ui.theme.AvatarEmerald
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.P1Crimson
import com.example.ui.theme.P2Amber
import com.example.ui.theme.PillDoneMint
import com.example.ui.theme.PingDuluNavy
import java.util.Locale

private enum class Rj45SubSection(val label: String) {
    PINOUT("Pinout RJ45"),
    POE("Standar PoE"),
    CABLE("Kabel UTP")
}

@Composable
internal fun Rj45PoeReferenceSection() {
    var activeSubSection by rememberSaveable { mutableStateOf(Rj45SubSection.PINOUT) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("toolkit_rj45_poe_section"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Sub-section selector chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Rj45SubSection.entries.forEach { section ->
                val selected = activeSubSection == section
                FilterChip(
                    selected = selected,
                    onClick = { activeSubSection = section },
                    label = {
                        Text(
                            text = section.label,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PingDuluNavy,
                        selectedLabelColor = Color.White
                    ),
                    modifier = Modifier.testTag("rj45_subtab_${section.name.lowercase()}")
                )
            }
        }

        when (activeSubSection) {
            Rj45SubSection.PINOUT -> Rj45PinoutGuideView()
            Rj45SubSection.POE -> PoeStandardsAndBudgetView()
            Rj45SubSection.CABLE -> EthernetCableSpecsView()
        }
    }
}

@Composable
private fun Rj45PinoutGuideView() {
    var wiringMode by rememberSaveable { mutableStateOf(Rj45WiringMode.T568B) }
    var selectedPinNumber by rememberSaveable { mutableIntStateOf(1) }

    val endAPins = remember(wiringMode) { Rj45PoeReference.pinsForEndA(wiringMode) }
    val endBPins = remember(wiringMode) { Rj45PoeReference.pinsForEndB(wiringMode) }
    val selectedPin = remember(endAPins, selectedPinNumber) {
        endAPins.firstOrNull { it.pinNumber == selectedPinNumber } ?: endAPins.first()
    }
    val hasDifferentEnds = wiringMode == Rj45WiringMode.CROSSOVER || wiringMode == Rj45WiringMode.ROLLOVER

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Wiring mode pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Rj45WiringMode.entries.forEach { mode ->
                val selected = wiringMode == mode
                Surface(
                    onClick = { wiringMode = mode },
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) PingDuluNavy else MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(
                        1.dp,
                        if (selected) PingDuluNavy else MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 40.dp)
                        .testTag("rj45_mode_${mode.name.lowercase()}")
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mode.shortLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Visual RJ45 Plug Card (End A)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = wiringMode.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = wiringMode.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = PingDuluNavy.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "Pin 1 → 8",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Text(
                    text = "Posisi konektor: Klip pengunci di BELAKANG, pin kuningan menghadap Anda (ketuk pin untuk detail)",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Rj45ConnectorDiagram(
                    label = wiringMode.endALabel,
                    pins = endAPins,
                    selectedPinNumber = selectedPinNumber,
                    onSelectPin = { selectedPinNumber = it }
                )

                if (hasDifferentEnds) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Rj45ConnectorDiagram(
                        label = wiringMode.endBLabel,
                        pins = endBPins,
                        selectedPinNumber = selectedPinNumber,
                        onSelectPin = { selectedPinNumber = it }
                    )
                }

                // Selected Pin Inspector
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rj45_pin_inspector"),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, PingDuluNavy.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            WireColorSwatch(
                                wireColor = selectedPin.wireColor,
                                modifier = Modifier.size(width = 24.dp, height = 16.dp)
                            )
                            Text(
                                text = "Pin ${selectedPin.pinNumber} · ${selectedPin.wireColor.displayName}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "Pair ${selectedPin.pairNumber}",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (wiringMode == Rj45WiringMode.ROLLOVER) {
                            PinInfoRow("Sinyal RS-232", selectedPin.consoleSignal)
                            PinInfoRow("Baudrate Default", "9600 bps · 8N1 · No Flow Control")
                        } else {
                            PinInfoRow("10/100 Mbps", selectedPin.signal100M)
                            PinInfoRow("1 Gbps (Gigabit)", selectedPin.signal1000M)
                            PinInfoRow("PoE Mode A", selectedPin.poeModeA)
                            PinInfoRow("PoE Mode B / Passive", selectedPin.poeModeB)
                        }
                    }
                }

                // Compact full 8-pin legend table
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (hasDifferentEnds) "TABEL PEMETAAN 8 PIN (UJUNG A ↔ UJUNG B)" else "URUTAN LENGKAP 8 PIN (${wiringMode.shortLabel})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.5.sp
                    )
                    endAPins.forEachIndexed { idx, pinA ->
                        val pinB = endBPins[idx]
                        val isSelected = pinA.pinNumber == selectedPinNumber
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) PingDuluNavy.copy(alpha = 0.10f)
                                    else Color.Transparent
                                )
                                .clickable { selectedPinNumber = pinA.pinNumber }
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "${pinA.pinNumber}",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(16.dp)
                            )
                            WireColorSwatch(
                                wireColor = pinA.wireColor,
                                modifier = Modifier.size(width = 20.dp, height = 12.dp)
                            )
                            Text(
                                text = pinA.wireColor.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (hasDifferentEnds) {
                                Text(
                                    text = "↔",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                WireColorSwatch(
                                    wireColor = pinB.wireColor,
                                    modifier = Modifier.size(width = 20.dp, height = 12.dp)
                                )
                                Text(
                                    text = pinB.wireColor.shortCode,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(28.dp),
                                    textAlign = TextAlign.End
                                )
                            } else {
                                Text(
                                    text = pinA.signal100M,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Field Tip Box
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = PingDuluNavy.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, PingDuluNavy.copy(alpha = 0.25f))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = wiringMode.usageNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun Rj45ConnectorDiagram(
    label: String,
    pins: List<Rj45PinSpec>,
    selectedPinNumber: Int,
    onSelectPin: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Gold brass contact pins row + wire bars
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    pins.forEach { pin ->
                        val isSelected = pin.pinNumber == selectedPinNumber
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) PingDuluNavy.copy(alpha = 0.14f)
                                    else Color.Transparent
                                )
                                .clickable { onSelectPin(pin.pinNumber) }
                                .padding(vertical = 4.dp)
                                .testTag("rj45_pin_${pin.pinNumber}"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Brass RJ45 contact tip
                            Box(
                                modifier = Modifier
                                    .width(12.dp)
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                    .background(Color(0xFFD97706))
                            )
                            // Colored insulated wire bar
                            WireColorSwatch(
                                wireColor = pin.wireColor,
                                vertical = true,
                                modifier = Modifier
                                    .width(14.dp)
                                    .height(34.dp)
                            )
                            Text(
                                text = "${pin.pinNumber}",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = pin.wireColor.shortCode,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WireColorSwatch(
    wireColor: WireColorCode,
    modifier: Modifier = Modifier,
    vertical: Boolean = false
) {
    val baseColor = Color(wireColor.primaryHex)
    val borderColor = Color(0xFF94A3B8)

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(if (wireColor.isStriped) Color.White else baseColor)
    ) {
        if (wireColor.isStriped) {
            if (vertical) {
                val bandHeight = size.height / 5f
                drawRect(color = baseColor, topLeft = Offset(0f, bandHeight * 0.6f), size = Size(size.width, bandHeight))
                drawRect(color = baseColor, topLeft = Offset(0f, bandHeight * 2.4f), size = Size(size.width, bandHeight))
                drawRect(color = baseColor, topLeft = Offset(0f, bandHeight * 4.0f), size = Size(size.width, bandHeight))
            } else {
                val stripeW = size.width / 3f
                drawRect(color = baseColor, topLeft = Offset(0f, 0f), size = Size(stripeW, size.height))
                drawRect(color = baseColor, topLeft = Offset(stripeW * 2f, 0f), size = Size(stripeW, size.height))
            }
        }
        drawRoundRect(
            color = borderColor.copy(alpha = 0.55f),
            cornerRadius = CornerRadius(4f, 4f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
        )
    }
}

@Composable
private fun PoeStandardsAndBudgetView() {
    var switchBudget by rememberSaveable { mutableIntStateOf(120) }
    var apCount by rememberSaveable { mutableIntStateOf(2) }
    var cameraCount by rememberSaveable { mutableIntStateOf(4) }
    var ptzCount by rememberSaveable { mutableIntStateOf(0) }
    var phoneCount by rememberSaveable { mutableIntStateOf(0) }

    val estimate = remember(switchBudget, apCount, cameraCount, ptzCount, phoneCount) {
        Rj45PoeReference.calculatePoeBudget(
            switchBudgetWatts = switchBudget,
            apCount = apCount,
            cameraCount = cameraCount,
            ptzCount = ptzCount,
            phoneCount = phoneCount
        )
    }

    val statusColor = when (estimate.status) {
        PoeBudgetStatus.SAFE -> AvatarEmerald
        PoeBudgetStatus.CAUTION -> P2Amber
        PoeBudgetStatus.OVERLOAD -> P1Crimson
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 1. Interactive PoE Switch Power Budget Estimator
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("poe_budget_calculator_card"),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = null,
                        tint = P2Amber,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Kalkulator Budget Daya PoE Switch",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Cek kecukupan Watt switch sebelum menambah AP / CCTV",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Switch capacity chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Rj45PoeReference.switchBudgetPresets.forEach { watts ->
                        val selected = switchBudget == watts
                        FilterChip(
                            selected = selected,
                            onClick = { switchBudget = watts },
                            label = {
                                Text(
                                    text = "${watts}W",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PingDuluNavy,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.testTag("poe_switch_preset_$watts")
                        )
                    }
                }

                PoeDeviceCounterRow(
                    label = "Access Point Wi-Fi (~15W)",
                    count = apCount,
                    onDecrement = { if (apCount > 0) apCount-- },
                    onIncrement = { if (apCount < 48) apCount++ },
                    tagPrefix = "poe_ap"
                )
                PoeDeviceCounterRow(
                    label = "IP Camera Fixed (~12W)",
                    count = cameraCount,
                    onDecrement = { if (cameraCount > 0) cameraCount-- },
                    onIncrement = { if (cameraCount < 48) cameraCount++ },
                    tagPrefix = "poe_cam"
                )
                PoeDeviceCounterRow(
                    label = "CCTV PTZ / Heater (~25W)",
                    count = ptzCount,
                    onDecrement = { if (ptzCount > 0) ptzCount-- },
                    onIncrement = { if (ptzCount < 24) ptzCount++ },
                    tagPrefix = "poe_ptz"
                )
                PoeDeviceCounterRow(
                    label = "IP Phone / VoIP (~7W)",
                    count = phoneCount,
                    onDecrement = { if (phoneCount > 0) phoneCount-- },
                    onIncrement = { if (phoneCount < 48) phoneCount++ },
                    tagPrefix = "poe_phone"
                )

                // Live utilization bar & summary
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
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
                                    imageVector = when (estimate.status) {
                                        PoeBudgetStatus.SAFE -> Icons.Rounded.CheckCircle
                                        PoeBudgetStatus.CAUTION -> Icons.Rounded.WarningAmber
                                        PoeBudgetStatus.OVERLOAD -> Icons.Rounded.ErrorOutline
                                    },
                                    contentDescription = null,
                                    tint = statusColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = estimate.status.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = statusColor
                                )
                            }
                            Text(
                                text = "${estimate.utilizationPercent}%",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                color = statusColor
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(7.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth((estimate.utilizationPercent / 100f).coerceIn(0f, 1f))
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(statusColor)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = String.format(Locale.US, "Beban: %.0fW / %dW", estimate.totalDemandWatts, estimate.switchBudgetWatts),
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (estimate.remainingWatts >= 0) {
                                    String.format(Locale.US, "Sisa: +%.0fW", estimate.remainingWatts)
                                } else {
                                    String.format(Locale.US, "Kurang: %.0fW", -estimate.remainingWatts)
                                },
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusColor
                            )
                        }
                    }
                }
            }
        }

        // 2. PoE Standards Reference List
        Text(
            text = "STANDAR IEEE PoE & PASSIVE PoE",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.6.sp
        )

        Rj45PoeReference.poeStandards.forEach { spec ->
            PoeStandardCard(spec = spec)
        }
    }
}

@Composable
private fun PoeDeviceCounterRow(
    label: String,
    count: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    tagPrefix: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedIconButton(
                onClick = onDecrement,
                enabled = count > 0,
                modifier = Modifier
                    .size(34.dp)
                    .testTag("${tagPrefix}_minus")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Remove,
                    contentDescription = "Kurangi $label",
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = count.toString(),
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(24.dp),
                textAlign = TextAlign.Center
            )
            OutlinedIconButton(
                onClick = onIncrement,
                modifier = Modifier
                    .size(34.dp)
                    .testTag("${tagPrefix}_plus")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Tambah $label",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun PoeStandardCard(spec: PoeStandardSpec) {
    val accent = if (spec.isPassiveWarning) P1Crimson else PingDuluNavy
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (spec.isPassiveWarning) P1Crimson.copy(alpha = 0.07f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (spec.isPassiveWarning) P1Crimson.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
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
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = accent.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = spec.standardCode,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = accent,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        text = spec.marketingName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "${spec.pseMaxWatts.toInt()}W",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    color = if (spec.isPassiveWarning) P1Crimson else PillDoneMint
                )
            }

            PinInfoRow("Daya PSE → PD", "${spec.pseMaxWatts}W → ${spec.pdUsableWatts}W")
            PinInfoRow("Tegangan", spec.voltageRange)
            PinInfoRow("Jalur Pin", spec.cablePairs)
            PinInfoRow("Minimal Kabel", spec.minCableCategory)

            Text(
                text = "Perangkat: ${spec.typicalDevices}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (spec.isPassiveWarning) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WarningAmber,
                        contentDescription = "Peringatan Passive PoE",
                        tint = P1Crimson,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "PERINGATAN: Tegangan selalu aktif tanpa handshake! Jangan colok ke port LAN biasa tanpa cek voltase.",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = P1Crimson
                    )
                }
            }
        }
    }
}

@Composable
private fun EthernetCableSpecsView() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "SPESIFIKASI KABEL TWISTED PAIR (UTP / STP)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.6.sp
        )
        Rj45PoeReference.cableSpecs.forEach { spec ->
            EthernetCableCard(spec = spec)
        }
    }
}

@Composable
private fun EthernetCableCard(spec: EthernetCableSpec) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(PingDuluNavy.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Cable,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = spec.category,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = spec.bandwidthMhz,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            PinInfoRow("Kecepatan Maks", spec.maxSpeed)
            PinInfoRow("Jarak Maksimal", spec.maxDistance)
            PinInfoRow("Dukungan PoE", spec.poeSupport)

            Text(
                text = spec.fieldNotes,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PinInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 10.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}
