package com.example.assinafamilia.worker;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

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
import com.example.assinafamilia.util.SessionManager;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class SyncWorker extends Worker {

    private static final long TEMPO_LIMITE_SEGUNDOS = 30L;

    private SessionManager sessionManager;
    private FirestoreSource firestoreSource;
    private UsuarioDao usuarioDao;
    private CategoriaDao categoriaDao;
    private AssinaturaDao assinaturaDao;
    private CobrancaDao cobrancaDao;
    private String familiaId;
    private String uidLogado;
    private boolean isGestor;

    public SyncWorker(@NonNull Context contexto, @NonNull WorkerParameters parametros) {
        super(contexto, parametros);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context contexto = getApplicationContext();
        sessionManager = new SessionManager(contexto);
        familiaId = sessionManager.obterFamiliaId();
        uidLogado = sessionManager.obterFirestoreUid();

        if (!sessionManager.isLogado()
                || TextUtils.isEmpty(familiaId)
                || TextUtils.isEmpty(uidLogado)
                || AppDatabase.FAMILIA_ID_PADRAO.equals(familiaId)) {
            return Result.success();
        }

        firestoreSource = new FirestoreSource(contexto);
        if (!firestoreSource.isFirebaseDisponivel()) {
            return Result.success();
        }
        if (!NetworkUtils.temConexaoInternet(contexto)) {
            return Result.retry();
        }

        AppDatabase banco = AppDatabase.getInstance(contexto);
        usuarioDao = banco.usuarioDao();
        categoriaDao = banco.categoriaDao();
        assinaturaDao = banco.assinaturaDao();
        cobrancaDao = banco.cobrancaDao();
        isGestor = sessionManager.isGestor();

        boolean houveFalha;
        try {
            houveFalha = !sincronizarUsuarios();
            houveFalha |= !sincronizarCategorias();
            houveFalha |= !sincronizarAssinaturas();
            houveFalha |= !sincronizarCobrancas();
        } catch (Exception excecao) {
            return Result.retry();
        }

        if (houveFalha) {
            return Result.retry();
        }
        sessionManager.salvarTokenSync(System.currentTimeMillis());
        return Result.success();
    }

    private boolean sincronizarUsuarios() {
        boolean semFalhas = true;
        List<Usuario> pendentes = usuarioDao.listarPendentesSincronizacao();
        if (pendentes == null) {
            return true;
        }
        for (Usuario usuario : pendentes) {
            if (!pertenceAFamiliaSessao(usuario.getFamiliaId())) {
                continue;
            }
            if (TextUtils.isEmpty(usuario.getFirestoreUid())) {
                continue;
            }
            if (!isGestor && !uidLogado.equals(usuario.getFirestoreUid())) {
                continue;
            }
            if (TextUtils.isEmpty(usuario.getFamiliaId())) {
                usuario.setFamiliaId(familiaId);
                usuario.setSincronizado(false);
                usuarioDao.atualizar(usuario);
            }
            if (aguardar(firestoreSource.salvarUsuario(familiaId, usuario))) {
                usuarioDao.marcarComoSincronizado(usuario.getId());
            } else {
                semFalhas = false;
            }
        }
        return semFalhas;
    }

    private boolean sincronizarCategorias() {
        if (!isGestor) {
            return true;
        }
        boolean semFalhas = true;
        List<Categoria> pendentes = categoriaDao.listarPendentesSincronizacao();
        if (pendentes == null) {
            return true;
        }
        for (Categoria categoria : pendentes) {
            if (!pertenceAFamiliaSessao(categoria.getFamiliaId())) {
                continue;
            }
            boolean precisaAtualizarLocal = false;
            if (TextUtils.isEmpty(categoria.getFamiliaId())) {
                categoria.setFamiliaId(familiaId);
                precisaAtualizarLocal = true;
            }
            if (TextUtils.isEmpty(categoria.getFirestoreId())) {
                categoria.setFirestoreId(gerarIdRemoto());
                precisaAtualizarLocal = true;
            }
            if (precisaAtualizarLocal) {
                categoria.setSincronizado(false);
                categoriaDao.atualizar(categoria);
            }
            if (aguardar(firestoreSource.salvarCategoria(familiaId, categoria))) {
                categoriaDao.marcarComoSincronizado(categoria.getId());
            } else {
                semFalhas = false;
            }
        }
        return semFalhas;
    }

    private boolean sincronizarAssinaturas() {
        boolean semFalhas = true;
        List<Assinatura> pendentes = assinaturaDao.listarPendentesSincronizacao();
        if (pendentes == null) {
            return true;
        }
        for (Assinatura assinatura : pendentes) {
            if (!pertenceAFamiliaSessao(assinatura.getFamiliaId())) {
                continue;
            }
            String donoUid = resolverDonoUid(assinatura.getDonoId());
            if (!isGestor && !podeMembroEnviarAssinatura(assinatura, donoUid)) {
                continue;
            }
            boolean precisaAtualizarLocal = false;
            if (TextUtils.isEmpty(assinatura.getFamiliaId())) {
                assinatura.setFamiliaId(familiaId);
                precisaAtualizarLocal = true;
            }
            if (TextUtils.isEmpty(assinatura.getFirestoreId())) {
                assinatura.setFirestoreId(gerarIdRemoto());
                precisaAtualizarLocal = true;
            }
            if (precisaAtualizarLocal) {
                assinatura.setSincronizado(false);
                assinaturaDao.atualizar(assinatura);
            }
            String categoriaFirestoreId = resolverCategoriaFirestoreId(assinatura.getCategoriaId());
            if (aguardar(firestoreSource.salvarAssinatura(familiaId, assinatura, donoUid, categoriaFirestoreId))) {
                assinaturaDao.marcarComoSincronizado(assinatura.getId());
            } else {
                semFalhas = false;
            }
        }
        return semFalhas;
    }

    private boolean sincronizarCobrancas() {
        boolean semFalhas = true;
        List<Cobranca> pendentes = cobrancaDao.listarPendentesSincronizacao();
        if (pendentes == null) {
            return true;
        }
        for (Cobranca cobranca : pendentes) {
            if (!pertenceAFamiliaSessao(cobranca.getFamiliaId())) {
                continue;
            }
            Assinatura assinaturaPai = assinaturaDao.buscarPorIdSync(cobranca.getAssinaturaId());
            if (assinaturaPai == null) {
                continue;
            }
            String donoUid = resolverDonoUid(assinaturaPai.getDonoId());
            String tipoDoUso = assinaturaPai.getTipoDoUso();
            if (!isGestor && !podeMembroEnviarCobranca(tipoDoUso, donoUid)) {
                continue;
            }
            if (TextUtils.isEmpty(assinaturaPai.getFirestoreId())) {
                semFalhas = false;
                continue;
            }
            boolean precisaAtualizarLocal = false;
            if (TextUtils.isEmpty(cobranca.getFamiliaId())) {
                cobranca.setFamiliaId(familiaId);
                precisaAtualizarLocal = true;
            }
            if (TextUtils.isEmpty(cobranca.getFirestoreId())) {
                cobranca.setFirestoreId(gerarIdRemoto());
                precisaAtualizarLocal = true;
            }
            if (precisaAtualizarLocal) {
                cobranca.setSincronizado(false);
                cobrancaDao.atualizar(cobranca);
            }
            boolean enviada = aguardar(firestoreSource.salvarCobranca(
                    familiaId, cobranca, assinaturaPai.getFirestoreId(), donoUid, tipoDoUso));
            if (!enviada && !isGestor) {
                enviada = aguardar(firestoreSource.atualizarStatusCobranca(
                        familiaId, cobranca.getFirestoreId(), cobranca.getStatusPagamento(), cobranca.isNotificado24h()));
            }
            if (enviada) {
                cobrancaDao.marcarComoSincronizado(cobranca.getId());
            } else {
                semFalhas = false;
            }
        }
        return semFalhas;
    }

    private boolean pertenceAFamiliaSessao(@Nullable String registroFamiliaId) {
        return TextUtils.isEmpty(registroFamiliaId) || familiaId.equals(registroFamiliaId);
    }

    @NonNull
    private String resolverDonoUid(int donoId) {
        Usuario dono = usuarioDao.buscarPorId(donoId);
        if (dono != null && !TextUtils.isEmpty(dono.getFirestoreUid())) {
            return dono.getFirestoreUid();
        }
        return uidLogado;
    }

    @Nullable
    private String resolverCategoriaFirestoreId(int categoriaId) {
        Categoria categoria = categoriaDao.buscarPorId(categoriaId);
        if (categoria == null || TextUtils.isEmpty(categoria.getFirestoreId())) {
            return null;
        }
        return categoria.getFirestoreId();
    }

    private boolean podeMembroEnviarAssinatura(@NonNull Assinatura assinatura, @NonNull String donoUid) {
        return DomainConstants.USO_INDIVIDUAL.equals(assinatura.getTipoDoUso()) && uidLogado.equals(donoUid);
    }

    private boolean podeMembroEnviarCobranca(@Nullable String tipoDoUso, @NonNull String donoUid) {
        return DomainConstants.USO_GERAL.equals(tipoDoUso) || uidLogado.equals(donoUid);
    }

    private boolean aguardar(@NonNull Task<Void> tarefa) {
        try {
            Tasks.await(tarefa, TEMPO_LIMITE_SEGUNDOS, TimeUnit.SECONDS);
            return true;
        } catch (Exception excecao) {
            if (excecao instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    @NonNull
    private static String gerarIdRemoto() {
        return UUID.randomUUID().toString();
    }
}
