package game.vedyya.drop;

import android.Manifest;
import android.app.Activity;
import android.os.Bundle;
import android.content.pm.PackageManager;
import android.speech.tts.TextToSpeech;
import android.webkit.*;
import java.util.Locale;
import java.io.IOException;

public final class DropActivity extends Activity {
    private WebView view;
    private TextToSpeech voice;
    private boolean ready;
    private PermissionRequest request;
    private static final String HOME = "https://appassets.androidplatform.net/index.html";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        voice = new TextToSpeech(this, status -> {
            ready = status == TextToSpeech.SUCCESS;
            if (ready) voice.setLanguage(Locale.US);
        });
        view = new WebView(this);
        setContentView(view);
        view.getSettings().setJavaScriptEnabled(true);
        view.getSettings().setDomStorageEnabled(true);
        view.getSettings().setAllowFileAccess(false);
        view.getSettings().setAllowContentAccess(false);
        view.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView w, WebResourceRequest r) {
                return !HOME.equals(r.getUrl().toString());
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView w, WebResourceRequest r) {
                if (HOME.equals(r.getUrl().toString())) {
                    try { return new WebResourceResponse("text/html","UTF-8",getAssets().open("index.html")); }
                    catch (IOException e) { return new WebResourceResponse("text/plain","UTF-8",null); }
                }
                return null;
            }
        });
        view.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(PermissionRequest r) {
                if (!"appassets.androidplatform.net".equals(r.getOrigin().getHost()) ||
                    !java.util.Arrays.asList(r.getResources()).contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) { r.deny(); return; }
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) {
                    r.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
                } else { if(request!=null)request.deny(); request=r;requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},7); }
            }
            @Override public void onPermissionRequestCanceled(PermissionRequest r) { if(request==r)request=null; }
        });
        view.addJavascriptInterface(new Object(){
            @JavascriptInterface public void say(String line){runOnUiThread(()->{if(ready && line!=null && line.length()<160)voice.speak(line,TextToSpeech.QUEUE_FLUSH,null,"callout");});}
        },"DropVoice");
        view.loadUrl(HOME);
    }
    @Override public void onRequestPermissionsResult(int code,String[] names,int[] results) {
        super.onRequestPermissionsResult(code,names,results);
        if(code==7 && request!=null){if(results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED)request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});else request.deny();request=null;}
    }
    @Override protected void onPause(){super.onPause();view.onPause();if(voice!=null)voice.stop();}
    @Override protected void onResume(){super.onResume();view.onResume();}
    @Override protected void onDestroy(){if(request!=null)request.deny();view.destroy();if(voice!=null)voice.shutdown();super.onDestroy();}
}
