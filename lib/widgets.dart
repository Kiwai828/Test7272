import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'theme.dart';
import 'models.dart';

// ── Done / Download Sheet ─────────────────────────
class DoneSheet extends StatelessWidget {
  final String url;
  const DoneSheet({super.key, required this.url});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.fromLTRB(24, 0, 24, 0),
      child: Column(mainAxisSize: MainAxisSize.min, children: [
        Container(width: 36, height: 4, margin: const EdgeInsets.symmetric(vertical: 14),
          decoration: BoxDecoration(color: C.bdr, borderRadius: BorderRadius.circular(2))),

        // Success icon with glow
        Container(width: 70, height: 70,
          decoration: BoxDecoration(
            color: C.mint.withOpacity(0.1), shape: BoxShape.circle,
            boxShadow: [BoxShadow(color: C.mint.withOpacity(0.25), blurRadius: 24)]),
          child: const Icon(Icons.check_circle_rounded, color: C.mint, size: 38)),

        const SizedBox(height: 16),
        const Text('Video ပြီးဆုံးပြီ! 🎉',
          style: TextStyle(color: C.t1, fontSize: 22, fontWeight: FontWeight.w800)),
        const SizedBox(height: 8),
        const Text('Download link ကို copy ကူးပြီး browser မှာ ဖွင့်ပါ',
          style: TextStyle(color: C.t2, fontSize: 13), textAlign: TextAlign.center),

        const SizedBox(height: 20),

        // URL box
        GlassCard(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
          borderColor: C.violet.withOpacity(0.2),
          child: SelectableText(url, style: const TextStyle(color: C.violet, fontSize: 11))),

        const SizedBox(height: 16),

        // Copy button
        GradBtn(
          label: 'Link ကူးရန်',
          icon: Icons.copy_rounded,
          colors: C.grad1,
          onTap: () {
            Clipboard.setData(ClipboardData(text: url));
            HapticFeedback.lightImpact();
            ScaffoldMessenger.of(context).showSnackBar(SnackBar(
              content: const Text('Link ကူးပြီးပြီ!', style: TextStyle(color: Colors.white, fontWeight: FontWeight.w600)),
              backgroundColor: C.mint, behavior: SnackBarBehavior.floating,
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
              margin: const EdgeInsets.all(16)));
          },
        ),
        const SizedBox(height: 32),
      ]),
    );
  }
}

// ── Topup / Packages Sheet ────────────────────────
class TopupSheet extends StatelessWidget {
  final UserInfo user;
  const TopupSheet({super.key, required this.user});

