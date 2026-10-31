# Graph Report - tmp.NQxlcG0PkS  (2026-10-31)

## Corpus Check
- 3 files · ~154,253 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 816 nodes · 2247 edges · 45 communities (38 shown, 7 thin omitted)
- Extraction: 99% EXTRACTED · 1% INFERRED · 0% AMBIGUOUS · INFERRED: 26 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- index.html.js
- showToast
- fiados.html.js
- peso
- cloudSync
- renderInicio2
- fmt1
- esCategoriaCambio
- fpPaneProveedores
- abrirModal
- render
- llLoad
- rowHTML
- renderTable
- analyze
- vdRender
- scRenderList
- crearComboBuscable
- onQty
- catComboIds
- _gpGuardarMultiplo
- _gpFiltrarProveedores
- mcCargarCategorias
- mmcRenderChart
- marcarPedidoRealizado
- gpRenderProductos
- stockDisplayHTML
- parseSales
- ajustar
- sbAbrir
- smRender
- vcBuscarCatalogo
- tag
- aplicarAjusteColorProducto
- _cartMaxNeededWidth
- chequearVersion
- inDescartar
- fpMovimientosCombinados
- currentW
- detenerHold
- mmFindItems
- _provMenuMount
- rzEstadoPush
- viEsc

## God Nodes (most connected - your core abstractions)
1. `showToast()` - 88 edges
2. `peso()` - 40 edges
3. `render()` - 36 edges
4. `normTxt()` - 35 edges
5. `sonidoGuardado()` - 31 edges
6. `abrirModal()` - 31 edges
7. `persist()` - 30 edges
8. `dlgConfirm()` - 27 edges
9. `analyze()` - 26 edges
10. `fmt1()` - 25 edges

## Surprising Connections (you probably didn't know these)
- `PcWidget` --indirect_call--> `render()`  [INFERRED]
  index.html.js → index.html.js  _Bridges community 10 → community 13_
- `fpPaneProveedores()` --indirect_call--> `fpGuardarBorrador()`  [INFERRED]
  index.html.js → index.html.js  _Bridges community 6 → community 8_
- `abrirConfirmarPedido()` --calls--> `peso()`  [EXTRACTED]
  index.html.js → index.html.js  _Bridges community 3 → community 5_
- `cartSubHtml()` --calls--> `peso()`  [EXTRACTED]
  index.html.js → index.html.js  _Bridges community 3 → community 12_
- `cgRenderLista()` --calls--> `peso()`  [EXTRACTED]
  index.html.js → index.html.js  _Bridges community 3 → community 4_

## Import Cycles
- None detected.

## Communities (45 total, 7 thin omitted)

### Community 0 - "index.html.js"
Cohesion: 0.02
Nodes (90): ABC_BY_SKU, ABREV_PACK, ACCION_LABELS, AJUSTAR_FOTO_DEFAULT, Barcode39, BARCODE_LOOKUP_PROVIDERS, BASICOS_REPOSICION, CAL_PROV (+82 more)

### Community 1 - "showToast"
Cohesion: 0.05
Nodes (88): aplicarPrecioABC(), Calc, cargarVencidosPendientes(), cerrarModal(), closeMerma(), closeVenc(), cpDescartar(), daActualizarBadge() (+80 more)

### Community 2 - "fiados.html.js"
Cohesion: 0.08
Nodes (61): FD_TIPOS_DESHACIBLES, fdAbrirDetalle(), fdAbrirHistorial(), fdAbrirReconciliar(), fdActualizarBannerFusion(), fdActualizarNuevoSaldo(), fdAplicarEstado(), fdAsignarCliente() (+53 more)

### Community 3 - "peso"
Cohesion: 0.06
Nodes (52): _abcListaFiltrada(), _abcRecalcularPreview(), abrirModalPrecioABC(), edRenderResultados(), esProvDescuento(), etAbrir(), etBarcodeSvg(), etCargarTamano() (+44 more)

### Community 4 - "cloudSync"
Cohesion: 0.07
Nodes (48): actualizarIndicadorEnVivo(), aplicarListasCompartidas(), aplicarPanelDeFondo(), aplicarRepintadoDiferido(), autoSyncIfStale(), buildDBfromCloud(), _cacheDelDiaVigente(), cargarABC() (+40 more)

### Community 5 - "renderInicio2"
Cohesion: 0.09
Nodes (40): abrirConfirmarPedido(), actualizarBadgeBn(), calcularProveedoresHoy(), calcularProveedoresHoyReal(), calcularRecepcionarHoy(), calProvAgregarDia(), calProvAgregarProveedor(), calProvEfectivo() (+32 more)

