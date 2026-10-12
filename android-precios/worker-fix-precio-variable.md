# Arreglo del servidor: precio VARIABLE en la tienda

Error que arregla (al guardar precio en «Cambiar precio» o en la Ficha web):

`/items HTTP 400 … price must not be set if pricing_type is 'VARIABLE' … variants[0].stores[0].price`

Causa: `actualizarPrecioCostoLoyverse` (Worker `marin376-api`) solo pasa a FIJO el precio
variable general del producto, no el de la tienda.

## Cómo aplicarlo (Cloudflare → Workers → marin376-api → Edit code)

1. Busca (Ctrl+F / buscar en la página): `const esVariable = String(v.default_pricing_type`
2. Debajo de esa línea hay un bloque que empieza con `if (precio != null) {` y termina con el
   cierre del `else if (esVariable) { … }`. Bórralo completo y pega esto en su lugar:

```js
  const tiendaVariable = (st) => esVariable || String(st.pricing_type || "").toUpperCase() === "VARIABLE";
  if (precio != null) {
    if (esVariable) v.default_pricing_type = "FIXED";
    v.default_price = precio;
    if (v.stores && v.stores.length) {
      v.stores.forEach((st) => {
        if (tiendaVariable(st)) st.pricing_type = "FIXED";
        st.price = precio;
      });
    }
  } else {
    if (esVariable) delete v.default_price;
    if (v.stores && v.stores.length) v.stores.forEach((st) => {
      if (tiendaVariable(st)) delete st.price;
    });
  }
```

3. Lo que sigue (`if (costo != null) { …`) no se toca.
4. **Deploy**.

Efecto: al guardar un precio, el producto queda con precio FIJO en la tienda (la caja deja de
pedir digitar el precio). No hay que actualizar la app.

## Opcional (recomendado): más tiempo para Loyverse

Error que arregla: `The operation was aborted due to timeout` al crear un producto (Loyverse
tarda más de 12 s en responder y el Worker corta la espera, aunque a veces igual lo crea).

En el mismo editor busca `var LOYVERSE_TIMEOUT_MS = 12e3;` y cámbialo por:

```js
var LOYVERSE_TIMEOUT_MS = 25e3;
```

Deploy. (La app 1.14 ya comprueba sola si el producto quedó creado antes de dejar reintentar.)
