package bo.alertasicoes

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object Notificador {
    private const val CANAL = "nuevas_convocatorias"
    private const val GRUPO = "bo.alertasicoes.NUEVAS"

    fun crearCanal(ctx: Context) {
        val canal = NotificationChannel(
            CANAL,
            "Nuevas convocatorias",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = "Aviso cuando una entidad vigilada publica una convocatoria en el SICOES" }
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
    }

    private fun puedeNotificar(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun avisarNuevas(ctx: Context, nuevas: List<Convocatoria>) {
        if (!puedeNotificar(ctx)) return
        val nm = NotificationManagerCompat.from(ctx)
        val abrir = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        for (c in nuevas.take(10)) {
            val detalle = buildString {
                append(c.objeto)
                append("\n\nModalidad: ").append(c.modalidad)
                append("\nPublicación: ").append(c.fechaPublicacion)
                append("\nPresentación: ").append(c.fechaPresentacion)
                append("\nCUCE: ").append(c.cuce)
            }
            val n = NotificationCompat.Builder(ctx, CANAL)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("Nueva convocatoria · ${c.tipo}")
                .setSubText(c.entidad)
                .setContentText(c.objeto)
                .setStyle(NotificationCompat.BigTextStyle().bigText(detalle))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setGroup(GRUPO)
                .setAutoCancel(true)
                .setContentIntent(abrir)
                .build()
            nm.notify((c.vigilanciaId + c.cuce).hashCode(), n)
        }

        if (nuevas.size > 1) {
            val inbox = NotificationCompat.InboxStyle()
            nuevas.take(6).forEach { inbox.addLine("${it.tipo}: ${it.objeto}") }
            val resumen = NotificationCompat.Builder(ctx, CANAL)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("${nuevas.size} convocatorias nuevas en el SICOES")
                .setStyle(inbox)
                .setGroup(GRUPO)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .setContentIntent(abrir)
                .build()
            nm.notify(1, resumen)
        }
    }
}
