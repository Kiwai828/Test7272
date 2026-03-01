import 'dart:io';
import 'package:flutter/material.dart';
import 'package:video_player/video_player.dart';
import 'theme.dart';

// ════════════════════════════════════════════════
// LIVE VIDEO PREVIEW
// - Video ratio-aware
// - Draggable + Resizable blur box
// - Draggable + Resizable logo box
// ════════════════════════════════════════════════

class LiveVideoPreview extends StatefulWidget {
  final String url;
  final bool blurEnabled;
  final File? logoFile;
  const LiveVideoPreview({
    super.key,
    required this.url,
    required this.blurEnabled,
    this.logoFile,
  });
  @override
  State<LiveVideoPreview> createState() => _LVPState();
}

class _LVPState extends State<LiveVideoPreview> {
  VideoPlayerController? _vpc;
  bool _ready = false;
  bool _err = false;
  double _aspectRatio = 9 / 16;

  // Blur box — position & size (in ratio 0..1 of container)
  double _bx = 0.1, _by = 0.5, _bw = 0.5, _bh = 0.18;

  // Logo box
  double _lx = 0.05, _ly = 0.05, _lw = 0.28, _lh = 0.15;

  @override
  void initState() {
    super.initState();
    _initPlayer();
  }

  @override
  void dispose() { _vpc?.dispose(); super.dispose(); }

  @override
  void didUpdateWidget(LiveVideoPreview old) {
    super.didUpdateWidget(old);
    if (old.url != widget.url) {
      _vpc?.dispose();
      setState(() { _ready = false; _err = false; });
      _initPlayer();
    }
  }

  Future<void> _initPlayer() async {
    try {
      final vpc = VideoPlayerController.networkUrl(Uri.parse(widget.url));
      await vpc.initialize();
      if (!mounted) { vpc.dispose(); return; }
      setState(() {
        _vpc = vpc;
        _ready = true;
        final sz = vpc.value.size;
        if (sz.width > 0 && sz.height > 0) _aspectRatio = sz.width / sz.height;
      });
      vpc.setLooping(true);
      vpc.play();
    } catch (e) {
      if (mounted) setState(() => _err = true);
    }
  }

