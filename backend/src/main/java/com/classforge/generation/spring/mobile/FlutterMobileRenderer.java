package com.classforge.generation.spring.mobile;

import com.classforge.generation.spring.domain.DomainManifestPlan;
import com.classforge.generation.spring.generated.GeneratedFile;
import com.classforge.generation.spring.generated.GeneratedFileType;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

public final class FlutterMobileRenderer {
    public static final String ROOT = "mobile/";
    public static final String FLUTTER_CHANNEL = "stable";

    public List<GeneratedFile> render(
            DomainManifestPlan manifest,
            String artifactName,
            String basePackage,
            String primaryColor
    ) {
        Objects.requireNonNull(manifest, "manifest is required");
        String packageName = dartPackage(artifactName) + "_mobile";
        String androidNamespace = basePackage + ".mobile";
        String color = normalizeColor(primaryColor);
        List<GeneratedFile> files = new ArrayList<>();

        add(files, "pubspec.yaml", pubspec(packageName, manifest.authentication().enabled()));
        add(files, "analysis_options.yaml", analysisOptions());
        add(files, "README.md", readme(manifest, artifactName, color));
        add(files, "lib/main.dart", mainDart(packageName));
        add(files, "lib/app/app.dart", appDart(manifest, artifactName));
        add(files, "lib/core/theme/app_theme.dart", theme(color));
        add(files, "lib/core/navigation/app_navigator.dart", appNavigator());
        add(files, "lib/core/api/page_response.dart", pageResponse());
        add(files, "lib/core/api/api_client.dart", apiClient(manifest.authentication().enabled()));
        add(files, "lib/core/widgets/reference_picker.dart", referencePicker());
        add(files, "lib/dashboard/dashboard_page.dart", dashboard(manifest));
        add(files, "lib/assistant/assistant_api.dart", assistantApi(manifest.authentication().enabled()));
        add(files, "lib/assistant/assistant_page.dart", assistantPage());
        add(files, "test/smoke_test.dart", smokeTest(packageName));

        if (manifest.authentication().enabled()) {
            add(files, "lib/core/auth/token_store.dart", tokenStore());
            add(files, "lib/core/auth/auth_api.dart", authApi());
            add(files, "lib/core/auth/auth_gate.dart", authGate());
            add(files, "lib/auth/login_page.dart", loginPage());
            add(files, "lib/auth/bootstrap_page.dart", bootstrapPage(authEntity(manifest), manifest));
        }

        for (DomainManifestPlan.Entity entity : manifest.entities()) {
            String dir = "lib/entities/" + snake(entity.codeName()) + "/";
            String prefix = snake(entity.codeName());
            add(files, dir + prefix + "_model.dart", entityModel(entity));
            add(files, dir + prefix + "_api.dart", entityApi(entity));
            add(files, dir + prefix + "_list_page.dart", entityList(entity));
            add(files, dir + prefix + "_detail_page.dart", entityDetail(entity));
            add(files, dir + prefix + "_form_page.dart", entityForm(entity, manifest));
        }

        addAndroid(files, packageName, androidNamespace, artifactName, manifest.authentication().enabled());
        return List.copyOf(files);
    }

    private void addAndroid(List<GeneratedFile> files, String packageName, String namespace, String artifactName, boolean auth) {
        add(files, "android/settings.gradle.kts", settingsGradle());
        add(files, "android/build.gradle.kts", androidBuildGradle());
        add(files, "android/gradle.properties", gradleProperties());
        add(files, "android/gradle/wrapper/gradle-wrapper.properties", wrapperProperties());
        add(files, "android/app/build.gradle.kts", appBuildGradle(namespace, auth));
        add(files, "android/app/src/main/AndroidManifest.xml", androidManifest(artifactName));
        add(files, "android/app/src/debug/AndroidManifest.xml", debugManifest());
        add(files, "android/app/src/profile/AndroidManifest.xml", debugManifest());
        add(files, "android/app/src/main/kotlin/" + namespace.replace('.', '/') + "/MainActivity.kt", mainActivity(namespace));
    }

    private void add(List<GeneratedFile> files, String path, String content) {
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
        if (!normalized.endsWith("\n")) normalized += "\n";
        files.add(new GeneratedFile(ROOT + path, GeneratedFileType.TEXT, normalized.getBytes(StandardCharsets.UTF_8)));
    }

