package com.basir.ai;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Basir AI - main screen of the assistant.
 *
 * Uses only Android framework APIs (no AndroidX) to keep the build simple
 * and bullet-proof on GitHub Actions CI.
 */
public class MainActivity extends Activity implements TextToSpeech.OnInitListener {

    public static final String APP_VERSION = "1.0.0";
    public static final String CONTACT_EMAIL = "ubdallahalrashdee@gmail.com";

    private static final int REQ_PERMISSIONS = 1001;
    private static final int REQ_VOICE = 1002;
    private static final int REQ_IMAGE_PICK = 1003;

    // ----- Persistence -----
    private SharedPreferences prefs;
    private BasirDb db;
    private final ExecutorService aiExecutor = Executors.newSingleThreadExecutor();

    // ----- Speech -----
    private TextToSpeech tts;
    private boolean ttsReady = false;

    // ----- Settings (cached from prefs) -----
    private String lang = "ar";
    private boolean privacyMode = true;
    private boolean speechEnabled = true;
    private boolean vibrationEnabled = true;
    private boolean autoSaveResults = false;
    private float ttsRate = 0.95f;
    private int fontStep = 0; // 0 normal, 1 large, 2 xlarge

    // ----- UI -----
    private LinearLayout root;

    // ----- Pending image analysis params -----
    private String pendingTask = "image_analysis";
    private String pendingTitle = "";
    private String pendingInstruction = "";
    private String pendingPrompt = "";

