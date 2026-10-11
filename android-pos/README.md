# Marín POS (app Android)

Abre la app web del local (Marín 376 o Argomedo 455) en pantalla completa para POS con Android 8.x donde el
navegador es viejo y no deja instalar la PWA. Usa un WebView: la app web se actualiza sola (siempre carga
la versión publicada en GitHub Pages) y esta app busca versiones nuevas de sí misma (`descargas/pos-version.json`).

- Se compila sola en GitHub Actions (`.github/workflows/android-pos.yml`).
- APK: `descargas/pos-marin376.apk`.
