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

public class MainActivity extends Activity {

    TextView status;
    EditText input, api;
    SpeechRecognizer mic;
    TextToSpeech voice;
    Handler handler = new Handler();

    boolean active = false;
    boolean awake = false;
    boolean busy = false;
    boolean voiceReady = false;
    boolean listening = false;

    JSONArray history = new JSONArray();

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);

        ScrollView scroll = new ScrollView(this);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(1);
        layout.setPadding(25, 30, 25, 30);
        layout.setGravity(Gravity.CENTER);
        layout.setBackgroundColor(Color.rgb(3, 12, 24));

        TextView title = new TextView(this);
        title.setText("J A R V I S  /  2.0");
        title.setTextSize(36);
        title.setTextColor(Color.CYAN);
        layout.addView(title);

        layout.addView(new Reactor(),
            new LinearLayout.LayoutParams(-1, 350));

        status = new TextView(this);
        status.setText("SYSTEM ONLINE");
        status.setTextSize(18);
        status.setTextColor(Color.CYAN);
        status.setGravity(Gravity.CENTER);
        layout.addView(status);

        api = new EditText(this);
        api.setHint("Gemini API Key");
        api.setSingleLine(true);
        api.setInputType(129);
        api.setText(getPreferences(0)
            .getString("key", ""));
        layout.addView(api,
            new LinearLayout.LayoutParams(-1, -2));

        Button save = new Button(this);
        save.setText("SAVE API KEY");
        layout.addView(save);

        save.setOnClickListener(v -> {
            getPreferences(0).edit()
                .putString("key",
                    api.getText().toString().trim())
                .apply();

            status.setText("API KEY SAVED");
        });

        input = new EditText(this);
        input.setHint("Enter command");
        layout.addView(input,
            new LinearLayout.LayoutParams(-1, -2));

        Button send = new Button(this);
        send.setText("SEND");
        layout.addView(send);

        send.setOnClickListener(v -> {
            String text = input.getText().toString();
            input.setText("");
            command(text);
        });

        Button start = new Button(this);
        start.setText("ACTIVATE JARVIS");
        layout.addView(start);

        start.setOnClickListener(v -> {
            if (checkSelfPermission(
                android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {

                requestPermissions(new String[]{
                    android.Manifest.permission.RECORD_AUDIO
                }, 1);

            } else {
                activate();
            }
        });

        Button stop = new Button(this);
        stop.setText("STOP LISTENING");
        layout.addView(stop);

        stop.setOnClickListener(v -> {
            active = false;
            awake = false;
            listening = false;

            if (mic != null) mic.cancel();

            status.setText("STANDBY");
        });

        Button clear = new Button(this);
        clear.setText("CLEAR MEMORY");
        layout.addView(clear);

        clear.setOnClickListener(v -> {
            if (!busy) {
                history = new JSONArray();
                getPreferences(0).edit()
                    .remove("history").apply();
                speak("Memory cleared, sir.");
            }
        });

        scroll.addView(layout);
        setContentView(scroll);

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
            status.setText("Voice unavailable");
        }

        try {
            history = new JSONArray(
                getPreferences(0)
                .getString("history", "[]"));
        } catch (Exception e) {}
    }

    class Reactor extends View {

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float angle = 0;

        Reactor() {
            super(MainActivity.this);
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);

            float x = getWidth() / 2f;
            float y = getHeight() / 2f;
            float r = Math.min(x, y) * 0.8f;

            p.setColor(busy ? Color.rgb(255,65,65) : Color.CYAN);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(4);

            c.drawCircle(x, y, r * 0.55f, p);
            c.drawCircle(x, y, r * 0.75f, p);

            RectF rect = new RectF(
                x-r, y-r, x+r, y+r);

            for (int i = 0; i < 6; i++) {
                c.drawArc(rect,
                    angle + i * 60, 40, false, p);
            }

            p.setStyle(Paint.Style.FILL);
            p.setTextSize(28);
            p.setTextAlign(Paint.Align.CENTER);
            c.drawText(busy ? "PROCESSING" : awake ? "ONLINE" : "STANDBY", x, y + 10, p);

            angle = (angle + 2) % 360;
            postInvalidateDelayed(40);
        }
    }

    void activate() {

        if (!SpeechRecognizer
            .isRecognitionAvailable(this)) {

            status.setText(
                "Speech recognition unavailable");
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

                        active = false;
                        status.setText(
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

                    if (lower.contains("hey jarvis")) {

                        awake = true;

                        String rest = text.substring(
                            lower.indexOf("hey jarvis")
                            + 10).trim();

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
            RecognizerIntent.EXTRA_LANGUAGE, "en-IN");

        status.setText(
            awake ? "LISTENING..." : "SAY HEY JARVIS");

        try {
            listening = true;
            mic.startListening(i);
        } catch (Exception e) {
            listening = false;
            status.setText(e.toString());
        }
    }

    void speak(String text) {

        status.setText(text);

        if (voiceReady && voice != null) {
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

        String q = text.toLowerCase(Locale.ROOT);

        if (q.contains("go to sleep")) {
            awake = false;
            speak("Standing by, sir.");
            return;
        }

        if (q.contains("what time")) {
            speak(new java.text.SimpleDateFormat(
                "hh:mm a", Locale.UK)
                .format(new Date()));
            return;
        }

        if (q.contains("clear memory")) {
            history = new JSONArray();
            getPreferences(0).edit()
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

        String key = getPreferences(0)
            .getString("key", "");

        if (key.isEmpty()) {
            speak("Please save your Gemini API key.");
            return;
        }

        busy = true;
        status.setText("PROCESSING...");

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
                        + "clearly. Address user as sir.")));

                body.put(
                    "system_instruction", instruction);

                String answer = generate(key, body);

                messages.put(
                    message("model", answer));

                while (messages.length() > 20) {
                    messages.remove(0);
                    messages.remove(0);
                }

                runOnUiThread(() -> {

                    if (isDestroyed()) return;

                    history = messages;

                    getPreferences(0).edit()
                        .putString("history",
                            history.toString())
                        .apply();

                    busy = false;
                    speak(answer);
                });

            } catch (Exception e) {

                runOnUiThread(() -> {

                    if (isDestroyed()) return;

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
    void openWebsite(String url) {

        try {

            startActivity(new Intent(
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

    @Override
    public void onRequestPermissionsResult(
        int request, String[] permissions,
        int[] results) {

        super.onRequestPermissionsResult(
            request, permissions, results);

        if (request == 1 && results.length > 0
            && results[0] ==
                PackageManager.PERMISSION_GRANTED) {

            activate();
        }
    }

    @Override
    protected void onPause() {

        active = false;
        listening = false;

        if (mic != null) mic.cancel();

        super.onPause();
    }

    @Override
    protected void onDestroy() {

        active = false;

        handler.removeCallbacksAndMessages(null);

        if (mic != null) mic.destroy();
        if (voice != null) voice.shutdown();

        super.onDestroy();
    }
}
