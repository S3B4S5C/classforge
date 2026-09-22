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
                  int pageIndex = 0, totalPages = 0, requestVersion = 0;
                  String appliedSearch = '';

                  @override void initState() { super.initState(); _load(); }
                  @override void dispose() { requestVersion++; search.dispose(); super.dispose(); }

                  void _search() { appliedSearch = search.text.trim(); _load(); }

                  Future<void> _load({int page = 0}) async {
                    if (!mounted) return;
                    final version = ++requestVersion;
                    setState(() { loading = true; error = ''; items = []; });
                    try {
                      final result = await api.list(page: page, q: appliedSearch);
                      if (!mounted || version != requestVersion) return;
                      if (result.items.isEmpty && page > 0) {
                        await _load(page: result.totalPages > 0 ? result.totalPages - 1 : 0);
                        return;
                      }
                      setState(() { items = result.items; pageIndex = result.page; totalPages = result.totalPages; });
                    } catch (e) { if (mounted && version == requestVersion) setState(() => error = e.toString()); }
                    finally { if (mounted && version == requestVersion) setState(() => loading = false); }
                  }

                  Future<void> _create() async {
                    final saved = await Navigator.of(context).push<%2$sModel>(MaterialPageRoute(builder: (_) => const %2$sFormPage()));
                    if (!mounted || saved == null) return;
                    search.clear(); appliedSearch = '';
                    await Navigator.of(context).push(MaterialPageRoute(builder: (_) => %2$sDetailPage(record: saved)));
                    if (mounted) _load();
                  }

                  @override
                  Widget build(BuildContext context) {
                    return Scaffold(
                      appBar: AppBar(title: const Text('%3$s')),
                      floatingActionButton: FloatingActionButton(
                        onPressed: _create,
                        child: const Icon(Icons.add),
                      ),
                      body: Column(children: [
                        Padding(
                          padding: const EdgeInsets.all(12),
                          child: TextField(controller: search, decoration: InputDecoration(labelText: 'Buscar', suffixIcon: IconButton(onPressed: _search, icon: const Icon(Icons.search))), onSubmitted: (_) => _search()),
                        ),
                        if (loading) const LinearProgressIndicator(),
                        if (error.isNotEmpty) Padding(padding: const EdgeInsets.all(12), child: Text(error, style: TextStyle(color: Theme.of(context).colorScheme.error))),
                        Expanded(
                          child: RefreshIndicator(
                            onRefresh: () => _load(page: pageIndex),
                            child: ListView.builder(
                              physics: const AlwaysScrollableScrollPhysics(),
                              itemCount: items.isEmpty ? 1 : items.length,
                              itemBuilder: (context, index) {
                                if (items.isEmpty) return Padding(padding: const EdgeInsets.all(24), child: Text(loading ? 'Cargando…' : error.isNotEmpty ? 'Desliza para reintentar.' : 'No hay registros.'));
                                final record = items[index];
                                return Card(
                                  margin: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                                  child: ListTile(
                                    title: Text('%3$s #${index + 1}'),
                                    subtitle: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                        %4$s
                                    ]),
                                    trailing: const Icon(Icons.chevron_right),
                                    onTap: () async { await Navigator.of(context).push(MaterialPageRoute(builder: (_) => %2$sDetailPage(record: record))); if (mounted) _load(page: pageIndex); },
                                  ),
                                );
                              },
                            ),
                          ),
                        ),
                        Padding(
                          padding: const EdgeInsets.fromLTRB(12, 6, 12, 12),
                          child: Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
                            TextButton(onPressed: loading || pageIndex == 0 ? null : () => _load(page: pageIndex - 1), child: const Text('Anterior')),
                            Text('Pagina ${pageIndex + 1} de ${totalPages == 0 ? 1 : totalPages}'),
                            TextButton(onPressed: loading || pageIndex + 1 >= totalPages ? null : () => _load(page: pageIndex + 1), child: const Text('Siguiente')),
                          ]),
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
                  bool deleting = false;
                  String error = '';

                  Widget _row(String label, Object? value) => ListTile(title: Text(label), subtitle: Text(value?.toString() ?? '—'));

                  Future<void> _edit() async {
                    final updated = await Navigator.of(context).push<%2$sModel>(MaterialPageRoute(builder: (_) => %2$sFormPage(existing: record)));
                    if (updated != null && mounted) setState(() => record = updated);
                  }

                  Future<void> _delete() async {
                    if (deleting) return;
                    setState(() { deleting = true; error = ''; });
                    final confirmed = await showDialog<bool>(context: context, builder: (dialogContext) => AlertDialog(
                      title: const Text('Eliminar registro'), content: const Text('Esta accion no se puede deshacer.'),
                      actions: [
                        TextButton(onPressed: () => Navigator.pop(dialogContext, false), child: const Text('Cancelar')),
                        FilledButton(onPressed: () => Navigator.pop(dialogContext, true), child: const Text('Eliminar')),
                      ],
                    ));
                    if (!mounted) return;
                    if (confirmed != true) { setState(() => deleting = false); return; }
                    try {
                      await api.delete(api.idOf(record));
                      if (mounted) Navigator.of(context).pop();
                    } catch (e) { if (mounted) setState(() => error = e.toString()); }
                    finally { if (mounted) setState(() => deleting = false); }
                  }

                  @override
                  Widget build(BuildContext context) {
                    return Scaffold(
                      appBar: AppBar(title: const Text('%3$s')),
                      body: ListView(children: [
                        if (deleting) const LinearProgressIndicator(),
                        if (error.isNotEmpty) ListTile(title: Text(error, style: TextStyle(color: Theme.of(context).colorScheme.error))),
                %4$s
                      ]),
                      persistentFooterButtons: [
                        TextButton.icon(onPressed: deleting ? null : _delete, icon: const Icon(Icons.delete), label: const Text('Eliminar')),
                        FilledButton.icon(onPressed: deleting ? null : _edit, icon: const Icon(Icons.edit), label: const Text('Editar')),
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
        String init = fields.stream().map(this::fieldInitialValue).collect(Collectors.joining("\n"));
        init += "\n" + entity.relations().stream().map(r -> "    %sValue = widget.existing?.data['%s']%s;".formatted(r.requestField(), r.requestField(), "MANY_TO_MANY".equals(r.kind()) ? " ?? <Object>[]" : "")).collect(Collectors.joining("\n"));
        String dispose = fields.stream().filter(a -> a.type() != DomainManifestPlan.SemanticType.BOOLEAN).map(a -> "    %sController.dispose();".formatted(a.apiName())).collect(Collectors.joining("\n"));
        String widgets = fields.stream().map(this::fieldWidget).collect(Collectors.joining("\n"));
        String relationDecl = entity.relations().stream().map(r -> "  Object? %sValue;".formatted(r.requestField())).collect(Collectors.joining("\n"));
        String relationWidgets = entity.relations().stream().map(r -> relationWidget(r, manifest)).collect(Collectors.joining("\n"));
        String payload = fields.stream().map(a -> "      if ((widget.existing == null ? %s : %s)%s) '%s': %s,".formatted(a.createWritable(), a.updateWritable(), a.writeOnly() && !a.validation().requiredOnUpdate() ? " && (widget.existing == null || " + a.apiName() + "Controller.text.isNotEmpty)" : "", a.apiName(), fieldPayload(a))).collect(Collectors.joining("\n"));
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
                    if (saving || !(formKey.currentState?.validate() ?? false)) return;
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

    String fieldInitialValue(DomainManifestPlan.Attribute attribute) {
        if (attribute.type() == DomainManifestPlan.SemanticType.BOOLEAN) {
            return "    %sValue = widget.existing?.data['%s'] == true;".formatted(attribute.apiName(), attribute.apiName());
        }
        if (attribute.type() == DomainManifestPlan.SemanticType.DATE || attribute.type() == DomainManifestPlan.SemanticType.DATETIME) {
            String end = attribute.type() == DomainManifestPlan.SemanticType.DATE ? "10" : "16";
            return "    %1$sController.text = widget.existing == null ? DateTime.now().toIso8601String().substring(0, %3$s) : (widget.existing?.data['%2$s']?.toString() ?? '');"
                    .formatted(attribute.apiName(), attribute.apiName(), end);
        }
        return "    %sController.text = widget.existing?.data['%s']?.toString() ?? '';".formatted(attribute.apiName(), attribute.apiName());
    }

    List<String> referenceLabelFields(DomainManifestPlan.Entity entity) {
        return entity.attributes().stream()
                .filter(DomainManifestPlan.Attribute::readable)
                .filter(attribute -> !attribute.identifier() && !attribute.sensitive() && !attribute.writeOnly())
                .filter(attribute -> attribute.type() == DomainManifestPlan.SemanticType.STRING
                        || attribute.type() == DomainManifestPlan.SemanticType.INTEGER
                        || attribute.type() == DomainManifestPlan.SemanticType.LONG
                        || attribute.type() == DomainManifestPlan.SemanticType.DECIMAL)
                .sorted(Comparator.comparingInt(this::referenceLabelScore).reversed())
                .map(DomainManifestPlan.Attribute::apiName)
                .toList();
    }

    int referenceLabelScore(DomainManifestPlan.Attribute attribute) {
        String name = attribute.apiName().toLowerCase(Locale.ROOT)
                .replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u")
                .replaceAll("[^a-z0-9]", "");
        int semantic = attribute.type() == DomainManifestPlan.SemanticType.STRING ? 100 : 0;
        if (name.equals("nombre") || name.equals("name") || name.contains("nombrecompleto")
                || name.contains("fullname") || name.contains("displayname") || name.contains("razonsocial")) return 1000 + semantic;
        if (name.contains("nombre") || name.endsWith("name") || name.contains("firstname") || name.contains("lastname")
                || name.contains("apellido") || name.contains("surname")) return 900 + semantic;
        if (name.contains("titulo") || name.contains("title") || name.contains("label") || name.contains("etiqueta")) return 800 + semantic;
        if (name.contains("codigo") || name.contains("code") || name.contains("username") || name.contains("usuario")
                || name.contains("email") || name.contains("correo")) return 700 + semantic;
        if (name.contains("descripcion") || name.contains("description") || name.contains("motivo")) return 600 + semantic;
        return semantic;
    }

    String fieldWidget(DomainManifestPlan.Attribute a) {
        if (a.type() == DomainManifestPlan.SemanticType.BOOLEAN) {
            return """
                          SwitchListTile(
                            title: const Text('%s'),
                            value: %sValue,
                            onChanged: saving || !(widget.existing == null ? %s : %s) ? null : (value) => setState(() => %sValue = value),
                          ),
                    """.formatted(escapeDart(a.logicalName()), a.apiName(), a.createWritable(), a.updateWritable(), a.apiName());
        }
        String keyboard = switch (a.type()) {
            case INTEGER, LONG, DECIMAL -> "const TextInputType.numberWithOptions(signed: true, decimal: true)";
            case DATE, DATETIME -> "TextInputType.datetime";
            default -> "TextInputType.text";
        };
        String obscure = a.sensitive() || a.writeOnly() ? "true" : "false";
        String valueCheck = switch (a.type()) {
            case INTEGER, LONG -> "if (int.tryParse(text) == null) return 'Ingresa un numero entero valido';";
            case DECIMAL -> "final number = double.tryParse(text); if (number == null || !number.isFinite) return 'Ingresa un numero valido';";
            case DATE, DATETIME -> "if (DateTime.tryParse(text) == null) return 'Ingresa una fecha valida';";
            default -> "";
        };
        String validator = """
                validator: (value) {
                  if (!(widget.existing == null ? %s : %s)) return null;
                  final text = (value ?? '').trim();
                  if (text.isEmpty) return (widget.existing == null ? %s : %s) ? 'Campo requerido' : null;
                  %s
                  return null;
                },
                """.formatted(a.createWritable(), a.updateWritable(), a.validation().requiredOnCreate(), a.validation().requiredOnUpdate(), valueCheck);
        return """
                          Padding(
                            padding: const EdgeInsets.only(bottom: 12),
                            child: TextFormField(
                              controller: %2$sController,
                              enabled: !saving && (widget.existing == null ? %6$s : %7$s),
                              decoration: const InputDecoration(labelText: '%1$s'),
                              keyboardType: %3$s,
                              obscureText: %4$s,
                              %5$s
                            ),
                          ),
                """.formatted(escapeDart(a.logicalName()), a.apiName(), keyboard, obscure, validator, a.createWritable(), a.updateWritable());
    }

    String relationWidget(DomainManifestPlan.Relation relation, DomainManifestPlan manifest) {
        DomainManifestPlan.Entity target = manifest.entities().stream().filter(e -> e.id().equals(relation.targetEntityId())).findFirst().orElseThrow();
        boolean multiple = "MANY_TO_MANY".equals(relation.kind()) || "ONE_TO_MANY".equals(relation.kind());
        String idFields = target.identifier().fields().stream().map(f -> "'" + f.name() + "'").collect(Collectors.joining(", "));
        return """
                          Padding(
                            padding: const EdgeInsets.only(bottom: 12),
                            child: ReferencePicker(
                              key: const ValueKey('%7$s'),
                              enabled: !saving,
                              requiredSelection: %9$s,
                              label: '%s',
                              endpoint: '%s',
                              idFields: const [%s],
                              labelFields: const [%s],
                              multiple: %s,
                              value: %sValue,
                              onChanged: (value) => setState(() => %sValue = value),
                            ),
                          ),
                """.formatted(escapeDart(relation.name()), target.endpoint(), idFields,
                referenceLabelFields(target).stream().map(field -> "'" + escapeDart(field) + "'").collect(Collectors.joining(", ")),
                multiple, relation.requestField(), relation.requestField(), relation.requestField(), !multiple && !Boolean.TRUE.equals(relation.optional()));
    }

    String fieldPayload(DomainManifestPlan.Attribute a) {
        if (a.type() == DomainManifestPlan.SemanticType.BOOLEAN) return a.apiName() + "Value";
        if (a.writeOnly()) return a.apiName() + "Controller.text.isEmpty ? null : " + a.apiName() + "Controller.text";
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
