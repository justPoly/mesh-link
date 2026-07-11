package com.orliczspace.mesh_link.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.orliczspace.mesh_link.ui.theme.MeshDimens

@Composable
fun MeshCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(MeshDimens.Medium),
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = MeshDimens.CardElevation
        )
    ) {
        Column(
            modifier = Modifier.padding(padding)
        ) {
            content()
        }
    }
}