package com.basir.ai;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Basir 1.0.1 - main screen.
 * Card-based, screen-reader-first UI. Gemini 3 powered (via the secure proxy).
 */
public class MainActivity extends Activity implements TextToSpeech.OnInitListener {

    public static final String APP_VERSION = "1.0.1";
    public static final String CONTACT_EMAIL = "ubdallahalrashdee@gmail.com";

    private static final int REQ_PERMISSIONS = 1001;
    private static final int REQ_VOICE = 1002;
    private static final int REQ_IMAGE_PICK = 1003;
    private static final int REQ_DOC_PICK = 1004;

    private SharedPreferences prefs;
    private BasirDb db;
    private final ExecutorService aiExecutor = Executors.newSingleThreadExecutor();

    private TextToSpeech tts;
    private boolean ttsReady = false;

    // Settings cache
    private String lang = "ar";
    private boolean privacyMode = true;
    private boolean speechEnabled = true;
    private boolean vibrationEnabled = true;
    private boolean autoSaveResults = false;
    private float ttsRate = 0.95f;
    private int fontStep = 0;

    private LinearLayout root;

    // Pending image
    private String pendingTask, pendingTitle, pendingInstruction, pendingPrompt;

    // ============================================================
    // Lifecycle
    // ============================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("basir_settings", MODE_PRIVATE);
        db = new BasirDb(this);
        loadSettings();
        tts = new TextToSpeech(this, this);
        requestCorePermissions();
        showHome();
    }

    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS) return;
        ttsReady = true;
        applyTtsConfig();
        speak(t("مرحبًا بك في بصير. مساعدك للقراءة والوصف والترجمة.",
                "Welcome to Basir. Your reading, description and translation assistant."));
    }

    @Override
    protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        aiExecutor.shutdownNow();
        super.onDestroy();
    }

    private void loadSettings() {
        lang = prefs.getString("language", "ar");
        privacyMode = prefs.getBoolean("privacy_mode", true);
        speechEnabled = prefs.getBoolean("speech_enabled", true);
        vibrationEnabled = prefs.getBoolean("vibration_enabled", true);
        autoSaveResults = prefs.getBoolean("auto_save", false);
        ttsRate = prefs.getFloat("tts_rate", 0.95f);
        fontStep = prefs.getInt("font_step", 0);
    }

    private void applyTtsConfig() {
        if (tts == null || !ttsReady) return;
        Locale locale = isEnglish() ? Locale.US : new Locale("ar", "SA");
        tts.setLanguage(locale);
        tts.setSpeechRate(ttsRate);
    }

    private void requestCorePermissions() {
        if (Build.VERSION.SDK_INT < 23) return;
        List<String> need = new ArrayList<>();
        addIfMissing(need, Manifest.permission.RECORD_AUDIO);
        addIfMissing(need, Manifest.permission.ACCESS_FINE_LOCATION);
        addIfMissing(need, Manifest.permission.ACCESS_COARSE_LOCATION);
        if (!need.isEmpty()) requestPermissions(need.toArray(new String[0]), REQ_PERMISSIONS);
    }

    private void addIfMissing(List<String> list, String perm) {
        if (checkSelfPermission(perm) != android.content.pm.PackageManager.PERMISSION_GRANTED) list.add(perm);
    }

    // ============================================================
    // Localization
    // ============================================================
    private boolean isEnglish() { return "en".equals(lang); }
    private String t(String ar, String en) { return isEnglish() ? en : ar; }

    // ============================================================
    // Theme colors (resolve via current night/day resources)
    // ============================================================
    private int colorBg()        { return getColor(R.color.basir_bg); }
    private int colorSurface()   { return getColor(R.color.basir_surface); }
    private int colorText()      { return getColor(R.color.basir_text); }
    private int colorTextSec()   { return getColor(R.color.basir_text_secondary); }
    private int colorPrimary()   { return getColor(R.color.basir_primary); }
    private int colorAccent()    { return getColor(R.color.basir_accent); }
    private int colorDanger()    { return getColor(R.color.basir_danger); }
    private int colorSuccess()   { return getColor(R.color.basir_success); }
    private int colorWarning()   { return getColor(R.color.basir_warning); }
    private int colorStroke()    { return getColor(R.color.basir_card_stroke); }

    private boolean isNightMode() {
        int mode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    // ============================================================
    // UI primitives
    // ============================================================

    private void resetScreen(String title, String subtitle) {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(colorBg());
        scroll.setFillViewport(true);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(32));
        scroll.addView(root);
        setContentView(scroll);

        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextSize(textSize(28));
        heading.setTypeface(null, Typeface.BOLD);
        heading.setTextColor(colorText());
        heading.setPadding(0, dp(4), 0, dp(6));
        heading.setContentDescription(title);
        if (Build.VERSION.SDK_INT >= 28) heading.setAccessibilityHeading(true);
        root.addView(heading, fullWidth());

        if (subtitle != null && !subtitle.isEmpty()) {
            TextView sub = new TextView(this);
            sub.setText(subtitle);
            sub.setTextSize(textSize(16));
            sub.setTextColor(colorTextSec());
            sub.setPadding(0, 0, 0, dp(16));
            sub.setLineSpacing(dp(2), 1.1f);
            root.addView(sub, fullWidth());
        }
    }

    /** Section header within a screen (no big title). */
    private void addSection(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(textSize(15));
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextColor(colorTextSec());
        tv.setAllCaps(false);
        tv.setLetterSpacing(0.02f);
        LinearLayout.LayoutParams p = fullWidth();
        p.setMargins(0, dp(20), 0, dp(8));
        if (Build.VERSION.SDK_INT >= 28) tv.setAccessibilityHeading(true);
        root.addView(tv, p);
    }

    /** Large primary action card: title + description, full width, rounded. */
    private void addCard(String title, String description, View.OnClickListener listener) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setClickable(true);
        card.setFocusable(true);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setColor(colorSurface());
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), colorStroke());
        card.setBackground(bg);

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(textSize(20));
        t.setTypeface(null, Typeface.BOLD);
        t.setTextColor(colorText());
        card.addView(t, fullWidth());

        if (description != null && !description.isEmpty()) {
            TextView d = new TextView(this);
            d.setText(description);
            d.setTextSize(textSize(15));
            d.setTextColor(colorTextSec());
            d.setLineSpacing(dp(2), 1.1f);
            LinearLayout.LayoutParams dp_ = fullWidth();
            dp_.setMargins(0, dp(6), 0, 0);
            card.addView(d, dp_);
        }

        card.setContentDescription(title + ". " + (description == null ? "" : description));
        card.setOnClickListener(listener);

        LinearLayout.LayoutParams p = fullWidth();
        p.setMargins(0, dp(8), 0, dp(8));
        root.addView(card, p);
    }

    /** Secondary outline button. */
    private void addOutlineButton(String text, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(textSize(16));
        b.setTextColor(colorPrimary());
        b.setMinHeight(dp(56));
        b.setPadding(dp(16), dp(12), dp(16), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setColor(colorSurface());
        bg.setCornerRadius(dp(28));
        bg.setStroke(dp(1), colorStroke());
        b.setBackground(bg);
        b.setOnClickListener(listener);
        b.setContentDescription(text);
        LinearLayout.LayoutParams p = fullWidth();
        p.setMargins(0, dp(6), 0, dp(6));
        root.addView(b, p);
    }

    /** Filled primary button (call to action). */
    private void addPrimaryButton(String text, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(textSize(17));
        b.setTextColor(Color.WHITE);
        b.setTypeface(null, Typeface.BOLD);
        b.setMinHeight(dp(56));
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setColor(colorPrimary());
        bg.setCornerRadius(dp(28));
        b.setBackground(bg);
        b.setOnClickListener(listener);
        b.setContentDescription(text);
        LinearLayout.LayoutParams p = fullWidth();
        p.setMargins(0, dp(8), 0, dp(8));
        root.addView(b, p);
    }

    /** Danger button (for destructive actions). */
    private void addDangerButton(String text, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(textSize(16));
        b.setTextColor(colorDanger());
        b.setMinHeight(dp(56));
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setColor(colorSurface());
        bg.setCornerRadius(dp(28));
        bg.setStroke(dp(1), colorDanger());
        b.setBackground(bg);
        b.setOnClickListener(listener);
        b.setContentDescription(text);
        LinearLayout.LayoutParams p = fullWidth();
        p.setMargins(0, dp(6), 0, dp(6));
        root.addView(b, p);
    }

    /** Settings row with a Switch widget. */
    private void addSwitchRow(String label, boolean checked, OnToggle action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(10), dp(14), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(colorSurface());
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), colorStroke());
        row.setBackground(bg);

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(textSize(16));
        tv.setTextColor(colorText());
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        row.addView(tv, tp);

        Switch sw = new Switch(this);
        sw.setChecked(checked);
        sw.setContentDescription(label);
        sw.setOnCheckedChangeListener((b, isChecked) -> action.run(isChecked));
        row.addView(sw);

        // Make the whole row clickable to toggle
        row.setOnClickListener(v -> sw.toggle());

        LinearLayout.LayoutParams p = fullWidth();
        p.setMargins(0, dp(6), 0, dp(6));
        root.addView(row, p);
    }

    private interface OnToggle { void run(boolean checked); }

    private void addPlainText(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(textSize(16));
        tv.setTextColor(colorText());
        tv.setPadding(0, dp(4), 0, dp(4));
        tv.setLineSpacing(dp(2), 1.15f);
        root.addView(tv, fullWidth());
    }

    private void addBackButton() {
        addOutlineButton(t("رجوع", "Back"), v -> showHome());
    }

    private EditText makeInput(String hint, boolean multiline) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setContentDescription(hint);
        e.setTextColor(colorText());
        e.setHintTextColor(colorTextSec());
        e.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(colorSurface());
        bg.setStroke(dp(1), colorStroke());
        bg.setCornerRadius(dp(12));
        e.setBackground(bg);
        if (multiline) {
            e.setMinLines(4);
            e.setInputType(InputType.TYPE_CLASS_TEXT |
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE |
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            e.setGravity(Gravity.TOP | Gravity.START);
        } else {
            e.setSingleLine(true);
        }
        return e;
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    private float textSize(float base) {
        switch (fontStep) {
            case 1: return base + 2f;
            case 2: return base + 5f;
            default: return base;
        }
    }

    // ============================================================
    // Home: 5 primary cards + More
    // ============================================================

    private void showHome() {
        resetScreen(t("بصير", "Basir"),
                t("مساعدك الذكي للقراءة، الوصف، الترجمة، والمستندات.",
                  "Your smart assistant for reading, description, translation and documents."));

        addCard(t("اسأل بصير", "Ask Basir"),
                t("اسأل بالصوت أو الكتابة واحصل على إجابة واضحة.",
                  "Ask by voice or text and get a clear answer."),
                v -> showAskScreen());

        addCard(t("وصف صورة أو مشهد", "Describe an image or scene"),
                t("صف الصور، لقطات الشاشة، والمشاهد المحيطة.",
                  "Describe images, screenshots and surrounding scenes."),
                v -> showDescribeScreen());

        addCard(t("قراءة المستندات", "Read a document"),
                t("قراءة PDF، الصور، الفواتير، العقود، والعروض التقديمية.",
                  "Read PDF, images, invoices, contracts, presentations."),
                v -> showDocumentScreen());

        addCard(t("ترجمة وشرح", "Translate & explain"),
                t("ترجمة النصوص مع تبسيط المعنى وشرح النبرة.",
                  "Translate texts and explain meaning and tone."),
                v -> showTranslateScreen());

        addCard(t("الطوارئ والمساعدة", "Emergency & help"),
                t("إرسال موقعك أو طلب مساعدة من جهة محفوظة.",
                  "Share your location or request help from a saved contact."),
                v -> showEmergencyScreen());

        addOutlineButton(t("المزيد من الأدوات", "More tools"), v -> showMoreScreen());

        // Bottom: status pill
        addOutlineButton(t("حالة التطبيق", "App status"), v -> showStatusScreen());
    }

    private void showMoreScreen() {
        resetScreen(t("المزيد من الأدوات", "More tools"),
                t("أدوات إضافية ومحفوظاتك.", "Additional tools and your saved data."));

        addCard(t("أدوات متقدمة", "Advanced tools"),
                t("وصف بديل، قراءة لقطة شاشة، بطاقات مذاكرة، صياغة رد، قراءة جدول.",
                  "Alt text, screenshot reading, study cards, reply drafting, table reading."),
                v -> showAdvancedScreen());

        addCard(t("محفوظاتي الخاصة", "My saved items"),
                t("الأشخاص، المنتجات، الأدوية، والأماكن.",
                  "People, products, medicine, and places."),
                v -> showMemoryScreen());

        addCard(t("المحفوظات", "Archive"),
                t("نتائج تحليل محفوظة محليًا.", "Locally saved analysis results."),
                v -> showArchiveScreen());

        addCard(t("آخر العمليات", "Recent activity"),
                t("سجل واضح بآخر ما قمت به في التطبيق.",
                  "A clear log of your recent actions."),
                v -> showHistoryScreen());

        addCard(t("الإعدادات", "Settings"),
                t("اللغة، الصوت، المظهر، الخصوصية، Gemini، الطوارئ.",
                  "Language, voice, theme, privacy, Gemini, emergency."),
                v -> showSettingsScreen());

        addCard(t("حول التطبيق", "About"),
                t("معلومات بصير والتواصل مع المطور.",
                  "About Basir and developer contact."),
                v -> showAboutScreen());

        addOutlineButton(t("أمر صوتي", "Voice command"), v -> startVoiceCommand());
        addBackButton();
    }

    // ============================================================
    // App status (separate screen instead of cluttering every page)
    // ============================================================

    private void showStatusScreen() {
        resetScreen(t("حالة التطبيق", "App status"),
                t("ملخص الإعدادات الحالية.", "Summary of current settings."));

        addPlainText(t("اللغة: ", "Language: ") + (isEnglish() ? "English" : "العربية"));
        addPlainText(t("وضع الخصوصية: ", "Privacy mode: ")
                + (privacyMode ? t("مفعل", "on") : t("معطل", "off")));
        addPlainText("Gemini: " + (AiClient.isConfigured(prefs)
                ? t("متصل", "connected") : t("يحتاج إعداد", "needs setup"))
                + " · " + (AiClient.MODE_DIRECT.equals(AiClient.getMode(prefs))
                        ? t("اتصال مباشر", "direct")
                        : t("خادم وسيط", "proxy")));
        addPlainText(t("النطق الصوتي: ", "Speech output: ")
                + (speechEnabled ? t("يعمل", "on") : t("متوقف", "off")));
        addPlainText(t("الاهتزاز: ", "Vibration: ")
                + (vibrationEnabled ? t("يعمل", "on") : t("متوقف", "off")));
        addPlainText(t("الحفظ التلقائي: ", "Auto-save: ")
                + (autoSaveResults ? t("مفعل", "on") : t("متوقف", "off")));
        addPlainText(t("قارئ الشاشة: ", "Screen reader: ")
                + (isTalkBackOn() ? t("يعمل", "detected") : t("غير مكتشف", "not detected")));
        addPlainText(t("الإصدار: ", "Version: ") + APP_VERSION);

        addBackButton();
    }

    private boolean isTalkBackOn() {
        AccessibilityManager am = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        return am != null && am.isEnabled() && am.isTouchExplorationEnabled();
    }

    // ============================================================
    // Ask Basir
    // ============================================================

    private void showAskScreen() {
        resetScreen(t("اسأل بصير", "Ask Basir"),
                t("اكتب سؤالك أو استخدم الإملاء الصوتي.",
                  "Type your question or use voice dictation."));

        EditText input = makeInput(t("اكتب سؤالك هنا", "Type your question here"), true);
        root.addView(input, fullWidth());

        addPrimaryButton(t("إرسال", "Send"), v -> {
            String q = input.getText().toString().trim();
            if (q.isEmpty()) {
                speak(t("اكتب سؤالك أولًا.", "Type your question first."));
                return;
            }
            callAi("ask", q, t("إجابة بصير", "Basir answer"),
                    "Answer as Basir, screen-reader friendly and practical.");
        });
        addOutlineButton(t("إملاء صوتي", "Voice dictation"), v -> startVoiceCommand());
        addOutlineButton(t("مسح", "Clear"), v -> input.setText(""));
        addBackButton();
    }

    // ============================================================
    // Describe image / scene
    // ============================================================

    private void showDescribeScreen() {
        resetScreen(t("وصف صورة أو مشهد", "Describe an image or scene"),
                t("اختر صورة من المعرض أو اكتب وصفًا للمشهد.",
                  "Pick an image from the gallery or type a scene description."));

        addCard(t("وصف تفصيلي للصورة", "Detailed image description"),
                t("تحليل دقيق مناسب للمكفوفين.",
                  "Detailed analysis suitable for blind users."),
                v -> pickImageForAi("image_describe",
                        t("وصف الصورة", "Image description"),
                        "Provide a detailed description suitable for a blind user. " +
                        "Start with a one-sentence summary, then objects, layout, visible text, and any practical notes.",
                        "Describe this image in detail."));

        addCard(t("إنشاء وصف بديل للصورة", "Generate alt text"),
                t("نص قصير ومنظم يصلح كـ alt للصورة.",
                  "Short structured alt text."),
                v -> pickImageForAi("alt_text",
                        t("الوصف البديل", "Alt text"),
                        "Write precise alt text for a blind user: objects, spatial relationships, " +
                        "colors, visible text, practical relevance.",
                        "Write detailed alt text for this image."));

        addCard(t("قراءة لقطة شاشة", "Read a screenshot"),
                t("شرح عناصر الشاشة والخطوة التالية.",
                  "Explain screen elements and next step."),
                v -> pickImageForAi("screenshot",
                        t("قراءة لقطة الشاشة", "Screenshot reading"),
                        "Explain the screenshot for a screen-reader user: page, buttons, messages, errors, and the next useful step.",
                        "Read this screenshot."));

        addOutlineButton(t("وصف نصي للمشهد", "Type a scene description"),
                v -> showTextTaskScreen("scene_text",
                        t("وصف المشهد", "Scene description"),
                        t("اكتب وصفًا للمكان وسأحوله إلى توجيه عملي.",
                          "Type a scene and I'll turn it into practical guidance."),
                        "Turn the written scene into practical guidance: summary, obstacles, directions, risk level, next step."));

        addBackButton();
    }

    // ============================================================
    // Documents
    // ============================================================

    private void showDocumentScreen() {
        resetScreen(t("قراءة المستندات", "Read a document"),
                t("حلّل نصًا، فاتورة، عقدًا، ورقة طبية، أو حوّل ملفًا إلى Word.",
                  "Analyze text, invoice, contract, medical note, or convert a file to Word."));

        addCard(t("تحليل نص أو مستند", "Analyze text or document"),
                t("الصق النص للتحليل العميق.", "Paste text for deep analysis."),
                v -> showTextTaskScreen("document_analysis",
                        t("تحليل المستند", "Document analysis"),
                        t("الصق النص هنا.", "Paste the text here."),
                        "Analyze for a blind user. Extract document type, summary, dates, amounts, parties, warnings, next steps."));

        addCard(t("تحليل فاتورة", "Analyze invoice"),
                t("الجهة، المبلغ، تاريخ الاستحقاق، رقم الحساب.",
                  "Issuer, amount, due date, account number."),
                v -> showTextTaskScreen("invoice",
                        t("تحليل فاتورة", "Invoice analysis"),
                        t("الصق نص الفاتورة هنا.", "Paste the invoice text here."),
                        "Extract issuer, total, due date, account number, period, late fees, and one action item."));

        addCard(t("تحليل عقد قانوني", "Legal contract analysis"),
                t("شرح تعليمي لا يغني عن مختص.",
                  "Educational explanation - not a substitute for a professional."),
                v -> showTextTaskScreen("legal",
                        t("تحليل قانوني", "Legal analysis"),
                        t("الصق نص العقد هنا.", "Paste the contract text here."),
                        "Educational legal analysis: parties, obligations, durations, penalty clauses, termination, jurisdiction."));

        addCard(t("تحليل ورقة طبية", "Medical note analysis"),
                t("شرح آمن دون تشخيص.", "Safe explanation without diagnosis."),
                v -> showTextTaskScreen("health",
                        t("تحليل طبي آمن", "Safe health analysis"),
                        t("الصق النص الطبي هنا.", "Paste the medical text here."),
                        "Safe analysis: medication names, dosage, warnings; advise consulting a doctor or pharmacist."));

        addCard(t("تحويل إلى Word قابل للقراءة", "Convert to readable Word"),
                t("حوّل PDF أو PowerPoint إلى Word منظم، مع وصف الصور والجداول للمكفوفين.",
                  "Convert PDF or PowerPoint to a structured Word file, with image and table descriptions."),
                v -> showConvertScreen());

        addBackButton();
    }

    // ============================================================
    // Translate
    // ============================================================

    private void showTranslateScreen() {
        resetScreen(t("ترجمة وشرح", "Translate & explain"),
                t("ترجمة سياقية مع شرح النبرة.",
                  "Contextual translation with tone notes."));

        EditText input = makeInput(t("الصق النص للترجمة", "Paste text to translate"), true);
        root.addView(input, fullWidth());

        addPrimaryButton(t("ترجم", "Translate"), v -> {
            String text = input.getText().toString().trim();
            if (text.isEmpty()) {
                speak(t("الصق النص أولًا.", "Paste text first."));
                return;
            }
            String target = isEnglish() ? "Arabic" : "English";
            callAi("translate", text, t("الترجمة", "Translation"),
                    "Translate to " + target + " in a natural, contextual style. " +
                    "Then add a short note about the tone.");
        });
        addOutlineButton(t("مسح", "Clear"), v -> input.setText(""));
        addBackButton();
    }

    // ============================================================
    // Advanced tools
    // ============================================================

    private void showAdvancedScreen() {
        resetScreen(t("أدوات متقدمة", "Advanced tools"),
                t("أدوات إضافية تعمل عبر Gemini.",
                  "Additional tools powered by Gemini."));

        addCard(t("إنشاء بطاقات مذاكرة", "Create study cards"),
                t("نص ← أسئلة وأجوبة منظمة.",
                  "Text → organized Q&A cards."),
                v -> showTextTaskScreen("study_cards",
                        t("بطاقات مذاكرة", "Study cards"),
                        t("الصق النص هنا.", "Paste text here."),
                        "Turn the text into direct Q&A study cards suitable for audio review."));

        addCard(t("صياغة رد مهذب", "Draft a polite reply"),
                t("اقتراح رد مناسب للنبرة بالعربية والإنجليزية.",
                  "Tone-aware reply in Arabic and English."),
                v -> showTextTaskScreen("reply",
                        t("رد مناسب", "Suitable reply"),
                        t("الصق الرسالة هنا.", "Paste the message here."),
                        "Explain the message tone and suggest a polite reply. Give Arabic and English versions."));

        addCard(t("قراءة جدول كنص", "Read a table as text"),
                t("جداول معقدة ← نص قابل للقراءة.",
                  "Complex tables → screen-reader-friendly text."),
                v -> showTextTaskScreen("table_to_text",
                        t("قراءة جدول", "Table to text"),
                        t("الصق نص الجدول هنا.", "Paste the table text here."),
                        "Convert the table-like text into clear plain-text rows with labels for each value."));

        addBackButton();
    }

    // ============================================================
    // Convert (PDF/PPTX -> Word)
    // ============================================================

    private void showConvertScreen() {
        resetScreen(t("تحويل إلى Word", "Convert to Word"),
                t("حوّل PDF أو PowerPoint إلى Word منظم. سيرفع الملف لخادم المعالجة ثم يُحذف بعد التحويل.",
                  "Convert PDF or PowerPoint to a structured Word file. The file is uploaded for processing and deleted after conversion."));

        addPlainText(t("الملفات المدعومة: PDF، PPT، PPTX.",
                       "Supported formats: PDF, PPT, PPTX."));

        addPrimaryButton(t("اختر ملفًا للتحويل", "Choose a file to convert"), v -> {
            if (!AiClient.isConfigured(prefs)) {
                speak(t("Gemini يحتاج إعداد أولًا.", "Gemini needs setup first."));
                showAiSettingsDialog();
                return;
            }
            confirmAndPickFile();
        });
        addBackButton();
    }

    private void confirmAndPickFile() {
        new AlertDialog.Builder(this)
                .setTitle(t("تأكيد الخصوصية", "Privacy confirmation"))
                .setMessage(t(
                        "سيتم رفع هذا الملف إلى خادم المعالجة لتحويله إلى Word. لا يتم حفظ الملف بعد انتهاء العملية. هل تريد المتابعة؟",
                        "This file will be uploaded for processing into a Word file. It will be deleted after conversion. Continue?"))
                .setPositiveButton(t("متابعة", "Continue"), (d, w) -> {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("*/*");
                    String[] types = {
                        "application/pdf",
                        "application/vnd.ms-powerpoint",
                        "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                    };
                    i.putExtra(Intent.EXTRA_MIME_TYPES, types);
                    try { startActivityForResult(i, REQ_DOC_PICK); }
                    catch (Exception e) {
                        speak(t("تعذر فتح منتقي الملفات.", "Could not open file picker."));
                    }
                })
                .setNegativeButton(t("إلغاء", "Cancel"), null)
                .show();
    }

    private void handleConvertFile(Uri uri) {
        resetScreen(t("جاري التحويل", "Converting"),
                t("جاري رفع الملف ومعالجته عبر Gemini. قد يستغرق دقيقة أو دقيقتين.",
                  "Uploading and processing via Gemini. This may take a minute or two."));
        addPlainText(t("جاري رفع الملف...", "Uploading file..."));
        speak(t("جاري التحويل.", "Converting now."));

        aiExecutor.execute(() -> {
            try {
                File out = new File(getExternalFilesDir(null),
                        "basir-" + System.currentTimeMillis() + ".docx");
                AiClient.convertToDocx(MainActivity.this, prefs, uri,
                        "full", lang, out);
                log("convert", out.getName());
                runOnUiThread(() -> showConvertResult(out));
            } catch (Exception e) {
                final String msg = safeError(e.getMessage());
                log("convert_error", msg);
                runOnUiThread(() -> {
                    resetScreen(t("تعذر إكمال التحويل", "Conversion failed"), msg);
                    addBackButton();
                });
            }
        });
    }

    private void showConvertResult(File docx) {
        resetScreen(t("تم إنشاء الملف بنجاح", "File created successfully"),
                t("ملف Word جاهز يحتوي على النص ووصف الصور والجداول.",
                  "Word file ready with text, image and table descriptions."));
        speak(t("تم إنشاء ملف Word بنجاح.", "Word file created successfully."));
        addPlainText(t("اسم الملف: ", "File name: ") + docx.getName());

        addPrimaryButton(t("فتح ملف Word", "Open the Word file"), v -> {
            Uri uri = Uri.fromFile(docx);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri,
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try { startActivity(i); }
            catch (Exception e) { speak(t("لا يوجد تطبيق لفتح Word.", "No app available to open Word.")); }
        });
        addOutlineButton(t("مشاركة الملف", "Share the file"), v -> {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            i.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(docx));
            startActivity(Intent.createChooser(i, t("مشاركة", "Share")));
        });
        addOutlineButton(t("حذف الملف من الجهاز", "Delete file from device"), v -> {
            if (docx.delete()) speak(t("تم الحذف.", "Deleted."));
            showHome();
        });
        addBackButton();
    }

    // ============================================================
    // Emergency
    // ============================================================

    private void showEmergencyScreen() {
        resetScreen(t("الطوارئ والمساعدة", "Emergency & help"),
                t("استغاثة سريعة مع مشاركة موقعك التقريبي.",
                  "Quick help with approximate location sharing."));

        String contact = prefs.getString("emergency_contact", "");
        addPlainText(contact.isEmpty()
                ? t("لم يتم حفظ جهة طوارئ بعد.", "No emergency contact saved yet.")
                : t("جهة الطوارئ محفوظة: ", "Emergency contact saved: ") + contact);

        addPrimaryButton(t("إرسال استغاثة الآن", "Send help now"),
                v -> confirmAndSendEmergency());
        addOutlineButton(t("مشاركة موقعي الحالي", "Share my current location"),
                v -> shareLocation());
        addOutlineButton(t("تشغيل صوت لتحديد مكاني", "Play locator sound"), v -> {
            log("locator", "play");
            for (int i = 0; i < 3; i++) speak(t("أنا هنا. أحتاج مساعدة.", "I am here. I need help."));
            if (vibrationEnabled) vibrate(1000);
        });
        addOutlineButton(t("إضافة أو تغيير جهة الطوارئ", "Add or change emergency contact"),
                v -> showEmergencyContactDialog());
        addBackButton();
    }

    private void confirmAndSendEmergency() {
        new AlertDialog.Builder(this)
                .setTitle(t("تأكيد الإرسال", "Confirm send"))
                .setMessage(t("هل تريد إرسال رسالة استغاثة إلى جهة الطوارئ؟",
                              "Send a help message to your emergency contact?"))
                .setPositiveButton(t("إرسال الآن", "Send now"), (d, w) -> sendEmergencySms())
                .setNegativeButton(t("إلغاء", "Cancel"), null)
                .show();
    }

    private void sendEmergencySms() {
        String c = prefs.getString("emergency_contact", "").trim();
        if (c.isEmpty()) { showEmergencyContactDialog(); return; }
        String msg = t("أنا بحاجة إلى مساعدة. موقعي التقريبي: ",
                       "I need help. My approximate location: ") + getLastKnownLocation();
        log("emergency_sms", msg);
        Intent i = new Intent(Intent.ACTION_SENDTO);
        i.setData(Uri.parse("smsto:" + c.replace(" ", "")));
        i.putExtra("sms_body", msg);
        try { startActivity(i); } catch (Exception e) {
            speak(t("تعذر فتح الرسائل.", "Could not open messaging."));
        }
    }

    private void shareLocation() {
        String loc = getLastKnownLocation();
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, loc);
        startActivity(Intent.createChooser(i, t("مشاركة الموقع", "Share location")));
    }

    private String getLastKnownLocation() {
        try {
            if (Build.VERSION.SDK_INT >= 23 &&
                    checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
                            android.content.pm.PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) !=
                            android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return t("إذن الموقع غير ممنوح", "Location permission not granted");
            }
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (lm == null) return t("الموقع غير متاح", "Location unavailable");
            Location loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (loc == null) loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (loc == null) return t("لا يوجد موقع معروف", "No last known location");
            return "https://maps.google.com/?q=" + loc.getLatitude() + "," + loc.getLongitude();
        } catch (Exception e) {
            return t("تعذر جلب الموقع", "Could not get location");
        }
    }

    private void showEmergencyContactDialog() {
        EditText input = makeInput(t("مثال: +9665XXXXXXXX", "e.g.: +9665XXXXXXXX"), false);
        input.setInputType(InputType.TYPE_CLASS_PHONE);
        input.setText(prefs.getString("emergency_contact", ""));
        new AlertDialog.Builder(this)
                .setTitle(t("جهة الطوارئ", "Emergency contact"))
                .setView(input)
                .setPositiveButton(t("حفظ", "Save"), (d, w) -> {
                    prefs.edit().putString("emergency_contact",
                            input.getText().toString().trim()).apply();
                    speak(t("تم الحفظ.", "Saved."));
                    showEmergencyScreen();
                })
                .setNegativeButton(t("إلغاء", "Cancel"), null)
                .show();
    }

    // ============================================================
    // Memory
    // ============================================================

    private void showMemoryScreen() {
        resetScreen(t("محفوظاتي الخاصة", "My saved items"),
                t("احفظ أشخاصًا، منتجات، أدوية، وأماكن. تُخزَّن محليًا على جهازك فقط.",
                  "Save people, products, medicine, and places. Stored locally on your device only."));

        addCard(t("إضافة شخص", "Add person"), null,
                v -> showThreeFieldDialog(t("إضافة شخص", "Add person"),
                        t("الاسم", "Name"), t("العلاقة", "Relation"), t("ملاحظات", "Notes"),
                        (a, b, c) -> { db.insertPerson(a, b, c);
                            speak(t("تم الحفظ.", "Saved.")); showMemoryScreen(); }));

        addCard(t("إضافة منتج أو دواء", "Add product or medicine"), null,
                v -> showThreeFieldDialog(t("إضافة منتج", "Add product"),
                        t("الاسم", "Name"), t("الباركود", "Barcode"), t("ملاحظات", "Notes"),
                        (a, b, c) -> { db.insertProduct(a, b, c);
                            speak(t("تم الحفظ.", "Saved.")); showMemoryScreen(); }));

        addCard(t("إضافة مكان", "Add place"), null,
                v -> showThreeFieldDialog(t("إضافة مكان", "Add place"),
                        t("الاسم", "Name"), t("الوصف", "Description"), t("ملاحظات الوصول", "Access notes"),
                        (a, b, c) -> { db.insertPlace(a, b, c);
                            speak(t("تم الحفظ.", "Saved.")); showMemoryScreen(); }));

        addCard(t("عرض المحفوظات", "Show saved items"), null, v -> {
            String summary = db.getMemorySummary(isEnglish());
            if (summary.isEmpty()) summary = t("لا توجد عناصر محفوظة.", "No saved items.");
            showResult(t("المحفوظات الخاصة", "Saved items"), summary, false);
        });

        addBackButton();
    }

    private interface Three { void run(String a, String b, String c); }

    private void showThreeFieldDialog(String title, String h1, String h2, String h3, Three action) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(8), dp(12), dp(8));
        EditText a = makeInput(h1, false);
        EditText b = makeInput(h2, false);
        EditText c = makeInput(h3, true);
        box.addView(a, fullWidth()); box.addView(b, fullWidth()); box.addView(c, fullWidth());
        new AlertDialog.Builder(this).setTitle(title).setView(box)
                .setPositiveButton(t("حفظ", "Save"), (d, w) -> action.run(
                        a.getText().toString().trim(),
                        b.getText().toString().trim(),
                        c.getText().toString().trim()))
                .setNegativeButton(t("إلغاء", "Cancel"), null).show();
    }

    // ============================================================
    // Archive / History
    // ============================================================

    private void showArchiveScreen() {
        resetScreen(t("المحفوظات", "Archive"),
                t("نتائج تحليل محفوظة محليًا.", "Locally saved analysis results."));
        List<String> docs = db.getRecentDocuments(40);
        if (docs.isEmpty()) addPlainText(t("لا توجد نتائج محفوظة بعد.", "No saved results yet."));
        else for (int i = 0; i < docs.size(); i++) addPlainText((i + 1) + ". " + docs.get(i));
        addBackButton();
    }

    private void showHistoryScreen() {
        resetScreen(t("آخر العمليات", "Recent activity"),
                t("السجل يحفظ النصوص فقط، ولا يحفظ الصور أو الملفات.",
                  "The log stores text only, no images or files."));
        List<String> logs = db.getRecentLogs(40);
        if (logs.isEmpty()) addPlainText(t("لا توجد عمليات بعد.", "No activity yet."));
        else for (int i = 0; i < logs.size(); i++) addPlainText((i + 1) + ". " + humanLog(logs.get(i)));
        addOutlineButton(t("مسح السجل", "Clear log"), v -> {
            db.clearLogs(); speak(t("تم المسح.", "Cleared.")); showHistoryScreen();
        });
        addBackButton();
    }

    /** Replace technical task keys with friendly Arabic/English text. */
    private String humanLog(String raw) {
        String r = raw;
        r = r.replace("[ask]", t("سؤال", "Question"));
        r = r.replace("[translate]", t("ترجمة", "Translation"));
        r = r.replace("[document_analysis]", t("تحليل مستند", "Document analysis"));
        r = r.replace("[image_describe]", t("وصف صورة", "Image description"));
        r = r.replace("[alt_text]", t("وصف بديل", "Alt text"));
        r = r.replace("[screenshot]", t("لقطة شاشة", "Screenshot"));
        r = r.replace("[scene_text]", t("وصف مشهد", "Scene description"));
        r = r.replace("[invoice]", t("فاتورة", "Invoice"));
        r = r.replace("[legal]", t("قانوني", "Legal"));
        r = r.replace("[health]", t("طبي", "Medical"));
        r = r.replace("[study_cards]", t("بطاقات مذاكرة", "Study cards"));
        r = r.replace("[reply]", t("رد", "Reply"));
        r = r.replace("[table_to_text]", t("جدول", "Table"));
        r = r.replace("[convert]", t("تحويل ملف", "File convert"));
        r = r.replace("[emergency_sms]", t("استغاثة", "Emergency"));
        r = r.replace("[locator]", t("صوت تحديد المكان", "Locator"));
        return r;
    }

    // ============================================================
    // Settings (grouped, with Switches)
    // ============================================================

    private void showSettingsScreen() {
        resetScreen(t("الإعدادات", "Settings"),
                t("تخصيص بصير ليناسبك.", "Customize Basir to suit you."));

        addSection(t("اللغة", "Language"));
        addPlainText(t("اللغة الحالية: ", "Current language: ") + (isEnglish() ? "English" : "العربية"));
        addOutlineButton(isEnglish() ? "التبديل إلى العربية" : "Switch to English", v -> {
            lang = isEnglish() ? "ar" : "en";
            prefs.edit().putString("language", lang).apply();
            applyTtsConfig();
            speak(isEnglish() ? "English selected." : "تم اختيار العربية.");
            showSettingsScreen();
        });

        addSection(t("الصوت والاهتزاز", "Voice & vibration"));
        addSwitchRow(t("النطق الصوتي", "Speech output"), speechEnabled, checked -> {
            speechEnabled = checked;
            prefs.edit().putBoolean("speech_enabled", checked).apply();
        });
        addSwitchRow(t("الاهتزاز", "Vibration"), vibrationEnabled, checked -> {
            vibrationEnabled = checked;
            prefs.edit().putBoolean("vibration_enabled", checked).apply();
        });
        addOutlineButton(t("سرعة النطق: ", "Speech rate: ") + rateLabel(), v -> showTtsRateDialog());

        addSection(t("المظهر", "Appearance"));
        addOutlineButton(t("حجم الخط: ", "Font size: ") + fontLabel(), v -> showFontStepDialog());
        addPlainText(t("الوضع الداكن يتبع إعدادات النظام تلقائيًا.",
                       "Dark mode follows your system settings automatically."));

        addSection(t("الخصوصية", "Privacy"));
        addSwitchRow(t("وضع الخصوصية", "Privacy mode"), privacyMode, checked -> {
            privacyMode = checked;
            prefs.edit().putBoolean("privacy_mode", checked).apply();
        });
        addSwitchRow(t("الحفظ التلقائي للنتائج", "Auto-save results"), autoSaveResults, checked -> {
            autoSaveResults = checked;
            prefs.edit().putBoolean("auto_save", checked).apply();
        });

        addSection("Gemini");
        addPlainText(t("الحالة: ", "Status: ")
                + (AiClient.isConfigured(prefs) ? t("متصل", "connected") : t("يحتاج إعداد", "needs setup")));
        addOutlineButton(t("إعداد Gemini", "Gemini setup"), v -> showAiSettingsDialog());
        addOutlineButton(t("اختبار اتصال Gemini", "Test Gemini connection"), v -> {
            if (!AiClient.isConfigured(prefs)) { showAiSettingsDialog(); return; }
            callAi("health", t("اختبار اتصال من بصير", "Connection test from Basir"),
                    t("اختبار Gemini", "Gemini test"),
                    "Return one short sentence confirming the connection works.");
        });

        addSection(t("الطوارئ", "Emergency"));
        addOutlineButton(t("جهة الطوارئ", "Emergency contact"), v -> showEmergencyContactDialog());

        addSection(t("بيانات الجهاز", "Device data"));
        addDangerButton(t("حذف بياناتي من الجهاز", "Delete my data from the device"),
                v -> new AlertDialog.Builder(this)
                        .setTitle(t("تأكيد الحذف", "Confirm delete"))
                        .setMessage(t("سيتم حذف السجل والمحفوظات. لا يمكن التراجع.",
                                      "The log and saved items will be deleted. Cannot be undone."))
                        .setPositiveButton(t("احذف", "Delete"), (d, w) -> {
                            db.clearAllData();
                            speak(t("تم الحذف.", "Deleted."));
                            showSettingsScreen();
                        })
                        .setNegativeButton(t("إلغاء", "Cancel"), null).show());

        addBackButton();
    }

    private String rateLabel() {
        if (ttsRate <= 0.75f) return t("بطيء", "Slow");
        if (ttsRate >= 1.2f) return t("سريع", "Fast");
        return t("عادي", "Normal");
    }

    private String fontLabel() {
        switch (fontStep) {
            case 1: return t("كبير", "Large");
            case 2: return t("كبير جدًا", "X-Large");
            default: return t("عادي", "Normal");
        }
    }

    private void showTtsRateDialog() {
        final String[] items = { t("بطيء", "Slow"), t("عادي", "Normal"), t("سريع", "Fast") };
        final float[] values = { 0.7f, 0.95f, 1.3f };
        new AlertDialog.Builder(this).setTitle(t("سرعة النطق", "Speech rate"))
                .setItems(items, (d, which) -> {
                    ttsRate = values[which];
                    prefs.edit().putFloat("tts_rate", ttsRate).apply();
                    applyTtsConfig();
                    showSettingsScreen();
                }).show();
    }

    private void showFontStepDialog() {
        final String[] items = { t("عادي", "Normal"), t("كبير", "Large"), t("كبير جدًا", "X-Large") };
        new AlertDialog.Builder(this).setTitle(t("حجم الخط", "Font size"))
                .setItems(items, (d, which) -> {
                    fontStep = which;
                    prefs.edit().putInt("font_step", fontStep).apply();
                    showSettingsScreen();
                }).show();
    }

    private void showAiSettingsDialog() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(8), dp(12), dp(8));
        scroll.addView(box);

        TextView info = new TextView(this);
        info.setText(t(
                "اختر طريقة الاتصال بـ Gemini. الوضع المباشر يعمل بمفتاح API من Google AI Studio دون أي خادم وسيط.",
                "Choose how to connect to Gemini. Direct mode works with a Google AI Studio key, no proxy required."));
        info.setTextSize(textSize(14));
        info.setTextColor(colorTextSec());
        box.addView(info, fullWidth());

        // ----- Mode selector -----
        final TextView modeLabel = new TextView(this);
        modeLabel.setText(t("وضع الاتصال", "Connection mode"));
        modeLabel.setTextColor(colorText());
        modeLabel.setTextSize(textSize(15));
        modeLabel.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams ml = fullWidth(); ml.setMargins(0, dp(14), 0, dp(6));
        box.addView(modeLabel, ml);

        final boolean[] directMode = { AiClient.MODE_DIRECT.equals(AiClient.getMode(prefs)) };
        final Switch modeSwitch = new Switch(this);
        modeSwitch.setText(t("الاتصال المباشر بـ Gemini (بدون خادم)",
                              "Direct Gemini connection (no server)"));
        modeSwitch.setTextSize(textSize(14));
        modeSwitch.setTextColor(colorText());
        modeSwitch.setChecked(directMode[0]);
        modeSwitch.setContentDescription(t(
                "زر تبديل وضع الاتصال. مفعل يعني اتصال مباشر، غير مفعل يعني عبر خادم وسيط.",
                "Connection-mode toggle. On = direct, off = secure proxy."));
        box.addView(modeSwitch, fullWidth());

        // ----- Direct mode fields -----
        final LinearLayout directGroup = new LinearLayout(this);
        directGroup.setOrientation(LinearLayout.VERTICAL);

        TextView directHelp = new TextView(this);
        directHelp.setText(t(
                "احصل على مفتاح مجاني من Google AI Studio: aistudio.google.com",
                "Get a free key from Google AI Studio: aistudio.google.com"));
        directHelp.setTextSize(textSize(13));
        directHelp.setTextColor(colorTextSec());
        LinearLayout.LayoutParams dh = fullWidth(); dh.setMargins(0, dp(10), 0, 0);
        directGroup.addView(directHelp, dh);

        final EditText geminiKey = makeInput(t("مفتاح Gemini API", "Gemini API key"), false);
        geminiKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        geminiKey.setText(prefs.getString("gemini_api_key", ""));
        LinearLayout.LayoutParams gk = fullWidth(); gk.setMargins(0, dp(8), 0, 0);
        directGroup.addView(geminiKey, gk);

        final EditText modelFast = makeInput(t("نموذج سريع (اختياري)", "Fast model (optional)"), false);
        modelFast.setText(prefs.getString("gemini_model_fast", GeminiDirectClient.DEFAULT_FLASH));
        LinearLayout.LayoutParams mf = fullWidth(); mf.setMargins(0, dp(8), 0, 0);
        directGroup.addView(modelFast, mf);

        final EditText modelPro = makeInput(t("نموذج متقدم (اختياري)", "Pro model (optional)"), false);
        modelPro.setText(prefs.getString("gemini_model_pro", GeminiDirectClient.DEFAULT_PRO));
        LinearLayout.LayoutParams mp = fullWidth(); mp.setMargins(0, dp(8), 0, 0);
        directGroup.addView(modelPro, mp);

        box.addView(directGroup, fullWidth());

        // ----- Proxy mode fields -----
        final LinearLayout proxyGroup = new LinearLayout(this);
        proxyGroup.setOrientation(LinearLayout.VERTICAL);

        TextView proxyHelp = new TextView(this);
        proxyHelp.setText(t(
                "أدخل رابط الخادم الوسيط الذي يحفظ مفتاح Gemini.",
                "Enter the proxy server URL that holds your Gemini key."));
        proxyHelp.setTextSize(textSize(13));
        proxyHelp.setTextColor(colorTextSec());
        LinearLayout.LayoutParams ph = fullWidth(); ph.setMargins(0, dp(10), 0, 0);
        proxyGroup.addView(proxyHelp, ph);

        final EditText url = makeInput("https://your-server/api/basir", false);
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        url.setText(prefs.getString("ai_server_url", ""));
        LinearLayout.LayoutParams up = fullWidth(); up.setMargins(0, dp(8), 0, 0);
        proxyGroup.addView(url, up);

        final EditText token = makeInput(t("رمز التطبيق (اختياري)", "App token (optional)"), false);
        token.setText(prefs.getString("ai_app_token", ""));
        LinearLayout.LayoutParams tp = fullWidth(); tp.setMargins(0, dp(8), 0, 0);
        proxyGroup.addView(token, tp);

        box.addView(proxyGroup, fullWidth());

        directGroup.setVisibility(directMode[0] ? View.VISIBLE : View.GONE);
        proxyGroup.setVisibility(directMode[0] ? View.GONE : View.VISIBLE);

        modeSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
            directMode[0] = isChecked;
            directGroup.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            proxyGroup.setVisibility(isChecked ? View.GONE : View.VISIBLE);
            speak(isChecked
                    ? t("الاتصال المباشر بـ Gemini.", "Direct Gemini connection.")
                    : t("الاتصال عبر خادم وسيط.", "Connection via secure proxy."));
        });

        new AlertDialog.Builder(this)
                .setTitle(t("إعداد Gemini", "Gemini setup"))
                .setView(scroll)
                .setPositiveButton(t("حفظ", "Save"), (d, w) -> {
                    SharedPreferences.Editor e = prefs.edit();
                    e.putString("ai_mode", directMode[0] ? AiClient.MODE_DIRECT : AiClient.MODE_PROXY);
                    if (directMode[0]) {
                        e.putString("gemini_api_key", geminiKey.getText().toString().trim());
                        e.putString("gemini_model_fast", modelFast.getText().toString().trim());
                        e.putString("gemini_model_pro",  modelPro.getText().toString().trim());
                    } else {
                        e.putString("ai_server_url", url.getText().toString().trim());
                        e.putString("ai_app_token", token.getText().toString().trim());
                    }
                    e.apply();
                    speak(AiClient.isConfigured(prefs)
                            ? t("تم الحفظ.", "Saved.")
                            : t("الإعداد غير مكتمل.", "Configuration is incomplete."));
                    showSettingsScreen();
                })
                .setNegativeButton(t("إلغاء", "Cancel"), null)
                .show();
    }

    // ============================================================
    // About
    // ============================================================

    private void showAboutScreen() {
        resetScreen(t("حول التطبيق", "About"),
                t("بصير - مساعد ذكي للمكفوفين وضعاف البصر.",
                  "Basir - smart assistant for blind and low-vision users."));

        addPlainText(t(
                "بصير يساعدك في قراءة المستندات، وصف الصور، ترجمة النصوص، تنظيم محفوظاتك، والاستفادة من أدوات الذكاء الاصطناعي بطريقة آمنة وسهلة.\n\n" +
                "مهم: التطبيق أداة مساعدة فقط، ولا يغني عن العصا البيضاء، الطبيب، المحامي، أو خدمات الطوارئ الرسمية في المواقف الخطرة.\n\n" +
                "الخصوصية: لا يتم حفظ الصور أو الملفات تلقائيًا. تتم المعالجة بعد موافقة المستخدم، ويمكن حذف البيانات المحلية من الإعدادات.\n\n" +
                "الإصدار: " + APP_VERSION + "\n" +
                "المطور: عبدالله الراشدي\n" +
                "البريد: " + CONTACT_EMAIL,

                "Basir helps you read documents, describe images, translate texts, organize your saved items, and use AI tools in a safe and simple way.\n\n" +
                "Important: The app is assistive only and does not replace a white cane, a doctor, a lawyer, or official emergency services in dangerous situations.\n\n" +
                "Privacy: Images and files are never saved automatically. Processing happens only after you confirm, and local data can be deleted from settings.\n\n" +
                "Version: " + APP_VERSION + "\n" +
                "Developer: Abdullah Al-Rashidi\n" +
                "Email: " + CONTACT_EMAIL));

        addPrimaryButton(t("مراسلة المطور", "Email the developer"), v -> {
            Intent i = new Intent(Intent.ACTION_SENDTO);
            i.setData(Uri.parse("mailto:" + CONTACT_EMAIL));
            i.putExtra(Intent.EXTRA_SUBJECT, "Basir feedback");
            try { startActivity(i); } catch (Exception e) {
                speak(t("تعذر فتح البريد.", "Could not open email."));
            }
        });
        addOutlineButton(t("مشاركة التطبيق", "Share the app"), v -> {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, t(
                    "تطبيق بصير - مساعد ذكي للمكفوفين. تواصل: " + CONTACT_EMAIL,
                    "Basir - smart assistant for blind users. Contact: " + CONTACT_EMAIL));
            startActivity(Intent.createChooser(i, t("مشاركة", "Share")));
        });
        addBackButton();
    }

    // ============================================================
    // Text + image AI flow (full screen, not dialog)
    // ============================================================

    private void showTextTaskScreen(String task, String title, String hint, String instruction) {
        resetScreen(title, t("اكتب أو ألصق النص ثم اضغط تشغيل.",
                             "Type or paste the text, then tap Run."));
        if (!AiClient.isConfigured(prefs)) {
            addPlainText(t("Gemini يحتاج إعداد. افتح الإعدادات ثم إعداد Gemini.",
                           "Gemini needs setup. Open Settings → Gemini setup."));
            addOutlineButton(t("إعداد Gemini الآن", "Open Gemini setup now"), v -> showAiSettingsDialog());
            addBackButton();
            return;
        }
        EditText input = makeInput(hint, true);
        root.addView(input, fullWidth());
        addPrimaryButton(t("تشغيل", "Run"), v -> {
            String text = input.getText().toString().trim();
            if (text.isEmpty()) { speak(t("اكتب نصًا أولًا.", "Type some text first.")); return; }
            callAi(task, text, title, instruction);
        });
        addBackButton();
    }

    private void pickImageForAi(String task, String title, String instruction, String prompt) {
        if (!AiClient.isConfigured(prefs)) {
            speak(t("Gemini يحتاج إعداد.", "Gemini needs setup."));
            showAiSettingsDialog();
            return;
        }
        pendingTask = task; pendingTitle = title;
        pendingInstruction = instruction; pendingPrompt = prompt;
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try { startActivityForResult(Intent.createChooser(i, t("اختر صورة", "Choose image")), REQ_IMAGE_PICK); }
        catch (Exception e) { speak(t("تعذر فتح المنتقي.", "Could not open picker.")); }
    }

    private void handlePickedImage(Uri uri) {
        resetScreen(pendingTitle, t("جاري تحليل الصورة عبر Gemini...",
                                    "Analyzing the image via Gemini..."));
        addPlainText(t("قد يستغرق ذلك بضع ثوانٍ.", "This may take a few seconds."));
        speak(t("جاري التحليل.", "Analyzing now."));

        aiExecutor.execute(() -> {
            try {
                String mime = AiClient.detectMime(MainActivity.this, uri);
                byte[] bytes = AiClient.readUriBytes(MainActivity.this, uri, 6 * 1024 * 1024);
                String b64 = AiClient.encodeBase64(bytes);
                String answer = AiClient.ask(prefs, pendingTask, pendingPrompt,
                        pendingInstruction, lang, b64, mime);
                log(pendingTask, answer);
                runOnUiThread(() -> showResult(pendingTitle, answer, true));
            } catch (Exception e) {
                final String msg = errorMessage(e);
                log("image_error", msg);
                runOnUiThread(() -> {
                    resetScreen(t("تعذر إكمال العملية", "Could not complete the operation"), msg);
                    addBackButton();
                });
            }
        });
    }

    private void callAi(String task, String input, String title, String instruction) {
        if (input == null || input.trim().isEmpty()) {
            speak(t("لم يتم إدخال نص.", "No text entered.")); return;
        }
        if (!AiClient.isConfigured(prefs)) { showAiSettingsDialog(); return; }
        resetScreen(title, t("جاري الاتصال بـ Gemini...", "Connecting to Gemini..."));
        speak(t("جاري المعالجة.", "Processing now."));
        aiExecutor.execute(() -> {
            try {
                String answer = AiClient.ask(prefs, task, input, instruction, lang);
                log(task, input + "\n→ " + answer);
                runOnUiThread(() -> showResult(title, answer, true));
            } catch (Exception e) {
                final String msg = errorMessage(e);
                log("ai_error", msg);
                runOnUiThread(() -> {
                    resetScreen(t("تعذر إكمال العملية", "Could not complete the operation"), msg);
                    addBackButton();
                });
            }
        });
    }

    private String errorMessage(Exception e) {
        return t("تعذر الاتصال بـ Gemini. تحقق من الإنترنت أو إعدادات مزود الذكاء الاصطناعي.",
                 "Could not connect to Gemini. Check your internet or Gemini settings.")
                + "\n\n" + safeError(e.getMessage());
    }

    private String safeError(String s) {
        if (s == null) return "";
        return s.length() > 280 ? s.substring(0, 280) + "..." : s;
    }

    // ============================================================
    // Result screen (structured)
    // ============================================================

    private void showResult(String title, String result, boolean saveable) {
        resetScreen(title, null);
        speak(t("اكتمل التحليل.", "Analysis complete."));

        addPlainText(result);

        if (saveable && autoSaveResults) {
            db.insertDocument(title, "ai_result", result, summarize(result));
        }

        addPrimaryButton(t("قراءة صوتية", "Read aloud"), v -> speak(result));
        addOutlineButton(t("نسخ النتيجة", "Copy result"), v -> {
            android.content.ClipboardManager cm = (android.content.ClipboardManager)
                    getSystemService(CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(android.content.ClipData.newPlainText("basir", result));
                speak(t("تم النسخ.", "Copied."));
            }
        });
        if (saveable) {
            addOutlineButton(t("حفظ في المحفوظات", "Save to archive"), v -> {
                db.insertDocument(title, "ai_result", result, summarize(result));
                speak(t("تم الحفظ.", "Saved."));
            });
        }
        addOutlineButton(t("مشاركة", "Share"), v -> {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, result);
            startActivity(Intent.createChooser(i, t("مشاركة", "Share")));
        });
        addBackButton();
    }

    private String summarize(String s) {
        if (s == null) return "";
        String x = s.replaceAll("\\s+", " ").trim();
        return x.length() <= 200 ? x : x.substring(0, 200) + "...";
    }

    // ============================================================
    // Voice command
    // ============================================================

    private void startVoiceCommand() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, isEnglish() ? "en-US" : "ar-SA");
        i.putExtra(RecognizerIntent.EXTRA_PROMPT,
                t("قل أمرًا أو اطرح سؤالًا.", "Say a command or ask a question."));
        try { startActivityForResult(i, REQ_VOICE); }
        catch (Exception e) {
            speak(t("التعرف الصوتي غير متاح.", "Speech recognition unavailable."));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_VOICE && resultCode == RESULT_OK && data != null) {
            ArrayList<String> res = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (res != null && !res.isEmpty()) handleVoiceCommand(res.get(0));
        } else if (requestCode == REQ_IMAGE_PICK && resultCode == RESULT_OK
                && data != null && data.getData() != null) {
            handlePickedImage(data.getData());
        } else if (requestCode == REQ_DOC_PICK && resultCode == RESULT_OK
                && data != null && data.getData() != null) {
            handleConvertFile(data.getData());
        }
    }

    private void handleVoiceCommand(String cmd) {
        if (cmd == null) return;
        log("voice", cmd);
        String c = cmd.toLowerCase(Locale.ROOT);
        if (contains(c, "اسأل", "ask", "سؤال", "question")) showAskScreen();
        else if (contains(c, "وصف", "describe", "صورة", "image", "مشهد", "scene")) showDescribeScreen();
        else if (contains(c, "قراءة", "read", "مستند", "document", "pdf")) showDocumentScreen();
        else if (contains(c, "ترجم", "translate")) showTranslateScreen();
        else if (contains(c, "طوارئ", "emergency", "مساعدة", "help")) showEmergencyScreen();
        else if (contains(c, "إعداد", "setting")) showSettingsScreen();
        else if (contains(c, "محفوظات", "memory", "saved")) showMemoryScreen();
        else if (contains(c, "حول", "about")) showAboutScreen();
        else {
            // Treat as a direct question to Gemini
            if (AiClient.isConfigured(prefs)) {
                callAi("ask", cmd, t("إجابة بصير", "Basir answer"),
                        "Answer as Basir.");
            } else speak(t("Gemini يحتاج إعداد.", "Gemini needs setup."));
        }
    }

    private boolean contains(String c, String... keys) {
        for (String k : keys) if (c.contains(k)) return true;
        return false;
    }

    // ============================================================
    // Speech / vibration / log helpers
    // ============================================================

    private void speak(String s) {
        if (!speechEnabled || tts == null || !ttsReady || s == null || s.trim().isEmpty()) return;
        tts.speak(s, TextToSpeech.QUEUE_FLUSH, null, "basir");
    }

    private void vibrate(int ms) {
        Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (v == null) return;
        if (Build.VERSION.SDK_INT >= 26)
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
        else v.vibrate(ms);
    }

    private void log(String type, String content) {
        if (db != null) db.insertLog(type, content);
    }
}
