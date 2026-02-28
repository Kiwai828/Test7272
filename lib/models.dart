import 'dart:convert';

class UserModel {
  final String username;
  final String telegramId;
  final int coins;
  final int freeLeft;
  final String? broadcastMessage;
  final List<PricingTier> pricingTiers;
  final String paymentMessage;
  final String contactUsername;

  UserModel({
    required this.username, required this.telegramId,
    required this.coins, required this.freeLeft,
    this.broadcastMessage, required this.pricingTiers,
    required this.paymentMessage, required this.contactUsername,
  });

  factory UserModel.fromJson(Map<String, dynamic> j) {
    return UserModel(
      username: j['username'] ?? '',
      telegramId: j['telegram_id'] ?? '',
      coins: j['coins'] ?? 0,
      freeLeft: j['free_left'] ?? 0,
      broadcastMessage: j['broadcast'],
      pricingTiers: (j['pricing_tiers'] as List? ?? [])
        .map((t) => PricingTier.fromJson(t)).toList(),
      paymentMessage: j['payment_message'] ?? '',
      contactUsername: j['contact_username'] ?? 'admin',
    );
  }
}

class PricingTier {
  final int maxSeconds;
  final int cost;
  PricingTier({required this.maxSeconds, required this.cost});
  factory PricingTier.fromJson(Map<String, dynamic> j) =>
    PricingTier(maxSeconds: j['max_seconds'] ?? 0, cost: j['cost'] ?? 0);
}

class HistoryItem {
  final String jobId;
  final String status;
  final String filePath;
  final String createdAt;
  final String errorMessage;
  final int secondsLeft;

  HistoryItem({
    required this.jobId, required this.status, required this.filePath,
    required this.createdAt, required this.errorMessage, required this.secondsLeft,
  });

  factory HistoryItem.fromJson(Map<String, dynamic> j) => HistoryItem(
    jobId: j['job_id'] ?? '',
    status: j['status'] ?? 'unknown',
    filePath: j['file_path'] ?? '',
    createdAt: j['created_at'] ?? '',
    errorMessage: j['error_message'] ?? '',
    secondsLeft: j['seconds_left'] ?? 0,
  );

  bool get isDone => status == 'completed';
  bool get isFailed => status == 'failed';
  bool get isPending => !isDone && !isFailed;
}
