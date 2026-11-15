package net.spotlive.player

/**
 * 1.0.12: striscia di diagnosi sul LED anche dopo l'abbinamento (mai token né segreti).
 * Raccoglie ultimi errori JS/console, stato del collegamento in tempo reale, manifest e spot in corso.
 */
object DiagnosiLed {
    const val MAX_ERRORI = 5
    const val DURATA_MIN_MS = 10 * 60_000L

    private val errori = ArrayDeque<String>()

    @Synchronized
    fun aggiungiErrore(testo: String) {
        val t = oscura(testo).replace('\n', ' ').take(140)
        if (errori.lastOrNull() == t) return
        errori.addLast(t)
        while (errori.size > MAX_ERRORI) errori.removeFirst()
    }

    @Synchronized
    fun errori(): List<String> = errori.toList()

    /** Toglie qualsiasi cosa somigli a un token (Bearer, JWT, stringhe lunghe base64/hex, parametri token=). */
    fun oscura(s: String): String = s
        .replace(Regex("(?i)bearer\\s+[^\\s\"',;]+"), "Bearer ***")
        .replace(Regex("(?i)(token|authorization|password|segreto|chiave)([\"']?\\s*[:=]\\s*[\"']?)[^\\s\"'&,;}]+"), "$1$2***")
        .replace(Regex("eyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}(\\.[A-Za-z0-9_-]+)?"), "***")
        .replace(Regex("[A-Za-z0-9_+/=-]{32,}"), "***")

    /** "Chrome/xx" dallo user agent. */
    fun chromeDaUa(ua: String?): String =
        ua?.let { Regex("Chrome/([0-9.]+)").find(it)?.groupValues?.get(1) } ?: "?"

    /**
     * Script iniettato a inizio documento: window.onerror, unhandledrejection, stato WebSocket, esito
     * manifest/passaggi (solo URL e codice HTTP, mai intestazioni né corpo).
     */
    const val SCRIPT_INIZIO = """
(function(){
  if (window.__slDiag) return;
  var d = window.__slDiag = { err: [], ws: 'mai aperto', wsN: 0, man: '', pas: '', pasOk: 0, t0: Date.now() };
  function pulisci(s){ s = String(s||''); return s.replace(/bearer\s+[^\s"',;]+/ig,'Bearer ***').replace(/[A-Za-z0-9_+\/=-]{32,}/g,'***').slice(0,140); }
  function err(s){ s = pulisci(s); if (d.err[d.err.length-1] === s) return; d.err.push(s); if (d.err.length > 5) d.err.shift(); }
  window.addEventListener('error', function(e){
    if (e && e.target && e.target !== window && e.target.tagName) { err('risorsa ' + e.target.tagName.toLowerCase() + ' non caricata'); return; }
    err('JS ' + (e && e.message || 'errore') + ' @' + String(e && e.filename || '').split('/').pop().split('?')[0] + ':' + (e && e.lineno || 0));
  }, true);
  window.addEventListener('unhandledrejection', function(e){
    var r = e && e.reason; err('promise ' + (r && (r.name ? r.name + ': ' : '') + (r && r.message || r) ));
  });
  try {
    var WS = window.WebSocket;
    if (WS) {
      var N = function(u, p){
        var s = p !== undefined ? new WS(u, p) : new WS(u);
        d.wsN++; d.ws = 'apertura…';
        s.addEventListener('open', function(){ d.ws = 'aperta ' + new Date().toTimeString().slice(0,8); });
        s.addEventListener('close', function(ev){ d.ws = 'chiusa ' + ev.code + (ev.reason ? ' ' + pulisci(ev.reason).slice(0,40) : '') + ' ' + new Date().toTimeString().slice(0,8); });
        s.addEventListener('error', function(){ err('WebSocket errore (tentativo ' + d.wsN + ')'); });
        return s;
      };
      N.prototype = WS.prototype; N.CONNECTING = 0; N.OPEN = 1; N.CLOSING = 2; N.CLOSED = 3;
      window.WebSocket = N;
    }
  } catch (x) {}
  try {
    var F = window.fetch;
    if (F) window.fetch = function(i, o){
      var u = typeof i === 'string' ? i : (i && i.url) || '';
      var p = F.apply(this, arguments);
      var ora = function(){ return new Date().toTimeString().slice(0,8); };
      if (/\/player\/manifest/.test(u)) p.then(function(r){ d.man = r.status + ' ' + ora(); }, function(e){ d.man = 'errore rete ' + ora(); err('manifest: ' + (e && e.message || e)); });
      else if (/\/player\/passaggi/.test(u)) p.then(function(r){ d.pas = r.status + ' ' + ora(); if (r.ok) d.pasOk++; }, function(){ d.pas = 'errore rete ' + ora(); });
      return p;
    };
  } catch (x) {}
})();
"""

    /** Lettura periodica dello stato della pagina (nessun token: solo tipi, codici e misure). */
    const val SCRIPT_STATO = """
(function(){try{
  var d = window.__slDiag || null;
  var v = document.querySelector('video'), im = document.querySelector('#root img');
  var spot = 'nessuno';
  if (v) { spot = 'video ' + (v.videoWidth|0) + 'x' + (v.videoHeight|0) + ' rs' + v.readyState + (v.paused ? ' pausa' : ' play') + ' t' + (v.currentTime||0).toFixed(1) + (v.error ? ' ERR' + v.error.code : '') + (v.muted ? ' muto' : ''); }
  else if (im) { spot = 'img ' + (im.naturalWidth|0) + 'x' + (im.naturalHeight|0) + (im.complete ? '' : ' carica…'); }
  var cache = !!localStorage.getItem('spotlive-manifest-cache');
  var sw = !!(navigator.serviceWorker && navigator.serviceWorker.controller);
  var vis = document.visibilityState;
  return JSON.stringify({ d: d ? { err: d.err, ws: d.ws, wsN: d.wsN, man: d.man, pas: d.pas, pasOk: d.pasOk } : null, spot: spot, cache: cache, sw: sw, vis: vis, nv: document.querySelectorAll('video').length });
}catch(x){return JSON.stringify({x:String(x).slice(0,80)})}})()
"""
}
