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

/**
 * Monitors internet connectivity in real-time using ConnectivityManager.NetworkCallback.
 * Provides Compose-observable State objects for UI updates.
 */
class InternetMonitor(context: Context) {

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d("InternetMonitor", "onAvailable: Internet became available")
            updateNetworkStatus(network)
        }

        override fun onLost(network: Network) {
            Log.d("InternetMonitor", "onLost: Internet connection lost")
            updateCurrentStatus() // Re-check current state (usually no internet)
        }

        override fun onCapabilitiesChanged(
            network: Network,
            capabilities: NetworkCapabilities
        ) {
            Log.d("InternetMonitor", "onCapabilitiesChanged: Network capabilities updated")
            updateNetworkStatus(network, capabilities)
        }
    }

    // Private backing mutable states
    private val _isConnected: MutableState<Boolean> = mutableStateOf(false)
    private val _connectionType: MutableState<String> = mutableStateOf("Unknown")

    // Public read-only State for safe observation in Compose using `by`
    val isConnected: State<Boolean> = _isConnected
    val connectionType: State<String> = _connectionType

    init {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            connectivityManager.registerNetworkCallback(request, networkCallback)
            Log.d("InternetMonitor", "Network callback registered successfully")
        } catch (e: SecurityException) {
            Log.e("InternetMonitor", "Permission denied: ${e.message}")
        }

        // Initial check on startup
        updateCurrentStatus()
    }

    /**
     * Checks the current active network and updates state accordingly.
     * Used on init and when connection is lost.
     */
    private fun updateCurrentStatus() {
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }

        val hasInternet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val type = if (hasInternet) {
            when {
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Mobile Data"
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
                else -> "Unknown"
            }
        } else {
            "None" // Explicitly show no connection
        }

        mainScope.launch {
            Log.d("InternetMonitor", "Status Update → Connected: $hasInternet, Type: $type")
            _isConnected.value = hasInternet
            _connectionType.value = type
        }
    }

    /**
     * Updates status when a network becomes available or capabilities change.
     */
    private fun updateNetworkStatus(
        network: Network?,
        capabilities: NetworkCapabilities? = network?.let {
            connectivityManager.getNetworkCapabilities(it)
        }
    ) {
        if (capabilities == null) {
            // Shouldn't happen, but fallback
            updateCurrentStatus()
            return
        }

        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val type = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile Data"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Unknown"
        }

        mainScope.launch {
            Log.d("InternetMonitor", "Status Update → Connected: $hasInternet, Type: $type")
            _isConnected.value = hasInternet
            _connectionType.value = type
        }
    }

    /**
     * Call this in Activity.onDestroy() to prevent memory leaks.
     */
    fun close() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            Log.d("InternetMonitor", "Network callback unregistered")
        } catch (e: Exception) {
            Log.w("InternetMonitor", "Error unregistering callback: ${e.message}")
        }
        mainScope.cancel()
        Log.d("InternetMonitor", "Coroutine scope cancelled")
    }
}