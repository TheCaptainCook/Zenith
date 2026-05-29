import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

class MyAppletsTab extends StatelessWidget {
  const MyAppletsTab({super.key});

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.all(24.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Text('My Applets', style: TextStyle(color: Colors.white, fontSize: 32, fontWeight: FontWeight.bold)),
                IconButton(
                  onPressed: () => context.go('/applets/new'),
                  icon: const Icon(Icons.add, color: Colors.white),
                  style: IconButton.styleFrom(backgroundColor: const Color(0xFF4F46E5)),
                ),
              ],
            ),
            const SizedBox(height: 32),
            Expanded(
              child: Center(
                child: Text('You haven\'t created any Applets yet.', style: TextStyle(color: Colors.white38)),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
