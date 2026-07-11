package com.orliczspace.mesh_link.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.orliczspace.mesh_link.ui.theme.MeshDimens

@Composable
fun MeshButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {

    Button(
        modifier = modifier
            .fillMaxWidth()
            .height(MeshDimens.ButtonHeight),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
        ),
        onClick = onClick
    ) {

        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium
        )

    }

}