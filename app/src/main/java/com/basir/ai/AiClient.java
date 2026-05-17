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
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * AiClient: HTTP client for the Basir secure proxy server (Gemini-powered).
 * The Gemini API key never lives inside the APK - it lives only on the proxy.
 */
public final class AiClient {

    private AiClient() {}

    /** Path appended to the proxy base URL for the chat/vision endpoint. */
    private static String chatEndpoint(String baseUrl) {
        String u = baseUrl.trim();
        if (u.endsWith("/api/basir") || u.endsWith("/api/basir/")) return u;
        if (u.endsWith("/")) return u + "api/basir";
        return u + "/api/basir";
    }

    /** Path appended to the proxy base URL for the PDF/PPTX -> Word endpoint. */
    private static String convertEndpoint(String baseUrl) {
        String u = baseUrl.trim();
        if (u.endsWith("/api/basir")) u = u.substring(0, u.length() - "/api/basir".length());
        if (u.endsWith("/api/basir/")) u = u.substring(0, u.length() - "/api/basir/".length());
        if (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u + "/api/convert";
    }

    public static String ask(String baseUrl, String appToken, String task,
                             String input, String instruction, String language) throws Exception {
        return ask(baseUrl, appToken, task, input, instruction, language, null, null);
    }

    public static String ask(String baseUrl, String appToken, String task,
                             String input, String instruction, String language,
                             String imageBase64, String mimeType) throws Exception {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
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

        HttpURLConnection conn = (HttpURLConnection) new URL(chatEndpoint(baseUrl)).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(120000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "Basir-Android/1.0.1");
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
            return response;
        } catch (Exception parseErr) {
            return response;
        }
    }

    /**
     * Upload a PDF or PPTX file to the proxy and save the returned .docx
     * into outFile. Returns the absolute path of outFile on success.
     */
    public static String convertToDocx(Context ctx, String baseUrl, String appToken,
                                       Uri sourceUri, String mode, String language,
                                       File outFile) throws Exception {
        if (baseUrl == null || baseUrl.trim().isEmpty()) throw new Exception("Proxy URL is empty");

        String boundary = "----BasirBoundary" + System.currentTimeMillis();
        ContentResolver resolver = ctx.getContentResolver();
        String mime = resolver.getType(sourceUri);
        if (mime == null) mime = "application/octet-stream";
        String filename = "document";
        if (mime.contains("pdf")) filename = "document.pdf";
        else if (mime.contains("presentation")) filename = "document.pptx";

        HttpURLConnection conn = (HttpURLConnection) new URL(convertEndpoint(baseUrl)).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(300000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        conn.setRequestProperty("User-Agent", "Basir-Android/1.0.1");
        if (appToken != null && !appToken.trim().isEmpty()) {
            conn.setRequestProperty("X-Basir-Client-Token", appToken.trim());
        }

        try (DataOutputStream out = new DataOutputStream(conn.getOutputStream())) {
            // Field: language
            writeFormField(out, boundary, "language", language == null ? "ar" : language);
            // Field: mode
            writeFormField(out, boundary, "mode", mode == null ? "full" : mode);
            // Field: file
            out.writeBytes("--" + boundary + "\r\n");
            out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n");
            out.writeBytes("Content-Type: " + mime + "\r\n\r\n");
            try (InputStream in = resolver.openInputStream(sourceUri)) {
                if (in == null) throw new Exception("Could not open the chosen file");
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            }
            out.writeBytes("\r\n");
            out.writeBytes("--" + boundary + "--\r\n");
        }

        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            String err = readAll(conn.getErrorStream());
            throw new Exception("HTTP " + code + ": " + truncate(err, 400));
        }

        try (InputStream in = conn.getInputStream();
             FileOutputStream fos = new FileOutputStream(outFile)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) fos.write(buf, 0, n);
        }
        return outFile.getAbsolutePath();
    }

    private static void writeFormField(DataOutputStream out, String boundary,
                                       String name, String value) throws Exception {
        out.writeBytes("--" + boundary + "\r\n");
        out.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.writeBytes("\r\n");
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
                if (total > maxBytes) throw new Exception("Image larger than allowed limit");
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
