# <img src="app/src/main/res/drawable-nodpi/urs_bear_logo.png" width="40" valign="bottom" /> urs

*User Resource Suite — or Utterly Random Stuff, depending on the day.*

Native Android app (Kotlin/Compose) — the primary client of the `urs`
household-tracking app family. Logs fuel, vehicle service and
diagnostics, inventory, shopping lists, beer, work time, a GPS life
map, baking plans, notes, recurring chores, voice notes, and a
personal kanban board — most of it offline-capable, synced against a
private, self-hosted backend over a WireGuard VPN tunnel.

See [`ARCHITECTURE.md`](ARCHITECTURE.md) for how the client, backend,
and sync design fit together.

## Features

- **Vehicle** — a garage of vehicles with a default, a service-log
  history per vehicle, and live OBD-II diagnostics over Bluetooth
  (read fault codes and live sensor data from a paired adapter).
- **Fuel** — log fill-ups (offline-capable), manage gas stations
  including ad-hoc GPS-captured ones, and view consumption statistics
  per vehicle.
- **Shopping List** — multiple lists, a searchable product picker with
  recently-used suggestions, quantities and on-sale flags, custom
  icons, and sharing between household members.
- **Inventory** — stock organized into categories and products, with
  configurable low-stock thresholds, daily reminders, custom icons,
  and sharing.
- **Beer log** — one-tap logging (330ml/500ml), daily and monthly
  charts, editable entry timestamps.
- **Work time** — a day's start/end time and any number of breaks,
  automatic daily totals and over-/undertime against a configurable
  target, a full wage breakdown (hourly rate, surcharges, tax/social
  deductions), and per-month overrides.
- **Life map** — opt-in periodic background GPS capture with
  combinable adaptive intervals (geofence- and activity-based), shown
  afterward as a gradient track on a map filtered by time range.
- **Baking** — step-by-step bake plans with timers and a history of
  past bakes.
- **Notes** — rich-text notes (bold/italic/underline/links/lists) with
  tags, optional reminders, and export as shared text or a calendar
  event.
- **Chores and Stuff** — a generic recurring-activity tracker: define
  your own types (with icon, color, and an expected interval), log
  events, browse a month calendar, and get an overdue badge per type.
- **Voice notes** — record, review, and manage audio notes with a
  retryable upload queue.
- **Kanban board** — boards with freely configurable columns and
  drag-and-drop cards (description, due date, priority, checklist,
  tags, optional link to a note), synced offline via the same
  outbox pattern as the rest of the app.
- **Watch companion relay** — a local HTTP relay so
  [urs-zepp](https://github.com/3lefeint/urs-zepp) (the Amazfit watch
  companion) can log beers and chores and upload audio notes without
  its own network access or login session.
- **Settings** — WireGuard VPN setup (including QR-code scanning),
  biometric app lock, notification permissions, and per-feature
  preferences.

A few more areas (health tracking, go-kart race times, a barcode-based
price monitor) are planned but not built yet — the Home screen reserves
a spot for each as "coming soon" until it lands.

## The urs family

- [urs-web](https://github.com/3lefeint/urs-web) — browser companion
  for tasks awkward on a phone, home-network only.
- [urs-zepp](https://github.com/3lefeint/urs-zepp) — Zepp OS mini-app
  for Amazfit watches, relayed through this app.
- [urs-backend](https://github.com/3lefeint/urs-backend) — issue
  tracker for the backend; the Go/MariaDB REST API itself is
  self-hosted, not on GitHub.

## Permissions

| Permission | Why |
|---|---|
| `INTERNET` | Talk to the backend API. |
| `ACCESS_NETWORK_STATE` | Detect connectivity changes, e.g. to trigger a background sync once the device is reachable again. |
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | Three independent uses: (1) reading the connected Wi-Fi SSID to detect the home network and skip the VPN tunnel when already on it — Android treats SSID access as location-sensitive, even though the app never derives physical location from it for this purpose; (2) capturing a GPS fix for a fuel fill-up at a station not already known to the app; (3) the life map's periodic location capture, opt-in and off by default. |
| `ACCESS_WIFI_STATE`, `NEARBY_WIFI_DEVICES` | Companion permissions for reading the Wi-Fi SSID (see above). `NEARBY_WIFI_DEVICES` is requested with `neverForLocation`, since it's only used to read the real SSID string, never to derive location from nearby devices. |
| `ACCESS_BACKGROUND_LOCATION` | Lets the life map's periodic capture keep running while the app isn't in the foreground. A separate runtime grant from Android 10 on, requested only after foreground location is already granted, and only if the life map feature is turned on in Settings (opt-in, off by default). |
| `ACTIVITY_RECOGNITION` | Detects stillness/movement so the life map's activity-based adaptive interval can pause or throttle capture while stationary. Opt-in, tied to the same life map setting. |
| `CAMERA` | Scan a WireGuard config QR code when setting up the VPN. |
| `BLUETOOTH`, `BLUETOOTH_ADMIN`, `BLUETOOTH_CONNECT` | Pair with and read live data from an OBD-II Bluetooth adapter for vehicle diagnostics. |
| `USE_BIOMETRIC` | Optional app-lock: unlock with fingerprint/face instead of leaving the app open. |
| `POST_NOTIFICATIONS` | Show reminders and other notifications (a runtime prompt on Android 13+; a no-op below that). |
| `SCHEDULE_EXACT_ALARM` | Fire scheduled reminders (notes, kanban due dates) at an exact time rather than an approximate window. |
| `RECEIVE_BOOT_COMPLETED` | Re-arm scheduled reminders after a device reboot, since exact alarms don't survive one. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | Keep the watch companion relay running while active. |

Every permission is requested at runtime, right before the feature that
needs it is first used — never upfront at app launch.
