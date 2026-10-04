# Gemini Wrapper

Android 10+ (minSdk 29) app that wraps https://gemini.google.com/app with two bottom tabs:

1. **Gemini logo** – the normal Gemini web page.
2. **Hexagon + square icon** – a custom chat screen. Text typed there is inserted into Gemini's real
   input box and sent. When the reply appears it is copied and shown as the AI reply in the custom chat.

Tapping either button plays a 360° rotation animation.

## Build the APK with GitHub

1. Create a repo and push everything in this folder (including the hidden `.github` folder).
2. Open the **Actions** tab → **Build APK** (it also runs on every push).
3. When it finishes, download the **GeminiWrapper-debug-apk** artifact, unzip it, install `app-debug.apk`.

No Gradle wrapper is committed; the workflow installs Gradle 8.9 and JDK 17 itself.

## If Gemini changes its page

All page-specific selectors are at the top of `app/src/main/assets/gemini_relay.js`
(`PRIMARY`, `FALLBACK`, `EDITOR_SELECTORS`, `SEND_SELECTORS`, `STOP_SELECTOR`).

## Notes

- Keep the app open while waiting for a reply; Android pauses WebViews in the background.
- The custom chat keeps using the same Gemini conversation. **New chat** opens a fresh Gemini chat.
- Chat history of the custom screen is stored locally on the device.
