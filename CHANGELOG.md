# Changelog

## 2026-10-17

- Added an **optional** cloud service (account + manual backup). Leaving the server
  address empty keeps the app fully local — nothing leaves the device.
  - `server/`: lightweight Node + Express + SQLite backend, one container + one data
    volume, deployable on a 2C/2G box (`Dockerfile`, `docker-compose.yml`).
  - Accounts: email register / login / password reset via email verification codes.
    SMTP credentials (including the provider "authorization code") are supplied by the
    operator through `server/.env` — only `.env.example` is committed.
  - User center: change username, upload / replace / remove avatar (avatar format is
    validated by magic bytes, files are served with `nosniff`).
  - Cloud backup: upload / download / delete a single `.dtk` per account (manual, no
    auto-sync). "Cloud restore" in the ＋ sheet reuses the same preview-and-confirm
    flow as local `.dtk` import.
  - App side: user-configurable server address (auto `http://`, trailing slash
    normalized), per-request JWT, token stored in its own DataStore file.
  - `.gitignore` excludes `.env`, `server/data/`, `*.dtk`, and signing material.

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
