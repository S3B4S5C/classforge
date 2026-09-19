package com.classforge.generation.spring.mobile;

import com.classforge.generation.spring.domain.DomainManifestPlan;
import java.util.*;
import java.util.stream.Collectors;

final class FlutterAssistantFilesRenderer {

    String assistantApi(boolean auth) {
        String authImport = auth ? "import '../core/auth/token_store.dart';\n" : "";
        String authField = auth ? "  final TokenStore _tokens = const TokenStore();\n" : "";
        String authHeader = auth ? "    final token = await _tokens.read();\n    if (token != null && token.isNotEmpty) request.headers['Authorization'] = 'Bearer $token';\n" : "";
        return """
                import 'dart:convert';
                import 'package:http/http.dart' as http;
                import '../core/api/api_client.dart';
                %s
                class AssistantPlan {
                  const AssistantPlan({required this.source, required this.transcript, required this.intent, required this.summary, required this.requiresConfirmation, this.previewToken, this.result});
                  final String source;
                  final String transcript;
                  final String intent;
                  final String summary;
                  final bool requiresConfirmation;
                  final String? previewToken;
                  final Object? result;
                  factory AssistantPlan.fromJson(Map<String, dynamic> json) => AssistantPlan(
                    source: json['source']?.toString() ?? '', transcript: json['transcript']?.toString() ?? '', intent: json['intent']?.toString() ?? '',
                    summary: json['summary']?.toString() ?? '', requiresConfirmation: json['requiresConfirmation'] == true,
                    previewToken: json['previewToken']?.toString(), result: json['result'],
                  );
                }

                class AssistantApi {
                  final ApiClient _api = ApiClient();
                %s
                  Future<AssistantPlan> plan(String text) async {
                    final raw = await _api.request('POST', '/api/assistant/plan', body: {'text': text});
                    return AssistantPlan.fromJson(Map<String, dynamic>.from(raw as Map));
                  }
                  Future<AssistantPlan> apply(String token) async {
                    final raw = await _api.request('POST', '/api/assistant/apply', body: {'previewToken': token});
                    return AssistantPlan.fromJson(Map<String, dynamic>.from(raw as Map));
                  }
                  Future<AssistantPlan> voice(String path) async {
                    final request = http.MultipartRequest('POST', Uri.parse('${ApiClient.baseUrl}/api/assistant/voice'));
                %s
                    request.files.add(await http.MultipartFile.fromPath('audio', path, filename: 'generated-assistant.wav'));
                    final streamed = await request.send();
                    final response = await http.Response.fromStream(streamed);
                    if (response.statusCode < 200 || response.statusCode >= 300) {
                      String message = 'HTTP ${response.statusCode}';
                      try { final decoded = jsonDecode(response.body); if (decoded is Map && decoded['message'] != null) message = decoded['message'].toString(); } catch (_) {}
                      throw ApiException(response.statusCode, message);
                    }
                    return AssistantPlan.fromJson(Map<String, dynamic>.from(jsonDecode(response.body) as Map));
                  }
                }
                """.formatted(authImport, authField, authHeader);
    }

