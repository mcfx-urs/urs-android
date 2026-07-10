package ch.mcfx.urs.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.navigation.Destination

private val FEATURE_TILES = listOf(
    Destination.FUEL,
    Destination.INVENTORY,
    Destination.HEALTH,
    Destination.GOKART,
    Destination.PRICE_MONITOR,
    Destination.USERS,
)

@Composable
fun HomeScreen(
    onNavigate: (Destination) -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(FEATURE_TILES) { destination ->
            FeatureTile(
                destination = destination,
                subtitle = quickStat(destination, uiState),
                onClick = { onNavigate(destination) },
            )
        }
    }
}

// Only Fuel has a quick-stat today; other tiles simply show none until they
// have data worth surfacing here too.
@Composable
private fun quickStat(destination: Destination, state: HomeUiState): String? = when (destination) {
    Destination.FUEL -> state.fuelAvgConsumptionL100Km?.let {
        stringResource(R.string.fuel_avg_consumption_6mo, it)
    }
    else -> null
}

@Composable
private fun FeatureTile(destination: Destination, subtitle: String? = null, onClick: () -> Unit) {
    val containerColor = if (destination.isAvailable) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val contentColor = if (destination.isAvailable) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .clickable(enabled = destination.isAvailable, onClick = onClick)
            .padding(16.dp),
    ) {
        Icon(destination.icon, contentDescription = null, tint = contentColor)
        Text(
            text = stringResource(destination.labelRes),
            style = MaterialTheme.typography.titleMedium,
            color = contentColor,
        )
        if (!destination.isAvailable) {
            Text(
                text = stringResource(R.string.coming_soon),
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
            )
        } else if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
            )
        }
    }
}
