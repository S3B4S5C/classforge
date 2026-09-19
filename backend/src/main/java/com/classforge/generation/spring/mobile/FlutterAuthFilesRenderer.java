package com.classforge.generation.spring.mobile;

import com.classforge.generation.spring.domain.DomainManifestPlan;
import java.util.*;
import java.util.stream.Collectors;

final class FlutterAuthFilesRenderer {

    String tokenStore() {
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

    String authApi() {
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

    String authGate() {
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

    String loginPage() {
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

    String bootstrapPage(DomainManifestPlan.Entity authEntity, DomainManifestPlan manifest) {
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

    String escapeDart(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'").replace("$", "\\$");
    }
}
