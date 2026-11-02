package app.kadam.walkearn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.IBinder;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Counts steps in the background with the phone's hardware step counter.
 * The counter keeps counting even when the app is closed; this service reads it
 * and keeps today's total in SharedPreferences.
 */
public class StepService extends Service implements SensorEventListener {
    static final String PREFS = "kadam_steps";
    private static final String CHANNEL = "kadam_steps_channel";

    private SensorManager sensorManager;
    private SharedPreferences prefs;
    private boolean listening = false;

    static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    static boolean hasPermission(Context c) {
        return Build.VERSION.SDK_INT < 29
                || c.checkSelfPermission(android.Manifest.permission.ACTIVITY_RECOGNITION)
                == PackageManager.PERMISSION_GRANTED;
    }

    static boolean hasSensor(Context c) {
        SensorManager sm = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
        return sm != null && sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null;
    }

    static int todaySteps(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (today().equals(p.getString("day", ""))) {
            return p.getInt("steps", 0);
        }
        return 0;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL, "Step tracking", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        return b.setContentTitle("Kadam")
                .setContentText("Step tracking active")
                .setSmallIcon(R.drawable.ic_stat)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!hasPermission(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            Notification n = buildNotification();
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH);
            } else {
                startForeground(1, n);
            }
        } catch (Exception e) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!listening) {
            sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
            Sensor s = sensorManager == null ? null : sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
            if (s == null) {
                stopSelf();
                return START_NOT_STICKY;
            }
            sensorManager.registerListener(this, s, SensorManager.SENSOR_DELAY_NORMAL);
            listening = true;
        }
        return START_STICKY;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        long value = (long) event.values[0];
        long last = prefs.getLong("last", -1L);
        long delta;
        if (last < 0) {
            delta = 0;               // first reading ever, nothing to add yet
        } else if (value < last) {
            delta = value;           // phone restarted, counter began again from zero
        } else {
            delta = value - last;
        }
        String day = today();
        int steps = day.equals(prefs.getString("day", "")) ? prefs.getInt("steps", 0) : 0;
        steps += (int) delta;
        prefs.edit().putString("day", day).putInt("steps", steps).putLong("last", value).apply();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    @Override
    public void onDestroy() {
        if (sensorManager != null) sensorManager.unregisterListener(this);
        listening = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
