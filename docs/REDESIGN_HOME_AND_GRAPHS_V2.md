# OyaVolt Home and graphs v2 — battery-centered energy flow

**Status:** implemented 2026-10-18. Device validation and known limitations are recorded in `IMPLEMENTATION_STATUS.md`.

**Primary device:** Poco X3 NFC, portrait. **Theme:** existing near-black OyaVolt palette with lime charging, amber discharging, and restrained teal supporting accents.

## 1. Goal and reference images

Home should answer in one glance: **Is the battery live? Is it charging or discharging? What is its SOC, power, current, and estimated time?** A short scroll should reveal how much energy was recorded today and this month. Analytics should make the timing and magnitude of changes easy to inspect, with the clear axes, period tabs, crosshair, and tooltip seen in the reference screenshots.

The images in `photo/` are visual references, not telemetry specifications:

| Reference | Keep | Adapt or omit |
| --- | --- | --- |
| [Energy-flow overview](../photo/2c902f92-a91a-4b89-9de0-955bf9cd59ce.jpg) | Recognizable nodes, directional paths, animated flow, generous visual breathing room | Remove grid and inverter nodes. Do not invent PV input Watts, grid readings, or whole-home load. |
| [Day graph and data cards](../photo/5cd14de2-9649-4b60-b446-aee4efd01326.jpg) | Full-day time axis, horizontal guides, local peaks, selected-time crosshair and in-plot tooltip | Label the measured battery direction and units; do not call it PV generation. Do not draw a zero baseline across unmeasured hours. |
| [Low-power day graph](../photo/ba1d3ef0-c8af-446a-94a0-d58a58db3193.jpg) | Readable scale even when power is small, date controls, sparse supporting cards | Use Watts for ordinary battery power rather than tiny decimal kW labels. |
| [Month bars](../photo/fa2b03fd-88c8-4271-b01f-c6a8c5c57b09.jpg) | Calendar-aligned bars, clear month navigation, selected-day tooltip | Bars represent measured battery-side Charged or Used kWh, never solar generation. |

The BMS supplies pack voltage and signed current; power is `voltage × current`. Positive current means charging and negative current means discharging. It cannot identify whether a charger is solar or wall powered, measure panel production, or measure non-battery household loads. Home may show **Solar (assumed)** because that is the normal charging setup, with an explanation available beside the label. The output node means **battery output**, not total home consumption.

## 2. Visual system and information hierarchy

Keep the dark theme the user chose. Use the current OyaVolt typography family and these roles consistently across Home, Analytics, and History:

| Role | Treatment |
| --- | --- |
| Canvas and app bar | Near black (`#070A08`), no busy photographic background |
| Main card | Dark charcoal (`#151B17` to `#202820`); one clear card boundary |
| Charging path and readings | Bright lime (`#A8F05A`); subdued lime tint behind summaries |
| Discharging path and readings | Amber (`#FFC857`); subdued amber tint behind summaries |
| Neutral/secondary information | Muted gray-green (`#ADB8A9`); still legible on dark cards |
| Warning or Stop | Existing red semantic color, reserved for real warnings and the Stop action |
| Spacing | 16 dp page gutter; 12 dp between major cards; 14–18 dp inside cards |
| Corners | 20–24 dp main cards; 12–16 dp inner tiles; one consistent family |
| Text | `titleMedium` section headings, `labelMedium` labels, `headlineSmall` live Watts, `titleSmall` secondary values; use tabular digits for time and readings |

Avoid repeating the same prominent value in the hero, essentials, and journal. Decorative glow should stay behind the flow path and never reduce text contrast. Icons support a written label; a color or icon alone never has to explain direction.

### Home order

1. Compact OyaVolt app bar with connection indicator, Settings, and red Stop or green Start action. Keep Change battery in the existing overflow menu.
2. Live energy card: short state/recording badges, three-node flow, SOC, Watts, signed Amps, and ETA.
3. Any active charging-cell or BMS safety banner, visually ahead of financial information.
4. **Today**: separate Charged and Used tiles with kWh, average W, and Value or Saved in pesos.
5. **This month**: a shallower two-column energy and peso summary with a quiet coverage line.
6. Battery essentials, Cell balance, Temperatures, and collapsed Technical details.

