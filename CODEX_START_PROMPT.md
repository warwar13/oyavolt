# Codex start prompt

Use `AGENTS.md` as the controlling project instruction.

Before starting, follow the **Model / reasoning policy** in `AGENTS.md`. If per-task/subagent model routing is unavailable, continue with the current parent model rather than stopping.

First inspect only the relevant files in `reference-web/`, especially the project handoff, protocol/parser code, and protocol tests. Extract the exact BLE UUIDs, command allowlist, packet/checksum rules, parsing offsets/types, signed-current convention, and any golden frames.

Then implement **M1 only**:
1. normalize the scaffold to application id + namespace `com.warwar13.gentai` and `minSdk 26` (unless a concrete compatibility reason requires another minSdk), then confirm the generated Android project builds;
2. create the Kotlin read-only Gentai protocol/frame parser;
3. port the golden protocol tests;
4. enforce the allowlist so no non-telemetry command can reach BLE code;
5. run unit tests and `assembleDebug`;
6. create/update a concise `IMPLEMENTATION_STATUS.md`.

Do not start M2 until M1 passes. Keep your response concise: files changed, test/build result, and any real blocker.
