import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'theme.dart';
import 'api_client.dart';
import 'models.dart';
import 'providers.dart';
import 'widgets.dart';

class HistoryScreen extends ConsumerWidget {
  const HistoryScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final histAsync = ref.watch(historyProvider);
    return Scaffold(
      backgroundColor: AppTheme.bg,
      body: CustomScrollView(slivers: [
        SliverAppBar(
          pinned: true,
          backgroundColor: AppTheme.bg,
          title: const Row(children: [
            Icon(CupertinoIcons.clock_fill, color: AppTheme.gold, size: 20),
            SizedBox(width: 8),
            Text('ပြုလုပ်ပြီးသော Videos', style: TextStyle(color: AppTheme.textHi, fontWeight: FontWeight.w800, fontSize: 18)),
          ]),
          actions: [
            IconButton(
              icon: const Icon(CupertinoIcons.refresh, color: AppTheme.textMid, size: 20),
              onPressed: () => ref.refresh(historyProvider),
            ),
          ],
        ),
        SliverToBoxAdapter(
          child: histAsync.when(
            data: (items) => items.isEmpty
              ? _emptyState()
              : ListView.builder(
                  shrinkWrap: true,
                  physics: const NeverScrollableScrollPhysics(),
                  padding: const EdgeInsets.all(16),
                  itemCount: items.length,
                  itemBuilder: (_, i) => _HistoryCard(item: items[i]),
                ),
            loading: () => const Padding(
              padding: EdgeInsets.all(80),
              child: Center(child: CircularProgressIndicator(color: AppTheme.primary, strokeWidth: 2)),
            ),
            error: (e, _) => Padding(
              padding: const EdgeInsets.all(40),
              child: Center(child: Text('Error: $e', style: const TextStyle(color: AppTheme.red))),
            ),
          ),
        ),
      ]),
    );
  }

  Widget _emptyState() => Padding(
    padding: const EdgeInsets.all(60),
    child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
      const Icon(CupertinoIcons.film, color: AppTheme.textLow, size: 60),
      const SizedBox(height: 16),
      const Text('History မရှိသေးပါ', style: TextStyle(color: AppTheme.textMid, fontSize: 16, fontWeight: FontWeight.w600)),
      const SizedBox(height: 8),
      const Text('Video ပြုပြင်ပြီးနောက် ဒီနေရာမှာ ပေါ်လာမည်', style: TextStyle(color: AppTheme.textLow, fontSize: 13), textAlign: TextAlign.center),
    ]),
  );
}

class _HistoryCard extends StatelessWidget {
  final HistoryItem item;
  const _HistoryCard({required this.item});

  @override
  Widget build(BuildContext context) {
    final api = ApiClient();
    Color statusColor;
    IconData statusIcon;
    String statusText;

    if (item.isDone) {
      statusColor = AppTheme.green; statusIcon = Icons.check_circle_rounded; statusText = 'ပြီးဆုံးပြီ';
    } else if (item.isFailed) {
      statusColor = AppTheme.red; statusIcon = Icons.error_rounded; statusText = 'မအောင်မြင်ပါ';
    } else {
      statusColor = AppTheme.gold; statusIcon = Icons.hourglass_top_rounded; statusText = 'Processing...';
    }

    // Expiry
    String? expiryText;
    if (item.isDone && item.secondsLeft > 0) {
      final hours = item.secondsLeft ~/ 3600;
      final mins = (item.secondsLeft % 3600) ~/ 60;
      expiryText = '$hours နာရီ $mins မိနစ် ကျန်သေးသည်';
    }

    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppTheme.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: item.isDone ? AppTheme.green.withOpacity(0.2)
          : item.isFailed ? AppTheme.red.withOpacity(0.2)
          : AppTheme.border),
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          Container(
            width: 36, height: 36,
            decoration: BoxDecoration(
              color: statusColor.withOpacity(0.1), borderRadius: BorderRadius.circular(10)),
            child: Icon(statusIcon, color: statusColor, size: 18),
          ),
          const SizedBox(width: 12),
          Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text('Job: ${item.jobId.length > 12 ? item.jobId.substring(0, 12) : item.jobId}...',
              style: const TextStyle(color: AppTheme.textHi, fontWeight: FontWeight.w700, fontSize: 14)),
            const SizedBox(height: 2),
            Text(item.createdAt, style: const TextStyle(color: AppTheme.textLow, fontSize: 12)),
          ])),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
            decoration: BoxDecoration(
              color: statusColor.withOpacity(0.1), borderRadius: BorderRadius.circular(20)),
            child: Text(statusText, style: TextStyle(color: statusColor, fontSize: 11, fontWeight: FontWeight.w700)),
          ),
        ]),

        if (item.isFailed && item.errorMessage.isNotEmpty) ...[
          const SizedBox(height: 10),
          Container(
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              color: AppTheme.red.withOpacity(0.06), borderRadius: BorderRadius.circular(10)),
            child: Text(item.errorMessage, style: const TextStyle(color: AppTheme.red, fontSize: 12)),
          ),
        ],

        if (item.isDone && item.secondsLeft > 0) ...[
          const SizedBox(height: 12),
          if (expiryText != null)
            Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: Row(children: [
                const Icon(Icons.access_time_rounded, color: AppTheme.textLow, size: 12),
                const SizedBox(width: 4),
                Text('သက်တမ်း: $expiryText', style: const TextStyle(color: AppTheme.textLow, fontSize: 11)),
              ]),
            ),
          SizedBox(
            width: double.infinity,
            child: OutlinedButton.icon(
              onPressed: () => showModalBottomSheet(
                context: context, backgroundColor: AppTheme.surface2,
                shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(24))),
                builder: (_) => DownloadSheet(url: api.resolveUrl(item.filePath))),
              style: OutlinedButton.styleFrom(
                foregroundColor: AppTheme.green,
                side: const BorderSide(color: AppTheme.green),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                padding: const EdgeInsets.symmetric(vertical: 10),
              ),
              icon: const Icon(Icons.download_rounded, size: 16),
              label: const Text('Download Video', style: TextStyle(fontWeight: FontWeight.w700)),
            ),
          ),
        ],
      ]),
    );
  }
}
