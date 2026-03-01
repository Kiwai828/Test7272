import 'dart:async';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:file_picker/file_picker.dart';
import 'package:video_player/video_player.dart';
import 'theme.dart';
import 'state.dart';
import 'api_client.dart';
import 'upload_helper.dart';
import 'widgets.dart';

class DashboardScreen extends ConsumerStatefulWidget {
  const DashboardScreen({super.key});
  @override
  ConsumerState<DashboardScreen> createState() => _DS();
}

class _DS extends ConsumerState<DashboardScreen> with SingleTickerProviderStateMixin {
  final _api = Api();
  final _urlCtrl = TextEditingController();
  final _wmCtrl = TextEditingController();
  final _aiCtrl = TextEditingController();

  String? _vfname;
  String? _previewUrl;

  // Effects
  bool _flip = false, _speed = false, _pitch = false, _noise = false, _blur = false;
  // Watermark
  bool _wmScroll = false, _wmBox = false;
  int _wmSize = 24;
  Color _wmColor = Colors.white, _wmBoxColor = Colors.black;
  double _wmBoxOp = 0.5;
  String _wmPos = 'bottom_center';
  // Logo
  File? _logoFile;
  // AI
  String _voice = 'male';

  String _msg = '';
  bool _processing = false, _urlLoad = false;
  String? _jobId;
  Timer? _poll;

  late AnimationController _entryCtrl;
  late Animation<double> _entryFade;
  late Animation<Offset> _entrySlide;

  @override
  void initState() {
    super.initState();
    _entryCtrl = AnimationController(vsync: this, duration: const Duration(milliseconds: 500));
    _entryFade = CurvedAnimation(parent: _entryCtrl, curve: Curves.easeOut);
    _entrySlide = Tween(begin: const Offset(0, 0.04), end: Offset.zero)
        .animate(CurvedAnimation(parent: _entryCtrl, curve: Curves.easeOutCubic));
    _entryCtrl.forward();
  }

  @override
  void dispose() {
    _poll?.cancel();
    _entryCtrl.dispose();
    _urlCtrl.dispose();
    _wmCtrl.dispose();
    _aiCtrl.dispose();
    super.dispose();
  }

  Future<void> _pickVideo() async {
    final r = await FilePicker.platform.pickFiles(type: FileType.video);
    if (r?.files.single.path == null) return;
    setState(() { _msg = 'Upload လုပ်နေသည်...'; _vfname = null; _previewUrl = null; });
    try {
      final f = await Uploader.upload(File(r!.files.single.path!), ref);
      final url = '${_api.baseUrl}/stream-file/$f';
      setState(() { _vfname = f; _previewUrl = url; _msg = '✅ Video တင်ပြီးပြီ'; });
      _reAnalyze();
    } catch (e) {
      setState(() => _msg = '❌ $e');
    }
  }

  Future<void> _fromUrl() async {
    final url = _urlCtrl.text.trim();
    if (url.isEmpty) return;
    setState(() { _urlLoad = true; _msg = 'Download ဆွဲနေသည်...'; _previewUrl = null; });
    try {
      final r = await _api.downloadUrl(url);
      if (r['status'] == 'success') {
        final fname = r['filename'] as String;
        final surl = '${_api.baseUrl}/stream-file/$fname';
        setState(() { _vfname = fname; _previewUrl = surl; _msg = '✅ Download ပြီးပြီ'; });
        _reAnalyze();
      } else {
        setState(() => _msg = '❌ ${r['message']}');
      }
    } catch (e) {
      setState(() => _msg = '❌ $e');
    }
    setState(() => _urlLoad = false);
  }

  Future<void> _reAnalyze() async {
    if (_vfname == null) return;
    try {
      final t = await _api.reAnalyze(_vfname!);
      if (t.isNotEmpty && mounted) setState(() => _aiCtrl.text = t);
    } catch (_) {}
  }

