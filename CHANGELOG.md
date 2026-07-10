# Changelog

All notable changes to urs-android are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added

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