    private String pubspec(String packageName, boolean auth) {
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

    private String analysisOptions() {
        return """
                analyzer:
                  language:
                    strict-casts: true
                    strict-inference: true
                """;
    }

    private String mainDart(String packageName) {
        return """
                import 'package:flutter/material.dart';
                import 'app/app.dart';

                void main() {
                  WidgetsFlutterBinding.ensureInitialized();
                  runApp(const GeneratedApp());
                }
                """;
    }

    private String appDart(DomainManifestPlan manifest, String artifactName) {
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

    private String theme(String color) {
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

    private String appNavigator() {
        return """
                import 'package:flutter/material.dart';

                final navigatorKey = GlobalKey<NavigatorState>();
                """;
    }

    private String pageResponse() {
        return """
                class PageResponse<T> {
                  const PageResponse({required this.items, required this.page, required this.size, required this.total});

                  final List<T> items;
                  final int page;
                  final int size;
                  final int total;

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

    private String apiClient(boolean auth) {
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

    private String referencePicker() {
        return """
                import 'dart:convert';
                import 'package:flutter/material.dart';
                import '../api/api_client.dart';

                class ReferencePicker extends StatefulWidget {
                  const ReferencePicker({super.key, required this.label, required this.endpoint, required this.idFields, required this.multiple, required this.value, required this.onChanged});
                  final String label;
                  final String endpoint;
                  final List<String> idFields;
                  final bool multiple;
                  final Object? value;
                  final ValueChanged<Object?> onChanged;

                  @override
                  State<ReferencePicker> createState() => _ReferencePickerState();
                }

                class _ReferencePickerState extends State<ReferencePicker> {
                  final ApiClient api = ApiClient();
                  List<Map<String, dynamic>> options = const [];
                  bool loading = true;

                  @override
                  void initState() { super.initState(); _load(); }

                  Future<void> _load() async {
                    try {
                      final raw = await api.request('GET', widget.endpoint, query: const {'page': '0', 'size': '100'});
                      final list = raw is Map ? (raw['items'] ?? raw['content'] ?? const []) : raw;
                      if (mounted) setState(() => options = (list as List).map((e) => Map<String, dynamic>.from(e as Map)).toList());
                    } finally {
                      if (mounted) setState(() => loading = false);
                    }
                  }

                  Object _id(Map<String, dynamic> item) {
                    if (widget.idFields.length == 1) return item[widget.idFields.first] as Object;
                    return {for (final field in widget.idFields) field: item[field]};
                  }
                  String _encoded(Object value) => jsonEncode(value);
                  String _label(Map<String, dynamic> item) => item.entries.take(3).map((e) => '${e.key}=${e.value}').join(', ');

                  @override
                  Widget build(BuildContext context) {
                    if (loading) return const LinearProgressIndicator();
                    if (!widget.multiple) {
                      final selected = widget.value == null ? null : _encoded(widget.value as Object);
                      return DropdownButtonFormField<String>(
                        decoration: InputDecoration(labelText: widget.label),
                        value: selected,
                        items: options.map((item) { final id = _id(item); return DropdownMenuItem(value: _encoded(id), child: Text(_label(item))); }).toList(),
                        onChanged: (value) => widget.onChanged(value == null ? null : jsonDecode(value)),
                      );
                    }
                    final selected = (widget.value as List?)?.map((e) => _encoded(e as Object)).toSet() ?? <String>{};
                    return InputDecorator(
                      decoration: InputDecoration(labelText: widget.label),
                      child: Wrap(
                        spacing: 8,
                        children: options.map((item) {
                          final id = _id(item);
                          final encoded = _encoded(id);
                          return FilterChip(
                            label: Text(_label(item)),
                            selected: selected.contains(encoded),
                            onSelected: (yes) {
                              final next = {...selected};
                              yes ? next.add(encoded) : next.remove(encoded);
                              widget.onChanged(next.map(jsonDecode).toList());
                            },
                          );
                        }).toList(),
                      ),
                    );
                  }
                }
                """;
    }

    private String dashboard(DomainManifestPlan manifest) {
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

    private String entityModel(DomainManifestPlan.Entity entity) {
        return """
                class %sModel {
                  const %sModel(this.data);
                  final Map<String, dynamic> data;
                  factory %sModel.fromJson(Map<String, dynamic> json) => %sModel(Map.unmodifiable(json));
                  Object? operator [](String key) => data[key];
                  Map<String, dynamic> toJson() => Map<String, dynamic>.from(data);
                }
                """.formatted(entity.codeName(), entity.codeName(), entity.codeName(), entity.codeName());
    }

    private String entityApi(DomainManifestPlan.Entity entity) {
        String prefix = snake(entity.codeName());
        String idParts = entity.identifier().fields().stream().map(f -> "'" + f.name() + "'").collect(Collectors.joining(", "));
        return """
                import '../../core/api/api_client.dart';
                import '../../core/api/page_response.dart';
                import '%1$s_model.dart';

                class %2$sApi {
                  %2$sApi({ApiClient? client}) : client = client ?? ApiClient();
                  final ApiClient client;
                  static const endpoint = '%3$s';
                  static const idFields = <String>[%4$s];

                  Future<PageResponse<%2$sModel>> list({int page = 0, int size = 20, String q = ''}) async {
                    final raw = await client.request('GET', endpoint, query: {'page': '$page', 'size': '$size', if (q.isNotEmpty) 'q': q});
                    return PageResponse.fromJson(Map<String, dynamic>.from(raw as Map), %2$sModel.fromJson);
                  }

                  Future<int> count() async {
                    final raw = await client.request('GET', '$endpoint/count');
                    if (raw is num) return raw.toInt();
                    if (raw is Map) return ((raw['count'] as num?)?.toInt() ?? 0);
                    return 0;
                  }

                  Future<%2$sModel> get(Map<String, dynamic> id) async {
                    final raw = await client.request('GET', _itemPath(id));
                    return %2$sModel.fromJson(Map<String, dynamic>.from(raw as Map));
                  }

                  Future<%2$sModel> create(Map<String, dynamic> request) async {
                    final raw = await client.request('POST', endpoint, body: request);
                    return %2$sModel.fromJson(Map<String, dynamic>.from(raw as Map));
                  }

                  Future<%2$sModel> update(Map<String, dynamic> id, Map<String, dynamic> request) async {
                    final raw = await client.request('PUT', _itemPath(id), body: request);
                    return %2$sModel.fromJson(Map<String, dynamic>.from(raw as Map));
                  }

                  Future<void> delete(Map<String, dynamic> id) async { await client.request('DELETE', _itemPath(id)); }

                  Map<String, dynamic> idOf(%2$sModel record) => {for (final field in idFields) field: record.data[field]};

                  String _itemPath(Map<String, dynamic> id) {
                    if (idFields.length == 1) return '$endpoint/${Uri.encodeComponent(id[idFields.first].toString())}';
                    final query = idFields.map((field) => '${Uri.encodeQueryComponent(field)}=${Uri.encodeQueryComponent(id[field].toString())}').join('&');
                    return '$endpoint/by-id?$query';
                  }
                }
                """.formatted(prefix, entity.codeName(), entity.endpoint(), idParts);
    }

    private String entityList(DomainManifestPlan.Entity entity) {
        String prefix = snake(entity.codeName());
        List<DomainManifestPlan.Attribute> visible = entity.attributes().stream().filter(DomainManifestPlan.Attribute::readable).limit(3).toList();
        String lines = visible.stream().map(a -> "Text('%s: ${record.data['%s'] ?? '—'}'),".formatted(escapeDart(a.logicalName()), a.apiName())).collect(Collectors.joining("\n                        "));
        return """
                import 'package:flutter/material.dart';
                import '%1$s_api.dart';
                import '%1$s_model.dart';
                import '%1$s_detail_page.dart';
                import '%1$s_form_page.dart';

                class %2$sListPage extends StatefulWidget {
                  const %2$sListPage({super.key});
                  @override State<%2$sListPage> createState() => _%2$sListPageState();
                }

                class _%2$sListPageState extends State<%2$sListPage> {
                  final api = %2$sApi();
                  final search = TextEditingController();
                  List<%2$sModel> items = const [];
                  bool loading = true;
                  String error = '';

                  @override void initState() { super.initState(); _load(); }
                  @override void dispose() { search.dispose(); super.dispose(); }

                  Future<void> _load() async {
                    setState(() { loading = true; error = ''; });
                    try {
                      final page = await api.list(q: search.text.trim());
                      if (mounted) setState(() => items = page.items);
                    } catch (e) { if (mounted) setState(() => error = e.toString()); }
                    finally { if (mounted) setState(() => loading = false); }
                  }

                  @override
                  Widget build(BuildContext context) {
                    return Scaffold(
                      appBar: AppBar(title: const Text('%3$s')),
                      floatingActionButton: FloatingActionButton(
                        onPressed: () async { await Navigator.of(context).push(MaterialPageRoute(builder: (_) => const %2$sFormPage())); _load(); },
                        child: const Icon(Icons.add),
                      ),
                      body: Column(children: [
                        Padding(
                          padding: const EdgeInsets.all(12),
                          child: TextField(controller: search, decoration: InputDecoration(labelText: 'Buscar', suffixIcon: IconButton(onPressed: _load, icon: const Icon(Icons.search))), onSubmitted: (_) => _load()),
                        ),
                        if (loading) const LinearProgressIndicator(),
                        if (error.isNotEmpty) Padding(padding: const EdgeInsets.all(12), child: Text(error, style: TextStyle(color: Theme.of(context).colorScheme.error))),
                        Expanded(
                          child: RefreshIndicator(
                            onRefresh: _load,
                            child: ListView.builder(
                              itemCount: items.length,
                              itemBuilder: (context, index) {
                                final record = items[index];
                                return Card(
                                  margin: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                                  child: ListTile(
                                    title: Text('%3$s #${index + 1}'),
                                    subtitle: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                        %4$s
                                    ]),
                                    trailing: const Icon(Icons.chevron_right),
                                    onTap: () async { await Navigator.of(context).push(MaterialPageRoute(builder: (_) => %2$sDetailPage(record: record))); _load(); },
                                  ),
                                );
                              },
                            ),
                          ),
                        ),
                      ]),
                    );
                  }
                }
                """.formatted(prefix, entity.codeName(), escapeDart(entity.displayName()), lines);
    }

    private String entityDetail(DomainManifestPlan.Entity entity) {
        String prefix = snake(entity.codeName());
        String rows = entity.attributes().stream().filter(DomainManifestPlan.Attribute::readable).map(a -> """
                      _row('%s', record.data['%s']),
                """.formatted(escapeDart(a.logicalName()), a.apiName())).collect(Collectors.joining());
        return """
                import 'package:flutter/material.dart';
                import '%1$s_api.dart';
                import '%1$s_model.dart';
                import '%1$s_form_page.dart';

                class %2$sDetailPage extends StatefulWidget {
                  const %2$sDetailPage({super.key, required this.record});
                  final %2$sModel record;
                  @override State<%2$sDetailPage> createState() => _%2$sDetailPageState();
                }

                class _%2$sDetailPageState extends State<%2$sDetailPage> {
                  final api = %2$sApi();
                  late %2$sModel record = widget.record;

                  Widget _row(String label, Object? value) => ListTile(title: Text(label), subtitle: Text(value?.toString() ?? '—'));

                  Future<void> _edit() async {
                    final updated = await Navigator.of(context).push<%2$sModel>(MaterialPageRoute(builder: (_) => %2$sFormPage(existing: record)));
                    if (updated != null && mounted) setState(() => record = updated);
                  }

                  Future<void> _delete() async {
                    await api.delete(api.idOf(record));
                    if (mounted) Navigator.of(context).pop();
                  }

                  @override
                  Widget build(BuildContext context) {
                    return Scaffold(
                      appBar: AppBar(title: const Text('%3$s')),
                      body: ListView(children: [
                %4$s
                      ]),
                      persistentFooterButtons: [
                        TextButton.icon(onPressed: _delete, icon: const Icon(Icons.delete), label: const Text('Eliminar')),
                        FilledButton.icon(onPressed: _edit, icon: const Icon(Icons.edit), label: const Text('Editar')),
                      ],
                    );
                  }
                }
                """.formatted(prefix, entity.codeName(), escapeDart(entity.displayName()), rows);
    }

    private String entityForm(DomainManifestPlan.Entity entity, DomainManifestPlan manifest) {
        String prefix = snake(entity.codeName());
        List<DomainManifestPlan.Attribute> fields = entity.attributes().stream().filter(a -> a.createWritable() || a.updateWritable()).toList();
        String controllerDecl = fields.stream().filter(a -> a.type() != DomainManifestPlan.SemanticType.BOOLEAN).map(a -> "  final %sController = TextEditingController();".formatted(a.apiName())).collect(Collectors.joining("\n"));
        String boolDecl = fields.stream().filter(a -> a.type() == DomainManifestPlan.SemanticType.BOOLEAN).map(a -> "  bool %sValue = false;".formatted(a.apiName())).collect(Collectors.joining("\n"));
        String init = fields.stream().map(a -> a.type() == DomainManifestPlan.SemanticType.BOOLEAN
                ? "    %sValue = widget.existing?.data['%s'] == true;".formatted(a.apiName(), a.apiName())
                : "    %sController.text = widget.existing?.data['%s']?.toString() ?? '';".formatted(a.apiName(), a.apiName())).collect(Collectors.joining("\n"));
        String dispose = fields.stream().filter(a -> a.type() != DomainManifestPlan.SemanticType.BOOLEAN).map(a -> "    %sController.dispose();".formatted(a.apiName())).collect(Collectors.joining("\n"));
        String widgets = fields.stream().map(this::fieldWidget).collect(Collectors.joining("\n"));
        String relationDecl = entity.relations().stream().map(r -> "  Object? %sValue;".formatted(r.requestField())).collect(Collectors.joining("\n"));
        String relationWidgets = entity.relations().stream().map(r -> relationWidget(r, manifest)).collect(Collectors.joining("\n"));
        String payload = fields.stream().map(a -> "      '%s': %s,".formatted(a.apiName(), fieldPayload(a))).collect(Collectors.joining("\n"));
        String relationPayload = entity.relations().stream().map(r -> "      '%s': %sValue,".formatted(r.requestField(), r.requestField())).collect(Collectors.joining("\n"));
        return """
                import 'package:flutter/material.dart';
                import '../../core/widgets/reference_picker.dart';
                import '%1$s_api.dart';
                import '%1$s_model.dart';

                class %2$sFormPage extends StatefulWidget {
                  const %2$sFormPage({super.key, this.existing});
                  final %2$sModel? existing;
                  @override State<%2$sFormPage> createState() => _%2$sFormPageState();
                }

                class _%2$sFormPageState extends State<%2$sFormPage> {
                  final api = %2$sApi();
                  final formKey = GlobalKey<FormState>();
                %3$s
                %4$s
                %5$s
                  bool saving = false;
                  String error = '';

                  @override
                  void initState() {
                    super.initState();
                %6$s
                  }

                  @override
                  void dispose() {
                %7$s
                    super.dispose();
                  }

                  @override
                  Widget build(BuildContext context) {
                    return Scaffold(
                      appBar: AppBar(title: Text(widget.existing == null ? 'Crear %8$s' : 'Editar %8$s')),
                      body: Form(
                        key: formKey,
                        child: ListView(padding: const EdgeInsets.all(16), children: [
                %9$s
                %10$s
                          if (error.isNotEmpty) Padding(padding: const EdgeInsets.symmetric(vertical: 8), child: Text(error, style: TextStyle(color: Theme.of(context).colorScheme.error))),
                          const SizedBox(height: 12),
                          FilledButton(onPressed: saving ? null : _save, child: Text(saving ? 'Guardando…' : 'Guardar')),
                        ]),
                      ),
                    );
                  }

                  Future<void> _save() async {
                    if (!(formKey.currentState?.validate() ?? false)) return;
                    setState(() { saving = true; error = ''; });
                    final request = <String, dynamic>{
                %11$s
                %12$s
                    };
                    try {
                      final saved = widget.existing == null
                          ? await api.create(request)
                          : await api.update(api.idOf(widget.existing!), request);
                      if (mounted) Navigator.of(context).pop(saved);
                    } catch (e) {
                      if (mounted) setState(() => error = e.toString());
                    } finally {
                      if (mounted) setState(() => saving = false);
                    }
                  }
                }
                """.formatted(prefix, entity.codeName(), controllerDecl, boolDecl, relationDecl, init, dispose,
                escapeDart(entity.displayName()), widgets, relationWidgets, payload, relationPayload);
    }

    private String fieldWidget(DomainManifestPlan.Attribute a) {
        if (a.type() == DomainManifestPlan.SemanticType.BOOLEAN) {
            return """
                          SwitchListTile(
                            title: const Text('%s'),
                            value: %sValue,
                            onChanged: (value) => setState(() => %sValue = value),
                          ),
                    """.formatted(escapeDart(a.logicalName()), a.apiName(), a.apiName());
        }
        String keyboard = switch (a.type()) {
            case INTEGER, LONG, DECIMAL -> "TextInputType.number";
            case DATE, DATETIME -> "TextInputType.datetime";
            default -> "TextInputType.text";
        };
        String obscure = a.sensitive() || a.writeOnly() ? "true" : "false";
        String validator = a.validation().requiredOnCreate() ? "validator: (value) => (value == null || value.trim().isEmpty) ? 'Campo requerido' : null," : "";
        return """
                          Padding(
                            padding: const EdgeInsets.only(bottom: 12),
                            child: TextFormField(
                              controller: %2$sController,
                              decoration: const InputDecoration(labelText: '%1$s'),
                              keyboardType: %3$s,
                              obscureText: %4$s,
                              %5$s
                            ),
                          ),
                """.formatted(escapeDart(a.logicalName()), a.apiName(), keyboard, obscure, validator);
    }

    private String relationWidget(DomainManifestPlan.Relation relation, DomainManifestPlan manifest) {
        DomainManifestPlan.Entity target = manifest.entities().stream().filter(e -> e.id().equals(relation.targetEntityId())).findFirst().orElseThrow();
        boolean multiple = "MANY_TO_MANY".equals(relation.kind()) || "ONE_TO_MANY".equals(relation.kind());
        String idFields = target.identifier().fields().stream().map(f -> "'" + f.name() + "'").collect(Collectors.joining(", "));
        return """
                          Padding(
                            padding: const EdgeInsets.only(bottom: 12),
                            child: ReferencePicker(
                              label: '%s',
                              endpoint: '%s',
                              idFields: const [%s],
                              multiple: %s,
                              value: %sValue,
                              onChanged: (value) => setState(() => %sValue = value),
                            ),
                          ),
                """.formatted(escapeDart(relation.name()), target.endpoint(), idFields, multiple, relation.requestField(), relation.requestField());
    }

    private String fieldPayload(DomainManifestPlan.Attribute a) {
        if (a.type() == DomainManifestPlan.SemanticType.BOOLEAN) return a.apiName() + "Value";
        return switch (a.type()) {
            case INTEGER, LONG -> "int.tryParse(" + a.apiName() + "Controller.text.trim())";
            case DECIMAL -> "double.tryParse(" + a.apiName() + "Controller.text.trim())";
            default -> a.apiName() + "Controller.text.trim().isEmpty ? null : " + a.apiName() + "Controller.text.trim()";
        };
    }

    private String tokenStore() {
        return """
                import 'package:flutter_secure_storage/flutter_secure_storage.dart';

                class TokenStore {
                  const TokenStore();
                  static const _storage = FlutterSecureStorage();
                  static const _key = 'classforge_jwt';
                  Future<String?> read() => _storage.read(key: _key);
                  Future<void> write(String token) => _storage.write(key: _key, value: token);
                  Future<void> clear() => _storage.delete(key: _key);
                }
                """;
    }

    private String authApi() {
        return """
                import '../api/api_client.dart';
                import 'token_store.dart';

                class AuthApi {
                  AuthApi({ApiClient? client}) : client = client ?? ApiClient();
                  final ApiClient client;
                  final TokenStore tokens = const TokenStore();

                  Future<void> login(String username, String password) async {
                    final raw = await client.request('POST', '/api/auth/login', body: {'username': username, 'password': password});
                    final token = (raw as Map)['accessToken']?.toString();
                    if (token == null || token.isEmpty) throw const ApiException(500, 'Respuesta de login sin accessToken');
                    await tokens.write(token);
                  }

                  Future<void> bootstrap(Map<String, dynamic> body) async {
                    final raw = await client.request('POST', '/api/auth/bootstrap', body: body);
                    final token = (raw as Map)['accessToken']?.toString();
                    if (token != null && token.isNotEmpty) await tokens.write(token);
                  }

                  Future<void> logout() => tokens.clear();
                }
                """;
    }

    private String authGate() {
        return """
                import 'package:flutter/material.dart';
                import 'token_store.dart';
                import '../../auth/login_page.dart';

                class AuthGate extends StatelessWidget {
                  const AuthGate({super.key, required this.child});
                  final Widget child;
                  @override
                  Widget build(BuildContext context) {
                    return FutureBuilder<String?>(
                      future: const TokenStore().read(),
                      builder: (context, snapshot) {
                        if (snapshot.connectionState != ConnectionState.done) return const Scaffold(body: Center(child: CircularProgressIndicator()));
                        return snapshot.data == null ? const LoginPage() : child;
                      },
                    );
                  }
                }
                """;
    }

    private String loginPage() {
        return """
                import 'package:flutter/material.dart';
                import '../core/auth/auth_api.dart';

                class LoginPage extends StatefulWidget {
                  const LoginPage({super.key});
                  @override State<LoginPage> createState() => _LoginPageState();
                }

                class _LoginPageState extends State<LoginPage> {
                  final username = TextEditingController();
                  final password = TextEditingController();
                  final auth = AuthApi();
                  String error = '';
                  bool loading = false;
                  @override void dispose() { username.dispose(); password.dispose(); super.dispose(); }

                  Future<void> _login() async {
                    setState(() { loading = true; error = ''; });
                    try {
                      await auth.login(username.text.trim(), password.text);
                      if (mounted) Navigator.of(context).pushNamedAndRemoveUntil('/', (route) => false);
                    } catch (e) { if (mounted) setState(() => error = e.toString()); }
                    finally { if (mounted) setState(() => loading = false); }
                  }

                  @override
                  Widget build(BuildContext context) => Scaffold(
                    body: Center(child: ConstrainedBox(
                      constraints: const BoxConstraints(maxWidth: 420),
                      child: Padding(padding: const EdgeInsets.all(24), child: Column(mainAxisSize: MainAxisSize.min, children: [
                        Text('Iniciar sesión', style: Theme.of(context).textTheme.headlineMedium),
                        const SizedBox(height: 20),
                        TextField(controller: username, decoration: const InputDecoration(labelText: 'Usuario')),
                        const SizedBox(height: 12),
                        TextField(controller: password, obscureText: true, decoration: const InputDecoration(labelText: 'Contraseña')),
                        if (error.isNotEmpty) Padding(padding: const EdgeInsets.only(top: 12), child: Text(error)),
                        const SizedBox(height: 16),
                        FilledButton(onPressed: loading ? null : _login, child: const Text('Entrar')),
                        TextButton(onPressed: () => Navigator.of(context).pushNamed('/bootstrap'), child: const Text('Crear primera cuenta')),
                      ])),
                    )),
                  );
                }
                """;
    }

    private String bootstrapPage(DomainManifestPlan.Entity authEntity, DomainManifestPlan manifest) {
        DomainManifestPlan.Attribute username = authEntity.attributes().stream().filter(a -> a.id().equals(manifest.authentication().usernameAttributeId())).findFirst().orElseThrow();
        DomainManifestPlan.Attribute password = authEntity.attributes().stream().filter(a -> a.id().equals(manifest.authentication().passwordAttributeId())).findFirst().orElseThrow();
        return """
                import 'package:flutter/material.dart';
                import '../core/auth/auth_api.dart';

                class BootstrapPage extends StatefulWidget {
                  const BootstrapPage({super.key});
                  @override State<BootstrapPage> createState() => _BootstrapPageState();
                }

                class _BootstrapPageState extends State<BootstrapPage> {
                  final username = TextEditingController();
                  final password = TextEditingController();
                  final auth = AuthApi();
                  String error = '';
                  @override void dispose() { username.dispose(); password.dispose(); super.dispose(); }

                  Future<void> _submit() async {
                    try {
                      await auth.bootstrap({'%s': username.text.trim(), '%s': password.text});
                      if (mounted) Navigator.of(context).pushNamedAndRemoveUntil('/', (route) => false);
                    } catch (e) { if (mounted) setState(() => error = e.toString()); }
                  }

                  @override
                  Widget build(BuildContext context) => Scaffold(
                    appBar: AppBar(title: const Text('Primera cuenta')),
                    body: ListView(padding: const EdgeInsets.all(24), children: [
                      TextField(controller: username, decoration: const InputDecoration(labelText: '%s')),
                      const SizedBox(height: 12),
                      TextField(controller: password, obscureText: true, decoration: const InputDecoration(labelText: '%s')),
                      if (error.isNotEmpty) Padding(padding: const EdgeInsets.only(top: 12), child: Text(error)),
                      const SizedBox(height: 16),
                      FilledButton(onPressed: _submit, child: const Text('Crear cuenta inicial')),
                    ]),
                  );
                }
                """.formatted(username.apiName(), password.apiName(), escapeDart(username.logicalName()), escapeDart(password.logicalName()));
    }

    private String smokeTest(String packageName) {
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

    private String readme(DomainManifestPlan manifest, String artifactName, String color) {
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


    private String assistantApi(boolean auth) {
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

    private String assistantPage() {
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

    private String settingsGradle() {
        return """
                pluginManagement {
                    val flutterSdkPath = run {
                        val properties = java.util.Properties()
                        file("local.properties").inputStream().use { properties.load(it) }
                        val flutterSdkPath = properties.getProperty("flutter.sdk")
                        require(flutterSdkPath != null) { "flutter.sdk not set in local.properties" }
                        flutterSdkPath
                    }
                    includeBuild("$flutterSdkPath/packages/flutter_tools/gradle")
                    repositories { google(); mavenCentral(); gradlePluginPortal() }
                }

                plugins {
                    id("dev.flutter.flutter-plugin-loader") version "1.0.0"
                    id("com.android.application") version "9.1.0" apply false
                    id("org.jetbrains.kotlin.android") version "2.4.0" apply false
                }

                include(":app")
                """;
    }

    private String androidBuildGradle() {
        return """
                allprojects { repositories { google(); mavenCentral() } }
                val newBuildDir = rootProject.layout.buildDirectory.dir("../../build").get()
                rootProject.layout.buildDirectory.value(newBuildDir)
                subprojects {
                    val newSubprojectBuildDir = newBuildDir.dir(project.name)
                    project.layout.buildDirectory.value(newSubprojectBuildDir)
                }
                subprojects { project.evaluationDependsOn(":app") }
                tasks.register<Delete>("clean") { delete(rootProject.layout.buildDirectory) }
                """;
    }

    private String gradleProperties() {
        return """
                org.gradle.jvmargs=-Xmx4G -XX:MaxMetaspaceSize=2G -XX:ReservedCodeCacheSize=512m -XX:+HeapDumpOnOutOfMemoryError
                android.useAndroidX=true
                android.enableJetifier=true
                android.newDsl=false
                android.builtInKotlin=false
                """;
    }

    private String wrapperProperties() {
        return """
                distributionBase=GRADLE_USER_HOME
                distributionPath=wrapper/dists
                distributionUrl=https://services.gradle.org/distributions/gradle-9.3.1-all.zip
                networkTimeout=10000
                validateDistributionUrl=true
                zipStoreBase=GRADLE_USER_HOME
                zipStorePath=wrapper/dists
                """;
    }

    private String appBuildGradle(String namespace, boolean auth) {
        String minSdk = "23";
        return """
                plugins {
                    id("com.android.application")
                    id("org.jetbrains.kotlin.android")
                    id("dev.flutter.flutter-gradle-plugin")
                }

                android {
                    namespace = "%1$s"
                    compileSdk = flutter.compileSdkVersion
                    ndkVersion = flutter.ndkVersion

                    compileOptions {
                        sourceCompatibility = JavaVersion.VERSION_17
                        targetCompatibility = JavaVersion.VERSION_17
                    }
                    kotlin {
                        compilerOptions {
                            jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
                        }
                    }

                    defaultConfig {
                        applicationId = "%1$s"
                        minSdk = %2$s
                        targetSdk = flutter.targetSdkVersion
                        versionCode = flutter.versionCode
                        versionName = flutter.versionName
                    }

                    buildTypes {
                        release { signingConfig = signingConfigs.getByName("debug") }
                    }
                }

                flutter { source = "../.." }
                """.formatted(namespace, minSdk);
    }

    private String androidManifest(String artifactName) {
        return """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                    <uses-permission android:name="android.permission.INTERNET" />
                    <uses-permission android:name="android.permission.RECORD_AUDIO" />
                    <application android:label="%s" android:name="${applicationName}">
                        <activity android:name=".MainActivity" android:exported="true" android:launchMode="singleTop" android:taskAffinity="" android:theme="@android:style/Theme.Black.NoTitleBar" android:configChanges="orientation|keyboardHidden|keyboard|screenSize|smallestScreenSize|locale|layoutDirection|fontScale|screenLayout|density|uiMode" android:hardwareAccelerated="true" android:windowSoftInputMode="adjustResize">
                            <meta-data android:name="io.flutter.embedding.android.NormalTheme" android:resource="@android:style/Theme.Black.NoTitleBar" />
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN" />
                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                        </activity>
                        <meta-data android:name="flutterEmbedding" android:value="2" />
                    </application>
                    <queries><intent><action android:name="android.intent.action.PROCESS_TEXT" /><data android:mimeType="text/plain" /></intent></queries>
                </manifest>
                """.formatted(escapeXml(title(artifactName)));
    }

    private String debugManifest() {
        return """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                    <uses-permission android:name="android.permission.INTERNET" />
                </manifest>
                """;
    }

    private String mainActivity(String namespace) {
        return """
                package %s

                import io.flutter.embedding.android.FlutterActivity

                class MainActivity: FlutterActivity()
                """.formatted(namespace);
    }

    private DomainManifestPlan.Entity authEntity(DomainManifestPlan manifest) {
        return manifest.entities().stream().filter(e -> e.id().equals(manifest.authentication().entityId())).findFirst().orElseThrow();
    }

    private String snake(String value) {
        String normalized = value.replaceAll("([a-z0-9])([A-Z])", "$1_$2").replace('-', '_').replace(' ', '_').toLowerCase(Locale.ROOT);
        return normalized.replaceAll("[^a-z0-9_]", "_");
    }

    private String dartPackage(String artifactName) {
        String result = artifactName.toLowerCase(Locale.ROOT).replace('-', '_').replaceAll("[^a-z0-9_]", "_");
        if (result.isBlank() || !Character.isLetter(result.charAt(0))) result = "app_" + result;
        return result;
    }

    private String title(String artifactName) {
        return java.util.Arrays.stream(artifactName.split("[-_]+"))
                .filter(s -> !s.isBlank())
                .map(s -> Character.toUpperCase(s.charAt(0)) + s.substring(1))
                .collect(Collectors.joining(" "));
    }

    private String normalizeColor(String value) {
        String color = value == null ? "#2563EB" : value.trim().toUpperCase(Locale.ROOT);
        if (!color.matches("^#[0-9A-F]{6}$")) throw new IllegalArgumentException("primary color must be #RRGGBB");
        return color;
    }

    private String escapeDart(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'").replace("$", "\\$");
    }

    private String escapeXml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