  Future<void> _process() async {
    if (_vfname == null) { _snack('Video ဦးစွာ ရွေးပါ', C.rose); return; }
    setState(() { _processing = true; _msg = '⏳ Queue ထဲ သွင်းနေသည်...'; });
    try {
      final d = <String, String>{
        'video_filename': _vfname!, 'blur_areas': '[]',
        'logo_x': '0', 'logo_y': '0', 'logo_w': '0', 'logo_h': '0',
        if (_flip) 'bypass_flip': 'on',
        if (_speed) 'bypass_speed': 'on',
        if (_pitch) 'bypass_pitch': 'on',
        if (_noise) 'bypass_noise': 'on',
        if (_blur) 'blur_enabled': 'on',
        if (_wmCtrl.text.isNotEmpty) 'text_watermark_text': _wmCtrl.text,
        'text_watermark_size': _wmSize.toString(),
        'text_watermark_color': _hex(_wmColor),
        'text_watermark_position': _wmPos,
        if (_wmScroll) 'text_watermark_scroll': 'on',
        if (_wmBox) 'text_watermark_box': 'on',
        'text_watermark_box_color': _hex(_wmBoxColor),
        'text_watermark_box_opacity': _wmBoxOp.toString(),
        if (_aiCtrl.text.isNotEmpty) 'ai_text': _aiCtrl.text,
        'voice_gender': _voice,
      };
      final r = await _api.processVideo(d);
      if (r['status'] == 'queued') {
        _jobId = r['job_id'];
        if (r['new_balance'] != null) {
          ref.read(userProvider.notifier).updateCoins(r['new_balance'], r['free_left'] ?? 0);
        }
        setState(() => _msg = '⏳ Processing...');
        _startPoll();
      } else {
        setState(() { _msg = '❌ ${r['message']}'; _processing = false; });
      }
    } catch (e) {
      setState(() { _msg = '❌ $e'; _processing = false; });
    }
  }

