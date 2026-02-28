import 'dart:io';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'api_client.dart';
import 'constants.dart';
import 'state.dart';

class Uploader {
  static Future<String> upload(File file, WidgetRef ref) async {
    final bytes = await file.readAsBytes();
    final name = file.path.split('/').last;
    final size = bytes.length;
    final cs = AppConstants.chunkSize;
    final total = (size / cs).ceil();

    ref.read(uploadProvider.notifier).update(active: true, total: total, chunk: 0, progress: 0);

    final id = await Api().uploadInit(name, total, size);

    for (int i = 0; i < total; i++) {
      final s = i * cs;
      final e = (s + cs).clamp(0, size);
      await Api().uploadChunk(id, i, bytes.sublist(s, e));
      ref.read(uploadProvider.notifier).update(chunk: i + 1, progress: (i + 1) / total, active: true);
    }

    final fname = await Api().uploadComplete(id);
    ref.read(uploadProvider.notifier).reset();
    return fname;
  }
}
