package ch.mcfx.urs.chores

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.ui.components.UrsCard
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing

/**
 * "Chores and Stuff" (GitHub issue #27). Part A: the data layer is wired and
 * syncing; this is a placeholder surface. The month-view calendar, day
 * sheet, type creation and stats strip land in part B.
 */
@Composable
fun ChoresScreen(viewModel: ChoresViewModel = viewModel(factory = ChoresViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        UrsText(stringResource(R.string.nav_chores), style = UrsTheme.typography.screenTitle)

        UrsText(
            stringResource(R.string.chores_placeholder_summary, state.activeTypes.size, state.events.size),
            style = UrsTheme.typography.body,
            color = UrsTheme.colors.onSurfaceMuted,
        )

        state.types.forEach { type ->
            UrsCard(modifier = Modifier.fillMaxWidth()) {
                UrsText(
                    text = if (type.archivedAtMillis == null) type.name else "${type.name} (archived)",
                    style = UrsTheme.typography.body,
                )
            }
        }
    }
}
