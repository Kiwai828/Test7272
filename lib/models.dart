class UserInfo {
  final String username, telegramId, paymentMsg, contactUser;
  final int coins, freeLeft;
  final String? broadcast;
  final List<Map<String, dynamic>> pricingTiers, packages;

  UserInfo({
    required this.username, required this.telegramId,
    required this.coins, required this.freeLeft,
    required this.paymentMsg, required this.contactUser,
    this.broadcast,
    required this.pricingTiers, required this.packages,
  });

  factory UserInfo.fromJson(Map<String, dynamic> j) => UserInfo(
    username: j['username'] ?? '',
    telegramId: j['telegram_id'] ?? '',
    coins: j['coins'] ?? 0,
    freeLeft: j['free_left'] ?? 0,
    paymentMsg: j['payment_message'] ?? '',
    contactUser: j['contact_username'] ?? 'admin',
    broadcast: j['broadcast'],
    pricingTiers: List<Map<String, dynamic>>.from(j['pricing_tiers'] ?? []),
    packages: List<Map<String, dynamic>>.from(j['packages'] ?? []),
  );

  UserInfo copyWith({int? coins, int? freeLeft}) => UserInfo(
    username: username, telegramId: telegramId,
    coins: coins ?? this.coins, freeLeft: freeLeft ?? this.freeLeft,
    paymentMsg: paymentMsg, contactUser: contactUser,
    broadcast: broadcast, pricingTiers: pricingTiers, packages: packages,
  );
}

class HistoryItem {
  final String jobId, status, filePath, createdAt, errorMsg;
  final int secondsLeft;

  HistoryItem({
    required this.jobId, required this.status, required this.filePath,
    required this.createdAt, required this.errorMsg, required this.secondsLeft,
  });

  factory HistoryItem.fromJson(Map<String, dynamic> j) => HistoryItem(
    jobId: j['job_id'] ?? '',
    status: j['status'] ?? '',
    filePath: j['file_path'] ?? '',
    createdAt: j['created_at'] ?? '',
    errorMsg: j['error_message'] ?? '',
    secondsLeft: j['seconds_left'] ?? 0,
  );

  bool get isDone => status == 'completed';
  bool get isFailed => status == 'failed';
  bool get isPending => !isDone && !isFailed;
}
