package com.jarvis.assistant;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.speech.*;
import android.speech.tts.TextToSpeech;
import android.widget.*;
import android.view.Gravity;
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
    JSONArray history = new JSONArray();

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(1);
        layout.setPadding(30, 40, 30, 30);
        layout.setGravity(Gravity.CENTER);
        layout.setBackgroundColor(Color.rgb(3, 12, 24));

        TextView title = new TextView(this);
        title.setText("J A R V I S");
        title.setTextSize(36);
        title.setTextColor(Color.CYAN);
        layout.addView(title);

        status = new TextView(this);
        status.setText("SYSTEM ONLINE");
        status.setTextSize(20);
        status.setTextColor(Color.CYAN);
        status.setGravity(Gravity.CENTER);
        layout.addView(status);

        api = new EditText(this);
        api.setHint("Gemini API Key");
        api.setSingleLine(true);
        api.setInputType(129);
        api.setText(getPreferences(0)
            .getString("key", ""));
        layout.addView(api);

        Button save = new Button(this);
        save.setText("SAVE API KEY");
        layout.addView(save);

        save.setOnClickListener(v -> {
            getPreferences(0).edit()
                .putString("key", api.getText().toString().trim())
                .apply();
            status.setText("API KEY SAVED");
        });

        input = new EditText(this);
        input.setHint("Enter command");
        layout.addView(input);

        Button send = new Button(this);
        send.setText("SEND");
        layout.addView(send);

        send.setOnClickListener(v -> {
            String text = input.getText().toString();
            input.setText("");
            command(text);
        });

        Button listen = new Button(this);
        listen.setText("ACTIVATE JARVIS");
        layout.addView(listen);

        listen.setOnClickListener(v -> {
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
            if (mic != null) mic.cancel();
            status.setText("STANDBY");
        });

        setContentView(layout);

        voice = new TextToSpeech(this, result -> {
            if (result == TextToSpeech.SUCCESS) {
                voice.setLanguage(Locale.UK);
                voice.setPitch(0.85f);
                voice.setSpeechRate(0.9f);
            }
        });

        try {
            history = new JSONArray(getPreferences(0)
                .getString("history", "[]"));
        } catch (Exception e) {}
    }

    void activate() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.setText("Speech service unavailable");
            return;
        }

        active = true;

        if (mic == null) {
            mic = SpeechRecognizer.createSpeechRecognizer(this);

            mic.setRecognitionListener(new RecognitionListener() {
                public void onReadyForSpeech(Bundle b) {}
                public void onBeginningOfSpeech() {}
                public void onRmsChanged(float f) {}
                public void onBufferReceived(byte[] b) {}
                public void onEndOfSpeech() {}
                public void onPartialResults(Bundle b) {}
                public void onEvent(int i, Bundle b) {}

                public void onError(int error) {
                    if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        active = false;
                        status.setText("Microphone permission required");
                    } else {
                        handler.postDelayed(() -> listenNow(), 1500);
                    }
                }

                public void onResults(Bundle b) {
                    ArrayList<String> results =
                        b.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION);

                    if (results == null || results.isEmpty()) {
                        listenNow();
                        return;
                    }

                    String text = results.get(0);
                    String lower = text.toLowerCase(Locale.ROOT);

                    if (lower.contains("hey jarvis")) {
                        awake = true;
                        String rest = text.substring(
                            lower.indexOf("hey jarvis") + 10).trim();

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
        if (!active || busy || mic == null) return;

        if (voice != null && voice.isSpeaking()) {
            handler.postDelayed(() -> listenNow(), 700);
            return;
        }

        Intent i = new Intent(
            RecognizerIntent.ACTION_RECOGNIZE_SPEECH);

        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);

        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN");

        status.setText(awake ? "LISTENING..." : "SAY HEY JARVIS");

        try {
            mic.startListening(i);
        } catch (Exception e) {
            status.setText(e.toString());
        }
    }

    void speak(String text) {
        status.setText(text);

        if (voice != null) {
            voice.speak(text, TextToSpeech.QUEUE_FLUSH,
                null, "jarvis");
        }

        handler.postDelayed(() -> listenNow(), 1000);
    }

    void command(String text) {
        if (text.trim().isEmpty() || busy) return;

        if (mic != null) mic.cancel();

        String q = text.toLowerCase(Locale.ROOT);

        if (q.contains("go to sleep")) {
            awake = false;
            speak("Standing by, sir.");
            return;
        }

        if (q.contains("what time")) {
            speak(new java.text.SimpleDateFormat(
                "hh:mm a", Locale.UK).format(new Date()));
            return;
        }

        if (q.contains("clear memory")) {
            history = new JSONArray();
            getPreferences(0).edit().remove("history").apply();
            speak("Memory cleared, sir.");
            return;
        }

        String key = getPreferences(0).getString("key", "");

        if (key.isEmpty()) {
            speak("Please save your Gemini API key.");
            return;
        }

        busy = true;
        status.setText("PROCESSING...");

        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                JSONArray messages = new JSONArray(history.toString());

                messages.put(message("user", text));
                body.put("contents", messages);

                JSONObject instruction = new JSONObject();
                instruction.put("parts", new JSONArray().put(
                    new JSONObject().put("text",
                    "You are JARVIS, a helpful AI assistant. "
                    + "Reply briefly and clearly. Address the user as sir.")));

                body.put("system_instruction", instruction);

                URL url = new URL(
                    "https://generativelanguage.googleapis.com/v1beta/"
                    + "models/gemini-2.5-flash:generateContent");

                HttpURLConnection c =
                    (HttpURLConnection) url.openConnection();

                c.setRequestMethod("POST");
                c.setRequestProperty("Content-Type", "application/json");
                c.setRequestProperty("x-goog-api-key", key);
                c.setConnectTimeout(20000);
                c.setReadTimeout(30000);
                c.setDoOutput(true);

                try (OutputStream out = c.getOutputStream()) {
                    out.write(body.toString().getBytes("UTF-8"));
                }

                int code = c.getResponseCode();

                if (code != 200) {
                    throw new Exception("Gemini HTTP " + code);
                }

                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(c.getInputStream()));

                StringBuilder response = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                reader.close();
                c.disconnect();

                String answer = new JSONObject(response.toString())
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text");

                messages.put(message("model", answer));

                runOnUiThread(() -> {
                    history = messages;
                    getPreferences(0).edit()
                        .putString("history", history.toString()).apply();
                    busy = false;
                    speak(answer);
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    busy = false;
                    speak("AI error: " + e.getMessage());
                });
            }
        }).start();
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
        int request, String[] permissions, int[] results) {

        super.onRequestPermissionsResult(
            request, permissions, results);

        if (request == 1 && results.length > 0
            && results[0] == PackageManager.PERMISSION_GRANTED) {
            activate();
        }
    }

    @Override
    protected void onPause() {
        active = false;
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