Target a roughly 300–340 dp live card at normal font size so the beginning of Today is visible on a normal Poco portrait screen. This is a visual target, not a fixed height: allow the card to grow for accessibility fonts and optional warnings. Use no nested full-size cards inside a large journal card.

## 3. Live Home card: exact behavior

### Composition

The SOC indicator becomes the **central Battery node**, replacing the separate large gauge and straight flow strip. A compact 270° ring around that node still uses the existing SOC severity colors. The printed SOC percentage never moves. A Solar node sits upper-left and a Battery output node upper-right; two gently curved paths connect them to the Battery node. On the Poco, use approximately 64–72 dp side nodes and a 96–112 dp central node, with labels beneath them. Keep all three nodes within the content gutter.

```text
  Solar (assumed)                       Battery output
       [sun]                                  [load]
          ╲                                   ╱
           ╲    measured path when active    ╱
                 [ 87%  BATTERY ]

  Discharging   108 W   −8.19 A      20% in ~8 h 10 m
```

The example values are placeholders. The diagram must not show a separate inverter, grid, or simultaneous solar-to-home route. The active path can have a small measured-W pill; the inactive path shows `—` or no number, never `0 W` unless a real measured zero is appropriate. The text row below the diagram is authoritative for Watts and Amps; keep it easy to read while the paths move. The ETA/target clock sits below in smaller type when available.

### State table

| State | Visible label and paths | Motion | Recording indicator |
| --- | --- | --- | --- |
| Fresh charging (`currentA > +0.15 A`) | `Charging`; Solar (assumed) → Battery bright lime; Battery → output dim | Particles travel toward Battery; soft receiving-node glow | `REC` only when the service reports an active charging recording |
| Fresh discharging (`currentA < −0.15 A`) | `Discharging`; Battery → Battery output bright amber; Solar → Battery dim | Particles travel away from Battery; soft output-node glow | `REC` only when the service reports an active discharging recording |
| Fresh near-zero current | `Idle`; both paths dim | No looping motion | No `REC` |
| Connecting or reconnecting | Short connection label; last valid SOC may remain with a timestamp | No motion | No `REC` |
| Stopped, failed, Bluetooth off, or stale sample | `Last recorded` and local last-reading time; historical numbers remain visibly stale | No motion | No `REC` |
| No saved sample | `Waiting for battery data`; monitoring action remains available | Static empty diagram or concise empty state | No `REC` |

Use the current live-data freshness threshold and signed-current mode mapping rather than separate UI guesses. If the connection state and sample freshness disagree, present the safer non-live state. Direction changes fade one path out and the other in over about 200–300 ms; neither direction remains active through a gap.

### Animation and interaction

- Use a Compose Canvas path and a small constant-speed particle train, approximately a 2.4-second loop. Vary particle opacity and size slightly for depth. Do not vary speed with Watts or imply calibrated flow volume.
- Animate a restrained halo at the **receiving** node only while that direction is fresh and active. Keep the SOC arc animation tied to SOC changes, roughly 500–650 ms; do not rotate it continuously.
- Run animation only while Home is visible and animator duration scale permits it. At zero animator scale, draw a static arrow on the active path. The app must not spend frame time on this Home animation while background monitoring continues on another screen or with the display off.
- Make the diagram decorative to accessibility. Expose one combined announcement such as `Discharging, battery 87 percent, output 108 watts, minus 8 point 19 amps`; do not announce particles or glow frames.
- An information icon next to `Solar (assumed)` opens a short sheet: “Charging source is not measured by the BMS. Solar is assumed; wall charging is possible.” A second line explains that Battery output excludes other household loads.

### Lower Home cards

