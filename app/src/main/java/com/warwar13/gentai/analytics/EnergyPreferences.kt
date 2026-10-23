package com.warwar13.gentai.analytics

import android.content.Context
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class EnergySettings(
    val trackingStartMs: Long,
    val reportingZoneId: String,
    val currencyCode: String,
    val tariffPerKwh: Double?,
    val systemCost: Double?,
    val ratedStorageKwh: Double? = null,
) {
    val zone: ZoneId get() = ZoneId.of(reportingZoneId)
    fun savingsFor(usedWh: Double): Double? = tariffPerKwh?.let { usedWh / 1_000.0 * it }
    fun potentialSolarValueFor(chargedWh: Double): Double? = tariffPerKwh?.let { chargedWh / 1_000.0 * it }
    fun paybackPercent(usedWh: Double): Double? = systemCost?.takeIf { it > 0.0 }?.let { cost -> savingsFor(usedWh)?.div(cost)?.times(100.0) }
    fun netRoiPercent(usedWh: Double): Double? = paybackPercent(usedWh)?.minus(100.0)
}

class EnergyPreferences private constructor(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private val _settings: MutableStateFlow<EnergySettings>
    val settings: StateFlow<EnergySettings> get() = _settings
    val rebuildPending: Boolean get() = preferences.getBoolean(KEY_REBUILD_PENDING, false)
    val bundledCapacityImportDisabled: Boolean get() = preferences.getBoolean(KEY_DISABLE_BUNDLED_IMPORT, false)

    init {
        if (!preferences.contains(KEY_START)) {
            preferences.edit()
                .putLong(KEY_START, System.currentTimeMillis())
                .putString(KEY_ZONE, ZoneId.systemDefault().id)
                .apply()
        }
        _settings = MutableStateFlow(read())
    }

    private fun read() = EnergySettings(
        trackingStartMs = preferences.getLong(KEY_START, System.currentTimeMillis()),
        reportingZoneId = preferences.getString(KEY_ZONE, ZoneId.systemDefault().id) ?: ZoneId.systemDefault().id,
        currencyCode = "PHP",
        tariffPerKwh = preferences.getString(KEY_TARIFF, "13.0")?.toDoubleOrNull(),
        systemCost = preferences.getString(KEY_COST, null)?.toDoubleOrNull(),
        ratedStorageKwh = preferences.getString(KEY_STORAGE, null)?.toDoubleOrNull(),
    )

    @Synchronized
    fun save(tariffPerKwh: Double?, systemCost: Double?, startDate: LocalDate, ratedStorageKwh: Double?): EnergySettings {
        require(tariffPerKwh == null || (tariffPerKwh.isFinite() && tariffPerKwh > 0.0)) { "Grid price must be greater than zero" }
        require(systemCost == null || (systemCost.isFinite() && systemCost >= 0.0)) { "System cost cannot be negative" }
        require(ratedStorageKwh == null || (ratedStorageKwh.isFinite() && ratedStorageKwh > 0.0)) { "Rated storage must be greater than zero" }
        require(startDate <= LocalDate.now(_settings.value.zone)) { "Start date cannot be in the future" }
        val currentStartDate = java.time.Instant.ofEpochMilli(_settings.value.trackingStartMs).atZone(_settings.value.zone).toLocalDate()
        val start = if (startDate == currentStartDate) _settings.value.trackingStartMs else startDate.atStartOfDay(_settings.value.zone).toInstant().toEpochMilli()
        val startChanged = start != _settings.value.trackingStartMs
        preferences.edit()
            .putString(KEY_TARIFF, tariffPerKwh?.toString())
            .putString(KEY_COST, systemCost?.toString())
            .putString(KEY_STORAGE, ratedStorageKwh?.toString())
            .putLong(KEY_START, start)
            .putBoolean(KEY_REBUILD_PENDING, rebuildPending || startChanged)
            .apply()
        return read().also { _settings.value = it }
    }

    @Synchronized
    fun markRebuilt(startMs: Long) {
        if (_settings.value.trackingStartMs == startMs) preferences.edit().putBoolean(KEY_REBUILD_PENDING, false).apply()
    }

    @Synchronized
    fun resetAfterHistoryDeletion(nowMs: Long = System.currentTimeMillis()) {
        check(preferences.edit()
            .putLong(KEY_START, nowMs)
            .putBoolean(KEY_REBUILD_PENDING, false)
            .putBoolean(KEY_DISABLE_BUNDLED_IMPORT, true)
            .commit()) { "Could not save the reset marker" }
        _settings.value = read()
    }

    companion object {
        private const val FILE_NAME = "solar_energy_preferences"
        private const val KEY_START = "tracking_start_ms"
        private const val KEY_ZONE = "reporting_zone"
        private const val KEY_TARIFF = "grid_price_per_kwh"
        private const val KEY_COST = "system_cost"
        private const val KEY_STORAGE = "rated_storage_kwh"
        private const val KEY_REBUILD_PENDING = "rebuild_pending"
        private const val KEY_DISABLE_BUNDLED_IMPORT = "disable_bundled_capacity_import"
        @Volatile private var instance: EnergyPreferences? = null
        internal fun isolatedForTest(context: Context): EnergyPreferences = EnergyPreferences(context)
        fun get(context: Context): EnergyPreferences = instance ?: synchronized(this) {
            instance ?: EnergyPreferences(context).also { instance = it }
        }
    }
}
