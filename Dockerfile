# ============================================================
#  Imagen del backend de AquaGo. Sirve igual para Render y para Railway:
#  las dos la construyen desde la raíz del repositorio (no hace falta Root Directory).
# ============================================================

# ---- Etapa 1: compilar el "fat jar" (el servidor con todas sus librerías en un solo .jar) ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY backend/ .
RUN chmod +x gradlew && ./gradlew buildFatJar --no-daemon --console=plain

# ---- Etapa 2: imagen ligera, solo para ejecutar ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*-all.jar app.jar
# Las fotos (/app/uploads/pagos, PAGOS_DIR), las cuentas y las solicitudes (/app/uploads, DATA_DIR) se guardan aquí.
# En Railway se monta un volumen en /app/uploads para que nada se borre al reiniciar.
RUN mkdir -p /app/uploads/pagos
# La plataforma asigna el puerto en PORT; 8081 es el de la computadora.
EXPOSE 8081
# El heap usa como máximo el 75 % de la memoria del contenedor (512 MB en los planes gratuitos).
CMD ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
