# OyaVolt Home polish — single-layer hierarchy and visual consistency

Status: implemented 2026-10-19. See `IMPLEMENTATION_STATUS.md` for validation and remaining device-only checks.

Date reviewed: 2026-10-18  
Device reviewed: Poco X3 NFC, 1080 × 2400, density 440, default font scale  
Scope: Home only. Analytics, History, Alerts, Settings, BLE, recording, database, and calculations are unchanged.

## 1. Objective

Polish the current Home screen without replacing the battery-flow concept or its animation. The finished screen should feel like one intentional dashboard instead of several generations of cards placed together.

The priorities are:

1. preserve the animated Solar → Battery → Output hero;
2. remove the visible “card inside card inside card” effect;
3. make type sizes, weights, alignment, and number formatting consistent;
4. reduce repeated information and excessive green accents;
5. make the Energy journal faster to understand in one glance;
6. keep cell safety and stale-data states honest and prominent;
7. make the lower technical sections visually related to the hero rather than a separate design system.

This is a presentation and Home-state task. It must not alter telemetry integration, energy totals, recording boundaries, tariff math, BLE commands, or stored data.

## 2. Current screen review

### What is already successful and must be preserved

- The moving amber discharge path is immediately understandable and visually distinctive.
- Solar, Battery, and Output are correctly centered around the battery rather than an invented grid or inverter.
- Charging and discharging use different colors, and the inactive path is visually quiet.
- SOC is readable at a glance and no longer collides with the flow path.
- Power, signed current, and ETA are all visible before scrolling.
- Today and month totals use the same recorded battery-side data model.
- Cell voltage, temperature, protection, and stale-data information remain available below the summary.
- The dark canvas, lime charging color, amber discharge color, and red warning/stop color fit the product.

### Findings from the Poco review

#### P0 — hierarchy and duplicate containers

- Energy journal currently has an outer card, a rounded Today container, two rounded Today metrics, a rounded month container, and two rounded month metrics. The many nested silhouettes make the section visually heavy.
- Battery essentials has an outer card plus four equal rounded tiles. Temperature repeats the same pattern with up to six tiles.
- The hero appropriately owns a main card, but its three bottom metrics also look like three more cards. Together with the center battery node, the upper screen becomes shape-heavy.
- Section-header placement is inconsistent: Battery essentials keeps its heading inside its card, while Cell balance and Temperature place headings outside their cards.

#### P0 — typography hierarchy

- Similar concepts use different sizes: Today values are visually larger than month values even though both represent kWh.
- Labels, subtitles, meta text, and secondary values sometimes differ only by color rather than a deliberate size/weight hierarchy.
- Most telemetry values are lime, so live values, healthy status, icons, and positive financial values compete for attention.
- The top-right `100% SOH` repeats the Health metric in Battery essentials.
- `kWh` appears in the Today heading and again in each value.
- Numeric columns do not explicitly use tabular figures, so changing values can subtly shift alignment.

#### P1 — journal clarity

- Today shows `N/A` for an unobserved charging direction while This month can show `0.000 kWh`, even when there was no qualifying charge recording. The difference is easy to read as contradictory.
- `Last reading …` and `Battery-side estimate` are separate, low-contrast footer lines. They consume space without creating a clear interaction or hierarchy.
- Coverage is useful but unexplained. `45.6% coverage` can be mistaken for battery SOC or solar efficiency.
- Charged/Value and Used/Saved are correct separate concepts, but their treatments are inconsistent between Today and month.

#### P1 — lower sections

- The cell bars exaggerate tiny differences because their length is normalized to the current minimum and maximum. A 3 mV spread looks visually dramatic even though it is healthy.
- Temperature repeats a thermometer icon in the section header and every sensor tile. This adds noise without new meaning.
- Essentials values are all green even when they are neutral measurements. Green should indicate active charging or healthy status, not every number.
- Cell, temperature, and technical headers do not share one exact layout, icon size, or vertical rhythm.

#### P1 — app bar and controls

- Bluetooth status, Settings, Stop, and More use different visual container treatments and apparent sizes.
- Stop is correctly prominent, but the surrounding controls need consistent 48 dp touch targets and optical alignment.
- The Home content and app bar are visually close in weight; the app bar can be slightly quieter so the live hero remains the focus.

## 3. Design direction

Use a **single owner surface** for each section:

- the hero may have one outer surface and one meaningful battery node;
- every other section gets one content card;
- subdivisions inside a section use spacing, dividers, tint blocks, or a subtle accent rail—not another set of floating cards;
- avoid a rounded background directly inside another rounded background unless it is an interactive control or a semantic diagram node.

