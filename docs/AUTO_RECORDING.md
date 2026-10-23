# Automatic directional recording

## Goal and user experience

While OyaVolt is monitoring the selected battery, it automatically saves sustained charging and discharging. No direction picker or separate unsaved Live View is needed. The Home battery readings remain live during idle, but idle telemetry is not stored. History presents a separate record and Watts/Amps/SOC graph for each active stretch. Stop monitoring remains an explicit user action; a temporary BLE loss triggers bounded reconnect attempts and a new record once active flow returns.

Opening the app with Bluetooth permission and a remembered device starts monitoring as before. The foreground service and visible notification continue while the screen is off. Optional boot resume uses the same automatic policy. Android or Xiaomi may still stop the process; recorded gaps make interruptions visible, and no unavailable energy is estimated.

## Rules

| Signed current | Classification | Persistence |
| --- | --- | --- |
| `> +0.15 A` | Charging | Save after two consecutive qualifying readings. |
| `< -0.15 A` | Discharging | Save after two consecutive qualifying readings. |
| `−0.15 A` through `+0.15 A` | Idle | Live display only; end the active record after two idle readings. |

The first of two confirming active readings is retained, so the 5-second confirmation does not discard the start of a valid stretch. A single noisy or opposite-direction reading is not saved. If the prior direction resumes, the next saved reading carries a gap marker, so calculations never bridge the omitted reading. A confirmed direction change ends the previous record at its last saved sample and begins a new one at the first confirmed sample of the new direction.

Disconnect immediately ends the active record at its last saved sample. Reconnect does not resume that record: it can create a new one after two active readings. A process restart closes any orphaned open record at its last saved sample. Records with fewer than two saved samples are discarded because they have no reliable energy interval. Existing historical and imported records are preserved.

## Data, analytics, and UI

- Reuse `flow_recordings`, `telemetry_samples.recordingId`, and `gapFromPreviousMs`; no database migration is required. Each saved row belongs to one direction. Connection-session metadata may exist while idle, but idle telemetry rows do not.
- Mark the first sample after idle, a direction switch, or reconnect as a boundary. Flow, daily, directional, battery-usage, and solar calculations must honor the marker even when the elapsed time is below the ordinary 30-second gap threshold. The user sees only measured Wh/Ah; peso figures continue to apply the configured tariff to measured energy.
- Refresh an open record's summary when it starts and about once a minute while it remains active. Its graph samples update live in History. Closed summaries are final.
- Home shows the actual save state: `Saving · Charging`, `Saving · Discharging`, or `Idle · Not saving`. Connecting and reconnecting keep their existing states. History foregrounds automatic directional records and retains legacy mixed recordings separately without duplicating the new connection sessions.
- Alerts and explicit capacity tests continue to observe valid live readings even during idle. The BLE protocol remains read-only. Signed battery current cannot prove whether charging energy came from solar or a wall charger; the charging value is an estimate based on measured battery-side energy, not verified solar generation.

## Verification

Unit tests cover two-reading confirmation, idle suppression, one-reading noise, direction changes, disconnect/reconnect, and short marked gaps in energy and charts. Run `gradlew.bat testDebugUnitTest`, `lintDebug`, and `assembleDebug`. On the Poco, verify app-open auto-connect, screen-off recording, idle with no new stored rows, a charge/discharge transition, BLE-off/reconnect splitting, and the persistent notification. Physical-device behavior remains unverified until those checks are performed.
