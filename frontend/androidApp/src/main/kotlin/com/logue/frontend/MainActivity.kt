package com.logue.frontend

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

class MainActivity : ComponentActivity() {

    // Si el usuario niega el permiso, el login dirá que no se pudo conectar con el servidor
    private val pedirRedLocal = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pedirPermisoRedLocal()

        setContent {
            App()
        }
    }

    /**
     * Desde Android 17 (API 37), una app con targetSdk 37 necesita el permiso
     * ACCESS_LOCAL_NETWORK para llegar a 10.0.2.2. Sin él, la conexión se bloquea
     * aunque el backend esté encendido.
     */
    private fun pedirPermisoRedLocal() {
        if (Build.VERSION.SDK_INT >= 37 &&
            checkSelfPermission(PERMISO_RED_LOCAL) != PackageManager.PERMISSION_GRANTED
        ) {
            pedirRedLocal.launch(PERMISO_RED_LOCAL)
        }
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
