/**
 * Basir AI - Secure proxy server for OpenAI / ChatGPT.
 *
 * Why a proxy?
 *   The OpenAI API key never lives inside the Android APK.
 *   The Android app POSTs requests here; this server adds the API key
 *   and forwards them to OpenAI, then returns the answer.
 *
 * Endpoint:
 *   POST /api/basir
 *   Headers: Content-Type: application/json
 *            X-Basir-Client-Token: <BASIR_APP_TOKEN>   (optional but recommended)
 *   Body: {
 *     task: string,
 *     input: string,
 *     instruction: string,
 *     language: "ar" | "en",
 *     image_base64?: string,
 *     mime_type?: string
 *   }
 *   Response: { "answer": "..." }
 *
 * Required env vars (.env):
 *   OPENAI_API_KEY       OpenAI secret key
 *   OPENAI_MODEL         Model name (default: gpt-4o)
 *   BASIR_APP_TOKEN      Shared secret to gate access (optional but recommended)
 *   PORT                 Listening port (default: 3000)
 */

require('dotenv').config();
const express = require('express');
const fetch = require('node-fetch');

const app = express();
app.use(express.json({ limit: '15mb' }));

const PORT = process.env.PORT || 3000;
const OPENAI_API_KEY = process.env.OPENAI_API_KEY;
const OPENAI_MODEL = process.env.OPENAI_MODEL || 'gpt-4o';
const BASIR_APP_TOKEN = (process.env.BASIR_APP_TOKEN || '').trim();

function buildSystemPrompt(language, instruction) {
  const langName = language === 'en' ? 'English' : 'Arabic';
  return [
    'You are Basir AI, an assistant for blind and low-vision users.',
    `Respond strictly in ${langName} unless the user explicitly asks otherwise.`,
    'Be practical, structured, screen-reader friendly, and concise when possible.',
    'Never identify real persons by face. Avoid medical diagnosis or legal verdicts.',
    instruction || ''
  ].filter(Boolean).join('\n');
}

app.get('/', (_req, res) => {
  res.json({ ok: true, service: 'basir-proxy', model: OPENAI_MODEL });
});

app.get('/health', (_req, res) => {
  res.json({ ok: true });
});

app.post('/api/basir', async (req, res) => {
  try {
    if (BASIR_APP_TOKEN) {
      const got = (req.header('X-Basir-Client-Token') || '').trim();
      if (got !== BASIR_APP_TOKEN) {
        return res.status(401).json({ error: 'Invalid client token' });
      }
    }
    if (!OPENAI_API_KEY) {
      return res.status(500).json({ error: 'OPENAI_API_KEY is not set on the server' });
    }

    const { task = 'ask', input = '', instruction = '', language = 'ar',
            image_base64, mime_type = 'image/jpeg' } = req.body || {};

    const systemPrompt = buildSystemPrompt(language, instruction);

    // Build user message (with optional image)
    let userContent;
    if (image_base64) {
      userContent = [
        { type: 'text', text: input || 'Describe this image.' },
        { type: 'image_url',
          image_url: { url: `data:${mime_type};base64,${image_base64}` } }
      ];
    } else {
      userContent = input || '';
    }

    const payload = {
      model: OPENAI_MODEL,
      messages: [
        { role: 'system', content: systemPrompt },
        { role: 'user', content: userContent }
      ],
      temperature: 0.4
    };

    const upstream = await fetch('https://api.openai.com/v1/chat/completions', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${OPENAI_API_KEY}`
      },
      body: JSON.stringify(payload)
    });

    const data = await upstream.json();
    if (!upstream.ok) {
      return res.status(upstream.status).json({
        error: (data && data.error && data.error.message) || 'OpenAI request failed'
      });
    }

    const answer = (data.choices && data.choices[0] && data.choices[0].message &&
                    data.choices[0].message.content) || '';
    return res.json({ answer, task, model: OPENAI_MODEL });
  } catch (err) {
    return res.status(500).json({ error: String(err && err.message || err) });
  }
});

app.listen(PORT, () => {
  console.log(`Basir proxy listening on :${PORT} (model=${OPENAI_MODEL})`);
});
