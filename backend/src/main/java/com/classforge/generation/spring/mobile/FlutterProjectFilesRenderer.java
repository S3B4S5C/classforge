package com.classforge.generation.spring.mobile;

import com.classforge.generation.spring.domain.DomainManifestPlan;
import java.util.*;
import java.util.stream.Collectors;

final class FlutterProjectFilesRenderer {

    String pubspec(String packageName, boolean auth) {
        String secure = auth ? "  flutter_secure_storage: ^11.1.1\n" : "";
        return """
                name: %s
                description: Flutter mobile generado por ClassForge.
                publish_to: "none"
                version: 0.1.0+1

                environment:
                  sdk: ">=3.12.0 <4.0.0"

                dependencies:
                  flutter:
                    sdk: flutter
                  http: ^1.6.0
                  record: ^7.1.1
                  path_provider: ^2.1.5
                %s
                dev_dependencies:
                  flutter_test:
                    sdk: flutter

                flutter:
                  uses-material-design: true
                """.formatted(packageName, secure);
    }

    String analysisOptions() {
        return """
                analyzer:
                  language:
                    strict-casts: true
                    strict-inference: true
                """;
    }

    String mainDart(String packageName) {
        return """
                import 'package:flutter/material.dart';
                import 'app/app.dart';

                void main() {
                  WidgetsFlutterBinding.ensureInitialized();
                  runApp(const GeneratedApp());
                }
                """;
    }

    String appDart(DomainManifestPlan manifest, String artifactName) {
        String imports = manifest.entities().stream()
                .map(e -> "import '../entities/%s/%s_list_page.dart';".formatted(snake(e.codeName()), snake(e.codeName())))
                .collect(Collectors.joining("\n"));
        String routes = manifest.entities().stream()
                .map(e -> "      '/entities/%s': (_) => const %sListPage(),".formatted(e.tableName(), e.codeName()))
                .collect(Collectors.joining("\n"));
        boolean auth = manifest.authentication().enabled();
        String authImports = auth ? """
                import '../auth/bootstrap_page.dart';
                import '../auth/login_page.dart';
                import '../core/auth/auth_gate.dart';
                """ : "";
        String home = auth ? "const AuthGate(child: DashboardPage())" : "const DashboardPage()";
        String authRoutes = auth ? """
                      '/login': (_) => const LoginPage(),
                      '/bootstrap': (_) => const BootstrapPage(),
                """ : "";
        String assistantRoute = auth
                ? "      '/assistant': (_) => const AuthGate(child: AssistantPage()),"
                : "      '/assistant': (_) => const AssistantPage(),";
        return """
                import 'package:flutter/material.dart';
                import '../core/theme/app_theme.dart';
                import '../core/navigation/app_navigator.dart';
                import '../dashboard/dashboard_page.dart';
                import '../assistant/assistant_page.dart';
                %s
                %s

                class GeneratedApp extends StatelessWidget {
                  const GeneratedApp({super.key});

                  @override
                  Widget build(BuildContext context) {
                    return MaterialApp(
                      navigatorKey: navigatorKey,
                      title: '%s',
                      debugShowCheckedModeBanner: false,
                      theme: AppTheme.light(),
                      home: %s,
                      routes: {
                %s
                %s
                %s
                      },
                    );
                  }
                }
                """.formatted(authImports, imports, escapeDart(title(artifactName)), home, authRoutes, assistantRoute, routes);
    }

    String theme(String color) {
        String hex = color.substring(1);
        return """
                import 'package:flutter/material.dart';

                abstract final class AppTheme {
                  static const Color primary = Color(0xFF%s);

                  static ThemeData light() {
                    return ThemeData(
                      colorScheme: ColorScheme.fromSeed(seedColor: primary),
                      useMaterial3: true,
                      inputDecorationTheme: const InputDecorationTheme(border: OutlineInputBorder()),
                    );
                  }
                }
                """.formatted(hex);
    }

    String appNavigator() {
        return """
                import 'package:flutter/material.dart';

                final navigatorKey = GlobalKey<NavigatorState>();
                """;
    }

    String pageResponse() {
        return """
                class PageResponse<T> {
                  const PageResponse({required this.items, required this.page, required this.size, required this.total});

                  final List<T> items;
                  final int page;
                  final int size;
                  final int total;
                  int get totalPages => size <= 0 ? 0 : (total / size).ceil();

                  factory PageResponse.fromJson(Map<String, dynamic> json, T Function(Map<String, dynamic>) mapper) {
                    final rawItems = (json['items'] ?? json['content'] ?? const <dynamic>[]) as List<dynamic>;
                    return PageResponse<T>(
                      items: rawItems.map((item) => mapper(Map<String, dynamic>.from(item as Map))).toList(),
                      page: (json['page'] as num?)?.toInt() ?? 0,
                      size: (json['size'] as num?)?.toInt() ?? rawItems.length,
                      total: (json['total'] as num?)?.toInt() ?? (json['totalElements'] as num?)?.toInt() ?? rawItems.length,
                    );
                  }
                }
                """;
    }

