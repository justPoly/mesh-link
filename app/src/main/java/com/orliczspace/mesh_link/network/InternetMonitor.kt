package com.orliczspace.mesh_link.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class InternetMonitor(context: Context) {

    companion object {
        private const val TAG = "InternetMonitor"
        private const val POLLING_INTERVAL_MS = 10_000L
    }

    private val connectivityManager =
        context.applicationContext.getSystemService(
            Context.CONNECTIVITY_SERVICE
        ) as ConnectivityManager

    private val mainScope =
        CoroutineScope(
            Dispatchers.Main + SupervisorJob()
        )

    private var pollingJob: kotlinx.coroutines.Job? = null

    private var callbackRegistered = false

    private val _isConnected = mutableStateOf(false)

    private val _connectionType =
        mutableStateOf("None")

    val isConnected: State<Boolean> = _isConnected

    val connectionType: State<String> = _connectionType

    @Volatile
    private var directInternetAvailable = false

    private val networkCallback =
        object : ConnectivityManager.NetworkCallback() {

            override fun onAvailable(network: Network) {
                updateCurrentStatus()
            }

            override fun onLost(network: Network) {
                updateCurrentStatus()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities
            ) {
                updateCurrentStatus()
            }
        }

    init {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(
                    NetworkCapabilities.NET_CAPABILITY_INTERNET
                )
                .addCapability(
                    NetworkCapabilities.NET_CAPABILITY_NOT_VPN
                )
                .build()

            connectivityManager.registerNetworkCallback(
                request,
                networkCallback
            )

            callbackRegistered = true

            Log.d(TAG, "Network callback registered")

        } catch (e: Exception) {
            Log.e(TAG, "Network callback registration failed", e)
        }

        updateCurrentStatus()

        pollingJob = mainScope.launch {
            while (isActive) {
                delay(POLLING_INTERVAL_MS)
                updateCurrentStatus()
            }
        }
    }

    private fun isValidatedPhysicalNetwork(
        capabilities: NetworkCapabilities
    ): Boolean {

        val hasInternet =
            capabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET
            )

        val isValidated =
            capabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_VALIDATED
            )

        val isVpn =
            capabilities.hasTransport(
                NetworkCapabilities.TRANSPORT_VPN
            )

        val isPhysicalNetwork =
            capabilities.hasTransport(
                NetworkCapabilities.TRANSPORT_WIFI
            ) ||
                    capabilities.hasTransport(
                        NetworkCapabilities.TRANSPORT_CELLULAR
                    ) ||
                    capabilities.hasTransport(
                        NetworkCapabilities.TRANSPORT_ETHERNET
                    )

        return hasInternet &&
                isValidated &&
                !isVpn &&
                isPhysicalNetwork
    }

    fun getDirectInternetNetwork(): Network? {

        return try {

            val activeNetwork =
                connectivityManager.activeNetwork

            val activeCapabilities =
                activeNetwork?.let {
                    connectivityManager.getNetworkCapabilities(it)
                }

            if (
                activeNetwork != null &&
                activeCapabilities != null &&
                isValidatedPhysicalNetwork(activeCapabilities)
            ) {
                return activeNetwork
            }

            connectivityManager.allNetworks.firstOrNull { network ->

                val capabilities =
                    connectivityManager.getNetworkCapabilities(network)

                capabilities != null &&
                        isValidatedPhysicalNetwork(capabilities)
            }

        } catch (e: Exception) {

            Log.e(TAG, "Failed to inspect networks", e)

            null
        }
    }

    fun hasDirectInternetAccess(): Boolean {
        return directInternetAvailable
    }

    private fun updateCurrentStatus() {

        mainScope.launch {

            val internetNetwork =
                getDirectInternetNetwork()

            val capabilities =
                internetNetwork?.let {
                    connectivityManager.getNetworkCapabilities(it)
                }

            val connected =
                capabilities != null &&
                        isValidatedPhysicalNetwork(capabilities)

            val type = when {

                !connected -> "None"

                capabilities?.hasTransport(
                    NetworkCapabilities.TRANSPORT_WIFI
                ) == true -> "Wi-Fi"

                capabilities?.hasTransport(
                    NetworkCapabilities.TRANSPORT_CELLULAR
                ) == true -> "Mobile Data"

                capabilities?.hasTransport(
                    NetworkCapabilities.TRANSPORT_ETHERNET
                ) == true -> "Ethernet"

                else -> "Unknown"
            }

            val previousConnected =
                _isConnected.value

            val previousType =
                _connectionType.value

            directInternetAvailable = connected

            _isConnected.value = connected
            _connectionType.value = type

            if (
                previousConnected != connected ||
                previousType != type
            ) {

                NetworkActivityLog.record(
                    message = if (connected) {
                        "Internet connected"
                    } else {
                        "Internet disconnected"
                    },
                    detail = type
                )
            }

            Log.d(
                TAG,
                "Direct Internet: $connected | Type: $type"
            )
        }
    }

    fun close() {

        if (callbackRegistered) {
            try {

                connectivityManager.unregisterNetworkCallback(
                    networkCallback
                )

            } catch (e: Exception) {

                Log.w(
                    TAG,
                    "Failed to unregister callback",
                    e
                )
            }

            callbackRegistered = false
        }

        pollingJob?.cancel()
        pollingJob = null

        directInternetAvailable = false

        _isConnected.value = false
        _connectionType.value = "None"

        mainScope.cancel()

        Log.d(TAG, "InternetMonitor stopped")
    }
}
