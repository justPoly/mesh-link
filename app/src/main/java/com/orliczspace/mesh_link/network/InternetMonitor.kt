package com.orliczspace.mesh_link.network

import android.content.Context
import android.net.*
import androidx.compose.runtime.mutableStateOf

class InternetMonitor(context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    val isConnected = mutableStateOf(false)
    val connectionType = mutableStateOf("Unknown")

    init {
        observeNetworkChanges()
        updateCurrentStatus()
    }

    private fun observeNetworkChanges() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(
            request,
            object : ConnectivityManager.NetworkCallback() {

                override fun onAvailable(network: Network) {
                    updateCurrentStatus()
                }

                override fun onLost(network: Network) {
                    updateCurrentStatus()
                }
            }
        )
    }

    private fun updateCurrentStatus() {
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)

        isConnected.value =
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        connectionType.value = when {
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Mobile Data"
            else -> "None"
        }
    }
}
