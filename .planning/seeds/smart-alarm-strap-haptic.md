---
name: smart-alarm-strap-haptic
description: BLE-commanded strap vibration alarm — WHOOP writes wake window schedule to strap; strap fires autonomously via haptic
metadata:
  type: seed
  trigger_condition: when planning v10.0 milestone scope
  planted_date: 2026-06-11
---

## Idea

Implement a smart alarm that writes a wake window schedule to the WHOOP strap via BLE. The strap fires autonomously at the optimal moment within the window using its built-in haptic motor — no phone involvement at wake time.

## What WHOOP has

Discovered via Ghidra (2026-06-11), `WhoopSleepCoach` framework (114+ classes):
- `SmartAlarmStrapService` — writes alarm schedule to strap via a BLE command
- `SmartAlarmManager` + `SmartAlarmTriggerManager` — orchestrate the alarm lifecycle
- `SmartAlarmDiagnosticManager` + `SmartAlarmDiagnosticService` — diagnostics/logging
- `LogHapticsManager` — manages haptic feedback on strap side (distinct from phone UIImpactFeedbackGenerator)
- `WakeWindow` — defines the earliest/latest acceptable wake time range
- `StrapDrivenAlarmSetEventData` — event payload for alarm set confirmation

The strap receives the alarm schedule and fires the haptic vibration **independently** — it does not need the phone to be connected at wake time.

## Why it matters

This is the most significant gap in Goose's sleep feature. Phase 55 implemented `SleepCoachViews` as read-only display (bedtime/wake time from bridge data). The WHOOP killer feature is the strap alarm — the user is woken by the device, not the phone, at the optimal moment in the wake window.

## What needs RE work

The BLE command to write the alarm schedule to the strap is **not yet identified**. This is the blocker:
- The GATT characteristic UUID for alarm scheduling is unknown
- The payload format (wake window start/end timestamps, vibration pattern) needs to be reverse-engineered
- Approach: attach a BLE sniffer during WHOOP app alarm setup, or decompile `SmartAlarmStrapService` methods in Ghidra to identify the command bytes

## Goose current state

- `GooseBLEClient` can already write arbitrary BLE commands (has `sendDebugResearchCommand`)
- Rust core has sleep staging and can compute optimal wake window from staging output
- Missing: the specific BLE command + payload, and the Swift orchestration layer

## Implementation sketch (once GATT command identified)

1. `GooseSmartAlarmManager` — computes wake window from sleep staging result
2. `GooseBLEClient+AlarmCommands.swift` — writes schedule command to strap
3. Confirmation handler — listens for ack from strap
4. UI in Sleep Coach view — set alarm window, show confirmation

## Files to create

- `GooseSwift/GooseSmartAlarmManager.swift`
- `GooseSwift/GooseBLEClient+AlarmCommands.swift`
