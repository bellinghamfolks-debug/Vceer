# Basir AI – بصير AI

> عينك الذكية في كل مكان · Your smart eye, everywhere

Basir AI is a comprehensive Android assistant for blind and low-vision users.
It combines AI-powered scene analysis, document reading, smart translation,
walking guidance, personal memory, and emergency tools — all wired to a secure
GPT proxy server (so no API key ever lives inside the APK).

## Features

- 📷 **Scene description** – send any image to GPT for alt text, obstacles, risks
- 📄 **Document reader** – invoices, contracts, medical notes with structured output
- 💬 **Ask Basir** – open-ended questions to GPT, screen-reader friendly answers
- 🧪 **AI Lab** – alt text, screenshot explanation, study cards, polite replies
- 🌐 **Smart translation** – contextual AR/EN with tone notes
- 🚶 **Walking assistant** – short voice + vibration alerts (does not replace a cane)
- 🆘 **Emergency mode** – one-tap SMS with approximate location
- 🧠 **Personal memory** – save people, products, places (local only, encrypted-safe)
- 🗂 **Archive & activity log** – everything stored locally on the device
- ⚙️ **Extensive settings** – language, TTS rate, font size, privacy, vibration, auto-save
- 🎤 **Voice commands** – navigate the whole app hands-free in Arabic or English

## How it builds

This repo includes a GitHub Actions workflow that builds a debug APK on every
push to `main`. To download:

1. Push to GitHub (use the **Save to Github** button on Emergent).
2. Open the **Actions** tab → **Build Basir AI APK** workflow.
3. Wait ~5 minutes for the build to finish.
4. Open the run → **Artifacts** → download `BasirAI-debug-apk`.
5. Install the APK on your Android phone.

## How the AI works

The app talks to a small Node.js proxy that holds the OpenAI API key.
See [`server/README_SERVER.md`](server/README_SERVER.md) for setup.

Once your proxy is online, open the app → **Settings → GPT Proxy setup**,
paste the URL, save, then tap **Test AI connection**.

## Stack

| Layer    | Technology                                  |
|----------|---------------------------------------------|
| App      | Android Native (Java 17, framework only)    |
| Build    | AGP 8.2.2 · Gradle 8.5 · compileSdk 34      |
| CI       | GitHub Actions (Ubuntu, Temurin JDK 17)     |
| Proxy    | Node.js 18+ · Express                       |
| AI       | OpenAI Chat Completions (text + vision)     |

## Contact

- 📧 ubdallahalrashdee@gmail.com
- 👤 عبدالله الراشدي · Abdullah Al-Rashidi

## License

All rights reserved. Contact the developer for licensing inquiries.
