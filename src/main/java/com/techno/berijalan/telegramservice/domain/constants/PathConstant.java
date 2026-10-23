package com.techno.berijalan.telegramservice.domain.constants;

public class PathConstant {
    private PathConstant() {}

    public static final String ROOT_ENDPOINT_CONTEXT = "/rest/v1/telegram";

    // Telegram Bot CRUD & Verification
    public static final String URI_TELEGRAM_BOT_CREATE       = "/bot/create";
    public static final String URI_TELEGRAM_BOT_UPDATE       = "/bot/update";
    public static final String URI_TELEGRAM_BOT_DELETE       = "/bot/delete/{id}";
    public static final String URI_TELEGRAM_BOT_GET_LIST     = "/bot/get-list";
    public static final String URI_TELEGRAM_BOT_GET_DETAIL   = "/bot/get-detail/{id}";
    public static final String URI_TELEGRAM_BOT_VERIFY_TOKEN = "/bot/verify-token";

    // Telegram Inbound Webhook (per Bot)
    public static final String URI_TELEGRAM_WEBHOOK          = "/webhook/{botId}";

    // Telegram Outbound Chat & History
    public static final String URI_TELEGRAM_CHAT_SEND        = "/chat/send";
    public static final String URI_TELEGRAM_CHAT_HISTORY     = "/chat/history/{bucketId}";
    public static final String URI_TELEGRAM_CHAT_BUCKETS     = "/chat/buckets";
    public static final String URI_TELEGRAM_CHAT_TOGGLE_STATUS = "/chat/toggle-status/{bucketId}";
    public static final String URI_TELEGRAM_CHAT_STREAM      = "/chat/stream";

    // Telegram Media Storage & Streaming
    public static final String URI_TELEGRAM_MEDIA_UPLOAD     = "/media/upload";
    public static final String URI_TELEGRAM_MEDIA_STREAM     = "/media/stream";
}


