package ch.mcfx.urs.data.local

/**
 * Shared stand-in-id scheme for every offline-first entity that references
 * another offline-first row before it's synced (e.g.
 * [InventoryProductEntity.categoryId], [ListItemEntity.listId]/[ListItemEntity
 * .productId]): the real backend id once known, otherwise this prefix plus
 * the referenced row's stable local [Long] id. Centralized here so the
 * convention can't drift between the several entities that each need their
 * own `publicId`/`localXxxId` pair built on top of it.
 */
internal const val LOCAL_ID_PREFIX = "local-"

internal fun localIdStandIn(id: Long): String = "$LOCAL_ID_PREFIX$id"

internal fun parseLocalIdStandIn(value: String): Long? =
    value.takeIf { it.startsWith(LOCAL_ID_PREFIX) }?.removePrefix(LOCAL_ID_PREFIX)?.toLongOrNull()
