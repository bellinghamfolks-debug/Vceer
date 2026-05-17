package com.basir.ai;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Direct client for Google's Generative Language API (Gemini).
 *
 * Endpoint pattern:
 *   POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={API_KEY}
 *
 * No server is required - the user's Gemini key is stored locally inside the app.
 * Used when the user picks "direct" mode in the settings dialog.
 */
public final class GeminiDirectClient {

    private GeminiDirectClient() {}

    public static final String DEFAULT_FLASH = "gemini-3-flash-preview";
    public static final String DEFAULT_PRO   = "gemini-3-pro-preview";

    private static final String BASE = "https://generativelanguage.googleapis.com/v1beta/models/";

    private static String endpoint(String model, String apiKey) throws Exception {
        return BASE + model + ":generateContent?key=" + URLEncoder.encode(apiKey, "UTF-8");
    }

    /**
     * Send a text-only or text+image prompt and return the model's plain text answer.
     *
     * @param apiKey      Gemini API key.
     * @param model       Model id, e.g. gemini-3-flash-preview.
     * @param systemText  System instruction shown to the model.
     * @param userText    The user prompt.
     * @param imageBase64 Optional base64-encoded image. May be null.
     * @param mimeType    Optional mime type when imageBase64 is provided.
     */
    public static String generateText(String apiKey, String model,
                                      String systemText, String userText,
                                      String imageBase64, String mimeType) throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new Exception("Gemini API key is empty");
        }
        JSONObject body = baseBody(systemText);
        JSONArray parts = new JSONArray();
        parts.put(new JSONObject().put("text", userText == null ? "" : userText));
        if (imageBase64 != null && !imageBase64.isEmpty()) {
            JSONObject inline = new JSONObject();
            inline.put("mimeType", (mimeType == null || mimeType.isEmpty()) ? "image/jpeg" : mimeType);
            inline.put("data", imageBase64);
            parts.put(new JSONObject().put("inlineData", inline));
        }
        body.put("contents", new JSONArray().put(new JSONObject().put("role", "user").put("parts", parts)));

        JSONObject resp = postJson(endpoint(model, apiKey), body);
        return extractText(resp);
    }

    /**
     * Send a binary file (PDF, image, audio, etc.) along with a prompt.
     * Returns plain text.
     */
    public static String generateWithFile(String apiKey, String model,
                                          String systemText, String userPrompt,
                                          byte[] fileBytes, String mimeType) throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new Exception("Gemini API key is empty");
        }
        if (fileBytes == null || fileBytes.length == 0) {
            throw new Exception("Empty file");
        }
        JSONObject body = baseBody(systemText);
        JSONArray parts = new JSONArray();
        parts.put(new JSONObject().put("text", userPrompt == null ? "" : userPrompt));
        JSONObject inline = new JSONObject();
        inline.put("mimeType", (mimeType == null || mimeType.isEmpty()) ? "application/octet-stream" : mimeType);
        inline.put("data", Base64.encodeToString(fileBytes, Base64.NO_WRAP));
        parts.put(new JSONObject().put("inlineData", inline));

        body.put("contents", new JSONArray().put(new JSONObject().put("role", "user").put("parts", parts)));

        JSONObject resp = postJson(endpoint(model, apiKey), body);
        return extractText(resp);
    }

    /**
     * Like generateWithFile but instructs Gemini to return a JSON object.
     * Used for the PDF/PPTX → Word pipeline.
     */
    public static JSONObject generateJsonWithFile(String apiKey, String model,
                                                  String systemText, String userPrompt,
                                                  byte[] fileBytes, String mimeType) throws Exception {
        JSONObject body = baseBody(systemText);
        body.put("generationConfig", new JSONObject().put("responseMimeType", "application/json"));

        JSONArray parts = new JSONArray();
        parts.put(new JSONObject().put("text", userPrompt == null ? "" : userPrompt));
        JSONObject inline = new JSONObject();
        inline.put("mimeType", (mimeType == null || mimeType.isEmpty()) ? "application/octet-stream" : mimeType);
        inline.put("data", Base64.encodeToString(fileBytes, Base64.NO_WRAP));
        parts.put(new JSONObject().put("inlineData", inline));

        body.put("contents", new JSONArray().put(new JSONObject().put("role", "user").put("parts", parts)));

        JSONObject resp = postJson(endpoint(model, apiKey), body);
        String text = extractText(resp);
        if (text == null || text.trim().isEmpty()) {
            throw new Exception("Empty Gemini response");
        }
        try {
            return new JSONObject(text);
        } catch (Exception e) {
            // Sometimes the model wraps JSON in ```json fences - strip them.
            String cleaned = text.trim();
            if (cleaned.startsWith("```")) {
                int firstNl = cleaned.indexOf('\n');
                if (firstNl > 0) cleaned = cleaned.substring(firstNl + 1);
                if (cleaned.endsWith("```")) cleaned = cleaned.substring(0, cleaned.length() - 3);
            }
            return new JSONObject(cleaned.trim());
        }
    }

    /**
     * Multi-part request with a list of inline parts. Used for PPTX processing,
     * where we send all slide images at once together with text instructions.
     */
    public static JSONObject generateJsonWithParts(String apiKey, String model,
                                                   String systemText, JSONArray userParts) throws Exception {
        JSONObject body = baseBody(systemText);
        body.put("generationConfig", new JSONObject().put("responseMimeType", "application/json"));
        body.put("contents", new JSONArray().put(new JSONObject().put("role", "user").put("parts", userParts)));

        JSONObject resp = postJson(endpoint(model, apiKey), body);
        String text = extractText(resp);
        if (text == null || text.trim().isEmpty()) throw new Exception("Empty Gemini response");
        try {
            return new JSONObject(text);
        } catch (Exception e) {
            String cleaned = text.trim();
            if (cleaned.startsWith("```")) {
                int firstNl = cleaned.indexOf('\n');
                if (firstNl > 0) cleaned = cleaned.substring(firstNl + 1);
                if (cleaned.endsWith("```")) cleaned = cleaned.substring(0, cleaned.length() - 3);
            }
            return new JSONObject(cleaned.trim());
        }
    }

    // ---------- internals ----------

    private static JSONObject baseBody(String systemText) throws Exception {
        JSONObject body = new JSONObject();
        if (systemText != null && !systemText.isEmpty()) {
            JSONObject sys = new JSONObject();
            sys.put("parts", new JSONArray().put(new JSONObject().put("text", systemText)));
            body.put("systemInstruction", sys);
        }
        // Sensible defaults; the model handles its own context.
        JSONObject gen = body.optJSONObject("generationConfig");
        if (gen == null) gen = new JSONObject();
        if (!gen.has("temperature")) gen.put("temperature", 0.7);
        body.put("generationConfig", gen);
        return body;
    }

    private static JSONObject postJson(String url, JSONObject payload) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(300000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "Basir-Android/1.0.2");

        byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = conn.getOutputStream()) { os.write(bytes); }

        int code = conn.getResponseCode();
        InputStream in = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        String text = readAll(in);
        if (code < 200 || code >= 300) {
            // Try to extract a clean error message from Google's error JSON.
            String message = text;
            try {
                JSONObject err = new JSONObject(text);
                if (err.has("error")) {
                    Object e = err.get("error");
                    if (e instanceof JSONObject) {
                        JSONObject eo = (JSONObject) e;
                        if (eo.has("message")) message = eo.getString("message");
                    }
                }
            } catch (Exception ignore) {}
            throw new Exception("HTTP " + code + ": " + truncate(message, 500));
        }
        return new JSONObject(text);
    }

    private static String extractText(JSONObject resp) throws Exception {
        JSONArray candidates = resp.optJSONArray("candidates");
        if (candidates == null || candidates.length() == 0) {
            if (resp.has("promptFeedback")) {
                return "(Blocked) " + resp.getJSONObject("promptFeedback").toString();
            }
            return "";
        }
        JSONObject cand = candidates.getJSONObject(0);
        JSONObject content = cand.optJSONObject("content");
        if (content == null) return "";
        JSONArray parts = content.optJSONArray("parts");
        if (parts == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length(); i++) {
            JSONObject p = parts.getJSONObject(i);
            if (p.has("text")) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(p.getString("text"));
            }
        }
        return sb.toString();
    }

    private static String readAll(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString().trim();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
