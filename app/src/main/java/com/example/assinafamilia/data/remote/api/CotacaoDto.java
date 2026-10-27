package com.example.assinafamilia.data.remote.api;

import com.google.gson.annotations.SerializedName;

public class CotacaoDto {

    @SerializedName("code")
    private String codigoOrigem;

    @SerializedName("codein")
    private String codigoDestino;

    @SerializedName("name")
    private String nomePar;

    @SerializedName("bid")
    private float valorCompra;

    @SerializedName("create_date")
    private String dataHoraCotacao;

    public CotacaoDto() {
    }

    public CotacaoDto(String codigoOrigem,
                      String codigoDestino,
                      String nomePar,
                      float valorCompra,
                      String dataHoraCotacao) {
        this.codigoOrigem = codigoOrigem;
        this.codigoDestino = codigoDestino;
        this.nomePar = nomePar;
        this.valorCompra = valorCompra;
        this.dataHoraCotacao = dataHoraCotacao;
    }

    public String getCodigoOrigem() {
        return codigoOrigem;
    }

    public void setCodigoOrigem(String codigoOrigem) {
        this.codigoOrigem = codigoOrigem;
    }

    public String getCodigoDestino() {
        return codigoDestino;
    }

    public void setCodigoDestino(String codigoDestino) {
        this.codigoDestino = codigoDestino;
    }

    public String getNomePar() {
        return nomePar;
    }

    public void setNomePar(String nomePar) {
        this.nomePar = nomePar;
    }

    public float getValorCompra() {
        return valorCompra;
    }

    public void setValorCompra(float valorCompra) {
        this.valorCompra = valorCompra;
    }

    public String getDataHoraCotacao() {
        return dataHoraCotacao;
    }

    public void setDataHoraCotacao(String dataHoraCotacao) {
        this.dataHoraCotacao = dataHoraCotacao;
    }
}
