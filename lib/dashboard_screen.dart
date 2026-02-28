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
  File? _logoFile;

  // Effects
  bool _flip = false, _speed = false, _pitch = false, _noise = false, _blur = false;
  // Watermark
  bool _wmScroll = false, _wmBox = false;
  int _wmSize = 24;
  Color _wmColor = Colors.white, _wmBoxColor = Colors.black;
  double _wmBoxOp = 0.5;
  String _wmPos = 'bottom_center';
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
  void dispose() { _poll?.cancel(); _entryCtrl.dispose(); _urlCtrl.dispose(); _wmCtrl.dispose(); _aiCtrl.dispose(); super.dispose(); }

  Future<void> _pickVideo() async {
    final r = await FilePicker.platform.pickFiles(type: FileType.video);
    if (r?.files.single.path == null) return;
    setState(() { _msg = 'Upload လုပ်နေသည်...'; _vfname = null; });
    try {
      final f = await Uploader.upload(File(r!.files.single.path!), ref);
      setState(() { _vfname = f; _msg = '✅ Video တင်ပြီးပြီ'; });
      _reAnalyze();
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
        _reAnalyze();
      } else { setState(() => _msg = '❌ ${r['message']}'); }
    } catch (e) { setState(() => _msg = '❌ $e'); }
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
        if (_flip) 'bypass_flip': 'on', if (_speed) 'bypass_speed': 'on',
        if (_pitch) 'bypass_pitch': 'on', if (_noise) 'bypass_noise': 'on',
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
            // ── App Bar ─────────────────────────────
            SliverAppBar(
              floating: true,
              backgroundColor: C.bg0,
              title: Row(children: [
                Container(width: 32, height: 32,
                  decoration: BoxDecoration(
                    gradient: const LinearGradient(colors: C.grad1),
                    borderRadius: BorderRadius.circular(10)),
                  child: const Icon(Icons.video_camera_back_rounded, color: Colors.white, size: 16)),
                const SizedBox(width: 10),
                const Text('Recap Maker', style: TextStyle(color: C.t1, fontWeight: FontWeight.w800, fontSize: 17)),
              ]),
              actions: [
                // Coin badge
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
                        color: C.gold.withOpacity(0.12),
                        borderRadius: BorderRadius.circular(20),
                        border: Border.all(color: C.gold.withOpacity(0.25))),
                      child: Row(children: [
                        const Icon(Icons.monetization_on_rounded, color: C.gold, size: 14),
                        const SizedBox(width: 5),
                        Text('${u.coins}', style: const TextStyle(color: C.gold, fontWeight: FontWeight.w800, fontSize: 13)),
                      ]),
                    ),
                  ),
                  loading: () => const SizedBox(width: 60),
                  error: (_, __) => const SizedBox(),
                ),
              ],
            ),

            SliverToBoxAdapter(child: Padding(
              padding: const EdgeInsets.fromLTRB(16, 4, 16, 100),
              child: Column(children: [

                // Broadcast
                user.when(
                  data: (u) => u.broadcast != null ? _broadcast(u.broadcast!) : const SizedBox(),
                  loading: () => const SizedBox(), error: (_, __) => const SizedBox()),

                const SizedBox(height: 12),

                // ── Video Source Card ─────────────────
                _buildVideoSource(),

                const SizedBox(height: 12),

                // ── Effects Card ──────────────────────
                GlassCard(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  const SectionHdr('EFFECTS', icon: Icons.tune_rounded),
                  ToggleRow(label: 'Video ကိုလှန်မည်', icon: Icons.flip_rounded, value: _flip, onChanged: (v) => setState(() => _flip = v)),
                  ToggleRow(label: 'Speed မြန်မည် (1.05×)', icon: Icons.speed_rounded, value: _speed, onChanged: (v) => setState(() => _speed = v)),
                  ToggleRow(label: 'အသံပြောင်းမည် (Copyright bypass)', icon: Icons.music_note_rounded, value: _pitch, color: C.cyan, onChanged: (v) => setState(() => _pitch = v)),
                  ToggleRow(label: 'Noise/Grain ထည့်မည်', icon: Icons.grain_rounded, value: _noise, onChanged: (v) => setState(() => _noise = v)),
                  ToggleRow(label: 'Blur နေရာဝှက်မည်', icon: Icons.blur_on_rounded, value: _blur, color: C.rose, onChanged: (v) => setState(() => _blur = v)),
                ])),

                const SizedBox(height: 12),

                // ── Watermark Card ────────────────────
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

                // ── Logo Card ─────────────────────────
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
                    child: Text('✅ ${_logoFile!.path.split('/').last}',
                      style: const TextStyle(color: C.mint, fontSize: 11))),
                ])),

                const SizedBox(height: 12),

                // ── AI Voice Card ─────────────────────
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
                          ]),
                        ),
                      ),
                    ]),
                    TextField(
                      controller: _aiCtrl, maxLines: 4,
                      style: const TextStyle(color: C.t1, fontSize: 13, height: 1.5),
                      decoration: InputDecoration(
                        hintText: 'AI Script ထည့်ပါ...',
                        hintStyle: const TextStyle(color: C.t3, fontSize: 12),
                        filled: true, fillColor: C.glass2,
                        contentPadding: const EdgeInsets.all(12),
                        border: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: BorderSide(color: C.bdr)),
                        enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: BorderSide(color: C.bdr)),
                        focusedBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: const BorderSide(color: C.violet)),
                      ),
                    ),
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
          ]),
        ),
      ),
    );
  }

  Widget _buildVideoSource() {
    return GlassCard(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      const SectionHdr('VIDEO SOURCE', icon: Icons.video_file_rounded),

      OutlineBtn(label: 'Video File တင်ရန်', icon: Icons.cloud_upload_rounded,
        color: C.violet, onTap: _processing ? null : _pickVideo),

      const SizedBox(height: 12),
      Row(children: [
        const Expanded(child: Divider(color: C.bdr)),
        Padding(padding: const EdgeInsets.symmetric(horizontal: 12),
          child: Text('သို့မဟုတ်', style: const TextStyle(color: C.t3, fontSize: 11))),
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
              : const Icon(Icons.download_rounded, color: Colors.white, size: 20)),
        ),
      ]),

      // Upload progress
      Consumer(builder: (_, ref, __) {
        final up = ref.watch(uploadProvider);
        if (!up.active) {
          if (_msg.isEmpty) return const SizedBox();
          return _statusBadge(_msg);
        }
        return Column(children: [
          const SizedBox(height: 12),
          Row(children: [
            Expanded(child: ClipRRect(
              borderRadius: BorderRadius.circular(4),
              child: LinearProgressIndicator(
                value: up.progress,
                backgroundColor: C.bdr, minHeight: 5,
                valueColor: const AlwaysStoppedAnimation(C.violet)))),
            const SizedBox(width: 10),
            Text('${(up.progress * 100).toInt()}%',
              style: const TextStyle(color: C.violet, fontSize: 11, fontWeight: FontWeight.w700)),
          ]),
          const SizedBox(height: 4),
          Text('${up.chunk}/${up.total} chunks', style: const TextStyle(color: C.t3, fontSize: 10)),
        ]);
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
          ]),
        ),
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
        Expanded(child: Text(msg, style: TextStyle(
          color: isOk ? C.mint : isErr ? C.rose : C.t2, fontSize: 12))),
      ]),
    );
  }

  Widget _broadcast(String msg) => Container(
    width: double.infinity,
    margin: const EdgeInsets.only(bottom: 4),
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
    width: double.infinity,
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
          contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12)),
      ),
    );
  }

  Widget _colorSwatch(String label, Color cur, ValueChanged<Color> cb) {
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Text(label, style: const TextStyle(color: C.t2, fontSize: 11, fontWeight: FontWeight.w600)),
      const SizedBox(height: 6),
      GestureDetector(
        onTap: () => _pickColor(cur, cb),
        child: Container(height: 38,
          decoration: BoxDecoration(color: cur, borderRadius: BorderRadius.circular(10), border: Border.all(color: C.bdr))),
      ),
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
          fontWeight: on ? FontWeight.w700 : FontWeight.w400))),
      ),
    );
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
              border: Border.all(color: c == cur ? C.violet : C.bdr, width: c == cur ? 2.5 : 1))),
        )).toList()),
    ));
    if (picked != null) cb(picked);
  }
}
