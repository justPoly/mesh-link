package com.orliczspace.mesh_link.network

import android.content.Context
import java.util.UUID

/**
 * Generates and stores a stable identity for this Mesh-link installation.
 *
 * The same ID is returned every time the app requests its node identity.
 */
object NodeIdentity {

    private const val PREFS_NAME = "mesh_link_identity"
    private const val KEY_NODE_ID = "persistent_node_id"

    @Volatile
    private var cachedNodeId: String? = null

    fun getOrCreate(context: Context): String {
        cachedNodeId?.let { return it }

        synchronized(this) {
            cachedNodeId?.let { return it }

            val preferences = context.applicationContext
                .getSharedPreferences(
                    PREFS_NAME,
                    Context.MODE_PRIVATE
                )

            val savedNodeId = preferences.getString(KEY_NODE_ID, null)

            if (!savedNodeId.isNullOrBlank()) {
                cachedNodeId = savedNodeId
                return savedNodeId
            }

            val newNodeId = UUID.randomUUID().toString()

            // Persist before returning so a failed save is not silently ignored.
            val savedSuccessfully = preferences.edit()
                .putString(KEY_NODE_ID, newNodeId)
                .commit()

            check(savedSuccessfully) {
                "Mesh-link could not save the persistent Node ID."
            }

            cachedNodeId = newNodeId
            return newNodeId
        }
    }
}
