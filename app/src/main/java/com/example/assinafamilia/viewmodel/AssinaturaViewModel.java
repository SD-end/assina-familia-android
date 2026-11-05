package com.example.assinafamilia.viewmodel;

import android.app.Application;
import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.assinafamilia.data.AppDatabase;
import com.example.assinafamilia.data.repository.AssinaturaRepository;
import com.example.assinafamilia.data.repository.CambioRepository;
import com.example.assinafamilia.data.repository.CategoriaRepository;
import com.example.assinafamilia.data.repository.FamiliaRepository;
import com.example.assinafamilia.model.Assinatura;
import com.example.assinafamilia.model.Categoria;
import com.example.assinafamilia.model.Cobranca;
import com.example.assinafamilia.model.Usuario;
import com.example.assinafamilia.util.DomainConstants;
import com.example.assinafamilia.util.Resource;
import com.example.assinafamilia.util.SessionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AssinaturaViewModel extends AndroidViewModel {

    public static final String FILTRO_USO_TODAS = "TODAS";
    public static final String FILTRO_USO_CASA = DomainConstants.USO_GERAL;
    public static final String FILTRO_USO_INDIVIDUAL = DomainConstants.USO_INDIVIDUAL;
    public static final int MORADOR_TODOS = -1;

    private static final int TAMANHO_MAXIMO_NOME_SERVICO = 120;

    private final AssinaturaRepository assinaturaRepository;
    private final CategoriaRepository categoriaRepository;
    private final FamiliaRepository familiaRepository;
    private final CambioRepository cambioRepository;
    private final SessionManager sessionManager;

    private final MutableLiveData<String> termoBuscaLiveData = new MutableLiveData<>("");
    private final MutableLiveData<String> filtroTipoUsoLiveData = new MutableLiveData<>(FILTRO_USO_TODAS);
    private final MutableLiveData<Boolean> mostrarInativasLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> moradorFiltroIdLiveData = new MutableLiveData<>(MORADOR_TODOS);

    private final LiveData<List<Assinatura>> assinaturasFonteLiveData;
    private final LiveData<List<Usuario>> moradoresAtivosLiveData;
    private final LiveData<List<Usuario>> moradoresFamiliaLiveData;
    private final LiveData<List<Categoria>> categoriasAtivasLiveData;
    private final LiveData<List<Categoria>> todasCategoriasLiveData;

    private final MediatorLiveData<List<Assinatura>> assinaturasFiltradasLiveData = new MediatorLiveData<>();
    private final MediatorLiveData<Float> totalMensalFiltradoLiveData = new MediatorLiveData<>(0f);
    private final MediatorLiveData<Integer> quantidadeAtivasFiltradasLiveData = new MediatorLiveData<>(0);

    public AssinaturaViewModel(@NonNull Application application) {
        super(application);
        Context contexto = application.getApplicationContext();
        this.assinaturaRepository = new AssinaturaRepository(contexto);
        this.categoriaRepository = new CategoriaRepository(contexto);
        this.familiaRepository = new FamiliaRepository(contexto);
        this.cambioRepository = new CambioRepository(contexto);
        this.sessionManager = new SessionManager(contexto);

        String familiaId = obterFamiliaIdAtivo();
        if (sessionManager.isGestor()) {
            this.assinaturasFonteLiveData = assinaturaRepository.listarTodasPorFamilia(familiaId);
        } else {
            this.assinaturasFonteLiveData = assinaturaRepository.listarParaMembro(
                    familiaId, sessionManager.obterUsuarioId());
        }
        this.moradoresAtivosLiveData = familiaRepository.listarMoradoresAtivos(familiaId);
        this.moradoresFamiliaLiveData = familiaRepository.listarMoradoresDaFamilia(familiaId);
        this.categoriasAtivasLiveData = categoriaRepository.listarAtivas(familiaId);
        this.todasCategoriasLiveData = categoriaRepository.listarTodas(familiaId);

        assinaturasFiltradasLiveData.addSource(assinaturasFonteLiveData,
                lista -> recalcularAssinaturasFiltradas());
        assinaturasFiltradasLiveData.addSource(termoBuscaLiveData,
                termo -> recalcularAssinaturasFiltradas());
        assinaturasFiltradasLiveData.addSource(filtroTipoUsoLiveData,
                tipo -> recalcularAssinaturasFiltradas());
        assinaturasFiltradasLiveData.addSource(mostrarInativasLiveData,
                inativas -> recalcularAssinaturasFiltradas());
        assinaturasFiltradasLiveData.addSource(moradorFiltroIdLiveData,
                moradorId -> recalcularAssinaturasFiltradas());

        totalMensalFiltradoLiveData.addSource(assinaturasFiltradasLiveData,
                filtradas -> recalcularTotalMensalFiltrado());
        totalMensalFiltradoLiveData.addSource(moradoresAtivosLiveData,
                moradores -> recalcularTotalMensalFiltrado());

        quantidadeAtivasFiltradasLiveData.addSource(assinaturasFiltradasLiveData,
                filtradas -> quantidadeAtivasFiltradasLiveData.setValue(
                        filtradas != null ? filtradas.size() : 0));
    }

    @NonNull
    public LiveData<List<Assinatura>> observarAssinaturasFiltradas() {
        return assinaturasFiltradasLiveData;
    }

    @NonNull
    public LiveData<Float> observarTotalMensalFiltrado() {
        return totalMensalFiltradoLiveData;
    }

    @NonNull
    public LiveData<Integer> observarQuantidadeAtivasFiltradas() {
        return quantidadeAtivasFiltradasLiveData;
    }

    public void definirTermoBusca(@Nullable String termo) {
        String limpo = termo != null ? termo.trim() : "";
        if (!limpo.equals(termoBuscaLiveData.getValue())) {
            termoBuscaLiveData.setValue(limpo);
        }
    }

    public void definirFiltroTipoUso(@Nullable String tipoUso) {
        String normalizado = normalizarFiltroTipoUso(tipoUso);
        if (!normalizado.equals(filtroTipoUsoLiveData.getValue())) {
            filtroTipoUsoLiveData.setValue(normalizado);
        }
    }

    public void definirMostrarInativas(boolean mostrarInativas) {
        Boolean atual = mostrarInativasLiveData.getValue();
        if (atual == null || atual != mostrarInativas) {
            mostrarInativasLiveData.setValue(mostrarInativas);
        }
    }

    public void definirMoradorFiltroId(int usuarioId) {
        Integer atual = moradorFiltroIdLiveData.getValue();
        if (atual == null || atual != usuarioId) {
            moradorFiltroIdLiveData.setValue(usuarioId);
        }
    }

    @NonNull
    public String obterFiltroTipoUsoAtual() {
        return normalizarFiltroTipoUso(filtroTipoUsoLiveData.getValue());
    }

    public boolean isMostrarInativas() {
        Boolean valor = mostrarInativasLiveData.getValue();
        return valor != null && valor;
    }

    public int obterMoradorFiltroId() {
        Integer valor = moradorFiltroIdLiveData.getValue();
        return valor != null ? valor : MORADOR_TODOS;
    }

    @NonNull
    public LiveData<List<Categoria>> listarCategoriasAtivas() {
        return categoriasAtivasLiveData;
    }

    @NonNull
    public LiveData<List<Categoria>> listarTodasCategorias() {
        return todasCategoriasLiveData;
    }

    @NonNull
    public LiveData<List<Usuario>> listarMoradoresAtivos() {
        return moradoresAtivosLiveData;
    }

    @NonNull
    public LiveData<List<Usuario>> listarMoradoresDaFamilia() {
        return moradoresFamiliaLiveData;
    }

    @NonNull
    public LiveData<Assinatura> buscarAssinaturaPorId(int id) {
        return assinaturaRepository.buscarPorId(id);
    }

    @NonNull
    public LiveData<List<Cobranca>> listarCobrancasPorAssinatura(int assinaturaId) {
        return assinaturaRepository.listarCobrancasPorAssinatura(assinaturaId);
    }

    @NonNull
    public LiveData<Resource<Float>> obterTaxaCambio(@NonNull String moeda) {
        return cambioRepository.obterTaxaCambio(moeda);
    }

    @NonNull
    public LiveData<Resource<Float>> converterParaBrl(@NonNull String moeda, float valorOriginal) {
        return cambioRepository.converterParaBrl(moeda, valorOriginal);
    }

    public static float converterTextoParaValor(@Nullable String valorTexto) {
        if (valorTexto == null) {
            return -1f;
        }
        String limpo = valorTexto.trim().replace(',', '.');
        if (limpo.isEmpty()) {
            return -1f;
        }
        try {
            float valor = Float.parseFloat(limpo);
            if (Float.isNaN(valor) || Float.isInfinite(valor)) {
                return -1f;
            }
            return valor;
        } catch (NumberFormatException excecao) {
            return -1f;
        }
    }

    @NonNull
    public LiveData<Resource<Assinatura>> salvarAssinatura(@Nullable Assinatura assinaturaExistente,
                                                           @Nullable String nomeServico,
                                                           int categoriaId,
                                                           @Nullable String valorOriginalTexto,
                                                           @Nullable String moeda,
                                                           float taxaCambioAplicada,
                                                           @Nullable String periodicidade,
                                                           @Nullable String tipoDoUso,
                                                           long dataProximoVencimento,
                                                           boolean statusAtiva) {
        String nomeLimpo = nomeServico != null ? nomeServico.trim() : "";
        float valorOriginal = converterTextoParaValor(valorOriginalTexto);

        if (nomeLimpo.isEmpty()
                || nomeLimpo.length() > TAMANHO_MAXIMO_NOME_SERVICO
                || categoriaId <= 0
                || valorOriginal <= 0f
                || taxaCambioAplicada <= 0f
                || dataProximoVencimento <= 0L) {
            return new MutableLiveData<>(
                    Resource.erro("Preencha todos os campos obrigatórios corretamente.", null));
        }

        String moedaDefinitiva = !TextUtils.isEmpty(moeda)
                ? moeda.trim().toUpperCase(Locale.ROOT)
                : DomainConstants.MOEDA_BRL;
        String periodicidadeDefinitiva = !TextUtils.isEmpty(periodicidade)
                ? periodicidade.trim().toUpperCase(Locale.ROOT)
                : DomainConstants.PER_MENSAL;

        boolean usuarioGestor = sessionManager.isGestor();
        int usuarioLogadoId = sessionManager.obterUsuarioId();

        String tipoUsoDefinitivo;
        int donoIdDefinitivo;
        if (!usuarioGestor) {
            tipoUsoDefinitivo = DomainConstants.USO_INDIVIDUAL;
            donoIdDefinitivo = usuarioLogadoId;
        } else {
            tipoUsoDefinitivo = DomainConstants.USO_INDIVIDUAL.equalsIgnoreCase(tipoDoUso)
                    ? DomainConstants.USO_INDIVIDUAL
                    : DomainConstants.USO_GERAL;
            donoIdDefinitivo = assinaturaExistente != null && assinaturaExistente.getDonoId() > 0
                    ? assinaturaExistente.getDonoId()
                    : usuarioLogadoId;
        }

        float valorTotal = DomainConstants.MOEDA_BRL.equalsIgnoreCase(moedaDefinitiva)
                ? valorOriginal
                : (valorOriginal * taxaCambioAplicada);

        Assinatura alvo;
        if (assinaturaExistente != null) {
            alvo = assinaturaExistente;
            alvo.setDonoId(donoIdDefinitivo);
            alvo.setCategoriaId(categoriaId);
            alvo.setNomeServico(nomeLimpo);
            alvo.setValorOriginal(valorOriginal);
            alvo.setMoeda(moedaDefinitiva);
            alvo.setValorTotal(valorTotal);
            alvo.setPeriodicidade(periodicidadeDefinitiva);
            alvo.setTipoDoUso(tipoUsoDefinitivo);
            alvo.setDataProximoVencimento(dataProximoVencimento);
            alvo.setStatus(statusAtiva);
        } else {
            alvo = new Assinatura(
                    donoIdDefinitivo,
                    categoriaId,
                    nomeLimpo,
                    valorOriginal,
                    moedaDefinitiva,
                    valorTotal,
                    periodicidadeDefinitiva,
                    tipoUsoDefinitivo,
                    dataProximoVencimento,
                    statusAtiva,
                    null,
                    obterFamiliaIdAtivo(),
                    false
            );
        }

        return assinaturaRepository.salvarAssinatura(alvo);
    }

    @NonNull
    public LiveData<Resource<Boolean>> alterarStatusAssinatura(@NonNull Assinatura assinatura,
                                                               boolean ativa) {
        return assinaturaRepository.alterarStatusAssinatura(assinatura, ativa);
    }

    @NonNull
    public LiveData<Resource<Boolean>> excluirAssinatura(@NonNull Assinatura assinatura) {
        return assinaturaRepository.excluirAssinatura(assinatura);
    }

    @NonNull
    public LiveData<Resource<Cobranca>> confirmarPagamentoCobranca(@NonNull Cobranca cobranca) {
        return assinaturaRepository.confirmarPagamentoCobranca(cobranca);
    }

    public boolean podeGerenciarAssinatura(@Nullable Assinatura assinatura) {
        if (sessionManager.isGestor()) {
            return true;
        }
        if (assinatura == null) {
            return false;
        }
        return DomainConstants.USO_INDIVIDUAL.equalsIgnoreCase(assinatura.getTipoDoUso())
                && assinatura.getDonoId() == sessionManager.obterUsuarioId();
    }

    public boolean isGestor() {
        return sessionManager.isGestor();
    }

    public int obterUsuarioId() {
        return sessionManager.obterUsuarioId();
    }

    private void recalcularAssinaturasFiltradas() {
        List<Assinatura> fonte = assinaturasFonteLiveData.getValue();
        if (fonte == null) {
            assinaturasFiltradasLiveData.setValue(new ArrayList<>());
            return;
        }

        String termo = termoBuscaLiveData.getValue() != null
                ? termoBuscaLiveData.getValue().trim().toLowerCase(Locale.ROOT)
                : "";
        String tipoUsoFiltro = obterFiltroTipoUsoAtual();
        boolean exibirSomenteInativas = isMostrarInativas();
        int moradorId = obterMoradorFiltroId();
        boolean usuarioGestor = sessionManager.isGestor();
        int usuarioLogadoId = sessionManager.obterUsuarioId();

        List<Assinatura> resultado = new ArrayList<>();
        for (Assinatura assinatura : fonte) {
            if (assinatura == null) {
                continue;
            }

            if (!usuarioGestor
                    && !DomainConstants.USO_GERAL.equalsIgnoreCase(assinatura.getTipoDoUso())
                    && assinatura.getDonoId() != usuarioLogadoId) {
                continue;
            }

            boolean ativaEsperada = !exibirSomenteInativas;
            if (assinatura.isStatus() != ativaEsperada) {
                continue;
            }

            if (!termo.isEmpty()) {
                String nome = assinatura.getNomeServico() != null
                        ? assinatura.getNomeServico().trim().toLowerCase(Locale.ROOT)
                        : "";
                if (!nome.contains(termo)) {
                    continue;
                }
            }

            if (FILTRO_USO_CASA.equals(tipoUsoFiltro)
                    && !DomainConstants.USO_GERAL.equalsIgnoreCase(assinatura.getTipoDoUso())) {
                continue;
            }
            if (FILTRO_USO_INDIVIDUAL.equals(tipoUsoFiltro)
                    && !DomainConstants.USO_INDIVIDUAL.equalsIgnoreCase(assinatura.getTipoDoUso())) {
                continue;
            }

            if (usuarioGestor && moradorId != MORADOR_TODOS) {
                boolean pertenceAoMorador = assinatura.getDonoId() == moradorId
                        || DomainConstants.USO_GERAL.equalsIgnoreCase(assinatura.getTipoDoUso());
                if (!pertenceAoMorador) {
                    continue;
                }
            }

            resultado.add(assinatura);
        }

        assinaturasFiltradasLiveData.setValue(resultado);
    }

    private void recalcularTotalMensalFiltrado() {
        List<Assinatura> filtradas = assinaturasFiltradasLiveData.getValue();
        if (filtradas == null || filtradas.isEmpty()) {
            totalMensalFiltradoLiveData.setValue(0f);
            return;
        }

        boolean usuarioGestor = sessionManager.isGestor();
        int usuarioLogadoId = sessionManager.obterUsuarioId();
        List<Usuario> moradoresAtivos = moradoresAtivosLiveData.getValue();
        int divisorMoradores = Math.max(1, moradoresAtivos != null ? moradoresAtivos.size() : 1);

        float soma = 0f;
        for (Assinatura assinatura : filtradas) {
            if (assinatura == null) {
                continue;
            }
            if (usuarioGestor) {
                soma += assinatura.getValorTotal();
            } else if (DomainConstants.USO_GERAL.equalsIgnoreCase(assinatura.getTipoDoUso())) {
                soma += assinatura.getValorTotal() / divisorMoradores;
            } else if (assinatura.getDonoId() == usuarioLogadoId) {
                soma += assinatura.getValorTotal();
            }
        }

        totalMensalFiltradoLiveData.setValue(soma);
    }

    @NonNull
    private static String normalizarFiltroTipoUso(@Nullable String tipoUso) {
        if (FILTRO_USO_CASA.equalsIgnoreCase(tipoUso)) {
            return FILTRO_USO_CASA;
        }
        if (FILTRO_USO_INDIVIDUAL.equalsIgnoreCase(tipoUso)) {
            return FILTRO_USO_INDIVIDUAL;
        }
        return FILTRO_USO_TODAS;
    }

    @NonNull
    private String obterFamiliaIdAtivo() {
        String familiaId = sessionManager.obterFamiliaId();
        return !TextUtils.isEmpty(familiaId) ? familiaId : AppDatabase.FAMILIA_ID_PADRAO;
    }
}
