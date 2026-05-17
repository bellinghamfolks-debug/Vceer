import express from "express";
import cors from "cors";

const app = express();
const port = process.env.PORT || 3000;
const model = process.env.OPENAI_MODEL || "gpt-5.5";
const apiKey = process.env.OPENAI_API_KEY;
const requiredClientToken = process.env.BASIR_APP_TOKEN || "";

app.use(cors());
app.use(express.json({ limit: process.env.BASIR_JSON_LIMIT || "12mb" }));

const rateWindowMs = Number(process.env.BASIR_RATE_WINDOW_MS || 60000);
const rateMax = Number(process.env.BASIR_RATE_MAX || 30);
const hits = new Map();

app.use((req, res, next) => {
  const key = req.ip || req.headers["x-forwarded-for"] || "unknown";
  const now = Date.now();
  const record = hits.get(key) || { count: 0, reset: now + rateWindowMs };
  if (now > record.reset) {
    record.count = 0;
    record.reset = now + rateWindowMs;
  }
  record.count += 1;
  hits.set(key, record);
  if (record.count > rateMax) {
    return res.status(429).json({ error: "تم تجاوز حد الطلبات المؤقت للوسيط. حاول لاحقًا." });
  }
  next();
});

app.get("/", (_req, res) => {
  res.json({
    ok: true,
    service: "Basir AI GPT-5.5 proxy",
    endpoint: "/api/basir",
    model,
    note: "Set OPENAI_API_KEY in the server environment. Do not put it inside the Android APK."
  });
});

app.get("/health", (_req, res) => {
  res.json({ ok: true, model, time: new Date().toISOString() });
});

app.post("/api/basir", async (req, res) => {
  try {
    if (requiredClientToken) {
      const incomingToken = req.get("X-Basir-Client-Token") || "";
      if (incomingToken !== requiredClientToken) {
        return res.status(401).json({ error: "رمز التطبيق غير صحيح." });
      }
    }

    if (!apiKey) {
      return res.status(500).json({ error: "OPENAI_API_KEY غير مضبوط في الخادم." });
    }

    const task = clean(req.body?.task, 80) || "ask";
    const userInput = clean(req.body?.input, 16000);
    const instruction = clean(req.body?.instruction, 3000);
    const language = clean(req.body?.language, 20) || "ar";
    const imageBase64 = cleanBase64(req.body?.image_base64, Number(process.env.BASIR_MAX_IMAGE_BASE64 || 9500000));
    const mimeType = cleanMime(req.body?.mime_type) || "image/jpeg";

    if (!userInput && !imageBase64) {
      return res.status(400).json({ error: "لا يوجد نص أو صورة للتحليل." });
    }

    const systemPrompt = buildSystemPrompt(language, task);
    const taskPrompt = buildTaskPrompt(task, instruction);
    const userPrompt = [
      `المهمة: ${task}`,
      taskPrompt,
      instruction ? `تعليمات خاصة من التطبيق: ${instruction}` : "",
      userInput ? "النص أو السؤال:" : "",
      userInput
    ].filter(Boolean).join("\n\n");

    const input = buildResponseInput(userPrompt, imageBase64, mimeType);

    const response = await fetch("https://api.openai.com/v1/responses", {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${apiKey}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        model,
        instructions: systemPrompt,
        reasoning: { effort: process.env.OPENAI_REASONING_EFFORT || "medium" },
        max_output_tokens: Number(process.env.OPENAI_MAX_OUTPUT_TOKENS || 1800),
        input
      })
    });

    const data = await response.json().catch(() => ({}));

    if (!response.ok) {
      const message = data?.error?.message || `OpenAI API error: HTTP ${response.status}`;
      return res.status(response.status).json({ error: message });
    }

    const answer = extractOutputText(data);
    res.json({
      ok: true,
      answer: answer || "لم يرجع النموذج نصًا واضحًا.",
      model,
      task,
      has_image: Boolean(imageBase64),
      created_at: new Date().toISOString()
    });
  } catch (error) {
    res.status(500).json({ error: error?.message || "خطأ غير معروف في الوسيط." });
  }
});