  @override
  Widget build(BuildContext context) {
    return GlassCard(
      padding: const EdgeInsets.fromLTRB(12, 10, 12, 12),
      borderColor: C.violet.withOpacity(0.3),
      child: Column(children: [
        // Header row
        Row(children: [
          const Icon(Icons.live_tv_rounded, color: C.violet, size: 13),
          const SizedBox(width: 6),
          const Text('LIVE PREVIEW',
            style: TextStyle(color: C.violet, fontSize: 11, fontWeight: FontWeight.w800, letterSpacing: 1)),
          const Spacer(),
          if (widget.blurEnabled)
            _badge('BLUR', C.rose),
          if (widget.logoFile != null) ...[
            const SizedBox(width: 6),
            _badge('LOGO', C.cyan),
          ],
          if (_ready) ...[
            const SizedBox(width: 6),
            GestureDetector(
              onTap: () => setState(() =>
                _vpc!.value.isPlaying ? _vpc!.pause() : _vpc!.play()),
              child: ValueListenableBuilder(
                valueListenable: _vpc!,
                builder: (_, v, __) => _badge(
                  v.isPlaying ? '⏸' : '▶', C.t2))),
          ],
        ]),

        const SizedBox(height: 10),

        // Video area — ratio-aware with overlays
        AspectRatio(
          aspectRatio: _aspectRatio,
          child: ClipRRect(
            borderRadius: BorderRadius.circular(10),
            child: LayoutBuilder(builder: (ctx, box) {
              final cw = box.maxWidth;
              final ch = box.maxHeight;
              return Stack(clipBehavior: Clip.hardEdge, children: [

                // ── Video ──────────────────────────
                Positioned.fill(child: _ready
                  ? VideoPlayer(_vpc!)
                  : Container(color: C.bg1, child: Center(
                      child: _err
                        ? const Icon(Icons.videocam_off_rounded, color: C.t3, size: 32)
                        : const CircularProgressIndicator(color: C.violet, strokeWidth: 2)))),

                // ── Blur overlay box ───────────────
                if (widget.blurEnabled)
                  _DragResizeBox(
                    color: C.rose,
                    icon: Icons.blur_on_rounded,
                    x: _bx * cw, y: _by * ch,
                    w: _bw * cw, h: _bh * ch,
                    containerW: cw, containerH: ch,
                    onUpdate: (x, y, w, h) => setState(() {
                      _bx = x / cw; _by = y / ch;
                      _bw = w / cw; _bh = h / ch;
                    }),
                  ),

                // ── Logo overlay box ───────────────
                if (widget.logoFile != null)
                  _DragResizeBox(
                    color: C.cyan,
                    icon: null,
                    image: widget.logoFile,
                    x: _lx * cw, y: _ly * ch,
                    w: _lw * cw, h: _lh * ch,
                    containerW: cw, containerH: ch,
                    onUpdate: (x, y, w, h) => setState(() {
                      _lx = x / cw; _ly = y / ch;
                      _lw = w / cw; _lh = h / ch;
                    }),
                  ),
              ]);
            }),
          ),
        ),

        // Progress bar
        if (_ready)
          Padding(
            padding: const EdgeInsets.only(top: 8),
            child: ValueListenableBuilder(
              valueListenable: _vpc!,
              builder: (_, v, __) {
                final pos = v.position.inMilliseconds.toDouble();
                final dur = v.duration.inMilliseconds.toDouble();
                return Row(children: [
                  Text(_fmt(v.position), style: const TextStyle(color: C.t3, fontSize: 9)),
                  Expanded(child: SliderTheme(
                    data: SliderThemeData(
                      trackHeight: 2,
                      thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 5),
                      activeTrackColor: C.violet,
                      inactiveTrackColor: C.bdr,
                      thumbColor: Colors.white,
                      overlayShape: SliderComponentShape.noOverlay),
                    child: Slider(
                      value: dur > 0 ? (pos / dur).clamp(0.0, 1.0) : 0,
                      onChanged: (val) =>
                        _vpc!.seekTo(Duration(milliseconds: (val * dur).toInt()))))),
                  Text(_fmt(v.duration), style: const TextStyle(color: C.t3, fontSize: 9)),
                ]);
              })),
      ]),
    );
  }

  Widget _badge(String label, Color c) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 3),
    decoration: BoxDecoration(
      color: c.withOpacity(0.1), borderRadius: BorderRadius.circular(6),
      border: Border.all(color: c.withOpacity(0.35))),
    child: Text(label, style: TextStyle(color: c, fontSize: 9, fontWeight: FontWeight.w800)));

  String _fmt(Duration d) =>
    '${d.inMinutes.remainder(60).toString().padLeft(2, '0')}:'
    '${d.inSeconds.remainder(60).toString().padLeft(2, '0')}';
}

// ════════════════════════════════════════════════
// Draggable + Resizable box with corner handles
// ════════════════════════════════════════════════
class _DragResizeBox extends StatefulWidget {
  final Color color;
  final IconData? icon;
  final File? image;
  final double x, y, w, h;
  final double containerW, containerH;
  final void Function(double x, double y, double w, double h) onUpdate;
  const _DragResizeBox({
    required this.color, this.icon, this.image,
    required this.x, required this.y, required this.w, required this.h,
    required this.containerW, required this.containerH,
    required this.onUpdate,
  });
  @override
  State<_DragResizeBox> createState() => _DRBState();
}

class _DRBState extends State<_DragResizeBox> {
  late double _x, _y, _w, _h;
  static const double _minSize = 30;
  static const double _handleSize = 22;

  @override
  void initState() {
    super.initState();
    _x = widget.x; _y = widget.y; _w = widget.w; _h = widget.h;
  }

  @override
  void didUpdateWidget(_DragResizeBox old) {
    super.didUpdateWidget(old);
    if (old.x != widget.x || old.y != widget.y ||
        old.w != widget.w || old.h != widget.h) {
      _x = widget.x; _y = widget.y; _w = widget.w; _h = widget.h;
    }
  }

