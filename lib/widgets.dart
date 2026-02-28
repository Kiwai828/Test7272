import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'theme.dart';

// ── Position Grid (9-cell like website) ──────────────
class PositionGrid extends StatelessWidget {
  final String value;
  final ValueChanged<String> onChanged;

  const PositionGrid({super.key, required this.value, required this.onChanged});

  static const _positions = [
    'top_left', 'top_center', 'top_right',
    'middle_left', 'center', 'middle_right',
    'bottom_left', 'bottom_center', 'bottom_right',
  ];

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(6),
      decoration: BoxDecoration(
        color: AppTheme.surface2,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: AppTheme.border),
      ),
      child: GridView.count(
        crossAxisCount: 3,
        shrinkWrap: true,
        physics: const NeverScrollableScrollPhysics(),
        mainAxisSpacing: 5, crossAxisSpacing: 5,
        childAspectRatio: 2.2,
        children: _positions.map((pos) {
          final active = value == pos;
          return GestureDetector(
            onTap: () => onChanged(pos),
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 180),
              decoration: BoxDecoration(
                gradient: active ? const LinearGradient(
                  colors: [AppTheme.primary, AppTheme.primary2],
                  begin: Alignment.topLeft, end: Alignment.bottomRight,
                ) : null,
                color: active ? null : AppTheme.border.withOpacity(0.4),
                borderRadius: BorderRadius.circular(8),
                boxShadow: active ? [BoxShadow(color: AppTheme.primary.withOpacity(0.35), blurRadius: 8)] : null,
              ),
              child: active
                ? const Center(child: Icon(Icons.check_rounded, color: Colors.white, size: 14))
                : null,
            ),
          );
        }).toList(),
      ),
    );
  }
}

// ── Download bottom sheet ─────────────────────────────
class DownloadSheet extends StatelessWidget {
  final String url;
  const DownloadSheet({super.key, required this.url});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(24),
      child: Column(mainAxisSize: MainAxisSize.min, children: [
        Container(width: 36, height: 4,
          margin: const EdgeInsets.only(bottom: 20),
          decoration: BoxDecoration(color: AppTheme.border, borderRadius: BorderRadius.circular(2))),
        Container(
          width: 64, height: 64,
          decoration: BoxDecoration(
            color: AppTheme.green.withOpacity(0.1),
            shape: BoxShape.circle,
          ),
          child: const Icon(Icons.check_circle_rounded, color: AppTheme.green, size: 36),
        ),
        const SizedBox(height: 16),
        const Text('Video ပြီးဆုံးပြီ! 🎉', style: TextStyle(
          color: AppTheme.textHi, fontSize: 20, fontWeight: FontWeight.w800)),
        const SizedBox(height: 8),
        const Text('Download link ကို copy ကူးပြီး browser မှာ ဖွင့်ပါ',
          style: TextStyle(color: AppTheme.textMid, fontSize: 13), textAlign: TextAlign.center),
        const SizedBox(height: 20),
        Container(
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(
            color: AppTheme.surface,
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: AppTheme.border),
          ),
          child: SelectableText(url, style: const TextStyle(color: AppTheme.primary, fontSize: 12)),
        ),
        const SizedBox(height: 16),
        SizedBox(
          width: double.infinity,
          child: ElevatedButton.icon(
            onPressed: () {
              Clipboard.setData(ClipboardData(text: url));
              ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
                content: Text('Link ကူးပြီးပြီ! Browser မှာ paste လုပ်ပါ'),
                backgroundColor: AppTheme.green,
                behavior: SnackBarBehavior.floating,
              ));
            },
            style: ElevatedButton.styleFrom(
              backgroundColor: AppTheme.green, foregroundColor: Colors.white,
              padding: const EdgeInsets.symmetric(vertical: 14),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
            ),
            icon: const Icon(Icons.copy_rounded, size: 18),
            label: const Text('Link ကူးရန်', style: TextStyle(fontWeight: FontWeight.w700, fontSize: 15)),
          ),
        ),
        const SizedBox(height: 8),
      ]),
    );
  }
}