    String assistantPage() {
        return """
                import 'dart:convert';
                import 'dart:io';
                import 'package:flutter/material.dart';
                import 'package:path_provider/path_provider.dart';
                import 'package:record/record.dart';
                import 'assistant_api.dart';

                class AssistantPage extends StatefulWidget {
                  const AssistantPage({super.key});
                  @override State<AssistantPage> createState() => _AssistantPageState();
                }

                class _AssistantPageState extends State<AssistantPage> {
                  final AssistantApi api = AssistantApi();
                  final AudioRecorder recorder = AudioRecorder();
                  final TextEditingController text = TextEditingController();
                  final messages = <_Message>[];
                  AssistantPlan? pending;
                  bool busy = false;
                  bool recording = false;
                  String? error;

                  @override
                  void dispose() { text.dispose(); recorder.dispose(); super.dispose(); }

                  Future<void> _send() async {
                    final value = text.text.trim(); if (value.isEmpty || busy) return;
                    setState(() { messages.add(_Message(true, value)); text.clear(); busy = true; error = null; });
                    try { _accept(await api.plan(value)); } catch (e) { _fail(e); }
                  }

                  Future<void> _startVoice() async {
                    try {
                      if (!await recorder.hasPermission()) { setState(() => error = 'Permiso de microfono denegado.'); return; }
                      final dir = await getTemporaryDirectory();
                      final path = '${dir.path}/classforge_generated_voice.wav';
                      await recorder.start(const RecordConfig(encoder: AudioEncoder.wav, sampleRate: 16000, numChannels: 1, autoGain: true, echoCancel: true, noiseSuppress: true), path: path);
                      if (mounted) setState(() { recording = true; error = null; });
                    } catch (e) { _fail(e); }
                  }

                  Future<void> _stopVoice() async {
                    if (!recording || busy) return;
                    setState(() { busy = true; error = null; });
                    try {
                      final path = await recorder.stop();
                      if (mounted) setState(() => recording = false);
                      if (path == null) throw StateError('No se capturo audio.');
                      final plan = await api.voice(path);
                      try { await File(path).delete(); } catch (_) {}
                      if (mounted) setState(() => messages.add(_Message(true, plan.transcript)));
                      _accept(plan);
                    } catch (e) { _fail(e); }
                  }

                  Future<void> _apply() async {
                    final token = pending?.previewToken; if (token == null || busy) return;
                    setState(() { busy = true; error = null; });
                    try { final result = await api.apply(token); if (mounted) setState(() => pending = null); _accept(result); } catch (e) { _fail(e); }
                  }

                  void _accept(AssistantPlan plan) {
                    if (!mounted) return;
                    setState(() {
                      busy = false;
                      if (plan.requiresConfirmation) { pending = plan; messages.add(_Message(false, plan.summary)); }
                      else { pending = null; messages.add(_Message(false, plan.summary, plan.result == null ? null : const JsonEncoder.withIndent('  ').convert(plan.result))); }
                    });
                  }
                  void _fail(Object e) { if (mounted) setState(() { busy = false; recording = false; error = e.toString(); }); }

                  @override
                  Widget build(BuildContext context) {
                    return Scaffold(
                      appBar: AppBar(title: const Text('Asistente')),
                      body: SafeArea(child: Padding(
                        padding: const EdgeInsets.all(16),
                        child: Column(children: [
                          Expanded(child: ListView(children: [
                            const Text('Whisper + Qwen locales via Spring generado.'), const SizedBox(height: 12),
                            for (final message in messages) Card(child: Padding(padding: const EdgeInsets.all(12), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text(message.user ? 'Tu' : 'Asistente', style: const TextStyle(fontWeight: FontWeight.bold)), Text(message.text), if (message.detail != null) SelectableText(message.detail!)]))),
                            if (pending != null) Card(child: Padding(padding: const EdgeInsets.all(12), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [const Text('Confirmacion requerida', style: TextStyle(fontWeight: FontWeight.bold)), Text(pending!.summary), Wrap(spacing: 8, children: [FilledButton(onPressed: busy ? null : _apply, child: const Text('Confirmar')), OutlinedButton(onPressed: busy ? null : () => setState(() => pending = null), child: const Text('Cancelar'))])]))),
                            if (error != null) Text(error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
                          ])),
                          TextField(controller: text, minLines: 1, maxLines: 3, enabled: !busy && !recording, decoration: const InputDecoration(labelText: 'Instruccion')),
                          const SizedBox(height: 8),
                          Row(children: [Expanded(child: FilledButton(onPressed: busy || recording ? null : _send, child: const Text('Enviar'))), const SizedBox(width: 8), Expanded(child: OutlinedButton.icon(onPressed: busy ? null : (recording ? _stopVoice : _startVoice), icon: Icon(recording ? Icons.stop : Icons.mic), label: Text(recording ? 'Detener' : 'Hablar')))]),
                        ]),
                      )),
                    );
                  }
                }

                class _Message {
                  const _Message(this.user, this.text, [this.detail]);
                  final bool user;
                  final String text;
                  final String? detail;
                }
                """;
    }
}
