# SpotLive Player come "proprietario del dispositivo" (device owner) — PREPARATO, NON ATTIVO

**1.0.6**: firma di rilascio (CN=SpotLive, O=Live Italia S.r.l., C=IT, SHA-256 0814aaef…3a7288), activity di provisioning QR (GET_PROVISIONING_MODE, ADMIN_POLICY_COMPLIANCE), riavvio remoto corretto (GestoreDispositivo.riavvia), comando «Rimuovi» il controllo completo in Configurazione player (PIN di servizio, clearDeviceOwnerApp).

Dalla 1.0.3 l'app contiene `sistema/AmministratoreDispositivo.kt` (DeviceAdminReceiver) e
`GestoreDispositivo`. Tutto il codice è inattivo finché il dispositivo non viene configurato
con SpotLive come proprietario. Sul tablet di prova attuale NON è attivo.

## Cosa si ottiene da proprietario
- player come app Home fissa (`addPersistentPreferredActivity`), niente schermata di blocco;
- blocco sul player senza conferme (`setLockTaskPackages` + `startLockTask`, nessun tasto di sistema);
- barra di stato disattivata, avvio in modalità provvisoria vietato;
- aggiornamenti SpotLive installati in silenzio (PackageInstaller senza finestra «Aggiorna», niente Play Protect da sbloccare a mano);
- riavvio del dispositivo da remoto (`DevicePolicyManager.reboot`, comando telecontrollo `riavvia_dispositivo` già previsto);
- schermo sempre acceso in carica (`STAY_ON_WHILE_PLUGGED_IN`), aggiornamenti di sistema solo 03:00-05:00;
- orari: spegnimento schermo con `lockNow()`; la riaccensione la fa il player (wake lock / turnScreenOn).
  Lo spegnimento vero del dispositivo a orario NON è possibile con le API pubbliche: si usa lo schermo nero/spento.

## Requisiti
Il device owner si imposta SOLO su un dispositivo nuovo o azzerato (ripristino dati di fabbrica),
prima di aggiungere account Google. Lo si toglie solo con un nuovo azzeramento.

## Provisioning con QR (consigliato per i player spediti)
1. Dispositivo nuovo/azzerato, schermata di benvenuto: toccare 6 volte lo stesso punto → lettore QR.
2. Inquadrare un QR che contiene questo JSON (URL di download definitivo da decidere, HTTPS):
```json
{
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME": "net.spotlive.player/net.spotlive.player.sistema.AmministratoreDispositivo",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION": "https://<dominio>/scarica-…/spotlive-player.apk",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM": "CBSq77FkNtqidUqt5xdayA5VbyZFh-z5uYzY3OA6cog",
  "android.app.extra.PROVISIONING_SKIP_ENCRYPTION": true,
  "android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED": true,
  "android.app.extra.PROVISIONING_WIFI_SSID": "<rete del negozio, facoltativo>"
}
```
   Il checksum è SHA-256 del certificato di firma di rilascio (0814aaef…3a7288, dalla 1.0.6) in base64url senza `=`. Se la
   firma cambia (consigliato: chiave di rilascio dedicata prima dei clienti) va ricalcolato.
3. Android scarica l'APK, lo installa e lo rende proprietario: alla fine parte il player.

## Alternativa con cavo (laboratorio)
Dispositivo azzerato, nessun account, Debug USB attivo:
`adb install spotlive-player.apk && adb shell dpm set-device-owner net.spotlive.player/.sistema.AmministratoreDispositivo`

## Note
- Dalla 1.0.6 l'APK è firmato con la chiave di rilascio (keystore fuori dal repository, password solo da
  variabile d'ambiente: SPOTLIVE_SIGNING_KEYSTORE, SPOTLIVE_SIGNING_PASSWORD). Le 1.0.5 e precedenti
  erano firmate con la chiave debug: il passaggio richiede disinstallazione e nuovo abbinamento.
- Per togliere il proprietario: Configurazione player › «Controllo completo attivo» › Rimuovi (PIN di servizio), oppure azzeramento.

## Orari di accensione e spegnimento (dalla 1.0.3)

Gli orari arrivano dal pannello (tabella `orario_accensione`, eccezioni «chiuso» e «orari diversi»)
dentro il manifest. L'app li legge dalla cache del player web ogni 30 s, li salva e programma una
sveglia esatta al prossimo cambio (`USE_EXACT_ALARM`, consentito alle app installate fuori dal
Play Store). Se gli orari non sono mai arrivati, il player resta sempre acceso.

| Tipo schermo (Configurazione player) | Fuori orario senza device owner | Con device owner | All'orario di accensione |
|---|---|---|---|
| Tablet | velo nero, luminosità minima, player in pausa, lo schermo si spegne col timeout di Android | schermo spento subito (`lockNow`) | sveglia + wake lock: lo schermo si riaccende e il player riparte |
| Schermo LED (box HDMI) | velo nero, player in pausa, uscita video **sempre attiva** (niente «nessun segnale») | uguale | il velo sparisce, il player riparte |
| Monitor / TV (box HDMI) | velo nero, player in pausa, il box va in standby col timeout di Android | standby subito (`lockNow`) | il box si risveglia |

HDMI-CEC: le API CEC di Android (`HdmiControlManager`) sono riservate alle app di sistema; né
un'app normale né un device owner possono inviare «standby»/«accendi». Funziona però il CEC
gestito dal sistema del box: con «HDMI-CEC» / «Spegnimento automatico TV» / «Accensione con un
tocco» attivi nelle impostazioni del box, quando il box va in standby manda lo standby al
monitor/TV e quando si risveglia lo riaccende. Va provato sull'hardware vero (dipende da box e TV).

Spegnimento reale e riaccensione hardware a orario (alimentazione): non possibili da app, nemmeno
con device owner. Servono funzioni del produttore (RTC/«accensione programmata» di alcuni box
signage) oppure una presa temporizzata esterna. `DevicePolicyManager.reboot()` (device owner)
permette solo il riavvio.
