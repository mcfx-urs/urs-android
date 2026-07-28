package ch.mcfx.urs.baking

import android.net.Uri

// Mirrors ShoppingListRoutes' shape: the hub screen (active plans) is
// Destination.BAKING's own route, PLAN_DETAIL is a parameterized second
// route, HISTORY a third top-level-reachable one. Encoded since a plan's
// stand-in id could, in principle, collide with path-reserved characters.
object BakingRoutes {
    const val PLANS = "baking/plans"
    const val PLAN_DETAIL = "baking/plans/{planId}"
    const val HISTORY = "baking/history"

    fun planDetail(planId: String): String = "baking/plans/${Uri.encode(planId)}"
}
