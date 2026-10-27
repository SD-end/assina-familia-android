package com.example.assinafamilia.model;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.example.assinafamilia.util.DomainConstants;

@Entity(
        tableName = "tabela_assinaturas",
        foreignKeys = {
                @ForeignKey(
                        entity = Usuario.class,
                        parentColumns = "id",
                        childColumns = "donoId",
                        onDelete = ForeignKey.CASCADE
                ),
                @ForeignKey(
                        entity = Categoria.class,
                        parentColumns = "id",
                        childColumns = "categoriaId",
                        onDelete = ForeignKey.RESTRICT
                )
        },
        indices = {
                @Index(value = {"donoId"}),
                @Index(value = {"categoriaId"})
        }
)
public class Assinatura {

    @PrimaryKey(autoGenerate = true)
    private int id;

    private int donoId;

    private int categoriaId;

    private String nomeServico;

    private float valorOriginal;

    private String moeda;

    private float valorTotal;

    private String periodicidade;

    private String tipoDoUso;

    private long dataProximoVencimento;

    private boolean status;

    private String firestoreId;

    private String familiaId;

    private boolean sincronizado;

    public Assinatura() {
    }

    @Ignore
    public Assinatura(int donoId, int categoriaId, String nomeServico, float valorOriginal,
                      String moeda, float valorTotal, String periodicidade, String tipoDoUso,
                      long dataProximoVencimento, boolean status, String firestoreId,
                      String familiaId, boolean sincronizado) {
        this.donoId = donoId;
        this.categoriaId = categoriaId;
        this.nomeServico = nomeServico;
        this.valorOriginal = valorOriginal;
        this.moeda = moeda;
        this.valorTotal = valorTotal;
        this.periodicidade = periodicidade;
        this.tipoDoUso = tipoDoUso;
        this.dataProximoVencimento = dataProximoVencimento;
        this.status = status;
        this.firestoreId = firestoreId;
        this.familiaId = familiaId;
        this.sincronizado = sincronizado;
    }

    @Ignore
    public Assinatura(String nomeServico, float valorTotal, String tipoDoUso) {
        this.nomeServico = nomeServico;
        this.valorOriginal = valorTotal;
        this.moeda = DomainConstants.MOEDA_BRL;
        this.valorTotal = valorTotal;
        this.periodicidade = DomainConstants.PER_MENSAL;
        this.tipoDoUso = tipoDoUso;
        this.dataProximoVencimento = System.currentTimeMillis();
        this.status = true;
        this.firestoreId = null;
        this.familiaId = null;
        this.sincronizado = false;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getDonoId() {
        return donoId;
    }

    public void setDonoId(int donoId) {
        this.donoId = donoId;
    }

    public int getCategoriaId() {
        return categoriaId;
    }

    public void setCategoriaId(int categoriaId) {
        this.categoriaId = categoriaId;
    }

    public String getNomeServico() {
        return nomeServico;
    }

    public void setNomeServico(String nomeServico) {
        this.nomeServico = nomeServico;
    }

    public float getValorOriginal() {
        return valorOriginal;
    }

    public void setValorOriginal(float valorOriginal) {
        this.valorOriginal = valorOriginal;
    }

    public String getMoeda() {
        return moeda;
    }

    public void setMoeda(String moeda) {
        this.moeda = moeda;
    }

    public float getValorTotal() {
        return valorTotal;
    }

    public void setValorTotal(float valorTotal) {
        this.valorTotal = valorTotal;
    }

    public String getPeriodicidade() {
        return periodicidade;
    }

    public void setPeriodicidade(String periodicidade) {
        this.periodicidade = periodicidade;
    }

    public String getTipoDoUso() {
        return tipoDoUso;
    }

    public void setTipoDoUso(String tipoDoUso) {
        this.tipoDoUso = tipoDoUso;
    }

    public long getDataProximoVencimento() {
        return dataProximoVencimento;
    }

    public void setDataProximoVencimento(long dataProximoVencimento) {
        this.dataProximoVencimento = dataProximoVencimento;
    }

    public boolean isStatus() {
        return status;
    }

    public void setStatus(boolean status) {
        this.status = status;
    }

    public String getFirestoreId() {
        return firestoreId;
    }

    public void setFirestoreId(String firestoreId) {
        this.firestoreId = firestoreId;
    }

    public String getFamiliaId() {
        return familiaId;
    }

    public void setFamiliaId(String familiaId) {
        this.familiaId = familiaId;
    }

    public boolean isSincronizado() {
        return sincronizado;
    }

    public void setSincronizado(boolean sincronizado) {
        this.sincronizado = sincronizado;
    }
}
