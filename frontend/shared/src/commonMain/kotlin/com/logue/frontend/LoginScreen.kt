package com.logue.frontend

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onLoggedIn: (LoginResponse) -> Unit) {
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = AppColors.Accent,
        unfocusedBorderColor = AppColors.Border,
        focusedLabelColor = AppColors.Accent,
        unfocusedLabelColor = AppColors.TextMuted,
        cursorColor = AppColors.Accent
    )

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(92.dp).clip(RoundedCornerShape(26.dp)).background(AppColors.Accent),
            contentAlignment = Alignment.Center
        ) {
            Text("AG", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
        Text("AquaGo", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("Solicita tu agua desde casa", color = AppColors.TextMuted, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(28.dp))

        AppCard {
            Text("Inicia sesión", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = user,
                onValueChange = { user = it },
                label = { Text("Usuario") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = pass,
                onValueChange = { pass = it },
                label = { Text("Contraseña") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors,
                visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = { showPass = !showPass }) {
                        Text(if (showPass) "Ocultar" else "Mostrar", color = AppColors.PrimaryLight)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    scope.launch {
                        loading = true
                        error = null
                        val res = try { login(user, pass) } catch (e: Exception) { null }
                        loading = false
                        if (res == null) error = "Credenciales incorrectas o sin conexión"
                        else onLoggedIn(res)
                    }
                },
                enabled = !loading,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.Primary,
                    contentColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Text(if (loading) "Entrando..." else "Iniciar sesión", fontWeight = FontWeight.Bold)
            }
            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = AppColors.Red, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}