    // ===================================================================
    // Activity lifecycle
    // ===================================================================

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
        speak(t("مرحبًا بك في بصير AI. مساعدك الذكي للحياة اليومية.",
                "Welcome to Basir AI. Your smart life companion."));
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
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
        int r = tts.setLanguage(locale);
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            toast(t("لغة النطق غير مدعومة بالكامل على هذا الجهاز.",
                    "TTS language is not fully supported on this device."));
        }
        tts.setSpeechRate(ttsRate);
    }

    private void requestCorePermissions() {
        if (Build.VERSION.SDK_INT < 23) return;
        List<String> need = new ArrayList<>();
        addIfMissing(need, Manifest.permission.CAMERA);
        addIfMissing(need, Manifest.permission.RECORD_AUDIO);
        addIfMissing(need, Manifest.permission.ACCESS_FINE_LOCATION);
        addIfMissing(need, Manifest.permission.ACCESS_COARSE_LOCATION);
        if (!need.isEmpty()) requestPermissions(need.toArray(new String[0]), REQ_PERMISSIONS);
    }

    private void addIfMissing(List<String> list, String perm) {
        if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) list.add(perm);
    }

    // ===================================================================
    // Localization helper
    // ===================================================================

    private boolean isEnglish() { return "en".equals(lang); }
    private String t(String ar, String en) { return isEnglish() ? en : ar; }

    // ===================================================================
    // UI primitives
    // ===================================================================

    private void resetScreen(String title, String intro) {
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(28));
        scroll.addView(root);
        setContentView(scroll);

        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextSize(textSize(28));
        heading.setTypeface(null, Typeface.BOLD);
        heading.setGravity(Gravity.CENTER);
        heading.setTextColor(Color.parseColor("#0D47A1"));
        heading.setPadding(0, 0, 0, dp(8));
        heading.setContentDescription((isEnglish() ? "Title: " : "العنوان: ") + title);
        root.addView(heading, fullWidth());

        TextView status = new TextView(this);
        status.setText(buildStatusText());
        status.setTextSize(textSize(15));
        status.setTextColor(Color.parseColor("#37474F"));
        status.setPadding(0, 0, 0, dp(12));
        root.addView(status, fullWidth());

        if (intro != null && !intro.trim().isEmpty()) {
            addParagraph(intro);
            speak(intro);
        }
    }

    private String buildStatusText() {
        String langTxt = isEnglish() ? "English" : "العربية";
        String privacyTxt = privacyMode
                ? t("الخصوصية: مفعّلة", "Privacy: on")
                : t("الخصوصية: غير مفعّلة", "Privacy: off");
        String aiTxt = AiClient.isConfigured(prefs)
                ? t("الذكاء: مهيأ", "AI: configured")
                : t("الذكاء: غير مهيأ", "AI: not configured");
        String talkbackTxt = isTalkBackOn()
                ? t("قارئ الشاشة: مفعّل", "Screen reader: on")
                : t("قارئ الشاشة: غير مكتشف", "Screen reader: off");
        return t("اللغة: ", "Language: ") + langTxt + " • " + privacyTxt +
                " • " + aiTxt + " • " + talkbackTxt;
    }

    private boolean isTalkBackOn() {
        AccessibilityManager am = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        return am != null && am.isEnabled() && am.isTouchExplorationEnabled();
    }

    private void addParagraph(String text) {
        if (text == null || text.isEmpty()) return;
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(textSize(18));
        tv.setTextColor(Color.parseColor("#212121"));
        tv.setPadding(0, dp(6), 0, dp(6));
        tv.setContentDescription(text);
        root.addView(tv, fullWidth());
    }

    private void addButton(String text, String cd, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(textSize(18));
        b.setAllCaps(false);
        b.setPadding(dp(10), dp(14), dp(10), dp(14));
        b.setContentDescription(cd == null ? text : cd);
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams p = fullWidth();
        p.setMargins(0, dp(6), 0, dp(6));
        root.addView(b, p);
    }

    private void addBackButton() {
        addButton(t("⬅ رجوع للرئيسية", "⬅ Back to home"),
                t("زر الرجوع للصفحة الرئيسية.", "Back to home button."),
                v -> showHome());
    }

    private EditText makeInput(String hint, boolean multiline) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setContentDescription(hint);
        if (multiline) {
            e.setMinLines(4);
            e.setInputType(InputType.TYPE_CLASS_TEXT |
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE |
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
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

    // ===================================================================
    // Home screen
    // ===================================================================

    private void showHome() {
        resetScreen(t("بصير AI", "Basir AI"),
                t("مساعدك الذكي للقراءة والتنقل والترجمة والذكاء الاصطناعي.",
                  "Your smart assistant for reading, navigation, translation and AI."));

        addButton(t("📷 وصف المشهد", "📷 Describe scene"),
                t("تحليل الصور والمشاهد بالذكاء الاصطناعي.",
                  "AI-powered scene and image description."),
                v -> showSceneScreen());

        addButton(t("📄 قراءة وتحليل المستندات", "📄 Read & analyze documents"),
                t("قراءة نصوص، فواتير، عقود، تقارير، وملاحظات.",
                  "Read texts, invoices, contracts, reports, notes."),
                v -> showTextReaderScreen());

        addButton(t("💬 اسأل بصير", "💬 Ask Basir"),
                t("اطرح أي سؤال على الذكاء الاصطناعي.",
                  "Ask the AI any question."),
                v -> showAskDialog());

        addButton(t("🧪 مختبر الذكاء", "🧪 AI Lab"),
                t("أدوات متقدمة: وصف بديل، شرح لقطة، بطاقات مذاكرة.",
                  "Advanced tools: alt text, screenshot, study cards."),
                v -> showAiLabScreen());

        addButton(t("🌐 الترجمة الذكية", "🌐 Smart translate"),
                t("ترجمة سياقية بين العربية والإنجليزية.",
                  "Contextual AR/EN translation."),
                v -> showTranslateDialog());

        addButton(t("🚶 مساعد المشي", "🚶 Walking assistant"),
                t("أوامر قصيرة واهتزازات للتوجيه.",
                  "Short alerts and vibrations for guidance."),
                v -> showWalkingScreen());

        addButton(t("🆘 وضع الطوارئ", "🆘 Emergency mode"),
                t("استغاثة سريعة مع مشاركة الموقع.",
                  "Quick help with location sharing."),
                v -> showEmergencyScreen());

        addButton(t("🧠 الذاكرة الشخصية", "🧠 Personal memory"),
                t("الأشخاص، المنتجات، الأماكن المحفوظة.",
                  "Saved people, products, places."),
                v -> showMemoryScreen());

        addButton(t("🗂 الأرشيف", "🗂 Archive"),
                t("النتائج المحفوظة من تحليلات الذكاء الاصطناعي.",
                  "Saved AI analysis results."),
                v -> showArchiveScreen());

        addButton(t("📋 سجل النشاط", "📋 Activity log"),
                t("آخر العمليات المحفوظة محليًا.",
                  "Recent locally saved activity."),
                v -> showHistoryScreen());

        addButton(t("⚙️ الإعدادات", "⚙️ Settings"),
                t("اللغة، الصوت، الخصوصية، الذكاء، الطوارئ.",
                  "Language, voice, privacy, AI, emergency."),
                v -> showSettingsScreen());

        addButton(t("ℹ️ حول التطبيق والتواصل", "ℹ️ About & contact"),
                t("معلومات التطبيق والتواصل مع المطور.",
                  "App info and developer contact."),
                v -> showAboutScreen());

        addButton(t("🎤 أمر صوتي", "🎤 Voice command"),
                t("قل أمرًا مثل: اقرأ، طوارئ، إعدادات.",
                  "Say a command like: read, emergency, settings."),
                v -> startVoiceCommand());
    }

    // ===================================================================
    // Scene
    // ===================================================================

    private void showSceneScreen() {
        log("scene_open", "scene screen");
        resetScreen(t("وصف المشهد", "Scene description"),
                t("اختر صورة من المعرض، أو اكتب وصفًا، أو افتح الكاميرا.",
                  "Pick a gallery image, type a description, or open the camera."));

        addButton(t("🖼 تحليل صورة من المعرض", "🖼 Analyze image from gallery"),
                t("يرسل الصورة المختارة للوسيط الآمن.",
                  "Sends the chosen image to the secure proxy."),
                v -> pickImageForAi("scene_image",
                        t("تحليل المشهد", "Scene analysis"),
                        "Analyze this scene for a blind user. Begin with concise alt text, " +
                        "then describe people without identification, obstacles, visible text, " +
                        "directions, risk level, and safe next steps.",
                        "Describe this scene for a blind user."));

        addButton(t("✍️ وصف نصي للمشهد", "✍️ Type scene description"),
                t("اكتب ما حولك ليحلله الذكاء الاصطناعي.",
                  "Type what's around you for AI analysis."),
                v -> showTextAiDialog("scene_text",
                        t("تحليل المشهد", "Scene analysis"),
                        "Turn the written scene into practical guidance for a blind user. " +
                        "Quick summary, obstacles, directions, risk level, next step."));

        addBackButton();
    }

    // ===================================================================
    // Text reader / document analysis
    // ===================================================================

    private void showTextReaderScreen() {
        log("reader_open", "reader screen");
        resetScreen(t("قراءة وتحليل المستندات", "Read & analyze documents"),
                t("الصق نصًا أو وصف مستند ليحلله الذكاء الاصطناعي.",
                  "Paste text or describe a document for AI analysis."));

        addButton(t("📝 تحليل نص أو مستند", "📝 Analyze text/document"),
                t("يفتح حقل لإدخال نص للتحليل العميق.",
                  "Opens a text field for deep analysis."),
                v -> showTextAiDialog("document_analysis",
                        t("تحليل مستند", "Document analysis"),
                        "Analyze for a blind user. Extract document type, concise summary, " +
                        "dates, amounts, parties, warnings, and practical next steps. " +
                        "For health or legal text, add safety limits."));

        addButton(t("🧾 تحليل فاتورة", "🧾 Analyze invoice"),
                t("تحليل مخصص لتفاصيل الفواتير.",
                  "Invoice-specific analysis."),
                v -> showTextAiDialog("invoice",
                        t("تحليل فاتورة", "Invoice analysis"),
                        "Extract issuer, total, due date, account number, period, " +
                        "any late fee, and a one-sentence action item."));

        addButton(t("⚖️ تحليل عقد قانوني", "⚖️ Legal contract analysis"),
                t("شرح تعليمي لا يغني عن مختص.",
                  "Educational only, not a substitute for a professional."),
                v -> showTextAiDialog("legal",
                        t("تحليل قانوني", "Legal analysis"),
                        "Educational legal analysis. Parties, obligations, durations, " +
                        "penalty clauses, termination, jurisdiction, and review points."));

        addButton(t("💊 تحليل ورقة طبية", "💊 Medical note analysis"),
                t("شرح آمن دون تشخيص.",
                  "Safe explanation without diagnosis."),
                v -> showTextAiDialog("health",
                        t("تحليل طبي آمن", "Safe health analysis"),
                        "Safe analysis: medication names, dosage if present, warnings, " +
                        "and a clear note to consult a doctor or pharmacist."));

        addBackButton();
    }

    // ===================================================================
    // Ask Basir
    // ===================================================================

    private void showAskDialog() {
        final EditText input = makeInput(t("اكتب سؤالك", "Type your question"), true);
        new AlertDialog.Builder(this)
                .setTitle(t("اسأل بصير", "Ask Basir"))
                .setMessage(t("سيتم الإرسال إلى وسيط GPT الآمن.",
                              "Will be sent to the secure GPT proxy."))
                .setView(input)
                .setPositiveButton(t("اسأل", "Ask"), (d, w) -> callAi("ask",
                        input.getText().toString(),
                        t("إجابة بصير", "Basir answer"),
                        "Answer as Basir AI for a blind or low-vision user. " +
                        "Be practical, structured, screen-reader friendly, concise when possible."))
                .setNegativeButton(t("إلغاء", "Cancel"), null)
                .show();
    }

    // ===================================================================
    // AI Lab
    // ===================================================================

    private void showAiLabScreen() {
        resetScreen(t("مختبر الذكاء", "AI Lab"),
                t("أدوات متقدمة تعتمد على GPT.", "Advanced GPT-powered tools."));

        addButton(t("🖼 تحليل صورة بالعمق", "🖼 Deep image analysis"),
                t("تحليل تفصيلي للصورة.", "Detailed image analysis."),
                v -> pickImageForAi("image_analysis",
                        t("تحليل صورة", "Image analysis"),
                        "Detailed alt text, visible text, objects, layout, risks, next steps. " +
                        "Do not identify faces.",
                        "Describe this image in detail for a blind user."));

        addButton(t("📝 وصف بديل للصورة", "📝 Image alt text"),
                t("ينشئ alt text دقيقًا.", "Generates precise alt text."),
                v -> pickImageForAi("alt_text",
                        t("وصف بديل", "Alt text"),
                        "Precise alt text: objects, spatial relationships, colors, " +
                        "visible text, practical relevance.",
                        "Write detailed alt text for this image."));

        addButton(t("📱 شرح لقطة شاشة", "📱 Explain screenshot"),
                t("يشرح عناصر الشاشة وخطوتك التالية.",
                  "Explains screen elements and your next step."),
                v -> pickImageForAi("screenshot",
                        t("شرح لقطة الشاشة", "Screenshot explanation"),
                        "Explain the screenshot for a screen-reader user: " +
                        "page name, buttons, messages, errors, the next useful step.",
                        "Explain this screenshot."));

        addButton(t("📚 بطاقات مذاكرة", "📚 Study cards"),
                t("نص ← أسئلة وأجوبة منظمة.",
                  "Text → Q&A study cards."),
                v -> showTextAiDialog("study_cards",
                        t("بطاقات مذاكرة", "Study cards"),
                        "Turn the text into direct Q&A study cards suitable for audio review."));

        addButton(t("✉️ جهز رد مهذب", "✉️ Polite reply"),
                t("يقترح ردًا مناسبًا للنبرة.",
                  "Suggests a tone-appropriate reply."),
                v -> showTextAiDialog("reply",
                        t("رد مناسب", "Suitable reply"),
                        "Explain the message tone and suggest a polite reply. " +
                        "Provide both Arabic and English when useful."));

        addButton(t("📊 تحويل جدول إلى نص", "📊 Table to text"),
                t("يحول الجداول إلى نص منظم.",
                  "Converts tables into structured text."),
                v -> showTextAiDialog("table_to_text",
                        t("تحويل جدول", "Table to text"),
                        "Convert the table-like text into clear plain-text rows " +
                        "with labels for each value."));

        addBackButton();
    }

    // ===================================================================
    // Translate
    // ===================================================================

    private void showTranslateDialog() {
        final EditText input = makeInput(t("الصق النص للترجمة", "Paste text to translate"), true);
        final String target = isEnglish() ? "Arabic" : "English";
        new AlertDialog.Builder(this)
                .setTitle(t("الترجمة الذكية", "Smart translate"))
                .setMessage(t("ترجمة سياقية مع شرح النبرة.",
                              "Contextual translation with tone notes."))
                .setView(input)
                .setPositiveButton(t("ترجم", "Translate"), (d, w) -> callAi("translate",
                        input.getText().toString(),
                        t("الترجمة", "Translation"),
                        "Translate to " + target + " in a natural, contextual style. " +
                        "Then add a short note about the tone (formal, casual, hesitant, etc.). " +
                        "Keep it screen-reader friendly."))
                .setNegativeButton(t("إلغاء", "Cancel"), null)
                .show();
    }

    // ===================================================================
    // Walking assistant
    // ===================================================================

    private void showWalkingScreen() {
        resetScreen(t("مساعد المشي", "Walking assistant"),
                t("تنبيه: مساعد فقط ولا يستبدل العصا البيضاء.",
                  "Notice: assistive only; does not replace a white cane."));

        addButton(t("🛑 توقف", "🛑 Stop"), null,
                v -> walkCommand(t("توقف", "Stop"), 650, null));
        addButton(t("➡️ يمين قليلًا", "➡️ Slight right"), null,
                v -> walkCommand(t("يمين قليلًا", "Slight right"), 0,
                        new long[]{0, 120, 90, 120}));
        addButton(t("⬅️ يسار قليلًا", "⬅️ Slight left"), null,
                v -> walkCommand(t("يسار قليلًا", "Slight left"), 0,
                        new long[]{0, 100, 80, 100, 80, 100}));
        addButton(t("⚠️ عائق منخفض", "⚠️ Low obstacle"), null,
                v -> walkCommand(t("عائق منخفض", "Low obstacle"), 180, null));
        addButton(t("🚪 باب أمامك", "🚪 Door ahead"), null,
                v -> walkCommand(t("باب أمامك", "Door ahead"), 250, null));
        addButton(t("🪜 سلم", "🪜 Stairs"), null,
                v -> walkCommand(t("سلم أمامك", "Stairs ahead"), 350, null));
        addBackButton();
    }

    private void walkCommand(String msg, int singleVibrate, long[] pattern) {
        log("walk_alert", msg);
        speak(msg);
        if (vibrationEnabled) {
            if (pattern != null) vibratePattern(pattern);
            else if (singleVibrate > 0) vibrate(singleVibrate);
        }
    }

    // ===================================================================
    // Emergency
    // ===================================================================

    private void showEmergencyScreen() {
        resetScreen(t("وضع الطوارئ", "Emergency mode"),
                t("إرسال رسالة استغاثة بموقعك التقريبي.",
                  "Send a help message with your approximate location."));

        String contact = prefs.getString("emergency_contact", "");
        addParagraph(contact.isEmpty()
                ? t("لم يتم حفظ جهة طوارئ بعد.", "No emergency contact saved.")
                : t("جهة الطوارئ: ", "Emergency contact: ") + contact);

        addButton(t("📨 إرسال استغاثة الآن", "📨 Send help now"), null,
                v -> sendEmergencySms());
        addButton(t("🔊 صوت تحديد المكان", "🔊 Locator sound"), null, v -> {
            log("emergency_locator", "locator");
            for (int i = 0; i < 3; i++) speak(t("أنا هنا. أحتاج مساعدة.", "I am here. I need help."));
            if (vibrationEnabled) vibrate(1000);
        });
        addButton(t("📞 حفظ/تغيير جهة الطوارئ", "📞 Set emergency contact"), null,
                v -> showEmergencyContactDialog());
        addBackButton();
    }

    private void sendEmergencySms() {
        String c = prefs.getString("emergency_contact", "").trim();
        if (c.isEmpty()) {
            speak(t("احفظ جهة الطوارئ أولًا.", "Save an emergency contact first."));
            showEmergencyContactDialog();
            return;
        }
        String msg = t("أنا بحاجة إلى مساعدة. موقعي التقريبي: ",
                       "I need help. My approximate location: ") + getLastKnownLocation();
        log("emergency_sms", msg);
        Intent i = new Intent(Intent.ACTION_SENDTO);
        i.setData(Uri.parse("smsto:" + c.replace(" ", "")));
        i.putExtra("sms_body", msg);
        try { startActivity(i); } catch (Exception e) {
            speak(t("تعذر فتح تطبيق الرسائل.", "Could not open messaging."));
        }
    }

    private String getLastKnownLocation() {
        try {
            if (Build.VERSION.SDK_INT >= 23 &&
                    checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
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
        final EditText input = makeInput(t("مثال: +9665XXXXXXXX", "e.g.: +9665XXXXXXXX"), false);
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

    // ===================================================================
    // Memory: people, products, places
    // ===================================================================

    private void showMemoryScreen() {
        resetScreen(t("الذاكرة الشخصية", "Personal memory"),
                t("بيانات محفوظة محليًا على جهازك فقط.",
                  "Data saved locally on your device only."));

        addButton(t("👤 إضافة شخص", "👤 Add person"), null,
                v -> showThreeFieldDialog(t("إضافة شخص", "Add person"),
                        t("الاسم", "Name"),
                        t("العلاقة", "Relation"),
                        t("ملاحظات", "Notes"),
                        (a, b, c) -> { db.insertPerson(a, b, c);
                            speak(t("تم حفظ الشخص.", "Person saved."));
                            showMemoryScreen(); }));

        addButton(t("🛒 إضافة منتج/دواء", "🛒 Add product/medicine"), null,
                v -> showThreeFieldDialog(t("إضافة منتج", "Add product"),
                        t("الاسم", "Name"),
                        t("الباركود", "Barcode"),
                        t("ملاحظات", "Notes"),
                        (a, b, c) -> { db.insertProduct(a, b, c);
                            speak(t("تم حفظ المنتج.", "Product saved."));
                            showMemoryScreen(); }));

        addButton(t("📍 إضافة مكان", "📍 Add place"), null,
                v -> showThreeFieldDialog(t("إضافة مكان", "Add place"),
                        t("الاسم", "Name"),
                        t("الوصف", "Description"),
                        t("ملاحظات الوصول", "Access notes"),
                        (a, b, c) -> { db.insertPlace(a, b, c);
                            speak(t("تم حفظ المكان.", "Place saved."));
                            showMemoryScreen(); }));

        addButton(t("📖 عرض الذاكرة", "📖 Show memory"), null, v -> {
            String summary = db.getMemorySummary(isEnglish());
            if (summary.isEmpty()) summary = t("لا توجد ذاكرة محفوظة بعد.", "No memory saved yet.");
            showResult(t("الذاكرة الشخصية", "Personal memory"), summary, false);
        });

        addBackButton();
    }

    private interface ThreeFieldAction { void run(String a, String b, String c); }

    private void showThreeFieldDialog(String title, String h1, String h2, String h3, ThreeFieldAction action) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(8), dp(12), dp(8));
        EditText e1 = makeInput(h1, false);
        EditText e2 = makeInput(h2, false);
        EditText e3 = makeInput(h3, true);
        box.addView(e1, fullWidth());
        box.addView(e2, fullWidth());
        box.addView(e3, fullWidth());
        new AlertDialog.Builder(this).setTitle(title).setView(box)
                .setPositiveButton(t("حفظ", "Save"), (d, w) -> action.run(
                        e1.getText().toString().trim(),
                        e2.getText().toString().trim(),
                        e3.getText().toString().trim()))
                .setNegativeButton(t("إلغاء", "Cancel"), null).show();
    }

    // ===================================================================
    // Archive & History
    // ===================================================================

    private void showArchiveScreen() {
        resetScreen(t("الأرشيف", "Archive"),
                t("نتائج محفوظة من الذكاء الاصطناعي.",
                  "Saved AI analysis results."));
        List<String> docs = db.getRecentDocuments(40);
        if (docs.isEmpty()) addParagraph(t("لا توجد نتائج محفوظة بعد.", "No saved results yet."));
        else for (int i = 0; i < docs.size(); i++) addParagraph((i + 1) + ". " + docs.get(i));
        addBackButton();
    }

    private void showHistoryScreen() {
        resetScreen(t("سجل النشاط", "Activity log"),
                t("النصوص فقط (لا تُحفظ الصور).",
                  "Text only (images are not saved)."));
        List<String> logs = db.getRecentLogs(40);
        if (logs.isEmpty()) addParagraph(t("لا يوجد نشاط بعد.", "No activity yet."));
        else for (int i = 0; i < logs.size(); i++) addParagraph((i + 1) + ". " + logs.get(i));
        addButton(t("🗑 مسح السجل", "🗑 Clear log"), null, v -> {
            db.clearLogs();
            speak(t("تم المسح.", "Cleared."));
            showHistoryScreen();
        });
        addBackButton();
    }

    // ===================================================================
    // Settings (extensive)
    // ===================================================================

    private void showSettingsScreen() {
        resetScreen(t("الإعدادات", "Settings"),
                t("تخصيص كامل للتطبيق.", "Full customization."));

        // Language
        addButton(isEnglish() ? "🌐 العربية / Switch to Arabic" : "🌐 English / التبديل للإنجليزية", null, v -> {
            lang = isEnglish() ? "ar" : "en";
            prefs.edit().putString("language", lang).apply();
            applyTtsConfig();
            speak(isEnglish() ? "English selected." : "تم اختيار العربية.");
            showSettingsScreen();
        });

        // Privacy
        addButton(privacyMode ? t("🔓 إيقاف الخصوصية", "🔓 Turn privacy off")
                              : t("🔒 تشغيل الخصوصية", "🔒 Turn privacy on"), null, v -> {
            privacyMode = !privacyMode;
            prefs.edit().putBoolean("privacy_mode", privacyMode).apply();
            speak(privacyMode ? t("الخصوصية مفعّلة.", "Privacy on.")
                              : t("الخصوصية متوقفة.", "Privacy off."));
            showSettingsScreen();
        });

        // Speech enabled
        addButton(speechEnabled ? t("🔇 إيقاف النطق", "🔇 Disable speech")
                                : t("🔊 تشغيل النطق", "🔊 Enable speech"), null, v -> {
            speechEnabled = !speechEnabled;
            prefs.edit().putBoolean("speech_enabled", speechEnabled).apply();
            if (speechEnabled) speak(t("تم تشغيل النطق.", "Speech enabled."));
            showSettingsScreen();
        });

        // Vibration
        addButton(vibrationEnabled ? t("📴 إيقاف الاهتزاز", "📴 Disable vibration")
                                   : t("📳 تشغيل الاهتزاز", "📳 Enable vibration"), null, v -> {
            vibrationEnabled = !vibrationEnabled;
            prefs.edit().putBoolean("vibration_enabled", vibrationEnabled).apply();
            speak(vibrationEnabled ? t("الاهتزاز مفعّل.", "Vibration on.")
                                   : t("الاهتزاز متوقف.", "Vibration off."));
            showSettingsScreen();
        });

        // TTS rate
        addButton(t("🎙 سرعة النطق", "🎙 Speech rate") + ": " + rateLabel(), null,
                v -> showTtsRateDialog());

        // Font step
        addButton(t("🔠 حجم الخط", "🔠 Font size") + ": " + fontLabel(), null,
                v -> showFontStepDialog());

        // Auto save
        addButton(autoSaveResults ? t("📥 إيقاف الحفظ التلقائي", "📥 Disable auto-save")
                                  : t("📥 تشغيل الحفظ التلقائي", "📥 Enable auto-save"), null, v -> {
            autoSaveResults = !autoSaveResults;
            prefs.edit().putBoolean("auto_save", autoSaveResults).apply();
            speak(autoSaveResults ? t("الحفظ التلقائي مفعّل.", "Auto-save on.")
                                  : t("الحفظ التلقائي متوقف.", "Auto-save off."));
            showSettingsScreen();
        });

        // Emergency contact
        addButton(t("📞 جهة الطوارئ", "📞 Emergency contact"), null,
                v -> showEmergencyContactDialog());

        // AI proxy
        addButton(t("🔌 إعداد وسيط GPT", "🔌 GPT proxy setup"), null,
                v -> showAiSettingsDialog());

        // Test AI
        addButton(t("✅ اختبار الاتصال بالذكاء", "✅ Test AI connection"), null, v -> {
            if (!AiClient.isConfigured(prefs)) {
                speak(t("اضبط رابط الوسيط أولًا.", "Configure the proxy URL first."));
                showAiSettingsDialog();
                return;
            }
            callAi("health",
                    t("اختبار اتصال من تطبيق بصير AI", "Connection test from Basir AI"),
                    t("اختبار GPT", "GPT test"),
                    "Return one short sentence confirming the proxy is working for Basir AI.");
        });

        // Clear data
        addButton(t("🧹 حذف كل البيانات المحلية", "🧹 Delete all local data"), null,
                v -> new AlertDialog.Builder(this)
                        .setTitle(t("تأكيد الحذف", "Confirm delete"))
                        .setMessage(t("سيتم حذف السجل والأرشيف والذاكرة. لا يمكن التراجع.",
                                      "Log, archive and memory will be deleted. Cannot be undone."))
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
        String[] items = {
                t("بطيء", "Slow"),
                t("عادي", "Normal"),
                t("سريع", "Fast")
        };
        final float[] values = {0.7f, 0.95f, 1.3f};
        new AlertDialog.Builder(this)
                .setTitle(t("سرعة النطق", "Speech rate"))
                .setItems(items, (d, which) -> {
                    ttsRate = values[which];
                    prefs.edit().putFloat("tts_rate", ttsRate).apply();
                    applyTtsConfig();
                    speak(t("تم ضبط السرعة.", "Rate updated."));
                    showSettingsScreen();
                }).show();
    }

    private void showFontStepDialog() {
        String[] items = {
                t("عادي", "Normal"),
                t("كبير", "Large"),
                t("كبير جدًا", "X-Large")
        };
        new AlertDialog.Builder(this)
                .setTitle(t("حجم الخط", "Font size"))
                .setItems(items, (d, which) -> {
                    fontStep = which;
                    prefs.edit().putInt("font_step", fontStep).apply();
                    speak(t("تم تغيير الحجم.", "Font size changed."));
                    showSettingsScreen();
                }).show();
    }

    private void showAiSettingsDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(8), dp(12), dp(8));

        TextView info = new TextView(this);
        info.setText(t("أدخل رابط الوسيط الآمن. لا تضع مفتاح OpenAI داخل التطبيق.",
                       "Enter the secure proxy URL. Never put the OpenAI key inside the app."));
        info.setTextSize(textSize(15));
        box.addView(info, fullWidth());

        final EditText url = makeInput(
                t("https://your-server/api/basir", "https://your-server/api/basir"), false);
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        url.setText(prefs.getString("ai_server_url", ""));
        box.addView(url, fullWidth());

        final EditText token = makeInput(t("رمز التطبيق (اختياري)", "App token (optional)"), false);
        token.setText(prefs.getString("ai_app_token", ""));
        box.addView(token, fullWidth());

        new AlertDialog.Builder(this)
                .setTitle(t("وسيط GPT", "GPT Proxy"))
                .setView(box)
                .setPositiveButton(t("حفظ", "Save"), (d, w) -> {
                    prefs.edit()
                            .putString("ai_server_url", url.getText().toString().trim())
                            .putString("ai_app_token", token.getText().toString().trim())
                            .apply();
                    speak(AiClient.isConfigured(prefs)
                            ? t("تم الحفظ.", "Saved.")
                            : t("تم الحفظ لكن الرابط ناقص.", "Saved but URL is incomplete."));
                    showSettingsScreen();
                })
                .setNegativeButton(t("إلغاء", "Cancel"), null)
                .show();
    }

    // ===================================================================
    // About & Contact
    // ===================================================================

    private void showAboutScreen() {
        resetScreen(t("حول التطبيق", "About"),
                t("بصير AI - عينك الذكية في كل مكان.",
                  "Basir AI - Your smart eye, everywhere."));

        addParagraph(t(
                "الإصدار: " + APP_VERSION + "\n" +
                "المطور: عبدالله الراشدي\n" +
                "البريد: " + CONTACT_EMAIL + "\n\n" +
                "بصير AI مساعد ذكي شامل للمكفوفين وضعاف البصر، " +
                "يجمع بين تحليل المشاهد، قراءة المستندات، الترجمة الذكية، " +
                "مساعدة المشي، الذاكرة الشخصية، ووضع الطوارئ. " +
                "يعتمد على وسيط آمن يشغل GPT دون كشف أي مفاتيح داخل التطبيق.\n\n" +
                "تنبيه: التطبيق مساعد فقط. لا يستبدل العصا البيضاء، الطبيب، " +
                "المحامي، أو خدمات الطوارئ في المواقف الخطرة.",

                "Version: " + APP_VERSION + "\n" +
                "Developer: Abdullah Al-Rashidi\n" +
                "Email: " + CONTACT_EMAIL + "\n\n" +
                "Basir AI is a comprehensive smart assistant for blind and low-vision users. " +
                "It combines scene analysis, document reading, smart translation, walking " +
                "assistance, personal memory and emergency mode. It uses a secure proxy " +
                "to run GPT without exposing any API key inside the app.\n\n" +
                "Notice: The app is assistive only. It does not replace a white cane, a doctor, " +
                "a lawyer, or emergency services in dangerous situations."));

        addButton(t("✉️ راسل المطور", "✉️ Email the developer"), null, v -> {
            Intent i = new Intent(Intent.ACTION_SENDTO);
            i.setData(Uri.parse("mailto:" + CONTACT_EMAIL));
            i.putExtra(Intent.EXTRA_SUBJECT, "Basir AI feedback");
            try { startActivity(i); } catch (Exception e) {
                speak(t("تعذر فتح البريد.", "Could not open email."));
            }
        });

        addButton(t("📤 مشاركة بريد التواصل", "📤 Share contact email"), null, v -> {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, CONTACT_EMAIL);
            startActivity(Intent.createChooser(i,
                    t("مشاركة البريد", "Share email")));
        });

        addBackButton();
    }

    // ===================================================================
    // AI dialogs & image picker
    // ===================================================================

    private void showTextAiDialog(String task, String title, String instruction) {
        final EditText input = makeInput(t("الصق النص هنا", "Paste text here"), true);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(AiClient.isConfigured(prefs)
                        ? t("سيتم الإرسال إلى الوسيط.", "Will be sent to the proxy.")
                        : t("اضبط الوسيط أولًا للحصول على نتيجة حقيقية.",
                            "Configure the proxy first for a real result."))
                .setView(input)
                .setPositiveButton(t("تشغيل", "Run"), (d, w) -> {
                    if (AiClient.isConfigured(prefs)) {
                        callAi(task, input.getText().toString(), title, instruction);
                    } else {
                        showAiSettingsDialog();
                    }
                })
                .setNegativeButton(t("إلغاء", "Cancel"), null).show();
    }

    private void pickImageForAi(String task, String title, String instruction, String prompt) {
        if (!AiClient.isConfigured(prefs)) {
            speak(t("تحليل الصور يتطلب ضبط الوسيط.", "Image analysis requires proxy setup."));
            showAiSettingsDialog();
            return;
        }
        pendingTask = task;
        pendingTitle = title;
        pendingInstruction = instruction;
        pendingPrompt = prompt;
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(i,
                    t("اختر صورة", "Choose an image")), REQ_IMAGE_PICK);
        } catch (Exception e) {
            speak(t("تعذر فتح المنتقي.", "Could not open picker."));
        }
    }

    private void handlePickedImage(Uri uri) {
        String title = pendingTitle;
        resetScreen(title, t("جاري إرسال الصورة للوسيط...",
                             "Sending image to the proxy..."));
        final String serverUrl = prefs.getString("ai_server_url", "").trim();
        final String appToken = prefs.getString("ai_app_token", "").trim();
        aiExecutor.execute(() -> {
            try {
                String mime = AiClient.detectMime(MainActivity.this, uri);
                byte[] bytes = AiClient.readUriBytes(MainActivity.this, uri, 6 * 1024 * 1024);
                String b64 = AiClient.encodeBase64(bytes);
                String answer = AiClient.ask(serverUrl, appToken, pendingTask,
                        pendingPrompt, pendingInstruction, lang, b64, mime);
                log("ai_image_" + pendingTask, answer);
                runOnUiThread(() -> showResult(title, answer, true));
            } catch (Exception e) {
                final String msg = t("تعذر تحليل الصورة. ", "Image analysis failed. ") +
                        safeError(e.getMessage());
                log("ai_image_error", msg);
                runOnUiThread(() -> showResult(
                        t("خطأ تحليل الصورة", "Image analysis error"), msg, false));
            }
        });
    }

    private void callAi(String task, String input, String title, String instruction) {
        if (input == null || input.trim().isEmpty()) {
            speak(t("لم يتم إدخال نص.", "No text entered."));
            return;
        }
        if (!AiClient.isConfigured(prefs)) {
            showAiSettingsDialog();
            return;
        }
        final String serverUrl = prefs.getString("ai_server_url", "").trim();
        final String appToken = prefs.getString("ai_app_token", "").trim();
        resetScreen(title, t("جاري الاتصال بالوسيط...", "Connecting to the proxy..."));
        aiExecutor.execute(() -> {
            try {
                String answer = AiClient.ask(serverUrl, appToken, task, input, instruction, lang);
                log("ai_" + task, input + "\n→ " + answer);
                runOnUiThread(() -> showResult(title, answer, true));
            } catch (Exception e) {
                final String msg = t("تعذر الاتصال. ", "Could not connect. ") +
                        safeError(e.getMessage()) + "\n\n" +
                        t("تحقق من الرابط، الإنترنت، والرمز.",
                          "Check the URL, internet, and token.");
                log("ai_error", msg);
                runOnUiThread(() -> showResult(
                        t("خطأ الاتصال", "Connection error"), msg, false));
            }
        });
    }

    private String safeError(String s) {
        if (s == null || s.trim().isEmpty()) return t("خطأ غير معروف", "Unknown error");
        return s.length() > 280 ? s.substring(0, 280) + "..." : s;
    }

    // ===================================================================
    // Result screen
    // ===================================================================

    private void showResult(String title, String result, boolean saveable) {
        resetScreen(title, result);
        if (saveable && autoSaveResults) {
            db.insertDocument(title, "ai_result", result, summarize(result));
            log("doc_autosaved", title);
        }
        addButton(t("🔁 إعادة قراءة", "🔁 Read again"), null, v -> speak(result));
        if (saveable) {
            addButton(t("💾 حفظ في الأرشيف", "💾 Save to archive"), null, v -> {
                db.insertDocument(title, "ai_result", result, summarize(result));
                log("doc_saved", title);
                speak(t("تم الحفظ.", "Saved."));
            });
        }
        addButton(t("📤 مشاركة النتيجة", "📤 Share result"), null, v -> {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, result);
            startActivity(Intent.createChooser(i,
                    t("مشاركة", "Share")));
        });
        addBackButton();
    }

    private String summarize(String s) {
        if (s == null) return "";
        String x = s.replaceAll("\\s+", " ").trim();
        return x.length() <= 200 ? x : x.substring(0, 200) + "...";
    }

    // ===================================================================
    // Voice command
    // ===================================================================

    private void startVoiceCommand() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, isEnglish() ? "en-US" : "ar-SA");
        i.putExtra(RecognizerIntent.EXTRA_PROMPT,
                t("قل أمرًا: اقرأ، طوارئ، إعدادات.",
                  "Say a command: read, emergency, settings."));
        try { startActivityForResult(i, REQ_VOICE); }
        catch (Exception e) {
            speak(t("التعرف الصوتي غير متاح.",
                    "Speech recognition unavailable."));
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
        }
    }

    private void handleVoiceCommand(String cmd) {
        if (cmd == null) return;
        log("voice", cmd);
        String c = cmd.toLowerCase(Locale.ROOT);
        if (contains(c, "اقر", "نص", "read", "text", "document")) showTextReaderScreen();
        else if (contains(c, "طوارئ", "مساعدة", "emergency", "help")) showEmergencyScreen();
        else if (contains(c, "سجل", "نشاط", "history", "activity")) showHistoryScreen();
        else if (contains(c, "إعداد", "اعداد", "setting", "settings")) showSettingsScreen();
        else if (contains(c, "مختبر", "ذكاء", "lab")) showAiLabScreen();
        else if (contains(c, "أرشيف", "ارشيف", "archive")) showArchiveScreen();
        else if (contains(c, "ذاكرة", "memory", "person", "people")) showMemoryScreen();
        else if (contains(c, "ترجم", "translate")) showTranslateDialog();
        else if (contains(c, "مشي", "تنقل", "walk")) showWalkingScreen();
        else if (contains(c, "صور", "مشهد", "scene", "image", "photo")) showSceneScreen();
        else if (contains(c, "حول", "about", "contact")) showAboutScreen();
        else if (contains(c, "اسأل", "سؤال", "ask")) showAskDialog();
        else {
            speak(t("لم أتعرف على الأمر: ", "Did not recognize: ") + cmd);
        }
    }

    private boolean contains(String c, String... keys) {
        for (String k : keys) if (c.contains(k)) return true;
        return false;
    }

    // ===================================================================
    // Speech & vibration
    // ===================================================================

    private void speak(String s) {
        if (!speechEnabled || tts == null || !ttsReady || s == null || s.trim().isEmpty()) return;
        tts.speak(s, TextToSpeech.QUEUE_FLUSH, null, "basir_utterance");
    }

    private void vibrate(int ms) {
        Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (v == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            v.vibrate(ms);
        }
    }

    private void vibratePattern(long[] pattern) {
        Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (v == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            v.vibrate(VibrationEffect.createWaveform(pattern, -1));
        } else {
            v.vibrate(pattern, -1);
        }
    }

    private void log(String type, String content) {
        if (db != null) db.insertLog(type, content);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
