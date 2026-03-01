import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';
import 'theme.dart';
import 'state.dart';
import 'shell.dart';
import 'constants.dart';

class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key});
  @override
  ConsumerState<LoginScreen> createState() => _S();
}

class _S extends ConsumerState<LoginScreen> with TickerProviderStateMixin {
  final _u = TextEditingController();
  final _p = TextEditingController();
  bool _hide = true;

  late AnimationController _bg, _content;
  late Animation<double> _bgScale, _fadeIn;
  late Animation<Offset> _slideUp;

  @override
  void initState() {
    super.initState();
    _bg = AnimationController(vsync: this, duration: const Duration(milliseconds: 1200))..forward();
    _content = AnimationController(vsync: this, duration: const Duration(milliseconds: 700));
    _bgScale = Tween(begin: 1.3, end: 1.0).animate(CurvedAnimation(parent: _bg, curve: Curves.easeOutCubic));
    _fadeIn = CurvedAnimation(parent: _content, curve: Curves.easeOut);
    _slideUp = Tween(begin: const Offset(0, 0.06), end: Offset.zero)
        .animate(CurvedAnimation(parent: _content, curve: Curves.easeOutCubic));
    Future.delayed(const Duration(milliseconds: 400), () { if (mounted) _content.forward(); });
  }

  @override
  void dispose() { _bg.dispose(); _content.dispose(); _u.dispose(); _p.dispose(); super.dispose(); }

  Future<void> _login() async {
    if (_u.text.trim().isEmpty || _p.text.trim().isEmpty) return;
    final ok = await ref.read(authProvider.notifier).login(_u.text.trim(), _p.text.trim());
    if (!mounted) return;
    if (ok) {
      Navigator.of(context).pushReplacement(PageRouteBuilder(
        transitionDuration: const Duration(milliseconds: 600),
        pageBuilder: (_, a, __) => const Shell(),
        transitionsBuilder: (_, anim, __, child) => FadeTransition(
          opacity: anim,
          child: SlideTransition(
            position: Tween(begin: const Offset(0, 0.04), end: Offset.zero)
                .animate(CurvedAnimation(parent: anim, curve: Curves.easeOutCubic)),
            child: child))));
    } else {
      HapticFeedback.mediumImpact();
      final err = ref.read(authProvider).error ?? 'Login မအောင်မြင်ပါ';
      _showSnack(err, C.rose);
    }
  }

  Future<void> _openRegister() async {
    // Try Telegram app first (tg:// scheme)
    final tgUrl = Uri.parse(AppConstants.registerUrl);
    // Convert https://t.me/xxx to tg://resolve?domain=xxx
    final botName = AppConstants.registerUrl.split('/').last;
    final tgAppUrl = Uri.parse('tg://resolve?domain=$botName');

    try {
      if (await canLaunchUrl(tgAppUrl)) {
        await launchUrl(tgAppUrl, mode: LaunchMode.externalApplication);
        return;
      }
    } catch (_) {}

    // Fallback to browser
    try {
      await launchUrl(tgUrl, mode: LaunchMode.externalApplication);
    } catch (_) {
      // Last resort: copy link
      await Clipboard.setData(ClipboardData(text: AppConstants.registerUrl));
      if (mounted) _showSnack('Link ကူးပြီးပြီ — Browser မှာ paste လုပ်ပါ', C.cyan);
    }
  }