The visual priority should be:

1. live direction and SOC;
2. current power, current, and ETA;
3. today’s energy and money;
4. month energy and money;
5. battery essentials and safety;
6. detailed cells, temperatures, and technical information.

## 4. Target Home structure

```text
OyaVolt                         BLE   Settings   Stop   More

┌ Live battery hero ─────────────────────────────────────┐
│ [Discharging]                                      [i] │
│                                                      │
│   Solar ───────── [ BATTERY / 80% ] ──────── Output  │
│   Assumed                 animated active path         │
│                                                      │
│   Power          Current             To 20%           │
│   110 W          −8.34 A             ~7h 10m          │
└──────────────────────────────────────────────────────┘

Energy journal                                      [i]
┌──────────────────────────────────────────────────────┐
│ TODAY                                                │
│ Charged                    Used                       │
│ N/A                        0.189 kWh                  │
│ Value N/A                  Saved ₱2.46                │
│ Avg N/A                    Avg 92.9 W                 │
│ ──────────────────────────────────────────────────── │
│ THIS MONTH                                           │
│ Charged                    Used                       │
│ N/A                        0.190 kWh                  │
│ Value N/A                  Saved ₱2.47                │
│ Recorded 45.6% of tracked time                   [i] │
└──────────────────────────────────────────────────────┘

Battery essentials
┌──────────────────────────────────────────────────────┐
│ Pack voltage  13.23 V   │ Remaining       79.70 Ah   │
│ Health         100%     │ Max temp            32 °C  │
└──────────────────────────────────────────────────────┘

Cell balance
┌ one truthful comparison card + summary row ─────────┐
└──────────────────────────────────────────────────────┘

Temperatures
┌ one divided sensor grid ─────────────────────────────┐
└──────────────────────────────────────────────────────┘

┌ Technical details                                ⌄ ┐
└──────────────────────────────────────────────────────┘
```

This wireframe defines hierarchy, not exact copy. It intentionally removes the second layer of rounded Today/month/metric cards.

## 5. Typography specification

Use one Home type scale. Prefer Material typography tokens configured centrally; use explicit sizes only where the SOC display requires it.

| Role | Recommended style | Weight | Color |
| --- | --- | --- | --- |
| App title | 28 sp / 34 sp | Bold | primary text |
| Section title | 20 sp / 26 sp | Semibold | primary text |
| Section subtitle | 14 sp / 20 sp | Regular | secondary text |
| Hero state | 14 sp / 20 sp | Semibold | direction accent |
| Hero SOC | 32 sp / 36 sp | Bold | SOC severity color |
| Node name | 14 sp / 20 sp | Semibold | primary text |
| Node qualifier | 12 sp / 16 sp | Medium | secondary text |
| Metric label | 13 sp / 18 sp | Medium | secondary text |
| Primary metric | 20 sp / 25 sp | Bold | primary text or active accent |
| Secondary metric | 16 sp / 22 sp | Semibold | primary text |
| Meta/help text | 12 sp / 16 sp | Regular | tertiary text |

Rules:

- Apply tabular numerals to SOC, W, A, V, Ah, °C, kWh, mV, percentages, time estimates, and peso amounts.
- Align comparable numbers to the same baseline.
- Keep units attached to their values with a non-breaking space where supported.
- Use sentence case for labels. Reserve uppercase for short structural markers such as `TODAY`, `THIS MONTH`, and `BATTERY`.
- Do not use font size alone to signal warnings; pair warning color with an icon or short state label.
- Support Android font scaling. Values may reduce to one compact precision step before ellipsizing, but must not silently clip units.

## 6. Spacing, shapes, and surfaces

### Page rhythm

- Horizontal page padding: 16 dp.
- Gap from app bar to hero: 12 dp.
- Gap between major sections: 16 dp.
- Section title to content card: 8 dp.
- Outer card padding: 16 dp.
- Internal row gap: 10–12 dp.
- Minimum touch target: 48 × 48 dp.
- Bottom content clearance above navigation: at least 24 dp after the final section.

### Shapes

- Hero radius: 28 dp.
- Standard section card radius: 20 dp.
- State chip radius: full pill.
- Information and icon containers: 12–14 dp or circle, used consistently.
- Do not give every metric its own 14–16 dp rounded rectangle.

### Elevation and borders

- Use tonal separation rather than several shadows.
- Hero: one subtle direction-colored outline, maximum 1 dp, with a restrained gradient.
- Standard section cards: no colored outline in normal state.
- Battery node: keep its outline because it is part of the energy diagram, not a nested data card.
- Warning cards may use a colored border or stronger tonal fill.

