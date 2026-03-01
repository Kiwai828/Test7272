class AppConstants {
  // ════════════════════════════════════════
  // URL တစ်ကြောင်းပဲ ပြောင်းရန်
  static const String baseUrl = 'https://YOUR_SERVER_URL_HERE';
  // Telegram Bot register link
  static const String registerUrl = 'https://t.me/YOUR_BOT_NAME_HERE';
  // ════════════════════════════════════════

  static const int chunkSize = 3 * 1024 * 1024; // 3MB
  static const Duration pollInterval = Duration(seconds: 4);
}
