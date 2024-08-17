package org.androdevlinux.utxo

import android.app.Application
import org.androdevlinux.utxo.widget.scheduleWidgetUpdate

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        scheduleWidgetUpdate(this)
    }
}
