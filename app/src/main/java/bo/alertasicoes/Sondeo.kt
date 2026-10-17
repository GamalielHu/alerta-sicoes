package bo.alertasicoes

import android.app.Application
import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Tarea que Android ejecuta en segundo plano cada cierto tiempo. */
class SondeoWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        // Fuera del horario elegido no se conecta a internet: solo se vuelve a dormir.
        if (!Almacen(applicationContext).dentroDeHorario()) return Result.success()
        Monitor.revisar(applicationContext)
        return Result.success()
    }
}

object Programador {
    private const val NOMBRE = "sondeo_sicoes"

    /** Android no permite menos de 15 minutos entre revisiones automáticas. */
    fun programar(ctx: Context, reemplazar: Boolean) {
        val almacen = Almacen(ctx)
        val minutos = almacen.intervaloMin()
        val req = PeriodicWorkRequestBuilder<SondeoWorker>(minutos.coerceAtLeast(15).toLong(), TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(almacen.pausarBateriaBaja())
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            NOMBRE,
            if (reemplazar) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
            req,
        )
    }
}

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Notificador.crearCanal(this)
        Programador.programar(this, reemplazar = false)
    }
}
