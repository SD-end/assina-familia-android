package com.example.assinafamilia.data.repository;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.assinafamilia.data.AppDatabase;
import com.example.assinafamilia.data.AssinaturaDao;
import com.example.assinafamilia.data.local.dao.CategoriaDao;
import com.example.assinafamilia.data.local.dao.CobrancaDao;
import com.example.assinafamilia.data.local.dao.UsuarioDao;
import com.example.assinafamilia.data.remote.firebase.FirestoreSource;
import com.example.assinafamilia.model.Assinatura;
import com.example.assinafamilia.model.Categoria;
import com.example.assinafamilia.model.Cobranca;
import com.example.assinafamilia.model.Usuario;
import com.example.assinafamilia.util.DomainConstants;
import com.example.assinafamilia.util.NetworkUtils;
import com.example.assinafamilia.util.Resource;
import com.example.assinafamilia.util.SessionManager;
import com.example.assinafamilia.worker.WorkScheduler;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class AssinaturaRepository {

    private static final long TEMPO_LIMITE_SEGUNDOS = 30L;
    private static final int TAMANHO_MAXIMO_NOME_SERVICO = 120;
    private static final String MENSAGEM_SESSAO_INVALIDA = "Sessão expirada. Faça login novamente.";
    private static final String MENSAGEM_PERMISSAO_NEGADA =
            "Permissão negada. Você só pode gerenciar suas próprias assinaturas individuais.";
    private static final String MENSAGEM_ASSINATURA_NAO_ENCONTRADA = "Assinatura não encontrada.";
    private static final String MENSAGEM_SINCRONIZACAO_PENDENTE =
            "Alteração salva localmente. Sincronização com a nuvem pendente.";

    private final Context contexto;
    private final AppDatabase banco;
    private final AssinaturaDao assinaturaDao;
    private final CobrancaDao cobrancaDao;
    private final UsuarioDao usuarioDao;
    private final CategoriaDao categoriaDao;
    private final FirestoreSource firestoreSource;
    private final SessionManager sessionManager;

    public AssinaturaRepository(@NonNull Context contexto) {
        this.contexto = contexto.getApplicationContext();
        this.banco = AppDatabase.getInstance(this.contexto);
        this.assinaturaDao = banco.assinaturaDao();
        this.cobrancaDao = banco.cobrancaDao();
        this.usuarioDao = banco.usuarioDao();
        this.categoriaDao = banco.categoriaDao();
        this.firestoreSource = new FirestoreSource(this.contexto);
        this.sessionManager = new SessionManager(this.contexto);
    }

    @NonNull
    public LiveData<List<Assinatura>> listarTodasPorFamilia(@NonNull String familiaId) {
        return assinaturaDao.listarTodasPorFamilia(familiaId);
    }

    @NonNull
    public LiveData<List<Assinatura>> listarParaMembro(@NonNull String familiaId, int usuarioId) {
        return assinaturaDao.listarParaMembro(familiaId, usuarioId);
    }

    @NonNull
    public LiveData<List<Assinatura>> listarAtivasPorFamilia(@NonNull String familiaId) {
        return assinaturaDao.listarAtivasPorFamilia(familiaId);
    }

    @NonNull
    public LiveData<Assinatura> buscarPorId(int id) {
        return assinaturaDao.buscarPorId(id);
    }

    @NonNull
    public LiveData<List<Cobranca>> listarCobrancasPorAssinatura(int assinaturaId) {
        return cobrancaDao.listarPorAssinatura(assinaturaId);
    }

    @NonNull
    public LiveData<List<Cobranca>> listarProximasCobrancasFamilia(@NonNull String familiaId) {
        return cobrancaDao.listarProximasCobrancasFamilia(familiaId);
    }

    @NonNull
    public LiveData<Resource<Assinatura>> salvarAssinatura(@NonNull Assinatura assinatura) {
        MutableLiveData<Resource<Assinatura>> resultado =
                new MutableLiveData<>(Resource.carregando(null));

        AppDatabase.databaseWriteExecutor.execute(() -> {
            String familiaId = sessionManager.obterFamiliaId();
            if (!sessionManager.isLogado() || TextUtils.isEmpty(familiaId)) {
                resultado.postValue(Resource.erro(MENSAGEM_SESSAO_INVALIDA, null));
                return;
            }

            boolean isGestor = sessionManager.isGestor();
            int usuarioId = sessionManager.obterUsuarioId();
            boolean criacao = assinatura.getId() <= 0;

            Assinatura existente = null;
            if (!criacao) {
                existente = assinaturaDao.buscarPorIdSync(assinatura.getId());
                if (existente == null) {
                    resultado.postValue(Resource.erro(MENSAGEM_ASSINATURA_NAO_ENCONTRADA, null));
                    return;
                }
                if (!isGestor && !pertenceAoUsuario(existente, usuarioId)) {
                    resultado.postValue(Resource.erro(MENSAGEM_PERMISSAO_NEGADA, null));
                    return;
                }
            }

            if (!isGestor) {
                assinatura.setTipoDoUso(DomainConstants.USO_INDIVIDUAL);
                assinatura.setDonoId(usuarioId);
            } else if (assinatura.getDonoId() <= 0) {
                assinatura.setDonoId(usuarioId);
            }

            normalizarAssinatura(assinatura);
            String erroValidacao = validarAssinatura(assinatura);
            if (erroValidacao != null) {
                resultado.postValue(Resource.erro(erroValidacao, null));
                return;
            }

            assinatura.setFamiliaId(familiaId);
            if (TextUtils.isEmpty(assinatura.getFirestoreId())) {
                assinatura.setFirestoreId(existente != null && !TextUtils.isEmpty(existente.getFirestoreId())
                        ? existente.getFirestoreId()
                        : gerarIdRemoto());
            }
            assinatura.setSincronizado(false);

            List<Cobranca> cobrancasCriadas = new ArrayList<>();
            try {
                banco.runInTransaction(() -> {
                    if (criacao) {
                        assinatura.setId(0);
                        assinatura.setId((int) assinaturaDao.inserir(assinatura));
                        Cobranca primeiraCobranca = new Cobranca(
                                assinatura.getId(),
                                assinatura.getDataProximoVencimento(),
                                assinatura.getValorTotal(),
                                gerarIdRemoto(),
                                familiaId);
                        primeiraCobranca.setId((int) cobrancaDao.inserir(primeiraCobranca));
                        cobrancasCriadas.add(primeiraCobranca);
                    } else {
                        assinaturaDao.atualizar(assinatura);
                    }
                });
            } catch (Exception excecao) {
                resultado.postValue(Resource.erro(
                        "Não foi possível salvar a assinatura neste dispositivo.", null));
                return;
            }

            WorkScheduler.executarVerificacaoVencimentosImediata(contexto);

            if (AppDatabase.FAMILIA_ID_PADRAO.equals(familiaId)) {
                resultado.postValue(Resource.sucesso(assinatura));
                return;
            }

            if (enviarAssinaturaECobrancas(familiaId, assinatura, cobrancasCriadas)) {
                resultado.postValue(Resource.sucesso(assinatura));
            } else {
                WorkScheduler.enfileirarSincronizacaoQuandoOnline(contexto);
                resultado.postValue(Resource.sucessoComFallbackOffline(
                        assinatura, MENSAGEM_SINCRONIZACAO_PENDENTE));
            }
        });

        return resultado;
    }

    @NonNull
    public LiveData<Resource<Boolean>> alterarStatusAssinatura(@NonNull Assinatura assinatura,
                                                               boolean ativa) {
        MutableLiveData<Resource<Boolean>> resultado =
                new MutableLiveData<>(Resource.carregando(null));

        AppDatabase.databaseWriteExecutor.execute(() -> {
            if (!sessionManager.isLogado()) {
                resultado.postValue(Resource.erro(MENSAGEM_SESSAO_INVALIDA, false));
                return;
            }

            Assinatura atual = assinaturaDao.buscarPorIdSync(assinatura.getId());
            if (atual == null) {
                resultado.postValue(Resource.erro(MENSAGEM_ASSINATURA_NAO_ENCONTRADA, false));
                return;
            }
            if (!podeGerenciar(atual)) {
                resultado.postValue(Resource.erro(MENSAGEM_PERMISSAO_NEGADA, false));
                return;
            }

            String familiaId = resolverFamiliaId(atual.getFamiliaId());
            atual.setFamiliaId(familiaId);
            if (TextUtils.isEmpty(atual.getFirestoreId())) {
                atual.setFirestoreId(gerarIdRemoto());
            }
            atual.setStatus(ativa);
            atual.setSincronizado(false);
            assinaturaDao.atualizar(atual);
            assinatura.setStatus(ativa);
            assinatura.setSincronizado(false);

            if (ativa) {
                WorkScheduler.executarVerificacaoVencimentosImediata(contexto);
            }

            if (isFamiliaSomenteLocal(familiaId)) {
                resultado.postValue(Resource.sucesso(true));
                return;
            }

            if (enviarAssinaturaECobrancas(familiaId, atual, Collections.emptyList())) {
                assinatura.setSincronizado(true);
                resultado.postValue(Resource.sucesso(true));
            } else {
                WorkScheduler.enfileirarSincronizacaoQuandoOnline(contexto);
                resultado.postValue(Resource.sucessoComFallbackOffline(
                        true, MENSAGEM_SINCRONIZACAO_PENDENTE));
            }
        });

        return resultado;
    }

    @NonNull
    public LiveData<Resource<Boolean>> excluirAssinatura(@NonNull Assinatura assinatura) {
        MutableLiveData<Resource<Boolean>> resultado =
                new MutableLiveData<>(Resource.carregando(null));

        AppDatabase.databaseWriteExecutor.execute(() -> {
            if (!sessionManager.isLogado()) {
                resultado.postValue(Resource.erro(MENSAGEM_SESSAO_INVALIDA, false));
                return;
            }

            Assinatura atual = assinaturaDao.buscarPorIdSync(assinatura.getId());
            if (atual == null) {
                resultado.postValue(Resource.erro(MENSAGEM_ASSINATURA_NAO_ENCONTRADA, false));
                return;
            }
            if (!podeGerenciar(atual)) {
                resultado.postValue(Resource.erro(MENSAGEM_PERMISSAO_NEGADA, false));
                return;
            }

            String familiaId = resolverFamiliaId(atual.getFamiliaId());
            if (isFamiliaSomenteLocal(familiaId) || TextUtils.isEmpty(atual.getFirestoreId())) {
                assinaturaDao.deletar(atual);
                resultado.postValue(Resource.sucesso(true));
                return;
            }

            atual.setFamiliaId(familiaId);
            atual.setStatus(false);
            if (enviarAssinatura(familiaId, atual)) {
                assinaturaDao.deletar(atual);
                resultado.postValue(Resource.sucesso(true));
                return;
            }

            atual.setSincronizado(false);
            assinaturaDao.atualizar(atual);
            WorkScheduler.enfileirarSincronizacaoQuandoOnline(contexto);
            resultado.postValue(Resource.sucessoComFallbackOffline(
                    false,
                    "Sem conexão com a nuvem. A assinatura foi inativada e será sincronizada automaticamente."));
        });

        return resultado;
    }

    @NonNull
    public LiveData<Resource<Cobranca>> confirmarPagamentoCobranca(@NonNull Cobranca cobranca) {
        MutableLiveData<Resource<Cobranca>> resultado =
                new MutableLiveData<>(Resource.carregando(null));

        AppDatabase.databaseWriteExecutor.execute(() -> {
            if (!sessionManager.isLogado()) {
                resultado.postValue(Resource.erro(MENSAGEM_SESSAO_INVALIDA, null));
                return;
            }

            Cobranca atual = cobrancaDao.buscarPorId(cobranca.getId());
            if (atual == null) {
                resultado.postValue(Resource.erro("Cobrança não encontrada.", null));
                return;
            }
            if (DomainConstants.COB_PAGA.equals(atual.getStatusPagamento())) {
                resultado.postValue(Resource.erro("Esta cobrança já foi confirmada como paga.", atual));
                return;
            }

            Assinatura assinaturaPai = assinaturaDao.buscarPorIdSync(atual.getAssinaturaId());
            if (assinaturaPai == null) {
                resultado.postValue(Resource.erro(MENSAGEM_ASSINATURA_NAO_ENCONTRADA, null));
                return;
            }

            boolean gerenciaAssinatura = podeGerenciar(assinaturaPai);
            boolean membroEmAssinaturaGeral = !sessionManager.isGestor()
                    && DomainConstants.USO_GERAL.equals(assinaturaPai.getTipoDoUso());
            if (!gerenciaAssinatura && !membroEmAssinaturaGeral) {
                resultado.postValue(Resource.erro(MENSAGEM_PERMISSAO_NEGADA, null));
                return;
            }

            if (!atual.atualizarStatus(DomainConstants.COB_PAGA)) {
                resultado.postValue(Resource.erro("Transição de status não permitida.", null));
                return;
            }

            String familiaId = resolverFamiliaId(assinaturaPai.getFamiliaId());
            if (TextUtils.isEmpty(atual.getFamiliaId())) {
                atual.setFamiliaId(familiaId);
            }
            if (TextUtils.isEmpty(atual.getFirestoreId())) {
                atual.setFirestoreId(gerarIdRemoto());
            }
            atual.setSincronizado(false);

            List<Cobranca> cobrancasProximoCiclo = new ArrayList<>();
            try {
                banco.runInTransaction(() -> {
                    cobrancaDao.atualizar(atual);
                    if (!assinaturaPai.isStatus()) {
                        return;
                    }
                    long dataProximoCiclo = calcularDataProximoCiclo(
                            atual.getDataVencimento(), assinaturaPai.getPeriodicidade());
                    assinaturaPai.setDataProximoVencimento(dataProximoCiclo);
                    if (TextUtils.isEmpty(assinaturaPai.getFirestoreId())) {
                        assinaturaPai.setFirestoreId(gerarIdRemoto());
                    }
                    if (gerenciaAssinatura) {
                        assinaturaPai.setSincronizado(false);
                    }
                    assinaturaDao.atualizar(assinaturaPai);

                    Cobranca proximaCobranca = new Cobranca(
                            assinaturaPai.getId(),
                            dataProximoCiclo,
                            assinaturaPai.getValorTotal(),
                            gerarIdRemoto(),
                            familiaId);
                    proximaCobranca.setId((int) cobrancaDao.inserir(proximaCobranca));
                    cobrancasProximoCiclo.add(proximaCobranca);
                });
            } catch (Exception excecao) {
                resultado.postValue(Resource.erro(
                        "Não foi possível registrar o pagamento neste dispositivo.", null));
                return;
            }

            cobranca.setStatusPagamento(atual.getStatusPagamento());
            cobranca.setSincronizado(false);
            WorkScheduler.executarVerificacaoVencimentosImediata(contexto);

            if (isFamiliaSomenteLocal(familiaId)) {
                resultado.postValue(Resource.sucesso(atual));
                return;
            }

            boolean sincronizado = gerenciaAssinatura
                    ? enviarPagamentoComoResponsavel(familiaId, atual, assinaturaPai, cobrancasProximoCiclo)
                    : enviarPagamentoComoMembro(familiaId, atual, assinaturaPai, cobrancasProximoCiclo);

            if (sincronizado) {
                cobranca.setSincronizado(true);
                resultado.postValue(Resource.sucesso(atual));
            } else {
                WorkScheduler.enfileirarSincronizacaoQuandoOnline(contexto);
                resultado.postValue(Resource.sucessoComFallbackOffline(
                        atual, MENSAGEM_SINCRONIZACAO_PENDENTE));
            }
        });

        return resultado;
    }

    private boolean enviarAssinaturaECobrancas(@NonNull String familiaId,
                                               @NonNull Assinatura assinatura,
                                               @NonNull List<Cobranca> cobrancas) {
        if (!podeSincronizarComNuvem(familiaId)) {
            return false;
        }
        String donoUid = resolverDonoUid(assinatura.getDonoId());
        if (TextUtils.isEmpty(donoUid)) {
            return false;
        }
        try {
            aguardar(firestoreSource.salvarAssinatura(familiaId, assinatura, donoUid,
                    resolverCategoriaFirestoreId(assinatura.getCategoriaId())));
            assinaturaDao.marcarComoSincronizado(assinatura.getId());
            assinatura.setSincronizado(true);
            for (Cobranca cobranca : cobrancas) {
                aguardar(firestoreSource.salvarCobranca(familiaId, cobranca,
                        assinatura.getFirestoreId(), donoUid, assinatura.getTipoDoUso()));
                cobrancaDao.marcarComoSincronizado(cobranca.getId());
                cobranca.setSincronizado(true);
            }
            return true;
        } catch (Exception excecao) {
            return false;
        }
    }

    private boolean enviarAssinatura(@NonNull String familiaId, @NonNull Assinatura assinatura) {
        if (!podeSincronizarComNuvem(familiaId)) {
            return false;
        }
        String donoUid = resolverDonoUid(assinatura.getDonoId());
        if (TextUtils.isEmpty(donoUid)) {
            return false;
        }
        try {
            aguardar(firestoreSource.salvarAssinatura(familiaId, assinatura, donoUid,
                    resolverCategoriaFirestoreId(assinatura.getCategoriaId())));
            return true;
        } catch (Exception excecao) {
            return false;
        }
    }

    private boolean enviarPagamentoComoResponsavel(@NonNull String familiaId,
                                                   @NonNull Cobranca cobrancaPaga,
                                                   @NonNull Assinatura assinaturaPai,
                                                   @NonNull List<Cobranca> cobrancasProximoCiclo) {
        if (!podeSincronizarComNuvem(familiaId)) {
            return false;
        }
        String donoUid = resolverDonoUid(assinaturaPai.getDonoId());
        if (TextUtils.isEmpty(donoUid)) {
            return false;
        }
        try {
            aguardar(firestoreSource.salvarCobranca(familiaId, cobrancaPaga,
                    assinaturaPai.getFirestoreId(), donoUid, assinaturaPai.getTipoDoUso()));
            cobrancaDao.marcarComoSincronizado(cobrancaPaga.getId());
            cobrancaPaga.setSincronizado(true);

            if (!assinaturaPai.isSincronizado()) {
                aguardar(firestoreSource.salvarAssinatura(familiaId, assinaturaPai, donoUid,
                        resolverCategoriaFirestoreId(assinaturaPai.getCategoriaId())));
                assinaturaDao.marcarComoSincronizado(assinaturaPai.getId());
                assinaturaPai.setSincronizado(true);
            }

            for (Cobranca proximaCobranca : cobrancasProximoCiclo) {
                aguardar(firestoreSource.salvarCobranca(familiaId, proximaCobranca,
                        assinaturaPai.getFirestoreId(), donoUid, assinaturaPai.getTipoDoUso()));
                cobrancaDao.marcarComoSincronizado(proximaCobranca.getId());
                proximaCobranca.setSincronizado(true);
            }
            return true;
        } catch (Exception excecao) {
            return false;
        }
    }

    private boolean enviarPagamentoComoMembro(@NonNull String familiaId,
                                              @NonNull Cobranca cobrancaPaga,
                                              @NonNull Assinatura assinaturaPai,
                                              @NonNull List<Cobranca> cobrancasProximoCiclo) {
        if (!podeSincronizarComNuvem(familiaId)) {
            return false;
        }
        String donoUid = resolverDonoUid(assinaturaPai.getDonoId());
        try {
            aguardar(firestoreSource.atualizarStatusCobranca(familiaId, cobrancaPaga.getFirestoreId(),
                    cobrancaPaga.getStatusPagamento(), cobrancaPaga.isNotificado24h()));
            cobrancaDao.marcarComoSincronizado(cobrancaPaga.getId());
            cobrancaPaga.setSincronizado(true);

            for (Cobranca proximaCobranca : cobrancasProximoCiclo) {
                aguardar(firestoreSource.salvarCobranca(familiaId, proximaCobranca,
                        assinaturaPai.getFirestoreId(), donoUid, assinaturaPai.getTipoDoUso()));
                cobrancaDao.marcarComoSincronizado(proximaCobranca.getId());
                proximaCobranca.setSincronizado(true);
            }
            return true;
        } catch (Exception excecao) {
            return false;
        }
    }

    private void normalizarAssinatura(@NonNull Assinatura assinatura) {
        assinatura.setNomeServico(assinatura.getNomeServico() != null
                ? assinatura.getNomeServico().trim()
                : "");
        if (TextUtils.isEmpty(assinatura.getMoeda())) {
            assinatura.setMoeda(DomainConstants.MOEDA_BRL);
        }
        if (TextUtils.isEmpty(assinatura.getPeriodicidade())) {
            assinatura.setPeriodicidade(DomainConstants.PER_MENSAL);
        }
    }

    @Nullable
    private String validarAssinatura(@NonNull Assinatura assinatura) {
        String nomeServico = assinatura.getNomeServico();
        if (nomeServico.isEmpty() || nomeServico.length() > TAMANHO_MAXIMO_NOME_SERVICO) {
            return "Informe o nome do serviço com até 120 caracteres.";
        }
        if (Float.isNaN(assinatura.getValorOriginal()) || assinatura.getValorOriginal() <= 0f) {
            return "O valor da assinatura deve ser maior que zero.";
        }
        if (Float.isNaN(assinatura.getValorTotal()) || assinatura.getValorTotal() <= 0f) {
            return "O valor convertido em reais deve ser maior que zero.";
        }
        if (assinatura.getCategoriaId() <= 0) {
            return "Selecione uma categoria.";
        }
        if (assinatura.getDataProximoVencimento() <= 0L) {
            return "Informe a data do próximo vencimento.";
        }
        if (!DomainConstants.USO_GERAL.equals(assinatura.getTipoDoUso())
                && !DomainConstants.USO_INDIVIDUAL.equals(assinatura.getTipoDoUso())) {
            return "Selecione o tipo de uso da assinatura.";
        }
        if (!DomainConstants.MOEDA_BRL.equals(assinatura.getMoeda())
                && !DomainConstants.MOEDA_USD.equals(assinatura.getMoeda())
                && !DomainConstants.MOEDA_EUR.equals(assinatura.getMoeda())) {
            return "Moeda não suportada.";
        }
        if (!DomainConstants.PER_MENSAL.equals(assinatura.getPeriodicidade())
                && !DomainConstants.PER_ANUAL.equals(assinatura.getPeriodicidade())) {
            return "Periodicidade inválida.";
        }
        if (categoriaDao.buscarPorId(assinatura.getCategoriaId()) == null) {
            return "Categoria selecionada não encontrada.";
        }
        if (usuarioDao.buscarPorId(assinatura.getDonoId()) == null) {
            return "Responsável pela assinatura não encontrado.";
        }
        return null;
    }

    private boolean podeGerenciar(@NonNull Assinatura assinatura) {
        return sessionManager.isGestor()
                || pertenceAoUsuario(assinatura, sessionManager.obterUsuarioId());
    }

    private static boolean pertenceAoUsuario(@NonNull Assinatura assinatura, int usuarioId) {
        return DomainConstants.USO_INDIVIDUAL.equals(assinatura.getTipoDoUso())
                && assinatura.getDonoId() == usuarioId;
    }

    @Nullable
    private String resolverDonoUid(int donoId) {
        Usuario dono = usuarioDao.buscarPorId(donoId);
        if (dono != null && !TextUtils.isEmpty(dono.getFirestoreUid())) {
            return dono.getFirestoreUid();
        }
        if (donoId == sessionManager.obterUsuarioId()) {
            return sessionManager.obterFirestoreUid();
        }
        return null;
    }

    @Nullable
    private String resolverCategoriaFirestoreId(int categoriaId) {
        Categoria categoria = categoriaDao.buscarPorId(categoriaId);
        if (categoria == null || TextUtils.isEmpty(categoria.getFirestoreId())) {
            return null;
        }
        return categoria.getFirestoreId();
    }

    @NonNull
    private String resolverFamiliaId(@Nullable String familiaIdRegistro) {
        if (!TextUtils.isEmpty(familiaIdRegistro)) {
            return familiaIdRegistro;
        }
        String familiaIdSessao = sessionManager.obterFamiliaId();
        return familiaIdSessao != null ? familiaIdSessao : "";
    }

    private boolean podeSincronizarComNuvem(@Nullable String familiaId) {
        return !isFamiliaSomenteLocal(familiaId)
                && NetworkUtils.temConexaoInternet(contexto)
                && firestoreSource.isFirebaseDisponivel()
                && !TextUtils.isEmpty(sessionManager.obterFirestoreUid());
    }

    private static boolean isFamiliaSomenteLocal(@Nullable String familiaId) {
        return TextUtils.isEmpty(familiaId) || AppDatabase.FAMILIA_ID_PADRAO.equals(familiaId);
    }

    private static long calcularDataProximoCiclo(long dataBase, @Nullable String periodicidade) {
        Calendar calendario = Calendar.getInstance();
        calendario.setTimeInMillis(dataBase);
        if (DomainConstants.PER_ANUAL.equals(periodicidade)) {
            calendario.add(Calendar.YEAR, 1);
        } else {
            calendario.add(Calendar.MONTH, 1);
        }
        return calendario.getTimeInMillis();
    }

    @NonNull
    private static String gerarIdRemoto() {
        return UUID.randomUUID().toString();
    }

    private static <T> T aguardar(@NonNull Task<T> tarefa) throws Exception {
        try {
            return Tasks.await(tarefa, TEMPO_LIMITE_SEGUNDOS, TimeUnit.SECONDS);
        } catch (InterruptedException excecao) {
            Thread.currentThread().interrupt();
            throw excecao;
        }
    }
}
