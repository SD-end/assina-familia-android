package com.example.assinafamilia.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "tabela_assinaturas")
public class Assinatura {

    @PrimaryKey(autoGenerate = true)
    private int id;
    private String nomeServico;
    private float valorTotal;
    private String tipoDoUso;

    public Assinatura() {

    }

    public Assinatura(String nomeServico, float valorTotal, String tipoDoUso) {
        this.nomeServico = nomeServico;
        this.valorTotal = valorTotal;
        this.tipoDoUso = tipoDoUso;
    }

    public int getId() {
        return id;
    }

    public String getNomeServico() {
        return nomeServico;
    }
    public float getValorTotal() {
        return valorTotal;
    }

    public String getTipoDoUso() {
        return tipoDoUso;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setNomeServico(String nomeServico) {
        this.nomeServico = nomeServico;
    }

    public void setValorTotal(float valorTotal) {
        this.valorTotal = valorTotal;
    }

    public void setTipoDoUso(String tipoDoUso) {
        this.tipoDoUso = tipoDoUso;
    }
}
