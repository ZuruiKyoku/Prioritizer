package com.slygames.prioritizer.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Reports device network connectivity so the UI can show a subtle offline indicator (spec: don't
 * block editing while offline, just let the user know changes will sync later).
 */
@Singleton
class ConnectivityObserver @Inject constructor(@ApplicationContext private val context: Context) {

    val isOnline: Flow<Boolean> = callbackFlow {
        val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        fun hasInternet(network: Network): Boolean {
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }

        val callback =
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        trySend(hasInternet(network))
                    }

                    override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                        trySend(hasInternet(network))
                    }

                    override fun onLost(network: Network) {
                        trySend(false)
                    }
                }

        // Emit an initial snapshot immediately so the UI doesn't briefly assume "online".
        val active = connectivityManager.activeNetwork
        trySend(active != null && hasInternet(active))

        connectivityManager.registerNetworkCallback(NetworkRequest.Builder().build(), callback)
        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}
