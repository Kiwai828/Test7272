import 'dart:io';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'api_client.dart';
import 'constants.dart';
import 'providers.dart';

class UploadHelper {
  static Future<String> uploadFile(File file, WidgetRef ref) async {
    final api = ApiClient();
    final bytes = await file.readAsBytes();
    final filename = file.path.split('/').last;
    final fileSize = bytes.length;
    final chunkSize = AppConstants.chunkSize;
    final total = (fileSize / chunkSize).ceil();

    ref.read(uploadProvider.notifier).update(
      uploading: true, total: total, chunk: 0, progress: 0,
      message: 'Upload session စတင်နေသည်...');

    final uploadId = await api.uploadInit(filename, total, fileSize);

    for (int i = 0; i < total; i++) {
      final start = i * chunkSize;
      final end = (start + chunkSize).clamp(0, fileSize);
      await api.uploadChunk(uploadId, i, bytes.sublist(start, end));
      ref.read(uploadProvider.notifier).update(
        chunk: i + 1, progress: (i + 1) / total,
        message: 'Upload: ${i + 1}/$total chunks');
    }

    ref.read(uploadProvider.notifier).update(message: 'ပြင်ဆင်နေသည်...');
    final sanitized = await api.uploadComplete(uploadId);
    ref.read(uploadProvider.notifier).reset();
    return sanitized;
  }
}
