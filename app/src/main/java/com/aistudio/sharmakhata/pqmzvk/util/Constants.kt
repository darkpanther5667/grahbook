package com.aistudio.sharmakhata.pqmzvk.util

object Constants {
    // ── PRODUCTION ─────────────────────────────────────────────────────
    // Points to the Render service that runs both the Express API + Next.js panel.
    const val BASE_URL = "https://wpapp-xz9l.onrender.com/"

    // ── LOCAL DEVELOPMENT ─────────────────────────────────────────────
    // Android Emulator: "http://10.0.2.2:3000/"
    // Physical device (same WiFi): "http://YOUR_COMPUTER_IP:3000/"
    // const val BASE_URL = "http://10.0.2.2:3000/"

    const val WALK_IN_CUSTOMER_ID = "walk-in"

    // TODO: Replace with your actual Sentry DSN from https://sentry.io
    const val SENTRY_DSN = "https://examplePublicKey@o0.ingest.sentry.io/0"
}
