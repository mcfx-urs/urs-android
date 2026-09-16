package ch.mcfx.urs.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.components.ursScreenContentPadding
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing

private const val ChangelogAssetPath = "CHANGELOG.md"

/**
 * Read-only viewer for the bundled `CHANGELOG.md` asset (GitHub issue #82) —
 * a straight copy taken at implementation time, kept in sync manually at
 * each future release, same as the app's own version number.
 */
@Composable
fun ChangelogScreen() {
    val context = LocalContext.current
    val blocks = remember {
        val markdown = context.assets.open(ChangelogAssetPath).bufferedReader().use { it.readText() }
        ChangelogParser.parse(markdown)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = ursScreenContentPadding(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        items(blocks) { block -> ChangelogBlockRow(block) }
    }
}

@Composable
private fun ChangelogBlockRow(block: ChangelogBlock) {
    when (block) {
        is ChangelogBlock.Header -> UrsText(
            block.text,
            style = when (block.level) {
                1 -> UrsTheme.typography.screenTitle
                2 -> UrsTheme.typography.cardTitle
                else -> UrsTheme.typography.body
            },
            color = UrsTheme.colors.accent,
            modifier = Modifier.padding(top = if (block.level == 1) 0.dp else Spacing.m),
        )
        is ChangelogBlock.Bullet -> UrsText("• ${block.text}", style = UrsTheme.typography.body)
        is ChangelogBlock.Paragraph -> UrsText(block.text, style = UrsTheme.typography.body)
    }
}
