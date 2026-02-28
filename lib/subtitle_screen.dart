import 'dart:async';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:file_picker/file_picker.dart';
import 'theme.dart';
import 'api_client.dart';
import 'providers.dart';
import 'upload_helper.dart';
import 'widgets.dart';

class SubtitleScreen extends ConsumerStatefulWidget {
  const SubtitleScreen({super.key});
  @override
  ConsumerState<SubtitleScreen> createState() => _SubtitleScreenState();
}

class _SubtitleScreenState extends ConsumerState<SubtitleScreen> {
  final _api = ApiClient();
  final _urlCtrl = TextEditingController();

  String? _videoFilename;
  File? _logoFile;

  // Options
  bool _flip = false, _noise = false;
  String _subtitlePosition = 'bottom_center';
  int _fontSize = 16;
  Color _fontColor = Colors.white;
  bool _boxEnabled = false;
  Color _boxColor = Colors.black;
  double _boxOpacity = 0.5;

  // State
  String _statusMsg = '';
  bool _processing = false;
  bool _urlLoading = false;
  String? _jobId;
  Timer? _pollTimer;

  int _freeLeft = 0;

  @override
  void initState() { super.initState(); _loadUser(); }
  @override
  void dispose() { _pollTimer?.cancel(); _urlCtrl.dispose(); super.dispose(); }

  Future<void> _loadUser() async {
    try {
      final d = await _api.getUser();
      if (mounted) setState(() { _freeLeft = d['free_left'] ?? 0; });
    } catch (_) {}
  }

  Future<void> _pickVideo() async {
    final r = await FilePicker.platform.pickFiles(type: FileType.video, allowMultiple: false);
    if (r?.files.single.path == null) return;
    setState(() { _statusMsg = 'Upload လုပ်နေသည်...'; _videoFilename = null; });
    try {
      final fname = await UploadHelper.uploadFile(File(r!.files.single.path!), ref);
      setState(() { _videoFilename = fname; _statusMsg = '✅ Video တင်ပြီးပြီ'; });
    } catch (e) { setState(() => _statusMsg = '❌ $e'); }
  }

  Future<void> _downloadFromUrl() async {
    final url = _urlCtrl.text.trim();
    if (url.isEmpty) return;
    setState(() { _urlLoading = true; _statusMsg = 'Download ဆွဲနေသည်...'; });
    try {
      final res = await _api.downloadFromUrl(url);
      if (res['status'] == 'success') {
        setState(() { _videoFilename = res['filename']; _statusMsg = '✅ Download ပြီးပြီ'; });
      } else { setState(() => _statusMsg = '❌ ${res['message']}'); }
    } catch (e) { setState(() => _statusMsg = '❌ $e'); }
    setState(() => _urlLoading = false);
  }

  Future<void> _startProcess() async {
    if (_videoFilename == null) { _snack('Video file ဦးစွာ ရွေးပါ'); return; }
    setState(() { _processing = true; _statusMsg = '⏳ Subtitle Generate လုပ်နေသည်...'; });
    try {
      final data = <String, String>{
        'video_filename': _videoFilename!,
        'blur_areas': '[]',
        'logo_x': '0', 'logo_y': '0', 'logo_w': '0', 'logo_h': '0',
        'subtitle_position': _subtitlePosition,
        'subtitle_font_size': _fontSize.toString(),
        'subtitle_font_color': _colorHex(_fontColor),
        'subtitle_box_color': _colorHex(_boxColor),
        'subtitle_box_opacity': _boxOpacity.toString(),
        if (_boxEnabled) 'subtitle_box_enabled': 'on',
        if (_flip) 'bypass_flip': 'on',
        if (_noise) 'bypass_noise': 'on',
      };
      final res = await _api.processSubtitles(data);
      if (res['status'] == 'queued') {
        _jobId = res['job_id'];
        setState(() => _statusMsg = '⏳ Queue ထဲ ရောက်ပြီ၊ AI Subtitle ထိုးနေသည်...');
        _startPoll();
      } else {
        setState(() { _statusMsg = '❌ ${res['message']}'; _processing = false; });
      }
    } catch (e) { setState(() { _statusMsg = '❌ $e'; _processing = false; }); }
  }