### Community 6 - "fmt1"
Cohesion: 0.08
Nodes (35): fmt1(), fpAbrir(), fpBadgeLote(), fpBadgeStock(), fpCampo(), fpCancelarImagenPreview(), fpConfirmarImagen(), fpCopiarAOtroLocal() (+27 more)

### Community 7 - "esCategoriaCambio"
Cohesion: 0.10
Nodes (35): closeRecibir(), esCategoriaCambio(), openRecibir(), _pesoDecimales(), rcActualizarBotonInventario(), rcActualizarCostoUnitarioHint(), rcAplicarBorrador(), rcAplicarVisibilidad() (+27 more)

### Community 8 - "fpPaneProveedores"
Cohesion: 0.12
Nodes (33): cpAbrir(), cpAutocompletarPorCodigo(), cpCantidadFinalUnidades(), cpCargarCatalogo(), cpLlenarSelect(), cpMontarPrecio(), cpPreseleccionarCategoriaPorNombre(), cpQtyMas() (+25 more)

### Community 9 - "abrirModal"
Cohesion: 0.11
Nodes (26): abrirModal(), abrirModalRenombrar(), abrirVencimientosDias(), _actualizarVisibilidadFab(), calProvAbrir(), cerrarOverlays(), guardarUltimaPantalla(), in2PintarAtencion() (+18 more)

### Community 10 - "render"
Cohesion: 0.18
Nodes (20): aplicarSugeridos(), buildChips(), openMenu(), opt(), renderProvMenu(), cgRestaurar(), closeScanner(), commitProv() (+12 more)

### Community 11 - "llLoad"
Cohesion: 0.12
Nodes (20): fmtDateInputToDDMMAAAA(), llCargarSinClasificar(), llCheckBadge(), llFaltaTxt(), llGuardarFecha(), llHtmlSinClasificar(), llIgnorar(), llLoad() (+12 more)

### Community 12 - "rowHTML"
Cohesion: 0.18
Nodes (20): cartApplyQty(), cartSubHtml(), cartUpdateTotals(), colorForProv(), confirmarPedidoConCanal(), covColor(), currentCambios(), currentOrderVisible() (+12 more)

### Community 13 - "renderTable"
Cohesion: 0.11
Nodes (18): _actualizarAlturaTop(), _actualizarLayout(), _aplicarLayoutCarrito(), _aplicarModo(), _aplicarVistaFilas(), favStarHTML(), _guardarModo(), pcSugerirRedondeos() (+10 more)

### Community 14 - "analyze"
Cohesion: 0.18
Nodes (18): analyze(), ccuMixedPacks(), ccuTierIdx(), currentOrder(), formulaBasico(), itemsForCategoria(), motivoVencDe(), packsOf() (+10 more)

### Community 15 - "vdRender"
Cohesion: 0.17
Nodes (15): fmt, vdAbrir(), vdActualizarNavSemana(), vdCargar(), vdFechasDisponibles(), vdFiltrados(), vdMaxOffsetSemana(), vdPoblarSelects() (+7 more)

### Community 16 - "scRenderList"
Cohesion: 0.24
Nodes (15): scAplicarFiltroInteligente(), scDraftClear(), scDraftGet(), scDraftSaveVal(), scDraftSet(), scDraftSkip(), scGuardar(), scLoad() (+7 more)

### Community 17 - "crearComboBuscable"
Cohesion: 0.29
Nodes (10): crearComboBuscable(), close(), destroy(), items(), mount(), open(), position(), render() (+2 more)

### Community 18 - "onQty"
Cohesion: 0.27
Nodes (10): agregarProveedorExtraSiFalta(), avisoYaPedidoEnOtroProveedor(), cargarPedidoConfirmadoEnCarrito(), esDelProveedor(), itemsFor(), itemsPedidoConfirmadoActual(), onQty(), pendienteDe() (+2 more)

### Community 19 - "catComboIds"
Cohesion: 0.44
Nodes (10): catComboChoose(), catComboClose(), catComboIds(), catComboItems(), catComboMount(), catComboOpen(), catComboPosition(), catComboRender() (+2 more)

### Community 20 - "_gpGuardarMultiplo"
Cohesion: 0.27
Nodes (10): fpPaneMinimo(), _gpCfgAjustar(), _gpCfgCambiarUnidad(), _gpCfgFmtActual(), _gpCfgOnChange(), _gpCfgOnClick(), _gpCfgProgramarGuardado(), _gpFilaConfig() (+2 more)

### Community 21 - "_gpFiltrarProveedores"
Cohesion: 0.22
Nodes (10): gpAbrirReasignar(), _gpCambiarModo(), gpCargar(), gpConfirmarEliminacion(), gpEliminar(), _gpFiltrarProveedores(), _gpFiltrarSectores(), gpRender() (+2 more)

