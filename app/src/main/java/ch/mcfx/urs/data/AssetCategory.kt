package ch.mcfx.urs.data

// Fixed, server-validated starter set (mcfx-urs/urs-backend#10) — not a
// DB-backed lookup table on either side; custom tags cover anything not
// fitting yet. Mirrors VehicleType's toRaw/fromRaw shape.
enum class AssetCategory {
    ELEKTRONIK, FAHRRAD, WERKZEUG, MOEBEL, HAUSHALT, HOBBY, SCHMUCK, SONSTIGES
}

fun AssetCategory.toRaw(): String = when (this) {
    AssetCategory.ELEKTRONIK -> "Elektronik"
    AssetCategory.FAHRRAD -> "Fahrrad"
    AssetCategory.WERKZEUG -> "Werkzeug"
    AssetCategory.MOEBEL -> "Möbel"
    AssetCategory.HAUSHALT -> "Haushalt"
    AssetCategory.HOBBY -> "Hobby"
    AssetCategory.SCHMUCK -> "Schmuck"
    AssetCategory.SONSTIGES -> "Sonstiges"
}

fun assetCategoryFromRaw(raw: String): AssetCategory =
    AssetCategory.entries.firstOrNull { it.toRaw() == raw } ?: AssetCategory.SONSTIGES
