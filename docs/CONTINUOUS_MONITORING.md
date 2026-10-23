# Continuous battery monitoring and logging

Status: **implemented, with overnight reliability and battery-cost validation still pending**. The original design is retained below, with the screen-off reliability correction described here. It does not replace the read-only BLE protocol or the existing charging/discharging calculations.

## Screen-off reliability correction (2026-10-20)

Device inspection found the foreground service alive while raw readings had repeated multi-minute gaps. A coroutine `delay` does not wake a suspended CPU, and a foreground service alone does not keep the CPU awake. The Poco was also not exempt from Android battery optimization. A short USB-connected smoke test did not establish overnight reliability.

For this query-driven BMS, monitoring now owns a partial CPU wake lock while connecting, polling, and retrying. Each acquisition expires after two minutes; actual BLE-loop progress renews it after one minute by releasing the old acquisition first. There is no independent renewal timer: a stalled loop loses its lease. Explicit Stop, service destruction, cancellation, and monitor completion release it, and callbacks after Stop cannot reacquire it. Each monitor instance owns its own lease. The display stays off.

Bluetooth-off waiting releases the CPU lock and waits for a system Bluetooth state broadcast instead of checking every two seconds. Re-enabling Bluetooth resumes connection attempts. Settings shows the Android optimization exemption, links to the system controls, explains Poco Autostart/No restrictions, and shows the timestamp of the last successful database write separately from connection state.

The original recommendation against a wake lock is superseded for this measured screen-off failure. Keeping five-second query polling available prevents normal CPU suspension and can materially increase phone battery consumption. A bounded acquisition is a leak safeguard, not a claim of low power: healthy continuous monitoring renews it throughout the requested period. The 24-hour battery target below remains unmeasured. A future slower sampling mode would be an explicit user tradeoff, not a silent change.

