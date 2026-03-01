import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
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
        transitionsBuilder: (_, anim, __, child) => FadeTransition(opacity: anim,
          child: SlideTransition(
            position: Tween(begin: const Offset(0, 0.04), end: Offset.zero)
                .animate(CurvedAnimation(parent: anim, curve: Curves.easeOutCubic)),
            child: child))));
    } else {
      HapticFeedback.mediumImpact();
      final err = ref.read(authProvider).error ?? 'Error';
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text(err, style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w600)),
        backgroundColor: C.rose, behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        margin: const EdgeInsets.all(16)));
    }
  }

  @override
  Widget build(BuildContext context) {
    final loading = ref.watch(authProvider).status == AuthStatus.loading;
    return Scaffold(
      backgroundColor: C.bg0,
      body: Stack(children: [
        AnimatedBuilder(animation: _bgScale, builder: (_, __) =>
          Transform.scale(scale: _bgScale.value, child: const MeshBg())),
        SafeArea(child: FadeTransition(opacity: _fadeIn,
          child: SlideTransition(position: _slideUp,
            child: SingleChildScrollView(
              padding: const EdgeInsets.symmetric(horizontal: 24),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                const SizedBox(height: 70),

                // Logo
                Row(children: [
                  Container(width: 52, height: 52,
                    decoration: BoxDecoration(
                      gradient: const LinearGradient(colors: C.grad1, begin: Alignment.topLeft, end: Alignment.bottomRight),
                      borderRadius: BorderRadius.circular(16),
                      boxShadow: [BoxShadow(color: C.violet.withOpacity(0.5), blurRadius: 24, offset: const Offset(0, 10))]),
                    child: const Icon(Icons.video_camera_back_rounded, color: Colors.white, size: 26)),
                  const SizedBox(width: 14),
                  Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    const Text('Recap Maker', style: TextStyle(color: C.t1, fontSize: 22, fontWeight: FontWeight.w900, letterSpacing: -0.5)),
                    Text('Pro Video Editor', style: TextStyle(color: C.violet.withOpacity(0.8), fontSize: 12, fontWeight: FontWeight.w600)),
                  ]),
                ]),

                const SizedBox(height: 56),

                const Text('Login ဝင်ပါ', style: TextStyle(color: C.t1, fontSize: 30, fontWeight: FontWeight.w900, height: 1.1)),
                const SizedBox(height: 6),
                const Text('Account ရှိပြီးသားဆိုရင် ဝင်ပါ', style: TextStyle(color: C.t3, fontSize: 13)),

                const SizedBox(height: 36),

                // Username
                _fieldLabel('Username'),
                _field(ctrl: _u, hint: 'Username ထည့်ပါ', icon: Icons.person_outline_rounded, enabled: !loading),
                const SizedBox(height: 14),

                // Password
                _fieldLabel('Password'),
                _field(
                  ctrl: _p, hint: 'Password ထည့်ပါ', icon: Icons.lock_outline_rounded,
                  enabled: !loading, obscure: _hide, onSubmit: (_) => _login(),
                  suffix: GestureDetector(
                    onTap: () => setState(() => _hide = !_hide),
                    child: Icon(_hide ? Icons.visibility_off_outlined : Icons.visibility_outlined, color: C.t3, size: 18))),

                const SizedBox(height: 36),

                GradBtn(label: 'Login ဝင်မည်', icon: Icons.arrow_forward_rounded,
                  colors: C.grad1, loading: loading, onTap: loading ? null : _login),

                const SizedBox(height: 20),

                // Divider
                const Row(children: [
                  Expanded(child: Divider(color: C.bdr)),
                  Padding(padding: EdgeInsets.symmetric(horizontal: 14),
                    child: Text('Account မရှိဘူးလား?', style: TextStyle(color: C.t3, fontSize: 12))),
                  Expanded(child: Divider(color: C.bdr)),
                ]),

                const SizedBox(height: 16),

                // Register button → Bot link
                GestureDetector(
                  onTap: () {
                    // Copy register link
                    Clipboard.setData(ClipboardData(text: AppConstants.registerUrl));
                    ScaffoldMessenger.of(context).showSnackBar(SnackBar(
                      content: const Text('Register link ကူးပြီးပြီ! Browser မှာ paste လုပ်ပါ',
                        style: TextStyle(color: Colors.white, fontWeight: FontWeight.w600)),
                      backgroundColor: C.cyan, behavior: SnackBarBehavior.floating,
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                      margin: const EdgeInsets.all(16)));
                  },
                  child: Container(
                    width: double.infinity, height: 50,
                    decoration: BoxDecoration(
                      color: const Color(0xFF0088cc).withOpacity(0.1),
                      borderRadius: BorderRadius.circular(14),
                      border: Border.all(color: const Color(0xFF0088cc).withOpacity(0.3))),
                    child: Row(mainAxisAlignment: MainAxisAlignment.center, children: [
                      Icon(Icons.telegram, color: const Color(0xFF0088cc).withOpacity(0.9), size: 20),
                      const SizedBox(width: 8),
                      const Text('Telegram Bot မှ Register လုပ်ရန်',
                        style: TextStyle(color: Color(0xFF0088cc), fontWeight: FontWeight.w700, fontSize: 14)),
                    ]))),

                const SizedBox(height: 10),

                // Show register URL
                Center(child: Text(AppConstants.registerUrl,
                  style: TextStyle(color: C.t3.withOpacity(0.6), fontSize: 10),
                  overflow: TextOverflow.ellipsis)),

                const SizedBox(height: 40),
              ]))))),
      ]));
  }

  Widget _fieldLabel(String t) => Padding(
    padding: const EdgeInsets.only(bottom: 8),
    child: Text(t, style: const TextStyle(color: C.t2, fontSize: 12, fontWeight: FontWeight.w700, letterSpacing: 0.5)));

  Widget _field({
    required TextEditingController ctrl, required String hint,
    required IconData icon, bool obscure = false, bool enabled = true,
    Widget? suffix, ValueChanged<String>? onSubmit,
  }) {
    return Container(
      decoration: BoxDecoration(color: C.glass, borderRadius: BorderRadius.circular(14), border: Border.all(color: C.bdr)),
      child: TextField(
        controller: ctrl, obscureText: obscure, enabled: enabled, onSubmitted: onSubmit,
        style: const TextStyle(color: C.t1, fontSize: 15),
        decoration: InputDecoration(
          hintText: hint, hintStyle: const TextStyle(color: C.t3, fontSize: 14),
          prefixIcon: Icon(icon, color: C.t3, size: 18),
          suffixIcon: suffix != null ? Padding(padding: const EdgeInsets.only(right: 12), child: suffix) : null,
          border: InputBorder.none,
          contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16))));
  }
}
