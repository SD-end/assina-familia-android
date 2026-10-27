package com.example.assinafamilia.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import androidx.annotation.NonNull;

public final class NetworkUtils {

    private NetworkUtils() {
        throw new UnsupportedOperationException("Classe utilitária não pode ser instanciada.");
    }

    public static boolean temConexaoInternet(@NonNull Context contexto) {
        ConnectivityManager gerenciadorConectividade = (ConnectivityManager)
                contexto.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);

        if (gerenciadorConectividade == null) {
            return false;
        }

        Network redeAtiva = gerenciadorConectividade.getActiveNetwork();
        if (redeAtiva == null) {
            return false;
        }

        NetworkCapabilities capacidadesRede =
                gerenciadorConectividade.getNetworkCapabilities(redeAtiva);
        if (capacidadesRede == null) {
            return false;
        }

        boolean possuiTransporteValido =
                capacidadesRede.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        || capacidadesRede.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                        || capacidadesRede.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);

        return possuiTransporteValido
                && capacidadesRede.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }
}
