import 'package:dio/dio.dart';
import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio_cookie_manager/dio_cookie_manager.dart';
import 'package:path_provider/path_provider.dart';
import 'constants.dart';

class Api {
  static final Api _i = Api._internal();
  factory Api() => _i;
  Api._internal();

  Dio? _dio;
  PersistCookieJar? _jar;

  String get baseUrl => AppConstants.baseUrl;

  // Auto-init: called before every request
  Future<Dio> get _client async {
    if (_dio != null) return _dio!;
    final dir = await getApplicationDocumentsDirectory();
    _jar = PersistCookieJar(storage: FileStorage('${dir.path}/.cookies/'));
    _dio = Dio(BaseOptions(
      baseUrl: AppConstants.baseUrl,
      connectTimeout: const Duration(seconds: 30),
      receiveTimeout: const Duration(minutes: 10),
      sendTimeout: const Duration(minutes: 10),
      followRedirects: false,
      validateStatus: (s) => s != null && s < 500,
    ));
    _dio!.interceptors.add(CookieManager(_jar!));
    return _dio!;
  }

  Future<void> init() async { await _client; }

  Future<bool> isLoggedIn() async {
    try {
      final d = await _client;
      final r = await d.get('/api/user_info');
      return r.data is Map && r.data['status'] == 'success';
    } catch (_) { return false; }
  }

  Future<void> logout() async {
    try { final d = await _client; await d.get('/logout'); } catch (_) {}
    await _jar?.deleteAll();
  }

  Future<Map<String, dynamic>> login(String u, String p) async {
    try {
      final d = await _client;
      final r = await d.post('/login',
        data: {'username': u, 'password': p},
        options: Options(contentType: Headers.formUrlEncodedContentType));
      if (r.data is Map<String, dynamic>) return r.data;
      return {'status': 'error', 'message': r.data.toString()};
    } on DioException catch (e) {
      return {'status': 'error', 'message': e.message ?? 'Connection failed'};
    }
  }

  Future<Map<String, dynamic>> userInfo() async {
    final d = await _client;
    final r = await d.get('/api/user_info');
    if (r.data is Map<String, dynamic>) return r.data;
    return {'status': 'error'};
  }

  Future<List<dynamic>> history() async {
    final d = await _client;
    final r = await d.get('/api/my_history');
    if (r.data is List) return r.data;
    return [];
  }

  Future<Map<String, dynamic>> jobStatus(String id) async {
    final d = await _client;
    final r = await d.get('/status/$id');
    if (r.data is Map<String, dynamic>) return r.data;
    return {'status': 'processing'};
  }

  // ── Chunked Upload ────────────────────────────
  Future<String> uploadInit(String name, int chunks, int size) async {
    final d = await _client;
    final r = await d.post('/upload-init',
      data: {'filename': name, 'total_chunks': chunks, 'file_size': size},
      options: Options(contentType: 'application/json'));
    if (r.data is! Map) throw 'Server error: ${r.data}';
    if (r.data['status'] != 'success') throw r.data['message'] ?? 'Init failed';
    return r.data['upload_id'] as String;
  }

  Future<void> uploadChunk(String id, int idx, List<int> bytes) async {
    final d = await _client;
    await d.post('/upload-chunk',
      data: FormData.fromMap({
        'upload_id': id,
        'chunk_index': idx.toString(),
        'chunk': MultipartFile.fromBytes(bytes, filename: 'chunk_$idx'),
      }));
  }

  Future<String> uploadComplete(String id) async {
    final d = await _client;
    final r = await d.post('/upload-complete',
      data: {'upload_id': id},
      options: Options(contentType: 'application/json'));
    if (r.data is! Map) throw 'Server error: ${r.data}';
    if (r.data['status'] != 'success') throw r.data['message'] ?? 'Complete failed';
    return r.data['filename'] as String;
  }

  Future<Map<String, dynamic>> downloadUrl(String url) async {
    final d = await _client;
    final r = await d.post('/download-from-url',
      data: {'url': url},
      options: Options(contentType: 'application/json'));
    if (r.data is Map<String, dynamic>) return r.data;
    return {'status': 'error', 'message': r.data.toString()};
  }

  Future<Map<String, dynamic>> processVideo(Map<String, String> data) async {
    final d = await _client;
    final r = await d.post('/process', data: data,
      options: Options(contentType: Headers.formUrlEncodedContentType));
    if (r.data is Map<String, dynamic>) return r.data;
    return {'status': 'error', 'message': r.data.toString()};
  }

  Future<Map<String, dynamic>> processSubtitle(Map<String, String> data) async {
    final d = await _client;
    final r = await d.post('/process-subtitles', data: data,
      options: Options(contentType: Headers.formUrlEncodedContentType));
    if (r.data is Map<String, dynamic>) return r.data;
    return {'status': 'error', 'message': r.data.toString()};
  }

  Future<String> reAnalyze(String fname) async {
    try {
      final d = await _client;
      final r = await d.post('/re-analyze',
        data: {'filename': fname},
        options: Options(contentType: Headers.formUrlEncodedContentType));
      if (r.data is Map) return r.data['translated_text'] ?? '';
      return '';
    } catch (_) { return ''; }
  }

  String streamUrl(String path) {
    if (path.startsWith('http')) return path;
    return '$baseUrl/stream-file/${path.split('/').last}';
  }

  String resolveUrl(String path) {
    if (path.startsWith('http')) return path;
    return '$baseUrl$path';
  }
}
