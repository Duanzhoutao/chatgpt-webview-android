package com.example.gptweb.web

object WebAppConfig {
    const val HOME_URL = "https://chatgpt.com/"
    const val EXIT_CONFIRM_WINDOW_MS = 2_000L
    const val APP_USER_AGENT_SUFFIX = "ChatGPTWebShell/1.0"
    const val OPEN_ALL_HTTP_URLS_IN_APP = true

    // Hosts that should stay inside the app WebView because ChatGPT/OpenAI auth
    // flows can break if they bounce out to an external browser mid-session.
    val INTERNAL_HOST_SUFFIX_ALLOWLIST = setOf(
        "chatgpt.com",
        "openai.com",
        "oaistatic.com",
        "oaiusercontent.com",
        "auth0.com",
        "google.com",
        "googleapis.com",
        "googleusercontent.com",
        "gstatic.com",
        "microsoftonline.com",
        "live.com",
        "apple.com",
    )
}