- **Today:** two equal-height direction tiles. Each shows icon and short heading, energy with up to three decimals for small values, `Avg … W`, and `Value ₱…` or `Saved ₱…`. At large font scale or narrow width, stack the tiles. If there is no qualifying measured interval, show `N/A`; missing tariff shows `Set tariff` without implying ₱0.
- **This month:** same Charged/Used order and color meaning, with energy and pesos. Keep coverage in a single quiet line and place the longer definition in an info sheet. Positive energy below 0.001 kWh displays `<0.001 kWh`, not `0.000 kWh`.
- **Essentials:** four aligned tiles for pack V, remaining Ah, SOH, and maximum measured temperature. **Cells:** its own card with C1–C4 to three decimals and cell spread. **Temperatures:** MOS, ambient, and probe values using matching tile heights and value sizes. Technical flags remain collapsed but accessible.
- Existing charging-cell warnings and decoded BMS protection information take visual priority over decorative flow. Red is not used merely because the battery is discharging.

## 4. Analytics design: reference-style charts

### Top-level layout

Analytics keeps a prominent **Charging / Discharging** segmented control; the selected direction determines all cards and chart color. Immediately below, show **Day / Month / Year / Total** period tabs, a centered period label, and previous/next controls. On Day, add **24 h / 6 h / 1 h** viewport chips; show **Watts / Amps / SOC** metric chips. Month, Year, and Total use energy bars in kWh, so metric chips are hidden there.

The default is Charging, Day, 24 h, Today, Watts. Changing direction retains the selected period and date. Changing period selects its containing calendar period; changing metric or date clears the selected point. Forward navigation stops at the current reporting day, month, or year. `Total` begins at the configured tracking start and has no previous/next controls.

Use one compact summary area above the graph:

| Card or field | Charging tab | Discharging tab |
| --- | --- | --- |
| Main energy | `Charged` kWh | `Used` kWh |
| Money | `Value` ₱ amount | `Saved` ₱ amount |
| Supporting metrics | Average W, peak W, recorded duration | Average W, peak W, recorded duration |
| Quiet metadata | Coverage and sample count | Coverage and sample count |

The money info button explains the current flat-tariff estimate. Value and Saved are separate concepts; they are not summed as one savings total. Cards must not obscure the graph or repeat long descriptions.

### Day line

- Reserve a fixed plot area of about 230–260 dp in the page card and about 330–400 dp in the expanded view. Give the left axis enough width for tick values and units; show roughly four horizontal guide lines. Put local time labels beneath the plot. Use sparse labels at a glance, with detail in the tooltip.
- The 24-hour overview occupies the selected local calendar day even when measurements cover only part of it. A daylight-saving day uses its actual 23- or 25-hour instant span; duplicated local times include a zone offset or abbreviation in the tooltip. For Today, mark future time as unavailable rather than measured zero.
- Default 24 h uses approximately 10-minute display buckets (at most about 144 visible points). The 6 h inspection view uses about 3-minute buckets (about 120 points); 1 h uses one-minute buckets or exact points when fewer. These are **display reductions only**. Pan a 6 h or 1 h window horizontally within the selected day. When changing viewport, center the selected time if there is one, otherwise the latest measured time; clamp to day boundaries.
- Keep each bucket’s average, true minimum, true maximum, sample count, time bounds, and pointer to exact source readings. Draw the average as a clear 2.5–3 dp line; show a faint min/max marker only when the range is materially wider than neighboring buckets so short dips remain discoverable without turning the overview into a noisy zigzag.
- Use timestamps for horizontal positions. A gap marker, an elapsed interval beyond the existing 30-second integration limit, an idle period, or the opposite direction breaks the line. A disconnected or unrecorded hour is empty with an optional very faint `No data` region; never draw a blue-style flat zero line across it.
- Watts and Amps include zero in the vertical range and use rounded tick values. SOC stays 0–100%. Prefer W for normal battery power, switching to kW labels only when values warrant it. Do not use tiny `0.001 kW` labels for a one-watt reading.

### Month, Year, and Total bars

