package ch.mcfx.urs.chores

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bathtub
import androidx.compose.material.icons.filled.Bed
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Iron
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.LocalLaundryService

import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Plumbing
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Weekend
import androidx.compose.material.icons.filled.Window
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.mcfx.urs.ui.components.UrsIcon
import ch.mcfx.urs.ui.components.UrsText

/**
 * A tracker type's icon is stored as one string: `"material:<key>"` for one
 * of the curated Material icons below, or any other string treated as a
 * literal emoji. See GitHub issue #27.
 */
private const val MATERIAL_PREFIX = "material:"

// Curated, chores-flavoured subset — insertion order is the picker's order.
val choreMaterialIcons: Map<String, ImageVector> = linkedMapOf(
    "CleaningServices" to Icons.Filled.CleaningServices,
    "LocalLaundryService" to Icons.Filled.LocalLaundryService,
    "Iron" to Icons.Filled.Iron,
    "Bed" to Icons.Filled.Bed,
    "Bathtub" to Icons.Filled.Bathtub,
    "Window" to Icons.Filled.Window,
    "Kitchen" to Icons.Filled.Kitchen,
    "Restaurant" to Icons.Filled.Restaurant,
    "Coffee" to Icons.Filled.Coffee,
    "ShoppingCart" to Icons.Filled.ShoppingCart,
    "Delete" to Icons.Filled.Delete,
    "Grass" to Icons.Filled.Grass,
    "LocalFlorist" to Icons.Filled.LocalFlorist,
    "WaterDrop" to Icons.Filled.WaterDrop,
    "Pets" to Icons.Filled.Pets,
    "Medication" to Icons.Filled.Medication,
    "Vaccines" to Icons.Filled.Vaccines,
    "FitnessCenter" to Icons.Filled.FitnessCenter,
    "DirectionsRun" to Icons.AutoMirrored.Filled.DirectionsRun,
    "DirectionsCar" to Icons.Filled.DirectionsCar,
    "Build" to Icons.Filled.Build,
    "Handyman" to Icons.Filled.Handyman,
    "Plumbing" to Icons.Filled.Plumbing,
    "Air" to Icons.Filled.Air,
    "Checkroom" to Icons.Filled.Checkroom,
    "Weekend" to Icons.Filled.Weekend,
    "Brush" to Icons.Filled.Brush,
    "MenuBook" to Icons.AutoMirrored.Filled.MenuBook,
    "Payments" to Icons.Filled.Payments,
)

/** A small curated emoji grid; the picker also accepts a freely typed one. */
val choreEmojiChoices: List<String> = listOf(
    "🛏️", "🧺", "🧼", "🧹", "🧽", "🚿", "🪣", "🪟", "🍽️", "🍳",
    "☕", "🛒", "🗑️", "♻️", "🪴", "💧", "🐕", "🐈", "💊", "💉",
    "🏋️", "🏃", "🚗", "🔧", "🔨", "🪛", "🧯", "👕", "🛋️", "🎨",
    "📚", "💸", "📅", "✅", "🔁", "⭐", "🧴", "🌿", "🔥", "🧊",
)

fun trackerIconToken(materialKey: String): String = MATERIAL_PREFIX + materialKey

fun trackerIconMaterialVector(token: String): ImageVector? =
    token.takeIf { it.startsWith(MATERIAL_PREFIX) }
        ?.removePrefix(MATERIAL_PREFIX)
        ?.let { choreMaterialIcons[it] }

/** Renders a type's icon — a Material vector or a literal emoji. */
@Composable
fun ChoreIconView(token: String, tint: Color, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    val vector = trackerIconMaterialVector(token)
    if (vector != null) {
        UrsIcon(imageVector = vector, contentDescription = null, tint = tint, modifier = modifier.size(size))
    } else {
        UrsText(text = token, style = TextStyle(fontSize = (size.value * 0.95f).sp), modifier = modifier)
    }
}

// --- Colours ---

val choreColorPalette: List<String> = listOf(
    "#E53935", "#FB8C00", "#FDD835", "#43A047", "#00ACC1",
    "#1E88E5", "#5E35B1", "#8E24AA", "#6D4C41", "#546E7A",
)

fun parseChoreColor(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color(0xFF9E9E9E))

val defaultChoreColor: String = choreColorPalette.first()
val defaultChoreIcon: String = trackerIconToken(choreMaterialIcons.keys.first())
