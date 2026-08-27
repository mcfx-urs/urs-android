package ch.mcfx.urs.watchrelay

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import ch.mcfx.urs.MainActivity
import ch.mcfx.urs.R
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.beer.BeerStats
import ch.mcfx.urs.notifications.NotificationChannels
import ch.mcfx.urs.vpn.NetworkGate
import fi.iki.elonen.NanoHTTPD
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking

private const val RELAY_PORT = 8787
private const val BEER_FILL_PATH = "/api/watch/beer-fill"
private const val TOKEN_HEADER = "x-relay-token"
private const val VOLUME_PARAM = "volume"
private const val NOTIFICATION_ID = 1

/**
 * Foreground service hosting a loopback-only HTTP server so the `urs-zepp`
 * watch app's Bluetooth-relayed requests (via its Zepp App side-service,
 * which does have real network access unlike the watch itself) can trigger
 * an authenticated backend call without the watch or the Zepp App ever
 * needing urs-backend's WireGuard tunnel or JWT session — both already
 * exist in this process. The first persistent [Service] in this codebase
 * (the VPN tunnel is brought up on demand, not always-on — see
 * [NetworkGate]'s own doc comment) — opt-in via Settings, matching
 * [ch.mcfx.urs.location.LocationHistorySettingsStore]'s toggle pattern.
 */
class WatchRelayService : Service() {

    private var server: NanoHTTPD? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundWithNotification()
        val container = (application as UrsApplication).container
        server = RelayHttpServer(container.networkGate) { volumeMl ->
            container.beerRepository.logBeer(volumeMl, LocalDateTime.now().format(BeerStats.DATE_FORMAT))
        }.also { it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        server?.stop()
        server = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundWithNotification() {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(this, NotificationChannels.WATCH_RELAY)
            .setSmallIcon(R.drawable.ic_notification_bear)
            .setContentTitle(getString(R.string.watch_relay_notification_title))
            .setContentText(getString(R.string.watch_relay_notification_body))
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        fun start(context: Context) {
            context.startForegroundService(Intent(context, WatchRelayService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WatchRelayService::class.java))
        }
    }
}

/**
 * Bound to `127.0.0.1` only — reachable from any app on the same phone (not
 * just the Zepp App), not from the network. [TOKEN_HEADER] is the only
 * guard against another local app blind-posting fake fills, see
 * [WATCH_RELAY_TOKEN]'s doc comment for why that's proportionate here.
 * [NanoHTTPD.serve] runs synchronously on one of NanoHTTPD's own worker
 * threads (never the main thread), so blocking it with [runBlocking] to
 * call the suspend repository/gate functions is safe and keeps the request
 * genuinely synchronous — the watch app's `httpRequest` call is waiting on
 * exactly this response.
 */
private class RelayHttpServer(
    private val networkGate: NetworkGate,
    private val onBeerFill: suspend (volumeMl: Int) -> Unit,
) : NanoHTTPD("127.0.0.1", RELAY_PORT) {

    override fun serve(session: IHTTPSession): Response {
        if (session.method != Method.POST || session.uri != BEER_FILL_PATH) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "not found")
        }
        if (session.headers[TOKEN_HEADER] != WATCH_RELAY_TOKEN) {
            return newFixedLengthResponse(Response.Status.UNAUTHORIZED, MIME_PLAINTEXT, "unauthorized")
        }
        val volumeMl = session.parameters[VOLUME_PARAM]?.firstOrNull()?.toIntOrNull()
        if (volumeMl == null || volumeMl <= 0) {
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "invalid volume")
        }
        return runBlocking {
            val reachable = when (networkGate.ensureReachable()) {
                NetworkGate.Result.OnHomeNetwork, NetworkGate.Result.Connected -> true
                else -> false
            }
            if (!reachable) {
                return@runBlocking newFixedLengthResponse(Response.Status.SERVICE_UNAVAILABLE, MIME_PLAINTEXT, "backend unreachable")
            }
            try {
                onBeerFill(volumeMl)
                newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "ok")
            } catch (e: Exception) {
                newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "failed: ${e.message}")
            }
        }
    }
}