    String apiClient(boolean auth) {
        String authImport = auth ? "import '../auth/token_store.dart';\nimport '../navigation/app_navigator.dart';" : "";
        String tokenField = auth ? "  final TokenStore _tokens = const TokenStore();" : "";
        String authHeaders = auth ? """
                    final token = await _tokens.read();
                    if (token != null && token.isNotEmpty) headers['Authorization'] = 'Bearer $token';
                """ : "";
        String unauthorized = auth ? """
                    if (response.statusCode == 401) {
                      await _tokens.clear();
                      navigatorKey.currentState?.pushNamedAndRemoveUntil('/login', (route) => false);
                    }
                """ : "";
        return """
                import 'dart:convert';
                import 'package:http/http.dart' as http;
                %s

                class ApiClient {
                  ApiClient({http.Client? client}) : _client = client ?? http.Client();

                  static const String baseUrl = 'http://10.0.2.2:8080';
                  final http.Client _client;
                %s

                  Future<dynamic> request(String method, String path, {Object? body, Map<String, String>? query}) async {
                    final uri = Uri.parse('$baseUrl$path').replace(queryParameters: query?.map((k, v) => MapEntry(k, v)));
                    final headers = <String, String>{'Accept': 'application/json', 'Content-Type': 'application/json'};
                %s
                    late http.Response response;
                    final encoded = body == null ? null : jsonEncode(body);
                    switch (method) {
                      case 'GET': response = await _client.get(uri, headers: headers); break;
                      case 'POST': response = await _client.post(uri, headers: headers, body: encoded); break;
                      case 'PUT': response = await _client.put(uri, headers: headers, body: encoded); break;
                      case 'DELETE': response = await _client.delete(uri, headers: headers); break;
                      default: throw ArgumentError('Unsupported method: $method');
                    }
                %s
                    if (response.statusCode < 200 || response.statusCode >= 300) {
                      String message = 'HTTP ${response.statusCode}';
                      try {
                        final decoded = jsonDecode(response.body);
                        if (decoded is Map && decoded['message'] != null) message = decoded['message'].toString();
                      } catch (_) {}
                      throw ApiException(response.statusCode, message);
                    }
                    if (response.body.trim().isEmpty) return null;
                    return jsonDecode(response.body);
                  }
                }

                class ApiException implements Exception {
                  const ApiException(this.statusCode, this.message);
                  final int statusCode;
                  final String message;
                  @override String toString() => message;
                }
                """.formatted(authImport, tokenField, authHeaders, unauthorized);
    }

