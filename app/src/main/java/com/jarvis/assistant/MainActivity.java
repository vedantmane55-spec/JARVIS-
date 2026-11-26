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
    android.net.Uri picture;
    boolean reviewing;
    void button(LinearLayout layout, String label, Runnable action) {
        Button b = new Button(this); b.setText(label); layout.addView(b);
        b.setOnClickListener(v -> action.run());
    }
    void choose(String mime, int code) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime);
        startActivityForResult(i,code);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request,result,data);
        if (result != RESULT_OK || data == null) return;
        if (request == 23 && data.getExtras() != null && data.getExtras().get("data") instanceof Bitmap) {
            try {
                File photo = File.createTempFile("diagram-", ".jpg", getCacheDir());
                try (OutputStream out = new FileOutputStream(photo)) {
                    ((Bitmap)data.getExtras().get("data")).compress(Bitmap.CompressFormat.JPEG,95,out);
                }
                picture = android.net.Uri.fromFile(photo);
                send.setText("SEND IMAGE TO GEMINI");
                status.setText("Camera preview attached. For small labels attach a full-size photo.");
            } catch (IOException e) { status.setText("Unable to attach photo"); }
            return;
        }
        if (data.getData() == null) return;
        try {
            if (request == 21) Tasks.importPdf(this,data.getData());
            if (request == 22) {
                picture = data.getData();
                status.setText("Image attached. Type your question and tap Send to Gemini.");
                send.setText("SEND IMAGE TO GEMINI");
            }
        } catch (Exception e) { status.setText("Could not open the selected file"); }
    }
    static String imageData(android.content.Context ctx, android.net.Uri uri) throws Exception {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(in,null,opts); }
        if (opts.outWidth <= 0 || opts.outHeight <= 0) throw new IOException("Unsupported image");
        opts.inSampleSize = 1;
        while (Math.max(opts.outWidth,opts.outHeight) / opts.inSampleSize > 1600) opts.inSampleSize *= 2;
        opts.inJustDecodeBounds = false;
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            Bitmap bitmap = BitmapFactory.decodeStream(in,null,opts);
            if (bitmap == null) throw new IOException("Could not read image");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try { bitmap.compress(Bitmap.CompressFormat.JPEG,85,bytes); } finally { bitmap.recycle(); }
            return android.util.Base64.encodeToString(bytes.toByteArray(),android.util.Base64.NO_WRAP);
        }
    }
    void showPlan() {
        if (reviewing || service == null || service.plan.length() == 0) return;
        JarvisService owner = service;
        JSONArray steps = owner.plan;
        StringBuilder summary = new StringBuilder();
        try {
            for (int i=0;i<steps.length();i++) summary.append(i+1).append(". ").append(Tasks.describe(steps.getJSONObject(i))).append("\n");
        } catch(Exception e) { owner.plan = new JSONArray(); status.setText(e.getMessage()); return; }
        reviewing = true;
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this).setTitle("Run these steps?")
            .setMessage(summary.toString()).setPositiveButton("Run",(d,w)->runStep(owner,steps))
            .setNegativeButton("Discard",(d,w)->owner.plan = new JSONArray()).create();
        dialog.setOnDismissListener(d -> reviewing = false); dialog.show();
    }
    void runStep(JarvisService owner, JSONArray steps) {
        if (owner.plan != steps || steps.length() == 0 || isDestroyed() || owner.executing) return;
        JSONObject step = steps.optJSONObject(0);
        try {
            if ("device".equals(step.getString("type"))) {
                owner.executing = true;
                new Thread(()->{
                    String failure = null;
                    try { Tasks.device(owner,step.getString("value")); }
                    catch(Exception e) { failure = "Device request failed. Check the routine before retrying."; }
                    String error = failure;
                    owner.handler.post(()->{
                        owner.executing = false;
                        if (error != null) { owner.plan = new JSONArray(); owner.speak(error); return; }
                        steps.remove(0);
                        if (steps.length()==0) owner.speak("Device request sent.");
                        else if (hasWindowFocus()) runStep(owner,steps);
                        else owner.status("Open Jarvis and tap Review steps to continue.");
                    });
                }).start(); return;
            }
            Tasks.execute(this,step);
            steps.remove(0);
            if (steps.length()==0) owner.speak("Requested steps sent.");
            else if ("calendar".equals(step.optString("type")) || "pdf".equals(step.optString("type")) || "alarm".equals(step.optString("type")))
                owner.status("Return to Jarvis and tap Review steps to continue.");
            else runStep(owner,steps);
        } catch(Exception e) { owner.plan = new JSONArray(); owner.speak("Stopped at " + step.optString("type") + ". " + e.getMessage()); }
    }
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
        title.setText("J A R V I S  /  3.1");
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
            if (service.busy || service.executing) return;
            input.setText("");
            if (picture == null) service.command(text);
            else {
                android.net.Uri selected = picture; picture = null; send.setText("SEND");
                service.ask(text.trim().isEmpty() ? "Explain this image" : text, selected);
            }
        });

        button(layout,"Photograph a diagram",()->{
            if (checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) camera();
            else requestPermissions(new String[]{android.Manifest.permission.CAMERA},4);
        });
        button(layout,"Attach diagram or screenshot",()->choose("image/*",22));
        button(layout,"Save a PDF shortcut",()->choose("application/pdf",21));
        button(layout,"Connect device routine",()->Tasks.configure(this));
        button(layout,"Review steps",()->showPlan());
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            button(layout,"Enable reminder notifications",()->requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},3));
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

    void camera() {
        try { startActivityForResult(new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE),23); }
        catch (android.content.ActivityNotFoundException e) { status.setText("Use Attach diagram to choose a photo."); }
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

        if (request == 4 && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) camera();
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
