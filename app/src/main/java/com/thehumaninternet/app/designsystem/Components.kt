package com.thehumaninternet.app.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thehumaninternet.app.R

/** A text CTA in the given role. Pass `Modifier.weight(1f)` to pair two in a row. */
@Composable
fun CtaButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    role: CTARole = CTARole.PRIMARY,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(Theme.cornerRadius)
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.5f)
            .clip(shape)
            .background(role.background)
            .border(1.dp, role.outline, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(title, style = Theme.font(16, FontWeight.SemiBold), color = role.foreground)
    }
}

/** An icon-only CTA: the glyph centred on a circle in the given role. */
@Composable
fun CtaIcon(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    role: CTARole = CTARole.PRIMARY,
    diameter: Dp = 36.dp,
    iconSize: Dp = diameter * 0.5f,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .size(diameter)
            .clip(CircleShape)
            .background(role.background)
            .border(1.dp, role.outline, CircleShape)
            .then(
                if (onClick != null) Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = role.foreground, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun HITextField(
    placeholder: String,
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    BasicTextField(
        value = text,
        onValueChange = onTextChange,
        singleLine = true,
        textStyle = Theme.font(17).copy(color = Theme.textPrimary),
        cursorBrush = SolidColor(Theme.textPrimary),
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Theme.surface)
            .border(1.dp, Theme.divider, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        decorationBox = { field ->
            if (text.isEmpty()) {
                Text(placeholder, style = Theme.font(17), color = Theme.placeholder)
            }
            field()
        },
    )
}

@Composable
fun FieldLabel(text: String) {
    Text(text, style = Theme.font(13, FontWeight.Medium), color = Theme.textSecondary)
}

@Composable
fun RadioRow(title: String, isSelected: Boolean, onClick: () -> Unit, subtitle: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            if (isSelected) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            tint = Theme.selection,
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = Theme.font(15, FontWeight.Medium), color = Theme.textPrimary)
            if (subtitle != null) {
                Text(subtitle, style = Theme.font(12), color = Theme.textSecondary)
            }
        }
    }
}

/**
 * The logo. Drawn from the same artwork as the photo watermark, so the two
 * can't drift apart.
 */
@Composable
fun BrandMark(width: Dp = 60.dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.brand_mark_watermark),
        contentDescription = null,
        // The artwork is 1024×860.
        modifier = modifier.width(width).aspectRatio(1024f / 860f),
    )
}

@Composable
fun Spinner(modifier: Modifier = Modifier, color: Color = Theme.textPrimary, diameter: Dp = 28.dp) {
    CircularProgressIndicator(modifier = modifier.size(diameter), color = color, strokeWidth = 2.5.dp)
}
