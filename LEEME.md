# Alerta SICOES

App Android que revisa el SICOES (búsqueda de **Convocatorias**) cada cierto tiempo
y te avisa con una notificación cuando una entidad que vigilas publica una
contratación **vigente**.

## Qué hace

- Vigilas una o varias entidades (p. ej. "Gobierno Autonomo Municipal de La Paz").
  - El botón **"Buscar el nombre exacto en el SICOES"** te muestra los nombres tal como
    están registrados, para elegir el correcto.
  - Opción **"Solo ese nombre exacto"**: evita que entren otras entidades cuyo nombre
    contenga el mismo texto.
- Eliges los tipos: **Obras** (por defecto), Bienes, Servicios Generales y
  Servicios de Consultoría.
- Solo trae convocatorias en estado **Vigente**.
- Revisa automáticamente cada 15 min, 30 min (por defecto), 1 h, 2 h o 4 h.
  Android no permite menos de 15 min.
- Cada convocatoria muestra: tipo, modalidad, entidad, objeto, **fecha de
  publicación**, **fecha de presentación** (con los días que faltan), **cuándo la
  detectó la app**, CUCE y estado. Las nuevas llevan la etiqueta **NUEVA** por 48 h.
- **Ver ficha en SICOES** abre la ficha del proceso dentro de la app.
- **Compartir** envía los datos por WhatsApp, correo, etc.

La primera vez que agregas una entidad, la app carga sus convocatorias vigentes
sin notificar (para no llenarte de avisos); desde ahí avisa solo las nuevas.

## Cómo obtener el APK

### Opción A: GitHub (sin instalar nada en tu compu)
1. Crea un repositorio en https://github.com (puede ser privado).
2. Sube todo el contenido de esta carpeta (botón *Add file → Upload files*,
   arrastrando también la carpeta `.github`).
3. Entra a la pestaña **Actions**: la compilación "Compilar APK" arranca sola
   (unos 5–8 minutos).
4. Al terminar, abre la ejecución y descarga **AlertaSicoes-apk** (viene en .zip;
   adentro está `AlertaSicoes.apk`).

### Opción B: Android Studio
Abre esta carpeta en Android Studio y usa *Build → Build APK(s)*.

## Instalar en el celular
1. Pasa `AlertaSicoes.apk` al celular y ábrelo (acepta "instalar apps de origen
   desconocido" si lo pide).
2. Al abrir la app, **permite las notificaciones**.
3. En la pestaña *Entidades* toca **Ajustes de batería** y deja la app **sin
   restricciones**; si no, algunos celulares (Xiaomi, Samsung, Huawei...) frenan
   las revisiones en segundo plano.

Las actualizaciones se instalan encima sin perder datos (la app usa una firma fija:
`app/firma.jks`).

## Notas técnicas
- La app consulta la misma búsqueda pública que usa la página web: abre
  `portal/index.php`, luego la página de convocatorias (obtiene el token de
  sesión) y pide los resultados a `portal/contrataciones/operacion.php`
  (10 por página, estado `r1=11` = Vigente).
- Si el SICOES cambia su página, la app mostrará el error arriba de la lista.
