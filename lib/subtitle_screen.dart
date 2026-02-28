import 'dart:async';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:file_picker/file_picker.dart';
import 'theme.dart';
import 'state.dart';
import 'api_client.dart';
import 'upload_helper.dart';
import 'widgets.dart';

class SubtitleScreen extends ConsumerStatefulWidget {
  const SubtitleScreen({super.key});
  @override
  ConsumerState<SubtitleScreen> createState() => _SS();
}

class _SS extends ConsumerState<SubtitleScreen> with SingleTickerProviderStateMixin {
  final _api = Api();
  final _urlCtrl = TextEditingController();
  String? _vfname;
  File? _logoFile;

  bool _flip = false, _noise = false;
  String _subPos = 'bottom_center';
  int _fontSize = 16;
  Color _fontColor = Colors.white, _boxColor = Colors.black;
  bool _boxEnabled = false;
  double _boxOp = 0.5;

  String _msg = '';
  bool _processing = false, _urlLoad = false;
  String? _jobId;
  Timer? _poll;

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
  }

  @override
  void dispose() { _poll?.cancel(); _ctrl.dispose(); _urlCtrl.dispose(); super.dispose(); }

  Future<void> _pickVideo() async {
    final r = await FilePicker.platform.pickFiles(type: FileType.video);
    if (r?.files.single.path == null) return;
    setState(() { _msg = 'Upload လုပ်နေသည်...'; _vfname = null; });
    try {
      final f = await Uploader.upload(File(r!.files.single.path!), ref);
      setState(() { _vfname = f; _msg = '✅ Video တင်ပြီးပြီ'; });
    } catch (e) { setState(() => _msg = '❌ $e'); }
  }

  Future<void> _fromUrl() async {
    final url = _urlCtrl.text.trim();
    if (url.isEmpty) return;
    setState(() { _urlLoad = true; _msg = 'Download ဆွဲနေသည်...'; });
    try {
      final r = await _api.downloadUrl(url);
      if (r['status'] == 'success') {
        setState(() { _vfname = r['filename']; _msg = '✅ Download ပြီးပြီ'; });
      } else { setState(() => _msg = '❌ ${r['message']}'); }
    } catch (e) { setState(() => _msg = '❌ $e'); }
    setState(() => _urlLoad = false);
  }

  Future<void> _process() async {
    if (_vfname == null) { _snack('Video ဦးစွာ ရွေးပါ'); return; }
    setState(() { _processing = true; _msg = '⏳ AI Subtitle ထိုးနေသည်...'; });
    try {
      final d = <String, String>{
        'video_filename': _vfname!, 'blur_areas': '[]',
        'logo_x': '0', 'logo_y': '0', 'logo_w': '0', 'logo_h': '0',
        'subtitle_position': _subPos,
        'subtitle_font_size': _fontSize.toString(),
        'subtitle_font_color': _hex(_fontColor),
        'subtitle_box_color': _hex(_boxColor),
        'subtitle_box_opacity': _boxOp.toString(),
        if (_boxEnabled) 'subtitle_box_enabled': 'on',
        if (_flip) 'bypass_flip': 'on',
        if (_noise) 'bypass_noise': 'on',
      };
      final r = await _api.processSubtitle(d);
      if (r['status'] == 'queued') {
        _jobId = r['job_id'];
        if (r['new_balance'] != null) {
          ref.read(userProvider.notifier).updateCoins(r['new_balance'], r['free_left'] ?? 0);
        }
        setState(() => _msg = '⏳ AI subtitle ထိုးနေသည်...');
        _startPoll();
      } else {
        setState(() { _msg = '❌ ${r['message']}'; _processing = false; });
      }
    } catch (e) { setState(() { _msg = '❌ $e'; _processing = false; }); }
  }

  void _startPoll() {
    _poll?.cancel();
    _poll = Timer.periodic(const Duration(seconds: 4), (_) async {
      if (_jobId == null) return;
      try {
        final d = await _api.jobStatus(_jobId!);
        if (d['status'] == 'completed') {
          _poll?.cancel();
          HapticFeedback.heavyImpact();
          if (mounted) setState(() { _processing = false; _msg = '✅ Subtitle ထိုးပြီးဆုံးပြီ!'; });
          ref.read(userProvider.notifier).load();
          ref.read(historyProvider.notifier).load();
          _showDone(_api.streamUrl(d['url'] ?? ''));
        } else if (d['status'] == 'failed') {
          _poll?.cancel();
          if (mounted) setState(() { _processing = false; _msg = '❌ ${d['message'] ?? 'Failed'}'; });
        } else {
          if (mounted) setState(() => _msg = '⏳ ${d['message'] ?? 'Processing...'}');
        }
      } catch (_) {}
    });
  }

  void _showDone(String url) => showModalBottomSheet(
    context: context, backgroundColor: C.bg2, isScrollControlled: true,
    shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(28))),
    builder: (_) => DoneSheet(url: url));

  void _snack(String msg) => ScaffoldMessenger.of(context).showSnackBar(
    SnackBar(content: Text(msg), backgroundColor: C.rose, behavior: SnackBarBehavior.floating,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)), margin: const EdgeInsets.all(16)));

  String _hex(Color c) => '#${c.value.toRadixString(16).padLeft(8, '0').substring(2).toUpperCase()}';

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: C.bg0,
      body: FadeTransition(opacity: _fade, child: SlideTransition(position: _slide,
        child: CustomScrollView(slivers: [
          SliverAppBar(floating: true, backgroundColor: C.bg0,
            title: Row(children: [
              Container(width: 32, height: 32,
                decoration: BoxDecoration(gradient: const LinearGradient(colors: C.grad2), borderRadius: BorderRadius.circular(10)),
                child: const Icon(Icons.closed_caption_rounded, color: Colors.white, size: 16)),
              const SizedBox(width: 10),
              const Text('Auto Subtitle', style: TextStyle(color: C.t1, fontWeight: FontWeight.w800, fontSize: 17)),
            ])),

          SliverToBoxAdapter(child: Padding(
            padding: const EdgeInsets.fromLTRB(16, 4, 16, 100),
            child: Column(children: [

              // Video source
              GlassCard(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                const SectionHdr('VIDEO SOURCE', icon: Icons.video_file_rounded, color: C.cyan),
                OutlineBtn(label: 'Video File တင်ရန်', icon: Icons.cloud_upload_rounded,
                  color: C.cyan, onTap: _processing ? null : _pickVideo),
                const SizedBox(height: 12),
                Row(children: [
                  const Expanded(child: Divider(color: C.bdr)),
                  Padding(padding: const EdgeInsets.symmetric(horizontal: 12),
                    child: const Text('သို့မဟုတ်', style: TextStyle(color: C.t3, fontSize: 11))),
                  const Expanded(child: Divider(color: C.bdr)),
                ]),
                const SizedBox(height: 12),
                Row(children: [
                  Expanded(child: _textField(_urlCtrl, 'YouTube / TikTok / Facebook Link')),
                  const SizedBox(width: 8),
                  GestureDetector(
                    onTap: _urlLoad ? null : _fromUrl,
                    child: Container(width: 44, height: 44,
                      decoration: BoxDecoration(gradient: const LinearGradient(colors: C.grad2), borderRadius: BorderRadius.circular(12)),
                      child: _urlLoad
                        ? const Center(child: SizedBox(width: 16, height: 16, child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2)))
                        : const Icon(Icons.download_rounded, color: Colors.white, size: 20))),
                ]),

                // Progress / status
                Consumer(builder: (_, ref, __) {
                  final up = ref.watch(uploadProvider);
                  if (up.active) return Padding(
                    padding: const EdgeInsets.only(top: 12),
                    child: Column(children: [
                      ClipRRect(borderRadius: BorderRadius.circular(4),
                        child: LinearProgressIndicator(value: up.progress, backgroundColor: C.bdr, minHeight: 5,
                          valueColor: const AlwaysStoppedAnimation(C.cyan))),
                      const SizedBox(height: 4),
                      Text('${(up.progress * 100).toInt()}% — ${up.chunk}/${up.total}',
                        style: const TextStyle(color: C.t3, fontSize: 10)),
                    ]));
                  return const SizedBox();
                }),

                if (_msg.isNotEmpty && !ref.watch(uploadProvider).active) ...[
                  const SizedBox(height: 10),
                  _statusBadge(_msg),
                ],

                if (_vfname != null) ...[
                  const SizedBox(height: 8),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                    decoration: BoxDecoration(color: C.mint.withOpacity(0.07), borderRadius: BorderRadius.circular(8)),
                    child: Row(children: [
                      const Icon(Icons.check_circle_rounded, color: C.mint, size: 13),
                      const SizedBox(width: 6),
                      Expanded(child: Text(_vfname!, style: const TextStyle(color: C.t2, fontSize: 11), overflow: TextOverflow.ellipsis)),
                    ])),
                ],
              ])),

              const SizedBox(height: 12),

              GlassCard(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                const SectionHdr('EFFECTS', icon: Icons.tune_rounded, color: C.cyan),
                ToggleRow(label: 'Video ကိုလှန်မည်', icon: Icons.flip_rounded, value: _flip, onChanged: (v) => setState(() => _flip = v), color: C.cyan),
                ToggleRow(label: 'Noise/Grain ထည့်မည်', icon: Icons.grain_rounded, value: _noise, onChanged: (v) => setState(() => _noise = v), color: C.cyan),
              ])),

              const SizedBox(height: 12),

              // Subtitle settings
              GlassCard(borderColor: C.cyan.withOpacity(0.3),
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  const SectionHdr('SUBTITLE STYLE', icon: Icons.closed_caption_rounded, color: C.cyan),

                  const Text('နေရာ', style: TextStyle(color: C.t2, fontSize: 11, fontWeight: FontWeight.w700, letterSpacing: 0.5)),
                  const SizedBox(height: 8),
                  PositionGrid(value: _subPos, onChanged: (v) => setState(() => _subPos = v)),
                  const SizedBox(height: 14),

                  Row(children: [
                    const Expanded(child: Text('Font Size', style: TextStyle(color: C.t2, fontSize: 11, fontWeight: FontWeight.w600))),
                    Text('$_fontSize', style: const TextStyle(color: C.cyan, fontWeight: FontWeight.w800, fontSize: 13)),
                  ]),
                  SliderTheme(
                    data: SliderThemeData(trackHeight: 2, thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 7),
                      activeTrackColor: C.cyan, inactiveTrackColor: C.t3.withOpacity(0.2), thumbColor: C.cyan),
                    child: Slider(value: _fontSize.toDouble(), min: 8, max: 48, onChanged: (v) => setState(() => _fontSize = v.round()))),

                  const SizedBox(height: 8),
                  Row(children: [
                    const Expanded(child: Text('Font Color', style: TextStyle(color: C.t2, fontSize: 11, fontWeight: FontWeight.w600))),
                    GestureDetector(
                      onTap: () => _pickColor(_fontColor, (c) => setState(() => _fontColor = c)),
                      child: Container(width: 32, height: 24,
                        decoration: BoxDecoration(color: _fontColor, borderRadius: BorderRadius.circular(6), border: Border.all(color: C.bdr)))),
                  ]),

                  const SizedBox(height: 12),
                  ToggleRow(label: 'နောက်ခံဘောင် ထည့်မည်', icon: Icons.crop_square_rounded,
                    value: _boxEnabled, color: C.cyan, onChanged: (v) => setState(() => _boxEnabled = v)),

                  if (_boxEnabled) ...[
                    const SizedBox(height: 8),
                    Row(children: [
                      Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                        const Text('ဘောင်အရောင်', style: TextStyle(color: C.t2, fontSize: 11, fontWeight: FontWeight.w600)),
                        const SizedBox(height: 6),
                        GestureDetector(
                          onTap: () => _pickColor(_boxColor, (c) => setState(() => _boxColor = c)),
                          child: Container(height: 36,
                            decoration: BoxDecoration(color: _boxColor, borderRadius: BorderRadius.circular(10), border: Border.all(color: C.bdr)))),
                      ])),
                      const SizedBox(width: 12),
                      Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                        Text('Opacity: ${_boxOp.toStringAsFixed(1)}', style: const TextStyle(color: C.t2, fontSize: 11, fontWeight: FontWeight.w600)),
                        SliderTheme(
                          data: SliderThemeData(trackHeight: 2, thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 7),
                            activeTrackColor: C.cyan, inactiveTrackColor: C.t3.withOpacity(0.2), thumbColor: C.cyan),
                          child: Slider(value: _boxOp, min: 0.1, max: 1.0, divisions: 9, onChanged: (v) => setState(() => _boxOp = v))),
                      ])),
                    ]),
                  ],
                ])),

              const SizedBox(height: 12),

              GlassCard(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                const SectionHdr('LOGO', icon: Icons.image_rounded, color: C.cyan),
                OutlineBtn(
                  label: _logoFile != null ? 'Logo ပြောင်းရန်' : 'Logo ထည့်ရန်',
                  icon: Icons.add_photo_alternate_rounded, color: C.cyan,
                  onTap: () async {
                    final r = await FilePicker.platform.pickFiles(type: FileType.image);
                    if (r?.files.single.path != null) setState(() => _logoFile = File(r!.files.single.path!));
                  }),
                if (_logoFile != null) Padding(
                  padding: const EdgeInsets.only(top: 8),
                  child: Text('✅ ${_logoFile!.path.split('/').last}', style: const TextStyle(color: C.mint, fontSize: 11))),
              ])),

              const SizedBox(height: 20),

              // Free badge
              ref.watch(userProvider).whenOrNull(data: (u) => u.freeLeft > 0 ? Container(
                width: double.infinity, margin: const EdgeInsets.only(bottom: 10),
                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 9),
                decoration: BoxDecoration(color: C.mint.withOpacity(0.07), borderRadius: BorderRadius.circular(12), border: Border.all(color: C.mint.withOpacity(0.2))),
                child: Row(children: [
                  const Icon(Icons.card_giftcard_rounded, color: C.mint, size: 15),
                  const SizedBox(width: 8),
                  Text('အခမဲ့ ပြုလုပ်ခွင့်: ${u.freeLeft} ကြိမ် ကျန်သေးသည်',
                    style: const TextStyle(color: C.mint, fontSize: 12, fontWeight: FontWeight.w700)),
                ])) : null) ?? const SizedBox(),

              GradBtn(
                label: '🎬  စာတန်းထိုးပြီး ပြုပြင်မည်',
                icon: Icons.closed_caption_rounded,
                colors: C.grad2,
                loading: _processing,
                onTap: _vfname == null ? null : _process,
              ),

              const SizedBox(height: 32),
            ]),
          )),
        ]),
      )),
    );
  }

  Widget _textField(TextEditingController ctrl, String hint) {
    return Container(
      decoration: BoxDecoration(color: C.glass2, borderRadius: BorderRadius.circular(12), border: Border.all(color: C.bdr)),
      child: TextField(controller: ctrl, style: const TextStyle(color: C.t1, fontSize: 13),
        decoration: InputDecoration(hintText: hint, hintStyle: const TextStyle(color: C.t3, fontSize: 12),
          prefixIcon: const Icon(Icons.link_rounded, color: C.t3, size: 16),
          border: InputBorder.none, contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12))));
  }

  Widget _statusBadge(String msg) {
    final isOk = msg.startsWith('✅');
    final isErr = msg.startsWith('❌');
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: isOk ? C.mint.withOpacity(0.07) : isErr ? C.rose.withOpacity(0.07) : C.glass2,
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: isOk ? C.mint.withOpacity(0.2) : isErr ? C.rose.withOpacity(0.2) : C.bdr)),
      child: Row(children: [
        if (_processing) ...[
          const SizedBox(width: 14, height: 14, child: CircularProgressIndicator(color: C.cyan, strokeWidth: 2)),
          const SizedBox(width: 8),
        ],
        Expanded(child: Text(msg, style: TextStyle(color: isOk ? C.mint : isErr ? C.rose : C.t2, fontSize: 12))),
      ]));
  }

  Future<void> _pickColor(Color cur, ValueChanged<Color> cb) async {
    final colors = [Colors.white, Colors.yellow, C.rose, C.mint, C.violet, C.cyan, Colors.orange, Colors.black];
    final p = await showDialog<Color>(context: context, builder: (_) => AlertDialog(
      backgroundColor: C.bg2,
      title: const Text('အရောင်ရွေးရန်', style: TextStyle(color: C.t1, fontSize: 16, fontWeight: FontWeight.w700)),
      content: Wrap(spacing: 10, runSpacing: 10,
        children: colors.map((c) => GestureDetector(
          onTap: () => Navigator.pop(context, c),
          child: Container(width: 46, height: 46,
            decoration: BoxDecoration(color: c, borderRadius: BorderRadius.circular(12),
              border: Border.all(color: c == cur ? C.cyan : C.bdr, width: c == cur ? 2.5 : 1))))).toList())));
    if (p != null) cb(p);
  }
}
