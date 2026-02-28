import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:video_player/video_player.dart';
import 'theme.dart';
import 'state.dart';
import 'models.dart';
import 'api_client.dart';

class HistoryScreen extends ConsumerStatefulWidget {
  const HistoryScreen({super.key});
  @override
  ConsumerState<HistoryScreen> createState() => _HS();
}

class _HS extends ConsumerState<HistoryScreen> with SingleTickerProviderStateMixin {
  late AnimationController _ctrl;
  late Animation<double> _fade;
  late Animation<Offset> _slide;

  @override
  void initState() {
    super.initState();
    _ctrl = AnimationController(vsync: this, duration: const Duration(milliseconds: 500));
    _fade = CurvedAnimation(parent: _ctrl, curve: Curves.easeOut);
    _slide = Tween(begin: const Offset(0, 0.04), end: Offset.zero)
      .animate(CurvedAnimation(parent: _ctrl, curve: Curves.easeOutCubic));
    _ctrl.forward();
    // Load history when tab opens
    Future.microtask(() => ref.read(historyProvider.notifier).load());
  }

  @override
  void dispose() { _ctrl.dispose(); super.dispose(); }

  @override
  Widget build(BuildContext context) {
    final hist = ref.watch(historyProvider);
    return Scaffold(
      backgroundColor: C.bg0,
      body: FadeTransition(opacity: _fade, child: SlideTransition(position: _slide,
        child: CustomScrollView(slivers: [
          SliverAppBar(
            floating: true, backgroundColor: C.bg0,
            title: Row(children: [
              Container(width: 32, height: 32,
                decoration: BoxDecoration(
                  gradient: const LinearGradient(colors: C.gradGold),
                  borderRadius: BorderRadius.circular(10)),
                child: const Icon(CupertinoIcons.clock_fill, color: Colors.white, size: 15)),
              const SizedBox(width: 10),
              const Text('History', style: TextStyle(color: C.t1, fontWeight: FontWeight.w800, fontSize: 17)),
            ]),
            actions: [
              IconButton(
                icon: const Icon(CupertinoIcons.refresh, color: C.t2, size: 18),
                onPressed: () => ref.read(historyProvider.notifier).load()),
            ],
          ),

          SliverToBoxAdapter(child: hist.when(
            loading: () => const Padding(
              padding: EdgeInsets.all(80),
              child: Center(child: CircularProgressIndicator(color: C.violet, strokeWidth: 2))),
            error: (e, _) => Padding(
              padding: const EdgeInsets.all(40),
              child: Center(child: Text('Error: $e', style: const TextStyle(color: C.rose, fontSize: 13)))),
            data: (items) => items.isEmpty ? _empty() : Padding(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 100),
              child: Column(children: items.asMap().entries.map((e) =>
                _HistoryCard(item: e.value, index: e.key)).toList())))),
        ]),
      )),
    );
  }

  Widget _empty() => Padding(
    padding: const EdgeInsets.all(60),
    child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
      Container(width: 80, height: 80,
        decoration: BoxDecoration(color: C.glass, shape: BoxShape.circle),
        child: const Icon(CupertinoIcons.film, color: C.t3, size: 36)),
      const SizedBox(height: 20),
      const Text('History မရှိသေးပါ', style: TextStyle(color: C.t1, fontSize: 16, fontWeight: FontWeight.w700)),
      const SizedBox(height: 8),
      const Text('Video ပြုပြင်ပြီးနောက် ဒီနေရာမှာ ပေါ်လာမည်',
        style: TextStyle(color: C.t3, fontSize: 13), textAlign: TextAlign.center),
    ]));
}

class _HistoryCard extends StatefulWidget {
  final HistoryItem item;
  final int index;
  const _HistoryCard({required this.item, required this.index});
  @override
  State<_HistoryCard> createState() => _HCS();
}

class _HCS extends State<_HistoryCard> with SingleTickerProviderStateMixin {
  late AnimationController _entryCtrl;
  late Animation<double> _entryFade;
  late Animation<Offset> _entrySlide;

  @override
  void initState() {
    super.initState();
    _entryCtrl = AnimationController(vsync: this, duration: const Duration(milliseconds: 400));
    _entryFade = CurvedAnimation(parent: _entryCtrl, curve: Curves.easeOut);
    _entrySlide = Tween(begin: const Offset(0, 0.1), end: Offset.zero)
      .animate(CurvedAnimation(parent: _entryCtrl, curve: Curves.easeOutCubic));
    Future.delayed(Duration(milliseconds: widget.index * 60), () {
      if (mounted) _entryCtrl.forward();
    });
  }

  @override
  void dispose() { _entryCtrl.dispose(); super.dispose(); }

