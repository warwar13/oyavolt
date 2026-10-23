# OyaVolt Home redesign: triangular battery flow

**Status:** design and implementation handoff only. This document does not authorize a code change by itself.

**Screen:** Home dashboard on the Poco X3 NFC, portrait first. Keep the existing dark OyaVolt visual language and the native telemetry, monitoring, alerts, and energy calculations.

## 1. What this redesign should achieve

The first glance at Home should answer, in this order:

1. How full is the battery?
2. Is the live battery charging, discharging, idle, or disconnected?
3. Which side of the energy flow is active?
4. How many watts and amps are being measured, and is an ETA available?
5. Is monitoring recording data, and is the reading fresh?

The current hero arranges Solar, Battery, and Output horizontally. The proposed hero is a clear **triangle**: a prominent battery SOC at the top point, Solar at the lower-left point, and Discharging at the lower-right point. The diagonal sides carry the direction animation. The triangle should feel like one composed instrument, with generous space around its three points and a calm background. The SOC remains readable when the animation is paused.

This is a presentation redesign of Home. The current energy journal, battery essentials, cell balance, temperatures, technical details, BMS warning presentation, Settings route, and bottom navigation remain available below or around the new hero. The design must continue to work with the existing foreground monitoring service and stored telemetry.

## 2. Exact meaning of the three points

| Point | Primary label | Supporting label | Data meaning |
| --- | --- | --- | --- |
| Top | `Battery` and a large `SOC %` | `Charging`, `Discharging`, `Idle`, or connection state | SOC reported by the BMS. This is the central measured object. |
| Lower left | `Solar` | `Assumed source` or `Charging input` | The battery is receiving power. The BMS measures charge into the battery but cannot identify whether the charger is solar or wall powered. |
| Lower right | `Discharging` | `Battery output` | Measured energy leaving the battery. This is not a measurement of total house load or inverter output. |

Use **Solar (assumed)** wherever space permits. A small information control explains: “The BMS measures power entering the battery but cannot tell whether it came from solar or a wall charger.” The Discharging node explains: “Battery output is measured at the battery; other household loads are not included.” Keep those qualifications near the visual, since the two lower labels could otherwise imply sensors the app does not have.

The left diagonal is the **charge path**, directed from Solar toward Battery. The right diagonal is the **discharge path**, directed from Battery toward Discharging. A very faint lower edge or base glow may complete the triangle visually, but it has no arrow, particle motion, numeric reading, or energy-flow meaning. There is no direct Solar-to-Discharging route.

## 3. Layout and visual composition

### First-screen wireframe

```text
OyaVolt                                  BLE  Settings

┌─────────────────────────────────────────────────────┐
│ [Recording / connection state]                  [i] │
│                                                     │
│                   BATTERY                           │
│              ╭─────────────╮                        │
│              │    64%      │   SOC arc              │
│              │ Discharging │                        │
│              ╰─────────────╯                        │
│                ╱           ╲                        │
│     charge path             discharge path          │
│              ╱               ╲                      │
│     [sun] Solar           [bolt] Discharging        │
│     Assumed source        Battery output            │
│                                                     │
│         128 W       −9.8 A       To 20% ~7 h       │
│         Power       Current      Estimate            │
│                                                     │
│  Last reading time / gap message when applicable    │
└─────────────────────────────────────────────────────┘

Energy journal: Today and This month
Battery essentials · Cell balance · Temperatures
Technical details
```

The example readings are placeholders. The top battery point is the visual anchor. The status chip sits above the triangle, and the metric rail sits below it. Neither overlaps a flow line. The battery node should show a printed SOC percentage as the primary value; the arc or fill reinforces it. Avoid a constantly spinning ring because it can read as “loading” and use power without adding data.

### Size and spacing targets

- Keep the Home page gutter at **16 dp**, with **12 dp** between major sections. The hero owns one rounded surface, approximately **24–28 dp** corner radius, with **16–20 dp** internal padding.
- On the Poco portrait width, place the battery point around the horizontal center. Its visible body is approximately **104–120 dp** wide and **100–112 dp** tall. The lower nodes are approximately **72–84 dp** wide, with icon and text kept within the hero.
- The triangle itself should occupy roughly **230–270 dp** vertically at normal font size. The complete hero will be taller after the status and metric rows; let content determine its final height rather than clipping it to a fixed value.
- Start each diagonal at the outside edge of its node, keeping the SOC numeral and node labels clear. A **2–3 dp** active stroke and a quieter **1–1.5 dp** inactive stroke should be visible on the near-black surface without looking like a wiring diagram.
- Place the lower nodes symmetrically, near 20% and 80% of the diagram width. Their text is centered under each icon. Leave enough horizontal separation for translated labels and large fonts.
- Keep the metric rail as one shallow row or a set of dividers within the hero. A second layer of floating mini-cards would make the triangle feel crowded. At larger font scales, stack the metrics vertically with dividers.
- The first screen should still show at least the beginning of the Energy journal on a normal Poco portrait layout. Prioritize legible data and allow scrolling rather than shrinking SOC text to force a height target.

