package com.jarvis.assistant;

import android.content.Context;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
public class TasksTest {
    @Test public void contextIncludesRememberedFactsAndOnlyDeviceNames() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        Tasks.save(app,"devices","Study lamp","https://example.org/private-token");
        Tasks.save(app,"notes","Use easy English","1");
        String context = Tasks.context(app);
        assertTrue(context.contains("Study lamp"));
        assertTrue(context.contains("Use easy English"));
        assertFalse(context.contains("private-token"));
    }
    @Test public void invalidDatesAndTimesCannotRun() throws Exception {
        JSONObject alarm = new JSONObject().put("type","alarm").put("hour",25).put("minute",0);
        assertThrows(Exception.class,()->Tasks.describe(alarm));
        JSONObject reminder = new JSONObject().put("type","reminder").put("value","Study").put("when","2099-02-31 10:00");
        assertThrows(Exception.class,()->Tasks.describe(reminder));
    }
    @Test public void reminderSurvivesServiceRestart() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        Reminder.schedule(app,"Revise",System.currentTimeMillis()+600000);
        assertEquals(1,app.getSharedPreferences("reminders",0).getAll().size());
        org.robolectric.shadows.ShadowAlarmManager alarms = org.robolectric.Shadows.shadowOf(app.getSystemService(android.app.AlarmManager.class));
        assertNotNull(alarms.peekNextScheduledAlarm());
    }
}
