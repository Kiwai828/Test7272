import 'dart:io';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'api_client.dart';
import 'constants.dart';
import 'state.dart';

class Uploader {
  static Future<String> upload(File file, WidgetRef ref) async {
    final bytes = await file.readAsBytes();
    final rawName = file.path.split('/').last;
    // Sanitize filename - only keep alphanumeric, dots, underscores
    final name = rawName.replaceAll(RegExp(r'[^\w\.\-]'), '_');
    final size = bytes.length;
    final cs = AppConstants.chunkSize;
    final total = (size / cs).ceil().clamp(1, 9999);

    ref.read(uploadProvider.notifier)
        .update(active: true, total: total, chunk: 0, progress: 0);

    try {
      // Step 1: Init session
      final id = await Api().uploadInit(name, total, size);

      // Step 2: Upload chunks
      for (int i = 0; i < total; i++) {
        final start = i * cs;
        final end = (start + cs).clamp(0, size);
        await Api().uploadChunk(id, i, bytes.sublist(start, end));
        ref.read(uploadProvider.notifier)
            .update(chunk: i + 1, progress: (i + 1) / total, active: true);
      }

      // Step 3: Complete
      final fname = await Api().uploadComplete(id);
      ref.read(uploadProvider.notifier).reset();
      return fname;
    } catch (e) {
      ref.read(uploadProvider.notifier).reset();
      rethrow;
    }
  }
}
