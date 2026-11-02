package app.kadam.walkearn;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Starts step tracking again after the phone restarts. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        if (!StepService.hasPermission(context) || !StepService.hasSensor(context)) return;
        try {
            Intent s = new Intent(context, StepService.class);
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(s);
            } else {
                context.startService(s);
            }
        } catch (Exception ignored) {
            // The app will start tracking again the next time it is opened.
        }
    }
}
