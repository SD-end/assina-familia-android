package com.example.assinafamilia.viewmodel;

import android.app.Application;
import android.content.Context;
import android.text.TextUtils;
import android.util.Patterns;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.assinafamilia.data.AppDatabase;
import com.example.assinafamilia.data.repository.AuthRepository;
import com.example.assinafamilia.data.repository.FamiliaRepository;
import com.example.assinafamilia.model.Usuario;
import com.example.assinafamilia.util.Resource;
import com.example.assinafamilia.util.SessionManager;

import java.util.List;
import java.util.Locale;

public class FamiliaViewModel extends AndroidViewModel {

    private static final int TAMANHO_MAXIMO_NOME = 120;
    private static final int TAMANHO_MINIMO_SENHA = 6;

    private final FamiliaRepository familiaRepository;
    private final AuthRepository authRepository;
    private final SessionManager sessionManager;

    public FamiliaViewModel(@NonNull Application application) {
        super(application);
        Context contexto = application.getApplicationContext();
        this.familiaRepository = new FamiliaRepository(contexto);
        this.authRepository = new AuthRepository(contexto);
        this.sessionManager = new SessionManager(contexto);
    }

    @NonNull
    public LiveData<List<Usuario>> listarMoradoresDaFamilia() {
        return familiaRepository.listarMoradoresDaFamilia(obterFamiliaIdAtivo());
    }

    @NonNull
    public LiveData<List<Usuario>> listarMoradoresAtivos() {
        return familiaRepository.listarMoradoresAtivos(obterFamiliaIdAtivo());
    }

    @NonNull
    public LiveData<Resource<Usuario>> adicionarMorador(@Nullable String nome,
                                                        @Nullable String email,
                                                        @Nullable String senhaProvisoria) {
        String nomeLimpo = nome != null ? nome.trim() : "";
        String emailLimpo = email != null ? email.trim() : "";
        String senhaInformada = senhaProvisoria != null ? senhaProvisoria : "";

        if (nomeLimpo.isEmpty()) {
            return new MutableLiveData<>(
                    Resource.erro("Informe o nome do morador.", null));
        }
        if (nomeLimpo.length() > TAMANHO_MAXIMO_NOME) {
            return new MutableLiveData<>(
                    Resource.erro("O nome deve ter no máximo 120 caracteres.", null));
        }
        if (emailLimpo.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(emailLimpo).matches()) {
            return new MutableLiveData<>(
                    Resource.erro("Informe um e-mail válido.", null));
        }
        if (senhaInformada.length() < TAMANHO_MINIMO_SENHA) {
            return new MutableLiveData<>(
                    Resource.erro("A senha provisória deve ter no mínimo 6 caracteres.", null));
        }

        return familiaRepository.adicionarMorador(nomeLimpo, emailLimpo, senhaInformada);
    }

    @NonNull
    public LiveData<Resource<Boolean>> alterarStatusMorador(@NonNull Usuario morador, boolean ativo) {
        return familiaRepository.alterarStatusMorador(morador, ativo);
    }

    @NonNull
    public LiveData<Resource<Usuario>> atualizarMeusDados(@Nullable String novoNome,
                                                          @Nullable String novaSenhaOpcional,
                                                          @Nullable String confirmacaoNovaSenha) {
        String nomeLimpo = novoNome != null ? novoNome.trim() : "";
        String senhaInformada = novaSenhaOpcional != null ? novaSenhaOpcional : "";
        String confirmacaoInformada = confirmacaoNovaSenha != null ? confirmacaoNovaSenha : "";

        if (nomeLimpo.isEmpty()) {
            return new MutableLiveData<>(
                    Resource.erro("Informe o seu nome completo.", null));
        }
        if (nomeLimpo.length() > TAMANHO_MAXIMO_NOME) {
            return new MutableLiveData<>(
                    Resource.erro("O nome deve ter no máximo 120 caracteres.", null));
        }

        boolean informouSenhaOuConfirmacao =
                !senhaInformada.isEmpty() || !confirmacaoInformada.isEmpty();
        if (informouSenhaOuConfirmacao) {
            if (senhaInformada.length() < TAMANHO_MINIMO_SENHA) {
                return new MutableLiveData<>(
                        Resource.erro("A nova senha deve ter no mínimo 6 caracteres.", null));
            }
            if (!senhaInformada.equals(confirmacaoInformada)) {
                return new MutableLiveData<>(
                        Resource.erro("A confirmação da nova senha não coincide.", null));
            }
        }

        return familiaRepository.atualizarMeusDados(
                nomeLimpo,
                !senhaInformada.isEmpty() ? senhaInformada : null
        );
    }

    @NonNull
    public LiveData<Resource<Boolean>> sincronizarAgora() {
        return authRepository.sincronizarFamiliaAtual();
    }

    public void sair() {
        authRepository.sair();
    }

    public boolean isGestor() {
        return sessionManager.isGestor();
    }

    public int obterUsuarioId() {
        return sessionManager.obterUsuarioId();
    }

    @Nullable
    public String obterNomeUsuario() {
        return sessionManager.obterNome();
    }

    @Nullable
    public String obterEmailUsuario() {
        return sessionManager.obterEmail();
    }

    @Nullable
    public String obterPerfilUsuario() {
        return sessionManager.obterPerfil();
    }

    @NonNull
    public String obterIniciaisUsuario() {
        String nome = sessionManager.obterNome();
        if (nome == null || nome.trim().isEmpty()) {
            return "U";
        }
        String[] partes = nome.trim().split("\\s+");
        if (partes.length >= 2 && !partes[0].isEmpty() && !partes[partes.length - 1].isEmpty()) {
            String iniciais = String.valueOf(partes[0].charAt(0))
                    + partes[partes.length - 1].charAt(0);
            return iniciais.toUpperCase(Locale.ROOT);
        }
        String primeiraPalavra = partes[0];
        if (primeiraPalavra.length() >= 2) {
            return primeiraPalavra.substring(0, 2).toUpperCase(Locale.ROOT);
        }
        return primeiraPalavra.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    @NonNull
    private String obterFamiliaIdAtivo() {
        String familiaId = sessionManager.obterFamiliaId();
        return !TextUtils.isEmpty(familiaId) ? familiaId : AppDatabase.FAMILIA_ID_PADRAO;
    }
}
