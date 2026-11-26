package com.jarvis.assistant;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.speech.*;
import android.speech.tts.TextToSpeech;
import android.widget.*;
import android.view.*;
import java.util.*;
import java.net.*;
import java.io.*;
import org.json.*;

import android.app.*;
import android.content.*;
import android.os.*;

public class JarvisService extends Service {
    final class LocalBinder extends Binder {
        JarvisService service() { return JarvisService.this; }
    }
    final LocalBinder binder = new LocalBinder();
    MainActivity screen;
    float micLevel;
    long levelAt, studioRetryAfter;
    boolean renderingVoice;
    int speechGeneration;
    android.media.MediaPlayer studioPlayer;
    File studioFile;
    boolean speakingNow() {
        return studioPlayer != null || (voiceReady && voice != null && voice.isSpeaking());
    }
    void stopStudio() {
        speechGeneration++;
        renderingVoice = false;
        if(studioPlayer!=null) { studioPlayer.release(); studioPlayer=null; }
        if(studioFile!=null) { studioFile.delete(); studioFile=null; }
    }
    String lastReply = "";
    void reply(String text) {
        lastReply = text;
        if (screen != null) screen.answer.setText(text);
    }
    String statusText = "SYSTEM ONLINE";
    boolean destroyed, executing;
    PowerManager.WakeLock listeningLock;
    final Runnable recognizerTimeout = this::recoverRecognizer;
    void recoverRecognizer() {
        if (!active || !listening || mic == null) return;
        listening = false;
        mic.cancel();
        handler.postDelayed(this::listenNow,1000);
    }
    void releaseListeningLock() {
        if (listeningLock != null && listeningLock.isHeld()) listeningLock.release();
    }
    JSONArray plan = new JSONArray();
    void status(String text) {
        statusText = text;
        if (screen != null) screen.status.setText(text);
    }
    public IBinder onBind(Intent intent) { return binder; }

