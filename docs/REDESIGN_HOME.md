# Home redesign — light dashboard and live energy flow

Status: implemented and refined. The current dark-neon refinements are documented in `docs/REDESIGN_HOME_FIXES.md`. No BLE, recording, database, or calculation behavior was changed.

## Goal

Make Home pleasant to check at a glance on the Poco X3 NFC. The first screen should answer: how full is the battery, is it charging or discharging, how much power is moving now, and what was measured today? Keep every useful existing reading available without repeating the same number in multiple prominent places.

The visual direction follows the light Analytics and History pages: warm off-white canvas, white cards, dark text, green for charging, amber for discharging, and restrained red for warnings and the Stop action. The shared bottom navigation may retain its existing dark styling; the Home page itself, including its app bar, should be light.

## What Home must preserve

- Live SOC, signed-current direction, absolute watts, signed amps, estimated time and target clock when available.
- Pack voltage, remaining Ah, SOH, four cell voltages at three decimals, cell delta, MOS/ambient/probe temperatures, and charging cell-safety warnings.
- Today's separate charged and used kWh, average charging/discharging watts, estimated charging **Value** and discharge **Saved** amounts; this month's separate charged/used totals and peso estimates.
- Connection and monitoring state, Start/Stop, Settings, change-battery access, last-reading timestamp, and the existing technical details/protection information.
- Existing data semantics: positive current charges, negative current discharges; charging source is unknown; idle periods are not recorded; gaps are never turned into energy.

No new battery measurements are required for the visual redesign. The battery cannot identify whether incoming energy came from solar or a wall charger. Do not draw a sun icon on the live charging path or label live charging as confirmed solar.

## Visual system

| Element | Proposed value | Purpose |
| --- | --- | --- |
| Page and app bar | `#F5F7F5` | Same calm base as Analytics and History |
| Main card | `#FFFFFF` | Strong separation from the page |
| Primary text | `#17251C` | Clear on white |
| Secondary text | `#5D6D64` | Labels and timestamps |
| Hairline | `#DDE5DF` | Sections and card boundaries |
| Charging | `#167A50` / tint `#E6F6EC` | Charge state, path, charged amount |
| Discharging | `#A85B13` / tint `#FFF2E4` | Discharge state, path, used amount |
| Idle or stopped | `#6B766F` | Neutral state, no moving path |
| Warning and Stop | Material error red | Safety banner and active Stop control |

Use the existing typography family. Prefer sentence case and short labels. Use a 16 dp page gutter, 12 dp between major cards, 16–20 dp inside cards, and 18–24 dp card corners. Use the same radii, tile heights, icon sizes, and label-to-value spacing in both directions. Avoid heavy gradients, neon glow, stacked tinted cards, and large shadows. Color should mark state and hierarchy rather than fill most of the screen.

The app bar has one-line `OyaVolt` at left. At right, retain a compact connection icon, Settings, the circular red Stop or green Play action, and the existing overflow/change-battery action when relevant. Do not bring back the long `Saving · Discharging` text. Make the connection icon and action descriptions accessible even though they are icon-only.

## Page structure

1. **App bar:** `OyaVolt` plus the compact controls above. No second-line subtitle on narrow screens.
2. **Live hero:** one white card with a short state label, animated flow path, SOC ring, prominent watts, signed amps, and ETA. The state label is shown once. Keep the SOC percentage and the actual numeric readings static and easy to read while the decorative path moves.
3. **Safety alert, when active:** an existing charging-cell or protection warning appears directly below the hero, before the financial journal. It must remain readable without animation.
4. **Today:** two equal-width white cards, `Charged` and `Used`, each with kWh as the leading number, `Avg W` below, and a small distinct `Value` or `Saved` peso row. Green/amber icons identify the direction. If there is no valid measured interval for a direction, its readings show `N/A`; a valid measured zero is shown as zero.
5. **This month:** one shallow white card beneath Today, with the same Charged/Used order and separate kWh and peso values. Keep coverage as quiet metadata or in an info sheet, not a large headline.
6. **Battery essentials:** a compact two-column group for pack voltage, remaining Ah, SOH, and maximum temperature. Use a simple icon with each label. These replace duplicate large gauges for the same readings. Show `N/A` for unavailable values.
7. **Cells:** a separate compact card after the essentials. Show `Cells` and delta at its top, then C1–C4 in four aligned tiles with existing three-decimal values and balance colors.
8. **Temperatures and diagnostics:** keep MOS, ambient and available probes in a tidy grid or expandable section. Retain protection/status bits and signal in `Technical details`, collapsed by default. Preserve access to the existing alert history in navigation.

The Solar battery journal information should become the Today and This month sections above, with one small info icon that explains the estimates. Avoid a large journal card containing more cards. Keep `Solar battery journal` only if it still helps identify the group; otherwise use the shorter section heading `Energy journal`.

