package ch.mcfx.urs.watchrelay

import ch.mcfx.urs.BuildConfig

/**
 * Shared secret between this relay and the `urs-zepp` watch app's side
 * service (must match the value there exactly) — sent as the
 * `X-Relay-Token` header on every request. Not a strong security boundary
 * (fixed across installs, not per-device paired) — the actual threat this
 * guards against is another app on the same phone blind-guessing the
 * loopback endpoint, not a determined attacker with source access to either
 * private repo. Proportionate to what's at stake (a spoofed beer-log entry),
 * not to what a real pairing flow would cost to build. Sourced from
 * `relay.properties` (gitignored, see `app/build.gradle.kts`), not
 * hardcoded here, so the value never sits in the tracked source tree.
 */
val WATCH_RELAY_TOKEN: String = BuildConfig.WATCH_RELAY_TOKEN
