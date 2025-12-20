package com.orliczspace.mesh_link.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class InternetMonitor(context: Context) {

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            updateNetworkStatus(network)
        }

        override fun onLost(network: Network) {
            updateCurrentStatus()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            capabilities: NetworkCapabilities
        ) {
            updateNetworkStatus(network, capabilities)
        }
    }

    // Private mutable states
    private val _isConnected: MutableState<Boolean> = mutableStateOf(false)
    private val _connectionType: MutableState<String> = mutableStateOf("Unknown")

    // Public read-only State for Compose observation with `by` delegation
    val isConnected: State<Boolean> = _isConnected
    val connectionType: State<String> = _connectionType

    init {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            connectivityManager.registerNetworkCallback(request, networkCallback)
        } catch (e: SecurityException) {
            Log.e("MeshLink", "Permission denied for network callback: ${e.message}")
        }

        updateCurrentStatus()
    }

    private fun updateCurrentStatus() {
        try {
            val network = connectivityManager.activeNetwork
            updateNetworkStatus(network)
        } catch (e: SecurityException) {
            mainScope.launch {
                _isConnected.value = false
                _connectionType.value = "Unknown"
            }
        }
    }

    private fun updateNetworkStatus(
        network: Network?,
        capabilities: NetworkCapabilities? = network?.let {
            connectivityManager.getNetworkCapabilities(it)
        }
    ) {
        val hasInternet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val type = when {
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Mobile Data"
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
            else -> "None"
        }

        mainScope.launch {
            _isConnected.value = hasInternet
            _connectionType.value = type
        }
    }

    /** Clean up resources when no longer needed */
    fun close() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            // Safe to ignore
        }
        mainScope.cancel()
    }
}