## 7. Color discipline

- Near-black remains the page background.
- White/off-white is the default color for numeric telemetry.
- Lime is reserved for charging, healthy status, selected/live controls, and positive charging value.
- Amber is reserved for discharging, used energy, and discharge savings.
- Red is reserved for critical SOC, BMS warnings, dangerous temperatures/cell conditions, and Stop.
- Secondary labels use the same neutral gray throughout Home.
- Avoid green-tinting every Essentials and Temperature value. A neutral measurement stays white until its state needs emphasis.
- Maintain at least WCAG AA contrast for text against card and tint surfaces.

## 8. Component specifications

### 8.1 App bar

- Keep `OyaVolt` and all existing actions.
- Give Bluetooth, Settings, Stop, and More the same 48 dp interaction box.
- Use one 36–40 dp visual container convention for status/action circles.
- Keep Stop filled red because recording/monitoring state must remain obvious.
- Use a quiet tonal Bluetooth indicator. Do not let its background compete with Stop.
- Align all icons optically to the same center and use one 22–24 dp icon size.
- Preserve accessibility descriptions and distinguish connected, reconnecting, and disconnected without relying only on color.

### 8.2 Live battery hero

Preserve the existing curved paths, particles, and receiver glow. This is the strongest part of Home.

Polish changes for later implementation:

- Replace the top-right SOH duplicate with a 48 dp information action. Its sheet/dialog explains `Solar (assumed)`, `Battery output`, signed current, and that animations represent direction rather than calibrated speed.
- Keep the state chip short: `Charging`, `Discharging`, `Idle`, or `Last recorded`.
- Keep `Solar / Assumed` and `Output / Measured`; do not add longer explanatory text to the diagram.
- Keep the battery node solid and centered. SOC remains the only large number inside it.
- Reduce decorative glow opacity if it touches node labels or makes the endpoint appear larger than the battery.
- Ensure active paths terminate at node edges and do not visually pass behind labels.
- In stale/disconnected/historical states, stop particles immediately and neutralize both paths. If useful, preserve the previous direction only as `Last: Discharging` in accessible/help text, not as an active amber path.

#### Hero metrics rail

Replace the three rounded metric tiles with one flat rail inside the hero:

- three equal columns;
- 1 dp low-contrast vertical dividers between columns;
- label above value;
- one small leading icon per label, all at the same size;
- no individual tile backgrounds;
- Power and Current use the active direction accent;
- ETA is neutral unless unavailable or warning-related;
- use `To full` while charging and `To 20%` while discharging;
- show `—` rather than removing the ETA column, so the rail does not jump when state changes.

The rail should remain at least 64 dp high and should not reflow when values update.

#### Warning placement

- Cell/protection warnings remain directly below the metrics rail inside the hero.
- Warning copy is limited to two lines, followed by an optional `Details` action.
- Safety red overrides direction color for the warning itself and critical SOC.
- The hero may grow for a genuine warning; compactness must never hide safety information.

### 8.3 Energy journal

Use a section heading outside one journal card. The heading row contains:

- energy icon;
- `Energy journal` title;
- optional info action;
- no permanent `Today and this month` subtitle if the two period labels are already visible.

Inside the one card:

- Today is the first flat block.
- A thin horizontal divider separates Today from This month.
- Each period uses two equal columns: Charged and Used.
- A subtle vertical divider separates direction columns.
- Do not wrap each metric in another rounded card.
- Use a 3 dp accent rail or small direction icon to identify green Charged and amber Used.
- Today may include average power; month should not add an empty average row.
- Keep `Value` under Charged and `Saved` under Used. Never combine them.

#### Journal data formatting

- Under 1 kWh: show three decimals, such as `0.189 kWh`.
- 1–99.99 kWh: show two decimals.
- 100 kWh and above: show one decimal.
- Peso amounts always show the peso symbol and two decimals.
- Average power: one decimal below 1,000 W; compact kW only when it materially improves fit.
- Use `N/A` when that direction has no qualifying recording.
- Show `0.000 kWh` only for an explicitly observed zero, not merely because the opposite direction has data.
- Preserve `Set tariff` when price configuration is unavailable.

#### Journal footer

Replace the two loose footer lines with one compact metadata row:

`Recorded 45.6% of tracked time   ⓘ`

- The info action explains coverage, tracking start, gaps, battery-side DC estimation, and unmeasured direct household solar loads.
- While live, omit `Last reading` from the journal because freshness belongs to the hero.
- While stale or historical, show the timestamp once in the hero and do not repeat it here.
- If Today and month totals are temporarily based on different refresh moments, update them together or show one shared `Updated` timestamp.

