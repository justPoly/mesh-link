package com.orliczspace.mesh_link.ui.components.effects

import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun rememberShimmerBrush(): Brush {

    val transition = rememberInfiniteTransition(label = "shimmer")

    val x by transition.animateFloat(

        initialValue = -600f,

        targetValue = 1200f,

        animationSpec = infiniteRepeatable(

            animation = tween(
                durationMillis = 2200,
                easing = LinearEasing
            )

        ),

        label = ""

    )

    return Brush.linearGradient(

        colors = listOf(

            Color.Transparent,

            Color.White.copy(alpha = 0.08f),

            Color.Transparent

        ),

        start = Offset(x, 0f),

        end = Offset(x + 250f, 250f)

    )

}