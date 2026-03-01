import 'dart:io';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'api_client.dart';
import 'constants.dart';
import 'state.dart';

class Uploader {
  static Future<String> upload(File file, WidgetRef ref) async {
    final bytes = await file.readAsBytes();
    final name = file.path.split('/').last
        .replaceAll(RegExp(r'[^\w\.\-]'), '_'); // sanitize filename
    final size = bytes.length;
    final cs = AppConstants.chunkSize;
    final total = (size / cs).ceil().clamp(1, 9999);

    ref.read(uploadProvider.notifier)
        .update(active: true, total: total, chunk: 0, progress: 0);

    // Step 1: init
    final id = await Api().uploadInit(name, total, size);

    // Step 2: chunks
    for (int i = 0; i < total; i++) {
      final start = i * cs;
      final end = (start + cs).clamp(0, size);
      await Api().uploadChunk(id, i, bytes.sublist(start, end));
      final progress = (i + 1) / total;
      ref.read(uploadProvider.notifier)
          .update(chunk: i + 1, progress: progress, active: true);
    }

    // Step 3: complete
    final fname = await Api().uploadComplete(id);
    ref.read(uploadProvider.notifier).reset();
    return fname;
  }
}
