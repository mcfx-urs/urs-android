package ch.mcfx.urs.ui.icons

import ch.mcfx.urs.R

/**
 * One bundled Material Symbols vector drawable (`res/drawable/ic_*.xml`, each
 * sourced individually rather than pulled from `material-icons-extended`)
 * offered by [IconPickerSheet]. [id] is the stable token stored on an entity;
 * [keywords] together with [id] back the picker's substring search.
 */
data class IconCatalogEntry(
    val id: String,
    val drawable: Int,
    val keywords: List<String>,
)

/** The curated, searchable icon set shared across features. */
object IconCatalog {

    val entries: List<IconCatalogEntry> = listOf(
        e("home", R.drawable.ic_home, "house", "household"),
        e("shopping_cart", R.drawable.ic_shopping_cart, "shopping", "cart", "groceries", "trolley"),
        e("shopping_bag", R.drawable.ic_shopping_bag, "shopping", "bag", "store"),
        e("grocery", R.drawable.ic_grocery, "grocery", "groceries", "food", "supermarket"),
        e("kitchen", R.drawable.ic_kitchen, "kitchen", "fridge", "refrigerator"),
        e("restaurant", R.drawable.ic_restaurant, "food", "restaurant", "dining", "cutlery"),
        e("local_cafe", R.drawable.ic_local_cafe, "coffee", "cafe", "tea", "drink"),
        e("local_bar", R.drawable.ic_local_bar, "bar", "drinks", "cocktail", "alcohol"),
        e("bakery_dining", R.drawable.ic_bakery_dining, "bakery", "bread", "croissant", "pastry"),
        e("cleaning_services", R.drawable.ic_cleaning_services, "cleaning", "mop", "chores", "housework"),
        e("local_laundry_service", R.drawable.ic_local_laundry_service, "laundry", "washing", "washer"),
        e("iron", R.drawable.ic_iron, "iron", "ironing", "clothes"),
        e("bed", R.drawable.ic_bed, "bed", "bedroom", "sleep", "sheets"),
        e("weekend", R.drawable.ic_weekend, "sofa", "couch", "living room", "furniture"),
        e("bathtub", R.drawable.ic_bathtub, "bath", "bathroom", "tub"),
        e("checkroom", R.drawable.ic_checkroom, "clothes", "wardrobe", "closet", "hanger"),
        e("delete", R.drawable.ic_delete, "trash", "bin", "garbage", "waste"),
        e("build", R.drawable.ic_build, "tools", "wrench", "repair", "settings"),
        e("handyman", R.drawable.ic_handyman, "tools", "hammer", "wrench", "diy", "repair"),
        e("hardware", R.drawable.ic_hardware, "hammer", "tools", "nail"),
        e("plumbing", R.drawable.ic_plumbing, "plumbing", "pipe", "wrench", "water"),
        e("format_paint", R.drawable.ic_format_paint, "paint", "brush", "decorate", "roller"),
        e("grass", R.drawable.ic_grass, "lawn", "grass", "garden", "mow"),
        e("local_florist", R.drawable.ic_local_florist, "plant", "flower", "garden", "florist"),
        e("park", R.drawable.ic_park, "tree", "park", "nature", "outdoors"),
        e("water_drop", R.drawable.ic_water_drop, "water", "plants", "watering", "drop"),
        e("pets", R.drawable.ic_pets, "pet", "dog", "cat", "animal", "paw"),
        e("medication", R.drawable.ic_medication, "medicine", "pills", "pharmacy", "drugs"),
        e("vaccines", R.drawable.ic_vaccines, "vaccine", "injection", "syringe", "shot"),
        e("fitness_center", R.drawable.ic_fitness_center, "gym", "fitness", "workout", "weights"),
        e("directions_car", R.drawable.ic_directions_car, "car", "vehicle", "drive", "auto"),
        e("directions_bike", R.drawable.ic_directions_bike, "bike", "bicycle", "cycling"),
        e("local_gas_station", R.drawable.ic_local_gas_station, "fuel", "gas", "petrol", "station"),
        e("menu_book", R.drawable.ic_menu_book, "book", "recipe", "reading", "manual"),
        e("work", R.drawable.ic_work, "work", "office", "job", "briefcase"),
        e("payments", R.drawable.ic_payments, "money", "payment", "cash", "finance"),
        e("savings", R.drawable.ic_savings, "savings", "piggy bank", "money"),
        e("redeem", R.drawable.ic_redeem, "gift", "present", "voucher", "reward"),
        e("luggage", R.drawable.ic_luggage, "travel", "suitcase", "luggage", "trip"),
        e("celebration", R.drawable.ic_celebration, "party", "celebration", "event", "birthday"),
        e("star", R.drawable.ic_star, "star", "favorite", "important"),
        e("lightbulb", R.drawable.ic_lightbulb, "idea", "light", "bulb", "electricity"),
    )

    private val byId: Map<String, IconCatalogEntry> = entries.associateBy { it.id }

    fun entry(id: String?): IconCatalogEntry? = id?.let { byId[it] }

    fun drawableFor(id: String?): Int? = entry(id)?.drawable

    /** Case-insensitive substring match against id and keywords; a blank query returns everything. */
    fun search(query: String): List<IconCatalogEntry> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return entries
        return entries.filter { entry -> entry.id.contains(q) || entry.keywords.any { it.contains(q) } }
    }

    private fun e(id: String, drawable: Int, vararg keywords: String) =
        IconCatalogEntry(id, drawable, keywords.toList())
}
