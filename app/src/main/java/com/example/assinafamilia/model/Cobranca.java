package com.example.assinafamilia.model;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.example.assinafamilia.util.DomainConstants;

@Entity(
        tableName = "cobrancas",
        foreignKeys = {
                @ForeignKey(
                        entity = Assinatura.class,
                        parentColumns = "id",
                        childColumns = "assinaturaId",
                        onDelete = ForeignKey.CASCADE
                )
        },
        indices = {
                @Index(value = {"assinaturaId"})
        }
)
public class Cobranca {

    @PrimaryKey(autoGenerate = true)
    private int id;

    private int assinaturaId;

    private long dataVencimento;

    private float valorCobrado;

    private String statusPagamento;

    private boolean notificado24h;

    private String firestoreId;

    private String familiaId;

    private boolean sincronizado;

    public Cobranca() {
    }

    @Ignore
    public Cobranca(int assinaturaId, long dataVencimento, float valorCobrado,
                    String firestoreId, String familiaId) {
        this.assinaturaId = assinaturaId;
        this.dataVencimento = dataVencimento;
        this.valorCobrado = valorCobrado;
        this.statusPagamento = DomainConstants.COB_PENDENTE;
        this.notificado24h = false;
        this.firestoreId = firestoreId;
        this.familiaId = familiaId;
        this.sincronizado = false;
    }

    public boolean atualizarStatus(String novoStatus) {
        if (DomainConstants.COB_PAGA.equals(this.statusPagamento)
                && DomainConstants.COB_ATRASADA.equals(novoStatus)) {
            return false;
        }
        this.statusPagamento = novoStatus;
        return true;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getAssinaturaId() {
        return assinaturaId;
    }

    public void setAssinaturaId(int assinaturaId) {
        this.assinaturaId = assinaturaId;
    }

    public long getDataVencimento() {
        return dataVencimento;
    }

    public void setDataVencimento(long dataVencimento) {
        this.dataVencimento = dataVencimento;
    }

    public float getValorCobrado() {
        return valorCobrado;
    }

    public void setValorCobrado(float valorCobrado) {
        this.valorCobrado = valorCobrado;
    }

    public String getStatusPagamento() {
        return statusPagamento;
    }

    public void setStatusPagamento(String statusPagamento) {
        this.statusPagamento = statusPagamento;
    }

    public boolean isNotificado24h() {
        return notificado24h;
    }

    public void setNotificado24h(boolean notificado24h) {
        this.notificado24h = notificado24h;
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
