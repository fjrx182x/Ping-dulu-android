package com.example.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R

private val PingDuluLogoTopBlue = Color(0xFF2563EB)
private val PingDuluLogoBottomBlue = Color(0xFF1D4ED8)

/**
 * Official PingDulu squircle brand mark rendering the server-issued wizard-hat launcher foreground
 * centered inside the official royal-blue vertical gradient container.
 */
@Composable
fun PingDuluLogoMark(
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    cornerRadius: Dp = 10.dp,
    borderStroke: BorderStroke? = null,
    contentDescription: String = "Logo PingDulu"
) {
    val shape = RoundedCornerShape(cornerRadius)
    Surface(
        modifier = modifier.size(size),
        shape = shape,
        color = Color.Transparent,
        border = borderStroke
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(PingDuluLogoTopBlue, PingDuluLogoBottomBlue)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}
