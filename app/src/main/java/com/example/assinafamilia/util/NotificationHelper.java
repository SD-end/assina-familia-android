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
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.assinafamilia.view.MainActivity;

import java.util.Locale;

public final class NotificationHelper {

    private static final Locale LOCALE_BRASIL = new Locale("pt", "BR");
    private static final String TITULO_NOTIFICACAO = "Vencimento de assinatura";
    private static final String FORMATO_MENSAGEM = "A assinatura %s vence amanhã no valor de R$ %.2f";

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

        String mensagem = String.format(LOCALE_BRASIL, FORMATO_MENSAGEM, nomeServico, valor);

        NotificationCompat.Builder construtor = new NotificationCompat.Builder(
                contextoAplicacao, DomainConstants.CANAL_NOTIFICACAO_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(TITULO_NOTIFICACAO)
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
}
