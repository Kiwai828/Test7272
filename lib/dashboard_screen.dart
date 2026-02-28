import 'dart:async';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:file_picker/file_picker.dart';
import 'theme.dart';
import 'api_client.dart';
import 'providers.dart';
import 'upload_helper.dart';
import 'login_screen.dart';
import 'widgets.dart';

class DashboardScreen extends ConsumerStatefulWidget {
  const DashboardScreen({super.key});
  @override
  ConsumerState<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends ConsumerState<DashboardScreen> {
  final _api = ApiClient();
  final _urlCtrl = TextEditingController();
  final _watermarkCtrl = TextEditingController();
  final _aiTextCtrl = TextEditingController();

  String? _videoFilename;
  File? _logoFile;

  // Toggles
  bool _flip = false, _speed = false, _pitch = false, _noise = false, _blurEnabled = false;

  // Watermark
  bool _wmScroll = false, _wmBox = false;
  int _wmSize = 24;
  Color _wmColor = Colors.white;
  Color _wmBoxColor = Colors.black;
  double _wmBoxOpacity = 0.5;
  String _wmPosition = 'bottom_center';

  // AI voice
  String _voiceGender = 'male';

  // State
  String _statusMsg = '';
  bool _processing = false;
  bool _urlLoading = false;
  String? _jobId;
  Timer? _pollTimer;

  // User info
  int _coins = 0;
  int _freeLeft = 0;
  List<dynamic> _pricingTiers = [];
  String? _broadcast;

  @override
  void initState() {
    super.initState();
    _loadUser();
  }

  @override
  void dispose() { _pollTimer?.cancel(); _urlCtrl.dispose(); _watermarkCtrl.dispose(); _aiTextCtrl.dispose(); super.dispose(); }

  Future<void> _loadUser() async {
    try {
      final d = await _api.getUser();
      if (mounted) setState(() {
        _coins = d['coins'] ?? 0;
        _freeLeft = d['free_left'] ?? 0;
        _pricingTiers = d['pricing_tiers'] ?? [];
        _broadcast = d['broadcast'];
      });
    } catch (_) {}
  }

  Future<void> _pickVideo() async {
    final r = await FilePicker.platform.pickFiles(type: FileType.video, allowMultiple: false);
    if (r?.files.single.path == null) return;
    try {
      setState(() { _statusMsg = 'Upload လုပ်နေသည်...'; _videoFilename = null; });
      final fname = await UploadHelper.uploadFile(File(r!.files.single.path!), ref);
      setState(() { _videoFilename = fname; _statusMsg = '✅ Video တင်ပြီးပြီ'; });
      await _reAnalyze();
    } catch (e) {
      setState(() => _statusMsg = '❌ Upload မအောင်မြင်ပါ: $e');
    }
  }

  Future<void> _downloadFromUrl() async {
    final url = _urlCtrl.text.trim();
    if (url.isEmpty) return;
    setState(() { _urlLoading = true; _statusMsg = 'Link မှ Download ဆွဲနေသည်...'; });
    try {
      final res = await _api.downloadFromUrl(url);
      if (res['status'] == 'success') {
        setState(() { _videoFilename = res['filename']; _statusMsg = '✅ Download ပြီးပြီ'; });
        await _reAnalyze();
      } else {
        setState(() => _statusMsg = '❌ ${res['message']}');
      }
    } catch (e) {
      setState(() => _statusMsg = '❌ $e');
    }
    setState(() => _urlLoading = false);
  }

  Future<void> _reAnalyze() async {
    if (_videoFilename == null) return;
    try {
      final text = await _api.reAnalyze(_videoFilename!);
      if (text.isNotEmpty && mounted) setState(() => _aiTextCtrl.text = text);
    } catch (_) {}
  }

  Future<void> _startProcessing() async {
    if (_videoFilename == null) { _snack('Video file ဦးစွာ ရွေးပါ'); return; }
    setState(() { _processing = true; _statusMsg = 'Processing Queue ထဲ သွင်းနေသည်...'; });
    try {
      final data = <String, String>{
        'video_filename': _videoFilename!,
        'blur_areas': '[]',
        'logo_x': '0', 'logo_y': '0', 'logo_w': '0', 'logo_h': '0',
        if (_flip) 'bypass_flip': 'on',
        if (_speed) 'bypass_speed': 'on',
        if (_pitch) 'bypass_pitch': 'on',
        if (_noise) 'bypass_noise': 'on',
        if (_blurEnabled) 'blur_enabled': 'on',
        if (_watermarkCtrl.text.isNotEmpty) 'text_watermark_text': _watermarkCtrl.text,
        'text_watermark_size': _wmSize.toString(),
        'text_watermark_color': _colorHex(_wmColor),
        'text_watermark_position': _wmPosition,
        if (_wmScroll) 'text_watermark_scroll': 'on',
        if (_wmBox) 'text_watermark_box': 'on',
        'text_watermark_box_color': _colorHex(_wmBoxColor),
        'text_watermark_box_opacity': _wmBoxOpacity.toString(),
        if (_aiTextCtrl.text.isNotEmpty) 'ai_text': _aiTextCtrl.text,
        'voice_gender': _voiceGender,
      };
      final res = await _api.processVideo(data);
      if (res['status'] == 'queued') {
        _jobId = res['job_id'];
        setState(() => _statusMsg = '⏳ Queue ထဲ ရောက်ပြီ၊ Processing...');
        _startPoll();
      } else {
        setState(() { _statusMsg = '❌ ${res['message']}'; _processing = false; });
      }
    } catch (e) {
      setState(() { _statusMsg = '❌ $e'; _processing = false; });
    }
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
          if (mounted) setState(() { _processing = false; _statusMsg = '✅ Processing ပြီးဆုံးပြီ!'; });
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

  void _showDownload(String url) {
    showModalBottomSheet(context: context, backgroundColor: AppTheme.surface2,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(24))),
      builder: (_) => DownloadSheet(url: url));
  }

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
        // App Bar
        SliverAppBar(
          pinned: true,
          backgroundColor: AppTheme.bg,
          title: const Row(children: [
            Icon(Icons.video_camera_back_rounded, color: AppTheme.primary, size: 22),
            SizedBox(width: 8),
            Text('Recap Maker', style: TextStyle(color: AppTheme.textHi, fontWeight: FontWeight.w800, fontSize: 18)),
          ]),
          actions: [
            // Coins
            GestureDetector(
              onTap: () => showModalBottomSheet(context: context,
                backgroundColor: AppTheme.surface2, isScrollControlled: true,
                shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(24))),
                builder: (_) => TopupSheet(pricingTiers: _pricingTiers)),
              child: Container(
                margin: const EdgeInsets.only(right: 8),
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                decoration: BoxDecoration(
                  color: AppTheme.gold.withOpacity(0.12),
                  borderRadius: BorderRadius.circular(20),
                  border: Border.all(color: AppTheme.gold.withOpacity(0.3)),
                ),
                child: Row(children: [
                  const Icon(Icons.monetization_on_rounded, color: AppTheme.gold, size: 16),
                  const SizedBox(width: 5),
                  Text('$_coins', style: const TextStyle(color: AppTheme.gold, fontWeight: FontWeight.w700, fontSize: 14)),
                ]),
              ),
            ),
            IconButton(
              icon: const Icon(Icons.logout_rounded, color: AppTheme.textMid, size: 20),
              onPressed: () async {
                await ref.read(authProvider.notifier).logout();
                if (mounted) Navigator.of(context).pushAndRemoveUntil(
                  MaterialPageRoute(builder: (_) => const LoginScreen()), (_) => false);
              },
            ),
          ],
        ),

        SliverToBoxAdapter(
          child: Column(children: [
            // Broadcast banner
            if (_broadcast != null)
              Container(
                width: double.infinity, margin: const EdgeInsets.fromLTRB(16, 4, 16, 0),
                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                decoration: BoxDecoration(
                  color: AppTheme.primary.withOpacity(0.1),
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: AppTheme.primary.withOpacity(0.25)),
                ),
                child: Row(children: [
                  const Icon(Icons.campaign_rounded, color: AppTheme.primary, size: 16),
                  const SizedBox(width: 8),
                  Expanded(child: Text(_broadcast!, style: const TextStyle(color: AppTheme.textHi, fontSize: 13))),
                ]),
              ),

            Padding(
              padding: const EdgeInsets.all(16),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [

                // ── Section 1: Video Source ──────────────────
                AppCard(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  const SectionLabel('မူရင်း Video File', icon: Icons.video_file_rounded),
                  const SizedBox(height: 12),

                  // Upload button
                  _outlineBtn(
                    icon: Icons.cloud_upload_rounded,
                    label: 'Video File တင်ရန်',
                    color: AppTheme.primary,
                    onTap: _processing ? null : _pickVideo,
                  ),
                  const SizedBox(height: 10),

                  // OR divider
                  Row(children: [
                    const Expanded(child: Divider(color: AppTheme.border)),
                    Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 12),
                      child: Text('သို့မဟုတ်', style: TextStyle(color: AppTheme.textLow, fontSize: 12)),
                    ),
                    const Expanded(child: Divider(color: AppTheme.border)),
                  ]),
                  const SizedBox(height: 10),

                  // URL input
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
                          focusedBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: const BorderSide(color: AppTheme.primary)),
                        ),
                      ),
                    ),
                    const SizedBox(width: 8),
                    GestureDetector(
                      onTap: _urlLoading ? null : _downloadFromUrl,
                      child: Container(
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: AppTheme.primary, borderRadius: BorderRadius.circular(12),
                        ),
                        child: _urlLoading
                          ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2))
                          : const Icon(Icons.link_rounded, color: Colors.white, size: 20),
                      ),
                    ),
                  ]),

                  // Status
                  if (_statusMsg.isNotEmpty) ...[
                    const SizedBox(height: 12),
                    Container(
                      padding: const EdgeInsets.all(10),
                      decoration: BoxDecoration(
                        color: _statusMsg.startsWith('✅') ? AppTheme.green.withOpacity(0.08)
                          : _statusMsg.startsWith('❌') ? AppTheme.red.withOpacity(0.08)
                          : AppTheme.surface2,
                        borderRadius: BorderRadius.circular(10),
                        border: Border.all(color: _statusMsg.startsWith('✅') ? AppTheme.green.withOpacity(0.2)
                          : _statusMsg.startsWith('❌') ? AppTheme.red.withOpacity(0.2)
                          : AppTheme.border),
                      ),
                      child: Row(children: [
                        if (_processing) ...[
                          const SizedBox(width: 16, height: 16,
                            child: CircularProgressIndicator(color: AppTheme.primary, strokeWidth: 2)),
                          const SizedBox(width: 10),
                        ],
                        Expanded(child: Text(_statusMsg,
                          style: TextStyle(
                            color: _statusMsg.startsWith('✅') ? AppTheme.green
                              : _statusMsg.startsWith('❌') ? AppTheme.red
                              : AppTheme.textMid,
                            fontSize: 12))),
                      ]),
                    ),
                    // Upload progress
                    Consumer(builder: (_, ref, __) {
                      final up = ref.watch(uploadProvider);
                      if (!up.uploading) return const SizedBox.shrink();
                      return Padding(
                        padding: const EdgeInsets.only(top: 8),
                        child: Column(children: [
                          ClipRRect(
                            borderRadius: BorderRadius.circular(4),
                            child: LinearProgressIndicator(
                              value: up.progress,
                              backgroundColor: AppTheme.border,
                              valueColor: const AlwaysStoppedAnimation(AppTheme.primary),
                              minHeight: 6,
                            ),
                          ),
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
                      decoration: BoxDecoration(
                        color: AppTheme.green.withOpacity(0.08),
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Row(children: [
                        const Icon(Icons.check_circle_rounded, color: AppTheme.green, size: 14),
                        const SizedBox(width: 6),
                        Expanded(child: Text(_videoFilename!,
                          style: const TextStyle(color: AppTheme.textMid, fontSize: 11),
                          overflow: TextOverflow.ellipsis)),
                      ]),
                    ),
                  ],
                ])),

                const SizedBox(height: 12),

                // ── Section 2: Effects ───────────────────────
                AppCard(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  const SectionLabel('Effect များ', icon: Icons.tune_rounded),
                  const SizedBox(height: 4),
                  AppToggleRow(label: 'Video ကိုလှန်မည်', icon: Icons.flip_rounded, value: _flip, onChanged: (v) => setState(() => _flip = v)),
                  AppToggleRow(label: 'Speed မြန်မည် (1.05x)', icon: Icons.speed_rounded, value: _speed, onChanged: (v) => setState(() => _speed = v)),
                  AppToggleRow(label: 'အသံပြောင်းမည် (Copyright)', icon: Icons.music_note_rounded, value: _pitch, iconColor: AppTheme.teal, onChanged: (v) => setState(() => _pitch = v)),
                  AppToggleRow(label: 'Noise/Grain ထည့်မည်', icon: Icons.grain_rounded, value: _noise, onChanged: (v) => setState(() => _noise = v)),
                  AppToggleRow(label: 'နေရာဝှက် (Blur)', icon: Icons.blur_on_rounded, value: _blurEnabled, iconColor: AppTheme.red, onChanged: (v) => setState(() => _blurEnabled = v)),
                ])),

                const SizedBox(height: 12),

                // ── Section 3: Watermark ─────────────────────
                AppCard(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  const SectionLabel('စာတန်း Watermark', icon: Icons.text_fields_rounded),
                  const SizedBox(height: 8),
                  TextField(
                    controller: _watermarkCtrl,
                    style: const TextStyle(color: AppTheme.textHi, fontSize: 14),
                    decoration: InputDecoration(
                      hintText: 'ဥပမာ: သင်၏ Channel အမည်',
                      hintStyle: const TextStyle(color: AppTheme.textLow, fontSize: 13),
                      filled: true, fillColor: AppTheme.surface2,
                      contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                      border: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: const BorderSide(color: AppTheme.border)),
                      enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: const BorderSide(color: AppTheme.border)),
                      focusedBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: const BorderSide(color: AppTheme.primary)),
                    ),
                  ),
                  const SizedBox(height: 12),
                  // Watermark position
                  Text('နေရာ', style: TextStyle(color: AppTheme.textMid, fontSize: 12, fontWeight: FontWeight.w600)),
                  const SizedBox(height: 8),
                  PositionGrid(value: _wmPosition, onChanged: (v) => setState(() => _wmPosition = v)),
                  const SizedBox(height: 12),
                  // Advanced watermark settings
                  Row(children: [
                    Expanded(child: _colorPicker('စာလုံးအရောင်', _wmColor, (c) => setState(() => _wmColor = c))),
                    const SizedBox(width: 8),
                    Expanded(child: _sliderField('Size', _wmSize.toDouble(), 12, 72, (v) => setState(() => _wmSize = v.round()))),
                  ]),
                  const SizedBox(height: 8),
                  AppToggleRow(label: 'ရွေ့လျားစေမည်', icon: Icons.swap_horiz_rounded, value: _wmScroll, onChanged: (v) => setState(() => _wmScroll = v)),
                  AppToggleRow(label: 'နောက်ခံဘောင်', icon: Icons.crop_square_rounded, value: _wmBox, onChanged: (v) => setState(() => _wmBox = v)),
                  if (_wmBox) ...[
                    const SizedBox(height: 8),
                    Row(children: [
                      Expanded(child: _colorPicker('ဘောင်အရောင်', _wmBoxColor, (c) => setState(() => _wmBoxColor = c))),
                      const SizedBox(width: 8),
                      Expanded(child: _sliderField('Opacity', _wmBoxOpacity, 0.1, 1.0, (v) => setState(() => _wmBoxOpacity = v))),
                    ]),
                  ],
                ])),

                const SizedBox(height: 12),

                // ── Section 4: Logo ──────────────────────────
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

                const SizedBox(height: 12),

                // ── Section 5: AI Voice ──────────────────────
                AppCard(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Row(children: [
                    const Expanded(child: SectionLabel('AI အသံထပ်ခြင်း', icon: Icons.smart_toy_rounded)),
                    GestureDetector(
                      onTap: _reAnalyze,
                      child: Container(
                        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                        decoration: BoxDecoration(
                          color: AppTheme.primary.withOpacity(0.1),
                          borderRadius: BorderRadius.circular(8),
                          border: Border.all(color: AppTheme.primary.withOpacity(0.25)),
                        ),
                        child: const Row(children: [
                          Icon(Icons.refresh_rounded, color: AppTheme.primary, size: 14),
                          SizedBox(width: 4),
                          Text('ပြန်ယူမည်', style: TextStyle(color: AppTheme.primary, fontSize: 12)),
                        ]),
                      ),
                    ),
                  ]),
                  const SizedBox(height: 8),
                  TextField(
                    controller: _aiTextCtrl,
                    maxLines: 4,
                    style: const TextStyle(color: AppTheme.textHi, fontSize: 13),
                    decoration: InputDecoration(
                      hintText: 'AI Script ထည့်ပါ...',
                      hintStyle: const TextStyle(color: AppTheme.textLow, fontSize: 12),
                      filled: true, fillColor: AppTheme.surface2,
                      contentPadding: const EdgeInsets.all(14),
                      border: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: const BorderSide(color: AppTheme.border)),
                      enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: const BorderSide(color: AppTheme.border)),
                      focusedBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: const BorderSide(color: AppTheme.primary)),
                    ),
                  ),
                  const SizedBox(height: 10),
                  Row(children: [
                    Expanded(child: _voiceChip('male', '👨 အမျိုးသားအသံ')),
                    const SizedBox(width: 8),
                    Expanded(child: _voiceChip('female', '👩 အမျိုးသမီးအသံ')),
                  ]),
                ])),

                const SizedBox(height: 20),

                // ── Process button ───────────────────────────
                // Free badge
                if (_freeLeft > 0)
                  Container(
                    width: double.infinity,
                    margin: const EdgeInsets.only(bottom: 10),
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
                  label: '✨ စတင်ပြုပြင်မည်',
                  icon: Icons.auto_fix_high_rounded,
                  loading: _processing,
                  onPressed: _videoFilename == null ? null : _startProcessing,
                ),
                const SizedBox(height: 32),
              ]),
            ),
          ]),
        ),
      ]),
    );
  }

  Widget _outlineBtn({required IconData icon, required String label, required Color color, VoidCallback? onTap}) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.symmetric(vertical: 13),
        decoration: BoxDecoration(
          color: color.withOpacity(0.07),
          borderRadius: BorderRadius.circular(12),
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

  Widget _colorPicker(String label, Color current, ValueChanged<Color> onChanged) {
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Text(label, style: const TextStyle(color: AppTheme.textMid, fontSize: 12)),
      const SizedBox(height: 4),
      GestureDetector(
        onTap: () => _pickColor(current, onChanged),
        child: Container(
          height: 40,
          decoration: BoxDecoration(
            color: current,
            borderRadius: BorderRadius.circular(10),
            border: Border.all(color: AppTheme.border),
          ),
        ),
      ),
    ]);
  }

  Widget _sliderField(String label, double val, double min, double max, ValueChanged<double> onChanged) {
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Text('$label: ${val.toStringAsFixed(val < 10 ? 1 : 0)}', style: const TextStyle(color: AppTheme.textMid, fontSize: 12)),
      Slider(value: val, min: min, max: max,
        activeColor: AppTheme.primary,
        inactiveColor: AppTheme.border,
        onChanged: onChanged),
    ]);
  }

  Widget _voiceChip(String gender, String label) {
    final selected = _voiceGender == gender;
    return GestureDetector(
      onTap: () => setState(() => _voiceGender = gender),
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 10),
        decoration: BoxDecoration(
          color: selected ? AppTheme.primary.withOpacity(0.15) : AppTheme.surface2,
          borderRadius: BorderRadius.circular(10),
          border: Border.all(color: selected ? AppTheme.primary.withOpacity(0.5) : AppTheme.border),
        ),
        child: Center(child: Text(label, style: TextStyle(
          color: selected ? AppTheme.primary : AppTheme.textMid,
          fontSize: 13, fontWeight: selected ? FontWeight.w700 : FontWeight.w400))),
      ),
    );
  }

  Future<void> _pickColor(Color current, ValueChanged<Color> onPicked) async {
    // Simple color grid picker
    final colors = [Colors.white, Colors.yellow, Colors.red, Colors.green, Colors.blue, Colors.orange, Colors.purple, Colors.cyan, Colors.black];
    final picked = await showDialog<Color>(
      context: context,
      builder: (_) => AlertDialog(
        backgroundColor: AppTheme.surface2,
        title: const Text('အရောင်ရွေးရန်', style: TextStyle(color: AppTheme.textHi)),
        content: Wrap(
          spacing: 8, runSpacing: 8,
          children: colors.map((c) => GestureDetector(
            onTap: () => Navigator.pop(context, c),
            child: Container(
              width: 44, height: 44,
              decoration: BoxDecoration(
                color: c, borderRadius: BorderRadius.circular(10),
                border: Border.all(color: c == current ? AppTheme.primary : AppTheme.border, width: 2),
              ),
            ),
          )).toList(),
        ),
      ),
    );
    if (picked != null) onPicked(picked);
  }
}