### 8.4 Battery essentials

- Move the section heading outside the content card so it matches Cell balance and Temperature.
- Shorten the subtitle to `Pack health at a glance`, or omit it if spacing is tight.
- Use one 2 × 2 grid inside a single card.
- Separate cells using subtle horizontal/vertical dividers instead of four rounded tile backgrounds.
- Remove repeated background tints.
- Default values are white; icons use lime when healthy.
- Rename `Temperature` to `Max temp` because the shown number is the maximum available sensor value.
- Keep: Pack voltage, Remaining, Health, Max temp.
- Use consistent two-decimal V/Ah precision and integer percentage/temperature precision.

### 8.5 Cell balance

- Keep the heading outside and retain the healthy/warning/critical summary.
- Do not normalize bars to the current min/max in a way that turns a 3 mV spread into dramatically different lengths.
- Preferred presentation: each row shows cell label, exact voltage, and signed deviation from pack-cell average; a small centered deviation indicator uses a stable threshold scale such as ±25 mV.
- Alternative: use a fixed safe voltage scale, but only if it remains legible on the Poco.
- The summary row remains Lowest, Highest, Delta, using one divided strip rather than three rounded cards.
- Healthy delta uses neutral/lime; caution uses amber; unsafe uses red. Existing BMS warnings remain more important than visual balance status.
- Preserve three-decimal cell voltage and integer mV delta.

### 8.6 Temperatures

- Keep the heading outside one content card.
- Use a divided two-column sensor grid with equal row heights.
- The section header owns the thermometer icon; remove repeated thermometer icons from every healthy sensor cell.
- Each cell shows sensor name above one aligned temperature value.
- Values are white when normal, amber at the existing caution threshold, and red at the existing critical threshold.
- If only one cell exists in the final row, it uses half width and does not stretch into a visually different component.
- Keep all available MOS, Ambient, and Probe readings; this is a density/style change only.

### 8.7 Technical details

- Keep collapsed by default.
- Match the standard section-card radius and padding.
- Use a 20 dp info icon and 20 dp chevron within 48 dp touch targets.
- Keep title and subtitle aligned to the same baseline/spacing as other section headers.
- Expanded content remains diagnostic and may be denser than the main Home screen.

## 9. Home display-state rules

The visual mapping must remain deterministic and testable.

| State | State chip | Active path | Motion | Primary accent | Freshness |
| --- | --- | --- | --- | --- | --- |
| Fresh charging | Charging | Solar → Battery | lime particles | lime | live |
| Fresh discharging | Discharging | Battery → Output | amber particles | amber | live |
| Fresh idle | Idle | neither | stopped | neutral | live |
| Reconnecting with fresh-enough sample | Reconnecting | neither | stopped | neutral/amber | explicit |
| Stale/disconnected | Last recorded | neither | stopped | gray | timestamp shown |
| Monitoring stopped | Last recorded | neither | stopped | gray | timestamp shown |
| Critical SOC or BMS warning | direction remains truthful | measured path only | may continue unless stale | red safety takes priority | explicit |
| Reduced motion | truthful direction | measured path highlighted | no moving particles | direction color | unchanged |

Positive current remains charging and negative current remains discharging. Never display both paths as measured simultaneously.

## 10. Motion and performance

- Preserve the current curved-path animation timing unless device testing finds it visually rushed.
- Particles should move at constant visual speed; do not imply that animation speed measures Watts.
- Keep three particles and the soft receiving glow unless reduced-motion is enabled.
- When inactive, do not merely hide particles while an infinite transition continues unnecessarily; stop or avoid the active animation workload.
- Respect Android animator-duration/reduced-motion behavior.
- Numbers remain stationary and update without scale/bounce animations.
- Avoid recomposing the entire Home tree for particle frames; isolate Canvas animation state from journal and technical cards.

## 11. Accessibility and resilience

- Every action keeps a 48 dp target and a meaningful content description.
- Combine the flow diagram into one semantic description, for example: `Discharging, battery 80 percent, 110 watts to battery output`.
- Decorative particles, dividers, and repeated icons are excluded from accessibility traversal.
- Do not encode direction, warning severity, or connection state by color alone.
- At 1.3× and 1.5× font scale, metric labels may wrap to two lines, but values and units remain intact.
- At large font scale, switch paired journal/essential columns to a vertical layout if needed instead of shrinking text below the specified scale.
- Verify long values such as `−123.45 A`, `999.999 kWh`, `₱12,345.67`, and multi-hour ETA.
- Show `—` for temporarily unavailable live telemetry and `N/A` for unavailable calculated/reporting data; do not use them interchangeably.