### Visual hierarchy and color

Use the current palette: near-black background (`#070A08`), dark surface, lime (`#A8F05A`) for charge flow, amber (`#FFC857`) for discharge flow, muted gray-green for inactive geometry, and red only for critical SOC or actual warning/stop semantics. Support labels stay readable in the existing muted text color.

**Keep direction and SOC severity separate.** The active path color describes energy direction. The SOC number and its arc describe battery level: above 40% uses the healthy color, 21–40% uses warning amber, and 20% or below uses critical red. A low-SOC battery can therefore show a red SOC arc while its discharge path remains amber. BMS protection warnings retain their own warning presentation and are never replaced by the triangle glow.

Do not represent 64% as 64% of the triangle filled with light; that could be confused with power or coverage. The SOC arc is the only quantitative fill in the diagram. Power and current are printed values below it.

## 4. Animation language

The animation should be visually satisfying at a glance and quiet enough to live on a battery-monitoring screen.

### Charging

- The lower-left Solar-to-Battery diagonal brightens lime. Two or three small light points travel **upward toward the battery**. A restrained halo appears around the receiving battery point.
- The right diagonal stays visible at low contrast so the whole triangle shape remains understandable. It has no moving points.
- The label reads `Charging`; show measured power magnitude and signed positive amps in the metric rail. The Solar node continues to say `Assumed source`.

### Discharging

- The Battery-to-lower-right diagonal brightens amber. Light points travel **downward away from the battery**. A restrained halo appears at the Discharging point.
- The left diagonal remains at low contrast and has no moving points.
- The label reads `Discharging`; show measured power magnitude and signed negative amps. Do not add a separate “solar generation” value.

### Idle and unavailable data

- Fresh near-zero current shows `Idle`, both diagonals quiet, and no looping motion. A real measured zero can be printed in the metric rail.
- Connecting and reconnecting show their short state text, static geometry, and no animation. The last SOC may remain visible if labeled with its last-reading time.
- A stale sample, stopped monitor, Bluetooth-off state, or failed connection shows `Last recorded` or the actual connection state, a clearly subdued triangle, and a timestamp. Historical watts and SOC may remain visible but must look historical. Do not animate a historical direction.
- If no saved sample exists, show a static empty triangle with `Waiting for battery data` and the existing Settings path to enable or choose monitoring.

### Timing and motion budget

- A particle traversal of a diagonal should take about **2.2–2.8 seconds**. Two or three particles per active path are sufficient. A small blurred glow behind each point can add depth, with the core staying sharp.
- A direction change fades the old path down and the new path up over roughly **200–300 ms**. It should not momentarily show both paths as active. An idle or disconnected transition stops particles promptly.
- SOC arc changes may ease to a newly reported percentage over **400–650 ms**, then stop. The numeral should update clearly and never roll through implausible intermediate values for a long period.
- Particle speed and count stay constant; they do **not** encode watts, current, Ah, or charge rate. Those quantities belong in text. A faster animation at high load would imply a calibration the visual does not have.
- Animation runs only while Home is visible, the display is active, the sample is fresh, and system animation settings allow motion. It should not keep rendering frames while the screen is off or another tab is open. With reduced motion or animation scale zero, render a static directional arrow and highlight.
- Use one lightweight drawing surface and a single shared animation clock for the active path. Do not start a separate infinite animation for each dot, label, arc, and halo. Monitoring and database recording must continue independently of this UI effect.

## 5. State and data contract

The implementer should reuse the current Home state mapping and telemetry values rather than deriving a second definition of “charging.” In the present app, BMS current above **+0.15 A** means charging, below **−0.15 A** means discharging, and the near-zero band means idle. A live sample must also be from an effectively connected monitor and pass the existing **20-second freshness** check. Reconnecting overrides the last measured direction for animation purposes.

| State | SOC point | Active diagonal | Metric rail | Recording indication |
| --- | --- | --- | --- | --- |
| Fresh charging | Current BMS SOC and severity arc | Lower left → top, lime | Measured W magnitude, positive A, time-to-full only when available | Show a recording indicator only when the monitor reports an active charging recording; monitoring status stays visible independently. |
| Fresh discharging | Current BMS SOC and severity arc | Top → lower right, amber | Measured W magnitude, negative A, time-to-20% only when available | Show a recording indicator only when an active discharge recording exists. |
| Fresh idle | Current BMS SOC | None | Actual near-zero reading; ETA unavailable | Monitoring may still save raw idle observations, so do not claim recording is off. |
| Reconnecting / connecting | Last SOC marked historical, if available | None | Historical values dimmed or replaced by dashes | Show the connection state, not a live directional recording badge. |
| Stopped / stale / Bluetooth off | Last SOC with `Last recorded` time | None | Historical values clearly labeled | Show a Settings action for monitoring; do not show live REC. |
| No sample | Placeholder | None | Dashes | Show a concise empty state and the Settings route. |