  void _startPoll() {
    _poll?.cancel();
    _poll = Timer.periodic(AppConstants.pollInterval, (_) async {
      if (_jobId == null) return;
      try {
        final d = await _api.jobStatus(_jobId!);
        if (d['status'] == 'completed') {
          _poll?.cancel();
          HapticFeedback.heavyImpact();
          if (mounted) setState(() { _processing = false; _msg = '✅ ပြီးဆုံးပြီ!'; });
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

  void _snack(String msg, Color c) => ScaffoldMessenger.of(context).showSnackBar(
    SnackBar(content: Text(msg, style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w600)),
      backgroundColor: c, behavior: SnackBarBehavior.floating,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      margin: const EdgeInsets.all(16)));

  String _hex(Color c) => '#${c.value.toRadixString(16).padLeft(8, '0').substring(2).toUpperCase()}';

  @override
  Widget build(BuildContext context) {
    final user = ref.watch(userProvider);
    return Scaffold(
      backgroundColor: C.bg0,
      body: FadeTransition(opacity: _entryFade,
        child: SlideTransition(position: _entrySlide,
          child: CustomScrollView(slivers: [
            // AppBar
            SliverAppBar(
              floating: true, backgroundColor: C.bg0,
              title: Row(children: [
                Container(width: 32, height: 32,
                  decoration: BoxDecoration(gradient: const LinearGradient(colors: C.grad1), borderRadius: BorderRadius.circular(10)),
                  child: const Icon(Icons.video_camera_back_rounded, color: Colors.white, size: 16)),
                const SizedBox(width: 10),
                const Text('Recap Maker', style: TextStyle(color: C.t1, fontWeight: FontWeight.w800, fontSize: 17)),
              ]),
              actions: [
                user.when(
                  data: (u) => GestureDetector(
                    onTap: () => showModalBottomSheet(
                      context: context, backgroundColor: C.bg2, isScrollControlled: true,
                      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(28))),
                      builder: (_) => TopupSheet(user: u)),
                    child: Container(
                      margin: const EdgeInsets.only(right: 6),
                      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                      decoration: BoxDecoration(
                        color: C.gold.withOpacity(0.12), borderRadius: BorderRadius.circular(20),
                        border: Border.all(color: C.gold.withOpacity(0.25))),
                      child: Row(children: [
                        const Icon(Icons.monetization_on_rounded, color: C.gold, size: 14),
                        const SizedBox(width: 5),
                        Text('${u.coins}', style: const TextStyle(color: C.gold, fontWeight: FontWeight.w800, fontSize: 13)),
                      ]))),
                  loading: () => const SizedBox(width: 60),
                  error: (_, __) => const SizedBox()),
              ]),

            SliverToBoxAdapter(child: Padding(
              padding: const EdgeInsets.fromLTRB(16, 4, 16, 100),
              child: Column(children: [

                // Broadcast
                user.when(
                  data: (u) => u.broadcast != null ? _broadcast(u.broadcast!) : const SizedBox(),
                  loading: () => const SizedBox(), error: (_, __) => const SizedBox()),

                const SizedBox(height: 12),

                // Video source card
                _buildVideoSource(),

                // ── LIVE VIDEO PREVIEW ─────────────────
                if (_previewUrl != null) ...[
                  const SizedBox(height: 12),
                  LiveVideoPreview(
                    url: _previewUrl!,
                    blurEnabled: _blur,
                    logoFile: _logoFile,
                  ),
                ],

                const SizedBox(height: 12),

                // Effects
                GlassCard(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  const SectionHdr('EFFECTS', icon: Icons.tune_rounded),
                  ToggleRow(label: 'Video ကိုလှန်မည်', icon: Icons.flip_rounded, value: _flip, onChanged: (v) => setState(() => _flip = v)),
                  ToggleRow(label: 'Speed မြန်မည် (1.05×)', icon: Icons.speed_rounded, value: _speed, onChanged: (v) => setState(() => _speed = v)),
                  ToggleRow(label: 'အသံပြောင်းမည် (Copyright bypass)', icon: Icons.music_note_rounded, value: _pitch, color: C.cyan, onChanged: (v) => setState(() => _pitch = v)),
                  ToggleRow(label: 'Noise/Grain ထည့်မည်', icon: Icons.grain_rounded, value: _noise, onChanged: (v) => setState(() => _noise = v)),
                  ToggleRow(label: 'Blur နေရာဝှက်မည်', icon: Icons.blur_on_rounded, value: _blur, color: C.rose, onChanged: (v) => setState(() => _blur = v)),
                ])),

                const SizedBox(height: 12),

                // Watermark
                GlassCard(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  const SectionHdr('WATERMARK', icon: Icons.text_fields_rounded),
                  _textField(_wmCtrl, 'Channel အမည် ထည့်ပါ', Icons.edit_rounded),
                  const SizedBox(height: 14),
                  const Text('နေရာ', style: TextStyle(color: C.t2, fontSize: 11, fontWeight: FontWeight.w700, letterSpacing: 0.5)),
                  const SizedBox(height: 8),
                  PositionGrid(value: _wmPos, onChanged: (v) => setState(() => _wmPos = v)),
                  const SizedBox(height: 12),
                  Row(children: [
                    Expanded(child: _colorSwatch('စာလုံးအရောင်', _wmColor, (c) => setState(() => _wmColor = c))),
                    const SizedBox(width: 10),
                    Expanded(child: _sizeSlider('Size', _wmSize.toDouble(), 12, 72, (v) => setState(() => _wmSize = v.round()))),
                  ]),
                  const SizedBox(height: 8),
                  ToggleRow(label: 'ရွေ့လျားစေမည်', icon: Icons.swap_horiz_rounded, value: _wmScroll, onChanged: (v) => setState(() => _wmScroll = v)),
                  ToggleRow(label: 'နောက်ခံဘောင်', icon: Icons.crop_square_rounded, value: _wmBox, onChanged: (v) => setState(() => _wmBox = v)),
                  if (_wmBox) ...[
                    const SizedBox(height: 8),
                    Row(children: [
                      Expanded(child: _colorSwatch('ဘောင်အရောင်', _wmBoxColor, (c) => setState(() => _wmBoxColor = c))),
                      const SizedBox(width: 10),
                      Expanded(child: _sizeSlider('Opacity', _wmBoxOp, 0.1, 1.0, (v) => setState(() => _wmBoxOp = v))),
                    ]),
                  ],
                ])),

                const SizedBox(height: 12),

                // Logo
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

                const SizedBox(height: 12),

                // AI Voice
                GlassCard(
                  borderColor: C.violet.withOpacity(0.3),
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Row(children: [
                      const Expanded(child: SectionHdr('AI VOICE', icon: Icons.smart_toy_rounded)),
                      GestureDetector(
                        onTap: _reAnalyze,
                        child: Container(
                          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                          decoration: BoxDecoration(
                            color: C.violet.withOpacity(0.1), borderRadius: BorderRadius.circular(8),
                            border: Border.all(color: C.violet.withOpacity(0.2))),
                          child: const Row(children: [
                            Icon(Icons.refresh_rounded, color: C.violet, size: 12),
                            SizedBox(width: 4),
                            Text('AI ပြန်ယူ', style: TextStyle(color: C.violet, fontSize: 11, fontWeight: FontWeight.w700)),
                          ]))),
                    ]),
                    TextField(
                      controller: _aiCtrl, maxLines: 4,
                      style: const TextStyle(color: C.t1, fontSize: 13, height: 1.5),
                      decoration: InputDecoration(
                        hintText: 'AI Script ထည့်ပါ...',
                        hintStyle: const TextStyle(color: C.t3, fontSize: 12),
                        filled: true, fillColor: C.glass2,
                        contentPadding: const EdgeInsets.all(12),
                        border: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: const BorderSide(color: C.bdr)),
                        enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: const BorderSide(color: C.bdr)),
                        focusedBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: const BorderSide(color: C.violet)),
                      )),
                    const SizedBox(height: 10),
                    Row(children: [
                      Expanded(child: _voiceChip('male', '👨 အမျိုးသား')),
                      const SizedBox(width: 8),
                      Expanded(child: _voiceChip('female', '👩 အမျိုးသမီး')),
                    ]),
                  ])),

                const SizedBox(height: 20),

                // Free badge
                user.when(
                  data: (u) => u.freeLeft > 0 ? _freeBadge(u.freeLeft) : const SizedBox(),
                  loading: () => const SizedBox(), error: (_, __) => const SizedBox()),

                const SizedBox(height: 10),

                GradBtn(
                  label: '✨  စတင်ပြုပြင်မည်',
                  icon: Icons.auto_fix_high_rounded,
                  colors: C.grad1,
                  loading: _processing,
                  onTap: _vfname == null ? null : _process,
                ),
              ]),
            )),
          ]))),
    );
  }

  Widget _buildVideoSource() {
    return GlassCard(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      const SectionHdr('VIDEO SOURCE', icon: Icons.video_file_rounded),
      OutlineBtn(
        label: 'Video File တင်ရန်',
        icon: Icons.cloud_upload_rounded,
        color: C.violet,
        onTap: _processing ? null : _pickVideo),
      const SizedBox(height: 12),
      Row(children: [
        const Expanded(child: Divider(color: C.bdr)),
        const Padding(padding: EdgeInsets.symmetric(horizontal: 12),
          child: Text('သို့မဟုတ်', style: TextStyle(color: C.t3, fontSize: 11))),
        const Expanded(child: Divider(color: C.bdr)),
      ]),
      const SizedBox(height: 12),
      Row(children: [
        Expanded(child: _textField(_urlCtrl, 'YouTube / TikTok / Facebook Link', Icons.link_rounded)),
        const SizedBox(width: 8),
        GestureDetector(
          onTap: _urlLoad ? null : _fromUrl,
          child: Container(
            width: 44, height: 44,
            decoration: BoxDecoration(gradient: const LinearGradient(colors: C.grad1), borderRadius: BorderRadius.circular(12)),
            child: _urlLoad
              ? const Center(child: SizedBox(width: 16, height: 16, child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2)))
              : const Icon(Icons.download_rounded, color: Colors.white, size: 20))),
      ]),

      // Upload progress
      Consumer(builder: (_, ref, __) {
        final up = ref.watch(uploadProvider);
        if (up.active) return Padding(
          padding: const EdgeInsets.only(top: 12),
          child: Column(children: [
            ClipRRect(
              borderRadius: BorderRadius.circular(4),
              child: LinearProgressIndicator(
                value: up.progress, backgroundColor: C.bdr, minHeight: 6,
                valueColor: const AlwaysStoppedAnimation(C.violet))),
            const SizedBox(height: 4),
            Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
              Text('${up.chunk}/${up.total} chunks', style: const TextStyle(color: C.t3, fontSize: 10)),
              Text('${(up.progress * 100).toInt()}%', style: const TextStyle(color: C.violet, fontSize: 11, fontWeight: FontWeight.w700)),
            ]),
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
    ]));
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
          const SizedBox(width: 14, height: 14, child: CircularProgressIndicator(color: C.violet, strokeWidth: 2)),
          const SizedBox(width: 8),
        ],
        Expanded(child: Text(msg, style: TextStyle(color: isOk ? C.mint : isErr ? C.rose : C.t2, fontSize: 12))),
      ]));
  }

  Widget _broadcast(String msg) => Container(
    width: double.infinity, margin: const EdgeInsets.only(bottom: 4),
    padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
    decoration: BoxDecoration(
      color: C.violet.withOpacity(0.08), borderRadius: BorderRadius.circular(14),
      border: Border.all(color: C.violet.withOpacity(0.2))),
    child: Row(children: [
      const Icon(Icons.campaign_rounded, color: C.violet, size: 15),
      const SizedBox(width: 8),
      Expanded(child: Text(msg, style: const TextStyle(color: C.t1, fontSize: 12))),
    ]));

  Widget _freeBadge(int n) => Container(
    width: double.infinity, margin: const EdgeInsets.only(bottom: 4),
    padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 9),
    decoration: BoxDecoration(
      color: C.mint.withOpacity(0.07), borderRadius: BorderRadius.circular(12),
      border: Border.all(color: C.mint.withOpacity(0.2))),
    child: Row(children: [
      const Icon(Icons.card_giftcard_rounded, color: C.mint, size: 15),
      const SizedBox(width: 8),
      Text('အခမဲ့ ပြုလုပ်ခွင့်: $n ကြိမ် ကျန်သေးသည်',
        style: const TextStyle(color: C.mint, fontSize: 12, fontWeight: FontWeight.w700)),
    ]));

  Widget _textField(TextEditingController ctrl, String hint, IconData icon) {
    return Container(
      decoration: BoxDecoration(color: C.glass2, borderRadius: BorderRadius.circular(12), border: Border.all(color: C.bdr)),
      child: TextField(
        controller: ctrl,
        style: const TextStyle(color: C.t1, fontSize: 13),
        decoration: InputDecoration(
          hintText: hint, hintStyle: const TextStyle(color: C.t3, fontSize: 12),
          prefixIcon: Icon(icon, color: C.t3, size: 16),
          border: InputBorder.none,
          contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12))));
  }

  Widget _colorSwatch(String label, Color cur, ValueChanged<Color> cb) {
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Text(label, style: const TextStyle(color: C.t2, fontSize: 11, fontWeight: FontWeight.w600)),
      const SizedBox(height: 6),
      GestureDetector(
        onTap: () => _pickColor(cur, cb),
        child: Container(height: 38,
          decoration: BoxDecoration(color: cur, borderRadius: BorderRadius.circular(10), border: Border.all(color: C.bdr)))),
    ]);
  }

  Widget _sizeSlider(String label, double val, double min, double max, ValueChanged<double> cb) {
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Text('$label: ${val.toStringAsFixed(val < 10 ? 1 : 0)}',
        style: const TextStyle(color: C.t2, fontSize: 11, fontWeight: FontWeight.w600)),
      SliderTheme(
        data: SliderThemeData(trackHeight: 2, thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 7)),
        child: Slider(value: val, min: min, max: max, activeColor: C.violet, inactiveColor: C.t3.withOpacity(0.3), onChanged: cb)),
    ]);
  }

  Widget _voiceChip(String g, String label) {
    final on = _voice == g;
    return GestureDetector(
      onTap: () => setState(() => _voice = g),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        padding: const EdgeInsets.symmetric(vertical: 10),
        decoration: BoxDecoration(
          gradient: on ? const LinearGradient(colors: C.grad1) : null,
          color: on ? null : C.glass2,
          borderRadius: BorderRadius.circular(10),
          border: Border.all(color: on ? Colors.transparent : C.bdr),
          boxShadow: on ? [BoxShadow(color: C.violet.withOpacity(0.3), blurRadius: 10)] : []),
        child: Center(child: Text(label, style: TextStyle(
          color: on ? Colors.white : C.t2, fontSize: 13,
          fontWeight: on ? FontWeight.w700 : FontWeight.w400)))));
  }

  Future<void> _pickColor(Color cur, ValueChanged<Color> cb) async {
    final colors = [Colors.white, Colors.yellow, const Color(0xFFFF4070), const Color(0xFF00E299),
      const Color(0xFF7C5CFC), const Color(0xFF00D9C6), Colors.orange, Colors.black];
    final picked = await showDialog<Color>(context: context, builder: (_) => AlertDialog(
      backgroundColor: C.bg2,
      title: const Text('အရောင်ရွေးရန်', style: TextStyle(color: C.t1, fontSize: 16, fontWeight: FontWeight.w700)),
      content: Wrap(spacing: 10, runSpacing: 10,
        children: colors.map((c) => GestureDetector(
          onTap: () => Navigator.pop(context, c),
          child: AnimatedContainer(duration: const Duration(milliseconds: 150),
            width: 46, height: 46,
            decoration: BoxDecoration(color: c, borderRadius: BorderRadius.circular(12),
              border: Border.all(color: c == cur ? C.violet : C.bdr, width: c == cur ? 2.5 : 1))))).toList())));
    if (picked != null) cb(picked);
  }
}

