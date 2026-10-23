# History and Alerts redesign

Status: design proposal only. This document defines the next Android History and Alerts implementation. No code, notification behavior, telemetry, database, or stored data has been changed by this document.

## Goals

History should make recorded charging and discharging runs easy to review at the end of a day or month. Alerts should show only warnings that the battery management system (BMS) actually reported. Both pages should use the same light, calm visual language as the redesigned Home and Analytics pages.

The most important alert decision is explicit:

> The Alerts page and alert notifications retain BMS protection warnings only. OyaVolt must not create an alert just because SOC is low, the pack looks full, a cell exceeds a local threshold, or the cell-to-cell mV difference is large.

The BMS warning payload remains the source of truth. If it reports overvoltage, undervoltage, overcurrent, short circuit, temperature protection, or another decoded protection flag, show that warning and its timestamp. If it reports no protection warning, do not invent one from the ordinary telemetry values.

## Shared visual language

Use a local light Material 3 scheme consistent with Home, Analytics, and History's current light palette:

| Role | Color | Use |
| --- | --- | --- |
| Page | `#F5F7F5` | Page background and app bar |
| Card | `#FFFFFF` | Main cards and expanded content |
| Text | `#17251C` | Primary readings and headings |
| Muted | `#5D6D64` | Dates, units, coverage and explanations |
| Border/grid | `#DDE5DF` | Dividers and chart guides |
| Charging | `#167A50` / `#E6F6EC` | Incoming-energy records and Value |
| Discharging | `#A85B13` / `#FFF2E4` | Used-energy records and Saved |
| BMS warning | Material error red tint | Protection warning cards only |
| Neutral | `#6B766F` | Empty, legacy and inactive states |

Use 16 dp page gutters, 12 dp between cards, 16–20 dp card padding, and 18–22 dp corners. Keep headings short and put long explanations behind an info icon or expandable details. Use icons to reinforce Charging, Discharging, History, and BMS protection; never use color alone to communicate severity or direction.

Both pages should have a light top bar with a one-line title, readable dark status-bar icons, and safe-area handling. The shared bottom navigation remains unchanged unless a separate navigation redesign is requested.

## Alerts: source policy and removals

### Keep only BMS protection warnings

Retain the path that receives decoded BMS `status.warnings`/protection flags and de-duplicates newly active warnings. A BMS event may include values such as:

- Pack or cell overvoltage
- Pack or cell undervoltage
- Charge or discharge overcurrent
- Short circuit or overcurrent protection
- Over-temperature or under-temperature protection
- Any future warning that the verified protocol parser explicitly decodes from the BMS

Display the exact decoded warning text, with a short category label when it can be mapped safely. Unknown BMS warning text must remain visible as `BMS protection warning` plus the original text; do not discard it because it lacks a local category.

### Remove OyaVolt-created warning sources

The implementation should stop generating, notifying, recording, and displaying these as Alerts:

1. **Low-SOC reminders** (`LowSoc` / `LOW_SOC`). SOC is a reading, not a BMS protection event.
2. **Full-charge unplug alarms** (`FullCharge` / `FULL_CHARGE`). Remove the “Battery full — unplug charger” notification, the audible full-charge channel, reminders, episode persistence, and Alert history entries.
3. **Local charging cell-risk alerts** (`ChargingCellRisk` / `CELL_IMBALANCE`). Remove the “Unplug charger — Cx high” notification and all cell-spread/mV wording from the Alerts page and notification history.
4. **Any future alert based only on a locally chosen voltage, SOC, current, temperature, or cell-delta threshold.** Thresholds may still color Home telemetry or support diagnostics, but they must not become alert events unless the BMS itself reports a protection warning.

The cell values, pack voltage, SOC, current, temperature, and cell delta may remain available in Home, Analytics, and History as measurements. The cell-to-cell mV difference is removed from Alerts and notifications as a trigger and as alert copy. It should not be presented as a BMS warning unless the BMS warning payload explicitly names a cell protection condition.

### Historical cleanup

The current `alert_events` table contains older locally generated types. The implementation should ensure that the user sees only BMS events after the update:

- Make the Alerts query include only `BMS_PROTECTION` rows, or migrate away `LOW_SOC`, `FULL_CHARGE`, and `CELL_IMBALANCE` rows in a one-time cleanup.
- Prefer a small Room migration or repository cleanup that removes only those alert rows. Do not delete telemetry samples, flow recordings, History records, or capacity tests.
- Keep BMS protection rows and their timestamps. If the schema gains `source`, set existing protection rows to `BMS`; old local rows must never be relabeled as BMS.
- Add an explicit retention rule for BMS events, currently 30 days. Pruning must not delete newer events and must remain independent of telemetry-history deletion.

### Notification behavior

Keep one high-importance BMS protection notification channel, with a concise notification title such as `BMS protection warning` and the decoded warning text as the body. Use the existing de-duplication episode behavior so one continuously active warning does not produce repeated notifications on every poll. A new BMS warning after recovery may notify again.

