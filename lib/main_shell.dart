import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'theme.dart';
import 'dashboard_screen.dart';
import 'subtitle_screen.dart';
import 'history_screen.dart';

class MainShell extends ConsumerStatefulWidget {
  const MainShell({super.key});
  @override
  ConsumerState<MainShell> createState() => _MainShellState();
}

class _MainShellState extends ConsumerState<MainShell> {
  int _tab = 0;

  final _screens = const [
    DashboardScreen(),
    SubtitleScreen(),
    HistoryScreen(),
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.bg,
      body: IndexedStack(index: _tab, children: _screens),
      bottomNavigationBar: _buildTabBar(),
    );
  }

  Widget _buildTabBar() {
    return Container(
      decoration: BoxDecoration(
        color: AppTheme.surface,
        border: Border(top: BorderSide(color: AppTheme.border, width: 1)),
      ),
      child: SafeArea(
        top: false,
        child: SizedBox(
          height: 60,
          child: Row(children: [
            _tab_(0, CupertinoIcons.house_fill, CupertinoIcons.house, 'Dashboard'),
            _tab_(1, CupertinoIcons.captions_bubble_fill, CupertinoIcons.captions_bubble, 'Subtitles'),
            _tab_(2, CupertinoIcons.clock_fill, CupertinoIcons.clock, 'History'),
          ]),
        ),
      ),
    );
  }

  Widget _tab_(int idx, IconData activeIcon, IconData inactiveIcon, String label) {
    final active = _tab == idx;
    return Expanded(
      child: GestureDetector(
        onTap: () => setState(() => _tab = idx),
        behavior: HitTestBehavior.opaque,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 200),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              AnimatedContainer(
                duration: const Duration(milliseconds: 200),
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 5),
                decoration: BoxDecoration(
                  color: active ? AppTheme.primary.withOpacity(0.15) : Colors.transparent,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Icon(
                  active ? activeIcon : inactiveIcon,
                  color: active ? AppTheme.primary : AppTheme.textLow,
                  size: 22,
                ),
              ),
              const SizedBox(height: 3),
              Text(label, style: TextStyle(
                color: active ? AppTheme.primary : AppTheme.textLow,
                fontSize: 10,
                fontWeight: active ? FontWeight.w700 : FontWeight.w400,
              )),
            ],
          ),
        ),
      ),
    );
  }
}
