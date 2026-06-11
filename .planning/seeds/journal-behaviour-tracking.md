---
name: journal-behaviour-tracking
description: Tagged behaviour logging per recovery cycle — alcohol, caffeine, stress, illness — feeds recovery score coaching
metadata:
  type: seed
  trigger_condition: when planning v10.0 milestone scope
  planted_date: 2026-06-11
---

## Idea

Extend the existing free-text daily journal (Phase 54) with tagged behaviour tracking per recovery cycle, equivalent to WHOOP's `JournalBehaviorTracker` / `JournalTrackedBehavior`.

## What WHOOP has

Discovered via Ghidra (2026-06-11), `WhoopJournal` framework (39+ classes):
- `JournalBehaviorTracker` — tracks tagged behaviours per cycle
- `JournalTrackedBehavior` — individual behaviour entry (type + value)
- `JournalDraftService` — persists draft entries before submission
- `JournalCalendarModule` — browse journal entries by date
- `JournalV2Router` — navigation within journal
- `JournalEntry` — one entry per recovery cycle (not per day)

Behaviour categories inferred from WHOOP's known UX: alcohol (units), caffeine (mg), stress level (1–5), illness, supplements, sleep environment (light, noise, temperature).

## Why it matters

Tagged behaviours feed recovery score interpretation — "recovery was low because of alcohol yesterday" requires structured data, not free text. The coaching layer (`VoWService`, strain coach) uses behaviour history to contextualise scores. Phase 54's free-text journal cannot be queried or correlated with biometric trends.

## Goose current state

Phase 54 (`GooseSwift/CoachJournalViews.swift` or equivalent) added a free-text journal entry with SQLite persistence. No structured tags, no behaviour categories, no calendar navigation.

## What to build

**Minimal version (avoids feature creep):**
1. Fixed set of behaviour tags: alcohol, caffeine, poor sleep environment, stress, illness — matching WHOOP's known categories
2. Per-cycle entry (linked to `external_sleep_sessions.id` or date), not per calendar day
3. SQLite table: `journal_behaviours { cycle_date, behaviour_type, value, notes }`
4. UI: tag picker on Coach tab journal, calendar view to browse history
5. Bridge method to query behaviours for a date range (for future correlation with recovery score)

**Not now:**
- ML correlation between behaviours and recovery score — needs 30+ cycles of data first
- Custom behaviour types — fixed set is sufficient for v1

## Files to touch

- New: `GooseSwift/JournalBehaviourViews.swift`
- Modify: Rust bridge — add `journal.log_behaviour` and `journal.behaviours_for_range` methods
- Modify: SQLite schema — add `journal_behaviours` table
- Modify: Coach tab journal UI (extend Phase 54 work)
