# Readable power graphs

Status: implemented. This document records the chart behavior used in Analytics and History.

## Why this changed

The earlier Analytics chart could draw up to 1,200 five-second readings across the Poco's phone-width plot. Brief dips and ordinary fluctuations became a dense zigzag. History's recording chart skipped every Nth reading when it had too many points, which could omit a gap or a short event; it also stretched its smallest and largest readings across the entire chart height. The general History graph used equal horizontal spacing for SQL time buckets even when there were missing periods.

The default presentation is now a smoothed time-based line. It is meant to answer: when was charging or discharging steady, and when did it meaningfully rise or fall? The graph remains an inspection tool; stored observations and Wh/Ah/savings calculations do not depend on this smoothing.

## Display data and safety rules

- The chart reducer starts from existing saved readings or SQL time-bucket summaries. It keeps their actual timestamps and divides the visible span into about 48 display intervals, with a minimum interval of one minute. For runs shorter than two minutes, it displays the individual readings.
- Each displayed point is the sample-count-weighted average of readings in its interval. It also retains the lowest and highest original values, source count, interval time bounds, and exact saved readings where the source is raw telemetry. These values are only for the display and tap readout.
- A `gapBefore` marker or a separation beyond the existing 30-second integration threshold starts a new line segment. Analytics also breaks at idle and opposite-direction readings. No line or average crosses these boundaries.
- The general History graph uses an SQL projection with bucket start/end time, count, low/high values, and a gap count. A source bucket containing an internal gap is omitted from the visual average and forces a break before the next valid bucket. Empty source buckets also create a break. This conservatively avoids combining readings from both sides of missing data.
- Fixed-stride selection has been removed from directional and recording charts. All original saved readings remain available for the display reducer. No database entity, migration, BLE query, recording boundary, or energy integration was changed.

## Appearance and interaction

- Charging uses the existing neon-lime accent; Discharging uses amber. The dark card, subdued grid, and sparse time/scale labels match the app's current dark theme.
- Watts and Amps include zero in the vertical scale, so small changes cannot consume the entire plot height. SOC uses a fixed 0–100% range. Voltage and temperature use a padded range around their displayed averages. History's signed general power/current values can appear on either side of zero.
- The horizontal position is based on time, not list index. Analytics spans the first to last matching-direction reading; a History recording spans its first to last sample; the general History graph spans the selected date range.
- Tap or drag selects the nearest displayed interval when touching a connected line or coming within 24 dp of an isolated point. The readout shows local time, average, low, high, source count, and the saved reading nearest the touch time when one is available. Touching a long gap clears the selection. Horizontal drags inspect the line; vertical swipes continue scrolling the page. Changing metric clears the old selection.
- Isolated valid intervals appear as small dots. Fewer than two display points show a short empty state. The chart exposes its range and break count to accessibility services.
- An expanded History recording now appears directly below its selected card so the graph is easy to find even when many recordings exist.

## Verification

Unit tests cover noisy five-second readings, a sustained drop, a one-reading dip, explicit and elapsed-time gaps, source low/high/count preservation, and exact-reading availability. Run `testDebugUnitTest`, `lintDebug`, and `assembleDebug` after chart changes. Inspect Charging, Discharging, an expanded recording, and the general History telemetry graph on the Poco with real stored data; check tap selection and that no visual line crosses a missing period.
