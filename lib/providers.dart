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
    final res = await ApiClient().login(u, p);
    if (res['status'] == 'success') {
      state = const AuthState(AuthStatus.ok);
      return true;
    }
    state = AuthState(AuthStatus.error, res['message'] ?? 'Login မအောင်မြင်ပါ');
    return false;
  }

  Future<void> logout() async {
    await ApiClient().logout();
    state = const AuthState(AuthStatus.idle);
  }
}

final authProvider = StateNotifierProvider<AuthNotifier, AuthState>(
  (_) => AuthNotifier());

// ── History ───────────────────────────────────────
final historyProvider = FutureProvider<List<HistoryItem>>((ref) async {
  final raw = await ApiClient().getHistory();
  return raw.map((e) => HistoryItem.fromJson(e)).toList();
});

// ── Upload progress ────────────────────────────────
class UploadState {
  final bool uploading;
  final double progress;
  final int chunk;
  final int total;
  final String message;
  const UploadState({
    this.uploading = false, this.progress = 0,
    this.chunk = 0, this.total = 0, this.message = '',
  });
  UploadState copyWith({bool? uploading, double? progress, int? chunk, int? total, String? message}) =>
    UploadState(
      uploading: uploading ?? this.uploading,
      progress: progress ?? this.progress,
      chunk: chunk ?? this.chunk,
      total: total ?? this.total,
      message: message ?? this.message,
    );
}

class UploadNotifier extends StateNotifier<UploadState> {
  UploadNotifier() : super(const UploadState());
  void reset() => state = const UploadState();
  void set(UploadState s) => state = s;
  void update({bool? uploading, double? progress, int? chunk, int? total, String? message}) =>
    state = state.copyWith(uploading: uploading, progress: progress, chunk: chunk, total: total, message: message);
}

final uploadProvider = StateNotifierProvider<UploadNotifier, UploadState>(
  (_) => UploadNotifier());
