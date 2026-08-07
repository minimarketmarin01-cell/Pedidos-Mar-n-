// Service Worker de Marín 376 — dedicado ÚNICAMENTE a Web Push (notificaciones aunque la
// app esté cerrada). No cachea nada ni interfiere con la lógica de actualización de versión
// que ya tiene index.html (esa usa las Cache API por su cuenta, sin depender de este SW).
//
// Se instala/activa apenas se registra y queda escuchando en segundo plano — es el navegador
// quien lo despierta cuando llega un push, no necesita que la pestaña esté abierta.

self.addEventListener('install', () => {
  self.skipWaiting(); // activa esta versión de inmediato, sin esperar a que se cierren pestañas viejas
});

self.addEventListener('activate', (event) => {
  event.waitUntil(self.clients.claim()); // toma control de las pestañas ya abiertas sin recargar
});

// El servidor manda el mensaje cifrado (ver webPushCifrarPayload en el Worker) con esta forma:
// { title, body, url }. Si por algún motivo el payload no viene o no es JSON válido, se
// muestra un mensaje genérico en vez de fallar en silencio.
self.addEventListener('push', (event) => {
  let data = { title: 'Marín 376', body: 'Tienes una notificación nueva', url: '/' };
  try {
    if (event.data) data = Object.assign(data, event.data.json());
  } catch (e) { /* payload no era JSON — se usa el genérico de arriba */ }

  const options = {
    body: data.body,
    icon: 'icono-192.png',
    badge: 'icono-192.png',
    data: { url: data.url || '/' },
    vibrate: [120, 60, 120], // patrón corto, no intrusivo — se siente como un aviso, no una alarma
    tag: 'marin376-riesgo-quiebre', // agrupa notificaciones seguidas del mismo tipo en vez de apilarlas
    renotify: true
  };
  event.waitUntil(self.registration.showNotification(data.title, options));
});

// Al tocar la notificación: si ya hay una pestaña de la app abierta, la enfoca; si no, abre una nueva.
self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const targetUrl = (event.notification.data && event.notification.data.url) || '/';
  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((clientList) => {
      for (const client of clientList) {
        if ('focus' in client) return client.focus();
      }
      if (self.clients.openWindow) return self.clients.openWindow(targetUrl);
    })
  );
});
