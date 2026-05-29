import 'package:flutter/material.dart';
import '../../../core/constants/api_constants.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:http/http.dart' as http;
import 'dart:convert';

class AppletCanvasFlow extends StatefulWidget {
  const AppletCanvasFlow({super.key});

  @override
  State<AppletCanvasFlow> createState() => _AppletCanvasFlowState();
}

class _AppletCanvasFlowState extends State<AppletCanvasFlow> {
  String? _trigger;
  String? _action;
  
  bool _isHoveringTrigger = false;
  bool _isHoveringAction = false;
  bool _isHoveringSave = false;

  Future<void> _saveApplet() async {
    if (_trigger == null || _action == null) return;
    final prefs = await SharedPreferences.getInstance();
    final token = prefs.getString('jwt_token');
    await http.post(
      Uri.parse('${ApiConstants.baseUrl}/automations'),
      headers: {'Authorization': 'Bearer $token', 'Content-Type': 'application/json'},
      body: jsonEncode({
        'name': 'Canvas Applet',
        'triggerConfig': {'type': _trigger},
        'actionConfig': {'type': _action},
      }),
    );
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Applet Saved Successfully!')));
      Navigator.of(context).pop();
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF111827),
      appBar: AppBar(title: const Text('Canvas Builder', style: TextStyle(color: Colors.white)), backgroundColor: Colors.transparent, elevation: 0),
      body: Stack(
        children: [
          // Background grid
          Positioned.fill(
            child: Opacity(
              opacity: 0.05,
              child: CustomPaint(
                painter: GridPainter(),
              ),
            ),
          ),
          Center(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                _buildNode(
                  title: 'Trigger',
                  value: _trigger,
                  options: const ['github_push', 'webhook'],
                  isHovering: _isHoveringTrigger,
                  onHover: (v) => setState(() => _isHoveringTrigger = v),
                  onSelect: (v) => setState(() => _trigger = v),
                ),
                Container(height: 50, width: 2, color: const Color(0xFF4F46E5)),
                _buildNode(
                  title: 'Action',
                  value: _action,
                  options: const ['slack_message', 'send_email'],
                  isHovering: _isHoveringAction,
                  onHover: (v) => setState(() => _isHoveringAction = v),
                  onSelect: (v) => setState(() => _action = v),
                ),
              ],
            ),
          ),
          if (_trigger != null && _action != null)
            Positioned(
              bottom: 32,
              right: 32,
              child: MouseRegion(
                onEnter: (_) => setState(() => _isHoveringSave = true),
                onExit: (_) => setState(() => _isHoveringSave = false),
                child: AnimatedScale(
                  scale: _isHoveringSave ? 1.1 : 1.0,
                  duration: const Duration(milliseconds: 200),
                  curve: Curves.elasticOut,
                  child: FloatingActionButton.extended(
                    onPressed: _saveApplet,
                    backgroundColor: const Color(0xFF4F46E5),
                    label: const Text('Save Applet', style: TextStyle(color: Colors.white)),
                    icon: const Icon(Icons.save, color: Colors.white),
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }

  Widget _buildNode({
    required String title,
    required String? value,
    required List<String> options,
    required bool isHovering,
    required Function(bool) onHover,
    required Function(String) onSelect,
  }) {
    return MouseRegion(
      onEnter: (_) => onHover(true),
      onExit: (_) => onHover(false),
      child: AnimatedScale(
        scale: isHovering ? 1.05 : 1.0,
        duration: const Duration(milliseconds: 200),
        curve: Curves.easeOutBack,
        child: Container(
          width: 250,
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: const Color(0xFF1F2937).withOpacity(0.9),
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: isHovering ? const Color(0xFF4F46E5) : Colors.white12, width: 2),
            boxShadow: isHovering ? [BoxShadow(color: const Color(0xFF4F46E5).withOpacity(0.3), blurRadius: 15, spreadRadius: 2)] : [],
          ),
          child: Column(
            children: [
              Text(title, style: const TextStyle(color: Colors.white, fontSize: 18, fontWeight: FontWeight.bold)),
              const SizedBox(height: 8),
              DropdownButton<String>(
                value: value,
                hint: const Text('Select...', style: TextStyle(color: Colors.white54)),
                dropdownColor: const Color(0xFF1F2937),
                isExpanded: true,
                underline: const SizedBox(),
                style: const TextStyle(color: Colors.white),
                items: options.map((opt) => DropdownMenuItem(value: opt, child: Text(opt))).toList(),
                onChanged: (v) {
                  if (v != null) onSelect(v);
                },
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class GridPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = Colors.white
      ..strokeWidth = 1.0;
    
    for (double i = 0; i < size.width; i += 40) {
      canvas.drawLine(Offset(i, 0), Offset(i, size.height), paint);
    }
    for (double i = 0; i < size.height; i += 40) {
      canvas.drawLine(Offset(0, i), Offset(size.width, i), paint);
    }
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