  @override
  Widget build(BuildContext context) {
    final item = widget.item;
    Color sc; IconData si; String st;
    if (item.isDone) { sc = C.mint; si = Icons.check_circle_rounded; st = 'ပြီးဆုံးပြီ'; }
    else if (item.isFailed) { sc = C.rose; si = Icons.error_rounded; st = 'မအောင်မြင်ပါ'; }
    else { sc = C.gold; si = Icons.hourglass_top_rounded; st = 'Processing...'; }

    String? expiry;
    if (item.isDone && item.secondsLeft > 0) {
      final h = item.secondsLeft ~/ 3600;
      final m = (item.secondsLeft % 3600) ~/ 60;
      expiry = '$h နာရီ $m မိနစ်';
    }

    return FadeTransition(opacity: _entryFade, child: SlideTransition(position: _entrySlide,
      child: Container(
        margin: const EdgeInsets.only(bottom: 10),
        decoration: BoxDecoration(
          color: C.bg2,
          borderRadius: BorderRadius.circular(18),
          border: Border.all(color: item.isDone ? C.mint.withOpacity(0.15)
            : item.isFailed ? C.rose.withOpacity(0.15) : C.bdr),
          boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.2), blurRadius: 12, offset: const Offset(0, 4))]),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Row(children: [
              Container(width: 38, height: 38,
                decoration: BoxDecoration(color: sc.withOpacity(0.1), borderRadius: BorderRadius.circular(11)),
                child: Icon(si, color: sc, size: 18)),
              const SizedBox(width: 12),
              Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text('Job #${item.jobId.length > 8 ? item.jobId.substring(0, 8) : item.jobId}...',
                  style: const TextStyle(color: C.t1, fontWeight: FontWeight.w700, fontSize: 14)),
                const SizedBox(height: 2),
                Text(item.createdAt, style: const TextStyle(color: C.t3, fontSize: 11)),
              ])),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(color: sc.withOpacity(0.1), borderRadius: BorderRadius.circular(20)),
                child: Text(st, style: TextStyle(color: sc, fontSize: 10, fontWeight: FontWeight.w800))),
            ]),

            if (item.isFailed && item.errorMsg.isNotEmpty) ...[
              const SizedBox(height: 10),
              Container(
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(color: C.rose.withOpacity(0.05), borderRadius: BorderRadius.circular(10)),
                child: Text(item.errorMsg, style: const TextStyle(color: C.rose, fontSize: 11))),
            ],

            if (item.isDone) ...[
              if (expiry != null) ...[
                const SizedBox(height: 8),
                Row(children: [
                  const Icon(Icons.access_time_rounded, color: C.t3, size: 11),
                  const SizedBox(width: 4),
                  Text('သက်တမ်း: $expiry ကျန်သေးသည်', style: const TextStyle(color: C.t3, fontSize: 11)),
                ]),
              ],
              const SizedBox(height: 12),
              // Video preview & download buttons
              Row(children: [
                Expanded(
                  child: GestureDetector(
                    onTap: () => _openPlayer(context, item),
                    child: Container(
                      padding: const EdgeInsets.symmetric(vertical: 10),
                      decoration: BoxDecoration(
                        color: C.violet.withOpacity(0.1), borderRadius: BorderRadius.circular(12),
                        border: Border.all(color: C.violet.withOpacity(0.25))),
                      child: const Row(mainAxisAlignment: MainAxisAlignment.center, children: [
                        Icon(Icons.play_circle_rounded, color: C.violet, size: 16),
                        SizedBox(width: 6),
                        Text('Preview', style: TextStyle(color: C.violet, fontWeight: FontWeight.w700, fontSize: 12)),
                      ]),
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: GestureDetector(
                    onTap: () => _copyLink(context, item),
                    child: Container(
                      padding: const EdgeInsets.symmetric(vertical: 10),
                      decoration: BoxDecoration(
                        gradient: const LinearGradient(colors: C.gradGold),
                        borderRadius: BorderRadius.circular(12),
                        boxShadow: [BoxShadow(color: C.gold.withOpacity(0.3), blurRadius: 8, offset: const Offset(0, 3))]),
                      child: const Row(mainAxisAlignment: MainAxisAlignment.center, children: [
                        Icon(Icons.download_rounded, color: Colors.white, size: 16),
                        SizedBox(width: 6),
                        Text('Download', style: TextStyle(color: Colors.white, fontWeight: FontWeight.w700, fontSize: 12)),
                      ]),
                    ),
                  ),
                ),
              ]),
            ],
          ]),
        ),
      ),
    ));
  }

  void _openPlayer(BuildContext ctx, HistoryItem item) {
    final url = Api().streamUrl(item.filePath);
    Navigator.of(ctx).push(PageRouteBuilder(
      opaque: false,
      barrierColor: Colors.black87,
      barrierDismissible: true,
      transitionDuration: const Duration(milliseconds: 350),
      pageBuilder: (_, __, ___) => _VideoPlayerPage(url: url),
      transitionsBuilder: (_, anim, __, child) => FadeTransition(
        opacity: anim,
        child: ScaleTransition(scale: Tween(begin: 0.92, end: 1.0)
          .animate(CurvedAnimation(parent: anim, curve: Curves.easeOutCubic)), child: child)),
    ));
  }

  void _copyLink(BuildContext ctx, HistoryItem item) {
    final url = Api().resolveUrl(item.filePath);
    Clipboard.setData(ClipboardData(text: url));
    HapticFeedback.lightImpact();
    ScaffoldMessenger.of(ctx).showSnackBar(SnackBar(
      content: const Text('Download link ကူးပြီးပြီ! Browser မှာ paste လုပ်ပါ',
        style: TextStyle(color: Colors.white, fontWeight: FontWeight.w600)),
      backgroundColor: C.mint, behavior: SnackBarBehavior.floating,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      margin: const EdgeInsets.all(16)));
  }
}

