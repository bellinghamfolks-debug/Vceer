# Basir - Gemini Proxy Server (v1.0.1)

Tiny Node.js server that powers Basir's AI features. Holds the Google Gemini
API key so it never lives inside the APK.

## Endpoints

### `POST /api/basir`
Text questions, image analysis, translation, replies.

```json
{
  "task": "ask",
  "input": "What's on this page?",
  "instruction": "Be concise.",
  "language": "ar",
  "image_base64": "...optional base64...",
  "mime_type": "image/jpeg"
}
```
Returns: `{ "answer": "...", "task": "ask", "model": "gemini-3-flash-preview" }`

### `POST /api/convert`
Convert a PDF or PPTX into a `.docx` file with image and table descriptions
(multipart form-data). Form fields:
- `file` (the .pdf or .pptx)
- `language` ("ar" or "en")
- `mode` ("full" | "simple" | "descriptions_only" | "text_only")

Returns the `.docx` directly as a binary download.

## Quick start

```bash
cd server
cp .env.example .env
# Set GEMINI_API_KEY and BASIR_APP_TOKEN
npm install
npm start
```

Then in the Basir Android app: **Settings → Gemini setup**, paste your public
proxy URL and the same `BASIR_APP_TOKEN`, then tap **Test Gemini connection**.

## Models

| Task type                | Model                       |
|--------------------------|-----------------------------|
| Quick Q&A, translation,  | `gemini-3-flash-preview`    |
| polite reply, health check |                           |
| Image analysis, docs,    | `gemini-3-pro-preview`      |
| legal, medical, PDF→Word |                           |

Override via `GEMINI_MODEL_FAST` and `GEMINI_MODEL_PRIMARY` in `.env`.

## Privacy

- Uploaded files are deleted immediately after the `.docx` is built.
- No data is stored on the server beyond the temporary upload.
