import 'package:flutter/material.dart';
import '../../../core/constants/api_constants.dart';
import 'applet_wizard_flow.dart';
import 'applet_canvas_flow.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:http/http.dart' as http;
import 'dart:convert';

class AppletBuilderScreen extends StatefulWidget {
  const AppletBuilderScreen({super.key});

  @override
  State<AppletBuilderScreen> createState() => _AppletBuilderScreenState();
}

class _AppletBuilderScreenState extends State<AppletBuilderScreen> {
  String preference = 'wizard';
  bool isLoading = true;

  @override
  void initState() {
    super.initState();
    _fetchPreference();
  }

  Future<void> _fetchPreference() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final token = prefs.getString('jwt_token');
      final res = await http.get(
        Uri.parse('${ApiConstants.baseUrl}/auth/me'),
        headers: {'Authorization': 'Bearer $token'},
      );
      if (res.statusCode == 200) {
        final data = json.decode(res.body);
        setState(() => preference = data['builderPreference'] ?? 'wizard');
      }
    } finally {
      setState(() => isLoading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    if (isLoading) return const Scaffold(backgroundColor: Color(0xFF111827), body: Center(child: CircularProgressIndicator()));
    if (preference == 'canvas') return const AppletCanvasFlow();
    return const AppletWizardFlow();
  }
}