  void _showSnack(String msg, Color c) => ScaffoldMessenger.of(context).showSnackBar(
    SnackBar(content: Text(msg, style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w600)),
      backgroundColor: c, behavior: SnackBarBehavior.floating,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      margin: const EdgeInsets.all(16)));

  @override
  Widget build(BuildContext context) {
    final loading = ref.watch(authProvider).status == AuthStatus.loading;
    return Scaffold(
      backgroundColor: C.bg0,
      body: Stack(children: [
        // Mesh background
        AnimatedBuilder(animation: _bgScale, builder: (_, __) =>
          Transform.scale(scale: _bgScale.value, child: const MeshBg())),

        SafeArea(child: FadeTransition(opacity: _fadeIn,
          child: SlideTransition(position: _slideUp,
            child: SingleChildScrollView(
              padding: const EdgeInsets.symmetric(horizontal: 24),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                const SizedBox(height: 60),

                // Logo mark
                Row(children: [
                  Container(width: 52, height: 52,
                    decoration: BoxDecoration(
                      gradient: const LinearGradient(colors: C.grad1, begin: Alignment.topLeft, end: Alignment.bottomRight),
                      borderRadius: BorderRadius.circular(16),
                      boxShadow: [BoxShadow(color: C.violet.withOpacity(0.5), blurRadius: 24, offset: const Offset(0, 10))]),
                    child: const Icon(Icons.video_camera_back_rounded, color: Colors.white, size: 26)),
                  const SizedBox(width: 14),
                  Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    const Text('Recap Maker', style: TextStyle(
                      color: Color(0xFFF5F7FF), fontSize: 22, fontWeight: FontWeight.w900, letterSpacing: -0.5)),
                    Text('Pro Video Editor', style: TextStyle(
                      color: C.violet.withOpacity(0.9), fontSize: 12, fontWeight: FontWeight.w600)),
                  ]),
                ]),

                const SizedBox(height: 52),

                const Text('ဝင်ရောက်ပါ', style: TextStyle(
                  color: Color(0xFFF5F7FF), fontSize: 30, fontWeight: FontWeight.w900)),
                const SizedBox(height: 6),
                const Text('Account ရှိပြီးသားဆိုရင် အောက်မှာ ဖြည့်ပါ', style: TextStyle(
                  color: Color(0xFFABB0CC), fontSize: 13)),

                const SizedBox(height: 32),

                // Username field
                _fieldLabel('Username'),
                _field(ctrl: _u, hint: 'Username', icon: Icons.person_outline_rounded, enabled: !loading),
                const SizedBox(height: 14),

                // Password field
                _fieldLabel('Password'),
                _field(
                  ctrl: _p, hint: 'Password', icon: Icons.lock_outline_rounded,
                  enabled: !loading, obscure: _hide, onSubmit: (_) => _login(),
                  suffix: GestureDetector(
                    onTap: () => setState(() => _hide = !_hide),
                    child: Icon(
                      _hide ? Icons.visibility_off_outlined : Icons.visibility_outlined,
                      color: const Color(0xFF7B84A8), size: 18))),

                const SizedBox(height: 32),

                // Login button
                GradBtn(
                  label: 'Login ဝင်မည်',
                  icon: Icons.arrow_forward_rounded,
                  colors: C.grad1,
                  loading: loading,
                  onTap: loading ? null : _login),

                const SizedBox(height: 24),

                // Divider
                Row(children: [
                  const Expanded(child: Divider(color: Color(0xFF2A2D45))),
                  Padding(padding: const EdgeInsets.symmetric(horizontal: 16),
                    child: const Text('Account မရှိသေးဘူးလား?',
                      style: TextStyle(color: Color(0xFF6B7094), fontSize: 12))),
                  const Expanded(child: Divider(color: Color(0xFF2A2D45))),
                ]),

                const SizedBox(height: 18),

                // Register → Open Telegram Bot directly
                GestureDetector(
                  onTap: _openRegister,
                  child: Container(
                    width: double.infinity, height: 52,
                    decoration: BoxDecoration(
                      color: const Color(0xFF0088CC).withOpacity(0.12),
                      borderRadius: BorderRadius.circular(14),
                      border: Border.all(color: const Color(0xFF0088CC).withOpacity(0.4))),
                    child: Row(mainAxisAlignment: MainAxisAlignment.center, children: [
                      Container(width: 28, height: 28,
                        decoration: BoxDecoration(
                          color: const Color(0xFF0088CC).withOpacity(0.15),
                          shape: BoxShape.circle),
                        child: const Icon(Icons.telegram, color: Color(0xFF29B6F6), size: 16)),
                      const SizedBox(width: 10),
                      const Column(mainAxisAlignment: MainAxisAlignment.center,
                        crossAxisAlignment: CrossAxisAlignment.start, children: [
                        Text('Telegram Bot မှ Register လုပ်ရန်',
                          style: TextStyle(color: Color(0xFF29B6F6), fontWeight: FontWeight.w700, fontSize: 14)),
                        Text('Telegram app ဖွင့်ပြီး Bot သို့ ရောက်မည်',
                          style: TextStyle(color: Color(0xFF5B9EC4), fontSize: 10)),
                      ]),
                      const Spacer(),
                      const Padding(padding: EdgeInsets.only(right: 14),
                        child: Icon(Icons.arrow_forward_ios_rounded, color: Color(0xFF0088CC), size: 13)),
                    ]))),

                const SizedBox(height: 40),
              ]))))),
      ]));
  }

  Widget _fieldLabel(String t) => Padding(
    padding: const EdgeInsets.only(bottom: 8),
    child: Text(t, style: const TextStyle(
      color: Color(0xFFCDD0E8), fontSize: 12, fontWeight: FontWeight.w700, letterSpacing: 0.5)));

  Widget _field({
    required TextEditingController ctrl, required String hint,
    required IconData icon, bool obscure = false, bool enabled = true,
    Widget? suffix, ValueChanged<String>? onSubmit,
  }) {
    return Container(
      decoration: BoxDecoration(
        color: const Color(0xFF0E1220),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: const Color(0xFF2A2D45))),
      child: TextField(
        controller: ctrl, obscureText: obscure, enabled: enabled, onSubmitted: onSubmit,
        style: const TextStyle(color: Color(0xFFF0F2FF), fontSize: 15),
        decoration: InputDecoration(
          hintText: hint,
          hintStyle: const TextStyle(color: Color(0xFF4A5070), fontSize: 14),
          prefixIcon: Icon(icon, color: const Color(0xFF6B7094), size: 18),
          suffixIcon: suffix != null ? Padding(padding: const EdgeInsets.only(right: 12), child: suffix) : null,
          border: InputBorder.none,
          contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16))));
  }
}
