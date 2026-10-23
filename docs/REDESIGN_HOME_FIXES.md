# Home visual fixes — compact telemetry dashboard

Status: implemented 2026-10-18.

## Problem being solved

The previous Home screen put too much information into one tall hero card. The SOC gauge, cells, temperature, electrical values, and journal were visually competing, so the journal was below the fold and the card hierarchy felt inconsistent. The redesign keeps the dark OyaVolt theme while making the first screen easier to scan.

## Design direction

- Keep the near-black OyaVolt canvas, lime primary, teal live accents, amber discharge accent, and red safety/stop accent.
- Use one spacing rhythm: 14–16 dp page/card padding, 10–12 dp section gaps, 14–22 dp corners.
- Use Material typography consistently: `titleMedium` for card headings, `labelMedium` for labels, and `titleSmall`/`headlineSmall` for values.
- Use icons as quiet anchors for state, power, battery health, voltage, and temperature. Icons supplement labels; they never carry meaning alone.
- Avoid nested cards with competing borders. An outer journal card owns the section; Today and This month use low-contrast surfaces inside it.

## Home hierarchy

1. Compact app bar: OyaVolt, connection, settings, and icon-only monitoring action.
2. Live hero: state chip, animated directional path, a smaller SOC gauge beside Watts/Amps/ETA, and a compact safety or stale-data banner.
3. Energy journal: Today’s Charged/Used metrics and This month totals. Keep kWh first and the peso estimate short.
4. Battery essentials: one consistent 2×2 grid for pack voltage, remaining Ah, SOH, and maximum temperature.
5. Cell balance: a separate card for C1–C4 and spread, so cell detail does not make the live hero excessively tall.
6. Temperature: a matching sensor grid with equal tile heights and value typography.
7. Technical details: collapsed diagnostics and raw protection information.

The hero must expose live direction and current power at a glance, but it should not duplicate the same pack/temperature/cell values in three places. The first portrait viewport should reach the energy journal after a normal scroll.

## Directional animation

The flow strip is deliberately decorative, not a calibrated power visualization. While a fresh connected sample is charging or discharging, five small particles travel in the signed-current direction. Each particle has a soft halo and the strip includes subtle directional chevrons. The animation loops at a constant gentle speed, independent of Watts, so the numeric power remains the measurement.

The path is stationary for idle, stale, disconnected, historical, or failed states. The existing freshness threshold and automatic recording mode remain the source of truth. Accessibility exposes one state description and never announces individual particles.

## Data and safety invariants

- Positive current is charging; negative current is discharging.
- The BMS does not prove solar versus wall charging, so Home uses battery-side wording and never claims a live source.
- Today/month totals, tariff calculations, gap handling, and `N/A` behavior reuse existing analytics.
- Cell safety warnings and BMS protection details remain visible and take priority over decoration.
- No BLE protocol, recording, persistence, or reset behavior changes.

## Acceptance checks

- Home remains dark and readable on the Poco X3 NFC.
- Hero height is materially smaller and journal content is reachable without excessive scrolling.
- Charging and discharging motion is obvious but calm; idle and stale samples never move.
- Journal, essentials, cells, and temperature have clear card boundaries and consistent label/value sizing.
- Unit tests, lint, debug build, APK install, launch, and logcat crash checks pass.
