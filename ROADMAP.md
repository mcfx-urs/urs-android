# urs-android — Roadmap

Port of the urs personal home app (Vue 3 SPA + Go backend) to a native Android app.
Long-term goal: the Android app fully replaces the web frontend; the web app gets
shut down. The Go/MariaDB backend stays and remains reachable only locally or via VPN.

## Decisions (agreed 2026-07-09)

| Topic | Decision |
|---|---|
| UI stack | Kotlin + Jetpack Compose (Material 3) — official native stack, best learning value |
| Architecture | Offline-first: Room (SQLite) is the source of truth on device; backend is the sync target |
| Sync model | Outbox pattern (append-only data makes this simple); WorkManager for background sync |
| VPN | WireGuard embedded in the app via `com.wireguard.android:tunnel`; on-demand tunnel for sync |
| Distribution | GitLab CI builds signed APK → GitLab Release → Obtainium on the phone auto-updates |
| Scope | Full feature parity, then web frontend is decommissioned |
| Users/devices | Start: 1 user, up to 2 devices. Later: family members with own phones |
| Backend | Unchanged in principle; gets small sync-support extensions (see below) |
| Developer background | Android/Kotlin complete beginner; strong TypeScript/Vue and Go experience |

Why offline-first: the VPN is only active on demand, so the app must work without a
backend connection. Nearly all urs data (fuel fills, body stats, race times) is
append-only, which avoids real conflict resolution — local writes queue up and get
pushed when the backend is reachable.

## Phase 0 — Tooling setup only (no practice labs)

Decision: skip codelabs/throwaway apps — all learning happens directly on the urs
app (learning by doing, with heavy pairing support). Kotlin/Compose concepts get
explained in place, by analogy to TypeScript/Vue (`val`/`var` ≈ `const`/`let`,
`remember`/`mutableStateOf` ≈ `ref`, coroutines ≈ `async`/`await`).

Setup status (checked 2026-07-09):

- [x] OpenJDK 21 installed system-wide
- [x] Android SDK at `~/Android/Sdk` (platform android-36, build-tools 35/36.1,
      platform-tools/adb 36, emulator + x86_64 image, licenses accepted)
- [x] KVM available, user in `kvm` group (emulator-ready)
- [x] `ANDROID_HOME` + platform-tools/emulator on PATH via `~/.zshrc`
- [ ] Android Studio via JetBrains Toolbox (recommended alongside VSCodium for
      Compose Preview, Logcat UI, on-device debugger; builds run headless via
      Gradle CLI regardless)
- [ ] Phone: enable developer options + USB debugging, authorize this machine

VPN note: WireGuard server runs on the UDM Pro and works — Phase 4 only needs an
additional peer profile for the app.

## Phase 1 — Project skeleton + first vertical slice (est. 2–4 weeks)

Goal: `urs-android` project exists with the target architecture, one feature works
online against the real backend (VPN switched on manually for now).

- Create the project in this directory (`12_` → consider renaming to `12_urs-android`).
- Stack: Compose + Material 3, Navigation Compose, Hilt (DI), Retrofit + OkHttp,
  kotlinx.serialization, `minSdk` ≥ 26, `targetSdk` latest.
- Architecture: MVVM + repository pattern, unidirectional data flow
  (UI → ViewModel → Repository → API/DB).
- Vertical slice: **body stats** — input form + list view against
  `GET/POST /api/v1/body-stats`. Smallest domain, exercises the whole stack.
- Base URL configurable (debug points at local dev backend).

Exit criteria: enter a weight on the phone, see it in the web app.

## Phase 2 — Offline-first core (est. 3–6 weeks)

Goal: the app works with no connectivity; sync happens when the backend is reachable.
This is the architectural heart of the project.

App side:

- Room database mirroring the domain entities.
- Writes go to Room + an outbox table (pending operations queue).
- WorkManager job: when backend reachable → push outbox, then pull changes.
- Pull refresh: fetch per resource with a `?since=<timestamp>` parameter.
- Sync status surfaced in the UI (pending count, last sync time).

Backend side (small, non-breaking additions):

- Client-generated UUIDs for new records (or idempotency keys) so retries are safe.
- `updated_at` columns + `?since=` query support on list endpoints.
- Web app keeps working unchanged during the transition.

Exit criteria: airplane mode → enter data → reconnect → data appears in backend;
second device sees it after pull.

## Phase 3 — Feature parity port (est. 2–4 months, iterative)

Order by daily-use value: input flows first, read views second, charts last.

1. **Fuel**: fill entry (biggest on-the-go win — this is where offline matters),
   stations, station creation, fuel stats.
2. **Gokart**: race time input, times list, tracks.
3. **Health**: stats views + charts. Charts with **Vico** (Compose-native chart
   library) as the Chart.js replacement.
4. **Users**: list/new/edit.
5. **Price monitor** views (talks to the price-radar service).

Each feature follows the Phase 2 pattern (Room entity → repository → sync → UI).

## Phase 4 — Embedded WireGuard (on-demand VPN)

Goal: the app brings the tunnel up by itself when it needs the backend.

- Embed `com.wireguard.android:tunnel`; the app becomes a `VpnService` provider
  (one-time user consent dialog).
- Store the WireGuard config encrypted (Android Keystore / EncryptedSharedPreferences).
- Logic: sync wanted → backend reachable? → if not and not on home Wi-Fi →
  tunnel up (split tunnel, only backend subnet) → sync → tunnel down.
- Constraint to know: Android allows only one active VPN at a time.
- Prerequisite: WireGuard endpoint exposed on the home infrastructure.

This can be developed in parallel with late Phase 3; until then the existing VPN
app is used manually.

## Phase 5 — CI/CD + release channel

Goal: `git tag` → signed APK on the phone.

- Generate a release keystore; store base64-encoded as GitLab CI variable
  (losing it means users must reinstall — back it up).
- Pipeline: lint + unit tests → `assembleRelease` → sign → attach APK to a
  GitLab Release.
- Obtainium on the phone tracks the GitLab project releases and installs updates.
- Versioning: semver tag drives `versionName`; auto-increment `versionCode`.

Can be pulled earlier (after Phase 1) if manual `adb install` gets annoying.

## Phase 6 — Native extras + web shutdown

- Home screen widgets (Glance): quick fuel entry, latest weight.
- GPS: suggest nearest filling station on fill entry; auto-detect gokart track.
- Camera: scan fuel receipts (ML Kit text recognition) as input helper.
- Multi-user/multi-device rollout for family; device identity for sync.
- App shortcuts, share targets, notifications as needed.
- When parity is confirmed in daily use: decommission the web frontend
  (keep the backend).

## Realistic expectations

As a complete Android beginner with limited hobby time: a genuinely useful app
(fuel + health entry, offline-capable) is achievable in ~2–3 months; full parity
including charts and WireGuard integration is more like 6–12 months. That is fine —
the web app keeps running until the app has earned the switch-off.
