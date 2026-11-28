package game.mosswake.island;
import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import java.io.IOException;
public final class DropActivity extends Activity {
 private WebView game;
 private static final String URL="https://appassets.androidplatform.net/index.html";
 @Override public void onCreate(Bundle state){
  super.onCreate(state); game=new WebView(this);setContentView(game);
  game.getSettings().setJavaScriptEnabled(true);game.getSettings().setDomStorageEnabled(true);
  game.getSettings().setAllowFileAccess(false);game.getSettings().setAllowContentAccess(false);
  game.setWebViewClient(new WebViewClient(){
   @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return true;}
   @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){
    if(URL.equals(r.getUrl().toString()))try{return new WebResourceResponse("text/html","UTF-8",getAssets().open("index.html"));}catch(IOException ignored){}
    return new WebResourceResponse("text/plain","UTF-8",null);
   }
  });game.loadUrl(URL);
 }
 @Override protected void onPause(){game.evaluateJavascript("if(saved)save()",null);game.onPause();super.onPause();}
 @Override protected void onResume(){super.onResume();game.onResume();}
 @Override protected void onDestroy(){game.destroy();super.onDestroy();}
}