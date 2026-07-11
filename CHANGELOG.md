# Changelog

All notable changes to urs-android are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added

- Notifications infrastructure: general-purpose capability for any
  feature to show immediate or scheduled notifications, not tied to a
  single feature area. Exact daily-scheduled reminders (`AlarmManager`,
  survives Doze), automatic catch-up for a missed reminder on next app
  open or device boot, grouped/summary notifications when several fire
  close together, and tap-to-deep-link (`urs://...`) into the relevant
  screen. New Settings tile to grant notification/exact-alarm access
  and send a test notification. (#1)
- Debug builds now use a distinct application ID (`ch.mcfx.urs.debug`)
  and app name ("urs (debug)"), so a debug build installs side by side
  with a release build instead of conflicting with it.
- Inventory: long-press a product row to set its quantity directly
  (instead of repeated +/- taps) and configure two independent
  warning-color thresholds (row turns orange/red at or below each) and
  an optional daily low-stock reminder ("notify at HH:MM if quantity is
  below X"), all per product. Reminders reuse the notifications
  infrastructure's `AlarmManager` scheduling, extended with an optional
  condition check (`ReminderScheduler` re-verifies the current quantity
  right before showing anything, so a reminder correctly stays silent
  once stock is replenished) and a more specific deep-link target (the
  product's own category list, not just the Inventory categories
  overview).
- Inventory: decrementing past 0 now reaches a distinct "not tracked"
  state (shown as "–"), one step below 0 rather than reusing it —
  useful for a product you're pausing tracking on without losing its
  saved thresholds/reminder. Incrementing from "–" returns to 0, not 1,
  keeping the stepper's step size consistent in both directions.
  Warning colors are suppressed while "not tracked", regardless of
  configured thresholds.

### Changed

- Home and Notifications: new custom design system (colors, type scale,
  card/button components) replaces Material 3.
- Navigation drawer and top bar: replaced with custom components,
  off Material 3.
- Settings hub: moved to list-style rows, off Material 3.
- Replaced the placeholder bear emoji and hand-drawn vector app icon
  with the actual mcfx brand mascot artwork: the new illustration is
  now the app launcher icon and the nav-drawer logo, while the
  original simple vector bear is kept as the notification status-bar
  icon (Android renders that one as a flat single-color silhouette, so
  a detailed multi-color illustration doesn't fit that slot).

## [0.2.0] - 2026-07-10

### Added

- Beer counter: a new Home tile with two tally buttons ("+1 · 5dl",
  "+1 · 33cl") that log a timestamped entry per tap. Shows a fun-fact
  card (liters consumed this year, converted into an equivalent number
  of bathtubs), a 30-day daily bar chart and a 12-month monthly bar
  chart (auto-scaling axis with gridlines, always scrolled to today by
  default), and a history list with per-entry delete. Talks to the
  backend's new `/api/v1/beer-log` endpoints.
- Inventory tracking: a new Home tile leads straight to a list of
  categories (e.g. "Medi's"), each holding products with a quantity and
  a ±1 stepper (e.g. "Aspirin: 2"). Add categories/products via a FAB,
  delete either via a trailing icon; a new product is auto-assigned to
  the category it was added from. Talks to the backend's new
  `/api/v1/inventory-category`/`/api/v1/inventory-product` endpoints.
- WireGuard VPN integration: the self-hosted backend is only reachable
  over VPN, so the app now embeds WireGuard directly (`com.wireguard.android:tunnel:1.0.20260102`)
  instead of requiring the separate WireGuard app. A one-time Settings
  setup (paste config text or scan its QR code) is stored via Android
  Keystore-backed AES-256-GCM encryption, never plaintext. Multiple
  home Wi-Fi networks can be saved (add/list/delete) — on any of them
  the app skips VPN entirely; anywhere else it silently tries to bring
  the tunnel up in the background before screens load, with no banner
  or prompts. The app is fully usable without ever configuring VPN at
  all; screens needing the backend fall back to their existing
  error/retry state if it's unreachable.
- Settings is now a hub with tiles (Users — coming soon, VPN) instead
  of one flat screen, mirroring the Fuel hub pattern.
- Fuel hub with dedicated sub-screens: Fill-ups (existing list), a new
  full-screen Add Fill-up flow (replaces the old bottom-sheet form), Gas
  Stations (list + add, wired to the backend's existing station-create
  endpoint), and Statistics (average consumption/price, totals, monthly
  breakdown, car filter — computed client-side from raw fills, matching
  urs-legacy-frontend's approach since the backend has no aggregation
  endpoints). The Home dashboard's Fuel tile now shows a quick-stat: average
  consumption over the last 6 months.
- App navigation shell: hamburger navigation drawer, home tile dashboard, and
  a Settings placeholder screen (Navigation Compose). Mirrors the web app's
  sidebar sections (Home, Fuel, Health, Gokart, Price Monitor, Users) plus a
  new Settings area not present in the web app. Unbuilt features are shown
  disabled with a "coming soon" marker in both the drawer and the home tiles;
  no placeholder screens/routes exist for them yet.
- Fallback Material accent color (apple green) for devices without Material
  You dynamic color (Android < 12); dynamic-color devices are unaffected.

- Fuel tracker (first vertical slice): recent fill-ups list and fill entry form
  (car/station dropdowns, odometer with last-value hint, price/liters with live
  total, date). Talks to the urs Go backend: staging in debug builds, production
  in release builds (BuildConfig.BASE_URL).
- Networking stack: Retrofit 3 + kotlinx.serialization + OkHttp logging (debug
  only), manual DI via AppContainer in UrsApplication.
- Fill payload mirrors the web client contract: client-computed driven distance,
  incremented station counter, midnight-suffixed date; backend "JSON null for
  empty list" quirk normalized in the repository.

- Initial Android project scaffold: Kotlin 2.4, Jetpack Compose (Material 3,
  BOM 2026.06.01), AGP 9.2.1, Gradle 9.6.1 wrapper, version catalog.
- Greeting screen with bear mascot and a tap counter as first Compose state demo.
- Adaptive launcher icon (bear face, from Latin "ursus").
- Application ID `ch.mcfx.urs`, minSdk 26, targetSdk 36.
