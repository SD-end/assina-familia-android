package com.example.assinafamilia.model;

import java.util.ArrayList;
import java.util.List;

public class ResumoDashboard {

    public static class CustoMorador {

        private int usuarioId;
        private String nomeMorador;
        private float totalIndividual;
        private float fracaoGeral;
        private float custoTotalMorador;

        public CustoMorador() {
        }

        public CustoMorador(int usuarioId, String nomeMorador, float totalIndividual,
                            float fracaoGeral, float custoTotalMorador) {
            this.usuarioId = usuarioId;
            this.nomeMorador = nomeMorador;
            this.totalIndividual = totalIndividual;
            this.fracaoGeral = fracaoGeral;
            this.custoTotalMorador = custoTotalMorador;
        }

        public int getUsuarioId() {
            return usuarioId;
        }

        public void setUsuarioId(int usuarioId) {
            this.usuarioId = usuarioId;
        }

        public String getNomeMorador() {
            return nomeMorador;
        }

        public void setNomeMorador(String nomeMorador) {
            this.nomeMorador = nomeMorador;
        }

        public float getTotalIndividual() {
            return totalIndividual;
        }

        public void setTotalIndividual(float totalIndividual) {
            this.totalIndividual = totalIndividual;
        }

        public float getFracaoGeral() {
            return fracaoGeral;
        }

        public void setFracaoGeral(float fracaoGeral) {
            this.fracaoGeral = fracaoGeral;
        }

        public float getCustoTotalMorador() {
            return custoTotalMorador;
        }

        public void setCustoTotalMorador(float custoTotalMorador) {
            this.custoTotalMorador = custoTotalMorador;
        }
    }

    private float totalGeralCasa;
    private float totalGastoFamilia;
    private float fracaoRateioPorMembro;
    private float meuCustoTotal;
    private float meuTotalIndividual;
    private int quantidadeAssinaturasAtivas;
    private int quantidadeMoradoresAtivos;
    private boolean possuiInadimplencia;
    private List<CustoMorador> detalhamentoMoradores;

    public ResumoDashboard() {
        this.detalhamentoMoradores = new ArrayList<>();
    }

    public float getTotalGeralCasa() {
        return totalGeralCasa;
    }

    public void setTotalGeralCasa(float totalGeralCasa) {
        this.totalGeralCasa = totalGeralCasa;
    }

    public float getTotalGastoFamilia() {
        return totalGastoFamilia;
    }

    public void setTotalGastoFamilia(float totalGastoFamilia) {
        this.totalGastoFamilia = totalGastoFamilia;
    }

    public float getFracaoRateioPorMembro() {
        return fracaoRateioPorMembro;
    }

    public void setFracaoRateioPorMembro(float fracaoRateioPorMembro) {
        this.fracaoRateioPorMembro = fracaoRateioPorMembro;
    }

    public float getMeuCustoTotal() {
        return meuCustoTotal;
    }

    public void setMeuCustoTotal(float meuCustoTotal) {
        this.meuCustoTotal = meuCustoTotal;
    }

    public float getMeuTotalIndividual() {
        return meuTotalIndividual;
    }

    public void setMeuTotalIndividual(float meuTotalIndividual) {
        this.meuTotalIndividual = meuTotalIndividual;
    }

    public int getQuantidadeAssinaturasAtivas() {
        return quantidadeAssinaturasAtivas;
    }

    public void setQuantidadeAssinaturasAtivas(int quantidadeAssinaturasAtivas) {
        this.quantidadeAssinaturasAtivas = quantidadeAssinaturasAtivas;
    }

    public int getQuantidadeMoradoresAtivos() {
        return quantidadeMoradoresAtivos;
    }

    public void setQuantidadeMoradoresAtivos(int quantidadeMoradoresAtivos) {
        this.quantidadeMoradoresAtivos = quantidadeMoradoresAtivos;
    }

    public boolean isPossuiInadimplencia() {
        return possuiInadimplencia;
    }

    public void setPossuiInadimplencia(boolean possuiInadimplencia) {
        this.possuiInadimplencia = possuiInadimplencia;
    }

    public List<CustoMorador> getDetalhamentoMoradores() {
        return detalhamentoMoradores;
    }

    public void setDetalhamentoMoradores(List<CustoMorador> detalhamentoMoradores) {
        this.detalhamentoMoradores = detalhamentoMoradores;
    }
}
