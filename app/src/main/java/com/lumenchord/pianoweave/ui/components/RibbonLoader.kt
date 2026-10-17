package com.lumenchord.pianoweave.ui.components

import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.lumenchord.pianoweave.R

/** Pause between two shines, so the loader breathes instead of flickering. */
private const val PAUSE_BETWEEN_SHINES_MS = 500L

/**
 * The splash screen's ribbons with their shine, repeating while something loads. The ribbons fill
 * about 70% of [size], with their glow around them.
 */
@Composable
fun RibbonLoader(modifier: Modifier = Modifier, size: Dp = 160.dp) {
    AndroidView(
        modifier = modifier.size(size),
        factory = { context ->
            ImageView(context).apply {
                val shine = context.getDrawable(R.drawable.splash_icon) as AnimatedVectorDrawable
                setImageDrawable(shine)
                shine.registerAnimationCallback(object : android.graphics.drawable.Animatable2.AnimationCallback() {
                    override fun onAnimationEnd(drawable: Drawable?) {
                        postDelayed({ if (isAttachedToWindow) shine.start() }, PAUSE_BETWEEN_SHINES_MS)
                    }
                })
                shine.start()
            }
        },
        onRelease = { view -> (view.drawable as? AnimatedVectorDrawable)?.run { clearAnimationCallbacks(); stop() } }
    )
}
