package org.androdevlinux.utxo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.androdevlinux.utxo.widget.scheduleWidgetUpdate

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Reschedule the widget updates after a reboot
            scheduleWidgetUpdate(context)
        }
    }
}
