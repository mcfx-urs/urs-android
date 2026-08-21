# Changelog

All notable changes to urs-android are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

## [0.18.1] - 2026-08-21

### Fixed

- The monthly Earnings tile's Net figure now applies the built-in default surcharge/deduction rates when the user has never opened Settings → Work Settings → Surcharges & deductions, instead of silently treating every rate as 0% (Net equaling Gross).
- Settings → Work Settings → Surcharges & deductions no longer traps its bottom fields and Save button behind the on-screen keyboard with no way to scroll them into view.

## [0.18.0] - 2026-08-21

### Added

- The work-time entry form now shows the daily hours worked and the deviation from the daily target live, updating as start time, end time, breaks, or the target change (#7).
- New Settings → Work Settings → Surcharges & deductions screen: configurable vacation pay, holiday, and 13th-month surcharge percentages, AHV/IV/EO, ALV, SUVA/NBU, and KTG deduction percentages, and a fixed BVG deduction amount. The monthly Earnings tile now shows gross and net wage stacked, decimal-aligned (#11).
- Tapping −/+ on an inventory product now shows a quick color-sweep across the row confirming the change: red left-to-right on decrease, blue right-to-left on increase (a plain opacity flash instead when the system's reduce-motion setting is on) (#8).
- The inventory product settings sheet gained "+7"/"-7" quick-adjust buttons for weekly-batch products (e.g. medication prepared once a week), independent of the row's own −/+1 buttons (#9).
- New Notes feature: a private, per-user list of notes (title, content, tags, an optional one-time reminder) with an active list filterable by tag, a complete/reopen toggle, and a separate archive view. Never shared across users, same privacy bar as the VPN config. New Home tile (#10).

## [0.17.0] - 2026-08-21

### Added

- New Settings → General → Language screen: choose English or Schwiizerdütsch for the app's own display language, independent of the device's system language.

### Changed

- Redesigned the Home screen: glass-style cards, a collapsing header (logo/settings row stays, welcome/hero row collapses on scroll), and real illustrated tile icons instead of emoji.
- Life Map's Home tile is now a larger, full-width card with a live map preview centered on the last known location.

### Fixed

- Screens with a long scrollable list could end up with their last row (and, in a couple of cases, a floating add button) resting behind the system navigation bar.
- Logging out no longer leaves the previous user's inventories cached locally — the local database is now cleared on logout, and inventories are additionally filtered by the logged-in user.
- The WireGuard VPN config was stored device-wide, so every app user shared and could use the same tunnel/private key; it's now stored per logged-in user, and an active tunnel is disconnected on logout instead of staying up across a user switch.
- Corrected the sourdough starter feeding ratio in the built-in Baking recipe template.

## [0.16.0] - 2026-07-28

### Added

- New Baking feature: backward-planned recipe schedules (starting with Sourdough Bread), per-step exact-alarm reminders with independent snooze, plan cancellation, multiple concurrent plans, and a bake history synced to the backend.
- New Settings → General → Theme screen: choose System default/Light/Dark app-wide appearance, independent of the device's own setting.
- Work Time entries gained a "Paid morning break (+15 min)" toggle, on by default, added on top of the logged work span instead of subtracted like a regular break.
- Life Map time-range filter gained "Last day" and "Last week" options.

### Changed

- Catalog product tiles (Katalog search, product/category forms, Image Review, Shopping List) now follow the active theme instead of always rendering with a light background.

### Fixed

- Text field labels no longer jump from inside the field to above it on focus, which previously shifted the field's height and pushed the rest of the form down.
- Life Map now reliably reflects the selected time range: the map's zoom and rendered track could previously stay stuck on a stale range after a fast filter change.

## [0.15.0] - 2026-07-27

### Added

- Location History settings gained a "Precision mode" toggle: schedules Life Map captures via exact alarms instead of WorkManager's default battery-friendly (but drift-prone) scheduling.

## [0.14.0] - 2026-07-26

### Added

- Super users see a new "Katalog" tab in Product Management to search the full product catalog (including external_catalog-imported products) and edit any result, not just manually-created products.

### Changed

- Image Review moved from a top-level Settings tile to an icon button inside Product Management (General → Product Management), reachable there for super users instead.

## [0.13.0] - 2026-07-26

### Changed

- Product Management's "Generate image" failure now shows a specific reason (no connection, server error, or unknown) in a dismissible pop-up instead of a generic inline message.

### Fixed

- Bottom sheets and forms with the on-screen keyboard open (Product Management's edit form, Add Worktime, Add Fuel Price) no longer have their content pushed up past the status bar with the bottom cut off.

## [0.12.0] - 2026-07-25

### Added

- Settings → Product Management's "New product"/"Edit product" form has an optional image description field and a "Generate image" button, producing an AI-generated product photo.
- New Settings → Image Review screen (super users only) to approve or reject AI-generated images before they're offered for reuse on other products.

### Changed

- Settings → Product Management's "New product" form now rejects a name that already exists in the catalog instead of silently attaching to that existing entry.

### Fixed

- Add/Edit Service Entry, Add/Edit Vehicle, and Add Fill-up now show "Next" on the keyboard between fields instead of the default checkmark, and no longer trap fields or the Save button behind the on-screen keyboard while scrolling.
- Product Management's "New product"/"Edit product" form no longer traps a focused field behind the on-screen keyboard.

## [0.11.0] - 2026-07-25

### Added

- Fuel Stats now shows line charts for average consumption and kilometers driven per month.
- Fuel Stats now shows a fuel price development chart, one line per fuel type.
- Tapping "+" to add a product now auto-focuses the search field and opens the keyboard.

### Changed

- Category headers in a list's tile grid are a bit larger, with more space after a category's last tile.
- "Recently used" products are now scoped per list instead of shown the same across every list.
- Picking a product from "recently used" now removes it from that list until it's used (added and later removed) again.
- The Fuel Stats vehicle filter is now a dropdown instead of a row of buttons.

### Fixed

- "Recently used" now also updates when a product is removed from a list (tapping a tile to "buy" it), not just when it's added — previously the same products stayed at the top indefinitely.
- Fuel Stats' Total Cost and Average Price/Liter no longer mix currencies — a fill entered in a foreign currency (e.g. PLN) was previously added to the CHF total using its raw, unconverted price.

## [0.10.0] - 2026-07-24

### Added

- Added vehicle create/edit/delete, and new optional vehicle fields (engine code, vehicle type, Fahrzeugausweis data, last-MFK-inspection date).
- Added a "Service History" screen under the Vehicle hub, for tracking service/maintenance entries per vehicle (date, odometer, provider or DIY, cost, categories, custom tags).
- Added a "Watch Relay" setting under General, letting the urs-zepp companion watch app log a beer fill without opening the app.
- Added a "Record Fuel Price" screen to the Fuel hub, for logging a price at a known station without a fill-up.
- Added a "New Fuel Fill" shortcut to Home, for creating a fill-up directly without going through the Fuel hub.
- The Beer tile on Home now shows the number of days since your last logged beer.
- Date fields (fill-up, fuel price, service entry, vehicle registration/MFK dates, work-time entry) now use a calendar date picker instead of a free-text field, with a built-in toggle to type the date instead.
- The About screen now shows a "State" section (sync status with last-synced time, backend reachability with a manual recheck, VPN connection state), so connectivity problems are visible instead of silent.
- Added a direct About shortcut next to Settings at the bottom of the navigation drawer.

### Changed

- The About screen's build timestamp is now formatted as `dd.MM.yyyy HH:mm:ss` instead of a raw `yyyyMMdd-HHmmss` string, and the daily joke no longer has quotation marks around it.
- Renamed "Car" to "Vehicle" throughout (Home tile, drawer, screen titles) — vehicle types beyond cars are planned.
- Completed the Car-to-Vehicle rename internally (local storage, sync payloads, backend API calls) to match the UI wording above.
- The "Vehicle" tile now appears on Home directly (previously only reachable from the drawer); the old standalone "Fuel" Home tile is gone, replaced by the new "New Fuel Fill" shortcut and the "Vehicle" tile.
- Life Map now also appears as a Home tile, not just in the drawer.
- The drawer no longer lists Gokart or Price Monitor (still reachable as "soon" tiles from Home).

### Removed

- Removed the "Users" and "Health" tiles/drawer entries (no feature behind them).
- Removed the "🐻 Urs looks out for you" footer line from Home.

### Fixed

- The Fuel hub's top bar now shows a back arrow instead of the drawer menu, matching its sub-screen-of-Car navigation.
- Settings is now pinned to the bottom edge of the drawer instead of just trailing the list.
- Home's tile grid no longer has its last row obscured by the system navigation bar.
- The Save/Log out button on several forms (fill-up, fuel price, service entry, vehicle, work-time entry, account settings) is no longer obscured by or unreachable behind the system navigation bar.

## [0.9.0] - 2026-07-22

### Added

- Super users can now tap a filling station in the stations list to edit its name, address, and coordinates.
- Added a "Skip nearby repeats within" setting under Location History to reduce redundant location captures while stationary.

### Changed

- Life map's track line now gradates from black (oldest point) to red (newest point) instead of a single solid color, and is thinner.
- Life map no longer shows start/end pin markers.
- Location capture now discards fixes with poor GPS accuracy instead of storing every fix unconditionally.

### Fixed

- The map's on-screen zoom +/- buttons no longer render partly hidden behind the system navigation bar on devices using 3-button navigation.

## [0.8.0] - 2026-07-22

### Added

- Added a toggle in VPN settings to restrict the WireGuard tunnel to urs's own traffic only.
- The debug build's app icon now has an orange background, distinguishing it from the release icon on the home screen.
- The debug build is now named "Ursa" instead of "urs (debug)", shown throughout the app (toolbar, About screen, login screen, notifications) as well as the home screen label.
- The manual "add filling station" form gained an address-based position search (backend geocoding) with a full-screen map step to confirm or manually place the pin.

### Fixed

- The map-confirm step's "Use this position" button no longer sits partly behind the system navigation bar on devices using 3-button navigation.
- Life map now reliably centers on the last location point when it first loads or the time range changes, instead of sometimes landing on an unrelated part of the world map.
- A silently retried token-refresh request could get flagged as reuse and force-log-out every session, most noticeable right after a backend redeploy.
- Life Map now shows the full location-history for the logged-in user
  pulled from the backend, instead of only points captured on the
  current device/install. A point removed server-side now also
  disappears from the device on the next refresh.

## [0.7.1] - 2026-07-22

### Fixed

- The VPN tunnel no longer repeatedly disconnects and reconnects while
  the app is open and away from the configured home Wi-Fi, which
  caused the VPN and Wi-Fi status-bar icons to flicker continuously.
- Manually disconnecting the VPN tunnel from Settings now actually
  stays disconnected, instead of reconnecting on its own within a few
  seconds.

## [0.7.0] - 2026-07-22

### Added

- The fuel fill-up form's filling-station picker now sorts stations by
  distance to the device's current location (showing the distance next
  to each one) instead of alphabetically, with a new app-wide location
  service that's kept warm on app foreground and requests location
  permission proactively on first Home-screen launch (#2).
- A new "Car" section in the navigation drawer (replacing the direct
  "Fuel" entry there, though Home's Fuel tile is unchanged), opening a
  hub with Fuel and OBD tiles.
- OBD: connects to a paired Mucar BT200 Bluetooth adapter and shows
  live engine RPM, vehicle speed, coolant temp, and fuel level,
  polled at 1 Hz with automatic reconnect on a dropped connection. A
  setup screen (gear icon) handles the Bluetooth permission request
  and shows pairing/connection status.
- A new Admin tile in Settings, visible only to super-user accounts,
  with a confirm-then-restart action for the backend server, which
  polls until the server responds again and reports back once it's up.
- The catalog image picker now has a search field that narrows the
  grid to images linked to a matching product/category name.
- Long-pressing a fill-up in the fuel-fills list opens Edit/Delete
  actions, so a mistaken entry can now be corrected or removed (#4).
- A "Change password" action in Account Settings, which also logs out
  every other session on success.

### Fixed

- Ad-hoc GPS-only filling stations (created via "No station / on the
  go") no longer appear as selectable options in the station picker
  for later fill-ups, which could previously assign a new fill-up the
  wrong, stale coordinates from a past ad-hoc stop.
- Login and biometric-unlock screens now bring up the VPN tunnel
  before contacting the backend, and show a distinct "can't reach the
  server" message instead of "wrong username or password" when it's
  unreachable.
- Login and biometric-unlock screens now follow the app's dark/light
  theme instead of always showing a light background, which also
  restores status bar icon visibility in dark mode.
- Inventory/shopping-list share sheets and the product-image picker no
  longer crash the app if their backend fetch fails (#5).
- The app no longer crashes on startup for an existing install whose
  local database predates the `source` column added to the
  filling-station cache — the Room database version was bumped to
  match.
- Work Settings save and the Work Time month-override sheet now show
  an error instead of silently discarding a failed save (#5).
- Product settings popup now shows an error instead of silently
  discarding a failed save (#5).

## [0.6.0] - 2026-07-21

### Added

- Fuel: "Filled to full" toggle on the add-fill-up form, so a partial
  fill-up no longer distorts the average-consumption figures.
- Life map: optional periodic background location capture, browsable
  on a map (Settings → Location History) filtered by time range (last
  month, 3/6/12 months, or all).
- Life map: 1/2/5/10-minute capture interval options, alongside the
  existing 15 min-4 h choices.
- Shopping list items: a quantity stepper (+/-) and an "On sale only"
  toggle, alongside the existing note field, in both the add-product
  popup and the edit sheet. Quantity shows as "Nx" on the tile when
  set; "On sale only" shows as a yellow/black striped tile border.
- Shopping list: a centered accent-colored bar separating the item
  grid from the "Recently used" section. List names, category headers,
  and the top app bar (title and back/menu icon) now use the accent
  color on shopping list screens.
- Home, Fuel, and Inventory: the accent-color treatment from the
  shopping list screens now also applies to the top app bar (title and
  back/menu icon), tile titles, inventory list names, and inventory
  product rows (name, quantity, +/-/remove icons). The navigation
  drawer's header and item icons/labels are always accent-colored now,
  not just the selected item.
- Settings: About is now the first item in the list, and its screen
  shows the bear logo, app name, "mcfx", version + build type, build
  timestamp, and a joke fetched from the backend at build time —
  centered, accent-colored.
- Settings: a new Product Management screen for creating, editing, and
  deleting manually-created catalog products and categories, with a
  picker for reusing an existing catalog image.

### Changed

- Settings menu restructured into About / Account / General. Account
  now also holds the former standalone Work Settings fields
  (employment %, target hours, hourly wage), with Log out moved to the
  bottom. General is a new sub-screen holding VPN, Notifications,
  Location History, and Product Management. The unused "Users"
  placeholder tile is removed.

### Fixed

- Life map: the map no longer grows over the time-range dropdown (or,
  at full size, the top app bar) when zoomed.
- Life map: zooming or panning no longer gets reset back to the
  default view every time a new location point is captured.
- Trigger an opportunistic outbox sync as soon as `NetworkGate` confirms
  the backend is reachable (home Wi-Fi or a freshly connected VPN
  tunnel), not only on a live Wi-Fi network change. A cold app start away
  from home Wi-Fi previously had no such trigger at all, leaving sync
  recovery solely to the 15-minute periodic worker and its exponential
  backoff.
- Shopping list add-product picker: the search field now clears after
  adding a product, so the next search starts from empty instead of
  the previous query.
- The add-product panel no longer closes on a stray tap that misses
  the search field/a product tile — it was falling through to a scrim
  behind the (already opaque, full-screen) panel, contradicting the
  panel's own no-tap-outside-dismiss design. The system back gesture
  now closes it instead, as originally intended.
- Shopping list: the "On sale only" tile border is thicker (3dp to
  5dp) so it reads more clearly against the tile photo.

## [0.5.0] - 2026-07-19

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
- Login: the app now requires signing in, gating the whole app behind
  a new login screen. Access/refresh tokens
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
- The inventory row +/- stepper no longer goes negative; decrementing
  from 0 now pauses tracking for that product instead, and incrementing
  from paused resumes at 0.

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
