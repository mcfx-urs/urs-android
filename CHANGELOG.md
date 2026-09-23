# Changelog

All notable changes to urs-android are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Fixed

- Life Map: the capture drift reading no longer reports an interval mode switch as scheduling drift (#88)

## [1.3.0] - 2026-09-18

### Added

- Added export (CSV, via the share sheet), per-capture timing, active-mode context, and a battery/power-state snapshot to the Life Map GPS capture log, and switched its retention from a fixed 500-row cap to a 7-day window (#86)
- Added Journal: a calendar-first, domain-organized replacement for Chores, running alongside it — month view with a week-number column and multi-day event bars, a day view with an hourly grid, an options menu with domain/type filtering and management, and a relocated per-type overview screen (#83)

### Changed

- Notes tag chips now render in their assigned color (hub list, detail screen, filter row) instead of a fixed accent color (#85)

### Fixed

- Notes: the tag filter no longer resets when opening a note and pressing back (#84)
- Life Map: the still/moving activity classification now requires several consecutive agreeing polls before switching the capture interval, instead of reacting to every single poll

## [1.2.0] - 2026-09-17

### Changed

- Changed the Kanban card description field to a rich-text editor, matching Notes (#79)
- Changed Kanban board columns to a glass-style card look, matching urs-web's Kanban design (#81)

### Added

- Added a changelog viewer to the About screen (#82)
- Added favorite Kanban boards as quick-jump badges on the Home Kanban tile (#80)
- Added a save confirmation bar after editing or saving a Kanban card (#78)
- Added a brightness slider to the Life Map track colour picker, so black and other dark shades are reachable (#65)
- Added an intensity-gradient mode for the Life Map track (one colour varying brightness instead of blending between three hues) (#66)
- Added a custom time range picker (from/to date + time) to the Life Map controls sheet, alongside the existing presets (#76)
- Added a share button to each voice note, exposing the recording via the system share sheet (#59)

### Changed

- Changed the Life Map track gradient to interpolate by cumulative track distance instead of point count, subdividing long segments so the ramp stays smooth across them too (#64)
- Changed the Life Map overlay controls from a permanently visible top bar into a single icon that opens a bottom sheet, so the map area stays free of controls (#75)

### Fixed

- Fixed Kanban board columns not filling the screen height, so long card lists pushed past the visible area instead of scrolling within their column (#77)
- Fixed note tags briefly disappearing during sync by wrapping the tag delete+insert pair in a single transaction (#70)
- Fixed the Notes screen being able to trigger multiple concurrent syncs on a single visit; also removed a duplicate connectivity-available trigger firing on every Wi-Fi capability tick (#71)
- Fixed a beer log entered via the watch relay only syncing on the next periodic sync instead of immediately (#68)
- Fixed the Home beer tile not refreshing when a beer is logged via the watch relay while Home is already the foreground screen
- Fixed the Beer screen's list/stats not refreshing when a beer is logged via the watch relay while that screen is already open

## [1.1.0] - 2026-09-16

### Security

- Switched the household-member picker to the new `GET /api/v1/household-users` endpoint; `GET /api/v1/getuser` now only ever returns the caller's own profile (#72)

### Changed

- Changed the beer-log home tile to show minutes/hours instead of "0 days ago" for logs within the same day (#69)

### Fixed

- Fixed the Home beer tile not refreshing after logging a new beer or across a day boundary until the app was restarted (#67)

## [1.0.0] - 2026-09-15

### Added

- LICENSE (MIT).

### Changed

- Watch-relay shared secret is no longer hardcoded in source; it is now read from a gitignored `relay.properties` at build time.

## [0.25.1] - 2026-09-14

### Fixed

- Kanban card/column drag: dragging into a different column live-reparented it into that column's list, which dropped the in-progress gesture (visible as the card flipping back and forth across the boundary) and, on a cancelled gesture, could leave it rendered in the wrong column without ever saving the move. The column/position a drag lands on is now decided once, on release, instead of live while still held.

## [0.25.0] - 2026-09-13

### Added

- Kanban board feature: boards, columns, cards, checklist, tags, due-date reminders, drag-and-drop reordering (#62).

## [0.24.0] - 2026-09-12

### Added

- Beer log: long-pressing an entry opens an edit sheet for adjusting its timestamp; amount stays as logged, and the timestamp can't be set in the future (#47).
- Vehicle list: tapping an entry opens a read-only detail screen with an Edit action; long-press keeps its existing Edit/Delete sheet (#48).
- Shopping List and Inventory home tiles show quick-jump badges for favorited lists/inventories, opening straight into that list; favorite/unfavorite from the existing long-press action sheet (#50).
- Watch relay accepts chunked audio-note uploads on a new `/api/watch/audio-note-chunk` endpoint, removing the note-length limit the single-request upload had (#41).
- Location History: two combinable adaptive-interval toggles alongside the fixed capture interval — geofence-based (switches to a dense interval on leaving a circle around the last point, back to a sparse one once settled) and activity-based (pauses or throttles capture during detected stillness); a "Location capture" card on the About screen opens a dedicated terminal-style log screen with a live header (current activity/confidence and geofence tier, each with how long they've been in that state) above the detailed event trail, which also records every explicit GPS read app-wide (life-map capture, geofence re-centering, fuel-fill, and the ambient location refresh), tagged by source (#60).
- Note reminder notifications have a "Done" action that clears the reminder without marking the note completed; completing a note from the app now also clears its reminder (#52).
- Sharing a note as text now appends a footer with the reminder date and time when the note has a reminder set (#31).
- Note tags are now included when sharing a note as text (in the footer) and when creating a calendar event from a note (appended to the description) (#32).
- Watch relay serves the active chore types on a new `/api/watch/chore-types` endpoint, letting the watch build its Chores menu from the account instead of a hardcoded list (#61).
- Shopping lists and inventories can be given an icon from a searchable picker ("Choose icon" in the long-press sheet); the favorite home-tile badge shows the icon instead of the name's first letter (#56).
- Data is now pulled from the server on tunnel-reachable, on the periodic 15-minute sync, and on manual "Sync now" — not only when each screen happened to be opened — so a fresh install or a post-migration wipe no longer shows empty screens until every one is visited manually; sync status shows as a dot on the Home logo and a tint on the Home hero moon (#53).

### Changed

- Shopping list add-product picker: tapping a result adds it to the list immediately, replacing the separate note/quantity confirm step; a confirmation bar then offers Undo and Edit (opens the item editor for note, quantity and on-sale). Added items start with no quantity set rather than a default of 1 (#51).
- Life Map: the track line now runs a cyan→blue→magenta gradient with an optional contrasting outline so it stays readable where it follows a coloured road; the three gradient colours and the outline are configurable in Location History settings, and an on-map Standard/Muted toggle desaturates the base map (#55).

### Fixed

- Unsaved text in notes, fuel fill-ups, link edits, and chores sheets no longer disappears on device rotation or an accidental back/Home/scrim dismiss — rotation now resumes the in-progress edit instead of reloading it, and leaving a form or sheet with unsaved changes shows a discard confirmation (#49).
- Joke of the day: the About screen's build-time joke is now fetched from the public dad-joke API instead of the private backend, so published release builds no longer all ship the same hardcoded fallback (#58).
- Life Map home tile: tapping the map preview now opens Life Map, not just the title text above it — the embedded map view was swallowing the tap (#57).
- Chores type filter: the button that opens the create-type form is now labelled "New type" instead of "Type" (#54).

## [0.23.0] - 2026-09-04

### Added

- Work-time entry: optional free-text comment, editable in the add/edit form and shown under the entry in the monthly list (#45).

### Changed

- Chores: the always-visible type row is now behind a filter button in the calendar header (between the next-month arrow and the share button); tapping it opens a sheet with the type filter and "create new type" (#34).
- Chores type filter: added a select-all / deselect-all toggle so isolating one type no longer means tapping every other one off (#40).
- Chores: archived types are now listed in the type filter sheet with a "Reactivate" action; previously they were inaccessible once archived (#37).
- BVG deduction: removed from the Surcharges & deductions settings; it is now entered per month in the month's adjustments sheet (alongside the days-worked override) and carried forward to later months until a later month sets its own value (#43).
- Wage breakdown: the meal allowance is now part of the gross figure (its own line in the gross build-up) instead of being appended to net as a separate "Net (incl. meal allowance)" line; it stays excluded from every surcharge and deduction base, so the net figure is unchanged (#42).
- Wage breakdown: each surcharge and deduction line is now rounded to 5 Rappen and the totals are the sum of the rounded lines, matching the official payslip (#44).
- Wage breakdown: line order now matches the official payslip — the meal allowance row sits directly after the base wage, deductions run AHV/IV/EO, ALV, SUVA/NBU, BVG, KTG, and a "Total deductions" row is shown before Net (#46).

## [0.22.0] - 2026-08-30

### Added

- Voice Notes: audio memos recorded on the watch are relayed to the phone, converted to standard Ogg-Opus on receipt, stored device-locally, and listed on a new Voice Notes screen with play/pause and delete (#41).
- Home screen edit mode: long-press any tile to enter it, then tap a tile to select it (resize via corner dots, drag to move, remove via the corner icon), add unplaced destinations via "Add tile", or reset the layout to default — the layout persists per device (#12).

### Changed

- Every Home tile (including the previously bespoke Work Time, New Fuel Fill, and Life Map rows) now renders through one unified tile component so resizing/moving behaves consistently across the whole grid (#12).
- Chores stats list is now a full-width, vertically scrolling list instead of a horizontally-scrolling row of cards, and supports manual reordering by long-press-drag (#33).
- Watch relay's audio-note endpoint accepts a base64-encoded body via an `x-audio-encoding: base64` header, decoding it before writing the file (urs-zepp#4).

### Fixed

- Screen content no longer ends up stuck behind the system navigation bar in 3-button navigation mode; the bar's space is now reserved once app-wide, so every screen (e.g. the Chores calendar) scrolls fully clear of it.
- Home screen edit mode: dragging a tile no longer corrupts drag state or drops mid-gesture when the move reorders the underlying tile list (#12).
- Home screen edit mode: the tile order, not explicit grid coordinates, is now the layout's source of truth — dragging a tile onto another reorders them like a list instead of leaving gaps a coordinate-based layout could produce, and a wide or tall tile that blocks a cell no longer gets backfilled by something later in the order. The reorder commits after hovering the same tile for 500ms rather than on every cell passed over, and a tile no longer visibly snaps back to its old cell when a drag is released after committing a reorder, nor toward a stale position left over from an unrelated tile's earlier reorder (#12).
- Note formatting toolbar's indent/outdent buttons now render in the correct left/right order (#30).
- Chores calendar export now only marks entries as exported once a share target is actually selected, not just when the share sheet opens (#35).
- Chores calendar export now writes one `.ics` file per event instead of bundling every event into a single file, so calendar apps that only import the first event from a file (e.g. Proton Mail Mobile) create all of them (#36).
- The app no longer forces a login screen when the access token has expired while offline; a token refresh that can't reach the server leaves the session intact instead of logging out, fixing vehicles (and other cached data) not showing up offline (#38).

## [0.21.0] - 2026-08-28

### Added

- Note editor can export the open note: share it as plain text via the Android share sheet, or open a pre-filled calendar event built from its title, content, and reminder time (#22).
- The About screen's Sync row opens a detail sheet listing every queued or failed change with its error, plus a manual "Sync now" trigger (#23).
- Default vehicle per user: set it in Settings or via a quick-switch chip row on the Fuel hub; drives the Home consumption stat, the Statistics filter, and the pre-selected vehicle when adding a fill-up, and syncs across devices (#13).
- New "Chores and Stuff" section: a generic per-user activity tracker with a month calendar view — log recurring tasks per day (past or future, optional time and note), filter the calendar by type, and see how long since each type was last done. Types carry a colour and a Material or emoji icon and can be archived (#27).
- The gas station edit list now shows a super-user-only "Ad-hoc stations to review" section listing GPS-located stations auto-created on fill-up, so their name and address can be filled in from the same edit form (#17).
- Admin screen can view and change the backend log level (debug/info/warn/error) at runtime, applied immediately with no server restart (#16).
- Chores calendar export: an action in the Chores screen builds one `.ics` file per calendar label from local data and shares it via the Android share sheet, scoped to new events since the last export or everything again; types gain an optional calendar label in the type editor (#28).
- Chores overdue tracking: types can set an expected interval in days; the stats strip shows an "overdue" badge once that long has passed since the last entry, and an opt-in per-type reminder fires via a periodic check, gated by a new "Chore overdue reminders" toggle in notification settings (#29).
- Watch relay: new `POST /api/watch/chore-event` endpoint logs a chore entry for a given tracker-type id with `source = watch` and today's date, using the same relay token as the beer-fill path (urs-zepp#3).
- Watch relay: new `POST /api/watch/audio-note` endpoint (proof of concept) writes a transferred `.opus` body to `filesDir/audio-notes/` and logs its path and size — no Room entity, UI, or sync (urs-zepp#4).
- Home screen layout groundwork: a persisted `(column, row, width, height)` tile model with recursive push-down conflict resolution and a default layout seeded from the current order; the edit-mode UI is not wired in yet (#12).
- Super-user-only Image Generator screen: generate an image from a free-text prompt with adjustable size/quality/background, preview it, then save it to the device gallery, share it, or delete it. Nothing is stored server-side (#20).

### Changed

- The watch relay's beer-fill endpoint logs the volume passed in the request rather than a fixed 500 ml, and rejects a missing or invalid volume (#26).

### Fixed

- Logging a beer or chore from the watch no longer fails when the phone can't reach the backend directly or bring up the VPN tunnel; the entry is queued offline and synced like every other change.
- Opening the vehicle list with no network connection no longer crashes the app.
- Shopping-list items and lists deleted while offline (or on another device) no longer reappear on the next sync (#15).
- Two devices editing the same shopping-list item, list name, or inventory quantity offline now keep whichever edit was made last, instead of whichever reached the server first; the losing edit is dropped silently and the screen reconciles to the winning value (#14).

## [0.20.0] - 2026-08-24

### Added

- Notes support rich text formatting: bold, italic, underline, links, and nested bullet/numbered lists, with a formatting toolbar on the content field (#21).
- Meal allowance checkbox on work-time entries: flags a day as requiring a CHF 18.- meal allowance, shown as a badge in the month overview and added into the month's net wage (#24).
- Tapping the Earnings tile in the Work Time month overview opens a detailed wage-calculation breakdown (#25).

### Changed

- Replaced placeholder home-screen tile icons with commissioned artwork.

### Fixed

- Notes are now pulled down from the server every time the notes screen is opened, not just once per app launch, so notes created or edited on one device/install appear on others without a full app restart.

## [0.19.0] - 2026-08-22

### Added

- Swipe left/right on the Work Time monthly overview to step to the next/previous month (#6).

### Fixed

- The new/edit note form no longer traps its bottom fields behind the on-screen keyboard with no way to scroll them into view.
- Settings → Account no longer traps its bottom fields (including the change-password fields) behind the on-screen keyboard with no way to scroll them into view.
- A note saved without tags no longer permanently fails to sync and re-create itself as a duplicate on the server on every later note sync.
- The top bar no longer pops in/out in a single frame when navigating to/from Home, shoving the still-transitioning screen below it down/up abruptly.
- A Date field and a Time field next to each other no longer misalign when one is empty and the other has a value.
- The Work Time monthly wage summary no longer briefly shows zero/blank target hours and deduction rates on every app cold start while they reload from the backend.

### Changed

- Screen transitions use a short fade instead of Navigation Compose's default slide.
- Shopping-lists overview rows use a larger title and more vertical padding for easier scanning (#19).

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

- Super users see a new "Katalog" tab in Product Management to search the full product catalog (including externally-imported products) and edit any result, not just manually-created products.

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
