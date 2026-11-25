package com.logue.frontend

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Conecta los ganchos de [Plataforma] con Android: SharedPreferences, red, notificaciones locales,
 * WorkManager (subir cambios sin conexión) y la app de mapas.
 * Se llama desde MainActivity y también desde [SincronizacionWorker], porque el sistema puede
 * despertar el trabajo de fondo sin abrir la app.
 */
object PlataformaAndroid {
    private const val PREFERENCIAS = "aquago_offline"
    private const val CLAVE_SERVIDOR = "servidor_configurado"
    private const val CANAL = "aquago_avisos"
    private const val TRABAJO_PERIODICO = "aquago_sync_periodico"
    private const val TRABAJO_INMEDIATO = "aquago_sync_ahora"
    private var siguienteNotificacion = 1000

    /** [servidor]: el valor de SERVIDOR_URL (MainActivity). `null` = usar el último guardado (trabajo de fondo). */
    fun iniciar(context: Context, servidor: String?) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFERENCIAS, Context.MODE_PRIVATE)
        if (servidor != null) prefs.edit().putString(CLAVE_SERVIDOR, servidor).apply()
        configurarServidor(prefs.getString(CLAVE_SERVIDOR, "").orEmpty())

        Plataforma.esAppMovil = true
        Plataforma.almacen = object : AlmacenLocal {
            override fun leer(clave: String): String? = prefs.getString(clave, null)
            override fun guardar(clave: String, valor: String) { prefs.edit().putString(clave, valor).apply() }
            override fun borrar(clave: String) { prefs.edit().remove(clave).apply() }
        }
        Plataforma.hayConexion = { hayRed(app) }
        Plataforma.notificar = { titulo, texto -> notificar(app, titulo, texto) }
        Plataforma.programarSincronizacion = { sincronizarCuandoHayaRed(app) }
        Plataforma.abrirMapa = { direccion -> abrirMapa(app, direccion) }
        Plataforma.fechaLocal = { dias ->
            val c = Calendar.getInstance()
            c.add(Calendar.DAY_OF_YEAR, dias)
            String.format(Locale.US, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
        }
        crearCanal(app)
        programarPeriodico(app)
    }

    private fun hayRed(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun crearCanal(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(CANAL, "Avisos de AquaGo", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Estatus de tus pipas, entregas nuevas y cambios subidos"
            }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(canal)
        }
    }

    private fun notificar(context: Context, titulo: String, texto: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val abrir = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendiente = abrir?.let {
            android.app.PendingIntent.getActivity(
                context, 0, it,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
        }
        val n = NotificationCompat.Builder(context, CANAL)
            .setSmallIcon(context.applicationInfo.icon)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setAutoCancel(true)
            .apply { if (pendiente != null) setContentIntent(pendiente) }
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(siguienteNotificacion++, n) }
    }

    private val conRed = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Se ejecuta en cuanto haya red (aunque la app esté cerrada). */
    private fun sincronizarCuandoHayaRed(context: Context) {
        val trabajo = OneTimeWorkRequestBuilder<SincronizacionWorker>().setConstraints(conRed).build()
        WorkManager.getInstance(context).enqueueUniqueWork(TRABAJO_INMEDIATO, ExistingWorkPolicy.REPLACE, trabajo)
    }

    /** Cada 15 minutos (lo mínimo que permite Android) revisa cambios y sube pendientes. */
    private fun programarPeriodico(context: Context) {
        val trabajo = PeriodicWorkRequestBuilder<SincronizacionWorker>(15, TimeUnit.MINUTES).setConstraints(conRed).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(TRABAJO_PERIODICO, ExistingPeriodicWorkPolicy.KEEP, trabajo)
    }

    private fun abrirMapa(context: Context, direccion: String) {
        val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(direccion))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val web = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(direccion))
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(geo) }.onFailure { runCatching { context.startActivity(web) } }
    }
}

/** Trabajo de fondo: sube la cola de cambios y revisa la lista para mandar notificaciones. */
class SincronizacionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        PlataformaAndroid.iniciar(applicationContext, null)
        return runCatching { Offline.trabajoEnSegundoPlano() }.fold(
            onSuccess = { if (Offline.sesionGuardada()?.let { Offline.cola(it.username).isNotEmpty() } == true) Result.retry() else Result.success() },
            onFailure = { Result.retry() }
        )
    }
}
