# AGENTS.md — Gentai Android

## Mission

Build a **personal, native Android battery telemetry/logger app** for a Poco X3 NFC. It replaces the old Bluefy/iPad browser workflow with reliable native BLE monitoring that can continue while the screen is off.

The existing web project is the protocol/behavior reference:
- local clone: `reference-web/` when present
- upstream: `https://github.com/warwar13/gentai`

Do not redesign the battery protocol from guesses. Inspect the existing handoff, protocol implementation, parser, storage behavior, and tests first.

## Token-efficiency rules

The user has limited Codex usage. Optimize for completed work per token.

1. Read only the smallest relevant files/sections. Do not dump entire files into chat unless required.
2. Do not repeat the plan after it has been agreed.
3. Keep progress responses to a few lines: changed, tested, next blocker.
4. Prefer making/running changes over writing long explanations.
5. Never rewrite working modules just for style.
6. Batch closely related edits, then run the narrowest useful test/build.
7. Use local source/code/tests before searching the web.
8. Do not ask for confirmation for normal implementation choices already covered here.
9. If a build fails, inspect the actual error and fix it; do not speculate at length.
10. Maintain a short `IMPLEMENTATION_STATUS.md` so a new Codex session can resume without rereading the entire history.

## Model / reasoning policy

The user is on ChatGPT Plus and wants to conserve Codex usage while still getting high-quality implementation.

Use this policy **only when the current Codex environment supports choosing or delegating models/effort for the task or subagent**. `AGENTS.md` is guidance, not a guarantee that the runtime can switch the parent model automatically.

### Default routing

- **Architecture / planning / difficult design decisions:** GPT-5.6 Sol, Medium reasoning.
- **Normal Kotlin/Android implementation:** GPT-5.6 Terra, Medium reasoning.
- **Code review / correctness review / security or BLE-safety review:** GPT-5.6 Sol, Medium reasoning.
- **Difficult bug investigation, BLE lifecycle races, foreground-service/background restrictions, protocol corruption, concurrency, or repeated failed fixes:** GPT-5.6 Sol, High reasoning.
- **Routine Gradle fixes, straightforward refactors, Compose layout changes, Room boilerplate, test additions, documentation, formatting, and repetitive checks:** GPT-5.6 Luna, Low or Medium reasoning.
- **Simple verification/build/test execution:** use the cheapest available capable model; do not escalate merely because a test failed once.

### Escalation rules

1. Start with the least expensive model/effort that is appropriate.
2. Do not use High reasoning for routine work.
3. Do not use Sol High for the whole project.
4. Escalate from Terra Medium to Sol High only after:
   - a genuinely difficult problem is identified, or
   - two evidence-based fix attempts fail, or
   - the issue risks corrupting telemetry, violating BLE safety, or breaking background reliability.
5. After the difficult issue is solved, return to Terra Medium or Luna for routine work.
6. Do not spawn extra agents just to "double check" ordinary code. Use a review agent only for meaningful milestones or risky changes.
7. When a dedicated review is warranted, prefer **Sol Medium** and ask it to inspect the diff/tests rather than reread the entire repository.
8. Keep review output concise: blocking issue, important issue, optional improvement. Avoid stylistic nitpicks unless they affect maintenance or correctness.

### Recommended milestone routing

- M1 protocol port: Terra Medium; final protocol/safety review: Sol Medium.
- M2 foreground BLE monitor: Terra Medium; lifecycle/background review: Sol Medium; hard race bugs: Sol High.
- M3 Room persistence: Terra Medium; routine DAO/tests can use Luna Medium.
- M4 Wh/Ah analytics: Terra Medium; calculation/correctness review: Sol Medium.
- M5 dashboard/history UI: Luna Medium or Terra Medium if state handling becomes complex.
- M6 capacity test: Terra Medium; calculation/gap-semantics review: Sol Medium.
- M7 export/backup: Luna Medium.
- M8 Poco reliability: Terra Medium; stubborn OEM/background issue: Sol High.

### Parent-model fallback

If this Codex session cannot switch/delegate models per task, stay on the user's selected parent model and follow the same **reasoning-effort philosophy**:
- routine work: Low/Medium;
- normal implementation: Medium;
- difficult/risky debugging: High only when justified.

Never block implementation just because the ideal model is unavailable.

## Hard BLE safety invariant

This app is telemetry-only.

The existing Gentai protocol defines the allowed read/query commands. The current known allowlist is:

- `0x00`
- `0x10`
- `0x11`
- `0x20`
- `0x21`
- `0x22`
- `0xF5`

Before implementing writes, verify these against `reference-web/PROJECT_HANDOFF.md`, `protocol.js`, and tests.

No command outside the verified telemetry allowlist may be sent to the BMS.

Do not implement:
- FET switching
- configuration writes
- calibration
- clearing/reset commands
- heater control
- balancing control
- hidden/undocumented write commands

Centralize packet construction behind one read-only protocol API that rejects non-allowlisted commands before Bluetooth is touched. Unit-test that rejection behavior.

## Source-of-truth behavior to preserve

Port from the web implementation rather than re-inventing:

- BLE service/characteristic UUIDs
- framing and checksum validation
- stream buffering / fragmented notification recovery
- signed current parsing
- positive current = charging
- negative current = discharging
- voltage/current/SOC/SOH/capacity/cycle/temperature/cell parsing
- protection/status bits
- reconnect behavior where sensible
- capacity-test calculations
- data-gap handling: **never fabricate energy/capacity through missing BLE periods**

Use golden packet samples from the old tests when available.

## Android technical direction

