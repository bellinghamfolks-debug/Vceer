# Basir AI

Basir AI is an accessibility-first Android project for blind and low-vision users. It uses a secure server proxy to call GPT-5.5 without storing the OpenAI API key inside the APK.

## Current version

`0.4.0-final-review`

## Main features

- Arabic and English interface switching.
- Screen-reader-friendly main interface.
- Text-to-Speech support.
- Voice command entry point.
- Secure GPT-5.5 proxy settings.
- Ask Basir GPT-5.5.
- Image analysis from gallery through the proxy.
- Alt text generation for blind users.
- Screenshot explanation.
- Document and text analysis.
- Study cards, legal-safe analysis, health-safe analysis, and reply drafting.
- Local archive and personal memory.
- Emergency contact and help message.
- About and Contact screen with `ubdallahalrashdee@gmail.com`.

## Build APK on GitHub

1. Create a GitHub repository.
2. Upload the contents of this project folder.
3. Open the repository Actions tab.
4. Run **Build Basir AI APK**.
5. Download the artifact named `BasirAI-debug-apk`.
6. The APK path inside the artifact is:

```text
app-debug.apk
```

The workflow runs:

```bash
gradle assembleDebug --stacktrace
```

## Server setup

Deploy the `server` folder on a Node.js host such as Render, Railway, Fly.io, or your own VPS.

Set environment variables:

```text
OPENAI_API_KEY=your_key_here
OPENAI_MODEL=gpt-5.5
BASIR_APP_TOKEN=change-this-random-secret
PORT=3000
```

Then set the Android app proxy URL to:

```text
https://your-server.com/api/basir
```

## Security note

Do not put `OPENAI_API_KEY` inside Android source code, GitHub public files, or the APK. The key belongs on the server only.

## Important safety note

Basir AI is assistive. It does not replace a white cane, guide dog, trained human assistance, doctors, pharmacists, lawyers, emergency services, or personal judgment in unsafe situations.
