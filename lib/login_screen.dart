import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'theme.dart';
import 'providers.dart';
import 'main_shell.dart';

class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key});
  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> with SingleTickerProviderStateMixin {
  final _user = TextEditingController();
  final _pass = TextEditingController();
  bool _obscure = true;
  late AnimationController _anim;
  late Animation<double> _fade, _slide;

  @override
  void initState() {
    super.initState();
    _anim = AnimationController(vsync: this, duration: const Duration(milliseconds: 800));
    _fade = CurvedAnimation(parent: _anim, curve: Curves.easeOut);
    _slide = Tween<double>(begin: 30, end: 0).animate(CurvedAnimation(parent: _anim, curve: Curves.easeOutCubic));
    _anim.forward();
  }

  @override
  void dispose() { _anim.dispose(); _user.dispose(); _pass.dispose(); super.dispose(); }

  Future<void> _login() async {
    if (_user.text.trim().isEmpty || _pass.text.trim().isEmpty) return;
    final ok = await ref.read(authProvider.notifier).login(_user.text.trim(), _pass.text.trim());
    if (!mounted) return;
    if (ok) {
      Navigator.of(context).pushReplacement(
        PageRouteBuilder(
          pageBuilder: (_, a, __) => const MainShell(),
          transitionsBuilder: (_, a, __, child) => FadeTransition(opacity: a, child: child),
          transitionDuration: const Duration(milliseconds: 400),
        ),
      );
    } else {
      final err = ref.read(authProvider).error ?? 'Error';
      HapticFeedback.mediumImpact();
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text(err),
        backgroundColor: AppTheme.red,
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        margin: const EdgeInsets.all(16),
      ));
    }
  }

  @override
  Widget build(BuildContext context) {
    final auth = ref.watch(authProvider);
    final loading = auth.status == AuthStatus.loading;

    return Scaffold(
      backgroundColor: AppTheme.bg,
      body: Stack(children: [
        // Background glow
        Positioned(top: -100, left: -80, child: Container(
          width: 300, height: 300,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            gradient: RadialGradient(colors: [
              AppTheme.primary.withOpacity(0.15),
              Colors.transparent,
            ]),
          ),
        )),
        Positioned(bottom: -60, right: -60, child: Container(
          width: 240, height: 240,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            gradient: RadialGradient(colors: [
              AppTheme.teal.withOpacity(0.08),
              Colors.transparent,
            ]),
          ),
        )),
        SafeArea(
          child: AnimatedBuilder(
            animation: _anim,
            builder: (_, __) => Transform.translate(
              offset: Offset(0, _slide.value),
              child: FadeTransition(
                opacity: _fade,
                child: SingleChildScrollView(
                  padding: const EdgeInsets.symmetric(horizontal: 28),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const SizedBox(height: 70),
                      // Logo
                      Container(
                        width: 64, height: 64,
                        decoration: BoxDecoration(
                          gradient: LinearGradient(
                            colors: [AppTheme.primary, AppTheme.primary2],
                            begin: Alignment.topLeft, end: Alignment.bottomRight,
                          ),
                          borderRadius: BorderRadius.circular(18),
                          boxShadow: [BoxShadow(color: AppTheme.primary.withOpacity(0.4), blurRadius: 20, offset: const Offset(0, 8))],
                        ),
                        child: const Icon(Icons.video_camera_back_rounded, color: Colors.white, size: 32),
                      ),
                      const SizedBox(height: 28),
                      const Text('Recap Maker', style: TextStyle(
                        color: AppTheme.textHi, fontSize: 32, fontWeight: FontWeight.w800, height: 1.1,
                      )),
                      const SizedBox(height: 6),
                      const Text('Account နဲ့ Login ဝင်ပါ', style: TextStyle(
                        color: AppTheme.textMid, fontSize: 16,
                      )),
                      const SizedBox(height: 48),

                      // Username
                      _label('Username'),
                      _field(controller: _user, hint: 'Username ထည့်ပါ',
                        icon: Icons.person_outline_rounded, enabled: !loading),
                      const SizedBox(height: 16),

                      // Password
                      _label('Password'),
                      _field(
                        controller: _pass, hint: 'Password ထည့်ပါ',
                        icon: Icons.lock_outline_rounded, enabled: !loading,
                        obscure: _obscure,
                        suffix: IconButton(
                          icon: Icon(_obscure ? Icons.visibility_off_outlined : Icons.visibility_outlined,
                            color: AppTheme.textLow, size: 20),
                          onPressed: () => setState(() => _obscure = !_obscure),
                        ),
                        onSubmit: (_) => _login(),
                      ),
                      const SizedBox(height: 36),

                      // Button
                      PrimaryButton(
                        label: 'Login ဝင်မည်',
                        icon: Icons.arrow_forward_rounded,
                        loading: loading,
                        onPressed: _login,
                      ),
                      const SizedBox(height: 24),

                      Center(child: Text('Account မရှိသေးဘူးဆိုရင် Admin ကိုဆက်သွယ်ပါ',
                        style: const TextStyle(color: AppTheme.textLow, fontSize: 12),
                        textAlign: TextAlign.center)),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      ]),
    );
  }

  Widget _label(String t) => Padding(
    padding: const EdgeInsets.only(bottom: 8),
    child: Text(t, style: const TextStyle(color: AppTheme.textMid, fontSize: 13, fontWeight: FontWeight.w600)),
  );

  Widget _field({
    required TextEditingController controller,
    required String hint,
    required IconData icon,
    bool obscure = false,
    bool enabled = true,
    Widget? suffix,
    ValueChanged<String>? onSubmit,
  }) {
    return TextField(
      controller: controller,
      obscureText: obscure,
      enabled: enabled,
      onSubmitted: onSubmit,
      textInputAction: suffix == null ? TextInputAction.go : TextInputAction.next,
      style: const TextStyle(color: AppTheme.textHi, fontSize: 15),
      decoration: InputDecoration(
        hintText: hint,
        hintStyle: const TextStyle(color: AppTheme.textLow, fontSize: 14),
        prefixIcon: Icon(icon, color: AppTheme.textLow, size: 20),
        suffixIcon: suffix,
        filled: true,
        fillColor: AppTheme.surface,
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
        border: OutlineInputBorder(borderRadius: BorderRadius.circular(14), borderSide: const BorderSide(color: AppTheme.border)),
        enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(14), borderSide: const BorderSide(color: AppTheme.border)),
        focusedBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(14), borderSide: const BorderSide(color: AppTheme.primary, width: 1.5)),
      ),
    );
  }
}
