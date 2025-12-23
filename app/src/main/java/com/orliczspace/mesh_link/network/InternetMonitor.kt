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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Monitors internet connectivity in real-time.
 * Provides Compose-observable State objects that update instantly
 * when the network changes, even while the app is running.
 */
class InternetMonitor(context: Context) {

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Optional fallback polling job for extra reliability on some devices
    private var pollingJob: kotlinx.coroutines.Job? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d("InternetMonitor", "onAvailable: Internet became available")
            updateNetworkStatus(network)
        }

        override fun onLost(network: Network) {
            Log.d("InternetMonitor", "onLost: Internet connection lost")
            updateCurrentStatus()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            capabilities: NetworkCapabilities
        ) {
            Log.d("InternetMonitor", "onCapabilitiesChanged: Capabilities changed")
            updateNetworkStatus(network, capabilities)
        }
    }

    // Private mutable backing states
    private val _isConnected: MutableState<Boolean> = mutableStateOf(false)
    private val _connectionType: MutableState<String> = mutableStateOf("Unknown")

    // Public read-only State objects for safe observation in Compose
    val isConnected: State<Boolean> = _isConnected
    val connectionType: State<String> = _connectionType

    init {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            connectivityManager.registerNetworkCallback(request, networkCallback)
            Log.d("InternetMonitor", "Network callback registered")
        } catch (e: SecurityException) {
            Log.e("InternetMonitor", "Permission denied: ${e.message}")
        }

        // Initial status check
        updateCurrentStatus()

        // Fallback polling every 10 seconds (very lightweight, ensures 100% reliability)
        pollingJob = mainScope.launch {
            while (true) {
                delay(10_000L) // 10 seconds
                Log.d("InternetMonitor", "Fallback poll checking current status")
                updateCurrentStatus()
            }
        }
    }

    /**
     * Checks the current active network (used on init, onLost, and fallback poll)
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
            "None"
        }

        mainScope.launch {
            Log.d("InternetMonitor", "Status Update → Connected: $hasInternet, Type: $type")
            _isConnected.value = hasInternet
            _connectionType.value = type
        }
    }

    /**
     * Called when a network becomes available or capabilities change
     */
    private fun updateNetworkStatus(
        network: Network?,
        capabilities: NetworkCapabilities? = network?.let {
            connectivityManager.getNetworkCapabilities(it)
        }
    ) {
        if (capabilities == null) {
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
     * Call this in Activity.onDestroy() to clean up resources and prevent leaks
     */
    fun close() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            Log.d("InternetMonitor", "Network callback unregistered")
        } catch (e: Exception) {
            Log.w("InternetMonitor", "Error unregistering callback: ${e.message}")
        }

        pollingJob?.cancel()
        pollingJob = null

        mainScope.cancel()
        Log.d("InternetMonitor", "Coroutine scope cancelled")
    }
}