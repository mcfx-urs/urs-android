package ch.mcfx.urs.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ch.mcfx.urs.R
import ch.mcfx.urs.data.remote.HouseholdUserDto
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing

/**
 * Reusable "manage sharing" bottom-sheet body — picks from the
 * household member list ([members]), toggling each on/off against
 * [sharedUserIds]. Deliberately UI-level reuse only, not backed by a
 * generic data-layer sharing abstraction: [onToggleShare] is left to the
 * caller to wire against whichever repository/endpoints actually apply
 * (inventory share vs list share — structurally identical but two entirely
 * separate backend routes, matching this codebase's explicit-per-feature
 * style elsewhere). Sharing itself is binary read+write, specific named
 * users — no permission-level picker.
 */
@Composable
fun UrsShareSheet(
    title: String,
    members: List<HouseholdUserDto>,
    sharedUserIds: Set<String>,
    onToggleShare: (userId: String, currentlyShared: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = Spacing.xl).padding(bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        UrsText(title, style = UrsTheme.typography.screenTitle)

        if (members.isEmpty()) {
            UrsText(
                text = stringResource(R.string.share_sheet_no_members),
                color = UrsTheme.colors.onSurfaceMuted,
                style = UrsTheme.typography.body,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                items(members, key = HouseholdUserDto::id) { member ->
                    val shared = member.id in sharedUserIds
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        UrsText(
                            text = member.userName.ifBlank { member.id },
                            style = UrsTheme.typography.body,
                            modifier = Modifier.weight(1f).padding(end = Spacing.m),
                        )
                        UrsCheckbox(
                            checked = shared,
                            onCheckedChange = { onToggleShare(member.id, shared) },
                        )
                    }
                }
            }
        }
    }
}
