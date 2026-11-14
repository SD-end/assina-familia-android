package com.example.assinafamilia.viewmodel;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

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
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DashboardViewModel extends AndroidViewModel {

    private static final String PREFS_NOTIFICACOES = "assina_familia_notificacoes_vistas";
    private static final String PREFIXO_CHAVE_VISTAS = "vistas_usuario_";

    private final AssinaturaRepository assinaturaRepository;
    private final FamiliaRepository familiaRepository;
    private final AuthRepository authRepository;
    private final SessionManager sessionManager;
    private final SharedPreferences preferenciasNotificacoes;

    private final LiveData<List<Assinatura>> assinaturasVisiveisLiveData;
    private final LiveData<List<Usuario>> moradoresFamiliaLiveData;
    private final LiveData<List<Cobranca>> proximasCobrancasFamiliaLiveData;
    private final LiveData<List<Cobranca>> historicoNotificacoesBrutoLiveData;

    private final MediatorLiveData<ResumoDashboard> resumoDashboardLiveData = new MediatorLiveData<>();
    private final MediatorLiveData<List<Cobranca>> proximasCobrancasVisiveisLiveData = new MediatorLiveData<>();
    private final MediatorLiveData<List<Cobranca>> historicoNotificacoesVisiveisLiveData = new MediatorLiveData<>();
    private final MutableLiveData<Integer> quantidadeNaoLidasLiveData = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> quantidadeAtrasadasLiveData = new MutableLiveData<>(0);

    public DashboardViewModel(@NonNull Application application) {
        super(application);
        Context contexto = application.getApplicationContext();
        this.assinaturaRepository = new AssinaturaRepository(contexto);
        this.familiaRepository = new FamiliaRepository(contexto);
        this.authRepository = new AuthRepository(contexto);
        this.sessionManager = new SessionManager(contexto);
        this.preferenciasNotificacoes =
                contexto.getSharedPreferences(PREFS_NOTIFICACOES, Context.MODE_PRIVATE);

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
        this.historicoNotificacoesBrutoLiveData =
                assinaturaRepository.listarHistoricoNotificacoesFamilia(
                        familiaId, calcularFimDeAmanha());

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

        historicoNotificacoesVisiveisLiveData.addSource(assinaturasVisiveisLiveData,
                assinaturas -> atualizarHistoricoNotificacoesVisiveis());
        historicoNotificacoesVisiveisLiveData.addSource(historicoNotificacoesBrutoLiveData,
                cobrancas -> atualizarHistoricoNotificacoesVisiveis());
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
    public LiveData<List<Cobranca>> observarHistoricoNotificacoesVisiveis() {
        return historicoNotificacoesVisiveisLiveData;
    }

    @NonNull
    public LiveData<Integer> observarQuantidadeNotificacoesNaoLidas() {
        return quantidadeNaoLidasLiveData;
    }

    @NonNull
    public LiveData<Integer> observarQuantidadeCobrancasAtrasadas() {
        return quantidadeAtrasadasLiveData;
    }

    public boolean isNotificacaoVisualizada(@NonNull Cobranca cobranca) {
        if (DomainConstants.COB_PAGA.equals(cobranca.getStatusPagamento())) {
            return true;
        }
        Set<String> vistas = preferenciasNotificacoes.getStringSet(
                PREFIXO_CHAVE_VISTAS + obterUsuarioId(), Collections.emptySet());
        return vistas != null && vistas.contains(gerarChaveNotificacao(cobranca));
    }

    public void marcarNotificacoesComoVisualizadas() {
        Set<String> vistas = new HashSet<>();
        Set<String> salvas = preferenciasNotificacoes.getStringSet(
                PREFIXO_CHAVE_VISTAS + obterUsuarioId(), Collections.emptySet());
        if (salvas != null) {
            vistas.addAll(salvas);
        }
        List<Cobranca> atuais = historicoNotificacoesVisiveisLiveData.getValue();
        if (atuais != null) {
            for (Cobranca cobranca : atuais) {
                if (cobranca != null) {
                    vistas.add(gerarChaveNotificacao(cobranca));
                }
            }
        }
        preferenciasNotificacoes.edit()
                .putStringSet(PREFIXO_CHAVE_VISTAS + obterUsuarioId(), vistas)
                .apply();
        quantidadeNaoLidasLiveData.setValue(0);
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

    private void atualizarHistoricoNotificacoesVisiveis() {
        List<Assinatura> assinaturas = assinaturasVisiveisLiveData.getValue();
        List<Cobranca> cobrancas = historicoNotificacoesBrutoLiveData.getValue();

        List<Cobranca> filtradas = new ArrayList<>();
        if (cobrancas != null && assinaturas != null) {
            Map<Integer, Assinatura> assinaturasPorId = new HashMap<>();
            for (Assinatura assinatura : assinaturas) {
                if (assinatura != null) {
                    assinaturasPorId.put(assinatura.getId(), assinatura);
                }
            }

            boolean usuarioGestor = sessionManager.isGestor();
            int usuarioId = sessionManager.obterUsuarioId();

            for (Cobranca cobranca : cobrancas) {
                if (cobranca == null) {
                    continue;
                }
                Assinatura assinatura = assinaturasPorId.get(cobranca.getAssinaturaId());
                if (assinatura == null || !assinatura.isStatus()) {
                    continue;
                }
                boolean visivel = usuarioGestor
                        || DomainConstants.USO_GERAL.equalsIgnoreCase(assinatura.getTipoDoUso())
                        || assinatura.getDonoId() == usuarioId;
                if (!visivel) {
                    continue;
                }
                filtradas.add(cobranca);
            }
        }

        historicoNotificacoesVisiveisLiveData.setValue(filtradas);
        recalcularQuantidadeNaoLidas(filtradas);

        long inicioDeHoje = calcularInicioDeHoje();
        int atrasadas = 0;
        for (Cobranca cobranca : filtradas) {
            if (cobranca == null) {
                continue;
            }
            String status = cobranca.getStatusPagamento();
            if (DomainConstants.COB_ATRASADA.equals(status)
                    || (!DomainConstants.COB_PAGA.equals(status)
                    && cobranca.getDataVencimento() < inicioDeHoje)) {
                atrasadas++;
            }
        }
        quantidadeAtrasadasLiveData.setValue(atrasadas);
    }

    private void recalcularQuantidadeNaoLidas(@NonNull List<Cobranca> filtradas) {
        int naoLidas = 0;
        for (Cobranca cobranca : filtradas) {
            if (cobranca != null && !isNotificacaoVisualizada(cobranca)) {
                naoLidas++;
            }
        }
        quantidadeNaoLidasLiveData.setValue(naoLidas);
    }

    @NonNull
    private String gerarChaveNotificacao(@NonNull Cobranca cobranca) {
        return cobranca.getId() + "_" + cobranca.getStatusPagamento()
                + "_" + cobranca.getDataVencimento();
    }

    private long calcularInicioDeHoje() {
        Calendar calendario = Calendar.getInstance();
        calendario.set(Calendar.HOUR_OF_DAY, 0);
        calendario.set(Calendar.MINUTE, 0);
        calendario.set(Calendar.SECOND, 0);
        calendario.set(Calendar.MILLISECOND, 0);
        return calendario.getTimeInMillis();
    }

    private long calcularFimDeAmanha() {
        Calendar calendario = Calendar.getInstance();
        calendario.add(Calendar.DAY_OF_MONTH, 1);
        calendario.set(Calendar.HOUR_OF_DAY, 23);
        calendario.set(Calendar.MINUTE, 59);
        calendario.set(Calendar.SECOND, 59);
        calendario.set(Calendar.MILLISECOND, 999);
        return calendario.getTimeInMillis();
    }

    @NonNull
    private String obterFamiliaIdAtivo() {
        String familiaId = sessionManager.obterFamiliaId();
        return !TextUtils.isEmpty(familiaId) ? familiaId : AppDatabase.FAMILIA_ID_PADRAO;
    }
}
