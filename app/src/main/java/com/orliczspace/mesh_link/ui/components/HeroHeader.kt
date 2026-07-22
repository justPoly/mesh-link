package com.orliczspace.mesh_link.ui.components
import java.util.Calendar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun HeroHeader(

    modifier: Modifier = Modifier,

    username: String = "Polycarp",

    connected: Boolean = true

) {

    Surface(

        modifier = modifier.fillMaxWidth(),

        shape = RoundedCornerShape(24.dp),

        tonalElevation = 6.dp

    ) {

        Box(

            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF1B5E20),
                            Color(0xFF2E7D32),
                            Color(0xFF43A047)
                        )
                    )
                )
                .padding(24.dp)

        ) {

            Column {

                Text(

                    text = getGreeting(),

                    style = MaterialTheme.typography.bodyMedium,

                    color = Color.White.copy(alpha = 0.8f)

                )

                Spacer(Modifier.height(6.dp))

                Text(

                    text = username,

                    style = MaterialTheme.typography.headlineLarge,

                    color = Color.White

                )

                Spacer(Modifier.height(24.dp))

                Row(

                    verticalAlignment = Alignment.CenterVertically

                ) {

                    Icon(

                        imageVector = Icons.Default.Wifi,

                        contentDescription = null,

                        tint = Color.White

                    )

                    Spacer(Modifier.width(12.dp))

                    Column {

                        Text(

                            "Mesh Network",

                            color = Color.White

                        )

                        Text(

                            if (connected) "Connected"
                            else "Offline",

                            color = Color.White.copy(alpha = 0.8f)

                        )

                    }

                }

            }

        }

    }

}
fun getGreeting(): String {

    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

    return when {

        hour < 12 -> "Good Morning"

        hour < 17 -> "Good Afternoon"

        else -> "Good Evening"

    }

}