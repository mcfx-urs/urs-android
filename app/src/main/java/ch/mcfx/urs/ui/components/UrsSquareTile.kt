package ch.mcfx.urs.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import ch.mcfx.urs.BuildConfig
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.ui.theme.LightUrsColors
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Radius
import ch.mcfx.urs.ui.tokens.Spacing
import coil3.compose.SubcomposeAsyncImage

private val HazardStripeYellow = Color(0xFFFFC400)
private val HazardStripeBlack = Color(0xFF1A1A1A)

/** `${BuildConfig.BASE_URL}api/v1/catalog-image/{catalogImageId}` — the one place this URL shape is built. */
fun catalogImageUrl(catalogImageId: Int?): String? = catalogImageId?.let { "${BuildConfig.BASE_URL}api/v1/catalog-image/$it" }

/**
 * `LazyVerticalGrid(GridCells.Fixed(3))`-friendly product tile, replacing
 * the row-based product/list-item layout across Inventory and Shopping List
 * (the shared-catalog grid redesign). Built on [UrsCard] for the same elevated-surface
 * treatment every other card in this app already has. [AsyncImage]-backed
 * (Coil, via [ch.mcfx.urs.UrsApplication.container]'s authenticated
 * `imageLoader` — `/api/v1/catalog-image/{id}` sits on the protected route
 * subrouter server-side, so an unauthenticated request would 401), falling
 * back to [Icons.Filled.ShoppingBasket] both while there's no
 * [catalogImageId] at all and on an actual load error.
 *
 * [dimmed] renders the title strikethrough and the whole tile at reduced
 * opacity — the add-product picker's "already on this list" visual state.
 * [mutedTitle] only affects the title text (strikethrough, muted grey),
 * leaving the rest of the tile at full opacity — the shopping list's
 * "recently used" suggestions, which aren't dimmed/disabled, just marked.
 * [quantityBadge] is an optional small overlay for inventory screens
 * (tracked quantity) or the shopping-list add-picker (quantity-on-hand hint).
 * [topEndBadge] is a second, independent overlay in the opposite corner —
 * the shopping list's "amount needed" ("Nx"), shown only when set. [hazardBorder]
 * draws a yellow/black diagonal-stripe border around the whole tile — the
 * shopping list's "only buy this on sale" marker.
 * [onLongClick], when non-null, wires the tile as a combined click target
 * (e.g. ListDetailScreen's tap-to-remove/long-press-to-edit-note) rather
 * than a plain one — kept internal to this component so a call site never
 * has to layer its own gesture detector on top of [onClick]'s.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UrsSquareTile(
    title: String,
    catalogImageId: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    dimmed: Boolean = false,
    mutedTitle: Boolean = false,
    quantityBadge: String? = null,
    topEndBadge: String? = null,
    hazardBorder: Boolean = false,
) {
    val alpha = if (dimmed) UrsTheme.colors.disabledAlpha else 1f
    val titleStrikethrough = dimmed || mutedTitle
    val titleColor = if (mutedTitle && !dimmed) LightUrsColors.onSurfaceMuted else LightUrsColors.onSurface.copy(alpha = alpha)

    UrsCard(
        radius = Radius.row,
        contentPadding = PaddingValues(0.dp),
        // Always the light palette's card color, regardless of the app's
        // active theme — catalog product photos are studio shots on a
        // white/cream backdrop (source data, not something this app
        // controls), so the whole tile (photo *and* title) takes on that
        // same light backdrop instead of only the image area. That's what
        // makes the photo's own background read as intentional rather than
        // a stray white patch dropped onto an otherwise dark tile.
        backgroundColor = LightUrsColors.background,
        modifier = modifier
            .aspectRatio(1f)
            .then(if (hazardBorder) Modifier.border(HazardStripeWidth, hazardStripeBrush(), RoundedCornerShape(Radius.row)) else Modifier)
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                } else {
                    Modifier.clickable(onClick = onClick)
                },
            ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    TileImage(catalogImageId = catalogImageId, contentDescription = title, alpha = alpha)
                }
                UrsText(
                    text = title,
                    style = UrsTheme.typography.caption.copy(
                        textDecoration = if (titleStrikethrough) TextDecoration.LineThrough else TextDecoration.None,
                    ),
                    // Dark by default, matching the tile's always-light background above;
                    // muted grey instead when only the title (not the whole tile) is marked.
                    color = titleColor,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.s, vertical = Spacing.xs),
                )
            }

            if (quantityBadge != null) {
                UrsPill(
                    text = quantityBadge,
                    modifier = Modifier.align(Alignment.TopStart).padding(Spacing.xs),
                )
            }

            if (topEndBadge != null) {
                UrsPill(
                    text = topEndBadge,
                    modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.xs),
                )
            }
        }
    }
}

private val HazardStripeWidth = 5.dp
private val HazardStripePeriod = 12.dp

// Diagonal repeating yellow/black gradient used as a border brush — reads as
// hazard/caution tape. Uses absolute pixel offsets (not relative to the
// tile's own size) so the stripe width/angle stays consistent across every
// tile regardless of its measured size, tiling indefinitely via `Repeated`.
@Composable
private fun hazardStripeBrush(): Brush {
    val stripePx = with(LocalDensity.current) { HazardStripePeriod.toPx() }
    return Brush.linearGradient(
        colors = listOf(HazardStripeYellow, HazardStripeYellow, HazardStripeBlack, HazardStripeBlack),
        start = Offset.Zero,
        end = Offset(stripePx, stripePx),
        tileMode = TileMode.Repeated,
    )
}

// Fit (not Crop) so a non-square product photo is never cut off, shrunk a
// bit further via the padding below so it sits clearly inside the tile
// rather than touching its edges.
@Composable
private fun TileImage(catalogImageId: Int?, contentDescription: String?, alpha: Float) {
    val imageUrl = catalogImageUrl(catalogImageId)
    Box(
        modifier = Modifier.fillMaxSize().padding(Spacing.m),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl == null) {
            PlaceholderIcon(alpha = alpha)
            return@Box
        }

        val app = LocalContext.current.applicationContext as UrsApplication
        SubcomposeAsyncImage(
            model = imageUrl,
            imageLoader = app.container.imageLoader,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
            loading = { PlaceholderIcon(alpha = alpha) },
            error = { PlaceholderIcon(alpha = alpha) },
        )
    }
}

@Composable
private fun PlaceholderIcon(alpha: Float = 1f) {
    UrsIcon(
        imageVector = Icons.Filled.ShoppingBasket,
        contentDescription = null,
        tint = LightUrsColors.onSurfaceMuted.copy(alpha = alpha),
        modifier = Modifier.size(32.dp),
    )
}