Remove the full-charge alarm channel and any notification IDs, sound, vibration, episode state, or channel descriptions that mention unplugging the charger. Remove “unplug charger,” “cell spread,” and local low-charge reminder wording from notification text and channel descriptions.

Opening a BMS notification should take the user to Alerts or the relevant battery details. The notification must not claim that the charging source is solar and must not prescribe an action that the BMS did not provide.

## Alerts page layout

Top-to-bottom structure:

1. Light app bar: `Alerts`, with an optional compact info icon explaining that entries come from decoded BMS protection flags and are retained for 30 days.
2. A small summary row: `BMS warnings` and the count in the selected retention window. If there are none, show `No BMS warnings in the last 30 days.`
3. Optional compact filters for `All`, `Overvoltage`, `Undervoltage`, `Current`, `Temperature`, and `Other`. Filters are categories derived from BMS warning text, never local threshold calculations. `All` is the default.
4. A date-grouped event list. Each day heading appears once; events within a day are newest first.
5. Each event is a compact card with:
   - Red warning icon and severity that is tied to the BMS protection event.
   - Short category title such as `Pack overvoltage` when mapping is verified, otherwise `BMS protection warning`.
   - Original BMS warning text as the main detail.
   - Local timestamp and optional SOC at the time the BMS warning was received.
   - An expand affordance for raw decoded flags/bitfield and the associated sample timestamp, if available.

Do not show cards for low SOC, full charge, charger unplugging, cell mV difference, or locally detected “high cell” conditions. Do not include a long paragraph above the list. Put the source explanation in the info dialog and a short caption such as `Warnings reported by the battery BMS` below the summary.

Empty and error states:

- No events: a calm notification icon, `No BMS warnings`, and `The battery has not reported a protection warning in this period.`
- Only removed legacy events exist: show the same empty state after cleanup; do not mention that old app-generated alerts were deleted.
- Database/read error: show a retry action and preserve the last successful page state where possible.
- Large font: event title, warning text and timestamp wrap; no warning text is clipped.

## History page goals

History should answer “what happened during the selected period?” in this order: total measured charging/discharging energy, individual recordings, a selected recording chart, then less frequently used legacy and capacity-test data. It should not make the user scan through daily summaries before finding the recording they opened.

Retain these data sources and actions:

- Automatically created Charging and Discharging flow recordings, including open/active status, duration, Wh/Ah, average/peak power, SOC endpoints, sample count, coverage, and gap-safe summaries.
- Raw samples for an expanded recording chart.
- Legacy mixed recordings, clearly labeled as legacy and classified by Analytics without rewriting them.
- Capacity-test summaries and compatible CSV import.
- Date-range selection, telemetry-history deletion, capacity-test preservation, and the existing reset/data controls.

Do not change BLE protocol, recording boundaries, gap semantics, tariff calculations, or the meaning of positive and negative current as part of the visual redesign.

## History page layout

### Header and period controls

Use a light `History` app bar. Keep import and delete actions as compact icon buttons with precise content descriptions; move the full destructive action into the page's lower `Data management` section so the app bar is calm.

Below the bar, show a single period control card:

- Presets: `Today`, `7 days`, `This month`, and `Custom`.
- A concise selected-range label with a calendar icon.
- Previous/next period arrows where they are meaningful; do not allow a future period.
- The reporting timezone used by Analytics and Solar reports for date boundaries.

### Period overview

Show two direction cards side by side at normal width and stacked at large font/narrow width:

| Charging card | Discharging card |
| --- | --- |
| Charged kWh and Ah | Used kWh and Ah |
| `Value ₱…` | `Saved ₱…` |
| Duration and average W | Duration and average W |
| Green charging icon | Amber discharge icon |

Place a quiet coverage line below the cards: observed time, missing time, and coverage percentage. If there is no valid interval, show `N/A` rather than a fabricated zero. Keep money labels short and use the peso sign consistently.

### Recordings list

Use a section heading such as `Recordings` with a short count. Add a compact direction filter (`All`, `Charging`, `Discharging`) and an optional sort control (`Newest`, `Longest`, `Most energy`).

Each recording card should show:

- Direction icon, `Charging` or `Discharging`, and a small state badge (`Active`, `Complete`, `Gap ended`, or the existing end reason in a tooltip/details row).
- Local start and end time, or `Active now`.
- Primary energy (`kWh`) and `Ah`.
- Secondary duration, average W, peak W, SOC start → end, and coverage.
- Value/Saved estimate only when tariff settings are available; otherwise `Set tariff`.
- Chevron or card tap to open the detail chart.

Avoid repeating the same long explanatory sentence on every card. Keep the card height stable when optional values are missing.

### Recording detail

Open the selected recording in an inline detail card or a detail destination. The top of the detail should show the same direction, time range, energy, money estimate, coverage and gap count.

Below it, use an interactive timestamp-scaled chart with compact metric chips:

- `Watts` default
- `Amps`
- `SOC`
- Optional `Voltage` or `Temperature` under a secondary `More metrics` control if the existing data supports it