## 12. Future implementation map

No implementation is performed by this document. A later coding pass should focus on these files:

| File | Planned responsibility |
| --- | --- |
| `MainScreen.kt` | App bar sizing, hero metadata/info action, flat metric rail, shared section headers, Essentials grid, truthful cell comparison, Temperature grid, technical card polish, state/motion behavior. |
| `SolarEnergyCards.kt` | Single-layer Energy journal, unified Today/month typography and direction columns, precision rules, N/A semantics, coverage info, shared refresh metadata. |
| Home display-state tests | Charging, discharging, idle, reconnecting, stale, stopped, critical warning, and reduced-motion mappings. |
| Home Compose/UI tests | Content hierarchy, labels, large-font stacking, unavailable values, warning visibility, and stable semantics. |

Prefer extracting small reusable Home primitives rather than creating a general design-system rewrite:

- `HomeSectionHeader`
- `DividedMetricGrid`
- `DirectionMetricColumn`
- `HomeMetaRow`
- `HeroMetricRail`

Do not rewrite working analytics, repository, Room, BLE, or notification modules for this polish.

## 13. Implementation order for the later coding pass

1. Add or update pure Home display-state and formatting tests.
2. Establish shared Home typography, section header, divider, padding, and value styles.
3. Flatten the hero metric rail while preserving the animation unchanged.
4. Flatten the Energy journal and reconcile direction-specific `N/A`/zero display semantics.
5. Convert Essentials and Temperature to divided single-surface grids.
6. Replace misleading relative cell bars with a stable comparison scale.
7. Standardize app-bar and Technical details controls.
8. Verify all live, stale, warning, and large-font states on the Poco.
9. Run unit tests, lint, and debug assembly; then install and inspect the final APK.
10. Update `IMPLEMENTATION_STATUS.md` only after code and device verification are complete.

## 14. Verification checklist for implementation

### Visual

- The animation remains the most expressive element and is not weakened by the polish.
- Only one outer silhouette is visible per section.
- Today and This month use identical value sizing and column alignment.
- Energy journal, Essentials, Cell balance, and Temperature share one heading pattern.
- Neutral telemetry is white; lime/amber/red have deliberate meanings.
- No section appears to be a separate theme or component library.
- The first portrait viewport shows the full hero and a useful portion of Today without crowding.

### State and data

- Charging, discharging, idle, stale, disconnected, stopped, and warning states display correctly.
- `N/A`, `—`, and measured zero remain semantically distinct.
- Home Today/month values still agree with Analytics for the same reporting boundary.
- Value and Saved remain separate; tariff math is unchanged.
- No interpolation is introduced across idle periods, opposite flow, session boundaries, or gaps.
- Cell bars/indicators do not exaggerate a healthy small delta.

### Device and accessibility

- Inspect at default, 1.3×, and 1.5× font scale on the Poco.
- Inspect while charging, discharging, idle, stale, and disconnected.
- Exercise the hero and coverage information actions.
- Verify TalkBack order from app bar through Technical details.
- Verify reduced motion and battery-saver presentation.
- Confirm smooth scrolling while the flow animation runs.
- Run `testDebugUnitTest`, `lintDebug`, and `assembleDebug`, install on the Poco, and scan the app process for crashes.

## 15. Acceptance criteria

The Home polish is complete only when:

- the current energy-flow animation is preserved and remains smooth;
- the nested/double-card appearance is removed from Journal, Essentials, and Temperature;
- all major section headings, labels, values, units, and metadata follow the documented type scale;
- duplicate SOH and freshness information is removed;
- the journal can be understood in one glance without explanatory paragraphs;
- cell balance visualization is proportional and does not dramatize a healthy 3 mV spread;
- warnings remain more prominent than decorative styling;
- large text does not clip important values;
- all existing telemetry, recording, energy, and safety behavior remains unchanged;
- automated checks pass and the result is visually inspected on the connected Poco.

## 16. Explicit decisions

- Keep the dark neon theme.
- Keep the current battery-centered curved animation.
- Keep Solar explicitly marked as assumed.
- Keep Output explicitly battery-side/measured.
- Keep Today and This month on Home.
- Keep detailed cells and every available temperature sensor on Home, below the summary.
- Flatten nested containers rather than removing useful information.
- Use tooltips/info sheets for explanations instead of permanent long text.
- Do not change analytics, history, BLE, recording, persistence, tariffs, or energy calculations in this milestone.
