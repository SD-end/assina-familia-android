package com.example.assinafamilia.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "cotacoes_cache")
public class CotacaoCache {

    @PrimaryKey
    @NonNull
    private String parMoeda;

    private float valorCompra;

    private long timestampAtualizacao;

    public CotacaoCache() {
        this.parMoeda = "";
    }

    @Ignore
    public CotacaoCache(@NonNull String parMoeda, float valorCompra, long timestampAtualizacao) {
        this.parMoeda = parMoeda;
        this.valorCompra = valorCompra;
        this.timestampAtualizacao = timestampAtualizacao;
    }

    @NonNull
    public String getParMoeda() {
        return parMoeda;
    }

    public void setParMoeda(@NonNull String parMoeda) {
        this.parMoeda = parMoeda;
    }

    public float getValorCompra() {
        return valorCompra;
    }

    public void setValorCompra(float valorCompra) {
        this.valorCompra = valorCompra;
    }

    public long getTimestampAtualizacao() {
        return timestampAtualizacao;
    }

    public void setTimestampAtualizacao(long timestampAtualizacao) {
        this.timestampAtualizacao = timestampAtualizacao;
    }
}
