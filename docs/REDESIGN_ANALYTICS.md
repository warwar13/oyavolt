# Analytics redesign — Charging and Discharging

Status: implemented; retained as the design reference. This document covers the Android Analytics page only. Home, History, Alerts, Settings, and the shared dark bottom navigation retain their current appearance.

## Goal and visual direction

The selected day's direction, measured energy, estimated peso amount, and power trend should be understandable in one glance. Keep the page calm and readable on the Poco X3 NFC, with detailed numbers available when wanted instead of filling the first screen.

Use a light Analytics page inside the existing dark app shell. The page header and scrolling content use a local Material 3 light scheme; the shared bottom navigation stays dark. Keep the system status bar legible when entering and leaving Analytics: use dark status icons over the light Analytics header, then restore the existing light icons on dark pages. Use a soft off-white background, white surfaces, restrained color, rounded cards, and comfortable spacing. Icons reinforce labels; they do not replace them.

| Role | Color | Use |
| --- | --- | --- |
| Page | `#F5F7F5` | Analytics background and top bar |
| Surface | `#FFFFFF` | Main cards, chart, expanded details |
| Text | `#17251C` | Primary text |
| Muted text | `#5D6D64` | Units, captions, secondary labels |
| Border/grid | `#DDE5DF` | Card outlines and chart guides |
| Charging | `#167A50` | Charging icon, selected tab, chart line, value highlight |
| Charging tint | `#E6F6EC` | Charging summary accent and selected control |
| Discharging | `#A85B13` | Discharging icon, selected tab, chart line, savings highlight |
| Discharging tint | `#FFF2E4` | Discharging summary accent and selected control |
| Missing data | `#A9B5AD` | Chart gap indicators only |

Check actual text and icon contrast in the light theme. Use the same typography family as the app; rely on size and weight for hierarchy rather than extra colors or all-caps paragraphs. Use 16 dp horizontal page padding, 12 dp between major cards, 16–20 dp within cards, and consistent 18–22 dp card corners. Keep decorative borders and shadows subtle; do not use strong gradients or tinted full-card backgrounds that compete with the readings.

## Page structure

Top to bottom on both tabs:

1. A compact `Analytics` top bar on the light page.
2. A one-row date selector with previous/next arrows, `Today` or a short selected date, and a `Today` shortcut only when viewing another day. Disable next on the current day.
3. A two-part segmented control: charging battery icon + `Charging`, and energy/output icon + `Discharging`. The selected segment uses its direction color and tint; inactive text remains readable.
4. A compact daily overview group with **two distinct cards** in one row: the larger energy card shows a small `Charged` or `Used` label and a prominent kWh number; the smaller peso card shows `Value` or `Saved` and a prominent `₱` amount. Beneath them, one shallow white strip holds `Avg W`, `Peak W`, and `Duration` as three evenly spaced values. At large font sizes or widths below about 360 dp, stack the two cards vertically instead of shrinking the numbers or truncating the peso amount. Do not repeat the direction in every label.
5. One trend card with a short heading (`Power trend`), a Watts/Amps/SOC segmented control, and the interactive chart. Aim for a plot around 190–220 dp high on a normal phone. Its heading and upper portion should be visible after the daily overview without requiring a long scroll.
6. Collapsed `Periods` and `Battery details` rows. Each row has a concise title, chevron, and useful one-line preview. Full segment timestamps, Ah, current, coverage, sample count, battery temperatures/voltage/cell delta, and other diagnostics live inside these sections.
7. A collapsed `More insights` row containing the existing Day/Month/Year/All solar report and payback/ROI content. Build its content when opened so the main day view stays responsive. Preserve the selected report period when it is collapsed and reopened.

Suggested phone hierarchy:

```text
Analytics                              [light header]
‹  Today · 18 Oct                         ›
[ ⚡ Charging       |  ↘ Discharging ]

┌ Charged ──────────┐  ┌ Value ──────────┐
│ 0.298 kWh         │  │ ₱3.87           │
└───────────────────┘  └─────────────────┘
┌ Avg W ──── Peak W ──── Duration ───────┐
│ 137       171         2h 10m           │
└─────────────────────────────────────────┘

┌ Power trend ────────────────────────────┐
│ [ Watts ] [ Amps ] [ SOC ]              │
│ 8 AM     interactive line     3 PM      │
│ 10:42 AM · 137 W                        │
└─────────────────────────────────────────┘

⌄ Periods       ⌄ Battery details
⌄ More insights
```

The sample numbers in this wireframe illustrate placement only. Actual values always come from the selected day's recorded telemetry. For Discharging, use `Used`, `Saved`, and the amber accent. Keep full labels available to accessibility services even where the visible UI uses `Avg W`. The energy and peso cards should be the clearest elements on the page; supporting statistics are intentionally quieter.

## Chart and interaction

