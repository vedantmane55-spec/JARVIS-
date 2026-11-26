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
    JarvisService service;
    Button send, stop, clear;
    android.content.ServiceConnection connection = new android.content.ServiceConnection() {
        public void onServiceConnected(android.content.ComponentName name, android.os.IBinder binder) {
            service = ((JarvisService.LocalBinder) binder).service();
            service.screen = MainActivity.this;
            status.setText(service.statusText);
            enableCommands(true);
        }
        public void onServiceDisconnected(android.content.ComponentName name) {
            service = null;
            enableCommands(false);
        }
    };
    void enableCommands(boolean enabled) {
        send.setEnabled(enabled);
        stop.setEnabled(enabled);
        clear.setEnabled(enabled);
    }
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
        title.setText("J A R V I S  /  3.0");
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

        Spinner language = new Spinner(this);
        String[] languages = {"English", "Hindi", "Marathi"};
        language.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, languages));
        language.setSelection(getPreferences(0).getInt("language", 0));
        language.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                getPreferences(0).edit().putInt("language", position).apply();
            }
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        layout.addView(language);
        send = new Button(this);
        send.setText("SEND");
        layout.addView(send);

        send.setOnClickListener(v -> {
            String text = input.getText().toString();
            input.setText("");
            service.command(text);
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

        stop = new Button(this);
        stop.setText("STOP LISTENING");
        layout.addView(stop);

        stop.setOnClickListener(v -> service.standby());

        clear = new Button(this);
        clear.setText("CLEAR MEMORY");
        layout.addView(clear);

        clear.setOnClickListener(v -> service.command("clear memory"));
        enableCommands(false);
        scroll.addView(layout);
        setContentView(scroll);
        bindService(new Intent(this, JarvisService.class), connection, BIND_AUTO_CREATE);
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

            boolean busy = service != null && service.busy;
            boolean awake = service != null && service.awake;
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
        Intent intent = new Intent(this, JarvisService.class);
        if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(intent);
        else startService(intent);
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

    @Override protected void onDestroy() {
        if (service != null && service.screen == this) service.screen = null;
        unbindService(connection);
        super.onDestroy();
    }
}
