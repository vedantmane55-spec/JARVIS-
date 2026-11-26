package com.jarvis.assistant;

import android.app.AlertDialog;
import android.content.*;
import android.widget.*;
import org.json.*;
import java.net.*;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.util.*;

final class HomeHub {
    private static SharedPreferences settings(Context c) { return c.getSharedPreferences("home_hub",0); }
    static String labels(Context c) { return settings(c).getString("entities","{}"); }
    static boolean supported(String id) { return id.matches("(light|switch)\\.[a-z0-9_]+"); }
    static String request(String base,String token,String path,JSONObject data) throws Exception {
        URL url = new URL(base + "/api/" + path);
        HttpsURLConnection conn = (HttpsURLConnection)url.openConnection();
        try {
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(8000); conn.setReadTimeout(10000);
            conn.setRequestProperty("Authorization","Bearer " + token);
            if (data != null) {
                conn.setRequestMethod("POST"); conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type","application/json");
                try(OutputStream out = conn.getOutputStream()) { out.write(data.toString().getBytes("UTF-8")); }
            }
            int code = conn.getResponseCode();
            if (code != 200 && code != 201) throw new IOException("Hub returned HTTP " + code);
            try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(),"UTF-8"))) {
                StringBuilder body = new StringBuilder(); String line;
                while ((line=in.readLine())!=null) body.append(line);
                return body.toString();
            }
        } finally { conn.disconnect(); }
    }
    static void configure(MainActivity ui) {
        SharedPreferences p = settings(ui);
        LinearLayout form = new LinearLayout(ui); form.setOrientation(1);
        EditText host = new EditText(ui), secret = new EditText(ui);
        host.setHint("Home Assistant HTTPS address"); host.setText(p.getString("base",""));
        secret.setSingleLine(); secret.setInputType(129); secret.setHint("Home Assistant access token");
        form.addView(host); form.addView(secret);
        new AlertDialog.Builder(ui).setTitle("Connect Home Assistant").setView(form)
            .setNegativeButton("Cancel",null).setPositiveButton("Find lights and switches",(d,w)->{
                String base = host.getText().toString().trim().replaceAll("/+$","");
                String token = secret.getText().toString().trim();
                if(token.isEmpty()) { ui.status.setText("Enter the token for this server."); return; }
                new Thread(()->{
                    try {
                        URL dest = new URL(base);
                        if (!dest.getProtocol().equals("https") || dest.getHost().isEmpty() || dest.getUserInfo()!=null || dest.getQuery()!=null || dest.getRef()!=null)
                            throw new IOException("Enter an HTTPS server address");
                        JSONArray states = new JSONArray(request(base,token,"states",null));
                        JSONObject entities = new JSONObject();
                        for(int i=0;i<states.length();i++) {
                            JSONObject state = states.getJSONObject(i);
                            String id = state.optString("entity_id");
                            if(supported(id)) entities.put(id,state.getJSONObject("attributes").optString("friendly_name",id));
                        }
                        p.edit().putString("base",base).putString("token",token).putString("entities",entities.toString()).apply();
                        ui.runOnUiThread(()->{ if(!ui.isDestroyed()) ui.status.setText("Connected: " + entities.length() + " lights and switches."); });
                    } catch(Exception e) { ui.runOnUiThread(()->ui.status.setText("Connection failed. Check HTTPS address, network and token.")); }
                }).start();
            }).show();
    }
    interface Transport { String call(String base,String token,String path,JSONObject data) throws Exception; }
    static String control(Context c,JSONObject step) throws Exception { return control(c,step,HomeHub::request); }
    static String control(Context c,JSONObject step, Transport transport) throws Exception {
        SharedPreferences p = settings(c);
        String id = step.getString("value"), state = step.getString("state");
        if(!supported(id) || !new JSONObject(labels(c)).has(id) || !(state.equals("on")||state.equals("off")))
            throw new IOException("Unknown device or state");
        String base=p.getString("base",""), token=p.getString("token","");
        transport.call(base,token,"services/"+id.split("\\.")[0]+"/turn_"+state,new JSONObject().put("entity_id",id));
        for(int attempt=0;attempt<4;attempt++) {
            if (attempt>0) Thread.sleep(500);
            JSONObject observed = new JSONObject(transport.call(base,token,"states/"+id,null));
            if(state.equals(observed.optString("state"))) return id + " is " + state + " according to Home Assistant.";
        }
        return "Command sent. Home Assistant has not confirmed " + state + ".";
    }
    static void select(MainActivity ui) {
        try {
            JSONObject entities = new JSONObject(labels(ui));
            ArrayList<String> ids = new ArrayList<>();
            for (Iterator<String> it = entities.keys(); it.hasNext();) ids.add(it.next());
            Collections.sort(ids);
            if(ids.isEmpty()) { ui.status.setText("Connect a Home Assistant server with lights or switches first."); return; }
            String[] names=new String[ids.size()];
            for(int i=0;i<names.length;i++) names[i]=entities.getString(ids.get(i))+" ("+ids.get(i)+")";
            new AlertDialog.Builder(ui).setTitle("Choose device").setItems(names,(dialog,index)->
                new AlertDialog.Builder(ui).setTitle(names[index]).setItems(new String[]{"Turn on","Turn off"},(d,which)->{
                    if(ui.service==null || ui.service.busy || ui.service.executing) return;
                    try {
                        ui.service.plan = new JSONArray().put(new JSONObject().put("type","home").put("value",ids.get(index)).put("state",which==0?"on":"off"));
                        ui.runStep(ui.service,ui.service.plan);
                    } catch(JSONException e) { ui.status.setText("Invalid device"); }
                }).show()).show();
        } catch(JSONException e) { ui.status.setText("Reconnect Home Assistant"); }
    }
}
