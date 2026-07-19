package ch.mcfx.urs.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ch.mcfx.urs.BuildConfig
import ch.mcfx.urs.R
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing

@Composable
fun AboutScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        UrsText(
            stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
            style = UrsTheme.typography.body,
        )
    }
}
