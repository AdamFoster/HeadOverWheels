package net.adamfoster.headoverwheels.service

import android.content.Context

class RideStateStore(context: Context) {

    private val prefs = context.getSharedPreferences("ride_state", Context.MODE_PRIVATE)

    data class PendingRide(
        val distance: Double,
        val gain: Double,
        val loss: Double,
        val elapsedMs: Long
    )

    fun savePendingRide(distance: Double, gain: Double, loss: Double, elapsedMs: Long) {
        // SharedPreferences has no putDouble; Float precision (~7 sig figs) is sufficient for
        // ride distances (sub-metre accuracy at up to ~16,000 km) and elevation values.
        prefs.edit()
            .putBoolean("ride_pending", true)
            .putFloat("ride_distance", distance.toFloat())
            .putFloat("ride_elevation_gain", gain.toFloat())
            .putFloat("ride_elevation_loss", loss.toFloat())
            .putLong("ride_elapsed_offset", elapsedMs)
            .apply()
    }

    fun loadPendingRide(): PendingRide? {
        if (!prefs.getBoolean("ride_pending", false)) return null
        return PendingRide(
            distance = prefs.getFloat("ride_distance", 0f).toDouble(),
            gain = prefs.getFloat("ride_elevation_gain", 0f).toDouble(),
            loss = prefs.getFloat("ride_elevation_loss", 0f).toDouble(),
            elapsedMs = prefs.getLong("ride_elapsed_offset", 0L)
        )
    }

    fun clearPendingRide() {
        prefs.edit()
            .putBoolean("ride_pending", false)
            .remove("ride_distance")
            .remove("ride_elevation_gain")
            .remove("ride_elevation_loss")
            .remove("ride_elapsed_offset")
            .apply()
    }

    fun saveHrAddress(address: String?) {
        val editor = prefs.edit()
        if (address != null) editor.putString("sensor_hr_address", address)
        else editor.remove("sensor_hr_address")
        editor.apply()
    }

    fun loadHrAddress(): String? = prefs.getString("sensor_hr_address", null)

    fun saveRadarAddress(address: String?) {
        val editor = prefs.edit()
        if (address != null) editor.putString("sensor_radar_address", address)
        else editor.remove("sensor_radar_address")
        editor.apply()
    }

    fun loadRadarAddress(): String? = prefs.getString("sensor_radar_address", null)
}
