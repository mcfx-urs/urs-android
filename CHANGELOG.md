# Changelog

All notable changes to urs-android are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added

- Shopping lists: create, rename, delete, and share lists with specific
  household members from a new Shopping List section in the navigation
  drawer. Each list's items render as a tile grid grouped by catalog
  category, tapping a tile adds/removes it. Products can be added from
  a POPULAR/RECENT/CATEGORIES tabbed picker with search, or by creating
  a brand-new custom product on the spot; the same product can be added
  to a list more than once with different notes.
- Inventory: named, ownable inventories (create/rename/delete/share)
  each tracking a set of catalog products with quantities and
  low-stock thresholds/reminders, rendered as a row list with inline
  +/- quantity buttons and a warning color once a threshold is
  crossed; long-pressing a row opens the quantity/thresholds/reminder
  settings sheet.
- Adding a product to a shopping list shows its up to three most
  recently used notes as tap-to-fill chips, and a shopping-list product
  tile shows a quantity-on-hand badge when it's tracked in an
  accessible inventory.
- Product/category images render in Shopping List, backed by the
  shared product catalog's image store.
- A "Recently used" section at the bottom of a shopping list, fed by
  the same data as the add-product picker's RECENT tab.
- A Shopping List tile on the home screen, next to Fuel.
- Login: the app now requires signing in (backend auth landed in ),
  gating the whole app behind a new login screen. Access/refresh tokens
  are stored encrypted at rest (Android Keystore-backed AES-256-GCM,
  same mechanism as the existing WireGuard config storage) and attached
  to every backend request automatically; an expired access token is
  silently refreshed and the request retried once, and a fully expired
  session routes back to the login screen from anywhere in the app. New
  Account row in Settings shows who's logged in and offers Log out. (#3)
- Biometric unlock: an opt-in toggle in Settings → Account (only shown if
  the device has strong biometric hardware enrolled) lets fingerprint/face
  replace re-entering the password. Once enabled, a successful biometric
  check unlocks the app for 24h before the next one is required; the
  password itself is never needed again unless the device's biometric
  enrollment changes (a new fingerprint added, all of them removed, etc.),
  which the OS itself permanently invalidates the unlock key over — that's
  the one case a real password login is required again. (#3)

### Changed

- Inventories and their tracked products are now offline-first: cached
  locally in Room, created offline via the same outbox/sync pattern as
  fuel fills and work-time entries, with a pending/failed sync badge on
  unsynced rows.
- The predefined product catalog (now including categories) refreshes
  automatically when the Shopping List or Inventory section is opened,
  rather than only on demand.
- Opening an inventory now goes straight to its tracked products,
  without an intermediate category-tile step.
- Product tiles, home screen feature tiles, and the inventory/shopping
  list row heights now use consistent, equal sizing instead of
  content-driven heights that varied from tile to tile.
- Product tiles no longer show a small +/- circle in the corner; a
  shopping list's "Recently used" tile titles are now shown muted and
  struck through instead.
- The add-a-product panel is now docked to the top of the screen instead
  of a bottom sheet, with a fixed size across every tab and the note
  step, and hides the list's floating add button while open.
- The add-a-product panel's Categories tab now shows an alphabetical
  list of category names instead of image tiles.

### Fixed

- Dropdown option lists (currency, station, etc.) no longer overflow past
  the screen edge with no way to scroll to the remaining entries.
- The "add a product" sheet no longer hides its search results or the
  custom-product form behind the on-screen keyboard.
- A brand-new, empty inventory now has a way to add its first product
  (previously only reachable by tapping an already-populated category
  tile, which a fresh inventory doesn't have yet).

## [0.4.0] - 2026-07-15

### Added

- `ARCHITECTURE.md` documenting the system architecture and
  offline-sync design.
- Work-time tracking: new Work Time section with an offline-capable entry
  form (start/end time, add/remove breaks, military-time auto-formatting
  as you type, "Next" keyboard action between fields) and a history list
  showing the computed daily total and over-/undertime per day; featured
  tile on the home screen; new settings field for the default daily
  target hours. Long-pressing an entry opens Edit (pre-filled, offline-
  capable) or Delete (with confirmation), both syncing in the background.
- Work-time earnings: month/year picker on the Work Time screen with four
  tiles for the selected month — hours worked, plus/minus (days worked
  that month × daily target, so the running month is never shown
  artificially behind), earnings, and % of the monthly contract Soll
  (employment percentage × possible weekdays that month, shown only for
  completed months). Days worked can be manually overridden per month for
  vacation/holidays/sick leave. New "Work Settings" screen (renamed from
  "Work Time") holds the employment percentage and hourly wage alongside
  the existing daily target.

## [0.3.0] - 2026-07-11

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
- Fuel: fill-ups can now be captured fully offline and in a foreign
  currency. A "no station / on the go" toggle captures a GPS fix
  instead of picking a known station; a currency picker (defaulting to
  CHF, cached locally) accompanies the price field. Every new fill-up
  is written to a local, always-available store first and queued for
  background sync, so the Fill-ups list, its pending/failed status
  badges, and the Add-fill form all keep working without a network
  connection. Syncing happens automatically on a connectivity change or
  periodically in the background, or immediately via a new "Sync now"
  tile in the Fuel hub.

### Changed

- Home and Notifications: new custom design system (colors, type scale,
  card/button components) replaces Material 3.
- Navigation drawer and top bar: replaced with custom components,
  off Material 3.
- Settings hub: moved to list-style rows, off Material 3.
- Fuel hub: moved to the card-tile pattern, off Material 3.
- Fill-ups, Gas Stations, and Inventory: moved to card-based lists, off
  Material 3. New bottom sheet, text field, floating action button,
  checkbox, and loading spinner components.
- Fuel Add and VPN settings: moved off Material 3. New dropdown
  selector component (car/filling station).
- Fuel Statistics and Beer: moved off Material 3. New filter chip and
  divider components.
- Material 3 fully removed from the app and from the Gradle dependencies.
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
