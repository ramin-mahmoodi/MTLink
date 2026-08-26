package ir.mtlink.client

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.concurrent.Executors

class PeriodicTestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = MTLinkStore(context)
        if (!store.appPreferences().periodicTestEnabled) return
        val pending = goAsync()
        val executor = Executors.newSingleThreadExecutor()
        executor.execute {
            try {
                val stored = store.proxies()
                val candidates = stored.sortedWith(compareByDescending<ProxyRecord> { it.favorite }.thenBy { it.testedAt }).take(24)
                val workers = Executors.newFixedThreadPool(4)
                try {
                    val updated = candidates.map { proxy -> workers.submit<ProxyRecord> { ProxyTestRunner.test(proxy) } }.map { it.get() }
                    val byId = updated.associateBy { it.id }
                    store.saveProxies(stored.map { byId[it.id] ?: it })
                } finally {
                    workers.shutdownNow()
                }
            } finally {
                // fixed: always finish goAsync work and release its one-shot executor.
                pending.finish()
                executor.shutdown()
            }
        }
    }
}