  void _clamp() {
    if (_w < _minSize) _w = _minSize;
    if (_h < _minSize) _h = _minSize;
    if (_x < 0) _x = 0;
    if (_y < 0) _y = 0;
    if (_x + _w > widget.containerW) _x = widget.containerW - _w;
    if (_y + _h > widget.containerH) _y = widget.containerH - _h;
  }

  void _notify() => widget.onUpdate(_x, _y, _w, _h);

  @override
  Widget build(BuildContext context) {
    return Positioned(
      left: _x, top: _y, width: _w, height: _h,
      child: Stack(clipBehavior: Clip.none, children: [

        // Main box — drag to move
        Positioned.fill(
          child: GestureDetector(
            behavior: HitTestBehavior.opaque,
            onPanUpdate: (d) {
              setState(() {
                _x += d.delta.dx;
                _y += d.delta.dy;
                _clamp();
              });
              _notify();
            },
            child: Container(
              decoration: BoxDecoration(
                color: widget.image != null
                  ? Colors.transparent
                  : widget.color.withOpacity(0.25),
                borderRadius: BorderRadius.circular(4),
                border: Border.all(color: widget.color, width: 1.5)),
              child: widget.image != null
                ? ClipRRect(
                    borderRadius: BorderRadius.circular(3),
                    child: Image.file(widget.image!, fit: BoxFit.contain))
                : widget.icon != null
                  ? Center(child: Icon(widget.icon, color: widget.color, size: 18))
                  : null,
            ),
          ),
        ),

        // ── Resize handles ─────────────────────

        // Bottom-right corner (resize W+H)
        _resizeHandle(
          alignment: Alignment.bottomRight,
          right: -_handleSize / 2, bottom: -_handleSize / 2,
          onDrag: (dx, dy) {
            setState(() { _w += dx; _h += dy; _clamp(); });
            _notify();
          }),

        // Bottom-left corner (resize W from left)
        _resizeHandle(
          alignment: Alignment.bottomLeft,
          left: -_handleSize / 2, bottom: -_handleSize / 2,
          onDrag: (dx, dy) {
            setState(() { _x += dx; _w -= dx; _h += dy; _clamp(); });
            _notify();
          }),

        // Top-right corner
        _resizeHandle(
          alignment: Alignment.topRight,
          right: -_handleSize / 2, top: -_handleSize / 2,
          onDrag: (dx, dy) {
            setState(() { _y += dy; _w += dx; _h -= dy; _clamp(); });
            _notify();
          }),

        // Top-left corner
        _resizeHandle(
          alignment: Alignment.topLeft,
          left: -_handleSize / 2, top: -_handleSize / 2,
          onDrag: (dx, dy) {
            setState(() { _x += dx; _y += dy; _w -= dx; _h -= dy; _clamp(); });
            _notify();
          }),

        // Right edge (width only)
        _resizeHandle(
          alignment: Alignment.centerRight,
          right: -_handleSize / 2, top: _h / 2 - _handleSize / 2,
          onDrag: (dx, dy) {
            setState(() { _w += dx; _clamp(); });
            _notify();
          }),

        // Bottom edge (height only)
        _resizeHandle(
          alignment: Alignment.bottomCenter,
          left: _w / 2 - _handleSize / 2, bottom: -_handleSize / 2,
          onDrag: (dx, dy) {
            setState(() { _h += dy; _clamp(); });
            _notify();
          }),
      ]),
    );
  }

  Widget _resizeHandle({
    required Alignment alignment,
    double? left, double? right, double? top, double? bottom,
    required void Function(double dx, double dy) onDrag,
  }) {
    return Positioned(
      left: left, right: right, top: top, bottom: bottom,
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onPanUpdate: (d) => onDrag(d.delta.dx, d.delta.dy),
        child: Container(
          width: _handleSize, height: _handleSize,
          decoration: BoxDecoration(
            color: widget.color,
            shape: BoxShape.circle,
            boxShadow: [BoxShadow(color: widget.color.withOpacity(0.5), blurRadius: 6)]),
          child: Icon(Icons.open_with_rounded,
            color: Colors.white, size: _handleSize * 0.55)),
      ),
    );
  }
}
