import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)
    // MainActivity extiende FragmentActivity (lo exige BiometricPrompt)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.biometric)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

// ---- VARIABLE DE ENTORNO SERVIDOR_URL (API-07) ----
// Se lee de frontend/local.properties, que NO se sube a GitHub (cada quien tiene su propio valor).
// Si no está ahí, también se acepta como variable de entorno del sistema. Vacía = servidor en la nube.
// Valores: "local" (backend en la PC, visto desde el emulador) o una URL completa. Ver local.properties.ejemplo.
val propiedadesLocales = Properties().apply {
    val archivo = rootProject.file("local.properties")
    if (archivo.exists()) archivo.inputStream().use { load(it) }
}

fun variable(clave: String): String =
    propiedadesLocales.getProperty(clave) ?: providers.environmentVariable(clave).orNull ?: ""

android {
    namespace = "com.logue.frontend"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.logue.frontend"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"

        // Llega al código como BuildConfig.SERVIDOR_URL; MainActivity se la pasa a configurarServidor()
        buildConfigField("String", "SERVIDOR_URL", "\"${variable("SERVIDOR_URL")}\"")
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true // genera la clase BuildConfig
    }
}