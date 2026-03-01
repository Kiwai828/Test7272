import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'dart:ui';

// ══════════════════════════════════════════════
// RECAP MAKER V4 — Cinematic Dark Glass
// Premium video tool aesthetic
// ══════════════════════════════════════════════

class C {
  // Backgrounds
  static const bg0    = Color(0xFF070A12);
  static const bg1    = Color(0xFF0B0F1A);
  static const bg2    = Color(0xFF101420);
  static const glass  = Color(0x1AFFFFFF);
  static const glass2 = Color(0x10FFFFFF);

  // Borders
  static const bdr    = Color(0x22FFFFFF);
  static const bdr2   = Color(0x14FFFFFF);

  // Accents
  static const violet = Color(0xFF7C5CFC);
  static const indigo = Color(0xFF4F35D6);
  static const cyan   = Color(0xFF00D9C6);
  static const rose   = Color(0xFFFF4070);
  static const gold   = Color(0xFFFFB020);
  static const mint   = Color(0xFF00E299);

  // Text
  static const t1 = Color(0xFFF5F7FF);
  static const t2 = Color(0xFFBCC3E0);
  static const t3 = Color(0xFF7B84A8);

  // Gradients
  static const grad1 = [Color(0xFF7C5CFC), Color(0xFF4F35D6)];
  static const grad2 = [Color(0xFF00D9C6), Color(0xFF00A896)];
  static const gradGold = [Color(0xFFFFB020), Color(0xFFFF7A00)];
}

class AppTheme {
  static ThemeData get theme => ThemeData(
    useMaterial3: true,
    brightness: Brightness.dark,
    scaffoldBackgroundColor: C.bg0,
    colorScheme: const ColorScheme.dark(
      primary: C.violet, secondary: C.cyan, surface: C.bg2, error: C.rose,
    ),
    appBarTheme: const AppBarTheme(
      backgroundColor: Colors.transparent,
      elevation: 0,
      scrolledUnderElevation: 0,
      systemOverlayStyle: SystemUiOverlayStyle(
        statusBarColor: Colors.transparent,
        statusBarIconBrightness: Brightness.light,
        systemNavigationBarColor: C.bg1,
        systemNavigationBarIconBrightness: Brightness.light,
      ),
    ),
  );
}

// ── Glass Card ──────────────────────────────────
class GlassCard extends StatelessWidget {
  final Widget child;
  final EdgeInsets? padding;
  final Color? borderColor;
  final double radius;
  final Gradient? gradient;
  const GlassCard({super.key, required this.child, this.padding, this.borderColor, this.radius = 20, this.gradient});

  @override
  Widget build(BuildContext context) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(radius),
      child: BackdropFilter(
        filter: ImageFilter.blur(sigmaX: 12, sigmaY: 12),
        child: Container(
          padding: padding ?? const EdgeInsets.all(18),
          decoration: BoxDecoration(
            color: const Color(0xFF0D1422),
            gradient: gradient ?? const LinearGradient(
              begin: Alignment.topLeft, end: Alignment.bottomRight,
              colors: [Color(0x20FFFFFF), Color(0x08FFFFFF)],
            ),
            borderRadius: BorderRadius.circular(radius),
            border: Border.all(color: borderColor ?? C.bdr, width: 1),
          ),
          child: child,
        ),
      ),
    );
  }
}

// ── Gradient Button ─────────────────────────────
class GradBtn extends StatefulWidget {
  final String label;
  final IconData icon;
  final List<Color> colors;
  final VoidCallback? onTap;
  final bool loading;
  final double height;
  const GradBtn({super.key, required this.label, required this.icon, required this.colors, this.onTap, this.loading = false, this.height = 54});

  @override
  State<GradBtn> createState() => _GradBtnState();
}

class _GradBtnState extends State<GradBtn> with SingleTickerProviderStateMixin {
  late AnimationController _ctrl;
  late Animation<double> _scale;

  @override
  void initState() {
    super.initState();
    _ctrl = AnimationController(vsync: this, duration: const Duration(milliseconds: 150));
    _scale = Tween(begin: 1.0, end: 0.96).animate(CurvedAnimation(parent: _ctrl, curve: Curves.easeOut));
  }

  @override
  void dispose() { _ctrl.dispose(); super.dispose(); }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTapDown: (_) => _ctrl.forward(),
      onTapUp: (_) { _ctrl.reverse(); widget.onTap?.call(); },
      onTapCancel: () => _ctrl.reverse(),
      child: AnimatedBuilder(
        animation: _scale,
        builder: (_, child) => Transform.scale(scale: _scale.value, child: child),
        child: Container(
          height: widget.height, width: double.infinity,
          decoration: BoxDecoration(
            gradient: LinearGradient(colors: widget.onTap == null
              ? [C.t3, C.t3] : widget.colors),
            borderRadius: BorderRadius.circular(16),
            boxShadow: widget.onTap == null ? [] : [
              BoxShadow(color: widget.colors.first.withOpacity(0.4), blurRadius: 20, offset: const Offset(0, 8)),
            ],
          ),
          child: Center(
            child: widget.loading
              ? SizedBox(width: 22, height: 22,
                  child: CircularProgressIndicator(color: Colors.white.withOpacity(0.8), strokeWidth: 2.5))
              : Row(mainAxisSize: MainAxisSize.min, children: [
                  Icon(widget.icon, color: Colors.white, size: 18),
                  const SizedBox(width: 8),
                  Text(widget.label, style: const TextStyle(color: Colors.white, fontSize: 15, fontWeight: FontWeight.w700, letterSpacing: 0.3)),
                ]),
          ),
        ),
      ),
    );
  }
}

