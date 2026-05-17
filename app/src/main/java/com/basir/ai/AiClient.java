package com.basir.ai;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * AiClient: HTTP client for the Basir AI proxy server.
 * The proxy server holds the OpenAI API key (no key inside the APK).
 *
 * Expected proxy contract:
 *   POST {endpoint}
 *   Headers: Content-Type: application/json; X-Basir-Client-Token: <optional token>
 *   Body: { task, input, instruction, language, image_base64?, mime_type? }
 *   Response: { "answer": "..." } or { "error": "..." }
 */
public final class AiClient {

    private AiClient() {}

    public static String ask(String endpoint, String appToken, String task,
                             String input, String instruction, String language) throws Exception {
        return ask(endpoint, appToken, task, input, instruction, language, null, null);
    }

    public static String ask(String endpoint, String appToken, String task,
                             String input, String instruction, String language,
                             String imageBase64, String mimeType) throws Exception {
        if (endpoint == null || endpoint.trim().isEmpty()) {
            throw new Exception("Proxy URL is empty");
        }

        JSONObject body = new JSONObject();
        body.put("task", task == null ? "ask" : task);
        body.put("input", input == null ? "" : input);
        body.put("instruction", instruction == null ? "" : instruction);
        body.put("language", language == null ? "ar" : language);
        if (imageBase64 != null && !imageBase64.trim().isEmpty()) {
            body.put("image_base64", imageBase64);
            body.put("mime_type", (mimeType == null || mimeType.trim().isEmpty()) ? "image/jpeg" : mimeType);
        }

        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint.trim()).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(120000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "BasirAI-Android/1.0");
        if (appToken != null && !appToken.trim().isEmpty()) {
            conn.setRequestProperty("X-Basir-Client-Token", appToken.trim());
        }

        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(bytes);
        }

        int code = conn.getResponseCode();
        InputStream stream = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        String response = readAll(stream);

        if (code < 200 || code >= 300) {
            throw new Exception("HTTP " + code + ": " + truncate(response, 400));
        }
        try {
            JSONObject json = new JSONObject(response);
            if (json.has("answer")) return json.getString("answer");
            if (json.has("error")) throw new Exception(json.getString("error"));
            if (json.has("choices")) {
                JSONArray arr = json.getJSONArray("choices");
                if (arr.length() > 0) {
                    JSONObject first = arr.getJSONObject(0);
                    if (first.has("message")) {
                        return first.getJSONObject("message").optString("content", response);
                    }
                }
            }
            return response;
        } catch (Exception parseErr) {
            return response;
        }
    }

    public static byte[] readUriBytes(Context context, Uri uri, int maxBytes) throws Exception {
        ContentResolver resolver = context.getContentResolver();
        try (InputStream input = resolver.openInputStream(uri);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (input == null) throw new Exception("Could not read the image stream");
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new Exception("Image is larger than the allowed limit");
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    public static String encodeBase64(byte[] data) {
        return Base64.encodeToString(data, Base64.NO_WRAP);
    }

    public static String detectMime(Context context, Uri uri) {
        String mime = context.getContentResolver().getType(uri);
        return (mime == null || mime.trim().isEmpty()) ? "image/jpeg" : mime;
    }

    public static boolean isConfigured(SharedPreferences prefs) {
        String url = prefs.getString("ai_server_url", "").trim();
        return url.startsWith("http://") || url.startsWith("https://");
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
