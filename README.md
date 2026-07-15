# 🐻 urs

**urs** is a native Android app (Kotlin/Compose). A household tracking
app used to log fuel fill-ups, inventory stock, beer consumption, and
personal work time — synced against a private, self-hosted backend
over a WireGuard VPN tunnel.

See [`ARCHITECTURE.md`](ARCHITECTURE.md) for a technical overview of
the system (client, backend, sync design).

## Features

- **Fuel tracking** — log fill-ups (offline-capable, syncs in the
  background once reachable), manage gas stations, and view
  consumption statistics per car.
- **Inventory** — organize household stock into categories and
  products, with configurable low-stock warning thresholds and daily
  reminders.
- **Beer log** — quick one-tap logging with daily and monthly charts.
- **Work-time tracking** — log a day's work start/end time and an
  arbitrary number of breaks (offline-capable); the daily total and
  over-/undertime against a configurable target are computed
  automatically.
- **Settings** — WireGuard VPN setup (including QR-code scanning),
  notification permissions, and per-feature preferences.

A few more areas (health tracking, go-kart race times, a barcode-based
price monitor, multi-user support) are planned but not built yet — the
navigation drawer marks each as "coming soon" until it lands.

## Permissions

| Permission | Why |
|---|---|
| `INTERNET` | Talk to the backend API. |
| `ACCESS_NETWORK_STATE` | Detect connectivity changes, e.g. to trigger a background sync once the device is reachable again. |
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | Two independent uses: (1) reading the connected Wi-Fi SSID to detect the home network and skip the VPN tunnel when already on it — Android treats SSID access as location-sensitive, even though the app never derives physical location from it for this purpose; (2) capturing a GPS fix for a fuel fill-up at a station not already known to the app. |
| `ACCESS_WIFI_STATE`, `NEARBY_WIFI_DEVICES` | Companion permissions for reading the Wi-Fi SSID (see above). `NEARBY_WIFI_DEVICES` is requested with `neverForLocation`, since it's only used to read the real SSID string, never to derive location from nearby devices. |
| `CAMERA` | Scan a WireGuard config QR code when setting up the VPN. |
| `POST_NOTIFICATIONS` | Show reminders and other notifications (a runtime prompt on Android 13+; a no-op below that). |
| `SCHEDULE_EXACT_ALARM` | Fire scheduled reminders at an exact time rather than an approximate window. |
| `RECEIVE_BOOT_COMPLETED` | Re-arm scheduled reminders after a device reboot, since exact alarms don't survive one. |

Every permission is requested at runtime, right before the feature that
needs it is first used — never upfront at app launch.
