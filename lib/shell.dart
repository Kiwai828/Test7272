import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'theme.dart';
import 'state.dart';
import 'dashboard_screen.dart';
import 'subtitle_screen.dart';
import 'history_screen.dart';

class Shell extends ConsumerStatefulWidget {
  const Shell({super.key});
  @override
  ConsumerState<Shell> createState() => _ShellState();
}

class _ShellState extends ConsumerState<Shell> with TickerProviderStateMixin {
  int _tab = 0;
  late List<AnimationController> _ctrls;
  late List<Animation<double>> _scales;

  @override
  void initState() {
    super.initState();
    ref.read(userProvider.notifier).load();

    _ctrls = List.generate(3, (_) =>
      AnimationController(vsync: this, duration: const Duration(milliseconds: 200)));
    _scales = _ctrls.map((c) =>
      Tween(begin: 1.0, end: 0.92).animate(CurvedAnimation(parent: c, curve: Curves.easeOut))).toList();
    _ctrls[0].forward();
  }

  @override
  void dispose() { for (final c in _ctrls) c.dispose(); super.dispose(); }

  void _setTab(int i) {
    if (i == _tab) return;
    HapticFeedback.selectionClick();
    setState(() => _tab = i);
  }

  static const _screens = [DashboardScreen(), SubtitleScreen(), HistoryScreen()];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: C.bg0,
      body: Stack(children: [
        // Animated screen switcher
        AnimatedSwitcher(
          duration: const Duration(milliseconds: 350),
          switchInCurve: Curves.easeOutCubic,
          switchOutCurve: Curves.easeInCubic,
          transitionBuilder: (child, anim) => FadeTransition(
            opacity: anim,
            child: SlideTransition(
              position: Tween(begin: const Offset(0, 0.03), end: Offset.zero)
                .animate(CurvedAnimation(parent: anim, curve: Curves.easeOutCubic)),
              child: child)),
          child: KeyedSubtree(key: ValueKey(_tab), child: _screens[_tab]),
        ),
      ]),
      extendBody: true,
      bottomNavigationBar: _NavBar(tab: _tab, onTap: _setTab, scales: _scales, ctrls: _ctrls),
    );
  }
}

class _NavBar extends StatelessWidget {
  final int tab;
  final ValueChanged<int> onTap;
  final List<Animation<double>> scales;
  final List<AnimationController> ctrls;
  const _NavBar({required this.tab, required this.onTap, required this.scales, required this.ctrls});

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: C.bg1,
        border: Border(top: BorderSide(color: C.bdr, width: 0.5)),
        boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.4), blurRadius: 20, offset: const Offset(0, -4))],
      ),
      child: SafeArea(
        top: false,
        child: SizedBox(height: 58,
          child: Row(children: [
            _item(0, CupertinoIcons.house_fill, CupertinoIcons.house, 'Dashboard'),
            _item(1, CupertinoIcons.captions_bubble_fill, CupertinoIcons.captions_bubble, 'Subtitle'),
            _item(2, CupertinoIcons.clock_fill, CupertinoIcons.clock, 'History'),
          ]),
        ),
      ),
    );
  }

  Widget _item(int i, IconData activeIco, IconData ico, String label) {
    final active = tab == i;
    return Expanded(
      child: GestureDetector(
        onTap: () => onTap(i),
        behavior: HitTestBehavior.opaque,
        child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
          AnimatedContainer(
            duration: const Duration(milliseconds: 250),
            curve: Curves.easeOutBack,
            padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 5),
            decoration: BoxDecoration(
              color: active ? C.violet.withOpacity(0.15) : Colors.transparent,
              borderRadius: BorderRadius.circular(12),
            ),
            child: Icon(active ? activeIco : ico,
              color: active ? C.violet : C.t3, size: 22),
          ),
          const SizedBox(height: 2),
          AnimatedDefaultTextStyle(
            duration: const Duration(milliseconds: 200),
            style: TextStyle(
              color: active ? C.violet : C.t3,
              fontSize: 10,
              fontWeight: active ? FontWeight.w700 : FontWeight.w400),
            child: Text(label)),
        ]),
      ),
    );
  }
}
