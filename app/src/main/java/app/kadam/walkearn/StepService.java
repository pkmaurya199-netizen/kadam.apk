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
 * Counts steps continuously with multi-tier hardware support:
 * 1. Hardware Step Counter (TYPE_STEP_COUNTER)
 * 2. Real-time Step Detector (TYPE_STEP_DETECTOR)
 * 3. Universal Accelerometer peak detector fallback
 */
public class StepService extends Service implements SensorEventListener {
    static final String PREFS = "kadam_steps";
    private static final String CHANNEL = "kadam_steps_channel";

    private SensorManager sensorManager;
    private SharedPreferences prefs;
    private boolean listening = false;
    private int activeSensorType = -1;

    // For accelerometer step detection fallback
    private long lastStepTime = 0;
    private float[] gravity = new float[3];
    private float[] linear_acceleration = new float[3];

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
        if (sm == null) return false;
        return sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
                || sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) != null
                || sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null;
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
        return b.setContentTitle("StepMudra")
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
            // Fallback for notification foreground
            try {
                startForeground(1, buildNotification());
            } catch (Exception ignored) {
            }
        }

        if (!listening) {
            sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
            if (sensorManager == null) {
                stopSelf();
                return START_NOT_STICKY;
            }

            // Priority 1: Step Counter
            Sensor counter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
            // Priority 2: Step Detector (Zero-latency instant events)
            Sensor detector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
            // Priority 3: Accelerometer
            Sensor accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

            if (counter != null) {
                sensorManager.registerListener(this, counter, SensorManager.SENSOR_DELAY_UI);
                activeSensorType = Sensor.TYPE_STEP_COUNTER;
                listening = true;
            } else if (detector != null) {
                sensorManager.registerListener(this, detector, SensorManager.SENSOR_DELAY_UI);
                activeSensorType = Sensor.TYPE_STEP_DETECTOR;
                listening = true;
            } else if (accel != null) {
                sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME);
                activeSensorType = Sensor.TYPE_ACCELEROMETER;
                listening = true;
            } else {
                stopSelf();
                return START_NOT_STICKY;
            }
        }
        return START_STICKY;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        String day = today();
        String savedDay = prefs.getString("day", "");
        
        // Rollover check: If day changed, reset today's steps
        if (!day.equals(savedDay)) {
            prefs.edit()
                .putString("day", day)
                .putInt("steps", 0)
                .putLong("last_counter", -1L)
                .apply();
        }

        int currentSteps = prefs.getInt("steps", 0);

        if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
            long value = (long) event.values[0];
            long last = prefs.getLong("last_counter", -1L);
            long delta;
            
            if (last < 0) {
                // First reading of this sensor session
                delta = 0;
            } else if (value < last) {
                // Device rebooted
                delta = value;
            } else {
                delta = value - last;
            }

            prefs.edit().putLong("last_counter", value).apply();

            if (delta > 0) {
                currentSteps += (int) delta;
                prefs.edit().putString("day", day).putInt("steps", currentSteps).apply();
            }
        } else if (event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
            if (event.values[0] == 1.0f) {
                currentSteps += 1;
                prefs.edit().putString("day", day).putInt("steps", currentSteps).apply();
            }
        } else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            final float alpha = 0.8f;
            gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0];
            gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1];
            gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2];

            linear_acceleration[0] = event.values[0] - gravity[0];
            linear_acceleration[1] = event.values[1] - gravity[1];
            linear_acceleration[2] = event.values[2] - gravity[2];

            double magnitude = Math.sqrt(
                linear_acceleration[0] * linear_acceleration[0] +
                linear_acceleration[1] * linear_acceleration[1] +
                linear_acceleration[2] * linear_acceleration[2]
            );

            long now = System.currentTimeMillis();
            if (magnitude > 2.8 && (now - lastStepTime > 320)) {
                lastStepTime = now;
                currentSteps += 1;
                prefs.edit().putString("day", day).putInt("steps", currentSteps).apply();
            }
        }
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
