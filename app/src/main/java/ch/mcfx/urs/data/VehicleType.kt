package ch.mcfx.urs.data

enum class VehicleType { CAR, MOTORBIKE, EBIKE }

fun VehicleType.toRaw(): String = name.lowercase()

fun vehicleTypeFromRaw(raw: String): VehicleType =
    VehicleType.entries.firstOrNull { it.toRaw() == raw } ?: VehicleType.CAR
