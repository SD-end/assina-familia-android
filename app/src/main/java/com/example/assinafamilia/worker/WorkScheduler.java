package com.example.assinafamilia.worker;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public final class WorkScheduler {

    public static final String TRABALHO_SINCRONIZACAO = "trabalho_sincronizacao_assina_familia";
    public static final String TRABALHO_VENCIMENTOS_PERIODICO = "trabalho_vencimentos_periodico_assina_familia";
    public static final String TRABALHO_VENCIMENTOS_IMEDIATO = "trabalho_vencimentos_imediato_assina_familia";
    public static final long INTERVALO_VENCIMENTOS_HORAS = 6L;

    private static final long ATRASO_INICIAL_BACKOFF_SEGUNDOS = 30L;

    private WorkScheduler() {
    }

    public static void enfileirarSincronizacaoQuandoOnline(@NonNull Context contexto) {
        Constraints restricoes = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        OneTimeWorkRequest requisicao = new OneTimeWorkRequest.Builder(SyncWorker.class)
                .setConstraints(restricoes)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, ATRASO_INICIAL_BACKOFF_SEGUNDOS, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(contexto.getApplicationContext())
                .enqueueUniqueWork(TRABALHO_SINCRONIZACAO, ExistingWorkPolicy.APPEND_OR_REPLACE, requisicao);
    }

    public static void agendarVerificacaoVencimentos(@NonNull Context contexto) {
        PeriodicWorkRequest requisicao = new PeriodicWorkRequest.Builder(
                VencimentoWorker.class, INTERVALO_VENCIMENTOS_HORAS, TimeUnit.HOURS)
                .build();
        WorkManager.getInstance(contexto.getApplicationContext())
                .enqueueUniquePeriodicWork(TRABALHO_VENCIMENTOS_PERIODICO, ExistingPeriodicWorkPolicy.KEEP, requisicao);
    }

    public static void executarVerificacaoVencimentosImediata(@NonNull Context contexto) {
        OneTimeWorkRequest requisicao = new OneTimeWorkRequest.Builder(VencimentoWorker.class).build();
        WorkManager.getInstance(contexto.getApplicationContext())
                .enqueueUniqueWork(TRABALHO_VENCIMENTOS_IMEDIATO, ExistingWorkPolicy.REPLACE, requisicao);
    }
}
