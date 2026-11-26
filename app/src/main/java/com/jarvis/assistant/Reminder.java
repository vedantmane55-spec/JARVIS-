package com.jarvis.assistant;

import android.app.*;
import android.content.*;
import android.os.Build;
import android.net.Uri;
import org.json.*;
import java.util.*;

public class Reminder extends BroadcastReceiver {
    private static PendingIntent intent(Context c,String id) {
        Intent i = new Intent(c,Reminder.class).setData(Uri.parse("jarvis-reminder:" + id));
        return PendingIntent.getBroadcast(c,0,i,PendingIntent.FLAG_IMMUTABLE);
    }
    private static void arm(Context c,String id,long time) {
        c.getSystemService(AlarmManager.class).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,time,intent(c,id));
    }
    static void schedule(Context c,String title,long time) throws Exception {
        String id = UUID.randomUUID().toString();
        NotificationManager manager = c.getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(new NotificationChannel("reminders","Reminders",NotificationManager.IMPORTANCE_HIGH));
        if (Build.VERSION.SDK_INT >= 24 && !manager.areNotificationsEnabled()) throw new Exception("Enable Jarvis notifications for reminders");
        JSONObject row = new JSONObject().put("title",title).put("time",time);
        c.getSharedPreferences("reminders",0).edit().putString(id,row.toString()).apply();
        arm(c,id,time);
    }
    public void onReceive(Context c,Intent i) {
        SharedPreferences p = c.getSharedPreferences("reminders",0);
        try {
            if (Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())) {
                for (String id : p.getAll().keySet()) arm(c,id,new JSONObject(p.getString(id,"{}")).getLong("time"));
                return;
            }
            if (i.getData() == null) return;
            String id = i.getData().getSchemeSpecificPart();
            String title = new JSONObject(p.getString(id,"{}")).getString("title");
            Notification.Builder n = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c,"reminders") : new Notification.Builder(c);
            c.getSystemService(NotificationManager.class).notify(id,1,n.setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("Jarvis reminder").setContentText(title).setAutoCancel(true)
                .setContentIntent(PendingIntent.getActivity(c,0,new Intent(c,MainActivity.class),PendingIntent.FLAG_IMMUTABLE)).build());
            p.edit().remove(id).apply();
        } catch (JSONException ignored) {}
    }
}
