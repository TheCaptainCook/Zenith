import 'package:flutter/material.dart';
import '../../../core/constants/api_constants.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:http/http.dart' as http;
import 'dart:convert';

class AppletWizardFlow extends StatefulWidget {
  const AppletWizardFlow({super.key});

  @override
  State<AppletWizardFlow> createState() => _AppletWizardFlowState();
}

class _AppletWizardFlowState extends State<AppletWizardFlow> {
  int _currentStep = 0;
  String? _trigger;
  String? _action;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF111827),
      appBar: AppBar(title: const Text('Wizard Builder', style: TextStyle(color: Colors.white)), backgroundColor: Colors.transparent, elevation: 0),
      body: Theme(
        data: ThemeData(
          canvasColor: const Color(0xFF111827),
          colorScheme: const ColorScheme.dark(primary: Color(0xFF4F46E5)),
        ),
        child: Stepper(
          currentStep: _currentStep,
          onStepContinue: () async {
            if (_currentStep == 0 && _trigger != null) {
              setState(() => _currentStep += 1);
            } else if (_currentStep == 1 && _action != null) {
                  final prefs = await SharedPreferences.getInstance();
                  final token = prefs.getString('jwt_token');
                  await http.post(
                    Uri.parse('${ApiConstants.baseUrl}/automations'),
                    headers: {'Authorization': 'Bearer $token', 'Content-Type': 'application/json'},
                    body: jsonEncode({
                      'name': 'Wizard Applet',
                      'triggerConfig': {'type': _trigger},
                      'actionConfig': {'type': _action},
                    }),
                  );
               if (context.mounted) {
                 ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Applet Saved Successfully!')));
                 Navigator.of(context).pop();
               }
            }
          },
          onStepCancel: () {
            if (_currentStep > 0) setState(() => _currentStep -= 1);
          },
          steps: [
            Step(
              title: const Text('Select Trigger', style: TextStyle(color: Colors.white)),
              content: Column(
                children: [
                  RadioListTile(title: const Text('GitHub Push', style: TextStyle(color: Colors.white)), value: 'github_push', groupValue: _trigger, onChanged: (v) => setState(() => _trigger = v.toString())),
                  RadioListTile(title: const Text('Webhook', style: TextStyle(color: Colors.white)), value: 'webhook', groupValue: _trigger, onChanged: (v) => setState(() => _trigger = v.toString())),
                ],
              ),
              isActive: _currentStep >= 0,
            ),
            Step(
              title: const Text('Select Action', style: TextStyle(color: Colors.white)),
              content: Column(
                children: [
                  RadioListTile(title: const Text('Send Slack Message', style: TextStyle(color: Colors.white)), value: 'slack_message', groupValue: _action, onChanged: (v) => setState(() => _action = v.toString())),
                  RadioListTile(title: const Text('Send Email', style: TextStyle(color: Colors.white)), value: 'send_email', groupValue: _action, onChanged: (v) => setState(() => _action = v.toString())),
                ],
              ),
              isActive: _currentStep >= 1,
            ),
          ],
        ),
      ),
    );
  }
}
