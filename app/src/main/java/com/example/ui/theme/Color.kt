package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Behance Primary Brand & Hero Active Job Palette (Jonathan Herlim Adaptation)
val PingDuluNavyDark = Color(0xFF1E2875)
val PingDuluNavy = Color(0xFF36459B)
val PingDuluNavyLight = Color(0xFF4353B3)
val PingDuluIndigoSoftLight = Color(0xFFEEF2FF)
val PingDuluIndigoSoftDark = Color(0xFF1E2452)
val PingDuluIndigoBorderLight = Color(0xFFC7D2FE)
val PingDuluIndigoBorderDark = Color(0xFF373F7A)
val PingDuluIndigoAccentDark = Color(0xFFA5B4FC)

val HeroNavyGradient: Brush = Brush.linearGradient(
    colorStops = arrayOf(
        0.0f to PingDuluNavyDark,
        0.60f to PingDuluNavy,
        1.0f to PingDuluNavyLight
    )
)

// Behance Kanban Status Filter Pills
val PillBacklogLilac = Color(0xFF9B9AE1)
val PillProgressSky = Color(0xFF6DA7E8)
val PillDoneMint = Color(0xFF52C58F)

// Priority Tokens (Outdoor-Proof High Contrast)
val P1Crimson = Color(0xFFEF4444)
val P1CrimsonTextLight = Color(0xFFDC2626)
val P1CrimsonTextDark = Color(0xFFFCA5A5)
val P2Amber = Color(0xFFF59E0B)
val P2AmberTextLight = Color(0xFFB45309)
val P2AmberTextDark = Color(0xFFFCD34D)
val P3Blue = Color(0xFF3B82F6)
val P3BlueTextLight = Color(0xFF1D4ED8)
val P3BlueTextDark = Color(0xFF93C5FD)

// Canvas & Surface Tokens
val CanvasLight = Color(0xFFF8FAFC)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceVariantLight = Color(0xFFF1F5F9)
val OutlineLight = Color(0xFFE2E8F0)
val TextPrimaryLight = Color(0xFF0F172A)
val TextSecondaryLight = Color(0xFF64748B)

val CanvasDark = Color(0xFF0F172A)
val SurfaceDark = Color(0xFF1E293B)
val SurfaceVariantDark = Color(0xFF273449)
val OutlineDark = Color(0xFF334155)
val TextPrimaryDark = Color(0xFFF8FAFC)
val TextSecondaryDark = Color(0xFF94A3B8)

// Live Stopwatch & Sync Semantic Colors
val StopwatchMint = Color(0xFF6EE7B7)
val SyncGreenLight = Color(0xFF047857)
val SyncGreenBgLight = Color(0xFFECFDF5)
val SyncGreenBorderLight = Color(0xFF6EE7B7)
val SyncGreenDark = Color(0xFF6EE7B7)
val SyncGreenBgDark = Color(0xFF064E3B)
val SyncGreenBorderDark = Color(0xFF059669)

// Category Pill Colors (Light & Dark)
val CatSrvBgLight = Color(0xFFFAF5FF)
val CatSrvFgLight = Color(0xFF7E22CE)
val CatSrvBorderLight = Color(0xFFE9D5FF)
val CatSrvBgDark = Color(0xFF2E1065)
val CatSrvFgDark = Color(0xFFD8B4FE)
val CatSrvBorderDark = Color(0xFF6B21A8)

val CatHrdBgLight = Color(0xFFFFFBEB)
val CatHrdFgLight = Color(0xFFB45309)
val CatHrdBorderLight = Color(0xFFFDE68A)
val CatHrdBgDark = Color(0xFF451A03)
val CatHrdFgDark = Color(0xFFFCD34D)
val CatHrdBorderDark = Color(0xFF92400E)

val CatSecBgLight = Color(0xFFFFF1F2)
val CatSecFgLight = Color(0xFFBE123C)
val CatSecBorderLight = Color(0xFFFECDD3)
val CatSecBgDark = Color(0xFF4C0519)
val CatSecFgDark = Color(0xFFFDA4AF)
val CatSecBorderDark = Color(0xFF9F1239)

val CatOnbBgLight = Color(0xFFF0FDFA)
val CatOnbFgLight = Color(0xFF0F766E)
val CatOnbBorderLight = Color(0xFF99F6E4)
val CatOnbBgDark = Color(0xFF042F2E)
val CatOnbFgDark = Color(0xFF5EEAD4)
val CatOnbBorderDark = Color(0xFF115E59)

// Technician Avatar Palette
val AvatarEmerald = Color(0xFF059669)
val AvatarCobalt = Color(0xFF36459B)
val AvatarSky = Color(0xFF0284C7)
val AvatarViolet = Color(0xFF7C3AED)
val AvatarTeal = Color(0xFF0D9488)
val LeadStarAmber = Color(0xFFFBBF24)
