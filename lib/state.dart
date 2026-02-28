import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'api_client.dart';
import 'models.dart';

// ── Auth ──────────────────────────────────────────
enum AuthStatus { idle, loading, ok, error }

class AuthState {
  final AuthStatus status;
  final String? error;
  const AuthState(this.status, [this.error]);
}

class AuthNotifier extends StateNotifier<AuthState> {
  AuthNotifier() : super(const AuthState(AuthStatus.idle));

  Future<bool> login(String u, String p) async {
    state = const AuthState(AuthStatus.loading);
    final r = await Api().login(u, p);
    if (r['status'] == 'success') {
      state = const AuthState(AuthStatus.ok);
      return true;
    }
    state = AuthState(AuthStatus.error, r['message'] ?? 'Login မအောင်မြင်ပါ');
    return false;
  }

  Future<void> logout() async {
    await Api().logout();
    state = const AuthState(AuthStatus.idle);
  }
}

final authProvider = StateNotifierProvider<AuthNotifier, AuthState>((_) => AuthNotifier());

// ── User Info ─────────────────────────────────────
class UserNotifier extends StateNotifier<AsyncValue<UserInfo>> {
  UserNotifier() : super(const AsyncValue.loading());

  Future<void> load() async {
    try {
      state = const AsyncValue.loading();
      final d = await Api().userInfo();
      state = AsyncValue.data(UserInfo.fromJson(d));
    } catch (e, s) {
      state = AsyncValue.error(e, s);
    }
  }

  void updateCoins(int coins, int freeLeft) {
    state.whenData((u) => state = AsyncValue.data(u.copyWith(coins: coins, freeLeft: freeLeft)));
  }
}

final userProvider = StateNotifierProvider<UserNotifier, AsyncValue<UserInfo>>((_) => UserNotifier());

// ── History ───────────────────────────────────────
class HistoryNotifier extends StateNotifier<AsyncValue<List<HistoryItem>>> {
  HistoryNotifier() : super(const AsyncValue.loading());

  Future<void> load() async {
    try {
      state = const AsyncValue.loading();
      final r = await Api().history();
      state = AsyncValue.data(r.map((e) => HistoryItem.fromJson(e)).toList());
    } catch (e, s) {
      state = AsyncValue.error(e, s);
    }
  }
}

final historyProvider = StateNotifierProvider<HistoryNotifier, AsyncValue<List<HistoryItem>>>((_) => HistoryNotifier());

// ── Upload Progress ────────────────────────────────
class UploadState {
  final bool active;
  final double progress;
  final int chunk, total;
  const UploadState({this.active = false, this.progress = 0, this.chunk = 0, this.total = 0});
  UploadState cp({bool? active, double? progress, int? chunk, int? total}) =>
    UploadState(active: active ?? this.active, progress: progress ?? this.progress,
      chunk: chunk ?? this.chunk, total: total ?? this.total);
}

final uploadProvider = StateNotifierProvider<UploadNotifier, UploadState>((_) => UploadNotifier());

class UploadNotifier extends StateNotifier<UploadState> {
  UploadNotifier() : super(const UploadState());
  void reset() => state = const UploadState();
  void update({bool? active, double? progress, int? chunk, int? total}) =>
    state = state.cp(active: active, progress: progress, chunk: chunk, total: total);
}