### Community 22 - "mcCargarCategorias"
Cohesion: 0.31
Nodes (9): mcActualizarContadorSeleccion(), mcCargarCategorias(), mcEliminarProducto(), mcPintarDesglose(), mcRefrescarChipActivo(), mcRefrescarDesgloseGeneral(), mcRenderChips(), mcRenderLista() (+1 more)

### Community 23 - "mmcRenderChart"
Cohesion: 0.28
Nodes (9): mmcAbrir(), mmcCargar(), mmcFiltrados(), mmcPoblarSelects(), mmcPorCategoria(), mmcPorDia(), mmcRender(), mmcRenderChart() (+1 more)

### Community 24 - "marcarPedidoRealizado"
Cohesion: 0.32
Nodes (8): cancelarPedidoConfirmado(), _colaPedGuardar(), _colaPedLeer(), confirmarAccion(), _errorPedidoReintentable(), _limpiarPedidoConfirmadoCargado(), marcarPedidoRealizado(), vaciarColaPedidos()

### Community 25 - "gpRenderProductos"
Cohesion: 0.29
Nodes (8): gpAbrir(), gpAbrirProductos(), gpAbrirProductosPorSector(), gpEliminarProducto(), _gpFiltrarProductos(), _gpMostrarNivel(), gpRenderProductos(), gpStockTxt()

### Community 26 - "stockDisplayHTML"
Cohesion: 0.33
Nodes (6): abrevPalabraPack(), packsConResto(), stkCorregirAbrir(), stkToque(), stockDisplayHTML(), toggleStockPacks()

### Community 27 - "parseSales"
Cohesion: 0.47
Nodes (6): findCol(), num(), parseCSV(), parseDate(), parseInventory(), parseSales()

### Community 28 - "ajustar"
Cohesion: 0.40
Nodes (5): ajustar(), edOvAbierto(), programarAjuste(), rcOvAbierto(), restaurar()

### Community 29 - "sbAbrir"
Cohesion: 0.40
Nodes (5): sbAbrir(), _sbFmt(), _sbMB(), sbRevisar(), sbUso()

### Community 30 - "smRender"
Cohesion: 0.60
Nodes (5): smActualizarBoton(), smCalcular(), smEliminar(), smFiltrados(), smRender()

### Community 31 - "vcBuscarCatalogo"
Cohesion: 0.40
Nodes (5): vcBuscarCatalogo(), vcNuevoLoteDe(), vcPick(), vcShowForm(), vcValidar()

### Community 32 - "tag"
Cohesion: 0.50
Nodes (3): aggregate(), sopMin(), tag()

### Community 33 - "aplicarAjusteColorProducto"
Cohesion: 0.67
Nodes (3): aplicarAjusteColorProducto(), aplicarNitidezSuave(), _cpAjustesFotoUI

### Community 34 - "_cartMaxNeededWidth"
Cohesion: 0.67
Nodes (3): _cartMaxNeededWidth(), _cartWLimits(), _restoreCartW()

### Community 35 - "chequearVersion"
Cohesion: 0.67
Nodes (3): chequearVersion(), hayAlgoAbierto(), limpiarCacheYActualizarSW()

### Community 36 - "inDescartar"
Cohesion: 0.67
Nodes (3): closeInsumos(), inDescartar(), inLimpiarBorrador()

### Community 37 - "fpMovimientosCombinados"
Cohesion: 0.67
Nodes (3): _fpIsoADDMMAAAA(), fpMovimientosCombinados(), _fpParseFechaHora()

## Knowledge Gaps
- **7 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `crearComboBuscable()` connect `crearComboBuscable` to `index.html.js`, `showToast`, `fpPaneProveedores`?**
  _High betweenness centrality (0.018) - this node is a cross-community bridge._
- **Should `index.html.js` be split into smaller, more focused modules?**
  _Cohesion score 0.017391304347826087 - nodes in this community are weakly interconnected._
- **Why does `wireAjustesFotoProducto()` connect `fmt1` to `index.html.js`?**
  _High betweenness centrality (0.007) - this node is a cross-community bridge._
- **Should `showToast` be split into smaller, more focused modules?**
  _Cohesion score 0.0522466039707419 - nodes in this community are weakly interconnected._
- **Why does `buildChips()` connect `render` to `index.html.js`?**
  _High betweenness centrality (0.007) - this node is a cross-community bridge._
- **Should `fiados.html.js` be split into smaller, more focused modules?**
  _Cohesion score 0.08234126984126984 - nodes in this community are weakly interconnected._
- **Should `peso` be split into smaller, more focused modules?**
  _Cohesion score 0.05580693815987934 - nodes in this community are weakly interconnected._