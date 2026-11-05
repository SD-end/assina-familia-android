package com.example.assinafamilia.data.repository;

import android.content.Context;
import android.database.sqlite.SQLiteConstraintException;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.assinafamilia.data.AppDatabase;
import com.example.assinafamilia.data.AssinaturaDao;
import com.example.assinafamilia.data.local.dao.CategoriaDao;
import com.example.assinafamilia.data.remote.firebase.FirestoreSource;
import com.example.assinafamilia.model.Categoria;
import com.example.assinafamilia.util.NetworkUtils;
import com.example.assinafamilia.util.Resource;
import com.example.assinafamilia.util.SessionManager;
import com.example.assinafamilia.worker.WorkScheduler;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class CategoriaRepository {

    private static final long TEMPO_LIMITE_SEGUNDOS = 30L;
    private static final int TAMANHO_MAXIMO_NOME = 100;
    private static final int TAMANHO_MAXIMO_DESCRICAO = 300;
    private static final String MENSAGEM_PERMISSAO_NEGADA =
            "Permissão negada. Apenas o gestor da família pode gerenciar categorias.";
    private static final String MENSAGEM_BLOQUEIO_RN04 =
            "Esta categoria possui assinaturas ativas vinculadas e não pode ser excluída. Você pode apenas inativá-la.";
    private static final String MENSAGEM_SINCRONIZACAO_PENDENTE =
            "Alteração salva localmente. Sincronização com a nuvem pendente.";

    private final Context contexto;
    private final CategoriaDao categoriaDao;
    private final AssinaturaDao assinaturaDao;
    private final FirestoreSource firestoreSource;
    private final SessionManager sessionManager;

    public CategoriaRepository(@NonNull Context contexto) {
        this.contexto = contexto.getApplicationContext();
        AppDatabase banco = AppDatabase.getInstance(this.contexto);
        this.categoriaDao = banco.categoriaDao();
        this.assinaturaDao = banco.assinaturaDao();
        this.firestoreSource = new FirestoreSource(this.contexto);
        this.sessionManager = new SessionManager(this.contexto);
    }

    @NonNull
    public LiveData<List<Categoria>> listarTodas(@NonNull String familiaId) {
        return categoriaDao.listarTodas(familiaId);
    }

    @NonNull
    public LiveData<List<Categoria>> listarAtivas(@NonNull String familiaId) {
        return categoriaDao.listarAtivas(familiaId);
    }

    @NonNull
    public LiveData<Resource<Categoria>> salvarCategoria(@NonNull Categoria categoria) {
        MutableLiveData<Resource<Categoria>> resultado =
                new MutableLiveData<>(Resource.carregando(null));

        AppDatabase.databaseWriteExecutor.execute(() -> {
            if (!sessionManager.isGestor()) {
                resultado.postValue(Resource.erro(MENSAGEM_PERMISSAO_NEGADA, null));
                return;
            }

            String nomeLimpo = categoria.getNome() != null ? categoria.getNome().trim() : "";
            String descricaoLimpa = categoria.getDescricao() != null ? categoria.getDescricao().trim() : "";
            if (nomeLimpo.isEmpty() || nomeLimpo.length() > TAMANHO_MAXIMO_NOME) {
                resultado.postValue(Resource.erro(
                        "Informe um nome de categoria com até 100 caracteres.", null));
                return;
            }
            if (descricaoLimpa.length() > TAMANHO_MAXIMO_DESCRICAO) {
                resultado.postValue(Resource.erro(
                        "A descrição deve ter no máximo 300 caracteres.", null));
                return;
            }

            String familiaId = sessionManager.obterFamiliaId();
            categoria.setNome(nomeLimpo);
            categoria.setDescricao(descricaoLimpa);
            categoria.setFamiliaId(familiaId);
            if (TextUtils.isEmpty(categoria.getFirestoreId())) {
                categoria.setFirestoreId(UUID.randomUUID().toString());
            }
            categoria.setSincronizado(false);

            try {
                if (categoria.getId() == 0) {
                    categoria.setId((int) categoriaDao.inserir(categoria));
                } else {
                    categoriaDao.atualizar(categoria);
                }
            } catch (Exception excecao) {
                resultado.postValue(Resource.erro(
                        "Não foi possível salvar a categoria neste dispositivo.", null));
                return;
            }

            if (isFamiliaSomenteLocal(familiaId)) {
                resultado.postValue(Resource.sucesso(categoria));
                return;
            }

            if (enviarCategoriaParaNuvem(familiaId, categoria)) {
                resultado.postValue(Resource.sucesso(categoria));
            } else {
                resultado.postValue(Resource.sucessoComFallbackOffline(
                        categoria, MENSAGEM_SINCRONIZACAO_PENDENTE));
            }
        });

        return resultado;
    }

    @NonNull
    public LiveData<Resource<Boolean>> alterarStatusCategoria(@NonNull Categoria categoria,
                                                              boolean ativa) {
        MutableLiveData<Resource<Boolean>> resultado =
                new MutableLiveData<>(Resource.carregando(null));

        AppDatabase.databaseWriteExecutor.execute(() -> {
            if (!sessionManager.isGestor()) {
                resultado.postValue(Resource.erro(MENSAGEM_PERMISSAO_NEGADA, false));
                return;
            }

            categoriaDao.alterarStatusAtiva(categoria.getId(), ativa);
            categoria.setAtiva(ativa);
            categoria.setSincronizado(false);

            Categoria atualizada = garantirIdentificadoresRemotos(categoria);
            String familiaId = atualizada.getFamiliaId();

            if (isFamiliaSomenteLocal(familiaId)) {
                resultado.postValue(Resource.sucesso(true));
                return;
            }

            if (enviarCategoriaParaNuvem(familiaId, atualizada)) {
                categoria.setSincronizado(true);
                resultado.postValue(Resource.sucesso(true));
            } else {
                resultado.postValue(Resource.sucessoComFallbackOffline(
                        true, MENSAGEM_SINCRONIZACAO_PENDENTE));
            }
        });

        return resultado;
    }

    @NonNull
    public LiveData<Resource<Boolean>> excluirCategoria(@NonNull Categoria categoria) {
        MutableLiveData<Resource<Boolean>> resultado =
                new MutableLiveData<>(Resource.carregando(null));

        AppDatabase.databaseWriteExecutor.execute(() -> {
            if (!sessionManager.isGestor()) {
                resultado.postValue(Resource.erro(MENSAGEM_PERMISSAO_NEGADA, false));
                return;
            }

            if (assinaturaDao.contarAssinaturasAtivasPorCategoria(categoria.getId()) > 0) {
                resultado.postValue(Resource.erro(MENSAGEM_BLOQUEIO_RN04, false));
                return;
            }

            Categoria atual = garantirIdentificadoresRemotos(categoria);
            String familiaId = atual.getFamiliaId();

            if (isFamiliaSomenteLocal(familiaId)) {
                resultado.postValue(Resource.sucesso(excluirOuInativarLocalmente(atual)));
                return;
            }

            atual.setAtiva(false);
            if (!enviarInativacaoParaNuvem(familiaId, atual)) {
                categoriaDao.alterarStatusAtiva(atual.getId(), false);
                WorkScheduler.enfileirarSincronizacaoQuandoOnline(contexto);
                resultado.postValue(Resource.sucessoComFallbackOffline(
                        false,
                        "Sem conexão com a nuvem. A categoria foi inativada e será sincronizada automaticamente."));
                return;
            }

            boolean excluidaFisicamente = excluirOuInativarLocalmente(atual);
            if (!excluidaFisicamente) {
                categoriaDao.marcarComoSincronizado(atual.getId());
            }
            resultado.postValue(Resource.sucesso(excluidaFisicamente));
        });

        return resultado;
    }

    private boolean excluirOuInativarLocalmente(@NonNull Categoria categoria) {
        try {
            categoriaDao.deletar(categoria);
            return true;
        } catch (SQLiteConstraintException excecao) {
            categoriaDao.alterarStatusAtiva(categoria.getId(), false);
            categoria.setAtiva(false);
            return false;
        }
    }

    @NonNull
    private Categoria garantirIdentificadoresRemotos(@NonNull Categoria categoria) {
        Categoria atual = categoriaDao.buscarPorId(categoria.getId());
        if (atual == null) {
            atual = categoria;
        }
        boolean precisaAtualizar = false;
        if (TextUtils.isEmpty(atual.getFamiliaId())) {
            atual.setFamiliaId(sessionManager.obterFamiliaId());
            precisaAtualizar = true;
        }
        if (TextUtils.isEmpty(atual.getFirestoreId())) {
            atual.setFirestoreId(UUID.randomUUID().toString());
            precisaAtualizar = true;
        }
        if (precisaAtualizar && atual.getId() > 0) {
            atual.setSincronizado(false);
            categoriaDao.atualizar(atual);
        }
        categoria.setFamiliaId(atual.getFamiliaId());
        categoria.setFirestoreId(atual.getFirestoreId());
        return atual;
    }

    private boolean enviarCategoriaParaNuvem(@NonNull String familiaId, @NonNull Categoria categoria) {
        if (!podeSincronizarComNuvem()) {
            WorkScheduler.enfileirarSincronizacaoQuandoOnline(contexto);
            return false;
        }
        try {
            aguardar(firestoreSource.salvarCategoria(familiaId, categoria));
            categoriaDao.marcarComoSincronizado(categoria.getId());
            categoria.setSincronizado(true);
            return true;
        } catch (Exception excecao) {
            WorkScheduler.enfileirarSincronizacaoQuandoOnline(contexto);
            return false;
        }
    }

    private boolean enviarInativacaoParaNuvem(@NonNull String familiaId, @NonNull Categoria categoria) {
        if (!podeSincronizarComNuvem()) {
            return false;
        }
        try {
            aguardar(firestoreSource.salvarCategoria(familiaId, categoria));
            return true;
        } catch (Exception excecao) {
            return false;
        }
    }

    private boolean podeSincronizarComNuvem() {
        return NetworkUtils.temConexaoInternet(contexto)
                && firestoreSource.isFirebaseDisponivel()
                && !TextUtils.isEmpty(sessionManager.obterFirestoreUid());
    }

    private static boolean isFamiliaSomenteLocal(@Nullable String familiaId) {
        return TextUtils.isEmpty(familiaId) || AppDatabase.FAMILIA_ID_PADRAO.equals(familiaId);
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
