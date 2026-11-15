package net.spotlive.player.sistema

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import net.spotlive.player.MainActivity

/**
 * Riavvio vero dell'app: gira in un processo separato (":riavvio"), chiude il processo
 * del player e lo riapre da zero, poi si chiude.
 */
class RiavvioActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pid = intent.getIntExtra(EXTRA_PID, -1)
        if (pid > 0) Process.killProcess(pid)
        window.decorView.postDelayed({
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            )
            finish()
            Process.killProcess(Process.myPid())
        }, 800)
    }

    companion object {
        private const val EXTRA_PID = "pid"

        fun riavvia(contesto: Context) {
            contesto.startActivity(
                Intent(contesto, RiavvioActivity::class.java)
                    .putExtra(EXTRA_PID, Process.myPid())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
