package com.orliczspace.mesh_link.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun SignalStrength(
    strength: Int,
    modifier: Modifier = Modifier
) {
    val bars = 4

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {

        repeat(bars) { index ->

            val active = index < strength

            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height((8 + index * 5).dp)
                    .background(
                        if (active)
                            MaterialTheme.colorScheme.primary
                        else
                            Color.Gray.copy(alpha = 0.25f),
                        RoundedCornerShape(2.dp)
                    )
            )

        }
    }
}