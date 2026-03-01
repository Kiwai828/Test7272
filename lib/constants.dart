class AppConstants {
  static const String baseUrl      = 'https://recapmaker.online';
  static const String registerUrl  = 'https://t.me/recapmaker_register_bot';

  // SSL BAD_RECORD_MAC fix: use smaller chunks (512KB instead of 3MB)
  // Large chunks cause TLS record corruption over unstable connections
  static const int chunkSize = 512 * 1024; // 512KB per chunk

  static const Duration pollInterval = Duration(seconds: 4);
}
