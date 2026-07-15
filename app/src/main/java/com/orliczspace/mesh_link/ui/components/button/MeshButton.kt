package com.orliczspace.mesh_link.ui.components.button

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MeshButton(

    text: String,

    onClick: () -> Unit

) {

    Button(

        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),

        shape = MaterialTheme.shapes.large,

        colors = ButtonDefaults.buttonColors(),

        onClick = onClick

    ) {

        Text(text)

    }

}