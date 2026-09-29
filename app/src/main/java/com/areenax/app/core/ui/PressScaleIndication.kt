package com.areenax.app.core.ui

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.launch

/**
 * A8-02 — ONE shared press language for the whole app.
 *
 * The audit found two coexisting feedback languages: 77 scale-press targets
 * (pressScale + indication = null, the web idiom) vs ~40 M3 ripple targets
 * (Button, Surface(onClick), IconButton). Instead of migrating every call
 * site, the app now provides THIS indication via LocalIndication at the
 * AppShell root: every M3 pressable below it renders a scale-press instead of
 * a ripple — exactly the web behavior (`active:scale-95`, no ripple) — while
 * the existing pressScale sites are unchanged.
 *
 * Compose 1.7 IndicationNodeFactory: scale applied at draw time (no relayout),
 * same visual idiom as Modifier.pressScale. Runtime rendering NOT VERIFIED
 * (no device).
 */
object PressScaleIndication : IndicationNodeFactory {

    override fun create(interactionSource: InteractionSource): DelegatableNode =
        PressScaleNode(interactionSource)

    override fun equals(other: Any?): Boolean = other === PressScaleIndication
    override fun hashCode(): Int = System.identityHashCode(this)
}

private class PressScaleNode(
    private val interactionSource: InteractionSource,
) : Modifier.Node(), DrawModifierNode {

    private var pressedCount = 0

    override fun onAttach() {
        // `coroutineScope` is a member of Modifier.Node (compose-ui 1.7.6).
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> pressedCount++
                    is PressInteraction.Release, is PressInteraction.Cancel ->
                        pressedCount = (pressedCount - 1).coerceAtLeast(0)
                }
                invalidateDraw()
            }
        }
    }

    override fun ContentDrawScope.draw() {
        if (pressedCount > 0) {
            scale(0.96f, 0.96f, Offset(size.width / 2f, size.height / 2f)) {
                this@draw.drawContent()
            }
        } else {
            drawContent()
        }
    }
}