// ── Full Video Player Page ───────────────────────
class _VideoPlayerPage extends StatefulWidget {
  final String url;
  const _VideoPlayerPage({required this.url});
  @override
  State<_VideoPlayerPage> createState() => _VPS();
}

class _VPS extends State<_VideoPlayerPage> {
  late VideoPlayerController _vpc;
  bool _ready = false, _err = false;

  @override
  void initState() {
    super.initState();
    _vpc = VideoPlayerController.networkUrl(Uri.parse(widget.url))
      ..initialize().then((_) {
        if (mounted) { setState(() => _ready = true); _vpc.play(); }
      }).catchError((_) { if (mounted) setState(() => _err = true); });
    _vpc.setLooping(false);
  }

  @override
  void dispose() { _vpc.dispose(); super.dispose(); }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      body: Stack(children: [
        // Video
        if (_ready) Center(child: GestureDetector(
          onTap: () { setState(() {}); _vpc.value.isPlaying ? _vpc.pause() : _vpc.play(); },
          child: AspectRatio(aspectRatio: _vpc.value.aspectRatio, child: VideoPlayer(_vpc)))),

        if (!_ready && !_err) const Center(child: CircularProgressIndicator(color: C.violet, strokeWidth: 2.5)),
        if (_err) Center(child: Column(mainAxisSize: MainAxisSize.min, children: [
          const Icon(Icons.error_outline_rounded, color: C.rose, size: 48),
          const SizedBox(height: 12),
          const Text('Video ဖွင့်မရပါ', style: TextStyle(color: C.t1, fontSize: 16)),
          const SizedBox(height: 8),
          const Text('Browser မှာ download link ကို သုံးပါ', style: TextStyle(color: C.t2, fontSize: 12)),
        ])),

        // Controls overlay
        if (_ready) Positioned(bottom: 0, left: 0, right: 0,
          child: Container(
            padding: const EdgeInsets.fromLTRB(16, 20, 16, 40),
            decoration: BoxDecoration(
              gradient: LinearGradient(begin: Alignment.topCenter, end: Alignment.bottomCenter,
                colors: [Colors.transparent, Colors.black.withOpacity(0.8)])),
            child: Column(children: [
              // Progress bar
              ValueListenableBuilder(valueListenable: _vpc, builder: (_, v, __) {
                final pos = v.position.inMilliseconds.toDouble();
                final dur = v.duration.inMilliseconds.toDouble();
                return Column(children: [
                  SliderTheme(
                    data: SliderThemeData(
                      trackHeight: 3,
                      thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 6),
                      activeTrackColor: C.violet, inactiveTrackColor: Colors.white24, thumbColor: Colors.white),
                    child: Slider(
                      value: dur > 0 ? (pos / dur).clamp(0.0, 1.0) : 0,
                      onChanged: (val) => _vpc.seekTo(Duration(milliseconds: (val * dur).toInt())))),
                  Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
                    Text(_fmt(v.position), style: const TextStyle(color: Colors.white70, fontSize: 11)),
                    Text(_fmt(v.duration), style: const TextStyle(color: Colors.white70, fontSize: 11)),
                  ]),
                ]);
              }),
              const SizedBox(height: 8),
              // Play/pause
              GestureDetector(
                onTap: () { setState(() {}); _vpc.value.isPlaying ? _vpc.pause() : _vpc.play(); },
                child: Container(width: 52, height: 52,
                  decoration: BoxDecoration(gradient: const LinearGradient(colors: C.grad1), shape: BoxShape.circle,
                    boxShadow: [BoxShadow(color: C.violet.withOpacity(0.5), blurRadius: 16)]),
                  child: ValueListenableBuilder(valueListenable: _vpc, builder: (_, v, __) =>
                    Icon(v.isPlaying ? Icons.pause_rounded : Icons.play_arrow_rounded, color: Colors.white, size: 28)))),
            ]))),

        // Close button
        SafeArea(child: Padding(
          padding: const EdgeInsets.all(8),
          child: GestureDetector(
            onTap: () => Navigator.of(context).pop(),
            child: Container(width: 38, height: 38,
              decoration: BoxDecoration(color: Colors.black54, shape: BoxShape.circle),
              child: const Icon(Icons.close_rounded, color: Colors.white, size: 20))))),
      ]),
    );
  }

  String _fmt(Duration d) {
    final m = d.inMinutes.remainder(60).toString().padLeft(2, '0');
    final s = d.inSeconds.remainder(60).toString().padLeft(2, '0');
    return '$m:$s';
  }
}
