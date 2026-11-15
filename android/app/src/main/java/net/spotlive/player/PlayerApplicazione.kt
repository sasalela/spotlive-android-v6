package net.spotlive.player

import android.app.Application
import net.spotlive.player.cache.CacheNativa
import net.spotlive.player.piattaforma.ArchivioSicuro
import net.spotlive.player.ponte.PonteGestore

class PlayerApplicazione : Application() {
    lateinit var cacheNativa: CacheNativa
        private set
    lateinit var archivioSicuro: ArchivioSicuro
        private set
    lateinit var ponteGestore: PonteGestore
        private set
    lateinit var luminosita: net.spotlive.player.luminosita.ApplicatoreLuminosita
        private set

    private fun processoPrincipale(): Boolean {
        val nome = if (android.os.Build.VERSION.SDK_INT >= 28) {
            getProcessName()
        } else {
            val pid = android.os.Process.myPid()
            getSystemService(android.app.ActivityManager::class.java)?.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName
        }
        return nome == null || nome == packageName
    }

    override fun onCreate() {
        super.onCreate()
        cacheNativa = CacheNativa(this)
        archivioSicuro = ArchivioSicuro(this)
        ponteGestore = PonteGestore(this, cacheNativa, archivioSicuro)
        // 1.0.15: luminosità LED (Taurus) e controllo aggiornamenti periodico, entrambi nativi.
        luminosita = net.spotlive.player.luminosita.ApplicatoreLuminosita(this, archivioSicuro)
        // Solo nel processo principale (RiavvioActivity gira in ":riavvio"): mai due applicatori sullo stesso LED.
        if (processoPrincipale()) {
            luminosita.avvia()
            net.spotlive.player.aggiornamenti.PianificatoreAggiornamenti.avvia(this)
        }
        // Con "Mostra sopra altre app" la finestrella rende l'app visibile fin dall'avvio del processo.
        runCatching { net.spotlive.player.sistema.Finestrella.aggiorna(this) }
    }
}
