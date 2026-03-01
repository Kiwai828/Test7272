class AppConstants {
  // ════════════════════════════════════════
  // URL တစ်ကြောင်းပဲ ပြောင်းရန်
  static const String baseUrl = 'https://recapmaker.online';
  // Telegram Bot register link
  static const String registerUrl = 'https://t.me/recapmaker_register_bot';
  // ════════════════════════════════════════

  static const int chunkSize = 3 * 1024 * 1024; // 3MB
  static const Duration pollInterval = Duration(seconds: 4);
}
