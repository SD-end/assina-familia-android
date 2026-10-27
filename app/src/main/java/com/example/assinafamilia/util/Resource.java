package com.example.assinafamilia.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class Resource<T> {

    public enum Status {
        SUCESSO,
        ERRO,
        CARREGANDO
    }

    @NonNull
    private final Status status;

    @Nullable
    private final T dados;

    @Nullable
    private final String mensagem;

    private final boolean fallbackOffline;

    private Resource(@NonNull Status status,
                     @Nullable T dados,
                     @Nullable String mensagem,
                     boolean fallbackOffline) {
        this.status = status;
        this.dados = dados;
        this.mensagem = mensagem;
        this.fallbackOffline = fallbackOffline;
    }

    public static <T> Resource<T> sucesso(@Nullable T dados) {
        return new Resource<>(Status.SUCESSO, dados, null, false);
    }

    public static <T> Resource<T> sucessoComFallbackOffline(@Nullable T dados,
                                                            @Nullable String mensagem) {
        return new Resource<>(Status.SUCESSO, dados, mensagem, true);
    }

    public static <T> Resource<T> erro(@Nullable String mensagem, @Nullable T dados) {
        return new Resource<>(Status.ERRO, dados, mensagem, false);
    }

    public static <T> Resource<T> carregando(@Nullable T dados) {
        return new Resource<>(Status.CARREGANDO, dados, null, false);
    }

    @NonNull
    public Status getStatus() {
        return status;
    }

    @Nullable
    public T getDados() {
        return dados;
    }

    @Nullable
    public String getMensagem() {
        return mensagem;
    }

    public boolean isFallbackOffline() {
        return fallbackOffline;
    }
}
