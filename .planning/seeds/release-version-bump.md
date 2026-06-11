---
name: release-version-bump
description: Bump MARKETING_VERSION and CURRENT_PROJECT_VERSION in xcodeproj to match the milestone number before each release
metadata:
  type: seed
  trigger_condition: before publishing each GitHub release / AltStore update
  planted_date: 2026-06-11
---

## Problem

`MARKETING_VERSION` and `CURRENT_PROJECT_VERSION` in `GooseSwift.xcodeproj` have never been updated — they are stuck at `0.1.0 (1)`. The About screen shows this stale value while AltStore and GitHub releases already show `8.0`.

## Convention to adopt

| Release | MARKETING_VERSION | CURRENT_PROJECT_VERSION |
|---------|-------------------|------------------------|
| v9.0 | `9.0` | `9` |
| v10.0 | `10.0` | `10` |
| … | … | … |

The About screen reads from `CFBundleShortVersionString` (= MARKETING_VERSION) and `CFBundleVersion` (= CURRENT_PROJECT_VERSION), so it will show e.g. `9.0 (9)` after the bump.

## Next action — v9.0 release

Before tagging `v9.0` and uploading the IPA:

1. In `GooseSwift.xcodeproj/project.pbxproj`, update all occurrences of:
   - `MARKETING_VERSION = 0.1.0;` → `MARKETING_VERSION = 9.0;`
   - `CURRENT_PROJECT_VERSION = 1;` → `CURRENT_PROJECT_VERSION = 9;`
2. Commit: `chore: bump version to 9.0 for release`
3. Tag + release as normal

## Files to change

- `GooseSwift.xcodeproj/project.pbxproj` — 4 occurrences of each key (Debug + Release × main target + extension target)
