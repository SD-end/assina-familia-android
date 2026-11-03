package com.example.assinafamilia.data.remote.firebase;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.assinafamilia.model.Usuario;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;

import java.util.List;
import java.util.UUID;

public class FirebaseAuthSource {

    public static final String NOME_APP_SECUNDARIO = "SecondaryAuthApp";
    public static final String ESQUEMA_URI_FAMILIA = "assinafamilia";
    public static final String HOST_URI_FAMILIA = "familia";

    private static final String MENSAGEM_FIREBASE_INDISPONIVEL =
            "Firebase indisponível neste dispositivo. Operando em modo offline.";

    private final Context contexto;

    public FirebaseAuthSource(@NonNull Context contexto) {
        this.contexto = contexto.getApplicationContext();
    }

    public boolean isFirebaseDisponivel() {
        return !FirebaseApp.getApps(contexto).isEmpty();
    }

    @Nullable
    private FirebaseAuth obterAuth() {
        if (!isFirebaseDisponivel()) {
            return null;
        }
        return FirebaseAuth.getInstance();
    }

    @Nullable
    public FirebaseUser obterUsuarioAtual() {
        FirebaseAuth auth = obterAuth();
        return auth != null ? auth.getCurrentUser() : null;
    }

    @Nullable
    public String obterUidAtual() {
        FirebaseUser usuario = obterUsuarioAtual();
        return usuario != null ? usuario.getUid() : null;
    }

    @NonNull
    public Task<FirebaseUser> entrar(@NonNull String email, @NonNull String senha) {
        FirebaseAuth auth = obterAuth();
        if (auth == null) {
            return falhaFirebaseIndisponivel();
        }
        return auth.signInWithEmailAndPassword(email.trim(), senha)
                .continueWith(FirebaseAuthSource::obterUsuarioDoResultado);
    }

    @NonNull
    public Task<FirebaseUser> cadastrarGestor(@NonNull String nome,
                                              @NonNull String email,
                                              @NonNull String senha) {
        FirebaseAuth auth = obterAuth();
        if (auth == null) {
            return falhaFirebaseIndisponivel();
        }
        final String familiaId = UUID.randomUUID().toString();
        return auth.createUserWithEmailAndPassword(email.trim(), senha)
                .continueWithTask(tarefa -> {
                    FirebaseUser novoUsuario = obterUsuarioDoResultado(tarefa);
                    return atualizarPerfilComFamilia(novoUsuario, nome, familiaId)
                            .continueWith(tarefaPerfil -> {
                                if (!tarefaPerfil.isSuccessful()
                                        && tarefaPerfil.getException() != null) {
                                    throw tarefaPerfil.getException();
                                }
                                return novoUsuario;
                            });
                });
    }

    @NonNull
    public Task<Void> enviarEmailRecuperacaoSenha(@NonNull String email) {
        FirebaseAuth auth = obterAuth();
        if (auth == null) {
            return falhaFirebaseIndisponivel();
        }
        return auth.sendPasswordResetEmail(email.trim());
    }

    public void sair() {
        FirebaseAuth auth = obterAuth();
        if (auth != null) {
            auth.signOut();
        }
    }

    @Nullable
    public static String extrairFamiliaId(@Nullable FirebaseUser usuario) {
        if (usuario == null) {
            return null;
        }
        return extrairFamiliaId(usuario.getPhotoUrl());
    }

    @Nullable
    public static String extrairFamiliaId(@Nullable Uri photoUri) {
        if (photoUri == null
                || !ESQUEMA_URI_FAMILIA.equals(photoUri.getScheme())
                || !HOST_URI_FAMILIA.equals(photoUri.getHost())) {
            return null;
        }
        String familiaId = photoUri.getLastPathSegment();
        return (familiaId == null || familiaId.trim().isEmpty()) ? null : familiaId;
    }

    @NonNull
    public static Uri montarUriFamilia(@NonNull String familiaId) {
        return Uri.parse(ESQUEMA_URI_FAMILIA + "://" + HOST_URI_FAMILIA + "/" + familiaId);
    }