- Default to Watts when entering the page or changing direction. Keep Watts, Amps, and SOC visible as compact controls. Chart values use absolute watts/amps for the selected direction; SOC uses 0–100%.
- Position points by timestamp, not by array index. Use the selected direction's first-to-last recorded point as the visible horizontal range with a small time margin, so an 8 AM–3 PM recording fills the plot rather than being compressed into a midnight-to-midnight day. Keep intervening idle, opposite-direction, and missing periods at their true proportional widths. Use short local-time ticks; show the full date only in the date selector or selected-point readout. Preserve a fixed 0–100% SOC scale; choose a rounded upper bound for W/A.
- Break the line at every existing `gapBefore` marker. During bounded downsampling, retain the first/last points, both sides of each break, and local high/low points where practical; do not let sampling connect across a missing or opposite-direction period or erase a meaningful spike. The plotted line reflects recorded points only.
- Use a clear direction-colored 2–3 dp line, subtle grid, and small point/crosshair while inspecting. An optional very faint area tint may add depth, but it must not obscure gaps or labels. Avoid gradients or animation that make readings hard to judge.
- Tap or drag inside the plot to select the nearest point **by timestamp/x position**. Show a stable, compact readout below the plot with local time and the selected metric, such as `10:42 AM · 137 W`; include the other two readings in a second short line or accessible description. Keep the readout within the card so it does not cover the line. If the nearest point is more than 24 dp horizontally from the touch, clear the selection; this prevents a touch in a long gap from snapping to a distant reading. Clear selection when day, direction, or metric changes.
- Keep the entire plot accessible: metric controls have labels and selected state; the chart has a concise spoken summary (metric, range, peak, and gap count); selected points expose time and W/A/SOC. Touch targets meet Android accessibility sizing.
- With fewer than two points, show a short chart empty state (`Not enough readings to draw a trend`) rather than an empty graph or a long explanation.

## Copy, estimates, and empty states

Visible copy should be short. Put a single information icon near the overview amount or trend title; its dialog explains: `Value` = charged kWh × current tariff, assuming solar charging; `Saved` = discharged kWh × current tariff; the BMS cannot confirm charging source or actual bill savings; missing intervals are excluded. The explanation is available on both tabs. Retain the existing discharge-only ROI basis in More insights.

Use Philippine peso formatting (`₱3.87`). The monetary amount uses the current tariff setting; if it is missing, show `Set tariff` in the peso card and make that card open Settings. For a direction with **no valid measured interval**, show `N/A` for kWh, peso amount, average, peak, and duration; do not present a fabricated `0.000 kWh` or `₱0.00`. If there is a valid measured interval whose computed amount rounds to zero, show the measured zero normally. A day with no samples shows `No charging recorded` or `No discharging recorded` in the energy card and a short chart empty state; the rest of the page remains navigable. A day with samples but no complete valid interval uses `Need more readings` so isolated points are not mistaken for measured energy.

Keep diagnostics honest: coverage and missing time remain accessible in Battery details; the information dialog explains gaps without a repeated paragraph under every chart. If a selected date has only the opposite direction, that tab shows its empty state while the other tab shows its recorded values.

## Implementation boundaries and verification

- Make the Analytics light scheme local to `AnalyticsScreen`; do not replace the app-wide dark theme. The local theme must cover its top bar, dialogs, controls, report cards, and expanded content. Coordinate edge-to-edge status-bar icon appearance with the active destination, restoring the dark-page setting on exit. The dark navigation bar remains the shared app component.
- Reuse `DirectionalAnalytics`, `EnergyPreferences`, `SolarAnalyticsCard`, and existing Room data. No BLE writes, database migration, telemetry recalculation, or deletion of historical recordings is part of this redesign.
- Use the configured reporting time zone consistently for selected-day boundaries, date labels, chart ticks, and point readouts. This avoids a mismatch between Analytics and the solar reports.
- Pass the existing Settings navigation action into Analytics so the `Set tariff` card opens the current Settings page. No new settings screen is needed.
- Keep Compose state scoped by selected day/direction/metric so stale tooltips or expansion state do not appear on another view. Changing day or direction scrolls to the top of the daily overview; changing only the chart metric keeps the page's scroll position. Collapsed sections start closed when first entering Analytics.
- Validate on the connected Poco X3 NFC in both tabs with a recorded day and an empty day. Check portrait layout, large font, dark/light status-bar transitions, selected/deselected controls, collapsed content, chart touch accuracy, gaps, missing tariff, and screen-reader labels. Capture and inspect both tabs on-device, including a graph with real data. Run `testDebugUnitTest`, `lintDebug`, and `assembleDebug` after implementation. Add focused tests for new chart time-position/gap/downsampling logic; existing analytics tests must continue to pass.

The next implementation pass should complete and inspect the Charging tab, then apply the same component system to Discharging, and finally place the long report behind More insights. Update `IMPLEMENTATION_STATUS.md` with the result and note any device-only checks left open.
