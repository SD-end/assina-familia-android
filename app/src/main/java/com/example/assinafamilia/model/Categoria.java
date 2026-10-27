package com.example.assinafamilia.model;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "categorias")
public class Categoria {

    @PrimaryKey(autoGenerate = true)
    private int id;

    private String firestoreId;

    private String familiaId;

    private String nome;

    private String descricao;

    private boolean ativa;

    private boolean sincronizado;

    public Categoria() {
    }

    @Ignore
    public Categoria(String firestoreId, String familiaId, String nome, String descricao) {
        this.firestoreId = firestoreId;
        this.familiaId = familiaId;
        this.nome = nome;
        this.descricao = descricao;
        this.ativa = true;
        this.sincronizado = false;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
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

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public boolean isAtiva() {
        return ativa;
    }

    public void setAtiva(boolean ativa) {
        this.ativa = ativa;
    }

    public boolean isSincronizado() {
        return sincronizado;
    }

    public void setSincronizado(boolean sincronizado) {
        this.sincronizado = sincronizado;
    }
}
