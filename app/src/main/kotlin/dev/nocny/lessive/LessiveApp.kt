package dev.nocny.lessive

import android.app.Application
import dev.nocny.lessive.notify.WashNotifier

class LessiveApp : Application() {
    override fun onCreate() {
        super.onCreate()
        WashNotifier.createChannels(this)
    }
}
