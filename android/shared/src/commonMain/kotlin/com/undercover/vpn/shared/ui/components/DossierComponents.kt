package com.undercover.vpn.shared.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.undercover.vpn.shared.ui.theme.DossierTypography

@Composable
fun DossierButton(
    text: String,
    isOutlined: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    val bgColor = if (isOutlined) Color.Transparent else MaterialTheme.colorScheme.primary
    val fgColor = if (isOutlined) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary
    val borderColor = if (isOutlined) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .then(if (isOutlined) Modifier.border(1.dp, borderColor) else Modifier)
            .background(bgColor)
            .clickable { onClick() }
            .padding(vertical = 18.dp, horizontal = 24.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                androidx.compose.material3.Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = fgColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(
                text = text,
                color = fgColor,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            )
        }
    }
}

@Composable
fun RedactedText(
    text: String,
    isRedacted: Boolean = false,
    redactedPlaceholder: String = "redacted",
    modifier: Modifier = Modifier,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium
) {
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(modifier = modifier, contentAlignment = Alignment.CenterStart) {
        Text(
            text = if (isRedacted) redactedPlaceholder else text,
            style = textStyle,
            color = MaterialTheme.colorScheme.primary
        )
        if (isRedacted) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
            )
        }
    }
}

@Composable
fun DossierSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val trackColor = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    val thumbColor = MaterialTheme.colorScheme.onPrimary
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(width = 48.dp, height = 24.dp)
            .background(if (checked) trackColor else Color.Transparent)
            .border(2.dp, trackColor)
            .clickable(
                interactionSource = interactionSource, 
                indication = null, 
                enabled = enabled && onCheckedChange != null
            ) { onCheckedChange?.invoke(!checked) }
            .padding(4.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(if (checked) thumbColor else trackColor)
        )
    }
}

@Composable
fun DossierCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val color = MaterialTheme.colorScheme.primary
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(20.dp)
            .border(1.dp, color)
            .clickable(interactionSource = interactionSource, indication = null) { onCheckedChange(!checked) }
    ) {
        if (checked) {
            Box(modifier = Modifier.size(12.dp).background(color))
        }
    }
}

@Composable
fun DossierRadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val borderColor = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    val fillContentColor = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(20.dp)
            .border(1.5.dp, borderColor)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = enabled,
                        onClick = onClick
                    )
                } else Modifier
            )
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(fillContentColor)
            )
        }
    }
}

data class DossierNavItem(
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    val hasBadge: Boolean = false
)

@Composable
fun DossierBottomNav(
    items: List<DossierNavItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, item ->
                    val isSelected = index == selectedIndex
                    val itemColor = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onItemSelected(index) }
                            .padding(vertical = 4.dp, horizontal = 2.dp)
                    ) {
                        if (item.icon != null) {
                            if (item.hasBadge) {
                                androidx.compose.material3.BadgedBox(
                                    badge = {
                                        androidx.compose.material3.Badge(
                                            containerColor = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(6.dp)
                                        )
                                    }
                                ) {
                                    androidx.compose.material3.Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.title,
                                        tint = itemColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            } else {
                                androidx.compose.material3.Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.title,
                                    tint = itemColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                letterSpacing = 0.5.sp
                            ),
                            color = itemColor,
                            maxLines = 1,
                            softWrap = false,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .height(2.dp)
                                    .width(24.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary,
                                        androidx.compose.foundation.shape.RoundedCornerShape(1.dp)
                                    )
                            )
                        } else {
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                    }
                }
            }
        }
    }
}