- Month draws one calendar slot per day, Year one per month, and Total one per year since the tracking start. Future slots in the current period are muted. Use a single direction-colored series because the tabs already separate Charging and Discharging.
- A slot with recorded energy has a bar whose height represents kWh. A slot without valid observed data has no bar and may have a small muted no-data mark. This distinction must survive selection and accessibility. Use an honest zero only when there was qualifying measured coverage and the integrated energy was zero.
- Use `SolarReport`/daily rollups for aggregate energy. The selected bar tooltip shows its date or month/year, Charged or Used kWh, Value or Saved in pesos if tariff exists, and measured coverage. Bars and header totals share the same source and boundary rules.
- Keep month labels legible without placing 31 labels at full size. Show every fifth day plus first/last on the axis; the selected day is shown precisely in the tooltip. Year can label all 12 months. Total labels each year if space permits, otherwise use horizontal scrolling or spaced ticks.

### Selection and expanded view

- Tap or horizontal drag finds the nearest **measured** bucket or bar. Draw a dashed vertical crosshair and a bright selected marker. Position a compact floating tooltip inside the plot, clamped away from screen edges and above or below the marker so it does not cover the value being inspected. Tapping a long gap clears selection and does not fabricate a reading.
- A day tooltip shows local time (or interval), average, low, high, sample count, and the nearest exact saved reading with its time when raw source data is available. A summarized SQL History point is labeled as an interval summary, never as an exact reading.
- The expand icon opens a full-screen dark chart with the same direction, metric, date, viewport, and selection. Allow panning and a close action. The normal chart keeps vertical page scrolling usable; the expanded chart prioritizes horizontal inspection. Closing returns to the same page position and selection.
- A two-point recording can still show its short line; one valid point appears as an isolated dot. An empty day shows a calm `No recorded charging/discharging` state and retains navigation. No tooltip opens for an empty source.

## 5. History charts and page fit

Use the same axes, colors, crosshair, floating tooltip, and expand control for an expanded charging or discharging recording. Keep Watts, Amps, and SOC metric switching and show its record start/end and coverage outside the plot. The record’s own start/end defines its full view; a single recording never silently combines with the next one.

Apply the same selection style to History’s general telemetry graph. Its SQL projection already carries bucket start/end, count, extrema, and gap flags. A source bucket containing an internal gap remains omitted, with a visible break before the next safe point. The tooltip says `interval summary` and shows its source count. History’s Today / 7 days / This month range controls remain independent of Analytics’ Day / Month / Year / Total selector; no second copy of the Analytics selector is needed on History.

## 6. One consistent data source and calculation contract

The current Home can show nonzero charging Today and zero for This month because Today reads directional analysis of raw samples while This month reads the tracked solar rollup. The new presentation must reconcile them before applying visual polish:

1. Use **recorded** battery samples and the existing tracking-start boundary for Home Today, Home This month, Analytics cards, and aggregate bars. Do not silently mix the old all-sample directional scope with the tracked rollup scope.
2. Let the existing gap-safe solar energy integration and daily rollups supply Wh totals. For an in-progress day, use the same integration policy through the latest valid recorded sample and refresh Home/Analytics after new recorded data arrives. Make Today a subset of This month in the same reporting zone, including during an open recording.
3. Derive day average and peak power from qualifying recorded readings within that displayed direction and window. Keep those statistics separate from integrated Wh. An instantaneous sample is not itself a measured energy interval.
4. Respect session changes, explicit gaps, elapsed intervals over 30 seconds, tracking start, and local day boundaries. Never connect a line or integrate energy through missing BLE time. Rebuilding existing rollups may be needed for correctness; do not delete or reset the user’s recordings.
5. Use the configured tariff for pesos: `chargedWh / 1000 × tariff` is **Value** under the solar assumption; `usedWh / 1000 × tariff` is **Saved**, an estimate of displaced grid energy. The BMS cannot prove solar origin or actual bill savings. Keep the peso sign as `₱` and use `Set tariff` when unavailable.
6. Do not add PV, grid, inverter, or whole-home measurements to the data model. No new BLE command, schema migration, or new persisted source classification is required for this redesign.

## 7. Suggested implementation map

This section identifies ownership so the later coding pass can proceed without redesigning the feature again:

| Area | Work |
| --- | --- |
| `MainScreen.kt` | Replace the current gauge-plus-strip hero with the three-node battery flow; derive a small testable Home display state; keep existing monitoring actions and safety data. |
| `SolarEnergyCards.kt` and energy repository | Make Today/month cards use one tracked, recorded integration scope; remove the current contradictory totals; keep tariff messaging compact. |
| `AnalyticsScreen.kt` and `SolarReports.kt` | Bring the period controls and aggregate bars into the primary Analytics flow; share the selected direction/date across summary cards and chart; reuse existing rollups. |
| `ReadableTrend.kt` and `ReadableTrendChart.kt` | Add viewport-aware display reduction, axes, event hints, crosshair/overlay tooltip, period bars, and expand behavior while retaining gap and exact-reading metadata. |
| `HistoryScreen.kt` | Reuse shared graph presentation for flow recordings and the SQL-summary telemetry graph, preserving record boundaries and summarized-data labels. |

Prefer a small immutable UI model for graph selection (`direction`, `period`, `date`, `metric`, `viewport`, `selected time`) and a pure mapping for Home connection/flow state. Do not add a chart library unless the existing Compose Canvas proves unable to provide accessible, smooth selection on the Poco. This is a UI and reporting change, not a battery-protocol change.

## 8. Acceptance checklist

### Visual and interaction

- On the Poco, Home shows the three battery-centered nodes with no grid or inverter. The first screen exposes SOC, direction, Watts, Amps, and the beginning of Today after a short scroll.
- Charging lights only Solar → Battery; discharging lights only Battery → Battery output. Idle, stale, stopped, and disconnected states do not move. The measured W label stays on the active path.
- Today and This month use uniform labels, icon sizes, tile heights, energy precision, and peso formatting. Essentials, cells, and temperature cards remain readable at normal and large font sizes.
- Day charts show meaningful rises/dips without the former crowded five-second zigzag. A tap displays a crosshair and legible floating details; a drag follows the recorded line; a vertical swipe scrolls the page. Expanded view preserves selection.
- Month, Year, and Total bars align to calendar slots, distinguish no data from measured zero, and have useful selected-period tooltips. Charging and discharging tabs remain visually and numerically separate.

### Correctness and reliability

- Unit-test Home state mapping for charging, discharging, idle, stale, reconnecting, stopped, and recorder-start delay.
- Unit-test viewport reduction for a brief dip, sustained drop, tiny power, two-point and one-point traces, an explicit gap, a >30-second gap, and exact-reading selection. Test period bars for empty and measured-zero slots.
- Unit-test Today/month agreement and reporting boundaries, including an in-progress recording, tracking start in mid-day, local midnight, month/year rollover, and a daylight-saving day.
- Verify the displayed kWh and peso amounts agree across Home, Analytics, and History for the same tracked period. Confirm no chart display reduction changes stored energy, recording sessions, or BLE messages.
- Run `testDebugUnitTest`, `lintDebug`, and `assembleDebug`. Install on the Poco, inspect real charging and discharging recordings, compare screenshots to the reference composition, exercise tooltips/full-screen charts, check large text and reduced motion, and scan for crashes. Record device-only findings in `IMPLEMENTATION_STATUS.md`.

## 9. Explicit decisions

- **Visual theme:** retain dark OyaVolt neon; the light reference screenshots guide composition and interactions only.
- **Charging label:** `Solar (assumed)`, with an explanation; wall backup remains possible.
- **Flow topology:** Solar (assumed) → Battery → Battery output. Only the measured active direction animates; there is no grid, inverter, or simultaneous multi-source display.
- **Default graph:** Charging / Day / Today / 24 h / Watts. Day is a line; Month, Year, and Total are kWh bars.
- **Data scope:** tracked, recorded battery-side intervals using the existing reporting zone and tariff. No inferred energy during missing periods.
- **Implementation status (2026-10-18):** implemented and device-checked on the Poco. Home, direction-separated Day charts, adaptive SOC scale, calendar aggregate bars, floating details, and full-screen inspection were visually exercised with the retained recording. Automated verification is recorded in `IMPLEMENTATION_STATUS.md`.
