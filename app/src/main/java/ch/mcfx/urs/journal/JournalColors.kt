package ch.mcfx.urs.journal

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

private val LightContent = Color(0xFFFFFFFF)
internal val JournalErrorColor = Color(0xFFD64545)
private val DarkContent = Color(0xFF12160F)

/** Text/icon color with the better contrast on [background], independent of the app theme. */
fun readableOn(background: Color): Color = if (background.luminance() > 0.4f) DarkContent else LightContent
