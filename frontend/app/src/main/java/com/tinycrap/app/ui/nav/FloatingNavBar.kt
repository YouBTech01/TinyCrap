package com.tinycrap.app.ui.nav

import com.tinycrap.app.ui.components.pressScale
import com.tinycrap.app.ui.components.Motion
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

enum class Destination(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Home("Home", Icons.Outlined.Home, Icons.Rounded.Home),
    Downloads("Downloaded", Icons.Outlined.Download, Icons.Rounded.Download),
    Settings("Settings", Icons.Outlined.Settings, Icons.Rounded.Settings),
}

/** A pill that floats above the content. The selected tab grows to show its name. */
@Composable
fun FloatingNavBar(
    selected: Int,
    activeDownloads: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 6.dp,
        shadowElevation = 12.dp,
    ) {
        Row(
            modifier = Modifier.padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Destination.entries.forEachIndexed { index, destination ->
                NavItem(
                    destination = destination,
                    selected = index == selected,
                    badge = if (destination == Destination.Downloads) activeDownloads else 0,
                    onClick = { onSelect(index) },
                )
            }
        }
    }
}

@Composable
private fun NavItem(destination: Destination, selected: Boolean, badge: Int, onClick: () -> Unit) {
    val background by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        label = "navBackground",
    )
    val tint by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "navTint",
    )
    val source = remember { MutableInteractionSource() }
    val iconScale = animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 520f),
        label = "navIcon",
    )
    Row(
        modifier = Modifier
            .height(48.dp)
            .pressScale(source, 0.94f)
            .clip(CircleShape)
            .background(background)
            .selectable(selected = selected, role = Role.Tab, interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp)
            .animateContentSize(Motion.spatial()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BadgedBox(
            badge = { if (badge > 0) Badge { Text(badge.toString()) } },
        ) {
            Icon(
                if (selected) destination.selectedIcon else destination.icon,
                contentDescription = if (selected) null else destination.label,
                tint = tint,
                modifier = Modifier.graphicsLayer {
                    scaleX = iconScale.value
                    scaleY = iconScale.value
                },
            )
        }
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn(Motion.effects(180)) + expandHorizontally(Motion.spatial()),
            exit = fadeOut(Motion.effects(120)) + shrinkHorizontally(Motion.spatial()),
        ) {
            Text(
                destination.label,
                style = MaterialTheme.typography.titleSmall,
                color = tint,
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
