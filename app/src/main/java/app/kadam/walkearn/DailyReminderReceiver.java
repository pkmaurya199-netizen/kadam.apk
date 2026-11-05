package app.kadam.walkearn;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Sends motivating push notifications in the evening (6:00 PM & 8:45 PM)
 * ONLY to users who have NOT completed today's daily challenge.
 */
public class DailyReminderReceiver extends BroadcastReceiver {
    public static final String ACTION_REMINDER_6PM = "app.kadam.walkearn.ACTION_REMINDER_6PM";
    public static final String ACTION_REMINDER_845PM = "app.kadam.walkearn.ACTION_REMINDER_845PM";

    private static final String CHANNEL_ID = "kadam_daily_reminder_channel";
    private static final int NOTIF_ID_6PM = 1001;
    private static final int NOTIF_ID_845PM = 1002;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();

        // Condition: ONLY send if daily challenge is NOT completed yet today!
        if (isDailyChallengeCompleted(context)) {
            return;
        }

        String title;
        String message;
        int notifId;

        if (ACTION_REMINDER_845PM.equals(action)) {
            title = "⏳ Raat 12 baje se pehle coins claim karein!";
            message = "Aaj ka daily reward expire hone wala hai! Thoda tehal kar challenge pura karein aur bonus coins claim karein 🚶‍♂️✨";
            notifId = NOTIF_ID_845PM;
        } else {
            title = "🏃‍♂️ Aaj ka Daily Challenge baaki hai!";
            message = "Sirf thode kadam baaki hain! Raat hone se pehle daily challenge pura karein aur apne coins paayein 🪙🔥";
            notifId = NOTIF_ID_6PM;
        }

        showNotification(context, title, message, notifId);

        // Reschedule for next day
        scheduleAlarms(context);
    }

    private boolean isDailyChallengeCompleted(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(StepService.PREFS, Context.MODE_PRIVATE);
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());

            // 1. Check if challenge was claimed/completed today in app
            boolean challengeClaimedToday = today.equals(prefs.getString("challenge_completed_day", ""));
            if (challengeClaimedToday) {
                return true;
            }

            // 2. Check if user already reached 10,000 steps today
            int steps = StepService.todaySteps(context);
            if (steps >= 10000) {
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private void showNotification(Context context, String title, String message, int notifId) {
        try {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;

            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        "Daily Challenge Reminders",
                        NotificationManager.IMPORTANCE_HIGH
                );
                channel.setDescription("Reminders for unfinished daily step challenges");
                channel.enableVibration(true);
                nm.createNotificationChannel(channel);
            }

            Intent openApp = new Intent(context, MainActivity.class);
            openApp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(
                    context,
                    notifId,
                    openApp,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
            );

            Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(context, CHANNEL_ID)
                    : new Notification.Builder(context);

            builder.setContentTitle(title)
                    .setContentText(message)
                    .setStyle(new Notification.BigTextStyle().bigText(message))
                    .setSmallIcon(R.drawable.ic_stat)
                    .setAutoCancel(true)
                    .setContentIntent(pi);

            nm.notify(notifId, builder.build());
        } catch (Exception ignored) {
        }
    }

    /**
     * Schedules the two daily evening reminders:
     * 1. 6:00 PM (18:00)
     * 2. 8:45 PM (20:45)
     */
    public static void scheduleAlarms(Context context) {
        try {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;

            // Alarm 1: 6:00 PM (18:00)
            scheduleAlarmAt(context, am, 18, 0, ACTION_REMINDER_6PM, 2001);

            // Alarm 2: 8:45 PM (20:45)
            scheduleAlarmAt(context, am, 20, 45, ACTION_REMINDER_845PM, 2002);
        } catch (Exception ignored) {
        }
    }

    private static void scheduleAlarmAt(Context context, AlarmManager am, int hour, int minute, String action, int reqCode) {
        try {
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, hour);
            cal.set(Calendar.MINUTE, minute);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);

            if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
                cal.add(Calendar.DAY_OF_YEAR, 1);
            }

            Intent intent = new Intent(context, DailyReminderReceiver.class);
            intent.setAction(action);
            PendingIntent pi = PendingIntent.getBroadcast(
                    context,
                    reqCode,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
            );

            try {
                if (Build.VERSION.SDK_INT >= 23) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
                } else {
                    am.setExact(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
                }
            } catch (SecurityException se) {
                if (Build.VERSION.SDK_INT >= 23) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
                } else {
                    am.set(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
                }
            }
        } catch (Exception ignored) {
        }
    }
}
