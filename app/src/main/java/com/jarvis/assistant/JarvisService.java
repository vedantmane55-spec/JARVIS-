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
    String statusText = "SYSTEM ONLINE";
    boolean destroyed;
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
        startForeground(1, note.setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Jarvis microphone active").setContentText("Say Hey Jarvis")
            .setOngoing(true).setContentIntent(open).addAction(0, "Stop", stop).build());
        activate();
        return START_NOT_STICKY;
    }
    void standby() {
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

        active = true;

        if (mic == null) {

            mic = SpeechRecognizer
                .createSpeechRecognizer(this);

            mic.setRecognitionListener(
                new RecognitionListener() {

                public void onReadyForSpeech(Bundle b) {}
                public void onBeginningOfSpeech() {}
                public void onRmsChanged(float f) {}
                public void onBufferReceived(byte[] b) {}
                public void onEndOfSpeech() {}
                public void onPartialResults(Bundle b) {}
                public void onEvent(int i, Bundle b) {}

                public void onError(int error) {

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

        if (voiceReady && voice.isSpeaking()) {

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
        } catch (Exception e) {
            listening = false;
            status(e.toString());
        }
    }

    void speak(String text) {

        status(text);

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

        if (text.trim().isEmpty() || busy) return;

        if (mic != null && listening) {
            mic.cancel();
            listening = false;
        }

        String q = text.trim().toLowerCase(Locale.ROOT).replaceAll("[?!.।]+$", "");
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
                .remove("history").apply();
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

                body.put("contents", messages);

                JSONObject instruction =
                    new JSONObject();

                instruction.put("parts",
                    new JSONArray().put(
                        new JSONObject().put("text",
                        "You are JARVIS, a helpful AI "
                        + "assistant. Reply briefly and "
                        + "clearly in " + replyLanguage + ". Address user as sir.")));

                body.put(
                    "system_instruction", instruction);

                String answer = generate(key, body);

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
                    speak(answer);
                });

            } catch (Exception e) {

                handler.post(() -> {

                    if (destroyed) return;

                    busy = false;
                    speak("AI error: " + e.getMessage());
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
                JSONArray parts = request(key, model + ":generateContent", body).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts");
                StringBuilder answer = new StringBuilder();
                for (int i=0; i<parts.length(); i++) { JSONObject p = parts.getJSONObject(i); if (!p.optBoolean("thought")) answer.append(p.optString("text")); }
                if (answer.length() == 0) throw new IOException("No text response");
                return answer.toString();
            } catch (Exception e) { last = e; if ("HTTP 401".equals(e.getMessage()) || "HTTP 403".equals(e.getMessage())) throw e; }
        }
        throw last;
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
        destroyed = true;
        active = false;
        handler.removeCallbacksAndMessages(null);
        if (mic != null) mic.destroy();
        if (voice != null) voice.shutdown();
        super.onDestroy();
    }
}