function buildSystemPrompt(language, task) {
  const outputLanguage = language === "en" ? "English" : "Arabic";
  return `You are Basir AI, an accessibility-first assistant for blind and low-vision users.
Answer in ${outputLanguage} unless the user explicitly asks for another language.
Use clear, screen-reader-friendly structure: short headings, numbered steps, and direct wording.
When analyzing images, provide detailed alt text first, then practical observations. Mention uncertainty when needed.
Prioritize actionable information: what matters, where it is, why it matters, and what the user can safely do next.
Never claim real-time navigation, diagnosis, legal representation, or face identity recognition.
For faces: describe non-sensitive visible attributes only; do not identify a person unless the user supplied the identity.
For health content, provide safe informational help only and advise checking with a doctor or pharmacist before acting.
For legal content, provide educational analysis only and advise consulting a qualified professional for formal action.
For navigation or safety, do not replace a cane, guide dog, human assistance, or official emergency services.
Current task: ${task}.
Be concise when urgent, and detailed when analyzing documents or images.`;
}

function buildTaskPrompt(task, instruction) {
  const prompts = {
    image_analysis: "اكتب وصفًا بديلًا دقيقًا للصورة ثم استخرج النصوص والعوائق والمخاطر والعناصر العملية.",
    alt_text: "اكتب Alt Text منظمًا ومفيدًا للمكفوفين، مع العلاقات المكانية والألوان والنصوص الظاهرة.",
    screenshot: "اشرح لقطة الشاشة: الصفحة، الأزرار، الرسائل، الخطأ، والخطوة التالية.",
    scene_image: "حلل الصورة كمشهد للمستخدم الكفيف: ملخص سريع، اتجاهات، عوائق، خطر، وما العمل الآن.",
    study_cards: "حوّل النص إلى بطاقات مذاكرة سؤال وجواب مباشرة ومناسبة للقراءة الصوتية.",
    legal: "حلل النص قانونيًا قراءة تعليمية مساعدة دون تمثيل قانوني نهائي.",
    health: "اشرح النص الصحي بأمان دون تشخيص أو تغيير جرعات.",
    reply: "اشرح النبرة واقترح ردًا مهذبًا وملائمًا للسياق.",
    document_analysis: "حلل المستند: النوع، الملخص، التواريخ، المبالغ، الجهات، المخاطر، والخطوات العملية.",
    translation: "ترجم أو اشرح النص حسب السياق والنبرة دون حرفية مفرطة.",
    product: "حلل المنتج أو الباركود أو النص المتاح، واذكر ما لا يمكن الجزم به دون صورة أو مصدر متجر.",
    ask: "أجب كمساعد بصير AI بشكل عملي ومنظم."
  };
  return prompts[task] || instruction || prompts.ask;
}

function buildResponseInput(userPrompt, imageBase64, mimeType) {
  if (!imageBase64) return userPrompt;
  return [
    {
      role: "user",
      content: [
        { type: "input_text", text: userPrompt },
        { type: "input_image", image_url: `data:${mimeType};base64,${imageBase64}`, detail: "auto" }
      ]
    }
  ];
}

function clean(value, maxLength) {
  if (typeof value !== "string") return "";
  return value.replace(/\u0000/g, "").trim().slice(0, maxLength);
}

function cleanBase64(value, maxLength) {
  if (typeof value !== "string") return "";
  const cleaned = value.replace(/\s+/g, "").replace(/^data:[^;]+;base64,/, "");
  if (!cleaned) return "";
  if (cleaned.length > maxLength) throw new Error("الصورة كبيرة جدًا للوسيط الحالي.");
  if (!/^[A-Za-z0-9+/=]+$/.test(cleaned)) throw new Error("صيغة الصورة غير صحيحة.");
  return cleaned;
}

function cleanMime(value) {
  if (typeof value !== "string") return "";
  const v = value.trim().toLowerCase();
  return /^image\/(jpeg|jpg|png|webp)$/.test(v) ? v.replace("image/jpg", "image/jpeg") : "image/jpeg";
}

function extractOutputText(data) {
  if (typeof data?.output_text === "string" && data.output_text.trim()) {
    return data.output_text.trim();
  }

  const chunks = [];
  for (const item of data?.output || []) {
    for (const content of item?.content || []) {
      if (typeof content?.text === "string") chunks.push(content.text);
    }
  }
  return chunks.join("\n").trim();
}

app.listen(port, () => {
  console.log(`Basir AI GPT-5.5 proxy running on port ${port}`);
});