// ── Outline Button ───────────────────────────────
class OutlineBtn extends StatelessWidget {
  final String label;
  final IconData icon;
  final Color color;
  final VoidCallback? onTap;
  final bool loading;
  const OutlineBtn({super.key, required this.label, required this.icon, required this.color, this.onTap, this.loading = false});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        width: double.infinity, height: 48,
        decoration: BoxDecoration(
          color: color.withOpacity(0.07),
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: color.withOpacity(0.35), width: 1),
        ),
        child: Center(child: loading
          ? SizedBox(width: 18, height: 18, child: CircularProgressIndicator(color: color, strokeWidth: 2))
          : Row(mainAxisSize: MainAxisSize.min, children: [
              Icon(icon, color: color, size: 16),
              const SizedBox(width: 7),
              Text(label, style: TextStyle(color: color, fontWeight: FontWeight.w700, fontSize: 13)),
            ])),
      ),
    );
  }
}

// ── Section Header ────────────────────────────────
class SectionHdr extends StatelessWidget {
  final String text;
  final IconData icon;
  final Color? color;
  const SectionHdr(this.text, {super.key, required this.icon, this.color});

  @override
  Widget build(BuildContext context) {
    final c = color ?? C.violet;
    return Padding(
      padding: const EdgeInsets.only(bottom: 14),
      child: Row(children: [
        Container(width: 28, height: 28,
          decoration: BoxDecoration(color: c.withOpacity(0.12), borderRadius: BorderRadius.circular(8)),
          child: Icon(icon, size: 14, color: c)),
        const SizedBox(width: 10),
        Text(text, style: TextStyle(color: c, fontSize: 12, fontWeight: FontWeight.w800, letterSpacing: 1.2)),
      ]),
    );
  }
}

// ── Toggle Row ────────────────────────────────────
class ToggleRow extends StatelessWidget {
  final String label;
  final IconData icon;
  final bool value;
  final ValueChanged<bool> onChanged;
  final Color? color;
  const ToggleRow({super.key, required this.label, required this.icon, required this.value, required this.onChanged, this.color});

  @override
  Widget build(BuildContext context) {
    final c = color ?? C.t2;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 5),
      child: Row(children: [
        Icon(icon, size: 16, color: value ? c : C.t3),
        const SizedBox(width: 10),
        Expanded(child: Text(label, style: TextStyle(
          color: value ? const Color(0xFFF5F7FF) : const Color(0xFFBCC3E0), fontSize: 13,
          fontWeight: value ? FontWeight.w600 : FontWeight.w400))),
        Transform.scale(scale: 0.85, child: Switch.adaptive(
          value: value, onChanged: onChanged,
          activeColor: c,
          trackColor: WidgetStateProperty.resolveWith((s) =>
            s.contains(WidgetState.selected) ? c.withOpacity(0.25) : C.t3.withOpacity(0.2)),
          thumbColor: WidgetStateProperty.resolveWith((s) =>
            s.contains(WidgetState.selected) ? c : C.t3),
        )),
      ]),
    );
  }
}

// ── Animated mesh background ─────────────────────
class MeshBg extends StatelessWidget {
  const MeshBg({super.key});

  @override
  Widget build(BuildContext context) {
    return Stack(children: [
      Positioned(top: -120, left: -80, child: _glow(C.violet, 320, 0.12)),
      Positioned(top: 200, right: -100, child: _glow(C.cyan, 280, 0.07)),
      Positioned(bottom: -60, left: 60, child: _glow(C.indigo, 240, 0.09)),
    ]);
  }

  Widget _glow(Color c, double size, double opacity) => Container(
    width: size, height: size,
    decoration: BoxDecoration(
      shape: BoxShape.circle,
      gradient: RadialGradient(colors: [c.withOpacity(opacity), Colors.transparent]),
    ),
  );
}

// ── Position Grid ─────────────────────────────────
class PositionGrid extends StatelessWidget {
  final String value;
  final ValueChanged<String> onChanged;
  const PositionGrid({super.key, required this.value, required this.onChanged});

  static const _pos = [
    'top_left','top_center','top_right',
    'middle_left','center','middle_right',
    'bottom_left','bottom_center','bottom_right',
  ];

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(6),
      decoration: BoxDecoration(
        color: C.glass2, borderRadius: BorderRadius.circular(14),
        border: Border.all(color: C.bdr2)),
      child: GridView.count(
        crossAxisCount: 3, shrinkWrap: true,
        physics: const NeverScrollableScrollPhysics(),
        mainAxisSpacing: 5, crossAxisSpacing: 5, childAspectRatio: 2.4,
        children: _pos.map((p) {
          final on = value == p;
          return GestureDetector(
            onTap: () => onChanged(p),
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 200),
              curve: Curves.easeOutBack,
              decoration: BoxDecoration(
                gradient: on ? const LinearGradient(colors: C.grad1) : null,
                color: on ? null : C.glass2,
                borderRadius: BorderRadius.circular(8),
                boxShadow: on ? [BoxShadow(color: C.violet.withOpacity(0.4), blurRadius: 8)] : [],
              ),
              child: on ? const Center(child: Icon(Icons.check_rounded, color: Colors.white, size: 12)) : null,
            ),
          );
        }).toList(),
      ),
    );
  }
}
