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

    TextView status, answer;
    EditText input, api;
    JarvisService service;
    Button send, stop, clear;
    VoiceOrb orb;
    FrameLayout stage;
    ScrollView settings;
    boolean inSettings() { return settings.getVisibility()==View.VISIBLE; }
    void showSettings(boolean show) {
        settings.setVisibility(show?View.VISIBLE:View.GONE);
        orb.setVisibility(show?View.GONE:View.VISIBLE);
        if (!show && stage.getWindowToken()!=null) {
            ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(stage.getWindowToken(),0);
        }
        getWindow().getDecorView().setSystemUiVisibility(show?View.SYSTEM_UI_FLAG_VISIBLE:
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
    @Override public void onBackPressed() {
        if(inSettings()) showSettings(false); else super.onBackPressed();
    }
    void beginVoice() {
        if(checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO},1);
        } else activate();
    }
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
            if ("device".equals(step.getString("type")) || "home".equals(step.getString("type"))) {
                owner.executing = true;
                new Thread(()->{
                    String failure = null;
                    String result = "Device request sent.";
                    try {
                        if ("home".equals(step.getString("type"))) result = HomeHub.control(owner,step);
                        else Tasks.device(owner,step.getString("value"));
                    }
                    catch(Exception e) { failure = "Device request failed. Check the routine before retrying."; }
                    String error = failure, feedback = result;
                    owner.handler.post(()->{
                        owner.executing = false;
                        if (error != null) { owner.plan = new JSONArray(); owner.speak(error); return; }
                        steps.remove(0);
                        if (steps.length()==0) owner.speak(feedback);
                        else if (hasWindowFocus()) runStep(owner,steps);
                        else owner.status("Open Jarvis and tap Review steps to continue.");
                    });
                }).start(); return;
            }
            Tasks.execute(this,step);
            steps.remove(0);
            if (steps.length()==0) owner.speak("Requested steps sent.");
            else if ("whatsapp".equals(step.optString("type")) || "calendar".equals(step.optString("type")) || "pdf".equals(step.optString("type")) || "alarm".equals(step.optString("type")))
                owner.status("Return to Jarvis and tap Review steps to continue.");
            else runStep(owner,steps);
        } catch(Exception e) { owner.plan = new JSONArray(); owner.speak("Stopped at " + step.optString("type") + ". " + e.getMessage()); }
    }
    android.content.ServiceConnection connection = new android.content.ServiceConnection() {
        public void onServiceConnected(android.content.ComponentName name, android.os.IBinder binder) {
            if (!(binder instanceof JarvisService.LocalBinder)) { enableCommands(false); return; }
            service = ((JarvisService.LocalBinder) binder).service();
            service.screen = MainActivity.this;
            status.setText(service.statusText);
            answer.setText(service.lastReply);
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

        settings = new ScrollView(this);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(1);
        layout.setPadding(25, 30, 25, 30);
        layout.setGravity(Gravity.CENTER);
        layout.setBackgroundColor(Color.rgb(3, 12, 24));

        button(layout,"Return to voice orb",()->showSettings(false));
        android.widget.Switch studio = new android.widget.Switch(this);
        studio.setText("Studio assistant voice (Gemini)");
        studio.setChecked(getPreferences(0).getBoolean("studio_voice",true));
        studio.setOnCheckedChangeListener((bttn,enabled)->getPreferences(0).edit().putBoolean("studio_voice",enabled).apply());
        layout.addView(studio);
        status = new TextView(this);
        status.setText("SYSTEM ONLINE");
        status.setTextSize(18);
        status.setTextColor(Color.CYAN);
        status.setGravity(Gravity.CENTER);
        layout.addView(status);
        answer = new TextView(this); answer.setTextIsSelectable(true); layout.addView(answer);

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
        button(layout,"Save WhatsApp contact",()->{
            LinearLayout form = new LinearLayout(this); form.setOrientation(1);
            EditText name = new EditText(this), phone = new EditText(this);
            name.setHint("Name you will say"); phone.setHint("Number with country code, e.g. +91…");
            phone.setInputType(android.text.InputType.TYPE_CLASS_PHONE); form.addView(name); form.addView(phone);
            new android.app.AlertDialog.Builder(this).setTitle("WhatsApp contact").setView(form)
                .setPositiveButton("Save",(d,w)->{
                    String alias=name.getText().toString().trim().toLowerCase(Locale.ROOT);
                    String digits=phone.getText().toString().replaceAll("[ ()-]","");
                    if(alias.isEmpty() || !digits.matches("\\+[1-9][0-9]{6,14}")) { status.setText("Enter name and international number."); return; }
                    try { Tasks.save(this,"wa_contacts",alias,digits.substring(1)); status.setText("Contact saved."); }
                    catch(JSONException e) { status.setText("Unable to save contact."); }
                }).setNegativeButton("Cancel",null).show();
        });
        button(layout,"Connect Home Assistant",()->HomeHub.configure(this));
        button(layout,"Control lights and switches",()->HomeHub.select(this));
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
        settings.addView(layout);
        stage = new FrameLayout(this);
        orb = new VoiceOrb(this);
        orb.setOnClickListener(v -> beginVoice());
        orb.setOnLongClickListener(v->{ showSettings(true); return true; });
        stage.addView(orb); stage.addView(settings);
        setContentView(stage);
        showSettings(b!=null && b.getBoolean("settings"));
        if (!getPreferences(0).getBoolean("orb_hint",false)) {
            Toast.makeText(this,"Tap to speak • Hold the orb for settings",Toast.LENGTH_LONG).show();
            getPreferences(0).edit().putBoolean("orb_hint",true).apply();
        }
        bindService(new Intent(this, JarvisService.class), connection, BIND_AUTO_CREATE);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state); state.putBoolean("settings",inSettings());
    }
    void camera() {
        try { startActivityForResult(new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE),23); }
        catch (android.content.ActivityNotFoundException e) { status.setText("Use Attach diagram to choose a photo."); }
    }
    void activate() {
        Intent intent = new Intent(this, JarvisService.class).setAction("TALK");
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
