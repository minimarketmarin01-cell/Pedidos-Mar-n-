#!/bin/bash
# Prueba en el PC de cómo la app (Api.java) lee las respuestas del Worker: catálogo completo y por cambios, categorías,
# búsqueda y ficha corta, y que con un servidor viejo (sin esas acciones) no baja el catálogo. Usa un servidor falso que
# entrega respuestas reales del Worker (mock/) y un lector JSON de gson con la misma interfaz que el de Android.
# Uso: android-precios/pruebas/api/probar_api.sh
set -euo pipefail
cd "$(dirname "$0")"
M2=https://repo.maven.apache.org/maven2
mkdir -p /tmp/probar_api && cd /tmp/probar_api
[ -f gson.jar ] || curl -fsSL --retry 5 --retry-delay 3 -o gson.jar $M2/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar
[ -f json.jar ] || curl -fsSL --retry 5 --retry-delay 3 -o json.jar $M2/org/json/json/20231013/json-20231013.jar
DIR="$OLDPWD"
SRC="$DIR/../../app/src/main/java/cl/marin376/precios"
rm -rf out && mkdir out
javac -nowarn -encoding UTF-8 -d out -cp gson.jar:json.jar "$DIR"/shim/android/util/*.java "$SRC"/{Api,Producto,Local}.java "$DIR/PruebaApi.java"
MODO=nuevo python3 "$DIR/servidor.py" 18711 "$DIR/mock" & P1=$!
MODO=viejo python3 "$DIR/servidor.py" 18712 "$DIR/mock" & P2=$!
trap 'kill $P1 $P2 2>/dev/null || true' EXIT
sleep 1.5
java -Dhttp.nonProxyHosts=127.0.0.1 -cp out:gson.jar:json.jar PruebaApi 18711
java -Dhttp.nonProxyHosts=127.0.0.1 -cp out:gson.jar:json.jar PruebaApi 18712 viejo
echo "Todas las pruebas de la app contra el Worker pasaron"