Default package/application id: `com.warwar13.gentai` unless the generated project already uses a deliberate alternative.

Preferred stack:
- Kotlin
- Jetpack Compose
- Coroutines + Flow
- Room/SQLite
- DataStore for settings/device identity
- native Android Bluetooth LE/GATT
- foreground service of type `connectedDevice` for continuous active monitoring
- optional Companion Device APIs where they improve association/background reliability
- WorkManager only for deferrable maintenance/export/cleanup jobs, never as the live BLE connection owner

No Android Studio assumptions. Everything must build/test from the command line using `gradlew.bat`.

Target modern Android SDK. Keep `minSdk` low enough for the user's Poco X3 NFC while implementing runtime behavior correctly on Android 12+ and newer.

## Background recording requirements

When the user starts monitoring:

1. establish/re-establish BLE GATT connection;
2. run an Android foreground service with a persistent notification;
3. poll/request telemetry at the verified interval (target about 5 seconds if that matches the existing protocol);
4. persist timestamped raw observations;
5. continue with the display off;
6. recover from transient disconnects using bounded backoff;
7. mark missing periods as gaps;
8. stop cleanly when the user explicitly stops monitoring.

Do not hide the foreground-service notification.

Do not claim “always running forever.” Android/OEM behavior can kill processes; record coverage/gaps so the data honestly shows interruptions.

## Telemetry model

Store raw values needed for later analysis, not only dashboard-formatted values.

At minimum, when available:
- UTC timestamp (epoch millis)
- connection/session id
- pack voltage V
- signed current A
- signed power W (`voltage * current`)
- SOC %
- SOH %
- remaining capacity Ah
- full capacity Ah
- cycle count
- individual cell voltages
- min/max cell voltage and delta
- all available temperature probes
- MOS/ambient temperature where available
- protection/status/FET bitfields
- RSSI when practical
- parser/protocol quality flags
- gap from previous valid sample

Derived charge/discharge integration must use actual elapsed time between valid samples. Reject/invalidate integration across a configurable gap threshold; never assume continuity across a disconnect.

## Sessions and summaries

Automatically classify segments into:
- charging
- discharging
- idle/near-zero current
- disconnected/data gap

Maintain useful summaries:
- start/end
- duration
- start/end SOC
- Ah in/out
- Wh in/out
- average power
- peak absolute power
- min pack voltage
- temperature maxima
- min/max cell delta
- sample count
- data coverage

Keep raw data for detailed analysis and support minute/daily/session rollups so long-term history stays fast.

## Database

Use Room entities/DAOs with migrations covered by tests.

Suggested conceptual tables:
- `TelemetrySample`
- `ConnectionSession`
- `PowerSession`
- `CapacityTest`
- `CapacityTestSample`
- `MinuteSummary`
- `DailySummary`

Names can change if a simpler normalized design is better.

Never commit the battery MAC/device identifier to Git. Persist selected device association locally only.

## UI milestones

Implement in this order unless the existing project state makes another sequence clearly safer.

### Scaffold normalization (first action)

The Android CLI scaffold may initially use a template-default namespace/application id and minSdk. Before protocol work, normalize it to:

- application id: `com.warwar13.gentai`
- namespace: `com.warwar13.gentai`
- minimum SDK: choose a value compatible with the Poco X3 NFC and required Android APIs; default target is `minSdk 26` unless the generated/current dependency set gives a better justified value
- keep the generated current compile/target SDK unless there is a concrete incompatibility

Move/rename Kotlin package paths as needed and run a clean build after normalization. Do not spend tokens explaining the rename unless it fails.

### M1 — Project + protocol port
- compile cleanly
- port protocol/frame parser
- port golden tests
- enforce read-only command allowlist

### M2 — Foreground BLE monitor
- permissions
- scan/select/connect
- notifications
- polling/query loop
- reconnect
- foreground `connectedDevice` service
- simple live telemetry screen

### M3 — Room persistence
- raw samples
- connection sessions
- gap tracking
- repository/Flow plumbing

### M4 — Analytics
- signed W
- Wh/Ah integration
- charging/discharging session detection
- daily/session summaries
- coverage %

### M5 — Dashboard/history
- live dashboard
- history charts
- cells/temperatures/protection detail
- session list/detail

### M6 — Capacity test
- port old behavior/calculations
- 5-second samples where appropriate
- pause/failure/gap semantics must be explicit

### M7 — Export/backup
- CSV exports
- database backup/export
- share through Android Storage Access Framework

### M8 — Poco reliability polish
- reconnect tuning
- reboot/app-restart behavior only if Android rules permit and user enables it
- battery optimization guidance
- error diagnostics

## Testing

At minimum:
- protocol/checksum unit tests
- fragmented/combined BLE frame-stream tests
- signed current tests
- allowlist rejection tests
- Wh/Ah integration tests including gaps
- session classifier tests
- Room DAO/migration tests where practical

Use fake BLE/repository layers so analytics and UI do not depend on a physical battery.

For changes to protocol parsing or energy integration, tests are mandatory before calling the milestone complete.

## Build commands

Fast validation:

```powershell
.\gradlew.bat testDebugUnitTest
```

Debug APK:

```powershell
.\gradlew.bat assembleDebug
```

Install on Poco:

```powershell
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

Prefer `--console=plain` when log output is going back into an agent context.

## Definition of done for each milestone

A milestone is complete only when:
- code builds;
- its relevant automated tests pass;
- no BLE safety invariant was weakened;
- `IMPLEMENTATION_STATUS.md` is updated concisely;
- known device-only verification is clearly marked rather than invented.

Do not spend tokens on a long retrospective.
