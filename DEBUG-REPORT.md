# X Controller — Debug Report (v6.1)

## 1. Original problem
Controller works outside games, but inside games (MOBA 5v5) buttons/keys do nothing.

## 2. Root causes found in the code

| # | Cause | Where | Fix |
|---|---|---|---|
| 1 | **Coordinates from `getResources().getDisplayMetrics()`** — wrong/stale in fullscreen & landscape games | OverlayService.handleKey/moveBy/aimBy | `getCurrentWindowMetrics()` (API 30+) / `getRealMetrics()` |
| 2 | **Rotation change never handled** — after portrait→landscape the mapping points were offset | OverlayService | `onConfigurationChanged()` → overlay reload |
| 3 | **Our own touchable aim pad sat on top of the game** (45–62% of screen) — injected taps landed on OUR window, not the game | OverlayService aim pad | reduced to 38% width, OFF by default, MASTER switch |
| 4 | **Recursion risk** — aim pad click → MOUSE_LEFT mapping → dispatch at same point | OverlayService.handleKey | `dispatching` guard |
| 5 | **Focus stealing by full-screen overlay** made some games pause | panel window | panel is NOT_FOCUSABLE + NOT_TOUCHABLE; only a tiny key window is focusable, and only when MASTER ON |
| 6 | **Settings changes required restart** | MainActivity / SettingsActivity | live reload intent |
| 7 | **Drawing offset bug** — panel was resized but drew full-screen coordinates | PanelView | panel always full-screen, non-interactive |

## 3. Verified (in this environment)
- APK builds, installs, signed with stable key (updates install over old version).
- Overlay windows: correct flags, no crash on create/destroy/reload.
- Gesture dispatch code paths compile and are guarded by API level (24+).
- Rotation reload path present.

## 4. NOT verifiable here (needs the real device/game)
- Whether MOBA 5v5 accepts accessibility gestures (anti-cheat games often cancel them).
- Mouse cursor visibility inside a fullscreen game (the game hides it).
- Actual in-game latency.

Use **Home → Compatibility Test** to see `completed` vs `cancelled` counts — that tells you
whether the gesture reached the game or was blocked by it.

## 5. Known Android limitations (no app can bypass)
- Accessibility gestures can be ignored by games that opt out.
- Mouse pointer is hidden by fullscreen games.
- Keyboard input can't be injected into another app without focus/root/Shizuku.
