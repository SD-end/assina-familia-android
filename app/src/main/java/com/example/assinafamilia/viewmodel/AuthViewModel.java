package com.example.assinafamilia.viewmodel;

import android.app.Application;
import android.content.Context;
import android.util.Patterns;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.assinafamilia.data.repository.AuthRepository;
import com.example.assinafamilia.model.Usuario;
import com.example.assinafamilia.util.Resource;
import com.example.assinafamilia.util.SessionManager;

public class AuthViewModel extends AndroidViewModel {

    private static final int TAMANHO_MAXIMO_NOME = 120;
    private static final int TAMANHO_MINIMO_SENHA = 6;

    private final AuthRepository authRepository;
    private final SessionManager sessionManager;

    public AuthViewModel(@NonNull Application application) {
        super(application);
        Context contexto = application.getApplicationContext();
        this.authRepository = new AuthRepository(contexto);
        this.sessionManager = new SessionManager(contexto);
    }

    @NonNull
    public LiveData<Resource<Usuario>> entrar(@Nullable String email, @Nullable String senha) {
        String emailLimpo = email != null ? email.trim() : "";
        String senhaInformada = senha != null ? senha : "";

        if (emailLimpo.isEmpty()
                || senhaInformada.isEmpty()
                || !Patterns.EMAIL_ADDRESS.matcher(emailLimpo).matches()) {
            return new MutableLiveData<>(
                    Resource.erro("Informe um e-mail válido e a senha.", null));
        }

        return authRepository.entrar(emailLimpo, senhaInformada);
    }

    @NonNull
    public LiveData<Resource<Usuario>> cadastrarGestor(@Nullable String nome,
                                                       @Nullable String email,
                                                       @Nullable String senha,
                                                       @Nullable String confirmacaoSenha) {
        String nomeLimpo = nome != null ? nome.trim() : "";
        String emailLimpo = email != null ? email.trim() : "";
        String senhaInformada = senha != null ? senha : "";
        String confirmacaoInformada = confirmacaoSenha != null ? confirmacaoSenha : "";

        if (nomeLimpo.isEmpty()) {
            return new MutableLiveData<>(
                    Resource.erro("Informe o seu nome completo.", null));
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
                    Resource.erro("A senha deve ter no mínimo 6 caracteres.", null));
        }
        if (!senhaInformada.equals(confirmacaoInformada)) {
            return new MutableLiveData<>(
                    Resource.erro("As senhas informadas não coincidem.", null));
        }

        return authRepository.cadastrarGestor(nomeLimpo, emailLimpo, senhaInformada);
    }

    @NonNull
    public LiveData<Resource<Boolean>> recuperarSenha(@Nullable String email) {
        String emailLimpo = email != null ? email.trim() : "";

        if (emailLimpo.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(emailLimpo).matches()) {
            return new MutableLiveData<>(
                    Resource.erro("Informe um e-mail válido para recuperação.", false));
        }

        return authRepository.recuperarSenha(emailLimpo);
    }

    @NonNull
    public LiveData<Resource<Boolean>> sincronizarFamiliaAtual() {
        return authRepository.sincronizarFamiliaAtual();
    }

    public void sair() {
        authRepository.sair();
    }

    public boolean isLogado() {
        return sessionManager.isLogado();
    }

    public boolean isGestor() {
        return sessionManager.isGestor();
    }

    public int obterUsuarioId() {
        return sessionManager.obterUsuarioId();
    }

    @Nullable
    public String obterFamiliaId() {
        return sessionManager.obterFamiliaId();
    }

    @Nullable
    public String obterNome() {
        return sessionManager.obterNome();
    }

    @Nullable
    public String obterEmail() {
        return sessionManager.obterEmail();
    }

    @Nullable
    public String obterPerfil() {
        return sessionManager.obterPerfil();
    }
}
