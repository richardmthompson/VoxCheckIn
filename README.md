# Vox Check-in

An Android app for spoken journaling with an AI coach. You talk about your day; the coach listens, asks you questions and helps you reflect. Each check-in is saved with the themes it touched, so you can look back over earlier conversations.

## How it works

- **Voice in, voice out.** Google Cloud Speech-to-Text hears you, Gemini works out what to say, and Google Cloud Text-to-Speech says it.
- **The coach thinks in separate steps** ([`DialogueChain.kt`](app/src/main/java/com/example/voxmanifestorapp/ui/agent/checkin/DialogueChain.kt)). One Gemini call reads the conversation so far, picks out its themes and judges whether the check-in has run its course. If it hasn't, the next call chooses how to coach the next turn (building rapport, exploring an experience, reflecting back what it heard, validating a feeling, offering another viewpoint) and writes the reply in that style. Giving each call one job keeps its answers easy to check.
- **Memory.** Conversations and their themes are stored on the phone with Room.
- **Built with** Kotlin, Jetpack Compose and Room; the agent framework is written by hand, without an agent library.

The [check-in README](app/src/main/java/com/example/voxmanifestorapp/ui/agent/checkin/README.md) walks through the flow from a button press to a spoken reply.

## Build

You need Android Studio with JDK 17 and Android SDK 34, a Gemini API key, and a Google Cloud service account with Speech-to-Text and Text-to-Speech enabled.

1. Add `GEMINI_API_KEY=...` to `local.properties`, or set it in the environment.
2. Save the service account's JSON key as `app/src/main/res/raw/creds`. Git ignores that file; keep it out of commits and out of any APK you share.
3. Build and test with `./gradlew assembleDebug testDebugUnitTest`, or install on a connected phone with `./gradlew installDebug`.
