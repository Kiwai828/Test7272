import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

// ══════════════════════════════════
// RECAP MAKER — Design System
// Dark, premium, video-editor feel
// ══════════════════════════════════

class AppTheme {
  // Core palette
  static const bg       = Color(0xFF07080F);   // near-black
  static const surface  = Color(0xFF0E1020);   // card bg
  static const surface2 = Color(0xFF161829);   // elevated card
  static const border   = Color(0xFF1E2240);   // subtle border
  static const primary  = Color(0xFF6C63FF);   // purple accent
  static const primary2 = Color(0xFF4F46E5);   // deeper purple
  static const teal     = Color(0xFF00D9C6);   // teal accent
  static const red      = Color(0xFFFF4F6B);   // danger/warning
  static const gold     = Color(0xFFFFB800);   // coins
  static const green    = Color(0xFF00E299);   // success

  static const textHi   = Color(0xFFEEF0FF);   // primary text
  static const textMid  = Color(0xFF8890B5);   // secondary text
  static const textLow  = Color(0xFF3D4266);   // dim text

  static ThemeData get theme => ThemeData(
    useMaterial3: true,
    brightness: Brightness.dark,
    scaffoldBackgroundColor: bg,
    colorScheme: ColorScheme.dark(
      primary: primary,
      secondary: teal,
      surface: surface,
      error: red,
    ),
    fontFamily: 'NotoSansMyanmar',
    appBarTheme: const AppBarTheme(
      backgroundColor: bg,
      elevation: 0,
      scrolledUnderElevation: 0,
      systemOverlayStyle: SystemUiOverlayStyle(
        statusBarColor: Colors.transparent,
        statusBarIconBrightness: Brightness.light,
      ),
    ),
    bottomNavigationBarTheme: const BottomNavigationBarThemeData(
      backgroundColor: Colors.transparent,
      elevation: 0,
    ),
  );
}

// ══════════════════════════════════
// Reusable widgets
// ══════════════════════════════════

class AppCard extends StatelessWidget {
  final Widget child;
  final EdgeInsets? padding;
  final Color? color;
  final VoidCallback? onTap;
  const AppCard({super.key, required this.child, this.padding, this.color, this.onTap});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: padding ?? const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: color ?? AppTheme.surface,
          borderRadius: BorderRadius.circular(18),
          border: Border.all(color: AppTheme.border, width: 1),
        ),
        child: child,
      ),
    );
  }
}

class SectionLabel extends StatelessWidget {
  final String text;
  final IconData icon;
  final Color? iconColor;
  const SectionLabel(this.text, {super.key, required this.icon, this.iconColor});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: Row(children: [
        Icon(icon, size: 14, color: iconColor ?? AppTheme.primary),
        const SizedBox(width: 6),
        Text(text, style: const TextStyle(
          color: AppTheme.textMid,
          fontSize: 12,
          fontWeight: FontWeight.w700,
          letterSpacing: 0.8,
        )),
      ]),
    );
  }
}

class AppToggleRow extends StatelessWidget {
  final String label;
  final IconData icon;
  final bool value;
  final ValueChanged<bool> onChanged;
  final Color? iconColor;
  const AppToggleRow({super.key, required this.label, required this.icon, required this.value, required this.onChanged, this.iconColor});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(children: [
        Container(
          width: 32, height: 32,
          decoration: BoxDecoration(
            color: (iconColor ?? AppTheme.primary).withOpacity(0.1),
            borderRadius: BorderRadius.circular(8),
          ),
          child: Icon(icon, size: 16, color: iconColor ?? AppTheme.primary),
        ),
        const SizedBox(width: 12),
        Expanded(child: Text(label, style: const TextStyle(color: AppTheme.textHi, fontSize: 14))),
        Switch.adaptive(
          value: value,
          onChanged: onChanged,
          activeColor: AppTheme.primary,
          trackColor: WidgetStateProperty.resolveWith((s) =>
            s.contains(WidgetState.selected)
              ? AppTheme.primary.withOpacity(0.3)
              : AppTheme.border),
        ),
      ]),
    );
  }
}

class PrimaryButton extends StatelessWidget {
  final String label;
  final IconData icon;
  final VoidCallback? onPressed;
  final bool loading;
  final Color? color;
  const PrimaryButton({super.key, required this.label, required this.icon, this.onPressed, this.loading = false, this.color});

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.infinity,
      height: 52,
      child: DecoratedBox(
        decoration: BoxDecoration(
          gradient: LinearGradient(
            colors: color != null
              ? [color!, color!.withOpacity(0.8)]
              : [AppTheme.primary, AppTheme.primary2],
          ),
          borderRadius: BorderRadius.circular(14),
          boxShadow: [BoxShadow(color: (color ?? AppTheme.primary).withOpacity(0.35), blurRadius: 16, offset: const Offset(0, 6))],
        ),
        child: Material(
          color: Colors.transparent,
          child: InkWell(
            onTap: loading ? null : onPressed,
            borderRadius: BorderRadius.circular(14),
            child: Center(
              child: loading
                ? const SizedBox(width: 22, height: 22, child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2.5))
                : Row(mainAxisSize: MainAxisSize.min, children: [
                    Icon(icon, color: Colors.white, size: 18),
                    const SizedBox(width: 8),
                    Text(label, style: const TextStyle(color: Colors.white, fontSize: 16, fontWeight: FontWeight.w700)),
                  ]),
            ),
          ),
        ),
      ),
    );
  }
}
