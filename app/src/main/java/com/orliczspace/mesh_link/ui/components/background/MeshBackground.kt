package com.orliczspace.mesh_link.ui.components.background

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun MeshBackground() {

    Canvas(
        modifier = Modifier.fillMaxSize()
    ) {

        drawRect(
            color = Color(0xFF07111F)
        )

    }

}