Power is calculated from measured pack voltage and signed current. The main Watts value may use magnitude because the nearby state label and path already show direction; the current value retains its plus or minus sign. Keep the convention consistent across the hero, accessibility text, and any tooltip. The ETA comes from the existing estimator and must remain an estimate based on the present current, not a promised finish time. Show `—` when it is unavailable.

The triangle must not alter polling, BLE commands, persistence, gap handling, SOC alerts, or energy integration. A disconnect remains a visible gap in History; the Home animation simply stops while that gap is in progress.

## 6. Controls, safety, and interaction

- Retain the compact OyaVolt app bar with Bluetooth/connection status and Settings. Monitoring continues to start and stop through Settings under the current app behavior.
- An information control inside the hero explains the two lower points and the meaning of the moving lights. It should not cover or block a BMS warning.
- Tapping a lower node may show a short explanatory sheet, but it should not switch a FET, send a BMS command, change recording state, or act like a control for power flow.
- Keep the charging-cell status banner, explicit recording-gap message, stale timestamp, and decoded BMS warnings readable. A BMS warning should take priority over decorative glow and use the existing warning channel and UI treatment.
- Preserve the existing 25% and 20% low-SOC reminder behavior. The red SOC state is visual feedback and does not create another notification.

## 7. Accessibility and responsive behavior

- The hero should expose one concise semantic summary, for example: `Battery 64 percent. Discharging at 128 watts, minus 9 point 8 amps. Monitoring active. Reading just now.` The particle layer, halo, and decorative base should be hidden from accessibility services.
- State text and arrows must communicate direction without relying on lime or amber alone. All useful controls have at least a **48 dp** touch target.
- Keep the SOC numeral, state, and numeric rail as real text, not text painted into a bitmap. Use tabular numerals to avoid shifting as values change.
- At **1.3×** font scale, allow the metric rail to stack and the hero to grow. At **2×**, keep all labels and controls reachable by scrolling; avoid overlap between the SOC point, diagonal endpoints, and lower labels.
- In narrow or landscape layouts, retain the apex-above-two-points order. Reduce decorative spacing before reducing text. If the width cannot support two lower labels on one line, let their supporting labels wrap below each point.
- Respect system reduced-motion and animation-scale settings. A static active arrow still identifies charging or discharging when motion is disabled.

## 8. Implementation handoff for the next model

The current Home implementation is in `app/src/main/java/com/example/gentai/ui/main/MainScreen.kt` (its Kotlin package is `com.warwar13.gentai.ui.main`). The main places to inspect are `BatteryHeroCard`, `BatteryFlowDiagram`, `BatteryCenterNode`, `HeroMetricRail`, `homeFlowPresentation`, `socGaugeSeverity`, and `LIVE_TELEMETRY_FRESH_MS`. The current hero already knows about connection state, sample freshness, charge/discharge mode, ETA, cell status, stale time, and gap messages. Reuse that state path and replace only its layout and visual animation behavior.

Before implementation, inspect the relevant current Home tests and the existing dark-theme design notes in `docs/REDESIGN_HOME_POLISH.md` and `docs/REDESIGN_HOME_AND_GRAPHS_V2.md`. The earlier horizontal diagram is the starting point, not the desired final geometry. Keep the battery-protocol read-only invariant from `AGENTS.md`.

The implementer should work in this order:

1. Make a static triangle that fits the Poco at normal and large font scales. Confirm SOC, path labels, metric rail, stale timestamp, and warnings have clear space.
2. Wire the existing state mapping into the three diagonal states and verify label/color meaning with fake charging, discharging, idle, reconnecting, and stale samples.
3. Add motion only after the static layout is readable. Confirm inactive and stale states render no repeated frames, including when Home is not visible.
4. Preserve the energy journal and lower safety/detail sections, then run the narrow relevant tests, the full unit suite, lint, and debug build.
5. Install on the Poco, inspect screenshots at the top and after a short scroll, verify a real charge or discharge path, test Settings and Back, and confirm monitoring resumes after installation. Note any installation gap in the recorded data.

## 9. Acceptance checklist

- [ ] SOC is visually dominant at the top point and remains readable with motion off.
- [ ] Solar is lower left and explicitly marked as an assumed charging source.
- [ ] Discharging/Battery output is lower right and described as battery-side output.
- [ ] Only the appropriate diagonal animates for a fresh measured direction; the base has no energy-flow animation.
- [ ] Charging, discharging, idle, reconnecting, stale, stopped, and no-sample states have honest labels and static fallbacks.
- [ ] Signed current, measured Watts, SOC, and ETA follow the existing telemetry and freshness rules.
- [ ] SOC severity color remains independent of charge/discharge path color; BMS warnings retain priority.
- [ ] Animation stops when Home leaves the screen, the display is off, or reduced motion is requested.
- [ ] At normal and large font scales, nothing overlaps or clips; the Energy journal remains easy to reach.
- [ ] No BLE write behavior, data logging, History totals, alerts, or stored data is changed by the visual redesign.