    @NonNull
    public Task<String> criarContaMorador(@NonNull Usuario morador,
                                          @NonNull String senhaProvisoria,
                                          @NonNull FirestoreSource firestoreSource) {
        if (!isFirebaseDisponivel()) {
            return falhaFirebaseIndisponivel();
        }
        final String familiaId = morador.getFamiliaId();
        if (familiaId == null || familiaId.trim().isEmpty()) {
            return Tasks.forException(
                    new IllegalArgumentException("Morador sem familiaId vinculado."));
        }

        final FirebaseAuth authSecundario;
        try {
            authSecundario = FirebaseAuth.getInstance(obterAppSecundario());
        } catch (Exception e) {
            return Tasks.forException(e);
        }

        return authSecundario
                .createUserWithEmailAndPassword(morador.getEmail().trim(), senhaProvisoria)
                .continueWithTask(tarefaCriacao -> {
                    FirebaseUser novoUsuario = obterUsuarioDoResultado(tarefaCriacao);
                    final String novoUid = novoUsuario.getUid();
                    morador.setFirestoreUid(novoUid);

                    return atualizarPerfilComFamilia(novoUsuario, morador.getNome(), familiaId)
                            .continueWithTask(tarefaPerfil ->
                                    firestoreSource.salvarUsuario(familiaId, morador))
                            .continueWith(tarefaFirestore -> {
                                morador.setSincronizado(tarefaFirestore.isSuccessful());
                                return novoUid;
                            });
                })
                .continueWith(tarefaFinal -> {
                    authSecundario.signOut();
                    if (!tarefaFinal.isSuccessful() && tarefaFinal.getException() != null) {
                        throw tarefaFinal.getException();
                    }
                    return tarefaFinal.getResult();
                });
    }

    @NonNull
    private FirebaseApp obterAppSecundario() {
        List<FirebaseApp> apps = FirebaseApp.getApps(contexto);
        for (FirebaseApp app : apps) {
            if (NOME_APP_SECUNDARIO.equals(app.getName())) {
                return app;
            }
        }
        return FirebaseApp.initializeApp(
                contexto, FirebaseApp.getInstance().getOptions(), NOME_APP_SECUNDARIO);
    }

    @NonNull
    public static String obterMensagemErro(@Nullable Exception excecao) {
        if (excecao instanceof FirebaseAuthWeakPasswordException) {
            return "Senha fraca. Utilize ao menos 6 caracteres.";
        }
        if (excecao instanceof FirebaseAuthUserCollisionException) {
            return "Este e-mail já está cadastrado.";
        }
        if (excecao instanceof FirebaseAuthInvalidUserException) {
            return "Usuário não encontrado ou desativado.";
        }
        if (excecao instanceof FirebaseAuthInvalidCredentialsException) {
            return "E-mail ou senha inválidos.";
        }
        if (excecao instanceof FirebaseNetworkException) {
            return "Sem conexão com a internet. Tente novamente.";
        }
        if (excecao instanceof IllegalStateException && excecao.getMessage() != null) {
            return excecao.getMessage();
        }
        return "Não foi possível concluir a operação. Tente novamente.";
    }
    @NonNull
    private static FirebaseUser obterUsuarioDoResultado(@NonNull Task<AuthResult> tarefa)
            throws Exception {
        if (!tarefa.isSuccessful()) {
            Exception excecao = tarefa.getException();
            throw excecao != null ? excecao : new IllegalStateException("Falha na autenticação.");
        }
        AuthResult resultado = tarefa.getResult();
        if (resultado == null || resultado.getUser() == null) {
            throw new IllegalStateException("Falha ao obter usuário criado.");
        }
        return resultado.getUser();
    }

    @NonNull
    private static Task<Void> atualizarPerfilComFamilia(@NonNull FirebaseUser usuario,
                                                        @Nullable String nome,
                                                        @NonNull String familiaId) {
        UserProfileChangeRequest requisicao = new UserProfileChangeRequest.Builder()
                .setDisplayName(nome)
                .setPhotoUri(montarUriFamilia(familiaId))
                .build();
        return usuario.updateProfile(requisicao);
    }

    @NonNull
    private static <T> Task<T> falhaFirebaseIndisponivel() {
        return Tasks.forException(new IllegalStateException(MENSAGEM_FIREBASE_INDISPONIVEL));
    }
}
