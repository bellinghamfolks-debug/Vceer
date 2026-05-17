# Basir AI - Proxy Server

A tiny Node.js server that proxies requests from the Basir AI Android app to
OpenAI. The OpenAI API key lives only on this server, never inside the APK.

## Quick start

```bash
cd server
cp .env.example .env
# Edit .env and set OPENAI_API_KEY and BASIR_APP_TOKEN
npm install
npm start
```

The server will listen on `PORT` (default `3000`).

## Deploying

You can deploy this server on any Node.js host (Render, Railway, Fly.io,
your own VPS, etc.). After deployment:

1. Open the Basir AI app on your phone.
2. Go to **Settings → GPT Proxy setup**.
3. Enter the public URL of your server, e.g. `https://your-server.com/api/basir`.
4. Enter the same `BASIR_APP_TOKEN` you set in `.env` as the "App token".
5. Tap **Save**, then **Test AI connection**.

## API

`POST /api/basir`

Request body (JSON):
```json
{
  "task": "ask",
  "input": "What is on this page?",
  "instruction": "Answer concisely.",
  "language": "ar",
  "image_base64": "...optional base64...",
  "mime_type": "image/jpeg"
}
```

Response:
```json
{ "answer": "...", "task": "ask", "model": "gpt-4o" }
```
