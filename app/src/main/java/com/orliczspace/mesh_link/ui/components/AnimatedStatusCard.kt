package com.orliczspace.mesh_link.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun AnimatedStatusCard(

    connected: Boolean,

    latency: String,

    internet: String

) {

    val transition = rememberInfiniteTransition(label = "pulse")

    val scale by transition.animateFloat(

        initialValue = 0.9f,

        targetValue = 1.2f,

        animationSpec = infiniteRepeatable(

            animation = tween(
                1200,
                easing = FastOutSlowInEasing
            ),

            repeatMode = RepeatMode.Reverse

        ),

        label = ""

    )

    val alpha by transition.animateFloat(

        initialValue = 0.4f,

        targetValue = 1f,

        animationSpec = infiniteRepeatable(

            animation = tween(1200),

            repeatMode = RepeatMode.Reverse

        ),

        label = ""

    )

    AnimatedVisibility(

        visible = true,

        enter = fadeIn()

    ) {

        Card(

            modifier = Modifier.fillMaxWidth(),

            shape = RoundedCornerShape(28.dp),

            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent
            )

        ) {

            Box(

                modifier = Modifier
                    .background(

                        Brush.linearGradient(

                            listOf(

                                Color(0xFF0F2027),

                                Color(0xFF203A43),

                                Color(0xFF2C5364)

                            )

                        )

                    )

                    .padding(24.dp)

            ) {

                Row(

                    verticalAlignment = Alignment.CenterVertically

                ) {

                    Box(

                        modifier = Modifier.size(60.dp),

                        contentAlignment = Alignment.Center

                    ) {

                        Box(

                            modifier = Modifier
                                .size(42.dp)
                                .scale(scale)
                                .alpha(alpha)
                                .background(

                                    Color.Green,

                                    CircleShape

                                )

                        )

                        Box(

                            modifier = Modifier
                                .size(22.dp)
                                .background(

                                    if (connected)
                                        Color.Green
                                    else
                                        Color.Red,

                                    CircleShape

                                )

                        )

                    }

                    Spacer(Modifier.width(18.dp))

                    Column(

                        modifier = Modifier.weight(1f)

                    ) {

                        Text(

                            "Mesh Status",

                            color = Color.White.copy(alpha = .8f),

                            style = MaterialTheme.typography.titleMedium

                        )

                        Spacer(Modifier.height(4.dp))

                        Text(

                            if (connected)
                                "Connected"
                            else
                                "Offline",

                            color = Color.White,

                            style = MaterialTheme.typography.headlineMedium

                        )

                        Spacer(Modifier.height(6.dp))

                        Text(

                            "$latency • $internet",

                            color = Color.White.copy(alpha = .7f)

                        )

                    }

                    Icon(

                        imageVector = Icons.Rounded.Wifi,

                        contentDescription = null,

                        tint = Color(0xFF4ADE80),

                        modifier = Modifier.size(42.dp)

                    )

                }

            }

        }

    }

}