Break lines at recorded gaps and direction changes. Do not connect across missing BLE time. Tap/drag selects the nearest timestamp and shows a concise local time/value readout. Preserve actual time spacing so an 8 AM–3 PM run visibly occupies that window rather than being stretched across an entire day.

Keep the chart readable in a light card: 190–220 dp plot height, direction color, soft grid, clear units, and a short empty state when fewer than two samples exist. The detail should expose sample count and coverage without making them the visual headline.

### Supporting sections

Place these after the selected recording detail, collapsed by default:

- **Legacy recordings:** older mixed records retained for compatibility. Explain in one line that Analytics classifies them automatically; do not call them current Charging/Discharging runs.
- **Capacity tests:** import status and results. Keep capacity-test samples separate from ordinary energy totals and make clear that a capacity test is not a solar savings record.
- **Daily summaries:** compact rows showing date, charged/used kWh, Value/Saved and coverage. Keep the full list behind an expandable section so a long month does not dominate the first screen.
- **Data management:** import, delete selected telemetry period, and reset controls with explicit confirmation. State exactly what is removed and what is preserved before the destructive button is enabled.

### History empty states

- No telemetry in the selected range: show `No recordings for this period` with a short suggestion to choose another range or start monitoring.
- Only legacy rows: show the legacy explanation and keep the flow filter honest.
- Active recording with no completed interval: show `Recording in progress` with live sample count and `Complete data will appear when the run ends.`
- Missing tariff: keep kWh/Ah and technical chart data available; only Value/Saved displays `Set tariff`.

## Data and migration boundaries

- Reuse existing Room flow recordings, telemetry samples, daily summaries, capacity tests and AlertEvent rows where their meaning remains valid.
- Add an alert source/category only if it makes BMS-only filtering explicit; avoid a broad migration that rewrites telemetry or recording history.
- If old generated alert rows are removed, delete only `LOW_SOC`, `FULL_CHARGE`, and `CELL_IMBALANCE` alert rows. Do not reset today's recording or any History record.
- Keep BMS warning de-duplication state for active BMS warning episodes. Remove only local full-charge/cell-alert persistence keys and their cleanup code when implementing the removal.
- Historical BMS events without a stored pack voltage or raw bitfield should still render their original warning text and timestamp. Do not backfill values from a later sample.
- Coverage, Wh/Ah integration and tariff estimates must continue to use existing gap-safe calculations and reporting timezone.

## Accessibility and device behavior

- All icon-only actions have spoken labels: import, delete, period navigation, filters, expand, open chart and retry.
- Direction, BMS warning category, time, SOC, energy, and money are available in reading order. Color and icons supplement text.
- Use at least 48 dp touch targets, wrap long BMS warning text, and support large font without horizontal clipping.
- Keep screen-off monitoring and foreground-service behavior unchanged. A notification received while the screen is off should open Alerts when tapped.
- Home, Analytics, History and Alerts should agree on the same reporting day, direction, kWh/Ah, peso estimate and gap handling.

## Implementation and verification plan

1. Implement the BMS-only alert policy first: stop app-generated alert creation/notification, remove full-charge/cell alert channels and persistence, and hide or migrate old local alert rows.
2. Redesign Alerts with the light card layout, BMS category filtering, day grouping, compact detail expansion and empty states.
3. Redesign History around period overview cards, direction-filtered recordings, a selected recording chart, and collapsed supporting sections.
4. Preserve existing deletion/import behavior with explicit confirmation and no telemetry or recording reset during the alert cleanup.
5. Add focused tests for BMS-only event conversion, legacy alert filtering/cleanup, notification titles, date grouping, History direction filters and empty states. Existing alert-policy tests for low-SOC/full-charge/cell-risk generation should be replaced with assertions that those sources no longer produce user alerts; BMS warning de-duplication tests must remain.
6. Run `testDebugUnitTest`, `lintDebug`, and `assembleDebug`.
7. Install on the Poco X3 NFC and verify: BMS overvoltage/undervoltage warning display, no unplug-charger or mV-difference alert, date-range filtering, charging/discharging record cards, gap-broken chart, large-font wrapping, destructive confirmations, notification tap navigation, and screen-off notification behavior.

## Acceptance criteria

- Alerts contains BMS protection warnings only. A low SOC, full pack, high local cell, or mV difference does not create an Alert row or notification.
- The full-charge “unplug charger” notification, channel, sound, vibration, history title, and cell-spread wording are gone.
- Exact BMS warning text remains visible, including unknown future warning text.
- Existing BMS warning de-duplication and 30-day retention continue to work.
- History first shows the selected period's separated Charging/Discharging totals and then useful recordings with light cards and readable charts.
- Charging and Discharging remain separate in every History summary, chart detail, and peso estimate.
- Gap intervals are visible as gaps and never become fabricated energy or savings.
- Legacy data, capacity tests, import, reset, and deletion remain understandable and protected by clear confirmations.
- The pages remain readable at large font sizes and on the Poco X3 NFC in portrait orientation.

This document is ready for implementation review. No code has been changed for the redesign yet.
