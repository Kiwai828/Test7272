// ═══════════════════════════════════════════════
// constants.dart — ဒီ FILE ထဲမှာပဲ URL ပြောင်းရန်
// ═══════════════════════════════════════════════

class AppConstants {
  // ⚠️ SERVER URL — ဒီတစ်ကြောင်းပဲ ပြောင်းရန်
  static const String baseUrl = 'https://recapmaker.online';
  // ဥပမာ: 'https://username-recap-maker.hf.space'

  static const int chunkSize = 3 * 1024 * 1024; // 3MB per chunk
  static const Duration pollInterval = Duration(seconds: 4);
  static const String appName = 'Recap Maker';
  static const String version = '1.0.0';
}