// ══════════════════════════════════════════════════
// LIVE VIDEO PREVIEW — with drag blur + logo overlay
// ══════════════════════════════════════════════════
class LiveVideoPreview extends StatefulWidget {
  final String url;
  final bool blurEnabled;
  final File? logoFile;
  const LiveVideoPreview({super.key, required this.url, required this.blurEnabled, this.logoFile});
  @override
  State<LiveVideoPreview> createState() => _LVPState();
}

class _LVPState extends State<LiveVideoPreview> {
  VideoPlayerController? _vpc;
  bool _ready = false;
  double _aspectRatio = 9 / 16;

  // Blur box drag
  Offset _blurPos = const Offset(40, 80);
  Size _blurSize = const Size(120, 70);

  // Logo drag
  Offset _logoPos = const Offset(10, 10);
  Size _logoSize = const Size(80, 80);

  @override
  void initState() {
    super.initState();
    _initPlayer();
  }

  Future<void> _initPlayer() async {
    try {
      _vpc = VideoPlayerController.networkUrl(Uri.parse(widget.url));
      await _vpc!.initialize();
      if (mounted) {
        setState(() {
          _ready = true;
          final v = _vpc!.value;
          if (v.size.width > 0 && v.size.height > 0) {
            _aspectRatio = v.size.width / v.size.height;
          }
        });
        _vpc!.play();
        _vpc!.setLooping(true);
      }
    } catch (_) {
      if (mounted) setState(() => _ready = false);
    }
  }

