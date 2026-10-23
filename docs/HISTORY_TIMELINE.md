# History timeline implementation

Status: implemented in the Android History screen. This document records the behavior and data rules for future changes.

## Purpose

History should answer three questions quickly: how much energy entered and left the battery during a period, when charging and discharging happened, and which recording explains an interesting part of the graph. The default page therefore shows a period summary, one signed power graph, and compact day groups. It does not open a recording automatically.

## Main screen

1. **Date controls:** Today, 7 days, This month, or a custom inclusive date range. Presets start at local calendar boundaries in the configured reporting timezone. Today and the current month end at the present time; a custom range includes the full final local day. Daylight-saving changes use the actual local midnight instants.
2. **Period totals:** charged and used kWh, their configured peso estimates, coverage, days with summaries, and recording count. Energy comes from the existing reporting-zone local-day energy rollups, so a recording spanning midnight does not get counted twice. Zero-record periods can still have imported or legacy energy data.
3. **Signed graph:** positive watts mean charging, negative watts mean discharging. Lime and amber distinguish the two directions, with a visible zero line. All, Charging, and Discharging filters affect the plotted power and the record rows, not the stored data or period totals. Tapping or dragging shows time, average, minimum, maximum, count, and nearby current and SOC.
4. **By day:** newest day first, collapsed by default. Each row shows charged/used energy, coverage, and the number of loaded direction records. Expanding shows a View day graph action and compact recording rows. Opening a recording shows a focused Watts/Amps/SOC detail, with Back returning to the previous list position. A recording that crosses a local midnight appears in each affected day; its card labels its energy as the whole-record total. The daily summary above it is the source for that day's energy.
5. **More history:** the earlier mixed/legacy recording list, capacity test import and cards, technical voltage/current/SOC/cell/temperature graphs, daily summary details, and telemetry deletion are collapsed under one control.

## Data and graph rules

- Raw telemetry, UTC `daily_summaries`, local-day `solar_daily_rollups`, and flow recording entities remain intact. History uses the local-day rollups for its visible day and period energy. The older UTC summaries remain accessible in More history for legacy diagnostics. No database migration or BLE command change is needed for this layout.
- The period chart is aggregated in SQLite to at most about 720 time buckets. Buckets containing a recorded gap, samples from multiple connection sessions, or both charging and discharging samples are omitted from the line. The next valid point begins a new segment; the UI never draws a continuous line across a known missing period. Direction changes also start a new segment.
- The recording detail uses a bounded SQLite aggregate rather than loading an entire recording's raw sample list into Compose. Gapped buckets are omitted and skipped time buckets break the line. Display aggregation does not alter raw samples or energy integration.
- Day groups load 14 calendar days at a time. Each visible day has a count query for its selected direction; expanded days fetch only the latest 50 records at first and can reveal more in 50-row batches. A recording detail is loaded by ID, so records beyond the first page remain accessible. The period recording count comes from a separate count query.
- Coverage divides observed milliseconds in the local-day rollup by actual elapsed time in the selected local period, clipped by the tracking start and present time. A missing rollup is shown as unavailable rather than inferred from a session's duration. The chart does not fabricate power or energy through gaps.

## Validation

- Unit checks cover reporting-zone calendar presets, daylight-saving boundaries, picker-day inclusion, and graph-bucket exclusion for mixed directions and connection gaps.
- Build gates: `testDebugUnitTest`, `lintDebug`, and `assembleDebug`.
- Device acceptance: verify the period graph appears before the day list, days start collapsed, expanding and opening a record works, Back restores the list location, date/direction filters work, and More history preserves import and deletion access. Verify current monitoring continues after installation and note any installation gap in recorded coverage.

## Follow-up limits

The period summary uses whole-day aggregates. A custom range intentionally selects whole local dates; a future time-of-day picker would require interval-specific summary calculation. The chart intentionally drops any bucket with mixed charge/discharge readings because a single average could hide the direction transition. Expanding a very dense day rereads its first N records when Show more records increases N, so a later keyset paging implementation would improve efficiency for extreme cases.