Illustrative phone layout; values are placeholders:

```text
OyaVolt                         [BLE] [⚙] [■]

┌──────────────────────────────────────────┐
│ Charging                                 │
│ input  • → → → •  battery                │
│                                          │
│               78%                        │
│          STATE OF CHARGE                 │
│                                          │
│              184 W                       │
│             +13.2 A                      │
│       Full in ~1 h 20 m · ≈ 3:20 PM      │
└──────────────────────────────────────────┘

Today                                 [ⓘ]
┌ Charged ─────────┐  ┌ Used ──────────────┐
│ 0.298 kWh        │  │ 0.412 kWh          │
│ Avg 137 W        │  │ Avg 104 W          │
│ Value ₱3.87      │  │ Saved ₱5.36        │
└──────────────────┘  └────────────────────┘

┌ This month ──────────────────────────────┐
│ Charged 4.2 kWh       Used 3.8 kWh       │
│ Value ₱54.60         Saved ₱49.40       │
└──────────────────────────────────────────┘

┌ Pack voltage ───┐  ┌ Remaining ─────────┐
│ 13.86 V         │  │ 42.10 Ah           │
└─────────────────┘  └────────────────────┘
┌ Temperature ────┐  ┌ Health ────────────┐
│ 31 °C           │  │ 96% SOH            │
└─────────────────┘  └────────────────────┘

┌ Cells                              12 mV ┐
│ C1 3.462  C2 3.460  C3 3.472  C4 3.465 │
└──────────────────────────────────────────┘

Temperatures                         [⌄]
Technical details                     [⌄]
```

Do not treat the wireframe's example times, voltages, energy or peso amounts as fixtures. Real data and available room determine the final text wrap. The Home scroll should expose the hero and the start of Today on a normal Poco portrait viewport; avoid oversized blank space above the journal.

## Live flow animation

Use one small **directional energy path** near the top of the hero. It is a visual cue for live flow, separate from the SOC ring and the recorded-energy totals.

| State | Path and motion | Label | Recording indication |
| --- | --- | --- | --- |
| Fresh charging sample (`currentA > +0.15 A`) | Two or three green dots travel from a generic input/bolt icon toward a battery icon, left to right, over about 2.4 seconds; repeat smoothly. | `Charging` | A small `REC` dot appears only after the automatic recording service has actually entered its charging recording mode. |
| Fresh discharging sample (`currentA < -0.15 A`) | Amber dots travel from the battery icon toward a generic load/bolt icon, left to right; arrows and icon order show outward flow. Same speed and distance. | `Discharging` | `REC` appears only during an active discharge recording. |
| Fresh near-zero sample | Stationary neutral battery/path; no looping animation. | `Idle` | No `REC`; idle is not saved. |
| Connecting/reconnecting, Bluetooth off, failed, stopped, or stale sample | Stationary neutral path or a subtle one-time status transition; never show moving energy. | Short true state such as `Connecting`, `Reconnecting`, or `Last recorded`. | No `REC` while disconnected or stale, even if service state takes a moment to catch up. |

The red Stop icon in the app bar means monitoring can be stopped; it is **not** proof that charging/discharging rows are currently being saved. Keep the service's actual recording state visually distinct. The green Play icon means monitoring can be started. When Play opens the battery picker, show that normal flow rather than a fake connected animation.

Animation rules:

- Show moving dots only when the monitoring connection is active **and the latest valid telemetry is fresh**. Define freshness against the existing polling cadence (about five seconds) with the app's live-data freshness threshold. A historical last sample is static even if it says charging.
- Derive direction from the same signed-current threshold used by automatic recording. A connection alone does not determine flow direction. Charging and discharging cannot be animated at the same time on this BMS.
- Use constant gentle speed. Do not vary animation speed with watts: that would imply a calibrated flow-rate scale. Numeric watts and amps carry the measurement.
- Transition direction by fading the old path out and the new one in over about 250 ms. Snap to neutral on stale/disconnect, so movement does not imply continued energy flow during a gap.
- Animate the SOC arc only when SOC changes, over roughly 500–650 ms. Never continuously rotate the SOC ring or move the printed percentage. Respect existing green/amber/red SOC thresholds and cell-safety colors.
- Loop only while Home is visible and the device permits animations. Respect Android reduced-motion/animator-scale settings; at zero scale show a static arrow and icons. Stop frame work when Home is offscreen or the display is off so this does not waste battery during background monitoring.
- Keep the moving path decorative for accessibility. Screen readers announce the state, SOC, watts, and amps once; they should not announce each moving dot or each animation frame.

Implementation can use Compose Canvas/animation primitives already in the project; no video, GIF, web view, or new animation dependency is needed. Favor a thin path and small dots over a large pulsing hero background.

## Content and state rules