  void _startPoll() {
    _pollTimer?.cancel();
    _pollTimer = Timer.periodic(const Duration(seconds: 4), (_) async {
      if (_jobId == null) return;
      try {
        final d = await _api.getJobStatus(_jobId!);
        if (d['status'] == 'completed') {
          _pollTimer?.cancel();
          HapticFeedback.heavyImpact();
          if (mounted) setState(() { _processing = false; _statusMsg = '✅ Subtitle ထိုးပြီးဆုံးပြီ!'; });
          _showDownload(_api.resolveUrl(d['url'] ?? ''));
          _loadUser();
        } else if (d['status'] == 'failed') {
          _pollTimer?.cancel();
          if (mounted) setState(() { _processing = false; _statusMsg = '❌ ${d['message'] ?? 'Failed'}'; });
        } else {
          if (mounted) setState(() => _statusMsg = '⏳ ${d['message'] ?? 'Processing...'}');
        }
      } catch (_) {}
    });
  }

  void _showDownload(String url) => showModalBottomSheet(
    context: context, backgroundColor: AppTheme.surface2,
    shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(24))),
    builder: (_) => DownloadSheet(url: url));

  void _snack(String msg) => ScaffoldMessenger.of(context).showSnackBar(
    SnackBar(content: Text(msg), backgroundColor: AppTheme.surface2,
      behavior: SnackBarBehavior.floating,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      margin: const EdgeInsets.all(16)));

  String _colorHex(Color c) => '#${c.value.toRadixString(16).padLeft(8, '0').substring(2).toUpperCase()}';

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.bg,
      body: CustomScrollView(slivers: [
        SliverAppBar(
          pinned: true,
          backgroundColor: AppTheme.bg,
          title: const Row(children: [
            Icon(Icons.closed_caption_rounded, color: AppTheme.teal, size: 22),
            SizedBox(width: 8),
            Text('Auto Subtitles', style: TextStyle(color: AppTheme.textHi, fontWeight: FontWeight.w800, fontSize: 18)),
          ]),
        ),
        SliverToBoxAdapter(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [

              // ── Video source ────────────────────────────
              AppCard(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                const SectionLabel('မူရင်း Video File', icon: Icons.video_file_rounded),
                const SizedBox(height: 12),
                _outlineBtn(icon: Icons.cloud_upload_rounded, label: 'Video File တင်ရန်',
                  color: AppTheme.teal, onTap: _processing ? null : _pickVideo),
                const SizedBox(height: 10),
                Row(children: [
                  const Expanded(child: Divider(color: AppTheme.border)),
                  Padding(padding: const EdgeInsets.symmetric(horizontal: 12),
                    child: Text('သို့မဟုတ်', style: TextStyle(color: AppTheme.textLow, fontSize: 12))),
                  const Expanded(child: Divider(color: AppTheme.border)),
                ]),
                const SizedBox(height: 10),
                Row(children: [
                  Expanded(
                    child: TextField(
                      controller: _urlCtrl,
                      style: const TextStyle(color: AppTheme.textHi, fontSize: 13),
                      decoration: InputDecoration(
                        hintText: 'YouTube, TikTok, Facebook Link',
                        hintStyle: const TextStyle(color: AppTheme.textLow, fontSize: 12),
                        filled: true, fillColor: AppTheme.surface2,
                        contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
                        border: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: const BorderSide(color: AppTheme.border)),
                        enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: const BorderSide(color: AppTheme.border)),
                        focusedBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: const BorderSide(color: AppTheme.teal)),
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  GestureDetector(
                    onTap: _urlLoading ? null : _downloadFromUrl,
                    child: Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(color: AppTheme.teal, borderRadius: BorderRadius.circular(12)),
                      child: _urlLoading
                        ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2))
                        : const Icon(Icons.link_rounded, color: Colors.white, size: 20),
                    ),
                  ),
                ]),
                if (_statusMsg.isNotEmpty) ...[
                  const SizedBox(height: 10),
                  Container(
                    padding: const EdgeInsets.all(10),
                    decoration: BoxDecoration(
                      color: _statusMsg.startsWith('✅') ? AppTheme.green.withOpacity(0.08)
                        : _statusMsg.startsWith('❌') ? AppTheme.red.withOpacity(0.08)
                        : AppTheme.surface2,
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: Row(children: [
                      if (_processing) ...[
                        const SizedBox(width: 16, height: 16,
                          child: CircularProgressIndicator(color: AppTheme.teal, strokeWidth: 2)),
                        const SizedBox(width: 10),
                      ],
                      Expanded(child: Text(_statusMsg, style: TextStyle(
                        color: _statusMsg.startsWith('✅') ? AppTheme.green
                          : _statusMsg.startsWith('❌') ? AppTheme.red
                          : AppTheme.textMid, fontSize: 12))),
                    ]),
                  ),
                  // Upload progress
                  Consumer(builder: (_, ref, __) {
                    final up = ref.watch(uploadProvider);
                    if (!up.uploading) return const SizedBox.shrink();
                    return Padding(
                      padding: const EdgeInsets.only(top: 8),
                      child: Column(children: [
                        ClipRRect(borderRadius: BorderRadius.circular(4),
                          child: LinearProgressIndicator(value: up.progress,
                            backgroundColor: AppTheme.border,
                            valueColor: const AlwaysStoppedAnimation(AppTheme.teal), minHeight: 6)),
                        const SizedBox(height: 4),
                        Text('${(up.progress * 100).toStringAsFixed(0)}% — ${up.chunk}/${up.total} chunks',
                          style: const TextStyle(color: AppTheme.textMid, fontSize: 11)),
                      ]),
                    );
                  }),
                ],
                if (_videoFilename != null) ...[
                  const SizedBox(height: 8),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                    decoration: BoxDecoration(color: AppTheme.green.withOpacity(0.08), borderRadius: BorderRadius.circular(8)),
                    child: Row(children: [
                      const Icon(Icons.check_circle_rounded, color: AppTheme.green, size: 14),
                      const SizedBox(width: 6),
                      Expanded(child: Text(_videoFilename!,
                        style: const TextStyle(color: AppTheme.textMid, fontSize: 11), overflow: TextOverflow.ellipsis)),
                    ]),
                  ),
                ],
              ])),

              const SizedBox(height: 12),

              // ── Effects ──────────────────────────────────
              AppCard(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                const SectionLabel('Effect များ', icon: Icons.tune_rounded),
                AppToggleRow(label: 'Video ကိုလှန်မည်', icon: Icons.flip_rounded, value: _flip, onChanged: (v) => setState(() => _flip = v)),
                AppToggleRow(label: 'Noise/Grain ထည့်မည်', icon: Icons.grain_rounded, value: _noise, onChanged: (v) => setState(() => _noise = v)),
              ])),

              const SizedBox(height: 12),

              // ── Subtitle Settings ─────────────────────────
              AppCard(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                const SectionLabel('Auto Subtitle ပုံစံ', icon: Icons.closed_caption_rounded, iconColor: AppTheme.teal),
                const SizedBox(height: 12),

                // Position grid
                Text('နေရာ', style: const TextStyle(color: AppTheme.textMid, fontSize: 12, fontWeight: FontWeight.w600)),
                const SizedBox(height: 8),
                PositionGrid(value: _subtitlePosition, onChanged: (v) => setState(() => _subtitlePosition = v)),
                const SizedBox(height: 16),

                // Font size
                Row(children: [
                  const Expanded(child: Text('စာလုံး Size', style: TextStyle(color: AppTheme.textMid, fontSize: 12))),
                  Text('$_fontSize', style: const TextStyle(color: AppTheme.textHi, fontWeight: FontWeight.w700)),
                ]),
                Slider(
                  value: _fontSize.toDouble(), min: 8, max: 48,
                  activeColor: AppTheme.teal, inactiveColor: AppTheme.border,
                  onChanged: (v) => setState(() => _fontSize = v.round()),
                ),
                const SizedBox(height: 8),

                // Font color
                Row(children: [
                  const Expanded(child: Text('စာလုံးအရောင်', style: TextStyle(color: AppTheme.textMid, fontSize: 12))),
                  GestureDetector(
                    onTap: () => _pickColor(_fontColor, (c) => setState(() => _fontColor = c)),
                    child: Container(width: 36, height: 28,
                      decoration: BoxDecoration(color: _fontColor, borderRadius: BorderRadius.circular(8), border: Border.all(color: AppTheme.border))),
                  ),
                ]),
                const SizedBox(height: 12),

                // Box toggle
                AppToggleRow(label: 'နောက်ခံဘောင် ထည့်မည်', icon: Icons.crop_square_rounded, value: _boxEnabled,
                  iconColor: AppTheme.teal, onChanged: (v) => setState(() => _boxEnabled = v)),
                if (_boxEnabled) ...[
                  const SizedBox(height: 8),
                  Row(children: [
                    Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      const Text('ဘောင်အရောင်', style: TextStyle(color: AppTheme.textMid, fontSize: 12)),
                      const SizedBox(height: 4),
                      GestureDetector(
                        onTap: () => _pickColor(_boxColor, (c) => setState(() => _boxColor = c)),
                        child: Container(height: 36, decoration: BoxDecoration(
                          color: _boxColor, borderRadius: BorderRadius.circular(10), border: Border.all(color: AppTheme.border))),
                      ),
                    ])),
                    const SizedBox(width: 12),
                    Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      Text('Opacity: ${_boxOpacity.toStringAsFixed(1)}', style: const TextStyle(color: AppTheme.textMid, fontSize: 12)),
                      Slider(value: _boxOpacity, min: 0.1, max: 1.0, divisions: 9,
                        activeColor: AppTheme.teal, inactiveColor: AppTheme.border,
                        onChanged: (v) => setState(() => _boxOpacity = v)),
                    ])),
                  ]),
                ],
              ])),

              const SizedBox(height: 12),

              // ── Logo ─────────────────────────────────────
              AppCard(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                const SectionLabel('Logo ထည့်ရန်', icon: Icons.image_rounded),
                const SizedBox(height: 8),
                _outlineBtn(
                  icon: Icons.add_photo_alternate_rounded,
                  label: _logoFile != null ? 'Logo ပြောင်းရန်' : 'Logo Image ရွေးရန်',
                  color: AppTheme.teal,
                  onTap: () async {
                    final r = await FilePicker.platform.pickFiles(type: FileType.image);
                    if (r?.files.single.path != null) setState(() => _logoFile = File(r!.files.single.path!));
                  },
                ),
                if (_logoFile != null)
                  Padding(padding: const EdgeInsets.only(top: 8),
                    child: Text('✅ ${_logoFile!.path.split('/').last}',
                      style: const TextStyle(color: AppTheme.green, fontSize: 12))),
              ])),

              const SizedBox(height: 20),

              if (_freeLeft > 0)
                Container(
                  width: double.infinity, margin: const EdgeInsets.only(bottom: 10),
                  padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 9),
                  decoration: BoxDecoration(
                    color: AppTheme.green.withOpacity(0.08),
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: AppTheme.green.withOpacity(0.2)),
                  ),
                  child: Row(children: [
                    const Icon(Icons.card_giftcard_rounded, color: AppTheme.green, size: 16),
                    const SizedBox(width: 8),
                    Text('အခမဲ့ ပြုလုပ်ခွင့်: $_freeLeft ကြိမ် ကျန်သေးသည်',
                      style: const TextStyle(color: AppTheme.green, fontSize: 13, fontWeight: FontWeight.w600)),
                  ]),
                ),

              PrimaryButton(
                label: '🎬 စာတန်းထိုးပြီး ပြုပြင်မည်',
                icon: Icons.closed_caption_rounded,
                color: AppTheme.teal,
                loading: _processing,
                onPressed: _videoFilename == null ? null : _startProcess,
              ),
              const SizedBox(height: 32),
            ]),
          ),
        ),
      ]),
    );
  }

  Widget _outlineBtn({required IconData icon, required String label, required Color color, VoidCallback? onTap}) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        width: double.infinity, padding: const EdgeInsets.symmetric(vertical: 13),
        decoration: BoxDecoration(
          color: color.withOpacity(0.07), borderRadius: BorderRadius.circular(12),
          border: Border.all(color: color.withOpacity(0.3)),
        ),
        child: Row(mainAxisAlignment: MainAxisAlignment.center, children: [
          Icon(icon, color: color, size: 18),
          const SizedBox(width: 8),
          Text(label, style: TextStyle(color: color, fontWeight: FontWeight.w700, fontSize: 14)),
        ]),
      ),
    );
  }

  Future<void> _pickColor(Color current, ValueChanged<Color> onPicked) async {
    final colors = [Colors.white, Colors.yellow, Colors.red, Colors.green, Colors.blue, Colors.orange, Colors.purple, Colors.cyan, Colors.black];
    final picked = await showDialog<Color>(
      context: context,
      builder: (_) => AlertDialog(
        backgroundColor: AppTheme.surface2,
        title: const Text('အရောင်ရွေးရန်', style: TextStyle(color: AppTheme.textHi)),
        content: Wrap(spacing: 8, runSpacing: 8,
          children: colors.map((c) => GestureDetector(
            onTap: () => Navigator.pop(context, c),
            child: Container(width: 44, height: 44,
              decoration: BoxDecoration(color: c, borderRadius: BorderRadius.circular(10),
                border: Border.all(color: c == current ? AppTheme.teal : AppTheme.border, width: 2))),
          )).toList()),
      ),
    );
    if (picked != null) onPicked(picked);
  }
}
