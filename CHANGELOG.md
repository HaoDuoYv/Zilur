# Changelog

## 2026-09-30

- Redesigned the UI/UX layer (P0–P5) without touching ViewModel / Domain / Data / Room schema:
  - Design tokens: semantic colors, opacity layers, corner radii, elevation levels, and a motion system (duration / easing / spring).
  - Component fixes: removed dead code (`NoteColors`, duplicate `TagChip`), fixed dark-mode surface, image error state, and unified pill buttons.
  - Home redesign: search bar on top, compact stats line, segmented view toggle.
  - Editor redesign: focused-card feedback, unified toolbar, review-panel recording state, block insert indicator.
  - Motion alignment + accessibility: tokenized all durations, reduced-motion support, and ≥48dp touch targets.

## 2026-07-08

- Added manually enabled Ebbinghaus review plans.
- Added note-linked TODO reminders.
- Added a reminder center for review and TODO items.
- Added WorkManager-based reminder checks and notification permission fallback.

## v1.0.0 (MVP)

- Initialized the Android app scaffold for ZhiLu.
- Added local-first Room data architecture, repositories, and Compose navigation.
- Added MVP screens for notes, tags, search, settings, camera entry, and trash management.
- Added JSON and Markdown export support.
