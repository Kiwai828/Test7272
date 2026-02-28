import 'package:dio/dio.dart';
import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio_cookie_manager/dio_cookie_manager.dart';
import 'constants.dart';

class Api {
  static final Api _i = Api._();
  factory Api() => _i;

  late final Dio dio;
  final _jar = CookieJar();

  Api._() {
    dio = Dio(BaseOptions(
      baseUrl: AppConstants.baseUrl,
      connectTimeout: const Duration(seconds: 30),
      receiveTimeout: const Duration(minutes: 10),
      sendTimeout: const Duration(minutes: 10),
    ));
    dio.interceptors.add(CookieManager(_jar));
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
      data: {'filename': name, 'total_chunks': chunks, 'file_size': size});
    if (r.data['status'] != 'success') throw r.data['message'];
    return r.data['upload_id'] as String;
  }

  Future<void> uploadChunk(String id, int idx, List<int> bytes) async {
    await dio.post('/upload-chunk', data: FormData.fromMap({
      'upload_id': id,
      'chunk_index': idx.toString(),
      'chunk': MultipartFile.fromBytes(bytes, filename: 'c$idx'),
    }));
  }

  Future<String> uploadComplete(String id) async {
    final r = await dio.post('/upload-complete', data: {'upload_id': id});
    if (r.data['status'] != 'success') throw r.data['message'];
    return r.data['filename'] as String;
  }

  Future<Map<String, dynamic>> downloadUrl(String url) async {
    final r = await dio.post('/download-from-url', data: {'url': url});
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
    final r = await dio.post('/re-analyze', data: {'filename': fname},
      options: Options(contentType: Headers.formUrlEncodedContentType));
    return r.data['translated_text'] ?? '';
  }

  String streamUrl(String path) {
    if (path.startsWith('http')) return path;
    // Use /stream-file/ for video preview
    final fname = path.split('/').last;
    return '${AppConstants.baseUrl}/stream-file/$fname';
  }

  String resolveUrl(String path) {
    if (path.startsWith('http')) return path;
    return '${AppConstants.baseUrl}$path';
  }
}
