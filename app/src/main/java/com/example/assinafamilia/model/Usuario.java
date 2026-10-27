package com.example.assinafamilia.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.example.assinafamilia.util.DomainConstants;

@Entity(tableName = "usuarios", indices = {@Index(value = {"email"}, unique = true)})
public class Usuario {

    @PrimaryKey(autoGenerate = true)
    private int id;

    private String firestoreUid;

    private String familiaId;

    private String nome;

    private String email;

    private String senhaHash;

    private String perfil;

    private boolean ativo;

    private boolean sincronizado;

    public Usuario() {
    }

    @Ignore
    public Usuario(String firestoreUid, String familiaId, String nome, String email,
                   String senhaHash, String perfil, boolean ativo, boolean sincronizado) {
        this.firestoreUid = firestoreUid;
        this.familiaId = familiaId;
        this.nome = nome;
        this.email = email;
        this.senhaHash = senhaHash;
        this.perfil = perfil;
        this.ativo = ativo;
        this.sincronizado = sincronizado;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getFirestoreUid() {
        return firestoreUid;
    }

    public void setFirestoreUid(String firestoreUid) {
        this.firestoreUid = firestoreUid;
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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public void setSenhaHash(String senhaHash) {
        this.senhaHash = senhaHash;
    }

    public String getPerfil() {
        return perfil;
    }

    public void setPerfil(String perfil) {
        this.perfil = perfil;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }

    public boolean isSincronizado() {
        return sincronizado;
    }

    public void setSincronizado(boolean sincronizado) {
        this.sincronizado = sincronizado;
    }

    public boolean isGestor() {
        return DomainConstants.PERFIL_GESTOR.equals(perfil);
    }

    @NonNull
    @Override
    public String toString() {
        return "Usuario{" +
                "id=" + id +
                ", nome='" + nome + '\'' +
                ", email='" + email + '\'' +
                ", perfil='" + perfil + '\'' +
                ", ativo=" + ativo +
                ", sincronizado=" + sincronizado +
                '}';
    }
}
