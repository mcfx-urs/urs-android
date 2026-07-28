package ch.mcfx.urs.data

import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

/** A fixed gram amount of one ingredient, rendered read-only — no ratio/quantity scaling for v1. */
data class IngredientAmount(val name: String, val grams: Int)

/**
 * One step of a [RecipeTemplate], fixed relative to the plan's single
 * anchor (bake/oven-start) time. [offsetFromAnchor] is negative for every
 * step before the anchor; the anchor step itself uses [Duration.ZERO].
 */
data class RecipeStepTemplate(val index: Int, val label: String, val offsetFromAnchor: Duration)

data class RecipeTemplate(
    val key: String,
    val name: String,
    val ingredients: List<IngredientAmount>,
    val steps: List<RecipeStepTemplate>,
)

/**
 * Computes this step's actual instant for a plan anchored at [anchor],
 * going through [ZonedDateTime] rather than raw [LocalDateTime] arithmetic
 * so a DST transition inside the (up to ~46h) planning window doesn't shift
 * the result — same pattern as `ReminderScheduler.armNext()`.
 */
fun RecipeStepTemplate.plannedAtMillis(anchor: LocalDateTime): Long =
    anchor.atZone(ZoneId.systemDefault()).plus(offsetFromAnchor).toInstant().toEpochMilli()

/**
 * Sourdough Bread, the only template seeded for this recipe type. Worked out in
 * full from a 55min bake at 230°C→200°C.
 * Step 7 (Benchrest) is its own checklist entry sharing step 8's timestamp
 * (anchor − 14h15min) — it fires as a "20min bench rest is up, start final
 * shaping" reminder rather than a "start resting" one, since the user
 * already knows to rest immediately after step 6 and gets no benefit from a
 * reminder for that; the useful moment to be notified is when the timer
 * runs out.
 */
val sourdoughBreadTemplate = RecipeTemplate(
    key = "sourdough_bread",
    name = "Sourdough Bread",
    ingredients = listOf(
        IngredientAmount("Aktiver Starter", 150),
        IngredientAmount("Wasser", 350),
        IngredientAmount("Brotmehl", 500),
        IngredientAmount("Salz", 10),
    ),
    steps = listOf(
        RecipeStepTemplate(1, "Starter füttern (3g Anstellgut + 83g Mehl + 83g Wasser)", Duration.ofMinutes(-(45 * 60 + 35).toLong())),
        RecipeStepTemplate(2, "Mischen (Starter + Wasser verrühren, dann Mehl)", Duration.ofMinutes(-(21 * 60 + 35).toLong())),
        RecipeStepTemplate(3, "Salzen + Stretch&Fold (Start Bulk Fermentation)", Duration.ofMinutes(-(20 * 60 + 35).toLong())),
        RecipeStepTemplate(4, "Coil Fold 2", Duration.ofMinutes(-(19 * 60 + 35).toLong())),
        RecipeStepTemplate(5, "Coil Fold 3", Duration.ofMinutes(-(18 * 60 + 35).toLong())),
        RecipeStepTemplate(6, "Teilen & Vorformen (Ende Bulk Fermentation)", Duration.ofMinutes(-(14 * 60 + 35).toLong())),
        RecipeStepTemplate(7, "Benchrest fertig (20min)", Duration.ofMinutes(-(14 * 60 + 15).toLong())),
        RecipeStepTemplate(8, "Final Shaping, ab in den Kühlschrank (Start Cold Retard)", Duration.ofMinutes(-(14 * 60 + 15).toLong())),
        RecipeStepTemplate(9, "Aus dem Kühlschrank", Duration.ofMinutes(-(1 * 60 + 15).toLong())),
        RecipeStepTemplate(10, "Ofen vorheizen (230°C)", Duration.ofMinutes(-45L)),
        RecipeStepTemplate(11, "Backen (230°C 20min Dampf → 200°C 35min)", Duration.ZERO),
    ),
)

val bakingTemplatesByKey: Map<String, RecipeTemplate> = listOf(sourdoughBreadTemplate).associateBy { it.key }
