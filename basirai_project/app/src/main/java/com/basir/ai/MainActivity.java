package com.basir.ai;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
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
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {

    private static final int REQ_PERMISSIONS = 1001;
    private static final int REQ_VOICE = 1002;
    private static final int REQ_CAMERA = 1003;
    private static final int REQ_IMAGE_PICK = 1004;
    private static final String CONTACT_EMAIL = "ubdallahalrashdee@gmail.com";
    private static final String APP_VERSION = "0.4.1-build-audited";

    private TextToSpeech tts;
    private LinearLayout root;
    private BasirDb db;
    private SharedPreferences prefs;
    private boolean privacyMode = true;
    private String currentLanguage = "ar";
    private final ExecutorService aiExecutor = Executors.newSingleThreadExecutor();

    private String pendingImageTask = "image_analysis";
    private String pendingImageTitleAr = "تحليل صورة";
    private String pendingImageTitleEn = "Image analysis";
    private String pendingImageInstruction = "Analyze the image for a blind or low-vision user. Provide detailed alt text, visible text, practical warnings, and uncertainty.";
    private String pendingImagePrompt = "Describe this image clearly for a screen-reader user.";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new BasirDb(this);
        prefs = getSharedPreferences("basir_settings", MODE_PRIVATE);
        privacyMode = prefs.getBoolean("privacy_mode", true);
        currentLanguage = prefs.getString("language", "ar");
        tts = new TextToSpeech(this, this);
        requestCorePermissions();
        buildHomeScreen(t(
                "مرحبًا بك في بصير AI. هذه نسخة مراجعة نهائية تدعم وسيط GPT-5.5 الآمن، تحليل الصور، الأرشيف، الذاكرة، الطوارئ، والتبديل بين العربية والإنجليزية.",
                "Welcome to Basir AI. This final-review build supports a secure GPT-5.5 proxy, image analysis, archive, memory, emergency tools, and Arabic-English switching."
        ));
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            applyTtsLanguage();
            tts.setSpeechRate(0.95f);
        }
    }

    private void applyTtsLanguage() {
        if (tts == null) return;
        Locale locale = isEnglish() ? Locale.US : new Locale("ar", "SA");
        int result = tts.setLanguage(locale);
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            toast(t("لغة النطق غير مدعومة بالكامل على هذا الجهاز.", "This TTS language is not fully supported on this device."));
        }
    }

    private boolean isEnglish() {
        return "en".equals(currentLanguage);
    }

    private String t(String ar, String en) {
        return isEnglish() ? en : ar;
    }

    private void requestCorePermissions() {
        if (Build.VERSION.SDK_INT >= 23) {
            List<String> needed = new ArrayList<>();
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.CAMERA);
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.RECORD_AUDIO);
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
            if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.ACCESS_COARSE_LOCATION);
            if (!needed.isEmpty()) requestPermissions(needed.toArray(new String[0]), REQ_PERMISSIONS);
        }
    }

    private void buildBase(String title, String spokenIntro) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setContentDescription(t("شاشة بصير AI. اسحب للتنقل بين العناصر.", "Basir AI screen. Swipe to move between items."));

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(24));
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(root);
        setContentView(scroll);

        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextSize(26);
        heading.setGravity(Gravity.CENTER);
        heading.setContentDescription(t("العنوان: ", "Title: ") + title);
        root.addView(heading, fullWidth());

        TextView status = new TextView(this);
        String statusText = buildStatusText();
        status.setText(statusText);
        status.setTextSize(16);
        status.setPadding(0, dp(10), 0, dp(14));
        status.setContentDescription(t("حالة التطبيق: ", "App status: ") + statusText);
        root.addView(status, fullWidth());

        if (spokenIntro != null && !spokenIntro.trim().isEmpty()) {
            addParagraph(spokenIntro);
            speak(spokenIntro);
        }
    }

    private String buildStatusText() {
        String privacy = privacyMode
                ? t("الخصوصية مفعّلة؛ لا يتم حفظ الصور تلقائيًا", "Privacy is on; images are not saved automatically")
                : t("الخصوصية غير مفعّلة", "Privacy is off");
        String lang = isEnglish() ? "English" : "العربية";
        String talkback = isTalkBackLikelyEnabled()
                ? t("قارئ الشاشة مفعّل غالبًا", "Screen reader is probably enabled")
                : t("قارئ الشاشة غير مكتشف", "Screen reader not detected");
        String ai = isAiConfigured()
                ? t("وسيط GPT-5.5 مهيأ", "GPT-5.5 proxy configured")
                : t("وسيط GPT-5.5 غير مهيأ", "GPT-5.5 proxy not configured");
        return t("اللغة: ", "Language: ") + lang + ". " + privacy + ". " + talkback + ". " + ai + ".";
    }

    private boolean isTalkBackLikelyEnabled() {
        AccessibilityManager am = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        return am != null && am.isEnabled() && am.isTouchExplorationEnabled();
    }

    private void buildHomeScreen(String intro) {
        buildBase(t("بصير AI", "Basir AI"), intro);
        addMainButton(t("وصف ما أمامي", "Describe what is in front of me"),
                t("زر وصف ما أمامي. يفتح وصف المشهد وتحليل الصور.", "Describe button. Opens scene description and image analysis."), v -> openSceneScreen());
        addMainButton(t("قراءة نص وتحليل مستند", "Read text and analyze document"),
                t("زر قراءة النصوص والمستندات.", "Text and document reader button."), v -> openTextReaderScreen());
        addMainButton(t("اسأل بصير GPT-5.5", "Ask Basir GPT-5.5"),
                t("يفتح مربع سؤال يرسل إلى الوسيط الآمن.", "Opens a question box that uses the secure proxy."), v -> showAskBasirDialog());
        addMainButton(t("مختبر الذكاء", "AI Lab"),
                t("أدوات تحليل الصورة، الوصف البديل، لقطة الشاشة، الدراسة، القانون، الصحة، والردود.", "Image, alt text, screenshot, study, legal, health, and reply tools."), v -> openIntelligenceLabScreen());
        addMainButton(t("الأرشيف والذاكرة", "Archive and memory"),
                t("يفتح الأرشيف والذاكرة الشخصية المحلية.", "Opens local archive and personal memory."), v -> openArchiveAndMemoryScreen());
        addMainButton(t("مساعدة في المشي", "Walking assistant"),
                t("يعرض أوامر مشي قصيرة واهتزازات مساعدة، ولا يستبدل العصا البيضاء.", "Shows short walking alerts and vibrations; it does not replace a cane."), v -> openWalkingAssistantScreen());
        addMainButton(t("وضع الطوارئ", "Emergency mode"),
                t("يفتح أدوات الاستغاثة ومشاركة الموقع التقريبي.", "Opens emergency tools and approximate location sharing."), v -> openEmergencyScreen());
        addMainButton(t("سجل آخر العمليات", "Recent activity"),
                t("يعرض آخر العمليات النصية المحفوظة محليًا.", "Shows recent locally saved text activity."), v -> openHistoryScreen());
        addMainButton(t("الإعدادات", "Settings"),
                t("تغيير اللغة، الخصوصية، جهة الطوارئ، وسيط GPT-5.5.", "Change language, privacy, emergency contact, and GPT-5.5 proxy."), v -> openSettingsScreen());
        addMainButton(t("حول التطبيق والتواصل", "About and contact"),
                t("يعرض معلومات التطبيق وبريد التواصل.", "Shows app information and contact email."), v -> openAboutScreen());

        Button voice = makeButton(t("أمر صوتي", "Voice command"));
        voice.setContentDescription(t("زر أمر صوتي. قل مثلًا: اقرأ نص، طوارئ، سجل، إعدادات.", "Voice command button. Say: read text, emergency, history, settings."));
        voice.setOnClickListener(v -> startVoiceCommand());
        root.addView(voice, fullWidthWithMargins());
    }

    private void addMainButton(String text, String contentDescription, View.OnClickListener listener) {
        Button button = makeButton(text);
        button.setContentDescription(contentDescription);
        button.setOnClickListener(listener);
        root.addView(button, fullWidthWithMargins());
    }

    private Button makeButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(20);
        button.setAllCaps(false);
        button.setPadding(dp(10), dp(12), dp(10), dp(12));
        return button;
    }

    private void openSceneScreen() {
        saveLog("scene", t("فتح وصف المشهد", "Opened scene description"));
        buildBase(t("وصف المشهد", "Scene description"), t(
                "اختر صورة من المعرض لتحليلها عبر GPT-5.5، أو اكتب وصفًا نصيًا للمشهد. هذا لا يستبدل العصا أو الانتباه أثناء المشي.",
                "Choose an image from the gallery for GPT-5.5 analysis, or type a scene description. This does not replace a cane or walking attention."
        ));
        addParagraph(t(
                "الناتج المطلوب: ملخص سريع، الأشخاص دون تحديد هوية، العوائق، النصوص الظاهرة، الاتجاهات، مستوى الخطر، وما يمكن فعله الآن.",
                "Expected output: quick summary, people without identification, obstacles, visible text, directions, risk level, and what to do next."
        ));
        addActionButton(t("تحليل صورة من المعرض", "Analyze image from gallery"),
                t("يرسل صورة يختارها المستخدم إلى الوسيط الآمن للتحليل.", "Sends a user-selected image to the secure proxy for analysis."),
                v -> chooseImageForAi("scene_image", t("تحليل المشهد من صورة", "Scene image analysis"), "Analyze this image as a scene for a blind user. Start with concise alt text, then people without identity, obstacles, visible text, directions, risk level, and safe next steps.", "Describe this scene for a blind user."));
        addActionButton(t("تحليل مشهد نصيًا", "Analyze written scene"),
                t("اكتب وصفًا للمشهد ليحوله GPT-5.5 إلى تعليمات عملية.", "Type a scene description and GPT-5.5 turns it into practical guidance."), v -> showSceneAiDialog());
        addActionButton(t("فتح الكاميرا التجريبية", "Open test camera"),
                t("يفتح تطبيق الكاميرا فقط؛ التحليل المباشر سيضاف لاحقًا.", "Opens the camera app only; live analysis will be added later."), v -> openCameraIntent());
        addBackButton();
    }

    private void openCameraIntent() {
        Intent intent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) != null) startActivityForResult(intent, REQ_CAMERA);
        else speak(t("لا يوجد تطبيق كاميرا متاح.", "No camera app is available."));
    }

    private void openTextReaderScreen() {
        saveLog("text_reader", t("فتح قارئ النصوص", "Opened text reader"));
        buildBase(t("قراءة النصوص والمستندات", "Text and document reader"), t(
                "الصق نص فاتورة أو عقد أو تقرير أو رسالة. عند ضبط الوسيط سيحللها GPT-5.5 فعليًا.",
                "Paste an invoice, contract, report, or message. Once the proxy is configured, GPT-5.5 will analyze it."
        ));
        addActionButton(t("إدخال نص للتحليل", "Enter text for analysis"),
                t("يفتح حقل نص لتحليل المستند.", "Opens a text field for document analysis."), v -> showDocumentAnalysisDialog());
        addActionButton(t("مثال فاتورة", "Invoice example"),
                t("يعرض مثال تحليل فاتورة.", "Shows an invoice analysis example."),
                v -> analyzeAndSpeak(t("فاتورة كهرباء. المبلغ المستحق 346 ريال. آخر موعد للسداد 25 مايو. يوجد ارتفاع عن الشهر السابق بنسبة 18%.", "Electricity bill. Amount due is 346 SAR. Payment deadline is May 25. Usage is 18% higher than last month.")));
        addBackButton();
    }

    private void showDocumentAnalysisDialog() {
        final EditText input = makeMultilineInput(t("الصق النص هنا للتحليل", "Paste text here for analysis"));
        new AlertDialog.Builder(this)
                .setTitle(t("تحليل مستند", "Analyze document"))
                .setMessage(t("الصق النص. إن كان الوسيط مضبوطًا سيعمل GPT-5.5؛ وإلا سيعمل التحليل المحلي المبدئي.", "Paste the text. If the proxy is configured, GPT-5.5 will be used; otherwise local basic analysis runs."))
                .setView(input)
                .setPositiveButton(t("حلل", "Analyze"), (dialog, which) -> analyzeAndSpeak(input.getText().toString()))
                .setNegativeButton(t("إلغاء", "Cancel"), null)
                .show();
    }

    private void analyzeAndSpeak(String text) {
        if (text == null || text.trim().isEmpty()) {
            speak(t("لم يتم إدخال نص.", "No text was entered."));
            return;
        }
        if (isAiConfigured()) {
            callCloudAi("document_analysis", text, t("نتيجة تحليل المستند", "Document analysis result"), "Analyze this document for a blind user. Extract type, concise summary, dates, amounts, parties, warnings, and practical next steps. For health or legal text, include safety limits.");
        } else {
            String result = LocalAnalyzer.analyzeDocument(text, isEnglish());
            saveLog("document_analysis", result);
            openResultScreen(t("نتيجة تحليل المستند", "Document analysis result"), result);
        }
    }

    private void showAskBasirDialog() {
        final EditText input = makeMultilineInput(t("اكتب سؤالك هنا", "Type your question here"));
        new AlertDialog.Builder(this)
                .setTitle(t("اسأل بصير GPT-5.5", "Ask Basir GPT-5.5"))
                .setMessage(isAiConfigured()
                        ? t("سيتم إرسال السؤال إلى الوسيط الآمن.", "The question will be sent to the secure proxy.")
                        : t("وسيط GPT-5.5 غير مضبوط. ستظهر إجابة محلية محدودة.", "GPT-5.5 proxy is not configured. A limited local answer will be shown."))
                .setView(input)
                .setPositiveButton(t("اسأل", "Ask"), (dialog, which) -> {
                    String q = input.getText().toString().trim();
                    if (isAiConfigured()) {
                        callCloudAi("ask", q, t("إجابة بصير GPT-5.5", "Basir GPT-5.5 answer"), "Answer as Basir AI for blind and low-vision users. Be practical, structured, concise when possible, and screen-reader friendly.");
                    } else {
                        String answer = LocalAnalyzer.answer(q, isEnglish());
                        saveLog("ask_basir", q + "\n" + answer);
                        openResultScreen(t("إجابة بصير", "Basir answer"), answer);
                    }
                })
                .setNegativeButton(t("إلغاء", "Cancel"), null)
                .show();
    }

    private void showSceneAiDialog() {
        final EditText input = makeMultilineInput(t("مثال: أنا في ممر ضيق، يوجد باب في النهاية وصندوق على الأرض", "Example: I am in a narrow hallway, there is a door at the end and a box on the floor"));
        new AlertDialog.Builder(this)
                .setTitle(t("تحليل مشهد نصيًا", "Analyze written scene"))
                .setMessage(isAiConfigured()
                        ? t("سيتم إرسال الوصف إلى GPT-5.5 عبر الوسيط.", "The description will be sent to GPT-5.5 through the proxy.")
                        : t("اضبط وسيط GPT-5.5 أولًا للحصول على تحليل حقيقي.", "Configure the GPT-5.5 proxy first for real analysis."))
                .setView(input)
                .setPositiveButton(t("حلل", "Analyze"), (dialog, which) -> {
                    if (isAiConfigured()) {
                        callCloudAi("scene_text", input.getText().toString(), t("تحليل المشهد", "Scene analysis"), "Convert the written scene into practical guidance for a blind user. Include quick summary, obstacles, directions, risk level, and what to do next. Do not claim live camera vision.");
                    } else {
                        showAiSettingsDialog();
                    }
                })
                .setNegativeButton(t("إلغاء", "Cancel"), null)
                .show();
    }

    private void openIntelligenceLabScreen() {
        buildBase(t("مختبر الذكاء", "AI Lab"), t(
                "أدوات متقدمة تعتمد على GPT-5.5 عند ضبط الوسيط. الصور لا تحفظ تلقائيًا داخل التطبيق.",
                "Advanced GPT-5.5 tools when the proxy is configured. Images are not saved automatically in the app."
        ));
        addActionButton(t("تحليل صورة حقيقي", "Real image analysis"), t("اختر صورة لتحليلها.", "Choose an image to analyze."),
                v -> chooseImageForAi("image_analysis", t("تحليل صورة", "Image analysis"), "Analyze the image for a blind user. Start with detailed alt text, then visible text, important objects, spatial layout, risks, and next steps. Do not identify faces.", "Describe this image for a blind user."));
        addActionButton(t("توليد وصف بديل للصورة", "Generate image alt text"), t("ينشئ وصفًا نصيًا دقيقًا للصورة.", "Creates detailed alt text for the image."),
                v -> chooseImageForAi("alt_text", t("الوصف البديل للصورة", "Image alt text"), "Write precise, structured alt text for a blind user. Include important objects, spatial relationships, colors, visible text, and practical relevance.", "Write detailed alt text for this image."));
        addActionButton(t("شرح لقطة شاشة", "Explain screenshot"), t("يشرح عناصر لقطة الشاشة وخطوتها التالية.", "Explains screenshot elements and next step."),
                v -> chooseImageForAi("screenshot", t("شرح لقطة الشاشة", "Screenshot explanation"), "Explain the screenshot for a screen-reader user: page, buttons, messages, errors, and the next useful step.", "Explain this screenshot."));
        addActionButton(t("تحويل نص إلى بطاقات مذاكرة", "Turn text into study cards"), t("يفتح حقل نص وينتج سؤال وجواب.", "Opens a text field and produces Q&A cards."),
                v -> showAiTextToolDialog("study_cards", t("بطاقات مذاكرة", "Study cards"), "Turn the text into direct question-and-answer study cards suitable for audio review."));
        addActionButton(t("تحليل قانوني آمن", "Safe legal analysis"), t("تحليل تعليمي لا يغني عن مختص.", "Educational analysis, not a substitute for a professional."),
                v -> showAiTextToolDialog("legal", t("تحليل قانوني", "Legal analysis"), "Analyze the legal text educationally. Extract parties, obligations, dates, risks, and review points. Do not present final legal representation."));
        addActionButton(t("تحليل صحي آمن", "Safe health analysis"), t("شرح معلومات صحية دون تشخيص.", "Explains health information without diagnosis."),
                v -> showAiTextToolDialog("health", t("تحليل صحي آمن", "Safe health analysis"), "Explain the health text safely. Extract medication names, dosage if present, and warnings. Do not diagnose or change doses; advise checking with a doctor or pharmacist."));
        addActionButton(t("جهز رد مناسب", "Draft a suitable reply"), t("يشرح النبرة ويقترح ردًا مهذبًا.", "Explains tone and suggests a polite reply."),
                v -> showAiTextToolDialog("reply", t("رد مناسب", "Suitable reply"), "Explain the message tone and suggest a polite, context-aware reply. Provide Arabic and English when useful."));
        addBackButton();
    }

    private void showAiTextToolDialog(String task, String title, String instruction) {
        final EditText input = makeMultilineInput(t("الصق النص هنا", "Paste text here"));
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(isAiConfigured()
                        ? t("سيتم إرسال النص إلى وسيط GPT-5.5.", "The text will be sent to the GPT-5.5 proxy.")
                        : t("اضبط وسيط GPT-5.5 أولًا للحصول على نتيجة حقيقية.", "Configure the GPT-5.5 proxy first for real output."))
                .setView(input)
                .setPositiveButton(t("تشغيل", "Run"), (dialog, which) -> {
                    if (isAiConfigured()) callCloudAi(task, input.getText().toString(), title, instruction);
                    else showAiSettingsDialog();
                })
                .setNegativeButton(t("إلغاء", "Cancel"), null)
                .show();
    }

    private void chooseImageForAi(String task, String title, String instruction, String prompt) {
        if (!isAiConfigured()) {
            speak(t("تحليل الصور يحتاج ضبط وسيط GPT-5.5 أولًا.", "Image analysis requires GPT-5.5 proxy setup first."));
            showAiSettingsDialog();
            return;
        }
        pendingImageTask = task;
        pendingImageTitleAr = title;
        pendingImageTitleEn = title;
        pendingImageInstruction = instruction;
        pendingImagePrompt = prompt;
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, t("اختر صورة لتحليلها في بصير", "Choose an image for Basir analysis")), REQ_IMAGE_PICK);
        } catch (Exception e) {
            speak(t("تعذر فتح منتقي الصور على هذا الجهاز.", "Could not open the image picker on this device."));
        }
    }

    private void handlePickedImage(Uri uri) {
        String serverUrl = prefs.getString("ai_server_url", "").trim();
        String appToken = prefs.getString("ai_app_token", "").trim();
        String title = isEnglish() ? pendingImageTitleEn : pendingImageTitleAr;
        buildBase(title, t("جاري تجهيز الصورة وإرسالها إلى الوسيط. لا يتم حفظ الصورة تلقائيًا.", "Preparing the image and sending it to the proxy. The image is not saved automatically."));
        aiExecutor.execute(() -> {
            try {
                String mime = getContentResolver().getType(uri);
                if (mime == null || mime.trim().isEmpty()) mime = "image/jpeg";
                byte[] bytes = readUriBytes(uri, 7 * 1024 * 1024);
                String base64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
                String answer = AiClient.ask(serverUrl, appToken, pendingImageTask, pendingImagePrompt, pendingImageInstruction, currentLanguage, base64, mime);
                saveLog("cloud_image_" + pendingImageTask, answer);
                runOnUiThread(() -> openResultScreen(title, answer));
            } catch (Exception e) {
                String msg = t("تعذر تحليل الصورة. السبب: ", "Could not analyze the image. Reason: ") + safeError(e.getMessage()) + "\n\n" + t("تأكد أن الصورة ليست كبيرة جدًا وأن الوسيط يدعم الصور.", "Make sure the image is not too large and the proxy supports images.");
                saveLog("cloud_image_error", msg);
                runOnUiThread(() -> openResultScreen(t("خطأ تحليل الصورة", "Image analysis error"), msg));
            }
        });
    }

    private byte[] readUriBytes(Uri uri, int maxBytes) throws Exception {
        try (InputStream input = getContentResolver().openInputStream(uri); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (input == null) throw new Exception(t("تعذر قراءة الصورة.", "Could not read the image."));
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) throw new Exception(t("حجم الصورة أكبر من الحد المسموح في هذه النسخة.", "The image is larger than the allowed limit in this version."));
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    private boolean isAiConfigured() {
        String url = prefs.getString("ai_server_url", "").trim();
        return url.startsWith("https://") || url.startsWith("http://");
    }

    private void showAiSettingsDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(12), dp(12), dp(12));

        TextView info = new TextView(this);
        info.setText(t("أدخل رابط الوسيط الآمن. لا تضع مفتاح OpenAI داخل التطبيق. للإنتاج استخدم HTTPS.", "Enter the secure proxy URL. Do not put the OpenAI key inside the app. Use HTTPS in production."));
        info.setTextSize(16);
        info.setContentDescription(info.getText());
        box.addView(info, fullWidth());

        final EditText urlInput = makeSingleLineInput(t("رابط الوسيط مثل https://your-server.com/api/basir", "Proxy URL such as https://your-server.com/api/basir"));
        urlInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        urlInput.setText(prefs.getString("ai_server_url", ""));
        box.addView(urlInput, fullWidth());

        final EditText tokenInput = makeSingleLineInput(t("رمز التطبيق الاختياري", "Optional app token"));
        tokenInput.setText(prefs.getString("ai_app_token", ""));
        box.addView(tokenInput, fullWidth());

        new AlertDialog.Builder(this)
                .setTitle(t("إعداد وسيط GPT-5.5", "GPT-5.5 proxy settings"))
                .setView(box)
                .setPositiveButton(t("حفظ", "Save"), (dialog, which) -> {
                    prefs.edit()
                            .putString("ai_server_url", urlInput.getText().toString().trim())
                            .putString("ai_app_token", tokenInput.getText().toString().trim())
                            .apply();
                    speak(isAiConfigured() ? t("تم حفظ الوسيط.", "Proxy saved.") : t("تم الحفظ، لكن الرابط غير مهيأ بعد.", "Saved, but the URL is not configured yet."));
                    openSettingsScreen();
                })
                .setNeutralButton(t("اختبار", "Test"), (dialog, which) -> {
                    prefs.edit()
                            .putString("ai_server_url", urlInput.getText().toString().trim())
                            .putString("ai_app_token", tokenInput.getText().toString().trim())
                            .apply();
                    callCloudAi("health", t("اختبار الاتصال من تطبيق بصير AI", "Connection test from Basir AI Android app"), t("اختبار GPT-5.5", "GPT-5.5 test"), "Return one short sentence confirming the proxy is working for Basir AI.");
                })
                .setNegativeButton(t("إلغاء", "Cancel"), null)
                .show();
    }

    private void callCloudAi(String task, String input, String title, String instruction) {
        if (input == null || input.trim().isEmpty()) {
            speak(t("لم يتم إدخال نص كافٍ.", "Not enough text was entered."));
            return;
        }
        String serverUrl = prefs.getString("ai_server_url", "").trim();
        String appToken = prefs.getString("ai_app_token", "").trim();
        if (!isAiConfigured()) {
            speak(t("وسيط GPT-5.5 غير مهيأ.", "GPT-5.5 proxy is not configured."));
            showAiSettingsDialog();
            return;
        }
        buildBase(title, t("جاري الاتصال بوسيط GPT-5.5. ستظهر النتيجة عند اكتمال الطلب.", "Connecting to the GPT-5.5 proxy. The result will appear when the request finishes."));
        addParagraph(t("المهمة: ", "Task: ") + task + "\n" + t("لا يوجد مفتاح OpenAI داخل APK.", "There is no OpenAI key inside the APK."));
        aiExecutor.execute(() -> {
            try {
                String answer = AiClient.ask(serverUrl, appToken, task, input, instruction, currentLanguage);
                saveLog("cloud_ai_" + task, input + "\n" + answer);
                runOnUiThread(() -> openResultScreen(title, answer));
            } catch (Exception e) {
                String msg = t("تعذر الاتصال بوسيط GPT-5.5. السبب: ", "Could not connect to the GPT-5.5 proxy. Reason: ") + safeError(e.getMessage()) + "\n\n" +
                        t("تحقق من الرابط، الإنترنت، رمز التطبيق، وأن الخادم يحتوي على OPENAI_API_KEY.", "Check the URL, internet connection, app token, and that the server has OPENAI_API_KEY.");
                saveLog("cloud_ai_error", msg);
                runOnUiThread(() -> openResultScreen(t("خطأ الاتصال", "Connection error"), msg));
            }
        });
    }

    private String safeError(String raw) {
        if (raw == null || raw.trim().isEmpty()) return t("خطأ غير معروف", "Unknown error");
        return raw.length() > 260 ? raw.substring(0, 260) + "..." : raw;
    }

    private void openWalkingAssistantScreen() {
        buildBase(t("مساعدة في المشي", "Walking assistant"), t(
                "تنبيه مهم: هذه أوامر مساعدة فقط ولا تستبدل العصا البيضاء أو المرشد أو الانتباه للطريق.",
                "Important: these are assistive prompts only and do not replace a cane, guide, or road awareness."
        ));
        addActionButton(t("توقف", "Stop"), t("اهتزاز طويل وتنبيه توقف.", "Long vibration and stop alert."), v -> alertCommand(t("توقف", "Stop"), 650));
        addActionButton(t("يمين قليلًا", "Slightly right"), t("اهتزازان لاتجاه اليمين.", "Two vibrations for right direction."), v -> patternCommand(t("يمين قليلًا", "Slightly right"), new long[]{0, 120, 90, 120}));
        addActionButton(t("يسار قليلًا", "Slightly left"), t("ثلاث اهتزازات لاتجاه اليسار.", "Three vibrations for left direction."), v -> patternCommand(t("يسار قليلًا", "Slightly left"), new long[]{0, 100, 80, 100, 80, 100}));
        addActionButton(t("عائق منخفض", "Low obstacle"), t("اهتزاز قصير لعائق منخفض.", "Short vibration for a low obstacle."), v -> alertCommand(t("عائق منخفض", "Low obstacle"), 180));
        addBackButton();
    }

    private void alertCommand(String message, int vibrationMs) {
        saveLog("walk_alert", message);
        vibrate(vibrationMs);
        speak(message);
    }

    private void patternCommand(String message, long[] pattern) {
        saveLog("walk_alert", message);
        vibratePattern(pattern);
        speak(message);
    }

    private void openEmergencyScreen() {
        buildBase(t("وضع الطوارئ", "Emergency mode"), t(
                "زر الطوارئ يجهز رسالة مساعدة بموقعك التقريبي إن توفر. لا يعتمد عليه وحده في الحالات الخطرة.",
                "Emergency mode prepares a help message with your approximate location if available. Do not rely on it alone in dangerous situations."
        ));
        String contact = prefs.getString("emergency_contact", "");
        addParagraph(contact.isEmpty() ? t("لم يتم حفظ جهة طوارئ بعد.", "No emergency contact is saved yet.") : t("جهة الطوارئ: ", "Emergency contact: ") + contact);
        addActionButton(t("إرسال رسالة استغاثة", "Send help message"), t("يفتح تطبيق الرسائل برسالة جاهزة.", "Opens the messaging app with a prepared message."), v -> sendEmergencySms());
        addActionButton(t("تشغيل صوت تحديد المكان", "Play locator voice"), t("ينطق أنا هنا ثلاث مرات مع اهتزاز.", "Says I am here three times with vibration."), v -> {
            saveLog("emergency_sound", "locator");
            for (int i = 0; i < 3; i++) speak(t("أنا هنا. أحتاج مساعدة.", "I am here. I need help."));
            vibrate(1000);
        });
        addActionButton(t("حفظ أو تغيير جهة الطوارئ", "Save or change emergency contact"), t("يفتح حقل رقم جهة الطوارئ.", "Opens emergency contact number field."), v -> showEmergencyContactDialog());
        addBackButton();
    }

    private void sendEmergencySms() {
        String contact = prefs.getString("emergency_contact", "");
        if (contact.isEmpty()) {
            speak(t("يرجى حفظ جهة الطوارئ أولًا.", "Please save an emergency contact first."));
            showEmergencyContactDialog();
            return;
        }
        String message = t("أنا بحاجة إلى مساعدة. هذا موقعي التقريبي إن توفر: ", "I need help. My approximate location if available: ") + getLastKnownLocationText();
        saveLog("emergency", message);
        Intent smsIntent = new Intent(Intent.ACTION_SENDTO);
        smsIntent.setData(Uri.parse("smsto:" + contact.replace(" ", "")));
        smsIntent.putExtra("sms_body", message);
        try { startActivity(smsIntent); } catch (Exception e) { speak(t("تعذر فتح تطبيق الرسائل.", "Could not open the messaging app.")); }
    }

    private String getLastKnownLocationText() {
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return t("إذن الموقع غير ممنوح", "Location permission not granted");
        }
        try {
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (lm == null) return t("الموقع غير متاح", "Location unavailable");
            Location loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (loc == null) loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (loc == null) return t("لا يوجد آخر موقع معروف", "No last known location");
            return "https://maps.google.com/?q=" + loc.getLatitude() + "," + loc.getLongitude();
        } catch (Exception e) {
            return t("تعذر جلب الموقع", "Could not get location");
        }
    }

    private void showEmergencyContactDialog() {
        final EditText input = makeSingleLineInput(t("مثال: +9665XXXXXXXX", "Example: +9665XXXXXXXX"));
        input.setInputType(InputType.TYPE_CLASS_PHONE);
        input.setText(prefs.getString("emergency_contact", ""));
        new AlertDialog.Builder(this)
                .setTitle(t("جهة الطوارئ", "Emergency contact"))
                .setMessage(t("أدخل رقم شخص موثوق.", "Enter a trusted person's number."))
                .setView(input)
                .setPositiveButton(t("حفظ", "Save"), (dialog, which) -> {
                    prefs.edit().putString("emergency_contact", input.getText().toString().trim()).apply();
                    speak(t("تم حفظ جهة الطوارئ.", "Emergency contact saved."));
                    openEmergencyScreen();
                })
                .setNegativeButton(t("إلغاء", "Cancel"), null)
                .show();
    }

    private void openArchiveAndMemoryScreen() {
        buildBase(t("الأرشيف والذاكرة", "Archive and memory"), t(
                "هذه البيانات محفوظة محليًا على الجهاز ولا تتم مزامنتها تلقائيًا.",
                "This data is saved locally on the device and is not synced automatically."
        ));
        addActionButton(t("عرض أرشيف المستندات", "Show document archive"), t("يعرض آخر النتائج المحفوظة.", "Shows recent saved results."), v -> openDocumentArchiveScreen());
        addActionButton(t("إضافة شخص", "Add person"), t("يحفظ شخصًا في الذاكرة المحلية.", "Saves a person in local memory."), v -> showPersonDialog());
        addActionButton(t("إضافة منتج أو دواء", "Add product or medicine"), t("يحفظ منتجًا أو دواءً.", "Saves a product or medicine."), v -> showProductMemoryDialog());
        addActionButton(t("إضافة مكان", "Add place"), t("يحفظ مكانًا وملاحظات الوصول.", "Saves a place and access notes."), v -> showPlaceDialog());
        addActionButton(t("عرض الذاكرة الشخصية", "Show personal memory"), t("يعرض الأشخاص والمنتجات والأماكن.", "Shows people, products, and places."), v -> openMemoryListScreen());
        addBackButton();
    }

    private void openDocumentArchiveScreen() {
        buildBase(t("أرشيف المستندات", "Document archive"), t("آخر النتائج والمستندات المحفوظة محليًا.", "Recent locally saved results and documents."));
        List<String> docs = db.getRecentDocuments(25);
        if (docs.isEmpty()) addParagraph(t("لا توجد مستندات محفوظة بعد.", "No saved documents yet."));
        else {
            StringBuilder all = new StringBuilder();
            int i = 1;
            for (String doc : docs) {
                String item = i + ". " + doc;
                addParagraph(item);
                all.append(item).append("\n");
                i++;
            }
            addActionButton(t("قراءة الأرشيف", "Read archive"), t("يقرأ قائمة الأرشيف صوتيًا.", "Reads the archive list aloud."), v -> speak(all.toString()));
        }
        addBackButton();
    }

    private void showPersonDialog() {
        showThreeFieldDialog(t("إضافة شخص", "Add person"), t("الاسم", "Name"), t("العلاقة", "Relationship"), t("ملاحظات", "Notes"), (a, b, c) -> {
            db.insertPerson(a, b, c);
            speak(t("تم حفظ الشخص في الذاكرة.", "Person saved in memory."));
            openArchiveAndMemoryScreen();
        });
    }

    private void showProductMemoryDialog() {
        showThreeFieldDialog(t("إضافة منتج أو دواء", "Add product or medicine"), t("اسم المنتج", "Product name"), t("الباركود إن وجد", "Barcode if available"), t("ملاحظات", "Notes"), (a, b, c) -> {
            db.insertProduct(a, b, c);
            speak(t("تم حفظ المنتج في الذاكرة.", "Product saved in memory."));
            openArchiveAndMemoryScreen();
        });
    }

    private void showPlaceDialog() {
        showThreeFieldDialog(t("إضافة مكان", "Add place"), t("اسم المكان", "Place name"), t("وصف مختصر", "Short description"), t("ملاحظات الوصول أو العوائق", "Access notes or obstacles"), (a, b, c) -> {
            db.insertPlace(a, c + "\n" + t("وصف: ", "Description: ") + b);
            speak(t("تم حفظ المكان في الذاكرة.", "Place saved in memory."));
            openArchiveAndMemoryScreen();
        });
    }

    private interface ThreeFieldAction { void run(String first, String second, String third); }

    private void showThreeFieldDialog(String title, String h1, String h2, String h3, ThreeFieldAction action) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(8), dp(12), dp(8));
        EditText first = makeSingleLineInput(h1);
        EditText second = makeSingleLineInput(h2);
        EditText third = makeMultilineInput(h3);
        box.addView(first, fullWidth());
        box.addView(second, fullWidth());
        box.addView(third, fullWidth());
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(box)
                .setPositiveButton(t("حفظ", "Save"), (dialog, which) -> action.run(first.getText().toString().trim(), second.getText().toString().trim(), third.getText().toString().trim()))
                .setNegativeButton(t("إلغاء", "Cancel"), null)
                .show();
    }

    private void openMemoryListScreen() {
        buildBase(t("الذاكرة الشخصية", "Personal memory"), t("الأشخاص والمنتجات والأماكن المحفوظة محليًا.", "Locally saved people, products, and places."));
        String summary = db.getMemorySummary(isEnglish());
        addParagraph(summary.isEmpty() ? t("لا توجد ذاكرة محفوظة بعد.", "No saved memory yet.") : summary);
        addActionButton(t("قراءة الذاكرة", "Read memory"), t("يقرأ الذاكرة الشخصية صوتيًا.", "Reads personal memory aloud."), v -> speak(summary.isEmpty() ? t("لا توجد ذاكرة محفوظة بعد.", "No saved memory yet.") : summary));
        addBackButton();
    }

    private void openHistoryScreen() {
        buildBase(t("سجل آخر العمليات", "Recent activity"), t("السجل يحفظ النصوص فقط. لا يتم حفظ الصور تلقائيًا.", "The log stores text only. Images are not saved automatically."));
        List<String> logs = db.getRecentLogs(30);
        if (logs.isEmpty()) addParagraph(t("لا يوجد سجل بعد.", "No activity yet."));
        else {
            StringBuilder all = new StringBuilder();
            int i = 1;
            for (String log : logs) {
                String item = i + ". " + log;
                addParagraph(item);
                all.append(item).append("\n");
                i++;
            }
            addActionButton(t("قراءة السجل صوتيًا", "Read activity aloud"), t("يقرأ آخر العمليات صوتيًا.", "Reads recent activity aloud."), v -> speak(all.toString()));
        }
        addActionButton(t("مسح السجل", "Clear activity"), t("يحذف سجل العمليات المحلي.", "Deletes the local activity log."), v -> {
            db.clearLogs();
            speak(t("تم مسح السجل.", "Activity cleared."));
            openHistoryScreen();
        });
        addBackButton();
    }

    private void openSettingsScreen() {
        buildBase(t("الإعدادات", "Settings"), t("إعدادات اللغة والخصوصية والطوارئ والوسيط.", "Language, privacy, emergency, and proxy settings."));
        addParagraph(buildStatusText());
        addActionButton(privacyMode ? t("إيقاف وضع الخصوصية", "Turn privacy mode off") : t("تشغيل وضع الخصوصية", "Turn privacy mode on"),
                t("يبدل وضع الخصوصية المحلي.", "Toggles local privacy mode."), v -> {
                    privacyMode = !privacyMode;
                    prefs.edit().putBoolean("privacy_mode", privacyMode).apply();
                    speak(privacyMode ? t("تم تشغيل وضع الخصوصية.", "Privacy mode is on.") : t("تم إيقاف وضع الخصوصية.", "Privacy mode is off."));
                    openSettingsScreen();
                });
        addActionButton(isEnglish() ? "Switch to Arabic / التبديل إلى العربية" : "Switch to English / التبديل إلى الإنجليزية",
                t("يغير لغة الواجهة والنطق الأساسي.", "Changes the main interface language and TTS language."), v -> {
                    currentLanguage = isEnglish() ? "ar" : "en";
                    prefs.edit().putString("language", currentLanguage).apply();
                    applyTtsLanguage();
                    speak(isEnglish() ? "English selected." : "تم اختيار العربية.");
                    openSettingsScreen();
                });
        addActionButton(t("جهة الطوارئ", "Emergency contact"), t("حفظ أو تغيير رقم الطوارئ.", "Save or change emergency number."), v -> showEmergencyContactDialog());
        addActionButton(t("وسيط GPT-5.5", "GPT-5.5 proxy"), t("إعداد رابط الوسيط واختباره.", "Set and test the proxy URL."), v -> showAiSettingsDialog());
        addActionButton(t("حذف كل بيانات بصير المحلية", "Delete all local Basir data"), t("يحذف السجل والأرشيف والذاكرة المحلية.", "Deletes local log, archive, and memory."), v -> confirmClearAllData());
        addActionButton(t("ماذا يوجد في هذه الصفحة؟", "What is on this page?"), t("يشرح عناصر الإعدادات.", "Explains the settings page."), v -> speak(t(
                "هذه صفحة الإعدادات. يمكنك تغيير الخصوصية، اللغة، جهة الطوارئ، وسيط GPT-5.5، أو حذف البيانات المحلية.",
                "This is the settings page. You can change privacy, language, emergency contact, GPT-5.5 proxy, or delete local data."
        )));
        addBackButton();
    }

    private void confirmClearAllData() {
        new AlertDialog.Builder(this)
                .setTitle(t("حذف البيانات المحلية", "Delete local data"))
                .setMessage(t("سيتم حذف السجل والأرشيف والذاكرة الشخصية من هذا الجهاز. لا يمكن التراجع من داخل التطبيق.", "This deletes the log, archive, and personal memory from this device. This cannot be undone inside the app."))
                .setPositiveButton(t("حذف", "Delete"), (dialog, which) -> {
                    db.clearAllData();
                    speak(t("تم حذف بيانات بصير المحلية.", "Local Basir data deleted."));
                    openSettingsScreen();
                })
                .setNegativeButton(t("إلغاء", "Cancel"), null)
                .show();
    }

    private void openAboutScreen() {
        buildBase(t("حول التطبيق", "About the app"), t(
                "بصير AI مساعد ذكي للمكفوفين وضعاف البصر، يركز على الوصف العملي، تحليل النصوص والصور، الأرشيف، الذاكرة، والخصوصية.",
                "Basir AI is an assistant for blind and low-vision users, focused on practical descriptions, text and image analysis, archive, memory, and privacy."
        ));
        addParagraph(t(
                "الاسم: بصير AI\nالشعار: عينك الذكية في كل مكان\nالإصدار: " + APP_VERSION + "\nالمطور/التواصل: " + CONTACT_EMAIL + "\n\nتنبيه: التطبيق مساعد فقط. لا يستبدل العصا البيضاء، الطبيب، المحامي، خدمات الطوارئ، أو الحكم الشخصي في المواقف الخطرة.",
                "Name: Basir AI\nTagline: Your smart eye everywhere\nVersion: " + APP_VERSION + "\nDeveloper/contact: " + CONTACT_EMAIL + "\n\nNotice: The app is assistive only. It does not replace a white cane, doctor, lawyer, emergency services, or personal judgment in dangerous situations."
        ));
        addActionButton(t("إرسال بريد للتواصل", "Send contact email"),
                t("يفتح تطبيق البريد للتواصل مع المطور.", "Opens the mail app to contact the developer."), v -> contactDeveloper());
        addActionButton(t("نسخ بريد التواصل بالمشاركة", "Share contact email"),
                t("يفتح المشاركة وفيها بريد التواصل.", "Opens sharing with the contact email."), v -> shareText(CONTACT_EMAIL));
        addBackButton();
    }

    private void contactDeveloper() {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:" + CONTACT_EMAIL));
        intent.putExtra(Intent.EXTRA_SUBJECT, "Basir AI feedback");
        intent.putExtra(Intent.EXTRA_TEXT, t("السلام عليكم، لدي ملاحظة حول تطبيق بصير AI:\n", "Hello, I have feedback about Basir AI:\n"));
        try { startActivity(intent); } catch (Exception e) { speak(t("تعذر فتح تطبيق البريد.", "Could not open the mail app.")); }
    }

    private void openResultScreen(String title, String result) {
        buildBase(title, result);
        addActionButton(t("قراءة النتيجة مرة أخرى", "Read result again"), t("يقرأ النتيجة صوتيًا.", "Reads the result aloud."), v -> speak(result));
        addActionButton(t("حفظ في أرشيف المستندات", "Save to document archive"), t("يحفظ النتيجة نصيًا محليًا.", "Saves the result locally as text."), v -> {
            db.insertDocument(title, "ai_result", result, LocalAnalyzer.makeSummary(result));
            saveLog("document_saved", title);
            speak(t("تم حفظ النتيجة في الأرشيف.", "Result saved to archive."));
        });
        addActionButton(t("مشاركة النتيجة", "Share result"), t("يفتح شاشة المشاركة لإرسال النتيجة.", "Opens the share sheet to send the result."), v -> shareText(result));
        addBackButton();
    }

    private void shareText(String text) {
        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.setType("text/plain");
        sendIntent.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(sendIntent, t("مشاركة نتيجة بصير", "Share Basir result")));
    }

    private EditText makeSingleLineInput(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setSingleLine(true);
        input.setContentDescription(hint);
        return input;
    }

    private EditText makeMultilineInput(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setMinLines(4);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setContentDescription(hint);
        return input;
    }

    private void addParagraph(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(18);
        tv.setPadding(0, dp(8), 0, dp(8));
        tv.setContentDescription(text);
        root.addView(tv, fullWidth());
    }

    private void addActionButton(String text, String cd, View.OnClickListener listener) {
        Button button = makeButton(text);
        button.setContentDescription(cd);
        button.setOnClickListener(listener);
        root.addView(button, fullWidthWithMargins());
    }

    private void addBackButton() {
        addActionButton(t("رجوع للرئيسية", "Back to home"), t("زر رجوع للرئيسية.", "Back to home button."), v -> buildHomeScreen(t("تم الرجوع إلى الصفحة الرئيسية.", "Returned to the home screen.")));
    }

    private void startVoiceCommand() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, isEnglish() ? "en-US" : "ar-SA");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, t("قل أمرًا مثل: اقرأ نص، طوارئ، سجل، إعدادات", "Say a command such as: read text, emergency, history, settings"));
        try { startActivityForResult(intent, REQ_VOICE); }
        catch (Exception e) { speak(t("التعرف الصوتي غير متاح على هذا الجهاز.", "Speech recognition is not available on this device.")); }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_VOICE && resultCode == RESULT_OK && data != null) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) handleVoiceCommand(results.get(0));
        } else if (requestCode == REQ_CAMERA) {
            String message = t("تم الرجوع من الكاميرا. للتحليل الحقيقي اختر تحليل صورة من المعرض عبر GPT-5.5.", "Returned from camera. For real analysis, choose gallery image analysis through GPT-5.5.");
            saveLog("camera", message);
            speak(message);
        } else if (requestCode == REQ_IMAGE_PICK && resultCode == RESULT_OK && data != null && data.getData() != null) {
            handlePickedImage(data.getData());
        }
    }

    private void handleVoiceCommand(String command) {
        if (command == null) return;
        String c = command.toLowerCase(Locale.ROOT);
        saveLog("voice", command);
        if (c.contains("اقر") || c.contains("نص") || c.contains("read") || c.contains("text")) openTextReaderScreen();
        else if (c.contains("طوارئ") || c.contains("مساعدة") || c.contains("emergency") || c.contains("help")) openEmergencyScreen();
        else if (c.contains("سجل") || c.contains("history") || c.contains("activity")) openHistoryScreen();
        else if (c.contains("إعداد") || c.contains("اعداد") || c.contains("setting")) openSettingsScreen();
        else if (c.contains("مختبر") || c.contains("ذكاء") || c.contains("lab")) openIntelligenceLabScreen();
        else if (c.contains("أرشيف") || c.contains("ارشيف") || c.contains("ذاكرة") || c.contains("archive") || c.contains("memory")) openArchiveAndMemoryScreen();
        else if (c.contains("صورة") || c.contains("image") || c.contains("photo")) chooseImageForAi("image_analysis", t("تحليل صورة", "Image analysis"), "Analyze this image for a blind user with alt text and practical notes.", "Describe this image clearly.");
        else if (c.contains("وصف") || c.contains("scene") || c.contains("describe")) openSceneScreen();
        else if (c.contains("حول") || c.contains("about") || c.contains("contact")) openAboutScreen();
        else {
            String msg = t("سمعت الأمر: ", "I heard: ") + command + t(". لم أتعرف على وظيفة مطابقة بعد.", ". I did not find a matching action yet.");
            speak(msg);
            toast(msg);
        }
    }

    private void speak(String text) {
        if (text == null || text.trim().isEmpty()) return;
        if (tts != null) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "basir_utterance");
    }

    private void vibrate(int milliseconds) {
        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator == null) return;
        if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE));
        else vibrator.vibrate(milliseconds);
    }

    private void vibratePattern(long[] pattern) {
        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator == null) return;
        if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
        else vibrator.vibrate(pattern, -1);
    }

    private void saveLog(String type, String content) {
        if (db != null) db.insertLog(type, content);
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams fullWidthWithMargins() {
        LinearLayout.LayoutParams params = fullWidth();
        params.setMargins(0, dp(6), 0, dp(6));
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
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

    public static class AiClient {
        static String ask(String endpoint, String appToken, String task, String input, String instruction, String language) throws Exception {
            return ask(endpoint, appToken, task, input, instruction, language, null, null);
        }

        static String ask(String endpoint, String appToken, String task, String input, String instruction, String language, String imageBase64, String mimeType) throws Exception {
            JSONObject body = new JSONObject();
            body.put("task", task);
            body.put("input", input);
            body.put("instruction", instruction);
            body.put("language", language == null ? "ar" : language);
            if (imageBase64 != null && !imageBase64.trim().isEmpty()) {
                body.put("image_base64", imageBase64);
                body.put("mime_type", mimeType == null || mimeType.trim().isEmpty() ? "image/jpeg" : mimeType);
            }

            HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(90000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Accept", "application/json");
            if (appToken != null && !appToken.trim().isEmpty()) conn.setRequestProperty("X-Basir-Client-Token", appToken.trim());

            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) { os.write(bytes); }

            int code = conn.getResponseCode();
            InputStream stream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
            String response = readAll(stream);
            if (code < 200 || code >= 300) throw new Exception("HTTP " + code + ": " + response);
            JSONObject json = new JSONObject(response);
            if (json.has("answer")) return json.getString("answer");
            if (json.has("error")) throw new Exception(json.getString("error"));
            return response;
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
    }

    public static class LocalAnalyzer {
        static String analyzeDocument(String text, boolean english) {
            String normalized = text == null ? "" : text.trim();
            String lower = normalized.toLowerCase(Locale.ROOT);
            StringBuilder sb = new StringBuilder();
            if (english) {
                sb.append("Basic local analysis:\n");
                sb.append("Character count: ").append(normalized.length()).append(".\n");
                if (lower.contains("invoice") || lower.contains("bill") || lower.contains("sar") || lower.contains("ريال")) sb.append("This may be a financial document. Look for amount, due date, issuer, and account number.\n");
                if (lower.contains("contract") || lower.contains("rent") || lower.contains("lease") || lower.contains("عقد")) sb.append("This may be a contract. Check duration, obligations, penalty clause, termination, and jurisdiction.\n");
                if (lower.contains("medicine") || lower.contains("dose") || lower.contains("mg") || lower.contains("دواء")) sb.append("This may be health-related. This is informational only; ask a doctor or pharmacist before acting.\n");
                sb.append("Short summary: ").append(makeSummary(normalized));
            } else {
                sb.append("تحليل محلي مبدئي:\n");
                sb.append("عدد الأحرف: ").append(normalized.length()).append(".\n");
                if (lower.contains("فاتورة") || lower.contains("مستحق") || lower.contains("ريال")) sb.append("يبدو أن النص قد يكون فاتورة أو مستندًا ماليًا. ابحث عن: المبلغ، تاريخ الاستحقاق، الجهة، ورقم الحساب.\n");
                if (lower.contains("عقد") || lower.contains("إيجار") || lower.contains("شرط") || lower.contains("طرف")) sb.append("يبدو أن النص قد يكون عقدًا. انتبه إلى: مدة العقد، الالتزامات، الشرط الجزائي، الإخلاء، والاختصاص القضائي.\n");
                if (lower.contains("دواء") || lower.contains("جرعة") || lower.contains("mg") || lower.contains("ملجم")) sb.append("يبدو أن النص قد يكون طبيًا. هذه قراءة مساعدة فقط ولا تعد تشخيصًا أو توصية علاجية. راجع الطبيب أو الصيدلي قبل تغيير أي جرعة.\n");
                sb.append("ملخص قصير: ").append(makeSummary(normalized));
            }
            return sb.toString();
        }

        static String makeSummary(String text) {
            if (text == null || text.trim().isEmpty()) return "";
            String t = text.replaceAll("\\s+", " ").trim();
            return t.length() <= 180 ? t : t.substring(0, 180) + "...";
        }

        static String answer(String question, boolean english) {
            if (question == null || question.trim().isEmpty()) return english ? "Type a question first." : "اكتب سؤالًا أولًا.";
            String q = question.toLowerCase(Locale.ROOT);
            if (q.contains("privacy") || q.contains("خصوصية")) return english ? "Privacy mode prevents automatic image saving, and you can clear local data from settings." : "وضع الخصوصية يمنع حفظ الصور تلقائيًا، ويمكنك حذف البيانات المحلية من الإعدادات.";
            if (q.contains("walk") || q.contains("تنقل") || q.contains("مشي")) return english ? "Walking assistant gives short alerts and vibrations, but it does not replace a white cane or safe mobility skills." : "مساعد المشي يعطي أوامر قصيرة واهتزازات، لكنه لا يستبدل العصا البيضاء أو مهارات الحركة الآمنة.";
            return english ? "This is a limited local answer. For deep answers, configure the secure GPT-5.5 proxy." : "هذه إجابة محلية محدودة. للإجابات العميقة اضبط وسيط GPT-5.5 الآمن.";
        }
    }

    public static class BasirDb extends SQLiteOpenHelper {
        private static final String DB_NAME = "basir_ai.db";
        private static final int DB_VERSION = 3;

        BasirDb(Context context) { super(context, DB_NAME, null, DB_VERSION); }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS logs (id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT, content TEXT, created_at TEXT)");
            db.execSQL("CREATE TABLE IF NOT EXISTS documents (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, kind TEXT, text_content TEXT, summary TEXT, created_at TEXT)");
            db.execSQL("CREATE TABLE IF NOT EXISTS persons (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, relation TEXT, notes TEXT, created_at TEXT)");
            db.execSQL("CREATE TABLE IF NOT EXISTS products (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, barcode TEXT, notes TEXT, created_at TEXT)");
            db.execSQL("CREATE TABLE IF NOT EXISTS places (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, latitude REAL, longitude REAL, notes TEXT, created_at TEXT)");
            db.execSQL("CREATE TABLE IF NOT EXISTS routines (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, notes TEXT, created_at TEXT)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            onCreate(db);
        }

        void insertLog(String type, String content) {
            SQLiteDatabase db = getWritableDatabase();
            String now = now();
            db.execSQL("INSERT INTO logs(type, content, created_at) VALUES(?, ?, ?)", new Object[]{type, content, now});
        }

        void insertDocument(String title, String kind, String textContent, String summary) {
            SQLiteDatabase db = getWritableDatabase();
            String now = now();
            db.execSQL("INSERT INTO documents(title, kind, text_content, summary, created_at) VALUES(?, ?, ?, ?, ?)", new Object[]{title, kind, textContent, summary, now});
        }

        List<String> getRecentDocuments(int limit) {
            List<String> list = new ArrayList<>();
            SQLiteDatabase db = getReadableDatabase();
            Cursor c = db.rawQuery("SELECT title, kind, summary, created_at FROM documents ORDER BY id DESC LIMIT ?", new String[]{String.valueOf(limit)});
            try {
                while (c.moveToNext()) list.add(c.getString(3) + " - " + c.getString(0) + " [" + c.getString(1) + "]: " + c.getString(2));
            } finally { c.close(); }
            return list;
        }

        void insertPerson(String name, String relation, String notes) {
            if (name == null || name.trim().isEmpty()) return;
            SQLiteDatabase db = getWritableDatabase();
            db.execSQL("INSERT INTO persons(name, relation, notes, created_at) VALUES(?, ?, ?, ?)", new Object[]{name, relation, notes, now()});
        }

        void insertProduct(String name, String barcode, String notes) {
            if (name == null || name.trim().isEmpty()) return;
            SQLiteDatabase db = getWritableDatabase();
            db.execSQL("INSERT INTO products(name, barcode, notes, created_at) VALUES(?, ?, ?, ?)", new Object[]{name, barcode, notes, now()});
        }

        void insertPlace(String name, String notes) {
            if (name == null || name.trim().isEmpty()) return;
            SQLiteDatabase db = getWritableDatabase();
            db.execSQL("INSERT INTO places(name, latitude, longitude, notes, created_at) VALUES(?, ?, ?, ?, ?)", new Object[]{name, 0, 0, notes, now()});
        }

        String getMemorySummary(boolean english) {
            StringBuilder sb = new StringBuilder();
            appendRows(sb, english ? "People" : "الأشخاص", "SELECT name, relation, notes FROM persons ORDER BY id DESC LIMIT 20");
            appendRows(sb, english ? "Products and medicines" : "المنتجات والأدوية", "SELECT name, barcode, notes FROM products ORDER BY id DESC LIMIT 20");
            appendRows(sb, english ? "Places" : "الأماكن", "SELECT name, '', notes FROM places ORDER BY id DESC LIMIT 20");
            return sb.toString().trim();
        }

        private void appendRows(StringBuilder sb, String heading, String sql) {
            SQLiteDatabase db = getReadableDatabase();
            Cursor c = db.rawQuery(sql, null);
            try {
                if (c.getCount() > 0) sb.append(heading).append(":\n");
                int i = 1;
                while (c.moveToNext()) {
                    sb.append(i).append(". ").append(c.getString(0));
                    String second = c.getString(1);
                    if (second != null && !second.trim().isEmpty()) sb.append(" - ").append(second);
                    String notes = c.getString(2);
                    if (notes != null && !notes.trim().isEmpty()) sb.append(". ").append(notes);
                    sb.append("\n");
                    i++;
                }
            } finally { c.close(); }
        }

        List<String> getRecentLogs(int limit) {
            List<String> list = new ArrayList<>();
            SQLiteDatabase db = getReadableDatabase();
            Cursor c = db.rawQuery("SELECT type, content, created_at FROM logs ORDER BY id DESC LIMIT ?", new String[]{String.valueOf(limit)});
            try {
                while (c.moveToNext()) list.add(c.getString(2) + " - " + c.getString(0) + ": " + c.getString(1));
            } finally { c.close(); }
            return list;
        }

        void clearLogs() { getWritableDatabase().execSQL("DELETE FROM logs"); }

        void clearAllData() {
            SQLiteDatabase db = getWritableDatabase();
            db.execSQL("DELETE FROM logs");
            db.execSQL("DELETE FROM documents");
            db.execSQL("DELETE FROM persons");
            db.execSQL("DELETE FROM products");
            db.execSQL("DELETE FROM places");
            db.execSQL("DELETE FROM routines");
        }

        private String now() {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        }
    }
}
