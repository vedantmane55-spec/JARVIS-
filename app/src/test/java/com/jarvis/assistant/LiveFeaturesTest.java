package com.jarvis.assistant;

import android.content.Context;
import java.io.*;
import java.util.*;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
public class LiveFeaturesTest {
    String event(String chunk, boolean last) throws Exception {
        JSONObject candidate = new JSONObject().put("content",new JSONObject().put("parts",
            new JSONArray().put(new JSONObject().put("text",chunk))));
        if(last) candidate.put("finishReason","STOP");
        return "data: " + new JSONObject().put("candidates",new JSONArray().put(candidate)) + "\n\n";
    }
    @Test public void streamDisplaysIncrementallyAndKeepsActionsOutOfPreview() throws Exception {
        String one = "{\"reply\":\"Nam", two = "askar!\",\"actions\":[]}";
        List<String> visible = new ArrayList<>();
        String all = GeminiStream.read(new BufferedReader(new StringReader(event(one,false)+event(two,true))),visible::add);
        assertEquals(Arrays.asList("Nam","Namaskar!"),visible);
        assertEquals(one+two,all);
        assertThrows(IOException.class,()->GeminiStream.read(new BufferedReader(new StringReader(event(one,false))),t->{}));
    }
    @Test public void smartSwitchConfirmsReportedStateAfterServiceCall() throws Exception {
        Context c=RuntimeEnvironment.getApplication();
        c.getSharedPreferences("home_hub",0).edit().putString("entities","{\"switch.desk\":\"Desk\"}").commit();
        JSONObject step=new JSONObject().put("value","switch.desk").put("state","on");
        List<String> paths=new ArrayList<>();
        String reply=HomeHub.control(c,step,(base,token,path,data)->{
            paths.add(path);
            if(data!=null) { assertEquals("switch.desk",data.getString("entity_id")); return "[]"; }
            return "{\"state\":\"on\"}";
        });
        assertTrue(reply.contains("is on"));
        assertEquals(Arrays.asList("services/switch/turn_on","states/switch.desk"),paths);
    }
    @Test public void whatsappDraftRequiresMessageAndShowsRecipient() throws Exception {
        JSONObject step=new JSONObject().put("type","whatsapp").put("recipient","Rohit");
        assertThrows(Exception.class,()->Tasks.describe(step));
        assertTrue(Tasks.describe(step.put("value","Reached home")).contains("Rohit: Reached home"));
    }
}