  @override
  void dispose() { _vpc?.dispose(); super.dispose(); }

  @override
  void didUpdateWidget(LiveVideoPreview old) {
    super.didUpdateWidget(old);
    if (old.url != widget.url) {
      _vpc?.dispose();
      _ready = false;
      _initPlayer();
    }
  }

  @override
  Widget build(BuildContext context) {
    return GlassCard(
      padding: const EdgeInsets.all(10),
      borderColor: C.violet.withOpacity(0.3),
      child: Column(children: [
        Row(children: [
          const Icon(Icons.live_tv_rounded, color: C.violet, size: 13),
          const SizedBox(width: 6),
          const Text('LIVE PREVIEW', style: TextStyle(color: C.violet, fontSize: 11, fontWeight: FontWeight.w800, letterSpacing: 1)),
          const Spacer(),
          if (widget.blurEnabled)
            Container(padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 3),
              decoration: BoxDecoration(color: C.rose.withOpacity(0.1), borderRadius: BorderRadius.circular(6),
                border: Border.all(color: C.rose.withOpacity(0.3))),
              child: const Text('DRAG BLUR', style: TextStyle(color: C.rose, fontSize: 9, fontWeight: FontWeight.w800))),
          if (widget.logoFile != null) ...[
            const SizedBox(width: 6),
            Container(padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 3),
              decoration: BoxDecoration(color: C.cyan.withOpacity(0.1), borderRadius: BorderRadius.circular(6),
                border: Border.all(color: C.cyan.withOpacity(0.3))),
              child: const Text('DRAG LOGO', style: TextStyle(color: C.cyan, fontSize: 9, fontWeight: FontWeight.w800))),
          ],
        ]),
        const SizedBox(height: 8),

        // Video container — ratio-aware
        AspectRatio(
          aspectRatio: _aspectRatio,
          child: ClipRRect(
            borderRadius: BorderRadius.circular(12),
            child: LayoutBuilder(builder: (ctx, box) {
              final w = box.maxWidth;
              final h = box.maxHeight;
              return Stack(clipBehavior: Clip.hardEdge, children: [
                // Video
                if (_ready)
                  VideoPlayer(_vpc!)
                else
                  Container(color: C.bg1,
                    child: const Center(child: CircularProgressIndicator(color: C.violet, strokeWidth: 2))),

                // Blur overlay box (draggable)
                if (widget.blurEnabled)
                  Positioned(
                    left: _blurPos.dx.clamp(0, w - _blurSize.width),
                    top: _blurPos.dy.clamp(0, h - _blurSize.height),
                    child: GestureDetector(
                      onPanUpdate: (d) => setState(() {
                        _blurPos = Offset(
                          (_blurPos.dx + d.delta.dx).clamp(0, w - _blurSize.width),
                          (_blurPos.dy + d.delta.dy).clamp(0, h - _blurSize.height));
                      }),
                      child: Container(
                        width: _blurSize.width, height: _blurSize.height,
                        decoration: BoxDecoration(
                          color: Colors.black45,
                          borderRadius: BorderRadius.circular(6),
                          border: Border.all(color: C.rose.withOpacity(0.8), width: 1.5)),
                        child: const Center(
                          child: Icon(Icons.blur_on_rounded, color: C.rose, size: 20)),
                      ))),

                // Logo overlay (draggable)
                if (widget.logoFile != null)
                  Positioned(
                    left: _logoPos.dx.clamp(0, w - _logoSize.width),
                    top: _logoPos.dy.clamp(0, h - _logoSize.height),
                    child: GestureDetector(
                      onPanUpdate: (d) => setState(() {
                        _logoPos = Offset(
                          (_logoPos.dx + d.delta.dx).clamp(0, w - _logoSize.width),
                          (_logoPos.dy + d.delta.dy).clamp(0, h - _logoSize.height));
                      }),
                      child: Container(
                        width: _logoSize.width, height: _logoSize.height,
                        decoration: BoxDecoration(
                          border: Border.all(color: C.cyan.withOpacity(0.6), width: 1.5),
                          borderRadius: BorderRadius.circular(4)),
                        child: ClipRRect(
                          borderRadius: BorderRadius.circular(3),
                          child: Image.file(widget.logoFile!, fit: BoxFit.contain))))),

                // Play/pause tap
                Positioned.fill(child: GestureDetector(
                  behavior: HitTestBehavior.translucent,
                  onTap: () {
                    if (_vpc == null) return;
                    setState(() => _vpc!.value.isPlaying ? _vpc!.pause() : _vpc!.play());
                  })),

                // Pause icon hint
                if (_ready)
                  Positioned(bottom: 6, right: 6,
                    child: ValueListenableBuilder(valueListenable: _vpc!, builder: (_, v, __) =>
                      AnimatedOpacity(
                        opacity: v.isPlaying ? 0.0 : 0.8,
                        duration: const Duration(milliseconds: 200),
                        child: Container(
                          padding: const EdgeInsets.all(4),
                          decoration: BoxDecoration(color: Colors.black54, borderRadius: BorderRadius.circular(6)),
                          child: const Icon(Icons.play_arrow_rounded, color: Colors.white, size: 16))))),
              ]);
            })),
        ),

        // Controls row
        if (_ready) Padding(
          padding: const EdgeInsets.only(top: 8),
          child: ValueListenableBuilder(valueListenable: _vpc!, builder: (_, v, __) {
            final pos = v.position.inMilliseconds.toDouble();
            final dur = v.duration.inMilliseconds.toDouble();
            return Column(children: [
              SliderTheme(
                data: SliderThemeData(
                  trackHeight: 2,
                  thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 5),
                  activeTrackColor: C.violet, inactiveTrackColor: C.bdr, thumbColor: Colors.white,
                  overlayShape: SliderComponentShape.noOverlay),
                child: Slider(
                  value: dur > 0 ? (pos / dur).clamp(0.0, 1.0) : 0,
                  onChanged: (val) => _vpc!.seekTo(Duration(milliseconds: (val * dur).toInt())))),
              Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
                Text(_fmt(v.position), style: const TextStyle(color: C.t3, fontSize: 10)),
                Text(_fmt(v.duration), style: const TextStyle(color: C.t3, fontSize: 10)),
              ]),
            ]);
          })),
      ]));
  }

  String _fmt(Duration d) {
    final m = d.inMinutes.remainder(60).toString().padLeft(2, '0');
    final s = d.inSeconds.remainder(60).toString().padLeft(2, '0');
    return '$m:$s';
  }
}
