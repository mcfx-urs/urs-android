package ch.mcfx.urs.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ch.mcfx.urs.BuildConfig
import ch.mcfx.urs.R
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing

@Composable
fun AboutScreen() {
    val colors = UrsTheme.colors
    val version = BuildConfig.VERSION_NAME.substringBefore("-")
    val buildType = BuildConfig.BUILD_TYPE.replaceFirstChar { it.uppercase() }

    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Image(
            painter = painterResource(R.drawable.urs_bear_logo),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
        )
        UrsText(
            stringResource(R.string.app_name),
            style = UrsTheme.typography.brand,
            color = colors.accent,
            modifier = Modifier.padding(top = Spacing.l),
        )
        UrsText(
            stringResource(R.string.about_org),
            style = UrsTheme.typography.body,
            color = colors.accent,
        )
        UrsText(
            stringResource(R.string.about_version_build_type, version, buildType),
            style = UrsTheme.typography.caption,
            color = colors.accent,
        )
        UrsText(
            stringResource(R.string.about_build_time, BuildConfig.BUILD_TIME),
            style = UrsTheme.typography.caption,
            color = colors.accent,
        )
        UrsText(
            stringResource(R.string.about_joke, BuildConfig.JOKE_OF_THE_DAY),
            style = UrsTheme.typography.caption.copy(textAlign = TextAlign.Center),
            color = colors.accent,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.l),
        )
    }
}
