package ch.mcfx.urs.data

/**
 * One user a container (inventory or list) has been shared with — the
 * common shape [ch.mcfx.urs.ui.components.UrsShareSheet] needs from either
 * [InventoryRepository]/[ShoppingListRepository], kept container-agnostic
 * here rather than reusing either repository's own DTO type directly, since
 * `inventory_share`/`list_share` are otherwise structurally identical
 * and this is UI-level reuse only — no shared backend abstraction
 * exists (or should exist) for the two.
 */
data class ShareEntry(val userId: String)
