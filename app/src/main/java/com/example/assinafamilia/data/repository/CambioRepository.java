package com.example.assinafamilia.data.repository;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.assinafamilia.data.AppDatabase;
import com.example.assinafamilia.data.local.dao.CotacaoDao;
import com.example.assinafamilia.data.remote.api.CotacaoDto;
import com.example.assinafamilia.data.remote.api.RetrofitClient;
import com.example.assinafamilia.model.CotacaoCache;
import com.example.assinafamilia.util.DomainConstants;
import com.example.assinafamilia.util.NetworkUtils;
import com.example.assinafamilia.util.Resource;

import java.util.Locale;
import java.util.Map;

import retrofit2.Response;

public class CambioRepository {

    private static final float TAXA_BRL = 1.0f;
    private static final float TAXA_CONTINGENCIA_USD = 5.80f;
    private static final float TAXA_CONTINGENCIA_EUR = 6.20f;
    private static final String SUFIXO_CHAVE_API = "BRL";
    private static final String SUFIXO_CHAVE_CACHE = "-BRL";
    private static final String MENSAGEM_CACHE_LOCAL =
            "Cotação em tempo real indisponível. Utilizando última taxa salva localmente.";
    private static final String MENSAGEM_CONTINGENCIA =
            "Modo offline: aplicando taxa estimada de contingência.";

    private final Context contexto;
    private final CotacaoDao cotacaoDao;

    public CambioRepository(@NonNull Context contexto) {
        this.contexto = contexto.getApplicationContext();
        this.cotacaoDao = AppDatabase.getInstance(this.contexto).cotacaoDao();
    }

    @NonNull
    public LiveData<Resource<Float>> obterTaxaCambio(@NonNull String moeda) {
        return calcularComTaxa(moeda, 1.0f);
    }

    @NonNull
    public LiveData<Resource<Float>> converterParaBrl(@NonNull String moeda, float valorOriginal) {
        if (Float.isNaN(valorOriginal) || valorOriginal <= 0f) {
            return new MutableLiveData<>(Resource.erro("Informe um valor maior que zero.", null));
        }
        return calcularComTaxa(moeda, valorOriginal);
    }

    @NonNull
    private LiveData<Resource<Float>> calcularComTaxa(@Nullable String moeda, float multiplicador) {
        String moedaNormalizada = moeda != null ? moeda.trim().toUpperCase(Locale.ROOT) : "";

        if (moedaNormalizada.isEmpty() || DomainConstants.MOEDA_BRL.equals(moedaNormalizada)) {
            return new MutableLiveData<>(Resource.sucesso(TAXA_BRL * multiplicador));
        }
        if (!DomainConstants.MOEDA_USD.equals(moedaNormalizada)
                && !DomainConstants.MOEDA_EUR.equals(moedaNormalizada)) {
            return new MutableLiveData<>(Resource.erro("Moeda não suportada.", null));
        }

        MutableLiveData<Resource<Float>> resultado =
                new MutableLiveData<>(Resource.carregando(null));

        AppDatabase.databaseWriteExecutor.execute(() -> {
            String parMoedaCache = moedaNormalizada + SUFIXO_CHAVE_CACHE;

            Float taxaOnline = buscarTaxaOnline(moedaNormalizada);
            if (taxaOnline != null) {
                cotacaoDao.salvarCotacao(new CotacaoCache(
                        parMoedaCache, taxaOnline, System.currentTimeMillis()));
                resultado.postValue(Resource.sucesso(taxaOnline * multiplicador));
                return;
            }

            CotacaoCache cache = cotacaoDao.obterUltimaCotacao(parMoedaCache);
            if (cache != null && cache.getValorCompra() > 0f) {
                resultado.postValue(Resource.sucessoComFallbackOffline(
                        cache.getValorCompra() * multiplicador, MENSAGEM_CACHE_LOCAL));
                return;
            }

            resultado.postValue(Resource.sucessoComFallbackOffline(
                    obterTaxaContingencia(moedaNormalizada) * multiplicador, MENSAGEM_CONTINGENCIA));
        });

        return resultado;
    }

    @Nullable
    private Float buscarTaxaOnline(@NonNull String moeda) {
        if (!NetworkUtils.temConexaoInternet(contexto)) {
            return null;
        }
        try {
            Response<Map<String, CotacaoDto>> resposta = RetrofitClient.obterInstancia()
                    .obterServico()
                    .obterCotacao(moeda)
                    .execute();
            Map<String, CotacaoDto> corpo = resposta.body();
            if (!resposta.isSuccessful() || corpo == null || corpo.isEmpty()) {
                return null;
            }
            CotacaoDto cotacao = corpo.get(moeda + SUFIXO_CHAVE_API);
            if (cotacao == null) {
                cotacao = corpo.values().iterator().next();
            }
            if (cotacao == null) {
                return null;
            }
            float taxa = cotacao.getValorCompra();
            return (Float.isNaN(taxa) || taxa <= 0f) ? null : taxa;
        } catch (Exception excecao) {
            return null;
        }
    }

    private static float obterTaxaContingencia(@NonNull String moeda) {
        return DomainConstants.MOEDA_EUR.equals(moeda) ? TAXA_CONTINGENCIA_EUR : TAXA_CONTINGENCIA_USD;
    }
}
