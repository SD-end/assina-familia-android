package com.example.assinafamilia.viewmodel;

import android.app.Application;
import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import com.example.assinafamilia.data.AppDatabase;
import com.example.assinafamilia.data.repository.AssinaturaRepository;
import com.example.assinafamilia.data.repository.AuthRepository;
import com.example.assinafamilia.data.repository.FamiliaRepository;
import com.example.assinafamilia.model.Assinatura;
import com.example.assinafamilia.model.Cobranca;
import com.example.assinafamilia.model.ResumoDashboard;
import com.example.assinafamilia.model.Usuario;
import com.example.assinafamilia.util.DomainConstants;
import com.example.assinafamilia.util.RateioCalculator;
import com.example.assinafamilia.util.Resource;
import com.example.assinafamilia.util.SessionManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DashboardViewModel extends AndroidViewModel {

    private final AssinaturaRepository assinaturaRepository;
    private final FamiliaRepository familiaRepository;
    private final AuthRepository authRepository;
    private final SessionManager sessionManager;

    private final LiveData<List<Assinatura>> assinaturasVisiveisLiveData;
    private final LiveData<List<Usuario>> moradoresFamiliaLiveData;
    private final LiveData<List<Cobranca>> proximasCobrancasFamiliaLiveData;

    private final MediatorLiveData<ResumoDashboard> resumoDashboardLiveData = new MediatorLiveData<>();
    private final MediatorLiveData<List<Cobranca>> proximasCobrancasVisiveisLiveData = new MediatorLiveData<>();

    public DashboardViewModel(@NonNull Application application) {
        super(application);
        Context contexto = application.getApplicationContext();
        this.assinaturaRepository = new AssinaturaRepository(contexto);
        this.familiaRepository = new FamiliaRepository(contexto);
        this.authRepository = new AuthRepository(contexto);
        this.sessionManager = new SessionManager(contexto);

        String familiaId = obterFamiliaIdAtivo();
        if (sessionManager.isGestor()) {
            this.assinaturasVisiveisLiveData = assinaturaRepository.listarTodasPorFamilia(familiaId);
        } else {
            this.assinaturasVisiveisLiveData = assinaturaRepository.listarParaMembro(
                    familiaId, sessionManager.obterUsuarioId());
        }
        this.moradoresFamiliaLiveData = familiaRepository.listarMoradoresDaFamilia(familiaId);
        this.proximasCobrancasFamiliaLiveData =
                assinaturaRepository.listarProximasCobrancasFamilia(familiaId);

        resumoDashboardLiveData.addSource(assinaturasVisiveisLiveData,
                assinaturas -> recalcularResumoDashboard());
        resumoDashboardLiveData.addSource(moradoresFamiliaLiveData,
                moradores -> recalcularResumoDashboard());
        resumoDashboardLiveData.addSource(proximasCobrancasFamiliaLiveData,
                cobrancas -> recalcularResumoDashboard());

        proximasCobrancasVisiveisLiveData.addSource(assinaturasVisiveisLiveData,
                assinaturas -> atualizarCobrancasVisiveis());
        proximasCobrancasVisiveisLiveData.addSource(proximasCobrancasFamiliaLiveData,
                cobrancas -> atualizarCobrancasVisiveis());
    }

    @NonNull
    public LiveData<ResumoDashboard> observarResumoDashboard() {
        return resumoDashboardLiveData;
    }

    @NonNull
    public LiveData<List<Assinatura>> observarAssinaturasVisiveis() {
        return assinaturasVisiveisLiveData;
    }

    @NonNull
    public LiveData<List<Cobranca>> observarProximasCobrancasVisiveis() {
        return proximasCobrancasVisiveisLiveData;
    }

    @NonNull
    public LiveData<Resource<Cobranca>> confirmarPagamentoCobranca(@NonNull Cobranca cobranca) {
        return assinaturaRepository.confirmarPagamentoCobranca(cobranca);
    }

    @NonNull
    public LiveData<Resource<Boolean>> sincronizarDados() {
        return authRepository.sincronizarFamiliaAtual();
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

    @NonNull
    public String obterPrimeiroNomeUsuario() {
        String nomeCompleto = sessionManager.obterNome();
        if (nomeCompleto != null && !nomeCompleto.trim().isEmpty()) {
            String[] partes = nomeCompleto.trim().split("\\s+");
            if (partes.length > 0 && !partes[0].isEmpty()) {
                return partes[0];
            }
        }
        return sessionManager.isGestor() ? "Gestor" : "Morador";
    }

    @Nullable
    public String obterPerfilUsuario() {
        return sessionManager.obterPerfil();
    }

    private void recalcularResumoDashboard() {
        ResumoDashboard resumo = RateioCalculator.calcularResumo(
                assinaturasVisiveisLiveData.getValue(),
                moradoresFamiliaLiveData.getValue(),
                proximasCobrancasFamiliaLiveData.getValue(),
                sessionManager.obterUsuarioId(),
                sessionManager.isGestor()
        );
        resumoDashboardLiveData.setValue(resumo);
    }

    private void atualizarCobrancasVisiveis() {
        List<Assinatura> assinaturas = assinaturasVisiveisLiveData.getValue();
        List<Cobranca> cobrancas = proximasCobrancasFamiliaLiveData.getValue();

        if (assinaturas == null || cobrancas == null || cobrancas.isEmpty()) {
            proximasCobrancasVisiveisLiveData.setValue(new ArrayList<>());
            return;
        }

        boolean usuarioGestor = sessionManager.isGestor();
        int usuarioId = sessionManager.obterUsuarioId();
        Set<Integer> idsAssinaturasPermitidasAtivas = new HashSet<>();

        for (Assinatura assinatura : assinaturas) {
            if (assinatura == null || !assinatura.isStatus()) {
                continue;
            }
            if (usuarioGestor
                    || DomainConstants.USO_GERAL.equalsIgnoreCase(assinatura.getTipoDoUso())
                    || assinatura.getDonoId() == usuarioId) {
                idsAssinaturasPermitidasAtivas.add(assinatura.getId());
            }
        }

        List<Cobranca> filtradas = new ArrayList<>();
        for (Cobranca cobranca : cobrancas) {
            if (cobranca != null && idsAssinaturasPermitidasAtivas.contains(cobranca.getAssinaturaId())) {
                filtradas.add(cobranca);
            }
        }
        proximasCobrancasVisiveisLiveData.setValue(filtradas);
    }

    @NonNull
    private String obterFamiliaIdAtivo() {
        String familiaId = sessionManager.obterFamiliaId();
        return !TextUtils.isEmpty(familiaId) ? familiaId : AppDatabase.FAMILIA_ID_PADRAO;
    }
}
