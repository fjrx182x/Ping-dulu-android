package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.domain.model.Priority
import com.example.domain.model.Stage
import com.example.domain.model.TicketCategory

@Immutable
data class BadgeColors(
    val container: Color,
    val content: Color,
    val border: Color
)

@Immutable
data class PingDuluExtendedColors(
    val isDark: Boolean,
    val heroPrimary: Color,
    val heroDark: Color,
    val heroLight: Color,
    val stopwatchMint: Color,
    val pillBacklog: Color,
    val pillInProgress: Color,
    val pillDone: Color,
    val priorityP1: BadgeColors,
    val priorityP2: BadgeColors,
    val priorityP3: BadgeColors,
    val categoryNet: BadgeColors,
    val categorySrv: BadgeColors,
    val categoryHrd: BadgeColors,
    val categorySec: BadgeColors,
    val categoryOnb: BadgeColors,
    val syncOnline: BadgeColors,
    val cardBorderSubtle: Color
) {
    fun colorsForPriority(priority: Priority): BadgeColors = when (priority) {
        Priority.P1_CRITICAL -> priorityP1
        Priority.P2_HIGH -> priorityP2
        Priority.P3_NORMAL -> priorityP3
    }

    fun colorsForCategory(category: TicketCategory): BadgeColors = when (category) {
        TicketCategory.NET -> categoryNet
        TicketCategory.SRV -> categorySrv
        TicketCategory.HRD -> categoryHrd
        TicketCategory.SEC -> categorySec
        TicketCategory.ONB -> categoryOnb
    }

    fun accentForStage(stage: Stage): Color = when (stage) {
        Stage.BACKLOG -> pillBacklog
        Stage.IN_PROGRESS -> pillInProgress
        Stage.DONE -> pillDone
    }
}

private val LightExtendedColors = PingDuluExtendedColors(
    isDark = false,
    heroPrimary = PingDuluNavy,
    heroDark = PingDuluNavyDark,
    heroLight = PingDuluNavyLight,
    stopwatchMint = StopwatchMint,
    pillBacklog = PillBacklogLilac,
    pillInProgress = PillProgressSky,
    pillDone = PillDoneMint,
    priorityP1 = BadgeColors(
        container = P1Crimson.copy(alpha = 0.12f),
        content = P1CrimsonTextLight,
        border = P1Crimson.copy(alpha = 0.35f)
    ),
    priorityP2 = BadgeColors(
        container = P2Amber.copy(alpha = 0.14f),
        content = P2AmberTextLight,
        border = P2Amber.copy(alpha = 0.40f)
    ),
    priorityP3 = BadgeColors(
        container = P3Blue.copy(alpha = 0.12f),
        content = P3BlueTextLight,
        border = P3Blue.copy(alpha = 0.35f)
    ),
    categoryNet = BadgeColors(
        container = PingDuluIndigoSoftLight,
        content = PingDuluNavy,
        border = PingDuluIndigoBorderLight
    ),
    categorySrv = BadgeColors(
        container = CatSrvBgLight,
        content = CatSrvFgLight,
        border = CatSrvBorderLight
    ),
    categoryHrd = BadgeColors(
        container = CatHrdBgLight,
        content = CatHrdFgLight,
        border = CatHrdBorderLight
    ),
    categorySec = BadgeColors(
        container = CatSecBgLight,
        content = CatSecFgLight,
        border = CatSecBorderLight
    ),
    categoryOnb = BadgeColors(
        container = CatOnbBgLight,
        content = CatOnbFgLight,
        border = CatOnbBorderLight
    ),
    syncOnline = BadgeColors(
        container = SyncGreenBgLight,
        content = SyncGreenLight,
        border = SyncGreenBorderLight
    ),
    cardBorderSubtle = OutlineLight
)

private val DarkExtendedColors = PingDuluExtendedColors(
    isDark = true,
    heroPrimary = PingDuluNavy,
    heroDark = PingDuluNavyDark,
    heroLight = PingDuluNavyLight,
    stopwatchMint = StopwatchMint,
    pillBacklog = PillBacklogLilac,
    pillInProgress = PillProgressSky,
    pillDone = PillDoneMint,
    priorityP1 = BadgeColors(
        container = P1Crimson.copy(alpha = 0.18f),
        content = P1CrimsonTextDark,
        border = P1Crimson.copy(alpha = 0.45f)
    ),
    priorityP2 = BadgeColors(
        container = P2Amber.copy(alpha = 0.18f),
        content = P2AmberTextDark,
        border = P2Amber.copy(alpha = 0.45f)
    ),
    priorityP3 = BadgeColors(
        container = P3Blue.copy(alpha = 0.18f),
        content = P3BlueTextDark,
        border = P3Blue.copy(alpha = 0.45f)
    ),
    categoryNet = BadgeColors(
        container = PingDuluIndigoSoftDark,
        content = PingDuluIndigoAccentDark,
        border = PingDuluIndigoBorderDark
    ),
    categorySrv = BadgeColors(
        container = CatSrvBgDark,
        content = CatSrvFgDark,
        border = CatSrvBorderDark
    ),
    categoryHrd = BadgeColors(
        container = CatHrdBgDark,
        content = CatHrdFgDark,
        border = CatHrdBorderDark
    ),
    categorySec = BadgeColors(
        container = CatSecBgDark,
        content = CatSecFgDark,
        border = CatSecBorderDark
    ),
    categoryOnb = BadgeColors(
        container = CatOnbBgDark,
        content = CatOnbFgDark,
        border = CatOnbBorderDark
    ),
    syncOnline = BadgeColors(
        container = SyncGreenBgDark,
        content = SyncGreenDark,
        border = SyncGreenBorderDark
    ),
    cardBorderSubtle = OutlineDark
)

private val LightColorScheme = lightColorScheme(
    primary = PingDuluNavy,
    onPrimary = Color.White,
    primaryContainer = PingDuluIndigoSoftLight,
    onPrimaryContainer = PingDuluNavyDark,
    secondary = PillProgressSky,
    onSecondary = Color.White,
    secondaryContainer = SurfaceVariantLight,
    onSecondaryContainer = TextPrimaryLight,
    tertiary = PillDoneMint,
    onTertiary = Color.White,
    background = CanvasLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = OutlineLight,
    outlineVariant = OutlineLight.copy(alpha = 0.65f),
    error = P1Crimson,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = PingDuluIndigoAccentDark,
    onPrimary = PingDuluNavyDark,
    primaryContainer = PingDuluIndigoSoftDark,
    onPrimaryContainer = PingDuluIndigoAccentDark,
    secondary = PillProgressSky,
    onSecondary = CanvasDark,
    secondaryContainer = SurfaceVariantDark,
    onSecondaryContainer = TextPrimaryDark,
    tertiary = PillDoneMint,
    onTertiary = CanvasDark,
    background = CanvasDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = OutlineDark,
    outlineVariant = OutlineDark.copy(alpha = 0.65f),
    error = P1CrimsonTextDark,
    onError = CanvasDark
)

val PingDuluShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

val LocalPingDuluColors = staticCompositionLocalOf { LightExtendedColors }

object PingDuluTheme {
    val extendedColors: PingDuluExtendedColors
        @Composable
        get() = LocalPingDuluColors.current
}

@Composable
fun PingDuluFieldOpsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalPingDuluColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = PingDuluShapes,
            content = content
        )
    }
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    PingDuluFieldOpsTheme(darkTheme = darkTheme, content = content)
}
