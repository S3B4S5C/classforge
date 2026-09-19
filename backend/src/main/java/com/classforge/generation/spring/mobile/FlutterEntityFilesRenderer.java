package com.classforge.generation.spring.mobile;

import com.classforge.generation.spring.domain.DomainManifestPlan;
import java.util.*;
import java.util.stream.Collectors;

final class FlutterEntityFilesRenderer {

    String entityModel(DomainManifestPlan.Entity entity) {
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

    String entityApi(DomainManifestPlan.Entity entity) {
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

    String entityList(DomainManifestPlan.Entity entity) {
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

    String entityDetail(DomainManifestPlan.Entity entity) {
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

    String entityForm(DomainManifestPlan.Entity entity, DomainManifestPlan manifest) {
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

    String fieldWidget(DomainManifestPlan.Attribute a) {
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

    String relationWidget(DomainManifestPlan.Relation relation, DomainManifestPlan manifest) {
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

    String fieldPayload(DomainManifestPlan.Attribute a) {
        if (a.type() == DomainManifestPlan.SemanticType.BOOLEAN) return a.apiName() + "Value";
        return switch (a.type()) {
            case INTEGER, LONG -> "int.tryParse(" + a.apiName() + "Controller.text.trim())";
            case DECIMAL -> "double.tryParse(" + a.apiName() + "Controller.text.trim())";
            default -> a.apiName() + "Controller.text.trim().isEmpty ? null : " + a.apiName() + "Controller.text.trim()";
        };
    }

    String snake(String value) {
        String normalized = value.replaceAll("([a-z0-9])([A-Z])", "$1_$2").replace('-', '_').replace(' ', '_').toLowerCase(Locale.ROOT);
        return normalized.replaceAll("[^a-z0-9_]", "_");
    }

    String escapeDart(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'").replace("$", "\\$");
    }
}
