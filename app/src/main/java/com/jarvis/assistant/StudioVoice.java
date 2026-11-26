package com.jarvis.assistant;

import android.util.Base64;
import java.io.*;
import java.nio.*;
import java.util.regex.*;
import org.json.*;

final class StudioVoice {
    static byte[] wrapPcm(byte[] data,int rate) {
        ByteBuffer b=ByteBuffer.allocate(44+data.length).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(0x46464952).putInt(36+data.length).putInt(0x45564157).putInt(0x20746d66);
        b.putInt(16).putShort((short)1).putShort((short)1).putInt(rate).putInt(rate*2);
        b.putShort((short)2).putShort((short)16).putInt(0x61746164).putInt(data.length).put(data);
        return b.array();
    }
    static File render(JarvisService service,String key,String text) throws Exception {
        JSONObject speaker=new JSONObject().put("prebuiltVoiceConfig",new JSONObject().put("voiceName","Charon"));
        JSONObject config=new JSONObject().put("responseModalities",new JSONArray().put("AUDIO"))
            .put("speechConfig",new JSONObject().put("voiceConfig",speaker));
        String direction="Read the following text exactly, in its original language. Use an original calm, articulate, "
            +"low-register assistant voice with measured pacing and subtle dry warmth. Use British diction in English.\n\n"+text;
        JSONObject body=new JSONObject().put("contents",new JSONArray().put(service.message("user",direction))).put("generationConfig",config);
        JSONObject result=service.request(key,"models/gemini-3.8-flash-lite-tts:generateContent",body);
        JSONObject audio=result.getJSONArray("candidates").getJSONObject(0).getJSONObject("content")
            .getJSONArray("parts").getJSONObject(0).getJSONObject("inlineData");
        byte[] data=Base64.decode(audio.getString("data"),Base64.DEFAULT);
        String mime=audio.optString("mimeType","");
        if(mime.startsWith("audio/L16") || mime.startsWith("audio/pcm")) {
            Matcher match=Pattern.compile("rate=(\\d+)").matcher(mime);
            data=wrapPcm(data,match.find()?Integer.parseInt(match.group(1)):24000);
        }
        File f=File.createTempFile("jarvis-voice-",".audio",service.getCacheDir());
        try(FileOutputStream out=new FileOutputStream(f)) { out.write(data); }
        catch(IOException e) { f.delete(); throw e; }
        return f;
    }
}
