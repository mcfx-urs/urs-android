package ch.mcfx.urs.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.chores.parseChoreColor
import ch.mcfx.urs.chores.readableTextColor
import ch.mcfx.urs.data.remote.TagDto
import ch.mcfx.urs.ui.components.UrsCard
import ch.mcfx.urs.ui.components.UrsColorWheelDialog
import ch.mcfx.urs.ui.components.UrsPill
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Radius
import ch.mcfx.urs.ui.tokens.Spacing

/**
 * Settings → Tags — lists the shared Notes/Kanban tag pool
 * (mcfx-urs/urs-backend#11) with its current colors, and lets any one be
 * recolored (mcfx-urs/urs-android#103) via the same free-form color-wheel
 * picker Life Map/Breadcrumbs' track-color setting already uses
 * ([UrsColorWheelDialog]) — any individual color, not a fixed palette, since
 * a small fixed pool doesn't actually solve two tags colliding once there
 * are more tags than palette entries.
 */
@Composable
fun TagsSettingsScreen(viewModel: TagsSettingsViewModel = viewModel(factory = TagsSettingsViewModel.Factory)) {
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val editingTag by viewModel.editingTag.collectAsStateWithLifecycle()

    if (tags.isEmpty()) {
        Box(Modifier.fillMaxSize()) {
            UrsText(
                text = stringResource(R.string.tags_settings_empty),
                color = UrsTheme.colors.onSurfaceMuted,
                modifier = Modifier.align(Alignment.Center).padding(Spacing.l),
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            items(tags, key = { it.name }) { tag ->
                TagRow(tag = tag, onClick = { viewModel.openColorPicker(tag) })
            }
        }
    }

    val currentEditingTag = editingTag
    if (currentEditingTag != null) {
        UrsColorWheelDialog(
            initialColor = parseChoreColor(currentEditingTag.color).toArgb(),
            title = currentEditingTag.name,
            onConfirm = { argb -> viewModel.setColor(String.format("#%06X", argb and 0xFFFFFF)) },
            onDismiss = viewModel::closeColorPicker,
        )
    }
}

@Composable
private fun TagRow(tag: TagDto, onClick: () -> Unit) {
    val color = parseChoreColor(tag.color)
    UrsCard(
        radius = Radius.row,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        UrsPill(text = tag.name, containerColor = color, contentColor = readableTextColor(color))
    }
}
