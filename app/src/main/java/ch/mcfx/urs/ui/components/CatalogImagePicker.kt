package ch.mcfx.urs.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ch.mcfx.urs.R
import ch.mcfx.urs.data.CatalogImagePage
import ch.mcfx.urs.data.local.CatalogProductEntity
import ch.mcfx.urs.data.remote.CatalogImageDto
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Radius
import ch.mcfx.urs.ui.tokens.Spacing
import kotlinx.coroutines.delay

/**
 * Image-reuse picker — the existing [CatalogImageDto]s as a tile grid, same
 * shape as a product grid ([UrsSquareTile] in a 3-column [LazyVerticalGrid])
 * so picking a photo to reuse feels like the rest of the catalog UI rather
 * than a bespoke gallery widget. No upload here — this only ever offers
 * images already seeded on the backend's image volume.
 *
 * The images are never loaded as a whole: [loadPage] fetches one page for the
 * current search text (server-side match over each image's keywords), the
 * search field is debounced, and the next page is requested when the user
 * scrolls close to the end of what is loaded. A blank search lists everything,
 * page by page.
 *
 * [initialQuery] seeds the search field instead of leaving it blank — used by
 * the barcode-scan quick-create flows to open this picker pre-filtered to the
 * resolved product name, surfacing same-name existing images as one-tap
 * suggestions instead of only "browse everything". Still a plain, editable
 * search field afterward — no separate "suggestions" mode.
 */
@Composable
fun CatalogImagePicker(
    loadPage: suspend (query: String, offset: Int) -> CatalogImagePage?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    initialQuery: String = "",
) {
    var query by remember { mutableStateOf(initialQuery) }
    var images by remember { mutableStateOf<List<CatalogImageDto>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    val gridState = rememberLazyGridState()

    LaunchedEffect(query) {
        loading = true
        try {
            if (images.isNotEmpty() || query != initialQuery) delay(SEARCH_DEBOUNCE_MS)
            val page = loadPage(query, 0)
            images = page?.items.orEmpty()
            total = page?.total ?: 0
        } finally {
            loading = false
        }
    }

    val nearEnd by remember {
        derivedStateOf {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            images.isNotEmpty() && images.size < total && lastVisible >= images.size - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(nearEnd, images.size) {
        if (!nearEnd || loading) return@LaunchedEffect
        loading = true
        try {
            val page = loadPage(query, images.size)
            if (page != null) {
                images = (images + page.items).distinctBy { it.id }
                total = page.total
            }
        } finally {
            loading = false
        }
    }

    Column(modifier = modifier) {
        UrsTextField(
            value = query,
            onValueChange = { query = it },
            label = stringResource(R.string.product_management_image_picker_search_label),
            modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s),
        )

        if (images.isEmpty()) {
            if (!loading) {
                UrsText(
                    text = stringResource(
                        if (query.isBlank()) R.string.product_management_image_picker_empty
                        else R.string.product_management_image_picker_no_matches,
                    ),
                    color = UrsTheme.colors.onSurfaceMuted,
                    modifier = Modifier.padding(Spacing.l),
                )
            }
            return@Column
        }

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            items(images, key = { it.id }) { image ->
                UrsSquareTile(
                    title = "",
                    catalogImageId = image.id.toIntOrNull(),
                    onClick = { onSelect(image.id) },
                )
            }
        }
    }
}

private val ResultsMaxHeight = 320.dp
private const val SEARCH_DEBOUNCE_MS = 300L
private const val LOAD_MORE_THRESHOLD = 6

/**
 * Existing-product picker — a live, search-driven result list (same search
 * flow Inventory's own product-search step uses, `catalogRepository.search`
 * via the caller's ViewModel — this composable is stateless, unlike
 * [CatalogImagePicker]'s own client-side filter over a preloaded list, since
 * the full catalog isn't preloaded here). Used by the barcode quick-create
 * flow (mcfx-urs/urs-android#113) to let picking one existing product
 * inherit its category and image together, instead of only an image via
 * [CatalogImagePicker]. A plain (non-lazy) list — result counts here are
 * small, name-matched suggestions, not a full catalog browse.
 */
@Composable
fun CatalogProductPicker(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<CatalogProductEntity>,
    onSelect: (CatalogProductEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        UrsTextField(
            value = query,
            onValueChange = onQueryChange,
            label = stringResource(R.string.product_management_image_picker_search_label),
            modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s),
        )

        if (query.isBlank()) return@Column

        if (results.isEmpty()) {
            UrsText(
                text = stringResource(R.string.product_existing_picker_no_matches),
                color = UrsTheme.colors.onSurfaceMuted,
                modifier = Modifier.padding(horizontal = Spacing.l),
            )
            return@Column
        }

        // Scrollable within a bounded height — a plain, unbounded column here would
        // leave a touch drag on the results to the surrounding bottom sheet, moving
        // the sheet instead of the list.
        Column(
            modifier = Modifier
                .heightIn(max = ResultsMaxHeight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            results.forEach { product ->
                UrsCard(
                    radius = Radius.row,
                    modifier = Modifier.fillMaxWidth().clickable(onClick = { onSelect(product) }),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                        CatalogImageThumb(catalogImageId = product.catalogImageId, modifier = Modifier.size(48.dp))
                        UrsText(product.name, style = UrsTheme.typography.body)
                    }
                }
            }
        }
    }
}
