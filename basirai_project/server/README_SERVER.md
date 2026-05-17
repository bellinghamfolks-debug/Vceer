# وسيط بصير AI لتشغيل GPT-5.5

هذا الخادم هو الوسيط الآمن بين تطبيق Android و OpenAI API.

## لماذا نحتاج وسيطًا؟

لا تضع مفتاح OpenAI API داخل تطبيق Android. ملف APK يمكن فكه واستخراج الأسرار منه. التطبيق يرسل الطلب إلى هذا الخادم، والخادم وحده يحتفظ بالمفتاح في متغيرات البيئة.

## المتغيرات المطلوبة

- `OPENAI_API_KEY`: مفتاح OpenAI API.
- `OPENAI_MODEL`: النموذج، الافتراضي `gpt-5.5`.
- `OPENAI_REASONING_EFFORT`: الافتراضي `medium`.
- `OPENAI_MAX_OUTPUT_TOKENS`: الافتراضي `1600`.
- `BASIR_APP_TOKEN`: رمز اختياري تضعه في التطبيق أيضًا لتقليل إساءة استخدام الخادم.

## التشغيل المحلي

```bash
cd server
npm install
cp .env.example .env
# عدل OPENAI_API_KEY داخل .env
npm start
```

الرابط الذي تضعه داخل التطبيق:

```text
http://YOUR_LOCAL_IP:3000/api/basir
```

على الهاتف الحقيقي لا تستخدم `localhost` لأنه يشير للهاتف نفسه، بل استخدم IP جهاز الكمبيوتر على نفس الشبكة.

## النشر على Render أو Railway أو أي منصة Node

1. ارفع مجلد المشروع إلى GitHub.
2. أنشئ Web Service من مجلد `server`.
3. أمر التثبيت: `npm install`.
4. أمر التشغيل: `npm start`.
5. أضف متغيرات البيئة السابقة.
6. بعد النشر، ضع رابط endpoint داخل التطبيق، مثل:

```text
https://your-service.onrender.com/api/basir
```

## تحديث 0.3.0: دعم الصور

أصبح الوسيط يقبل الحقول التالية في `/api/basir`:

```json
{
  "task": "image_analysis",
  "input": "صف هذه الصورة للمستخدم الكفيف",
  "language": "ar",
  "image_base64": "...",
  "mime_type": "image/jpeg"
}
```

المتغيرات الاختيارية الجديدة:

```env
BASIR_JSON_LIMIT=12mb
BASIR_MAX_IMAGE_BASE64=9500000
BASIR_RATE_WINDOW_MS=60000
BASIR_RATE_MAX=30
```
