package com.orliczspace.mesh_link.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun AnimatedMetricCard(

    title: String,

    value: Int,

    icon: ImageVector,

    color: Color,

    modifier: Modifier = Modifier,

    animationDelay: Long = 0L

) {

    var visible by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(Unit) {
        delay(animationDelay)
        visible = true
    }

    val animatedValue by animateIntAsState(
        targetValue = if (visible) value else 0,
        animationSpec = tween(1500),
        label = "counter"
    )

    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.85f,
        animationSpec = tween(800),
        label = "scale"
    )

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = tween(800)
        ) + slideInVertically(
            animationSpec = tween(800)
        )
    ) {

        Card(
            modifier = modifier
                .fillMaxWidth()
                .scale(scale),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 8.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent
            )
        ) {

            Box(

                modifier = Modifier
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xFF182848),
                                Color(0xFF4B6CB7)
                            )
                        )
                    )
                    .padding(20.dp)

            ) {

                Column(
                    modifier = Modifier.height(140.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Box(

                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    color.copy(alpha = 0.15f),
                                    RoundedCornerShape(14.dp)
                                ),

                            contentAlignment = Alignment.Center

                        ) {

                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = color
                            )

                        }

                        Spacer(Modifier.width(12.dp))

                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )

                    }

                    Text(
                        text = animatedValue.toString(),
                        style = MaterialTheme.typography.displaySmall,
                        color = Color.White
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color,
                                    RoundedCornerShape(50)
                                )
                        )

                        Spacer(Modifier.width(8.dp))

                        Text(
                            text = "Live",
                            color = color,
                            style = MaterialTheme.typography.labelLarge
                        )

                    }

                }

            }

        }

    }

}