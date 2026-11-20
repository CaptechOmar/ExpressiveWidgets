package com.captechomar.widgets.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.captechomar.widgets.widgets.Palettes

/**
 * A horizontally scrolling row of colour swatches, the same picker the Expressive Clock app uses:
 * each circle is a gradient of the palette's primary, tertiary and secondary.
 */
@Composable
fun PaletteRow(selectedId: String, onSelect: (String) -> Unit) {
    val context = LocalContext.current
    val dark = Palettes.isDark(context)
    val current = Palettes.normalize(selectedId)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(Palettes.options, key = { it.id }) { option ->
            val scheme: ColorScheme? = if (option.id == "dynamic") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                } else null
            } else Palettes.scheme(option.id, dark)
            if (scheme != null) Swatch(option.label, scheme, current == option.id) { onSelect(option.id) }
        }
    }
}

@Composable
private fun Swatch(name: String, scheme: ColorScheme, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(0f to scheme.primary, 0.5f to scheme.tertiary, 1f to scheme.secondary))
                .border(if (selected) 3.dp else 0.dp, if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
                .clickable(role = Role.RadioButton, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}
