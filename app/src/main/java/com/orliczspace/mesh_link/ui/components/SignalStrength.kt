package com.orliczspace.mesh_link.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun SignalStrength(
    percentage: Int
) {

    Row(
        verticalAlignment = Alignment.Bottom
    ) {

        repeat(5) { index ->

            val active = percentage > index * 20

            Box(
                modifier = Modifier
                    .padding(end = 3.dp)
                    .width(5.dp)
                    .height((10 + index * 6).dp)
                    .background(
                        if (active)
                            Color.Green
                        else
                            Color.Gray
                    )
            )

        }

    }

}