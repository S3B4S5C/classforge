# Ciclo 7 — Construcción — Frontend Flutter mobile

**Fase:** Construcción
**Estado:** CERRADO
**Caso:** CU-18

## Objetivo

Cerrar la generación mobile como proyecto Flutter independiente y Android formalmente aceptado.

## Decisiones

- Flutter en lugar de Capacitor;
- proyecto `mobile/` independiente del Angular web;
- componentes/páginas específicos por entidad;
- dashboard con conteos;
- mismo color primario de CU-17;
- to-one con selector simple y colecciones con multiselección;
- Simple sin Auth;
- Auth con `flutter_secure_storage`, bootstrap/login/JWT y 401;
- Android como plataforma de acceptance.

## Criterios de salida

- [x] proyecto Flutter completo;
- [x] código específico por entidad;
- [x] dashboard;
- [x] relaciones e IDs compuestos;
- [x] Simple/Auth;
- [x] password protegido;
- [x] theme compartido;
- [x] `flutter analyze`;
- [x] `flutter test`;
- [x] `flutter build apk --debug` Simple/Auth;
- [x] regresiones CU-13..17;
- [x] documentación/evidencia.

## Resultado

CU-18 y Ciclo 7 quedan CERRADOS. El siguiente candidato es CU-19 — consultar datos mediante voz sobre la aplicación generada.
