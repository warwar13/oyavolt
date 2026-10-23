# Solar energy metrics and acceptance rules

## Raw readings

Use timestamped BMS pack voltage `V` and signed current `A`; power `W = V × A`. Positive power means energy into the battery, negative power means energy out of the battery. Present kWh as Wh / 1,000. Values refer to the **battery DC terminals**, not inverter AC output or household consumption.

The optional rated-storage kWh setting is descriptive only. It does not change measured energy or financial calculations.

Typed recordings use the same signed raw values: Charging reports battery-side harvest and Discharging reports battery-side delivery to the load. The BMS cannot measure direct solar-to-load power or distinguish solar from wall charging.

## Valid intervals

Order readings by timestamp and stable row ID. An interval is valid only if both samples are recorded, belong to the same recording session, their timestamps increase by at most 30 seconds, and the later reading is not marked as the first reading after a disconnect. Never integrate imported capacity-test rows into solar totals. Use signed trapezoidal integration, applying `max(powerW, 0)` to charge and `max(-powerW, 0)` to discharge at each endpoint. Allocate any interval crossing a local hour/day/month or solar-start boundary proportionally by elapsed milliseconds. Count observed milliseconds only for valid clipped intervals. Do not extrapolate through missing time, an unrecorded first/last edge, or a stopped session.

Typed recordings keep near-zero current inside the active recording as idle time. Two consecutive valid samples beyond +/-0.15 A in the opposite direction close the current recording and begin a new one. Charging and discharging recordings therefore never overlap. Legacy mixed telemetry is retained as its original generic recording in History, but its raw samples are classified by signed current at read time for the separate Charging and Discharging Analytics tabs; no synthetic typed recording rows are created.

## Periods and coverage

Store one reporting time zone chosen from the device zone on initial setup. Calendar days and months follow that zone, including daylight-saving days of 23 or 25 hours. A selected period starts at the later of its calendar start and the solar-tracking start instant and ends at the earlier of its calendar end and now. Coverage is valid observed milliseconds divided by this elapsed span. A period without eligible readings displays `No data`, with zero observed energy and its unmeasured duration; a fully measured idle period can display `0 kWh`.

## Savings and ROI

`potentialSolarValue = measuredChargingKWh × currentFlatTariffPerKWh` is shown separately as a potential solar value. It is not guaranteed profit because BMS telemetry cannot prove whether charging came from solar or wall power. Charging value and discharge savings must never be combined into the payback/ROI basis.

`estimatedSavings = measuredDischargeKWh × currentFlatTariffPerKWh` in the selected currency. Charging energy is reported separately and never added to savings. This model assumes the discharged energy displaced grid purchase, and that charging was solar; it does not account for wall charging, direct solar loads, inverter loss, tariff schedules, or feed-in. Display the assumption next to money values. Tariff changes revalue every report using the current rate.

`paybackPercent = cumulativeEstimatedSavings / systemCost × 100`.

`netRoiPercent = (cumulativeEstimatedSavings - systemCost) / systemCost × 100`.

`remainingToBreakEven = max(systemCost - cumulativeEstimatedSavings, 0)`.

If rate or positive system cost is missing, show a setup prompt rather than a fabricated money or ROI value. Values above 100% payback remain visible.

## Acceptance examples

- Two valid 5-second readings at 12 V and -10 A contribute 0.0001667 kWh used; at a tariff of 0.30 per kWh the estimate is 0.00005 currency units before display rounding.
- A 60-second gap contributes no energy and zero observed time, even if both endpoint powers are equal.
- A reconnect after 10 seconds also contributes no interval energy.
- An interval crossing midnight is divided across the two local dates; the combined energy equals the unsplit interval.
- A day with no recording is missing data, while a connected and measured idle day has near-zero energy and positive coverage.
- An imported capacity-test CSV can appear in History but contributes nothing to solar totals or ROI.
- Changing the tariff changes the displayed estimates, never the measured kWh.