// ── Topup sheet ───────────────────────────────────────
class TopupSheet extends StatefulWidget {
  final List<dynamic> pricingTiers;
  const TopupSheet({super.key, required this.pricingTiers});
  @override
  State<TopupSheet> createState() => _TopupSheetState();
}

class _TopupSheetState extends State<TopupSheet> {
  List<dynamic> _packages = [];
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      // packages from API
      setState(() => _loading = false);
    } catch (_) { setState(() => _loading = false); }
  }

  @override
  Widget build(BuildContext context) {
    return DraggableScrollableSheet(
      initialChildSize: 0.55,
      maxChildSize: 0.9,
      minChildSize: 0.4,
      expand: false,
      builder: (_, ctrl) => Column(children: [
        Container(width: 36, height: 4, margin: const EdgeInsets.symmetric(vertical: 12),
          decoration: BoxDecoration(color: AppTheme.border, borderRadius: BorderRadius.circular(2))),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 20),
          child: Row(children: [
            const Icon(Icons.monetization_on_rounded, color: AppTheme.gold, size: 22),
            const SizedBox(width: 8),
            const Text('💎 Coins ဖြည့်ရန်', style: TextStyle(color: AppTheme.textHi, fontSize: 18, fontWeight: FontWeight.w800)),
          ]),
        ),
        const SizedBox(height: 4),
        // Pricing tiers info
        Expanded(
          child: ListView(controller: ctrl, padding: const EdgeInsets.all(20), children: [
            if (widget.pricingTiers.isNotEmpty) ...[
              const Text('Pricing', style: TextStyle(color: AppTheme.textMid, fontSize: 12, fontWeight: FontWeight.w600)),
              const SizedBox(height: 8),
              ...widget.pricingTiers.map((t) => Container(
                margin: const EdgeInsets.only(bottom: 6),
                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                decoration: BoxDecoration(
                  color: AppTheme.surface, borderRadius: BorderRadius.circular(10),
                  border: Border.all(color: AppTheme.border)),
                child: Row(children: [
                  const Icon(Icons.timer_rounded, color: AppTheme.textLow, size: 14),
                  const SizedBox(width: 8),
                  Text('${t['max_seconds']}s အောက်', style: const TextStyle(color: AppTheme.textHi, fontSize: 13)),
                  const Spacer(),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 3),
                    decoration: BoxDecoration(
                      color: AppTheme.gold.withOpacity(0.1), borderRadius: BorderRadius.circular(20)),
                    child: Row(children: [
                      const Icon(Icons.monetization_on_rounded, color: AppTheme.gold, size: 12),
                      const SizedBox(width: 4),
                      Text('${t['cost']} Coins', style: const TextStyle(color: AppTheme.gold, fontSize: 12, fontWeight: FontWeight.w700)),
                    ]),
                  ),
                ]),
              )).toList(),
              const SizedBox(height: 16),
            ],
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: AppTheme.surface, borderRadius: BorderRadius.circular(14),
                border: Border.all(color: AppTheme.border)),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                const Text('ငွေပေးချေရန်', style: TextStyle(color: AppTheme.textMid, fontSize: 12, fontWeight: FontWeight.w700)),
                const SizedBox(height: 8),
                const Text('Admin ကိုဆက်သွယ်ပြီး Coins ဖြည့်ပါ',
                  style: TextStyle(color: AppTheme.textHi, fontSize: 13)),
                const SizedBox(height: 12),
                SizedBox(
                  width: double.infinity,
                  child: ElevatedButton.icon(
                    onPressed: () {},
                    style: ElevatedButton.styleFrom(
                      backgroundColor: const Color(0xFF0088cc),
                      foregroundColor: Colors.white,
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                      padding: const EdgeInsets.symmetric(vertical: 12),
                    ),
                    icon: const Icon(Icons.telegram, size: 18),
                    label: const Text('Admin ကိုဆက်သွယ်ရန်', style: TextStyle(fontWeight: FontWeight.w700)),
                  ),
                ),
              ]),
            ),
          ]),
        ),
      ]),
    );
  }
}