Android battery optimization must be disabled for OyaVolt for its partial wake lock to work through Doze. On Poco, also allow Autostart and select No restrictions. These settings improve reliability but cannot guarantee uninterrupted recording; Force stop, shutdown, lost BLE, storage errors, and OEM process termination can still cause genuine gaps. Do not hide or remove the foreground notification. See [Android Doze guidance](https://developer.android.com/training/monitoring-device-state/doze-standby) and [wake-lock guidance](https://developer.android.com/develop/background-work/background-tasks/awake/wakelock/best-practices).

### Overnight acceptance check

1. In Settings enable Continuous monitoring; confirm a recent `Last saved to phone` timestamp, the persistent monitoring notification, and `Android battery optimization: exempt`. On Poco check Autostart and No restrictions in system settings. Leave Bluetooth on and the BMS in range.
2. Note phone charge and test start time, unplug USB/power, lock the display, and leave it for at least eight hours. Do not Force stop the app or use an app-cleaner action that stops monitoring.
3. Inspect History/Analytics coverage and the actual raw timestamps afterward. Check sample spacing and the largest unobserved interval, rather than trusting the switch or a cached SOC. A five-second wait follows BLE queries, so spacing is slightly longer than five seconds even in a healthy connection.
4. Compare phone charge loss with a comparable monitoring-off night. Report coverage and phone-power cost together. An on-USB forced-Doze test verifies a specific sleep condition; it does not replace this unplugged overnight check or prove the 24-hour battery target.

### Connected Poco results

- Screen off for 185 seconds: 35 saved observations, average interval 5.254 seconds, maximum 5.447 seconds, no gap markers. The CPU lease renewed beyond its initial two-minute timeout.
- Separate sleep-mode run: Android's event log confirmed 71.061 seconds in deep idle before a pre-existing alarm exited Doze. That verified interval contained 14 observations, maximum interval 5.359 seconds, no gaps. The whole 90-second run must not be described as sustained Doze.
- Bluetooth disabled: adapter OFF and CPU lock released. Bluetooth enabled again: lock reacquired, service foreground, recording resumed. Test interruptions remain genuine gaps.
- Android battery-optimization exemption verified; monitor left running and test settings restored. Poco manufacturer controls and an eight-hour unplugged test remain pending. The JSON reports alongside this document retain the timing evidence.

## Goal and decisions

Record useful BMS data throughout the day while the app is closed and the phone screen is locked. Preserve enough raw observations to inspect charging, discharging, idle periods, transitions, and outages later. A user-controlled switch in Settings determines whether the app should monitor. The screen does not need to stay on.

The agreed defaults are:

| Decision | Specification |
| --- | --- |
| Sampling | Target one valid battery-info reading about every **5 seconds** in charging, discharging, and idle. This is a target, not a guarantee when Android or BLE delays work. |
| Controls | Start and stop in **Settings only**. Home displays status and a Settings link. |
| Reboot | **Resume after phone restart** stays a separate opt-in switch. Both switches must be on to attempt boot resume. |
| Retention | Keep all raw readings until the user manually deletes them. Preserve existing exports and deletion tools. |
| Safety | Telemetry-only BMS queries. No configuration, switching, reset, calibration, heater, or balancing commands. |

“Phone off” here means **screen off/locked**. A powered-off phone cannot record. The app also cannot guarantee uninterrupted 24/7 collection if Android, Poco battery management, Bluetooth, or the BMS interrupts it. The product must show that lost coverage rather than make up measurements.

## Original implementation audit (before the first pass)

- `MonitorService` already owns a `connectedDevice` foreground service, visible notification, BLE connection, reconnect loop, and Room writer. `GentaiBleMonitor` polls battery info and running status each cycle, plus cell voltages every third cycle. The loop waits 5 seconds **after** its queries, so actual sample spacing can be longer.
- `AutomaticFlowRecorder` confirms a charging or discharging stretch with two readings beyond ±0.15 A. It currently discards idle and unconfirmed transition readings. Thus the phone still does much of the BLE work while idle but the database cannot show that the battery was observed during that time.
- A remembered device can start monitoring when the app opens even when persisted `requested` is false. A user pressing Stop can therefore find monitoring restarted on a later app open. The Settings page currently has only the reboot-resume checkbox; Home has the start/stop and device picker.
- Room already has timestamped raw telemetry, connection sessions, charging/discharging flow records, gap markers, and daily summaries. Existing analytics reject intervals over 30 seconds and marked disconnect boundaries. These are useful foundations, but coverage derived only from saved flow readings omits observed idle time.
- At a perfect 5-second cadence, logging all states produces **17,280 readings/day**, approximately **6.3 million/year**. The actual count will be lower when queries take time or connectivity is interrupted. Storage and History queries must be designed for this scale.

## Settings and visible state

### Monitoring switch

Add a prominent **Continuous monitoring** switch with a short explanation: “Record charging, discharging, and idle readings while the screen is off. A persistent notification appears while monitoring.” Its value represents the user's **desired state**, not merely whether a GATT connection happens to be up at this instant.

Turning it on should use the selected BMS if one exists. Otherwise, present the existing BLE device picker from Settings. Request the required Bluetooth permissions from the visible Settings screen. Do not mark setup complete or claim “Recording” until a service start has been accepted and valid samples are being saved. If the user cancels device selection or denies Bluetooth permission, leave the switch off and explain why. If a selected device is temporarily out of range, keep the switch on and show Reconnecting.

Turning it off must persist the off state first, cancel reconnection and polling, close the GATT connection and active flow/connection records, end the monitoring period, and remove the foreground notification. Reopening the app must not start it again. Device changes happen in Settings and start a new connection session; they must not create a false continuous energy interval between two devices.

Keep **Resume monitoring after phone restart** below the main switch. It remains opt-in. Its setting can stay stored while monitoring is off, but the boot receiver does nothing unless both the main switch and restart switch are on and a device has been selected. A service recreated by Android may resume a still-requested monitor; explicit Stop always wins. A visible app launch may reconcile an enabled request after an interruption, but never override an off request.

Remove Home's start/stop button and device picker. Home should show the actual state plus a direct Settings action. The status and switch must update together when the service starts, stops, reconnects, or reports an error; avoid separate Compose-only booleans that become stale after process death.

### Status wording

| State | Meaning shown to the user |
| --- | --- |
| Off | No monitoring requested; no new samples expected. |
| Connecting / Reconnecting | Monitoring requested; no valid current reading yet. Show the last saved reading time and retry state. |
| Recording · Charging / Discharging / Idle | A valid recent reading was saved; direction comes from signed current, not from whether a flow record exists. |
| Bluetooth off / Permission needed | Monitoring requested but the stated prerequisite blocks readings. |
| Recording error | The service cannot persist or continue safely; show the reason and a user-triggered retry, without a rapid restart loop. |

The notification remains an honest foreground-service indicator with low importance, an ongoing state, and a Stop action. The connected state shows only that battery monitoring is active; it does not display live SOC, current, or wattage. Rebuild it only when the rendered connection state changes, so a five-second telemetry sample does not cause notification churn. Keep the last-save time and data-gap warning in the app. On Android 13+, request notification permission and explain if it is denied; a foreground service can still run but Android may omit its notification from the drawer. Do not claim it is visible there when it is not.

Use the existing high-importance battery-warning channel for two local preparation reminders based on BMS SOC. While discharging, alert once when SOC reaches 25% or lower and once when it reaches 20% or lower. A reminder re-arms after SOC rises above its threshold by three percentage points (28% for the 25% reminder and 23% for the 20% reminder). Charging and idle readings do not trigger these reminders. BMS protection/error notifications remain independent and continue to be shown and recorded; no notification may send an inverter or BMS control command.

## Recording pipeline

1. Keep the existing protocol parser, checksum/frame handling, signed-current convention, and read-only command API. Before changing any query list, verify the telemetry allowlist against `reference-web/PROJECT_HANDOFF.md`, the web protocol source, and golden tests. No new command is needed for this feature.
2. For every **valid battery-info poll**, create one timestamped raw Room row regardless of charging state. Save pack voltage, signed current/power, SOC, SOH, capacities, cycles, available temperatures/cells/status, quality flags, connection-session ID, and a gap marker when applicable. Rejected frames are diagnostics, not fabricated telemetry rows.
3. Run flow classification using the saved row's ID. The existing two-reading confirmation still opens a charging or discharging record at the first qualifying reading. Link those confirmed rows to the record without inserting them twice. Idle and unconfirmed transition rows remain raw history with no flow-record ID. A sustained idle state closes the active flow record but does not stop raw logging.
4. Keep a single ordered writer for raw rows, flow links, and summary changes. A successful BLE callback is not “recorded” until the Room transaction succeeds. If persistence fails, surface Recording error, stop claiming fresh coverage, and avoid endless start/write/fail cycles.
5. Keep the live dashboard and BMS protection alerts driven by valid observations, including idle observations. An explicit capacity test can continue using its existing sample path; do not duplicate a capacity-test row into ordinary battery history unless the current design already requires it.

Optional cell and running-status responses arrive at different rates. Store their real age or mark stale/unavailable values through quality information; do not quietly label a cached optional value as measured at the new battery-info timestamp. RSSI remains optional where obtaining it is practical. Do not store full raw BLE packets or sensitive device addresses in diagnostic events.

## Coverage, gaps, and energy

Create a durable **monitoring period** for each continuous-on interval: ID, start time, optional end time, and end reason. An enabled period remains open through reconnects and process restarts; explicit Stop closes it. Historical data before this feature is preserved but must not be retroactively labeled as continuously monitored. Use connection sessions for individual GATT lifetimes and compact diagnostic events for Bluetooth off, disconnect, retry, permission loss, process recovery, and storage failure.

Within a requested period, valid consecutive samples can contribute observed time only when timestamps increase, the elapsed interval is at most the existing 30-second limit, and no disconnect or explicit gap boundary lies between them. Missing time starts when the last valid sample becomes overdue. Show an **ongoing gap** while the switch is on and no new valid sample arrives; close it at the next valid sample or when the user stops. After explicit Stop, elapsed time is not missing coverage. If the phone restarts without opt-in resume, show the unrecorded interval as unavailable when the app next opens, rather than silently implying it was measured.

Integrate charge/discharge Ah and Wh from actual elapsed time between eligible readings only. Preserve positive current as charging, negative as discharging, and the established ±0.15 A flow-record threshold. Idle samples can contribute observed coverage and their measured near-zero energy; they do not create a charging/discharging flow record. Never integrate across a disconnect, invalid sample, process interruption, different selected BMS, or a gap over the threshold. Do not infer energy from SOC movement across missing time.

Daily/session/solar reports must use the broader raw stream for observed coverage and preserve their existing signed-energy rules. A day with valid idle readings is **observed, near-zero energy**; a day without readings while monitoring was requested is **missing data**. Review local reporting-day boundaries, midnight/DST splits, live-day end at “now,” and chart gaps. Do not let the new idle rows double-count energy, create duplicate History records, or turn imported capacity-test data into ordinary monitoring coverage.

## Battery and storage budget

The current service already uses BLE while idle, so saving idle rows adds storage work but does not by itself introduce the five-second radio activity. The largest unknown is the real phone cost of keeping the BLE connection active and polling that often. The existing notification update on each connected-state emission, optional queries, and periodic summary rebuilding are also candidates for avoidable work. No percentage drain can be promised from source inspection alone.

Retain the selected five-second target initially. Reuse one GATT connection, avoid scanning while connected, avoid per-sample notification churn, and maintain daily/session summaries incrementally instead of repeatedly loading a growing day or all history. Bound and page History/chart queries so keeping years of raw rows does not slow app startup. Keep each raw reading durable rather than holding a large unsaved batch that could disappear on a process kill. Do not schedule five-second alarms. The bounded CPU lease and event-driven Bluetooth wait in the screen-off correction above replace the original no-wake-lock recommendation after device inspection showed real gaps while the foreground service was alive.

Measure on the Poco with comparable screen-off, unplugged periods: one with monitoring off and one with monitoring on, at similar starting charge, Bluetooth/environment, and network conditions. Record sample coverage, reconnects, app battery statistics, and whole-phone battery drain. The working target is **no more than five percentage points of additional phone battery use over 24 hours**, subject to reassessment after measuring the CPU wake-lock correction above. This is an acceptance target to measure, not a claim that the current or proposed app already meets it. If measured cost is too high, report it and offer an explicit sampling tradeoff; do not silently change five seconds to a slower rate.

## Android and Poco reliability

Use the existing `connectedDevice` foreground service as the live BLE owner. Keep its persistent notification. Do not make WorkManager the connection owner or schedule five-second background jobs. Begin a new user-requested service from the visible Settings screen. A boot receiver may attempt an opt-in restoration; catch and report a rejected start. Android background-start restrictions and OEM process management mean boot or process recovery can fail. [Android BLE background guidance](https://developer.android.com/develop/connectivity/bluetooth/ble/background), [foreground-service type rules](https://developer.android.com/develop/background-work/services/fgs/service-types), [background-start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start).

Keep bounded reconnect backoff and preserve an open requested period during an ordinary BLE outage. When Bluetooth is disabled or permission is removed, wait in a clearly blocked state rather than hammering GATT. On a service/process restart, close orphaned GATT/flow sessions at the last valid sample, restore the persisted requested state only if allowed, and mark the unobserved interval as a gap. Android **Force stop** and a fully powered-off phone cannot be bypassed; the next launch should reveal the interruption. Settings may explain Poco Autostart and battery restriction controls, but should never imply that they guarantee 24/7 uptime.

## Implementation sequence

1. Make the persisted monitoring request authoritative; move start/stop/device setup to Settings and remove Home controls and the app-open auto-start path that ignores `requested`.
2. Persist every valid reading once and link confirmed charge/discharge rows to flow records. Add monitoring-period and compact diagnostic storage with a tested Room migration; preserve existing data and exports.
3. Rework coverage, summaries, charts, and state presentation around raw idle readings and requested periods. Keep all energy calculations gap-safe.
4. Reduce unnecessary notification/summary work, then run the Poco screen-off and battery-use checks. Record measured results and remaining device-only uncertainties in `IMPLEMENTATION_STATUS.md`.

## Verification checklist

- **Unit tests:** off stays off after reopening; on restores only when permitted; boot resume needs both switches; repeated starts create one GATT owner; every valid idle/charge/discharge reading is saved once; two-reading confirmation links the correct rows; noisy one-reading transitions do not fabricate a flow record; explicit Stop ends the requested period.
- **Analytics tests:** zero or near-zero idle energy still counts as observed time; disconnects, rejected frames, 31-second gaps, process restarts, and device changes add no fabricated Wh/Ah; a short explicitly marked gap also blocks integration; ongoing gaps affect live coverage; midnight and DST totals remain correct.
- **Room migration tests:** pre-change samples, typed/legacy flow records, imports, and capacity tests survive; new periods begin only from actual enablement; querying long history remains bounded; export/deletion behavior still matches the user's selections.
- **Build checks:** `./gradlew.bat testDebugUnitTest --console=plain`, `./gradlew.bat lintDebug --console=plain`, and `./gradlew.bat assembleDebug --console=plain` from the command line.
- **Poco checks:** Settings enable/disable, permission denial, device selection, at least eight hours screen-off, idle-to-charge-to-discharge transitions, Bluetooth off/on, BMS out of range, notification visibility, process removal, reboot with opt-in on/off, and app reopen after explicit Stop. Inspect stored timestamps and coverage, not only Home's current value. Run a longer 24-hour comparison for battery cost and data yield.

Implementation is complete only when the app builds, relevant tests pass, the read-only BLE invariant remains intact, and device-only checks are reported as tested or still pending. The app must never display invented energy for time it did not observe.
