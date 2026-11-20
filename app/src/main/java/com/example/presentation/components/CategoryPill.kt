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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.TicketCategory
import com.example.ui.theme.PingDuluFieldOpsTheme
import com.example.ui.theme.PingDuluTheme
import com.example.ui.theme.TicketCodeTextStyle

@Composable
fun CategoryPill(
    category: TicketCategory,
    text: String = category.code,
    modifier: Modifier = Modifier,
    prominent: Boolean = false
) {
    val badgeColors = PingDuluTheme.extendedColors.colorsForCategory(category)

    Surface(
        modifier = modifier
            .semantics { contentDescription = "Tiket kategori ${category.displayName}, kode $text" }
            .testTag("category_pill_${category.code.lowercase()}"),
        shape = RoundedCornerShape(6.dp),
        color = badgeColors.container,
        border = BorderStroke(width = 1.dp, color = badgeColors.border)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = if (prominent) 8.dp else 6.dp,
                vertical = if (prominent) 3.dp else 2.dp
            ),
            style = TicketCodeTextStyle.copy(
                fontSize = if (prominent) 11.sp else 10.sp
            ),
            color = badgeColors.content,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CategoryPillPreview() {
    PingDuluFieldOpsTheme {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CategoryPill(category = TicketCategory.NET, text = "NET-1")
            CategoryPill(category = TicketCategory.SRV, text = "SRV-3")
            CategoryPill(category = TicketCategory.HRD, text = "HRD-4")
            CategoryPill(category = TicketCategory.SEC, text = "SEC-2")
            CategoryPill(category = TicketCategory.ONB, text = "ONB-8")
        }
    }
}
