import 'package:dio/dio.dart';
import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio_cookie_manager/dio_cookie_manager.dart';
import 'package:path_provider/path_provider.dart';
import 'constants.dart';

class Api {
  static final Api _i = Api._();
  factory Api() => _i;

  late final Dio dio;
  late final PersistCookieJar _jar;
  bool _initialized = false;

  String get baseUrl => AppConstants.baseUrl;

  Api._();

  Future<void> init() async {
    if (_initialized) return;
    final dir = await getApplicationDocumentsDirectory();
    _jar = PersistCookieJar(storage: FileStorage('${dir.path}/.cookies/'));
    dio = Dio(BaseOptions(
      baseUrl: AppConstants.baseUrl,
      connectTimeout: const Duration(seconds: 30),
      receiveTimeout: const Duration(minutes: 10),
      sendTimeout: const Duration(minutes: 10),
      followRedirects: false,
      validateStatus: (s) => s != null && s < 500,
    ));
    dio.interceptors.add(CookieManager(_jar));
    _initialized = true;
  }

  Future<bool> isLoggedIn() async {
    try {
      final r = await dio.get('/api/user_info');
      return r.data['status'] == 'success';
    } catch (_) { return false; }
  }

  Future<Map<String, dynamic>> login(String u, String p) async {
    try {
      final r = await dio.post('/login',
        data: {'username': u, 'password': p},
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

  Future<Map<String, dynamic>> userInfo() async {
    final r = await dio.get('/api/user_info');
    return r.data as Map<String, dynamic>;
  }

  Future<List<dynamic>> history() async {
    final r = await dio.get('/api/my_history');
    return r.data as List<dynamic>;
  }

  Future<Map<String, dynamic>> jobStatus(String id) async {
    final r = await dio.get('/status/$id');
    return r.data as Map<String, dynamic>;
  }

  Future<String> uploadInit(String name, int chunks, int size) async {
    final r = await dio.post('/upload-init',
      data: {'filename': name, 'total_chunks': chunks, 'file_size': size},
      options: Options(contentType: 'application/json'));
    if (r.data['status'] != 'success') throw r.data['message'] ?? 'Init failed';
    return r.data['upload_id'] as String;
  }

  Future<void> uploadChunk(String id, int idx, List<int> bytes) async {
    await dio.post('/upload-chunk',
      data: FormData.fromMap({
        'upload_id': id,
        'chunk_index': idx.toString(),
        'chunk': MultipartFile.fromBytes(bytes, filename: 'chunk_$idx'),
      }));
  }

  Future<String> uploadComplete(String id) async {
    final r = await dio.post('/upload-complete',
      data: {'upload_id': id},
      options: Options(contentType: 'application/json'));
    if (r.data['status'] != 'success') throw r.data['message'] ?? 'Complete failed';
    return r.data['filename'] as String;
  }

  Future<Map<String, dynamic>> downloadUrl(String url) async {
    final r = await dio.post('/download-from-url',
      data: {'url': url},
      options: Options(contentType: Headers.formUrlEncodedContentType));
    return r.data as Map<String, dynamic>;
  }

  Future<Map<String, dynamic>> processVideo(Map<String, String> d) async {
    final r = await dio.post('/process', data: d,
      options: Options(contentType: Headers.formUrlEncodedContentType));
    return r.data as Map<String, dynamic>;
  }

  Future<Map<String, dynamic>> processSubtitle(Map<String, String> d) async {
    final r = await dio.post('/process-subtitles', data: d,
      options: Options(contentType: Headers.formUrlEncodedContentType));
    return r.data as Map<String, dynamic>;
  }

  Future<String> reAnalyze(String fname) async {
    try {
      final r = await dio.post('/re-analyze',
        data: {'filename': fname},
        options: Options(contentType: Headers.formUrlEncodedContentType));
      return r.data['translated_text'] ?? '';
    } catch (_) { return ''; }
  }

  String streamUrl(String path) {
    if (path.startsWith('http')) return path;
    final fname = path.split('/').last;
    return '$baseUrl/stream-file/$fname';
  }

  String resolveUrl(String path) {
    if (path.startsWith('http')) return path;
    return '$baseUrl$path';
  }
}
