// Puente entre la página de AquaGo (Kotlin/Compose) y el navegador.
// - Guarda la sesión en localStorage (solo en esta computadora).
// - Botón "Iniciar sesión con Google" (Google Identity Services) y casilla reCAPTCHA v2.
//   Google necesita sus propios elementos HTML: se dibujan ENCIMA del lienzo de Compose, en el hueco
//   que reserva la pantalla de login (ZonaWeb.kt).
// No contiene claves: el ID de cliente y la clave del sitio (ambos públicos) llegan de GET /config/web.
(function () {
  "use strict";

  function cargarScript(src) {
    return new Promise(function (ok, falla) {
      var ya = document.querySelector('script[src="' + src + '"]');
      if (ya) { if (ya.dataset.listo) ok(); else ya.addEventListener("load", function () { ok(); }); return; }
      var s = document.createElement("script");
      s.src = src; s.async = true; s.defer = true;
      s.onload = function () { s.dataset.listo = "1"; ok(); };
      s.onerror = falla;
      document.head.appendChild(s);
    });
  }

  function capa(id) {
    var el = document.getElementById("aquago-" + id);
    if (!el) {
      el = document.createElement("div");
      el.id = "aquago-" + id;
      el.style.position = "fixed";
      el.style.zIndex = "10";
      el.style.display = "none";
      el.style.justifyContent = "center";
      el.style.alignItems = "center";
      document.body.appendChild(el);
    }
    return el;
  }

  var widgetCaptcha = null;

  window.aquago = {
    leer: function (k) { try { return window.localStorage.getItem("aquago:" + k); } catch (e) { return null; } },
    guardar: function (k, v) { try { window.localStorage.setItem("aquago:" + k, v); } catch (e) {} },
    borrar: function (k) { try { window.localStorage.removeItem("aquago:" + k); } catch (e) {} },

    fecha: function (dias) {
      var d = new Date();
      d.setDate(d.getDate() + dias);
      var m = String(d.getMonth() + 1).padStart(2, "0");
      var dia = String(d.getDate()).padStart(2, "0");
      return d.getFullYear() + "-" + m + "-" + dia;
    },

    // "" = servidor en la nube. ?servidor=local o abrir la página desde localhost = backend local.
    servidor: function () {
      var p = new URLSearchParams(window.location.search).get("servidor");
      if (p) return p;
      var h = window.location.hostname;
      return (h === "localhost" || h === "127.0.0.1") ? "local" : "";
    },

    enLinea: function () { return navigator.onLine !== false; },

    google: function (clientId, alEntrar) {
      cargarScript("https://accounts.google.com/gsi/client").then(function () {
        google.accounts.id.initialize({
          client_id: clientId,
          callback: function (r) { if (r && r.credential) alEntrar(r.credential); }
        });
        var el = capa("google");
        el.innerHTML = "";
        google.accounts.id.renderButton(el, { theme: "outline", size: "large", text: "signin_with", shape: "pill", locale: "es", width: 300 });
      }).catch(function () { console.warn("No se pudo cargar el botón de Google"); });
    },

    captcha: function (clave, alResponder) {
      window.aquagoCaptchaListo = function () {
        var el = capa("captcha");
        el.innerHTML = "";
        widgetCaptcha = grecaptcha.render(el, {
          sitekey: clave,
          hl: "es",
          callback: function (t) { alResponder(t); },
          "expired-callback": function () { alResponder(""); },
          "error-callback": function () { alResponder(""); }
        });
      };
      if (window.grecaptcha && window.grecaptcha.render) window.aquagoCaptchaListo();
      else cargarScript("https://www.google.com/recaptcha/api.js?onload=aquagoCaptchaListo&render=explicit&hl=es")
        .catch(function () { console.warn("No se pudo cargar reCAPTCHA"); });
    },

    reiniciarCaptcha: function () {
      if (widgetCaptcha !== null && window.grecaptcha) { try { grecaptcha.reset(widgetCaptcha); } catch (e) {} }
    },

    // Coordenadas en pixeles del lienzo -> pixeles CSS
    ubicar: function (id, x, y, ancho, alto, visible) {
      var el = capa(id);
      var r = window.devicePixelRatio || 1;
      el.style.left = (x / r) + "px";
      el.style.top = (y / r) + "px";
      el.style.width = (ancho / r) + "px";
      el.style.height = (alto / r) + "px";
      el.style.display = visible ? "flex" : "none";
    },

    descargar: function (nombre, contenido) {
      var blob = new Blob(["﻿" + contenido], { type: "text/csv;charset=utf-8" });
      var a = document.createElement("a");
      a.href = URL.createObjectURL(blob);
      a.download = nombre;
      document.body.appendChild(a);
      a.click();
      setTimeout(function () { URL.revokeObjectURL(a.href); a.remove(); }, 500);
    },

    // Reporte imprimible: se escribe en un marco oculto y se abre el diálogo de impresión (o «Guardar como PDF»)
    imprimir: function (html) {
      var marco = document.createElement("iframe");
      marco.style.position = "fixed"; marco.style.width = "0"; marco.style.height = "0"; marco.style.border = "0";
      document.body.appendChild(marco);
      var doc = marco.contentWindow.document;
      doc.open(); doc.write(html); doc.close();
      setTimeout(function () {
        marco.contentWindow.focus();
        marco.contentWindow.print();
        setTimeout(function () { marco.remove(); }, 60000);
      }, 300);
    },

    // «27/11/2026 21:40» con la hora de la computadora
    fechaHora: function () {
      var d = new Date();
      function dos(n) { return (n < 10 ? "0" : "") + n; }
      return dos(d.getDate()) + "/" + dos(d.getMonth() + 1) + "/" + d.getFullYear() + " " + dos(d.getHours()) + ":" + dos(d.getMinutes());
    }
  };
})();
