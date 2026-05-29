import 'package:flutter/material.dart';
import '../../../core/constants/api_constants.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:shared_preferences/shared_preferences.dart';

class ExploreTab extends StatelessWidget {
  const ExploreTab({super.key});

  Future<void> _startOAuth(BuildContext context, String provider) async {
    final prefs = await SharedPreferences.getInstance();
    final token = prefs.getString('jwt_token');
    if (token == null) return;

    final url = Uri.parse('${ApiConstants.baseUrl}/integrations/$provider/auth?token=$token');
    
    if (await canLaunchUrl(url)) {
      await launchUrl(url, mode: LaunchMode.externalApplication);
    } else {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Could not launch browser')));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.all(24.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Explore', style: TextStyle(color: Colors.white, fontSize: 32, fontWeight: FontWeight.bold)),
            const Text('Connect your favorite services', style: TextStyle(color: Colors.white54, fontSize: 16)),
            const SizedBox(height: 32),
            Expanded(
              child: GridView.count(
                crossAxisCount: 2,
                crossAxisSpacing: 16,
                mainAxisSpacing: 16,
                children: [
                  _buildIntegrationCard(context, 'MockOAuth', Icons.api, const Color(0xFF10B981), 'mockoauth'),
                  _buildIntegrationCard(context, 'GitHub', Icons.code, const Color(0xFF374151), 'github'),
                  _buildIntegrationCard(context, 'Slack', Icons.chat, const Color(0xFFEAB308), 'slack'),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildIntegrationCard(BuildContext context, String name, IconData icon, Color color, String provider) {
    return GestureDetector(
      onTap: () => _startOAuth(context, provider),
      child: Container(
        decoration: BoxDecoration(
          color: const Color(0xFF1F2937),
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: Colors.white12),
        ),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(icon, size: 48, color: color),
            const SizedBox(height: 16),
            Text(name, style: const TextStyle(color: Colors.white, fontSize: 18, fontWeight: FontWeight.bold)),
            const SizedBox(height: 4),
            const Text('Connect', style: TextStyle(color: Color(0xFF4F46E5), fontWeight: FontWeight.w600)),
          ],
        ),
      ),
    );
  }
}