    String referencePicker() {
        return """
                import 'dart:convert';
                import 'package:flutter/foundation.dart';
                import 'package:flutter/material.dart';
                import '../api/api_client.dart';
                import '../api/page_response.dart';

                class ReferencePicker extends StatefulWidget {
                  const ReferencePicker({super.key, required this.label, required this.endpoint, required this.idFields, required this.multiple, required this.value, required this.onChanged, this.labelFields = const [], this.enabled = true, this.requiredSelection = false, this.client});
                  final String label;
                  final String endpoint;
                  final List<String> idFields;
                  final List<String> labelFields;
                  final bool multiple;
                  final Object? value;
                  final ValueChanged<Object?> onChanged;
                  final bool enabled;
                  final bool requiredSelection;
                  final ApiClient? client;

                  @override State<ReferencePicker> createState() => _ReferencePickerState();
                }

                class _ReferencePickerState extends State<ReferencePicker> {
                  late final ApiClient api = widget.client ?? ApiClient();
                  List<Map<String, dynamic>> options = const [];
                  bool loading = true;
                  String error = '';
                  int requestVersion = 0;

                  @override void initState() { super.initState(); _load(); }
                  @override void dispose() { requestVersion++; super.dispose(); }
                  @override void didUpdateWidget(covariant ReferencePicker oldWidget) {
                    super.didUpdateWidget(oldWidget);
                    if (oldWidget.endpoint != widget.endpoint || !listEquals(oldWidget.idFields, widget.idFields) || !listEquals(oldWidget.labelFields, widget.labelFields) || oldWidget.multiple != widget.multiple) _load();
                  }

                  Future<void> _load() async {
                    final version = ++requestVersion;
                    final endpoint = widget.endpoint;
                    setState(() { options = []; loading = true; error = ''; });
                    try {
                      final all = <Map<String, dynamic>>[];
                      int page = 0, totalPages = 1;
                      do {
                        final raw = await api.request('GET', endpoint, query: {'page': '$page', 'size': '100'});
                        if (!mounted || version != requestVersion) return;
                        final result = PageResponse<Map<String, dynamic>>.fromJson(Map<String, dynamic>.from(raw as Map), (item) => item);
                        all.addAll(result.items); totalPages = result.totalPages; page++;
                      } while (page < totalPages);
                      setState(() => options = all);
                    } catch (e) { if (mounted && version == requestVersion) setState(() => error = e.toString()); }
                    finally { if (mounted && version == requestVersion) setState(() => loading = false); }
                  }

                  Object _id(Map<String, dynamic> item) {
                    if (widget.idFields.length == 1) return item[widget.idFields.first] as Object;
                    return {for (final field in widget.idFields) field: item[field]};
                  }
                  String _encoded(Object value) => jsonEncode(widget.idFields.length == 1 ? value : {for (final field in widget.idFields) field: (value as Map)[field]});
                  String _label(Map<String, dynamic> item) {
                    for (final field in widget.labelFields) {
                      final value = item[field];
                      if (value is String && value.trim().isNotEmpty) return value.trim();
                      if (value is num) return value.toString();
                    }
                    return widget.idFields.map((field) => item[field]?.toString() ?? '').where((value) => value.isNotEmpty).join(' / ');
                  }
                  String? _validate(bool empty) {
                    if (loading) return 'Espera a que se carguen las opciones';
                    if (error.isNotEmpty) return 'Reintenta cargar las opciones';
                    return widget.requiredSelection && empty ? 'Campo requerido' : null;
                  }

                  @override
                  Widget build(BuildContext context) {
                    final choices = <String, Map<String, dynamic>>{
                      for (final item in options)
                        if (widget.idFields.every((field) => item[field] != null)) _encoded(_id(item)): item,
                    };
                    final values = widget.multiple ? (widget.value as List? ?? []) : (widget.value == null ? [] : [widget.value]);
                    final selected = values.whereType<Object>().map(_encoded).toSet();
                    for (final value in values.whereType<Object>()) {
                      choices.putIfAbsent(_encoded(value), () => widget.idFields.length == 1 ? {widget.idFields.first: value} : Map<String, dynamic>.from(value as Map));
                    }
                    final enabled = widget.enabled && !loading && error.isEmpty;
                    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                      if (loading) const LinearProgressIndicator(),
                      if (error.isNotEmpty) Row(children: [
                        Expanded(child: Text(error, style: TextStyle(color: Theme.of(context).colorScheme.error))),
                        TextButton(onPressed: widget.enabled ? _load : null, child: const Text('Reintentar')),
                      ]),
                      if (!widget.multiple)
                        DropdownButtonFormField<String>(
                          key: ValueKey('${widget.endpoint}:${widget.idFields.join(',')}:${selected.join(',')}'),
                          initialValue: selected.isEmpty ? '' : selected.first,
                          isExpanded: true,
                          decoration: InputDecoration(labelText: widget.label),
                          items: [
                            const DropdownMenuItem(value: '', child: Text('Sin seleccion')),
                            for (final entry in choices.entries) DropdownMenuItem(value: entry.key, child: Text(_label(entry.value), overflow: TextOverflow.ellipsis)),
                          ],
                          validator: (value) => _validate(value == null || value.isEmpty),
                          onChanged: enabled ? (value) => widget.onChanged(value == null || value.isEmpty ? null : jsonDecode(value)) : null,
                        )
                      else
                        FormField<Object>(
                          validator: (_) => _validate(selected.isEmpty),
                          builder: (field) => InputDecorator(
                            decoration: InputDecoration(labelText: widget.label, errorText: field.errorText),
                            child: Wrap(spacing: 8, children: [
                              for (final entry in choices.entries) FilterChip(
                                label: Text(_label(entry.value)), selected: selected.contains(entry.key),
                                onSelected: enabled ? (yes) {
                                  final next = {...selected};
                                  yes ? next.add(entry.key) : next.remove(entry.key);
                                  final value = next.map(jsonDecode).toList();
                                  field.didChange(value); widget.onChanged(value);
                                } : null,
                              ),
                              if (choices.isEmpty && !loading && error.isEmpty) const Text('Sin opciones disponibles'),
                            ]),
                          ),
                        ),
                    ]);
                  }
                }
                """;
    }

