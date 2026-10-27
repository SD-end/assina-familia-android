package com.example.assinafamilia.data.remote.api;

import java.util.Map;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

public interface AwesomeApiService {

    @GET("json/last/{moeda}-BRL")
    Call<Map<String, CotacaoDto>> obterCotacao(@Path("moeda") String moeda);
}
