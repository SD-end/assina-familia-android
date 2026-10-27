package com.example.assinafamilia.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class SessionManager {

    private static final String NOME_PREFERENCIAS = "assina_familia_session";
    private static final String CHAVE_USUARIO_ID = "usuario_id";
    private static final String CHAVE_FIRESTORE_UID = "firestore_uid";
    private static final String CHAVE_FAMILIA_ID = "familia_id";
    private static final String CHAVE_NOME = "nome";
    private static final String CHAVE_EMAIL = "email";
    private static final String CHAVE_PERFIL = "perfil";
    private static final String CHAVE_TOKEN_SYNC = "token_sync";

    private static final int USUARIO_ID_INVALIDO = -1;
    private static final long TOKEN_SYNC_INVALIDO = 0L;

    private static final String SALT_SENHA = "AssinaFamilia#N2@Salt";

    private final SharedPreferences preferencias;

    public SessionManager(@NonNull Context contexto) {
        this.preferencias = contexto.getApplicationContext()
                .getSharedPreferences(NOME_PREFERENCIAS, Context.MODE_PRIVATE);
    }

    public void salvarSessao(int usuarioId,
                             @Nullable String firestoreUid,
                             @Nullable String familiaId,
                             @Nullable String nome,
                             @Nullable String email,
                             @Nullable String perfil) {
        preferencias.edit()
                .putInt(CHAVE_USUARIO_ID, usuarioId)
                .putString(CHAVE_FIRESTORE_UID, firestoreUid)
                .putString(CHAVE_FAMILIA_ID, familiaId)
                .putString(CHAVE_NOME, nome)
                .putString(CHAVE_EMAIL, email)
                .putString(CHAVE_PERFIL, perfil)
                .apply();
    }

    public void salvarTokenSync(long timestamp) {
        preferencias.edit()
                .putLong(CHAVE_TOKEN_SYNC, timestamp)
                .apply();
    }

    public int obterUsuarioId() {
        return preferencias.getInt(CHAVE_USUARIO_ID, USUARIO_ID_INVALIDO);
    }

    @Nullable
    public String obterFirestoreUid() {
        return preferencias.getString(CHAVE_FIRESTORE_UID, null);
    }

    @Nullable
    public String obterFamiliaId() {
        return preferencias.getString(CHAVE_FAMILIA_ID, null);
    }

    @Nullable
    public String obterNome() {
        return preferencias.getString(CHAVE_NOME, null);
    }

    @Nullable
    public String obterEmail() {
        return preferencias.getString(CHAVE_EMAIL, null);
    }

    @Nullable
    public String obterPerfil() {
        return preferencias.getString(CHAVE_PERFIL, null);
    }

    public long obterTokenSync() {
        return preferencias.getLong(CHAVE_TOKEN_SYNC, TOKEN_SYNC_INVALIDO);
    }

    public boolean isGestor() {
        return DomainConstants.PERFIL_GESTOR.equals(obterPerfil());
    }

    public boolean isLogado() {
        return obterUsuarioId() != USUARIO_ID_INVALIDO;
    }

    public void limparSessao() {
        preferencias.edit()
                .remove(CHAVE_USUARIO_ID)
                .remove(CHAVE_FIRESTORE_UID)
                .remove(CHAVE_FAMILIA_ID)
                .remove(CHAVE_NOME)
                .remove(CHAVE_EMAIL)
                .remove(CHAVE_PERFIL)
                .remove(CHAVE_TOKEN_SYNC)
                .apply();
    }

    @NonNull
    public static String gerarHashSenha(@NonNull String senha) {
        try {
            MessageDigest algoritmoHash = MessageDigest.getInstance("SHA-256");
            byte[] bytesHash = algoritmoHash.digest(
                    (SALT_SENHA + senha).getBytes(StandardCharsets.UTF_8));

            StringBuilder hashHexadecimal = new StringBuilder(bytesHash.length * 2);
            for (byte byteAtual : bytesHash) {
                hashHexadecimal.append(String.format("%02x", byteAtual));
            }
            return hashHexadecimal.toString();
        } catch (NoSuchAlgorithmException excecao) {
            throw new IllegalStateException("Algoritmo SHA-256 indisponível no dispositivo.", excecao);
        }
    }
}
