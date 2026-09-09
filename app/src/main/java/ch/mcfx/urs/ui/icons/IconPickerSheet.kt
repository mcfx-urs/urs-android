package ch.mcfx.urs.ui.icons

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ch.mcfx.urs.R
import ch.mcfx.urs.ui.components.UrsIcon
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.components.UrsTextField
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing

/**
 * Bottom-sheet content: a search field over [IconCatalog] plus a grid of the
 * matches. Reusable by any screen that needs to pick a bundled icon —
 * [selectedId] is the current choice (null = none), [onSelect] receives the
 * chosen id, or null when the user clears it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IconPickerSheet(
    selectedId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = UrsTheme.colors
    var query by remember { mutableStateOf("") }
    val results = remember(query) { IconCatalog.search(query) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.l)
            .padding(bottom = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            UrsText(
                text = stringResource(R.string.icon_picker_title),
                style = UrsTheme.typography.cardTitle,
                color = colors.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (selectedId != null) {
                UrsText(
                    text = stringResource(R.string.icon_picker_clear),
                    style = UrsTheme.typography.body,
                    color = colors.accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelect(null) }
                        .padding(horizontal = Spacing.s, vertical = Spacing.xs),
                )
            }
        }

        UrsTextField(
            value = query,
            onValueChange = { query = it },
            label = stringResource(R.string.icon_picker_search),
            singleLine = true,
        )

        if (results.isEmpty()) {
            UrsText(
                text = stringResource(R.string.icon_picker_no_matches),
                style = UrsTheme.typography.body,
                color = colors.onSurfaceMuted,
            )
        } else {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                results.forEach { entry ->
                    val selected = entry.id == selectedId
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) colors.accent.copy(alpha = 0.15f) else Color.Transparent)
                            .then(
                                if (selected) Modifier.border(1.dp, colors.accent, RoundedCornerShape(10.dp))
                                else Modifier,
                            )
                            .clickable { onSelect(entry.id) }
                            .padding(Spacing.s),
                        contentAlignment = Alignment.Center,
                    ) {
                        UrsIcon(
                            painter = painterResource(entry.drawable),
                            contentDescription = entry.id,
                            tint = colors.onSurface,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
    }
}
