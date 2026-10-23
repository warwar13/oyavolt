# OyaVolt solar energy update

## Purpose and measurement boundary

OyaVolt should answer: how much energy did the monitored battery supply today, this month, this year, and since solar tracking began; how much energy entered the battery; and what is the supplied energy worth at the user's grid tariff? Preserve the existing Gentai read-only BLE protocol and battery-safety alerts.

The BMS reports net battery voltage and signed current. Positive current charges the battery; negative current discharges it. It cannot identify a solar charger, wall charger, inverter AC output, total PV generation, or direct solar-to-load power. In this first release, label positive charge as **assumed solar to battery** and negative discharge as **measured battery energy used**. Label money as **estimated savings at current rate**. If wall charging occurs, that estimate may be high. Never draw a simultaneous multi-source energy-flow diagram from this BMS alone.

## Home

Keep the SOC instrument, live absolute watts, voltage, remaining Ah, cell and temperature safety information. Show a truthful live state: `Charging battery (solar assumed)`, `Battery powering load`, `Idle`, or `Disconnected`. Put compact Today and This Month cards near the hero: battery kWh used, assumed solar kWh charged, estimated savings when a tariff is configured, and recording coverage. Show the timestamp of the latest valid reading and distinguish `No data` from measured zero use. Move monitoring settings to a dedicated Settings page reached from a Home gear icon. Keep Change Battery and Stop available.

## Analytics and ROI

An energy report offers Day, Month, Year, and All Time periods. Day uses hourly energy buckets, Month daily buckets, Year monthly buckets, and All Time yearly buckets. Show used and charged energy separately, savings estimate, coverage, and observed/missing time. Keep the existing battery-use cycle, load statistics, and health details as a separate selected-day section. The ROI section shows cumulative estimated savings, system cost, payback percentage, money remaining to break even, and net ROI percentage; it is unavailable until cost and tariff are entered.

## Settings and recording

Settings contain a fixed Philippine peso display, one provider value per kWh (default 13.00), solar system cost, optional rated battery storage in kWh, solar-tracking start date, and the existing opt-in reboot-resume control. Rated storage is informational and never substitutes for BMS energy measurements. Rate edits revalue historical estimates using the **current configured rate**, which the UI says explicitly. The start date defaults to the first launch of this update; earlier telemetry remains in History but is outside solar accounting. A user can change the start date to include earlier recordings. Imported capacity tests never count in solar energy or ROI.

Opening the visible app with a remembered BMS and granted BLE permission starts persistent Live view when Bluetooth is available. Live view does not create a typed energy recording; choose Charging or Discharging from Home to record. The existing connected-device foreground service owns BLE, keeps its notification, and reconnects after interruptions. Stop ends the current recording; Android or Poco may still interrupt the service; reports expose gaps and coverage rather than claiming uninterrupted operation.

## Implementation order

1. Add settings, explicit recorded/imported provenance, local-calendar energy rollups, migration, and gap-safe tests.
2. Start Live view automatically from the visible app; require an explicit Charging or Discharging choice for typed recording and add Home totals and Settings.
3. Add period reports, hourly/daily/monthly charts, and ROI. Keep raw samples as the source of truth.
4. Validate unit tests, Room migration, lint and APK build; then check permission, screen-off, Bluetooth toggle, process recovery, Stop/reopen, and notification behavior on the Poco.

Settings includes a confirmed **Reset recorded data** action for the wall-to-solar switch. It permanently deletes telemetry samples, completed recording sessions, and energy rollups, while keeping capacity tests and alerts. It restarts the solar-tracking clock at reset time. If monitoring is active, later samples continue as a new history. The bundled capacity-test CSV is not automatically reimported afterward, but manual capacity-test import remains available. No BMS commands are added. A later source-entry feature can mark wall charging, and a separate solar-controller or inverter integration can add real PV generation and AC-load measurements.

## Design references

- [BLUETTI app energy statistics](https://s4.bluettipower.com/bluetti/support/EP2000APPUserManualEN.pdf/1731321568450a979.pdf) informed the Day/Month/Year/All Time presentation, while this BMS cannot provide its separate PV/Grid/Load counters.
- [Victron's energy-measurement requirements](https://www.victronenergy.com/media/pg/VRM_Portal_manual/en/frequently-asked-questions.html) explain why battery-only measurements cannot establish complete solar production and consumption flows.
- [Android BLE background guidance](https://developer.android.com/develop/connectivity/bluetooth/ble/background) and [foreground-service start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start) inform visible-app startup and continued connected-device monitoring.
