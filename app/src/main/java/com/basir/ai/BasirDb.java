package com.basir.ai;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * BasirDb: local SQLite storage for activity logs, archived AI results,
 * and the personal memory (people, products, places).
 */
public class BasirDb extends SQLiteOpenHelper {

    private static final String DB_NAME = "basir_ai.db";
    private static final int DB_VERSION = 1;

    public BasirDb(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS logs (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "type TEXT, content TEXT, created_at TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS documents (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "title TEXT, kind TEXT, text_content TEXT, summary TEXT, created_at TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS persons (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT, relation TEXT, notes TEXT, created_at TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS products (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT, barcode TEXT, notes TEXT, created_at TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS places (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT, description TEXT, notes TEXT, created_at TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onCreate(db);
    }

    private static String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date());
    }

    public void insertLog(String type, String content) {
        if (content == null || content.trim().isEmpty()) return;
        ContentValues v = new ContentValues();
        v.put("type", type == null ? "info" : type);
        v.put("content", content);
        v.put("created_at", now());
        getWritableDatabase().insert("logs", null, v);
    }

    public List<String> getRecentLogs(int limit) {
        List<String> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT created_at, type, content FROM logs ORDER BY id DESC LIMIT ?",
                new String[]{String.valueOf(limit)})) {
            while (c.moveToNext()) {
                list.add(c.getString(0) + " [" + c.getString(1) + "] " + c.getString(2));
            }
        }
        return list;
    }

    public void clearLogs() {
        getWritableDatabase().delete("logs", null, null);
    }

    public void insertDocument(String title, String kind, String text, String summary) {
        ContentValues v = new ContentValues();
        v.put("title", title);
        v.put("kind", kind);
        v.put("text_content", text);
        v.put("summary", summary);
        v.put("created_at", now());
        getWritableDatabase().insert("documents", null, v);
    }

    public List<String> getRecentDocuments(int limit) {
        List<String> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT created_at, title, kind, summary FROM documents ORDER BY id DESC LIMIT ?",
                new String[]{String.valueOf(limit)})) {
            while (c.moveToNext()) {
                list.add(c.getString(0) + " - " + c.getString(1) +
                        " [" + c.getString(2) + "]: " + c.getString(3));
            }
        }
        return list;
    }

    public void insertPerson(String name, String relation, String notes) {
        if (name == null || name.trim().isEmpty()) return;
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("relation", relation);
        v.put("notes", notes);
        v.put("created_at", now());
        getWritableDatabase().insert("persons", null, v);
    }

    public void insertProduct(String name, String barcode, String notes) {
        if (name == null || name.trim().isEmpty()) return;
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("barcode", barcode);
        v.put("notes", notes);
        v.put("created_at", now());
        getWritableDatabase().insert("products", null, v);
    }

    public void insertPlace(String name, String description, String notes) {
        if (name == null || name.trim().isEmpty()) return;
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("description", description);
        v.put("notes", notes);
        v.put("created_at", now());
        getWritableDatabase().insert("places", null, v);
    }

    public String getMemorySummary(boolean english) {
        StringBuilder sb = new StringBuilder();
        appendSection(sb, "persons", "name, relation, notes",
                english ? "People:" : "الأشخاص:");
        appendSection(sb, "products", "name, barcode, notes",
                english ? "Products:" : "المنتجات:");
        appendSection(sb, "places", "name, description, notes",
                english ? "Places:" : "الأماكن:");
        return sb.toString().trim();
    }

    private void appendSection(StringBuilder sb, String table, String cols, String title) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT " + cols + " FROM " + table + " ORDER BY id DESC LIMIT 50", null)) {
            if (c.getCount() == 0) return;
            sb.append(title).append('\n');
            while (c.moveToNext()) {
                sb.append("- ");
                for (int i = 0; i < c.getColumnCount(); i++) {
                    String v = c.getString(i);
                    if (v != null && !v.isEmpty()) sb.append(v).append(" | ");
                }
                sb.append('\n');
            }
            sb.append('\n');
        }
    }

    public void clearAllData() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("logs", null, null);
        db.delete("documents", null, null);
        db.delete("persons", null, null);
        db.delete("products", null, null);
        db.delete("places", null, null);
    }
}