- **Fresh live:** show current measurements and flow. Make the last-reading age available in a short caption or accessibility text, without competing with watts.
- **Idle:** keep the latest valid SOC and measurements visible, use a stationary path and `Idle`, and leave today's recorded totals intact.
- **Disconnected or stale:** keep the last reading with a clearly visible `Last recorded` state and timestamp. Freeze the path immediately. Do not let an old charging sample continue to animate or appear freshly measured.
- **No sample yet:** show a useful blank hero (`Waiting for battery data`) and start/select control; do not fill cards with fabricated zeros. Today/This month can still show prior recorded totals.
- **Missing tariff:** the energy numbers remain visible. Peso rows show `Set tariff` and open the existing Settings page; do not show `₱0.00` as an implied estimate.
- **Money explanation:** an info control explains `Value = charged kWh × tariff` assuming solar charging; `Saved = discharged kWh × tariff` is an estimate. The BMS cannot prove the charging source or actual bill savings. Show the peso sign `₱` consistently.
- **Gaps and partial coverage:** use existing gap-safe rollups/analytics and retain a concise coverage explanation in the info sheet or details. Neither animation nor money cards may infer energy through missing BLE periods.
- **Safety:** cell-voltage warning banners and protection alerts take priority over decorative motion. Red means a real stop/warning/critical condition, not ordinary discharging.

## Layout, accessibility, and system UI

- At normal phone width, Today cards can sit side by side. At narrow widths or large font scale, stack cards vertically rather than clipping kWh, peso, or labels. Cell values may use a horizontally scrollable row or two-by-two grid if four tiles cannot fit legibly; never truncate three-decimal voltage.
- Use at least 48 dp touch targets for app bar actions, info, and expandable rows. Icon-only controls need specific spoken labels (`Start monitoring`, `Stop monitoring`, `Bluetooth connected`, `Open settings`).
- Preserve a logical reading order: connection/state, SOC, power/current, ETA, battery essentials, cells/safety, Today, Month, details. Do not rely on color or animation alone to show direction.
- A local light Material 3 scheme should cover Home cards, top bar, dialogs, and the battery picker. Set dark status-bar icons against the light top bar when Home is active and restore the correct icon mode on other destinations. The shared bottom navigation can stay as it is unless a separate navigation redesign is requested.
- Respect safe-area insets, gesture navigation, portrait orientation, and the Poco X3 NFC's usable width. Keep spacing consistent when optional ETA, probes, or warnings appear/disappear.

## Implementation map for a later coding pass

1. Introduce a reusable local light palette matching Analytics/History; avoid copying a third independent set of nearly identical color values. Apply it to `MainScreen`, `SolarHomeCard`, dialogs and picker while leaving BLE and persistence untouched.
2. Refactor the Home hierarchy in `MainScreen.kt`: concise app bar, one live hero with flow strip, a conditional safety alert, journal, then essentials/cells and diagnostics. Remove only duplicate visual presentations; retain the underlying readings and safety logic.
3. Create a small state model for Home display using connection state, sample timestamp, signed current, and the service's actual active recording mode. Gate `REC` on both fresh live data and active recording mode. Keep UI freshness and flow decisions testable without an Android device.
4. Add the directional Compose animation with lifecycle and reduced-motion handling. Keep SOC animation tied to value changes. No frame-by-frame database reads or service updates.
5. Rework `SolarEnergyCards.kt` into Today and This month cards that reuse existing `DirectionalAnalytics`, `SolarReports`, `EnergySettings`, and reporting time zone. Keep charging Value and discharge Saved separate.
6. Update `IMPLEMENTATION_STATUS.md` after coding and record any device-only findings.

## Acceptance checks for implementation

- `OyaVolt` stays on one line with active and stopped app-bar actions on the Poco; no long recording text crowds it.
- A fresh charging sample animates toward the battery; a fresh discharging sample animates away; idle, reconnecting, stopped and stale views do not animate. Changing direction does not leave two paths moving at once.
- `REC` agrees with the service's actual automatic recording mode. The Stop/Play control remains usable in all relevant connection states.
- Home retains every measurement and safety warning listed above, with no duplicated headline watts or conflicting direction wording.
- Today's and month's kWh/₱ values agree with the existing Analytics/History values for the same reporting day and tariff. Missing intervals stay excluded and unavailable values show `N/A`/`Set tariff` appropriately.
- Check normal and large font sizes, narrow layout, no data, missing tariff, cell warning, Bluetooth interruption, screen reader labels, reduced motion, and status-bar/nav-bar contrast on the Poco X3 NFC.
- After implementation, run `testDebugUnitTest`, `lintDebug`, and `assembleDebug`; add focused tests only for new freshness/state mapping and any changed summary mapping. Inspect screenshots with real charging and discharging samples before calling the visual work complete.

This proposal is ready for review before any Home code changes.
