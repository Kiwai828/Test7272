import 'package:dio/dio.dart';
import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio_cookie_manager/dio_cookie_manager.dart';
import 'constants.dart';

class ApiClient {
  static final ApiClient _i = ApiClient._internal();
  factory ApiClient() => _i;

  late final Dio dio;
  final CookieJar _jar = CookieJar();

  ApiClient._internal() {
    dio = Dio(BaseOptions(
      baseUrl: AppConstants.baseUrl,
      connectTimeout: const Duration(seconds: 30),
      receiveTimeout: const Duration(minutes: 10),
      sendTimeout: const Duration(minutes: 10),
    ));
    dio.interceptors.add(CookieManager(_jar));
  }

  // ── AUTH ───────────────────────────────────────
  Future<Map<String, dynamic>> login(String username, String password) async {
    try {
      final r = await dio.post('/login',
        data: {'username': username, 'password': password},
        options: Options(contentType: Headers.formUrlEncodedContentType));
      return r.data as Map<String, dynamic>;
    } on DioException catch (e) {
      return {'status': 'error', 'message': e.message ?? 'Connection failed'};
    }
  }

  Future<void> logout() async {
    try { await dio.get('/logout'); } catch (_) {}
    await _jar.deleteAll();
  }

  // ── USER ───────────────────────────────────────
  Future<Map<String, dynamic>> getUser() async {
    final r = await dio.get('/api/user_info');
    return r.data as Map<String, dynamic>;
  }

  Future<List<dynamic>> getPackages() async {
    final r = await dio.get('/api/get_packages');
    return r.data as List<dynamic>;
  }

  // ── HISTORY ────────────────────────────────────
  Future<List<dynamic>> getHistory() async {
    final r = await dio.get('/api/my_history');
    return r.data as List<dynamic>;
  }

  Future<Map<String, dynamic>> getJobStatus(String jobId) async {
    final r = await dio.get('/status/$jobId');
    return r.data as Map<String, dynamic>;
  }

  // ── UPLOAD (chunked) ───────────────────────────
  Future<String> uploadInit(String filename, int totalChunks, int fileSize) async {
    final r = await dio.post('/upload-init', data: {
      'filename': filename, 'total_chunks': totalChunks, 'file_size': fileSize,
    });
    if (r.data['status'] != 'success') throw Exception(r.data['message']);
    return r.data['upload_id'] as String;
  }

  Future<void> uploadChunk(String uploadId, int index, List<int> bytes) async {
    await dio.post('/upload-chunk', data: FormData.fromMap({
      'upload_id': uploadId,
      'chunk_index': index.toString(),
      'chunk': MultipartFile.fromBytes(bytes, filename: 'chunk_$index'),
    }));
  }

  Future<String> uploadComplete(String uploadId) async {
    final r = await dio.post('/upload-complete', data: {'upload_id': uploadId});
    if (r.data['status'] != 'success') throw Exception(r.data['message']);
    return r.data['filename'] as String;
  }

  // ── DOWNLOAD FROM URL ──────────────────────────
  Future<Map<String, dynamic>> downloadFromUrl(String url) async {
    final r = await dio.post('/download-from-url', data: {'url': url});
    return r.data as Map<String, dynamic>;
  }

  // ── VIDEO PROCESS ──────────────────────────────
  Future<Map<String, dynamic>> processVideo(Map<String, String> data) async {
    final r = await dio.post('/process-video',
      data: data,
      options: Options(contentType: Headers.formUrlEncodedContentType));
    return r.data as Map<String, dynamic>;
  }

  // ── SUBTITLE PROCESS ───────────────────────────
  Future<Map<String, dynamic>> processSubtitles(Map<String, String> data) async {
    final r = await dio.post('/process-subtitles',
      data: data,
      options: Options(contentType: Headers.formUrlEncodedContentType));
    return r.data as Map<String, dynamic>;
  }

  // ── RE-ANALYZE ─────────────────────────────────
  Future<String> reAnalyze(String filename) async {
    final r = await dio.post('/re-analyze',
      data: {'filename': filename},
      options: Options(contentType: Headers.formUrlEncodedContentType));
    return r.data['translated_text'] ?? '';
  }

  String resolveUrl(String path) {
    if (path.startsWith('http')) return path;
    return '${AppConstants.baseUrl}$path';
  }
}
