package ir.tenderwatch

import android.app.Application
import ir.tenderwatch.sync.Notifier
import ir.tenderwatch.sync.SyncWorker

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannel(this)
        SyncWorker.schedule(this)
    }
}
