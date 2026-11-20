package com.example.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AssignmentTurnedIn
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ViewKanban
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AssignmentTurnedIn
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ViewKanban
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.PingDuluNavy

enum class AppDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    AGENDA(
        route = "agenda",
        label = "Agenda",
        selectedIcon = Icons.Rounded.Home,
        unselectedIcon = Icons.Outlined.Home
    ),
    KANBAN(
        route = "kanban",
        label = "Kanban",
        selectedIcon = Icons.Rounded.ViewKanban,
        unselectedIcon = Icons.Outlined.ViewKanban
    ),
    SPK(
        route = "spk",
        label = "SPK",
        selectedIcon = Icons.Rounded.AssignmentTurnedIn,
        unselectedIcon = Icons.Outlined.AssignmentTurnedIn
    ),
    SETTINGS(
        route = "settings",
        label = "Pengaturan",
        selectedIcon = Icons.Rounded.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )
}

@Composable
fun PingDuluBottomNavigationBar(
    currentDestination: AppDestination,
    onDestinationSelected: (AppDestination) -> Unit,
    onQuickAddClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag("bottom_navigation_bar")
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 10.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BottomNavTabItem(
                        destination = AppDestination.AGENDA,
                        selected = currentDestination == AppDestination.AGENDA,
                        onClick = { onDestinationSelected(AppDestination.AGENDA) },
                        modifier = Modifier.weight(1f)
                    )

                    BottomNavTabItem(
                        destination = AppDestination.KANBAN,
                        selected = currentDestination == AppDestination.KANBAN,
                        onClick = { onDestinationSelected(AppDestination.KANBAN) },
                        modifier = Modifier.weight(1f)
                    )

                    // Center spacer reserving space under the floating FAB
                    Spacer(modifier = Modifier.weight(1f))

                    BottomNavTabItem(
                        destination = AppDestination.SPK,
                        selected = currentDestination == AppDestination.SPK,
                        onClick = { onDestinationSelected(AppDestination.SPK) },
                        modifier = Modifier.weight(1f)
                    )

                    BottomNavTabItem(
                        destination = AppDestination.SETTINGS,
                        selected = currentDestination == AppDestination.SETTINGS,
                        onClick = { onDestinationSelected(AppDestination.SETTINGS) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Center Elevated Quick-Add FAB (+) cleanly straddling top divider with zero clipping
        FloatingActionButton(
            onClick = onQuickAddClicked,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(52.dp)
                .border(
                    width = 2.5.dp,
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp)
                )
                .testTag("fab_quick_add_ticket"),
            shape = RoundedCornerShape(16.dp),
            containerColor = PingDuluNavy,
            contentColor = Color.White,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 6.dp,
                pressedElevation = 10.dp
            )
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "Tambah tiket cepat",
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
private fun BottomNavTabItem(
    destination: AppDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        onClick = onClick,
        modifier = modifier
            .widthIn(min = 48.dp)
            .semantics {
                this.selected = selected
                contentDescription = "Tab ${destination.label}"
            }
            .testTag("nav_tab_${destination.route}"),
        color = Color.Transparent,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            Color.Transparent
                        }
                    )
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                    contentDescription = null,
                    tint = if (selected) activeColor else inactiveColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = destination.label,
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) activeColor else inactiveColor,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
fun PingDuluNavigationRail(
    currentDestination: AppDestination,
    onDestinationSelected: (AppDestination) -> Unit,
    onQuickAddClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationRail(
        modifier = modifier.testTag("adaptive_navigation_rail"),
        containerColor = MaterialTheme.colorScheme.surface,
        header = {
            Spacer(modifier = Modifier.height(12.dp))
            FloatingActionButton(
                onClick = onQuickAddClicked,
                modifier = Modifier.testTag("rail_fab_quick_add"),
                shape = RoundedCornerShape(16.dp),
                containerColor = PingDuluNavy,
                contentColor = Color.White
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Tambah tiket cepat"
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    ) {
        AppDestination.entries.forEach { destination ->
            val selected = currentDestination == destination
            NavigationRailItem(
                selected = selected,
                onClick = { onDestinationSelected(destination) },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = destination.label
                    )
                },
                label = {
                    Text(
                        text = destination.label,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            )
        }
    }
}
