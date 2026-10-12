#!/usr/bin/env bash
# Compara la calculadora de la app (CalcMotor.java) con la "Calculadora normal" de index.html:
# las mismas secuencias de teclas deben dar exactamente la misma pantalla y el mismo resultado.
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p out/calc && cd out/calc
python3 - <<'PY'
import random
src = open('../../../../index.html', encoding='utf-8').read()
i = src.index('const Calc=(function(){'); j = src.index('function calcAbierta()')
open('calc_web.js', 'w', encoding='utf-8').write(src[i:j])
random.seed(376)
keys = list('0123456789') + ['.', '+', '-', '*', '/', '=', '%', 'par', 'sign', 'back', 'c']
seqs = ["2 0 0 + 1 0 % =", "2 0 0 * 1 0 % =", "1 + 2 * 3 =", "par 1 + 2 par * 3 =", "5 * - 3 =", "1 / 0 =",
        "1 / 3 =", "1 / 1 0 0 0 0 0 0 0 =", "1 2 3 4 5 * 1 9 % =", "5 0 0 0 + 1 9 % ="]
for _ in range(3000):
    seqs.append(' '.join(random.choice(keys) for _ in range(random.randint(1, 14))))
open('seqs.txt', 'w').write('\n'.join(seqs) + '\n')
PY
node ../../calc_web_runner.js
mkdir -p cls
javac -encoding UTF-8 -d cls ../../../app/src/main/java/cl/marin376/precios/CalcMotor.java ../../CalcJavaRunner.java
java -Dfile.encoding=UTF-8 -cp cls CalcJavaRunner
if diff -q web.out java.out >/dev/null; then
  echo "OK calculadora: $(wc -l < web.out) secuencias iguales a la web (ej. 200+10% = $(sed -n 1p java.out))"
else
  echo "FALLA: la calculadora de la app no da lo mismo que la web"; diff web.out java.out | head -20; exit 1
fi
