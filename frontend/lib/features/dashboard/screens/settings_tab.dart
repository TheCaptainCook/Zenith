import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../auth/providers/auth_provider.dart';

class SettingsTab extends ConsumerWidget {
  const SettingsTab({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.all(24.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Settings', style: TextStyle(color: Colors.white, fontSize: 32, fontWeight: FontWeight.bold)),
            const SizedBox(height: 32),
            ListTile(
              title: const Text('Builder Preference', style: TextStyle(color: Colors.white)),
              subtitle: const Text('Canvas or Wizard', style: TextStyle(color: Colors.white54)),
              trailing: const Icon(Icons.arrow_forward_ios, color: Colors.white54, size: 16),
              onTap: () {
                // TODO: Update user preference via API
                ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Preferences update coming soon!')));
              },
            ),
            const Divider(color: Colors.white12),
            ListTile(
              title: const Text('Sign Out', style: TextStyle(color: Colors.redAccent)),
              leading: const Icon(Icons.logout, color: Colors.redAccent),
              onTap: () {
                ref.read(authProvider.notifier).logout();
              },
            ),
          ],
        ),
      ),
    );
  }
}
