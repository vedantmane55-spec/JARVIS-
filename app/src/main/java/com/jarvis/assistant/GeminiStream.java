package com.jarvis.assistant;

import java.io.*;
import java.util.regex.*;
import org.json.*;

final class GeminiStream {
    interface Preview { void show(String text); }
    private static final Pattern REPLY = Pattern.compile("^\\s*\\{\\s*\"reply\"\\s*:\\s*\"");
    static String preview(String json) throws JSONException {
        Matcher m = REPLY.matcher(json);
        if (!m.find()) return "";
        int end = m.end();
        while (end < json.length()) {
            char ch = json.charAt(end);
            if (ch == '"') break;
            if (ch == '\\') {
                if (end+1 >= json.length()) break;
                int width = json.charAt(end+1)=='u' ? 6 : 2;
                if (end+width > json.length()) break;
                end += width;
            } else end++;
        }
        return new JSONArray("[\"" + json.substring(m.end(),end) + "\"]").getString(0);
    }
    static String read(BufferedReader reader, Preview listener) throws Exception {
        StringBuilder result = new StringBuilder();
        String line; boolean finished = false;
        while ((line = reader.readLine()) != null) {
            if (!line.startsWith("data:")) continue;
            String data = line.substring(5).trim();
            if (data.equals("[DONE]")) break;
            JSONObject frame = new JSONObject(data);
            if (frame.has("error")) throw new IOException("Gemini stream failed");
            JSONArray candidates = frame.optJSONArray("candidates");
            if (candidates == null || candidates.length() == 0) continue;
            JSONObject candidate = candidates.getJSONObject(0);
            JSONObject content = candidate.optJSONObject("content");
            JSONArray parts = content == null ? null : content.optJSONArray("parts");
            if (parts != null) for (int i=0;i<parts.length();i++) {
                JSONObject part = parts.getJSONObject(i);
                if (!part.optBoolean("thought")) result.append(part.optString("text"));
            }
            String visible = preview(result.toString());
            if (!visible.isEmpty()) listener.show(visible);
            if (candidate.has("finishReason")) finished = "STOP".equals(candidate.optString("finishReason"));
        }
        if (!finished) throw new IOException("Gemini reply interrupted");
        return result.toString();
    }
}
