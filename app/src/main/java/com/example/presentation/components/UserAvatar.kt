package com.example.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.remote.PingDuluApiService
import com.example.domain.model.resolveAvatarUrl
import com.example.ui.theme.ActiveStatusEmerald
import com.example.ui.theme.AvatarGradientEnd
import com.example.ui.theme.AvatarGradientStart
import com.example.ui.theme.InactiveStatusSlate

/**
 * Reusable circular avatar component (Patch Ke-8).
 * Loads server avatar via Coil [AsyncImage] when available and automatically falls back
 * to an initial avatar badge if the URL is null/blank or fails to load offline.
 */
@Composable
fun UserAvatar(
    avatarUrl: String?,
    displayName: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    isActive: Boolean = true,
    showActiveBadge: Boolean = false,
    serverBaseUrl: String = PingDuluApiService.FIXED_BASE_URL,
    fallbackColor: Color? = null,
    ringColor: Color = MaterialTheme.colorScheme.surface,
    ringWidth: Dp = 1.5.dp
) {
    val resolvedUrl = remember(serverBaseUrl, avatarUrl) {
        resolveAvatarUrl(serverBaseUrl, avatarUrl)
    }
    var imageFailed by remember(resolvedUrl) { mutableStateOf(false) }
    val initial = remember(displayName) {
        displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "U"
    }
    val context = LocalContext.current

    Box(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "Foto $displayName" },
        contentAlignment = Alignment.Center
    ) {
        if (!resolvedUrl.isNullOrBlank() && !imageFailed) {
            // Render initial underneath while Coil fetches, so there is never an empty flash
            InitialAvatarFill(
                initial = initial,
                size = size,
                fallbackColor = fallbackColor,
                ringColor = ringColor,
                ringWidth = ringWidth
            )
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(resolvedUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "Foto $displayName",
                contentScale = ContentScale.Crop,
                onError = { imageFailed = true },
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .border(ringWidth, ringColor, CircleShape)
            )
        } else {
            InitialAvatarFill(
                initial = initial,
                size = size,
                fallbackColor = fallbackColor,
                ringColor = ringColor,
                ringWidth = ringWidth
            )
        }

        if (showActiveBadge) {
            val badgeSize = (size.value * 0.28f).dp.coerceIn(9.dp, 22.dp)
            val dotColor = if (isActive) ActiveStatusEmerald else InactiveStatusSlate
            Box(
                modifier = Modifier
                    .size(badgeSize)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(dotColor)
                    .border(2.dp, ringColor, CircleShape)
            )
        }
    }
}

@Composable
private fun InitialAvatarFill(
    initial: String,
    size: Dp,
    fallbackColor: Color?,
    ringColor: Color,
    ringWidth: Dp
) {
    val bgModifier = if (fallbackColor != null) {
        Modifier.background(fallbackColor)
    } else {
        Modifier.background(
            Brush.linearGradient(
                listOf(AvatarGradientStart, AvatarGradientEnd)
            )
        )
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(CircleShape)
            .then(bgModifier)
            .border(ringWidth, ringColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = (size.value * 0.42f).sp,
            lineHeight = (size.value * 0.46f).sp,
            maxLines = 1,
            softWrap = false
        )
    }
}