    public int onStartCommand(Intent intent, int flags, int id) {
        if (intent == null || "STOP".equals(intent.getAction())) {
            standby();
            return START_NOT_STICKY;
        }
        NotificationManager manager = getSystemService(NotificationManager.class);
        Notification.Builder note;
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(new NotificationChannel("listening", "Voice listening", NotificationManager.IMPORTANCE_LOW));
            note = new Notification.Builder(this, "listening");
        } else {
            note = new Notification.Builder(this);
        }
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, JarvisService.class).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent talk = PendingIntent.getService(this, 2, new Intent(this, JarvisService.class).setAction("TALK"), PendingIntent.FLAG_IMMUTABLE);
        startForeground(1, note.setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Jarvis microphone active").setContentText("Say Hey Jarvis")
            .setOngoing(true).setContentIntent(open).addAction(0,"Speak",talk).addAction(0, "Stop", stop).build());
        if ("TALK".equals(intent.getAction())) awake = true;
        activate();
        return START_NOT_STICKY;
    }
    void standby() {
        stopStudio();
        releaseListeningLock();
        active = awake = listening = false;
        handler.removeCallbacksAndMessages(null);
        if (mic != null) { mic.destroy(); mic = null; }
        if (voice != null) voice.stop();
        status("STANDBY");
        stopForeground(true);
        stopSelf();
    }
    SpeechRecognizer mic;
    TextToSpeech voice;
    Handler handler = new Handler();

    boolean active = false;
    boolean awake = false;
    boolean busy = false;
    boolean voiceReady = false;
    boolean listening = false;

    JSONArray history = new JSONArray();

    @Override public void onCreate() {
        super.onCreate();
        try {
            voice = new TextToSpeech(this, result -> {
                handler.post(() -> {
                    if (result == TextToSpeech.SUCCESS
                        && voice != null) {

                        voice.setLanguage(Locale.UK);
                        voice.setPitch(0.78f);
                        voice.setSpeechRate(0.9f);
                        voiceReady = true;
                    }
                });
            });
        } catch (Exception e) {
            status("Voice unavailable");
        }

        try {
            history = new JSONArray(
                getSharedPreferences("MainActivity", MODE_PRIVATE)
                .getString("history", "[]"));
        } catch (Exception e) {}
    }

    void activate() {

        if (!SpeechRecognizer
            .isRecognitionAvailable(this)) {

            standby();
            status("Speech recognition unavailable");
            return;
        }

        if (listeningLock == null) {
            listeningLock = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Jarvis:Listening");
            listeningLock.setReferenceCounted(false);
        }
        if (!listeningLock.isHeld()) listeningLock.acquire();
        active = true;

        if (mic == null) {

            mic = SpeechRecognizer
                .createSpeechRecognizer(this);

            mic.setRecognitionListener(
                new RecognitionListener() {

                public void onReadyForSpeech(Bundle b) {}
                public void onBeginningOfSpeech() {}
                public void onRmsChanged(float db) {
                    micLevel=Math.max(0f,Math.min(1f,(db+2f)/12f));
                    levelAt=SystemClock.uptimeMillis();
                }
                public void onBufferReceived(byte[] b) {}
                public void onEndOfSpeech() {}
                public void onPartialResults(Bundle b) {}
                public void onEvent(int i, Bundle b) {}

                public void onError(int error) {
                    handler.removeCallbacks(recognizerTimeout);

                    listening = false;

                    if (error ==
                        SpeechRecognizer
                        .ERROR_INSUFFICIENT_PERMISSIONS) {

                        standby();
                        status(
                            "Microphone permission required");

                    } else {
                        handler.postDelayed(
                            () -> listenNow(), 1800);
                    }
                }

                public void onResults(Bundle b) {
                    handler.removeCallbacks(recognizerTimeout);

                    listening = false;

                    if (!active) return;

                    ArrayList<String> results =
                        b.getStringArrayList(
                            SpeechRecognizer
                            .RESULTS_RECOGNITION);

                    if (results == null ||
                        results.isEmpty()) {

                        listenNow();
                        return;
                    }

                    String text = results.get(0);

                    String lower =
                        text.toLowerCase(Locale.ROOT);

                    java.util.regex.Matcher wake = java.util.regex.Pattern
                        .compile("(?:hey|hello|हे|हेलो|हॅलो)\\s+(?:jarvis|जार्विस|जारविस|जार्विस्)", java.util.regex.Pattern.CASE_INSENSITIVE)
                        .matcher(text);
                    if (wake.find()) {
                        awake = true;
                        String rest = text.substring(wake.end()).trim();
                        if (rest.isEmpty()) {
                            speak("Yes sir, how can I help?");
                        } else {
                            command(rest);
                        }

                    } else if (awake) {

                        command(text);

                    } else {

                        listenNow();
                    }
                }
            });
        }

        listenNow();
    }

    void listenNow() {

        if (!active || busy ||
            listening || mic == null) return;

        if (renderingVoice || speakingNow()) {

            handler.postDelayed(
                () -> listenNow(), 800);
            return;
        }

        Intent i = new Intent(
            RecognizerIntent.ACTION_RECOGNIZE_SPEECH);

        i.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);

        i.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE, new String[]{"en-IN","hi-IN","mr-IN"}[getSharedPreferences("MainActivity", MODE_PRIVATE).getInt("language",0)]);

        status(
            awake ? "LISTENING..." : "SAY HEY JARVIS");

        try {
            listening = true;
            mic.startListening(i);
            handler.removeCallbacks(recognizerTimeout);
            handler.postDelayed(recognizerTimeout,60000);
        } catch (Exception e) {
            listening = false;
            status("Retrying microphone");
            handler.postDelayed(this::listenNow,1800);
        }
    }

    void speak(String text) {

        status(text);

        stopStudio();
        if (voiceReady) voice.stop();
        if (listening && mic != null) {
            listening=false;
            mic.cancel();
            handler.removeCallbacks(recognizerTimeout);
        }
        android.content.SharedPreferences prefs=getSharedPreferences("MainActivity",MODE_PRIVATE);
        String key=prefs.getString("key","");
        if (prefs.getBoolean("studio_voice",true) && !key.isEmpty() && SystemClock.uptimeMillis()>=studioRetryAfter) {
            renderingVoice=true;
            int ticket=speechGeneration;
            new Thread(()->{
                try {
                    File file=StudioVoice.render(this,key,text);
                    handler.post(()->{
                        if (ticket!=speechGeneration || destroyed) { file.delete(); return; }
                        try {
                            studioFile=file; studioPlayer=new android.media.MediaPlayer();
                            studioPlayer.setDataSource(file.getAbsolutePath());
                            studioPlayer.setOnCompletionListener(player->{ stopStudio(); listenNow(); });
                            studioPlayer.setOnErrorListener((player,what,extra)->{ stopStudio(); speakLocal(text); return true; });
                            studioPlayer.setOnPreparedListener(player->{ renderingVoice=false; player.start(); });
                            studioPlayer.prepareAsync();
                        } catch(Exception e) { stopStudio(); speakLocal(text); }
                    });
                } catch(Exception e) {
                    handler.post(()->{
                        if(ticket!=speechGeneration || destroyed) return;
                        renderingVoice=false;
                        studioRetryAfter=SystemClock.uptimeMillis()+60000;
                        speakLocal(text);
                    });
                }
            }).start();
            return;
        }
        speakLocal(text);
    }
    void speakLocal(String text) {
        if (voiceReady && voice != null) {
            int lang = getSharedPreferences("MainActivity", MODE_PRIVATE).getInt("language",0);
            voice.setLanguage(lang == 0 ? Locale.UK : new Locale(lang == 1 ? "hi" : "mr", "IN"));
            voice.speak(text,
                TextToSpeech.QUEUE_FLUSH,
                null, "jarvis");
        }

        handler.postDelayed(
            () -> listenNow(), 1200);
    }

    void command(String text) {

        if (text.trim().isEmpty() || busy || executing) return;

        if (mic != null && listening) {
            mic.cancel();
            listening = false;
        }

        String q = text.trim().toLowerCase(Locale.ROOT).replaceAll("[?!.।]+$", "");
        if (q.matches("(?:(?:open|show) )?jarvis settings|जार्विस सेटिंग्स उघड|जार्विस सेटिंग्स खोलो")) {
            if(screen!=null && screen.hasWindowFocus()) screen.showSettings(true);
            speak("Jarvis settings."); return;
        }
        if (q.matches("show (?:the )?orb|voice mode|ऑर्ब दाखव|ऑर्ब दिखाओ")) {
            if(screen!=null) screen.showSettings(false);
            speak("At your service."); return;
        }
        if (q.equals("stop listening") || q.equals("ऐकणे थांबव") || q.equals("सुनना बंद करो")) { standby(); return; }
        switch (q) {
            case "आवाज वाढव": case "आवाज बढ़ाओ": case "आवाज़ बढ़ाओ": case "aawaz vadhav": q = "volume up"; break;
            case "आवाज कमी कर": case "आवाज कम करो": case "आवाज़ कम करो": case "aawaz kami kar": q = "volume down"; break;
            case "सेटिंग उघड": case "सेटिंग्स उघड": case "सेटिंग्स खोलो": q = "open settings"; break;
            case "बॅटरी किती आहे": case "बैटरी कितनी है": case "battery kiti ahe": q = "battery status"; break;
            case "किती वाजले": case "कितने बजे हैं": q = "what time"; break;
        }

        if (q.contains("go to sleep")) {
            awake = false;
            speak("Standing by, sir.");
            return;
        }

        if (q.equals("open settings")) {
            try { openActivity(new Intent(android.provider.Settings.ACTION_SETTINGS)); speak("Opening settings, sir."); } catch (Exception e) { speak("Settings unavailable."); }
            return;
        }
        if (q.equals("volume up") || q.equals("volume down")) {
            android.media.AudioManager audio = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
            audio.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, q.equals("volume up") ? 1 : -1, android.media.AudioManager.FLAG_SHOW_UI);
            speak("Volume adjusted, sir."); return;
        }
        if (q.equals("battery status")) {
            android.os.BatteryManager battery = (android.os.BatteryManager) getSystemService(BATTERY_SERVICE);
            speak("Battery is at " + battery.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) + " percent, sir."); return;
        }
        if (q.contains("what time")) {
            speak(new java.text.SimpleDateFormat(
                "hh:mm a", Locale.UK)
                .format(new Date()));
            return;
        }

        if (q.contains("clear memory")) {
            history = new JSONArray();
            getSharedPreferences("MainActivity", MODE_PRIVATE).edit()
                .remove("history").remove("notes").apply();
            speak("Memory cleared, sir.");
            return;
        }

        if (q.startsWith("open youtube")) {
            openWebsite("https://youtube.com");
            return;
        }

        if (q.startsWith("open google")) {
            openWebsite("https://google.com");
            return;
        }

        if (q.startsWith("open instagram")) {
            openWebsite("https://instagram.com");
            return;
        }

        ask(text,null);
    }
    void ask(String text,android.net.Uri image) {
        if (busy || executing) return;
        plan = new JSONArray();
        String key = getSharedPreferences("MainActivity", MODE_PRIVATE)
            .getString("key", "");

        if (key.isEmpty()) {
            speak("Please save your Gemini API key.");
            return;
        }

        busy = true;
        status("PROCESSING...");

        String replyLanguage = new String[]{"English","Hindi","Marathi"}[getSharedPreferences("MainActivity", MODE_PRIVATE).getInt("language",0)];
        new Thread(() -> {

            try {

                JSONObject body = new JSONObject();

                JSONArray messages =
                    new JSONArray(history.toString());

                messages.put(message("user", text));

                JSONArray requestMessages = new JSONArray(messages.toString());
                if (image != null) requestMessages.getJSONObject(requestMessages.length()-1).getJSONArray("parts").put(
                    new JSONObject().put("inline_data",new JSONObject().put("mime_type","image/jpeg").put("data",MainActivity.imageData(this,image))));
                body.put("contents", requestMessages);
                body.put("generationConfig",new JSONObject().put("responseMimeType","application/json"));

                JSONObject instruction =
                    new JSONObject();

                instruction.put("parts",
                    new JSONArray().put(
                        new JSONObject().put("text",
                        Tasks.RULES + Tasks.context(this) + " You are JARVIS, a helpful AI "
                        + "assistant. Reply briefly and "
                        + "clearly in " + replyLanguage + ". Address user as sir.")));

                body.put(
                    "system_instruction", instruction);

                JSONObject response = new JSONObject(generate(key, body));
                String answer = response.getString("reply");
                JSONArray proposed = response.optJSONArray("actions");
                JSONArray steps = proposed == null || image != null ? new JSONArray() : proposed;
                if (steps.length() > 8) throw new JSONException("Too many steps");
                for (int i=0;i<steps.length();i++) Tasks.describe(steps.getJSONObject(i));

                messages.put(
                    message("model", answer));

                while (messages.length() > 20) {
                    messages.remove(0);
                    messages.remove(0);
                }

                handler.post(() -> {

                    if (destroyed) return;

                    history = messages;

                    getSharedPreferences("MainActivity", MODE_PRIVATE).edit()
                        .putString("history",
                            history.toString())
                        .apply();

                    busy = false;
                    reply(answer);
                    plan = steps;
                    if (steps.length() == 0) speak(answer);
                    else {
                        speak("Open Jarvis to review " + steps.length() + " proposed steps.");
                        if (screen != null && screen.hasWindowFocus()) screen.showPlan();
                    }
                });

            } catch (Exception e) {

                handler.post(() -> {

                    if (destroyed) return;

                    busy = false;
                    reply("Gemini reply failed: " + e.getMessage());
                    speak(lastReply);
                });
            }

        }).start();
    }

    JSONObject request(String key, String endpoint, JSONObject body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL("https://generativelanguage.googleapis.com/v1beta/" + endpoint).openConnection();
        try {
            c.setRequestProperty("x-goog-api-key", key);
            c.setConnectTimeout(15000); c.setReadTimeout(35000);
            if (body != null) {
                c.setRequestMethod("POST"); c.setRequestProperty("Content-Type", "application/json");
                c.setDoOutput(true);
                try (OutputStream out = c.getOutputStream()) { out.write(body.toString().getBytes("UTF-8")); }
            }
            int code = c.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
                StringBuilder buf = new StringBuilder(); String line;
                while ((line = reader.readLine()) != null) buf.append(line);
                return new JSONObject(buf.toString());
            }
        } finally { c.disconnect(); }
    }
    String generate(String key, JSONObject body) throws Exception {
        ArrayList<String> models = new ArrayList<>();
        String page = "";
        do {
            JSONObject list = request(key, "models?pageSize=100" + (page.isEmpty() ? "" : "&pageToken=" + URLEncoder.encode(page, "UTF-8")), null);
            JSONArray items = list.optJSONArray("models");
            if (items != null) for (int i=0; i<items.length(); i++) {
                JSONObject m = items.getJSONObject(i); String name = m.optString("name");
                if (!name.matches("models/gemini-[a-zA-Z0-9._-]+") || name.matches(".*(image|audio|tts|live|robotics|embedding|computer-use|deep-research).*")) continue;
                JSONArray methods = m.optJSONArray("supportedGenerationMethods");
                if (methods != null && methods.toString().contains("generateContent")) models.add(name);
            }
            page = list.optString("nextPageToken", "");
        } while (!page.isEmpty());
        Collections.sort(models, Comparator.comparingInt(m -> (m.contains("flash") ? 0 : 10) + (m.contains("preview") || m.contains("exp") ? 5 : 0) + (m.contains("lite") ? 0 : 1)));
        Exception last = new IOException("No compatible Gemini model found"); int attempts = 0;
        for (String model : models) {
            if (++attempts > 6) break;
            try {
                return stream(key,model,body);
            } catch (Exception e) { last = e; if ("HTTP 401".equals(e.getMessage()) || "HTTP 403".equals(e.getMessage())) throw e; }
        }
        throw last;
    }
    String stream(String key,String model,JSONObject body) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL("https://generativelanguage.googleapis.com/v1beta/"+model+":streamGenerateContent?alt=sse").openConnection();
        try {
            c.setConnectTimeout(15000); c.setReadTimeout(45000);
            c.setRequestMethod("POST"); c.setDoOutput(true);
            c.setRequestProperty("Content-Type","application/json");
            c.setRequestProperty("Accept","text/event-stream");
            c.setRequestProperty("x-goog-api-key",key);
            try(OutputStream out=c.getOutputStream()) { out.write(body.toString().getBytes("UTF-8")); }
            int code=c.getResponseCode();
            if(code!=200) throw new IOException("HTTP "+code);
            try(BufferedReader reader=new BufferedReader(new InputStreamReader(c.getInputStream(),"UTF-8"))) {
                return GeminiStream.read(reader,partial->handler.post(()->{
                    if(!destroyed && busy) reply(partial);
                }));
            }
        } finally { c.disconnect(); }
    }
    void openActivity(Intent intent) {
        if (screen == null || !screen.hasWindowFocus())
            throw new IllegalStateException("Open Jarvis to launch another app");
        screen.startActivity(intent);
    }
    void openWebsite(String url) {

        try {

            openActivity(new Intent(
                Intent.ACTION_VIEW,
                android.net.Uri.parse(url)));

            speak("Opening, sir.");

        } catch (Exception e) {

            speak("Unable to open website.");
        }
    }

    JSONObject message(String role, String text)
        throws JSONException {

        return new JSONObject()
            .put("role", role)
            .put("parts", new JSONArray().put(
                new JSONObject().put("text", text)));
    }

    @Override public void onDestroy() {
        stopStudio();
        releaseListeningLock();
        destroyed = true;
        active = false;
        handler.removeCallbacksAndMessages(null);
        if (mic != null) mic.destroy();
        if (voice != null) voice.shutdown();
        super.onDestroy();
    }
}
