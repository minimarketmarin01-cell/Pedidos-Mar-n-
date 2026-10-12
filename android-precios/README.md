# Precios Marín (app Android)

App liviana para el POS con Android 8.1 para **imprimir etiquetas de precio** por Bluetooth (impresora
térmica de 58 mm o la interna del POS «InnerPrinter», ESC/POS o TSPL). No carga la app web.

Pantalla igual a la venta de Loyverse: barra verde, categoría arriba (o 🔍 buscar), lista alfabética con
foto, nombre y precio. Tocar un producto abre «Imprimir etiqueta» (cantidad, elaboración y vencimiento opcionales, vista previa);
al imprimir bien vuelve sola a la lista. ☰ = local, impresora, prueba, actualizar, ajustes y «Elegir categorías a mostrar» (por local; el buscador siempre busca en todos los productos).
Si el producto no tiene código de barras, «Crear código de barras» genera uno igual que la app web
(`generarCodigoBarrasInterno`: 04 + 10 dígitos al azar + verificador EAN-13, sin repetir en el catálogo) y lo
guarda en Loyverse con `editar_codigo_barras` (el servidor rechaza repetidos). También permite cambiarlo, con
confirmación porque las etiquetas viejas dejan de servir.
Al terminar se avanza papel (12 mm por defecto, «Papel extra al terminar») para que la última etiqueta
salga entera por el borde de corte.

- Locales: **Marín 376** y **Argomedo 455** (cada uno con su servidor y su lista guardada).
- Lista de productos guardada en el equipo (`catalogo_<local>.json`): abre al instante y funciona sin internet.
  Se actualiza sola si tiene más de 6 horas, o con el botón «Actualizar productos».
- Al elegir un producto confirma con el servidor (`ficha_producto`) el **precio y código actuales** y trae la
  fecha del lote más próximo a vencer (editable). Si no responde en 6 s, imprime con la lista guardada.
- Lector de códigos tipo teclado: escanear en el buscador elige el producto directo.

## Etiqueta (misma que «Imprimir etiquetas» de la app web)
Alto por defecto 32 mm; letra pequeña/normal/grande con los mismos mm que `ET_TEXTO_PRESETS`
(nombre 3,8/5,0/6,0 · precio 4,6/5,8/6,8 · código y fecha 2,8/4,0/4,8). Borde redondeado, nombre + precio
arriba, barras al medio, código + «Vence: dd/mm/aaaa» abajo.

Código de barras (pensado para lectores láser):
| Código del producto | Se imprime como |
|---|---|
| 13 dígitos, verificador correcto, no empieza en 0 | EAN-13 |
| Empieza en 0 (internos 04…), 12 dígitos, verificador malo, SKU con letras | Code 128 (el lector devuelve el texto exacto) |
| Solo si Code 128 no puede | Code 39 |

Barras sin suavizado con módulo entero en puntos (0,25–0,5 mm), zona de silencio ≥ 10 módulos (11 en EAN-13)
y la app avisa si las barras quedan bajo 12 mm de alto.

## Compilación y pruebas
- `.github/workflows/android-precios.yml` compila el APK y además corre `pruebas/`: genera los bytes
  ESC/POS y TSPL de 12 códigos y los decodifica con **pyzbar** (falla el CI si alguno no se lee).
- El APK queda en `descargas/precios-marin376.apk` (firmado con la misma llave de `android-etiquetas/`).

## Catálogo liviano en el servidor (opcional, recomendado)
Ver [`worker-catalogo-etiquetas.md`](worker-catalogo-etiquetas.md). Sin ese cambio la app funciona igual
(baja el catálogo completo y descarta en el momento todo lo que no es sku/nombre/precio/código).

## Actualizaciones
El CI publica, junto al APK, `descargas/precios-version.json` (`versionCode` y `versionName` de `app/build.gradle`).
Al abrirse (cada 3 h como máximo) o desde ☰ → «Buscar actualización», la app compara ese número con el
instalado. Si hay uno nuevo, baja el APK y abre el instalador (`ApkProvider` lo entrega como `content://`), y la
persona solo toca «Instalar». La primera vez Android 8 pide activar «Permitir de esta fuente» para la app.
**Cada cambio debe subir `versionCode`**, si no la app no se entera.

## Verdura rápida (solo Marín 376)
Botón «＋ Verdura» en la barra verde (o ☰ → «Verdura rápida»). Igual que «Verdura rápida» de la app web:
`crear_producto` en la categoría «VERDURAS y ENSALADAS» (id leído con `catalogo_crear`), IVA, inventario activo
sin stock inicial. Precio = costo bruto / (1 − margen) con margen 30 % por defecto (`Precio.java`, mismo cálculo que
`PriceCalc`); se propone la siguiente terminación en 90 y hay botones .50/.90/.00. Por defecto crea también el código
interno 04… para imprimir la etiqueta al tiro. Avisa si ya existe un producto con el mismo nombre.

## Calculadora
«🧮 Calculadora» en «Verdura rápida» (junto a Costo, con «Usar como costo») y en ☰. Es la «Calculadora normal»
de la web estilo Samsung: `CalcMotor.java` es la traducción 1:1 de `Calc` (index.html) y el CI la compara con el
JavaScript de la web en 3.000 secuencias de teclas (`pruebas/comparar_calculadora.sh`).
