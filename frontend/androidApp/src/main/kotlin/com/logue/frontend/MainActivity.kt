package com.logue.frontend

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.fragment.app.FragmentActivity

// FragmentActivity (que ya es una ComponentActivity) porque BiometricPrompt la necesita
class MainActivity : FragmentActivity() {

    // Red local: solo hace falta para el backend local. Si el usuario lo niega, el login dirá que no se pudo conectar
    // También pide el de notificaciones locales de AquaGo (estatus de la pipa, entregas nuevas).
    // Se piden juntos porque Android solo muestra una solicitud de permisos a la vez. Si los niega, la app funciona igual.
    private val lanzadorPermisos = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Diseño claro: íconos oscuros en las barras del sistema
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        // Servidor de local.properties (SERVIDOR_URL). Vacío = la nube; "local" = 10.0.2.2:8081 (emulador)
        // PlataformaAndroid también guarda la sesión, la copia sin conexión y programa la sincronización
        PlataformaAndroid.iniciar(this, BuildConfig.SERVIDOR_URL)
        pedirPermisos()

        setContent {
            App()
        }
    }

    /**
     * Desde Android 17 (API 37), una app con targetSdk 37 necesita el permiso
     * ACCESS_LOCAL_NETWORK para llegar a 10.0.2.2. Sin él, la conexión se bloquea
     * aunque el backend esté encendido. Desde Android 13 (API 33) las notificaciones
     * también necesitan permiso.
     */
    private fun pedirPermisos() {
        val faltan = buildList {
            if (Build.VERSION.SDK_INT >= 37) add(PERMISO_RED_LOCAL)
            if (Build.VERSION.SDK_INT >= 33) add(android.Manifest.permission.POST_NOTIFICATIONS)
        }.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (faltan.isNotEmpty()) lanzadorPermisos.launch(faltan.toTypedArray())
    }

    private companion object {
        const val PERMISO_RED_LOCAL = "android.permission.ACCESS_LOCAL_NETWORK"
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
