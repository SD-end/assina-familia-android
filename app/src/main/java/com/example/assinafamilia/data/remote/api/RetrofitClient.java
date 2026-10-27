package com.example.assinafamilia.data.remote.api;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class RetrofitClient {

    private static final String URL_BASE = "https://economia.awesomeapi.com.br/";
    private static final long TEMPO_LIMITE_SEGUNDOS = 10L;
    private static RetrofitClient instancia;
    private final AwesomeApiService servico;

    private RetrofitClient() {
        OkHttpClient clienteHttp = new OkHttpClient.Builder()
                .connectTimeout(TEMPO_LIMITE_SEGUNDOS, TimeUnit.SECONDS)
                .readTimeout(TEMPO_LIMITE_SEGUNDOS, TimeUnit.SECONDS)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(URL_BASE)
                .client(clienteHttp)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        servico = retrofit.create(AwesomeApiService.class);
    }

    public static synchronized RetrofitClient obterInstancia() {
        if (instancia == null) {
            instancia = new RetrofitClient();
        }
        return instancia;
    }

    public AwesomeApiService obterServico() {
        return servico;
    }
}
