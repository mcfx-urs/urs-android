package ch.mcfx.urs.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.ui.components.UrsBottomSheet
import ch.mcfx.urs.ui.components.UrsIcon
import ch.mcfx.urs.ui.components.UrsSquareTile
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.components.ursNavigationBarsBottomPadding
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing

private val ApproveTintColor = Color(0xFF2E8B57)
private val RejectTintColor = Color(0xFFD64545)

/**
 * Settings → Image Review — the super-user-gated approve/reject
 * queue for AI-generated catalog images. Only reachable when
 * AuthTokenStore.isSuperUser is true (see [SettingsScreen]) — the backend
 * also rejects the underlying admin catalog-image endpoints with 403 for
 * anyone else, this screen is just the UI on top. Tap a tile to
 * approve/reject it directly — unlike [ProductManagementScreen], there's no
 * separate view/edit action a plain tap needs to stay free for here.
 */
@Composable
fun ImageReviewScreen(viewModel: ImageReviewViewModel = viewModel(factory = ImageReviewViewModel.Factory)) {
    val images by viewModel.images.collectAsStateWithLifecycle()
    val loadFailed by viewModel.loadFailed.collectAsStateWithLifecycle()
    val actionSheetImage by viewModel.actionSheetImage.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(Spacing.l)) {
        when {
            loadFailed -> UrsText(
                text = stringResource(R.string.image_review_load_failed),
                color = RejectTintColor,
                modifier = Modifier.padding(top = Spacing.l),
            )

            images.isEmpty() -> UrsText(
                text = stringResource(R.string.image_review_empty),
                color = UrsTheme.colors.onSurfaceMuted,
                modifier = Modifier.padding(top = Spacing.l),
            )

            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = ursNavigationBarsBottomPadding(),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                items(images, key = { it.id }) { image ->
                    UrsSquareTile(
                        title = "",
                        catalogImageId = image.id.toIntOrNull(),
                        onClick = { viewModel.openActionSheet(image) },
                    )
                }
            }
        }
    }

    actionSheetImage?.let {
        UrsBottomSheet(onDismissRequest = viewModel::closeActionSheet) {
            ImageReviewActionSheet(onApprove = viewModel::approve, onReject = viewModel::reject)
        }
    }
}

@Composable
private fun ImageReviewActionSheet(onApprove: () -> Unit, onReject: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = Spacing.l).padding(bottom = Spacing.l)) {
        ImageReviewActionRow(
            label = stringResource(R.string.image_review_approve),
            icon = Icons.Filled.CheckCircle,
            tint = ApproveTintColor,
            onClick = onApprove,
        )
        ImageReviewActionRow(
            label = stringResource(R.string.image_review_reject),
            icon = Icons.Filled.Delete,
            tint = RejectTintColor,
            onClick = onReject,
        )
    }
}

@Composable
private fun ImageReviewActionRow(label: String, icon: ImageVector, tint: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UrsIcon(imageVector = icon, contentDescription = null, tint = tint)
        UrsText(label, style = UrsTheme.typography.cardTitle, color = tint)
    }
}
