package ch.mcfx.urs.watchrelay

/**
 * Shared secret between this relay and the `urs-zepp` watch app's side
 * service (must match the constant there exactly) — sent as the
 * `X-Relay-Token` header on every request. Not a strong security boundary
 * (fixed across installs, not per-device paired) — the actual threat this
 * guards against is another app on the same phone blind-guessing the
 * loopback endpoint, not a determined attacker with source access to either
 * private repo. Proportionate to what's at stake (a spoofed beer-log entry),
 * not to what a real pairing flow would cost to build.
 */
const val WATCH_RELAY_TOKEN = "33d248e9de3f6cd180d35718ca7d8464145a5dc3368535cc"