    String dashboard(DomainManifestPlan manifest) {
        String imports = manifest.entities().stream()
                .map(e -> "import '../entities/%s/%s_list_page.dart';".formatted(snake(e.codeName()), snake(e.codeName())))
                .collect(Collectors.joining("\n"));
        String cards = manifest.entities().stream().map(e -> """
                    DashboardEntity('%s', '%s', '/api/%s', () => const %sListPage()),
                """.formatted(escapeDart(e.displayName()), e.tableName(), e.tableName(), e.codeName())).collect(Collectors.joining());
        return """
                import 'package:flutter/material.dart';
                import '../core/api/api_client.dart';
                %s

                class DashboardPage extends StatefulWidget {
                  const DashboardPage({super.key});
                  @override State<DashboardPage> createState() => _DashboardPageState();
                }

                class _DashboardPageState extends State<DashboardPage> {
                  final ApiClient api = ApiClient();
                  final entities = <DashboardEntity>[
                %s
                  ];
                  final counts = <String, int>{};

                  @override
                  void initState() { super.initState(); _loadCounts(); }

                  Future<void> _loadCounts() async {
                    for (final entity in entities) {
                      try {
                        final raw = await api.request('GET', '${entity.endpoint}/count');
                        final value = raw is num ? raw.toInt() : (raw is Map ? ((raw['count'] as num?)?.toInt() ?? 0) : 0);
                        if (mounted) setState(() => counts[entity.key] = value);
                      } catch (_) {}
                    }
                  }

                  @override
                  Widget build(BuildContext context) {
                    return Scaffold(
                      appBar: AppBar(
                        title: const Text('Dashboard'),
                        actions: [IconButton(tooltip: 'Asistente', icon: const Icon(Icons.mic_none), onPressed: () => Navigator.of(context).pushNamed('/assistant'))],
                      ),
                      body: GridView.builder(
                        padding: const EdgeInsets.all(16),
                        gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(maxCrossAxisExtent: 280, mainAxisExtent: 150, crossAxisSpacing: 12, mainAxisSpacing: 12),
                        itemCount: entities.length,
                        itemBuilder: (context, index) {
                          final entity = entities[index];
                          return Card(
                            child: InkWell(
                              onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => entity.page())),
                              child: Padding(
                                padding: const EdgeInsets.all(16),
                                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                                  Text(entity.label, style: Theme.of(context).textTheme.titleLarge),
                                  const Spacer(),
                                  Text('${counts[entity.key] ?? '—'} registros', style: Theme.of(context).textTheme.headlineSmall),
                                ]),
                              ),
                            ),
                          );
                        },
                      ),
                    );
                  }
                }

                class DashboardEntity {
                  const DashboardEntity(this.label, this.key, this.endpoint, this.page);
                  final String label;
                  final String key;
                  final String endpoint;
                  final Widget Function() page;
                }
                """.formatted(imports, cards);
    }

    String smokeTest(String packageName) {
        return """
                import 'package:flutter/material.dart';
                import 'package:flutter_test/flutter_test.dart';
                import 'package:%s/core/theme/app_theme.dart';

                void main() {
                  test('generated theme exposes a primary color', () {
                    expect(AppTheme.primary, isA<Color>());
                  });
                }
                """.formatted(packageName);
    }

    String readme(DomainManifestPlan manifest, String artifactName, String color) {
        return """
                # %s mobile

                Flutter mobile generado por ClassForge CU-18 + CU-19.

                - modo: `%s`
                - color primario: `%s`
                - backend Android emulator: `http://10.0.2.2:8080`
                - plataforma formalmente aceptada: Android

                ## Ejecutar

                ```bash
                flutter pub get
                flutter run
                ```

                ## Validación

                ```bash
                flutter analyze
                flutter test
                flutter build apk --debug
                ```

                El proyecto usa `flutter_secure_storage` para JWT cuando Auth está habilitado.
                """.formatted(title(artifactName), manifest.generationMode(), color);
    }

    String snake(String value) {
        String normalized = value.replaceAll("([a-z0-9])([A-Z])", "$1_$2").replace('-', '_').replace(' ', '_').toLowerCase(Locale.ROOT);
        return normalized.replaceAll("[^a-z0-9_]", "_");
    }

    String title(String artifactName) {
        return java.util.Arrays.stream(artifactName.split("[-_]+"))
                .filter(s -> !s.isBlank())
                .map(s -> Character.toUpperCase(s.charAt(0)) + s.substring(1))
                .collect(Collectors.joining(" "));
    }

    String escapeDart(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'").replace("$", "\\$");
    }
}
