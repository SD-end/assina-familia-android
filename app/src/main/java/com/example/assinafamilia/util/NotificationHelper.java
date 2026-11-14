package com.example.assinafamilia.util;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.assinafamilia.view.MainActivity;

import java.util.Calendar;
import java.util.Locale;

public final class NotificationHelper {

    private static final Locale LOCALE_BRASIL = new Locale("pt", "BR");
    private static final long MILLIS_DIA = 86400000L;
    private static final String TITULO_ATRASO = "Assinatura em atraso";
    private static final String TITULO_VENCE_HOJE = "Assinatura vence hoje";
    private static final String TITULO_VENCIMENTO = "Vencimento de assinatura";
    private static final String MENSAGEM_ATRASO =
            "A assinatura %s está em atraso no valor de R$ %.2f";
    private static final String MENSAGEM_VENCE_HOJE =
            "A assinatura %s vence hoje no valor de R$ %.2f";
    private static final String MENSAGEM_VENCIMENTO =
            "A assinatura %s vence amanhã no valor de R$ %.2f";

    private NotificationHelper() {
    }

    public static void criarCanalNotificacao(@NonNull Context contexto) {
        NotificationChannel canal = new NotificationChannel(
                DomainConstants.CANAL_NOTIFICACAO_ID,
                DomainConstants.CANAL_NOTIFICACAO_NOME,
                NotificationManager.IMPORTANCE_HIGH);
        NotificationManager gerenciador = contexto.getSystemService(NotificationManager.class);
        if (gerenciador != null) {
            gerenciador.createNotificationChannel(canal);
        }
    }

    public static boolean possuiPermissaoNotificacao(@NonNull Context contexto) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(contexto, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    public static boolean exibirNotificacaoVencimento(@NonNull Context contexto,
                                                      int notificacaoId,
                                                      @NonNull String nomeServico,
                                                      float valor) {
        return exibirNotificacaoVencimento(
                contexto,
                notificacaoId,
                nomeServico,
                valor,
                System.currentTimeMillis() + MILLIS_DIA,
                DomainConstants.COB_PENDENTE);
    }

    public static boolean exibirNotificacaoVencimento(@NonNull Context contexto,
                                                      int notificacaoId,
                                                      @NonNull String nomeServico,
                                                      float valor,
                                                      long dataVencimento,
                                                      @Nullable String statusPagamento) {
        Context contextoAplicacao = contexto.getApplicationContext();
        if (!possuiPermissaoNotificacao(contextoAplicacao)) {
            return false;
        }
        criarCanalNotificacao(contextoAplicacao);

        Intent intentAbrirApp = new Intent(contextoAplicacao, MainActivity.class);
        intentAbrirApp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                contextoAplicacao,
                notificacaoId,
                intentAbrirApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        long diferencaDias = calcularDiferencaDias(dataVencimento);
        String titulo;
        String mensagem;
        if (DomainConstants.COB_ATRASADA.equals(statusPagamento) || diferencaDias < 0) {
            titulo = TITULO_ATRASO;
            mensagem = String.format(LOCALE_BRASIL, MENSAGEM_ATRASO, nomeServico, valor);
        } else if (diferencaDias == 0) {
            titulo = TITULO_VENCE_HOJE;
            mensagem = String.format(LOCALE_BRASIL, MENSAGEM_VENCE_HOJE, nomeServico, valor);
        } else {
            titulo = TITULO_VENCIMENTO;
            mensagem = String.format(LOCALE_BRASIL, MENSAGEM_VENCIMENTO, nomeServico, valor);
        }

        NotificationCompat.Builder construtor = new NotificationCompat.Builder(
                contextoAplicacao, DomainConstants.CANAL_NOTIFICACAO_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(titulo)
                .setContentText(mensagem)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(mensagem))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        try {
            NotificationManagerCompat.from(contextoAplicacao).notify(notificacaoId, construtor.build());
            return true;
        } catch (SecurityException excecao) {
            return false;
        }
    }

    private static long calcularDiferencaDias(long dataVencimento) {
        Calendar hoje = Calendar.getInstance();
        hoje.set(Calendar.HOUR_OF_DAY, 0);
        hoje.set(Calendar.MINUTE, 0);
        hoje.set(Calendar.SECOND, 0);
        hoje.set(Calendar.MILLISECOND, 0);

        Calendar vencimento = Calendar.getInstance();
        vencimento.setTimeInMillis(dataVencimento);
        vencimento.set(Calendar.HOUR_OF_DAY, 0);
        vencimento.set(Calendar.MINUTE, 0);
        vencimento.set(Calendar.SECOND, 0);
        vencimento.set(Calendar.MILLISECOND, 0);

        long diferencaMillis = vencimento.getTimeInMillis() - hoje.getTimeInMillis();
        return diferencaMillis / MILLIS_DIA;
    }
}