  @override
  Widget build(BuildContext context) {
    return DraggableScrollableSheet(
      initialChildSize: 0.6, maxChildSize: 0.92, minChildSize: 0.4,
      expand: false,
      builder: (_, ctrl) => Column(children: [
        Container(width: 36, height: 4, margin: const EdgeInsets.symmetric(vertical: 14),
          decoration: BoxDecoration(color: C.bdr, borderRadius: BorderRadius.circular(2))),

        Padding(padding: const EdgeInsets.symmetric(horizontal: 20),
          child: Row(children: [
            Container(width: 36, height: 36,
              decoration: BoxDecoration(gradient: const LinearGradient(colors: C.gradGold), borderRadius: BorderRadius.circular(10)),
              child: const Icon(Icons.monetization_on_rounded, color: Colors.white, size: 18)),
            const SizedBox(width: 12),
            const Text('💎 Coins ဖြည့်ရန်', style: TextStyle(color: C.t1, fontSize: 19, fontWeight: FontWeight.w800)),
            const Spacer(),
            // Current balance
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
              decoration: BoxDecoration(color: C.gold.withOpacity(0.12), borderRadius: BorderRadius.circular(20), border: Border.all(color: C.gold.withOpacity(0.25))),
              child: Row(children: [
                const Icon(Icons.monetization_on_rounded, color: C.gold, size: 13),
                const SizedBox(width: 4),
                Text('${user.coins}', style: const TextStyle(color: C.gold, fontWeight: FontWeight.w800, fontSize: 13)),
              ])),
          ])),

        const SizedBox(height: 16),

        Expanded(child: ListView(controller: ctrl, padding: const EdgeInsets.symmetric(horizontal: 20),
          children: [

            // Pricing tiers
            if (user.pricingTiers.isNotEmpty) ...[
              const Text('VIDEO PRICING', style: TextStyle(color: C.t3, fontSize: 11, fontWeight: FontWeight.w700, letterSpacing: 1)),
              const SizedBox(height: 10),
              ...user.pricingTiers.map((t) => Container(
                margin: const EdgeInsets.only(bottom: 8),
                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 11),
                decoration: BoxDecoration(
                  color: C.glass2, borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: C.bdr)),
                child: Row(children: [
                  const Icon(Icons.timer_outlined, color: C.t3, size: 14),
                  const SizedBox(width: 8),
                  Text('${t['max_seconds']}s အောက် video', style: const TextStyle(color: C.t1, fontSize: 13)),
                  const Spacer(),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 3),
                    decoration: BoxDecoration(color: C.gold.withOpacity(0.1), borderRadius: BorderRadius.circular(20)),
                    child: Row(children: [
                      const Icon(Icons.monetization_on_rounded, color: C.gold, size: 11),
                      const SizedBox(width: 3),
                      Text('${t['cost']} coins', style: const TextStyle(color: C.gold, fontSize: 11, fontWeight: FontWeight.w800)),
                    ])),
                ]))),
              const SizedBox(height: 16),
            ],

            // Packages
            if (user.packages.isNotEmpty) ...[
              const Text('PACKAGES', style: TextStyle(color: C.t3, fontSize: 11, fontWeight: FontWeight.w700, letterSpacing: 1)),
              const SizedBox(height: 10),
              ...user.packages.map((p) => Container(
                margin: const EdgeInsets.only(bottom: 8),
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    colors: [C.violet.withOpacity(0.1), C.indigo.withOpacity(0.05)]),
                  borderRadius: BorderRadius.circular(14),
                  border: Border.all(color: C.violet.withOpacity(0.2))),
                child: Row(children: [
                  Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Text('${p['name'] ?? 'Package'}', style: const TextStyle(color: C.t1, fontWeight: FontWeight.w700, fontSize: 14)),
                    const SizedBox(height: 4),
                    // Coins amount
                    Row(children: [
                      const Icon(Icons.monetization_on_rounded, color: C.gold, size: 13),
                      const SizedBox(width: 4),
                      Text('${p['coins'] ?? p['coin'] ?? p['amount'] ?? '?'} Coins ရမည်',
                        style: const TextStyle(color: C.gold, fontSize: 12, fontWeight: FontWeight.w700)),
                    ]),
                  ])),
                  // Price
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                    decoration: BoxDecoration(
                      color: C.violet.withOpacity(0.15),
                      borderRadius: BorderRadius.circular(10),
                      border: Border.all(color: C.violet.withOpacity(0.3))),
                    child: Text('${p['price'] ?? ''}',
                      style: const TextStyle(color: C.violet, fontWeight: FontWeight.w800, fontSize: 14))),
                ]))),
              const SizedBox(height: 16),
            ],

            // Payment info
            GlassCard(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              const Text('PAYMENT INFO', style: TextStyle(color: C.t3, fontSize: 11, fontWeight: FontWeight.w700, letterSpacing: 1)),
              const SizedBox(height: 10),
              if (user.paymentMsg.isNotEmpty)
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(color: C.glass2, borderRadius: BorderRadius.circular(10)),
                  child: Text(user.paymentMsg, style: const TextStyle(color: C.t1, fontSize: 12, height: 1.6, fontFamily: 'monospace'))),
              const SizedBox(height: 14),
              GradBtn(
                label: 'Admin ကိုဆက်သွယ်ရန်',
                icon: Icons.telegram,
                colors: [const Color(0xFF0088cc), const Color(0xFF006aaa)],
                onTap: () {},
              ),
            ])),

            const SizedBox(height: 32),
          ])),
      ]),
    );
  }
}
