package com.example.assinafamilia.data.remote.firebase;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.assinafamilia.model.Assinatura;
import com.example.assinafamilia.model.Categoria;
import com.example.assinafamilia.model.Cobranca;
import com.example.assinafamilia.model.Usuario;
import com.example.assinafamilia.util.DomainConstants;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FirestoreSource {

    public static final String COLECAO_FAMILIAS = "familias";
    public static final String COLECAO_USUARIOS = "usuarios";
    public static final String COLECAO_CATEGORIAS = "categorias";
    public static final String COLECAO_ASSINATURAS = "assinaturas";
    public static final String COLECAO_COBRANCAS = "cobrancas";

    public static final String CAMPO_FAMILIA_ID = "familiaId";
    public static final String CAMPO_FIRESTORE_ID = "firestoreId";
    public static final String CAMPO_NOME = "nome";

    public static final String CAMPO_CRIADOR_UID = "criadorUid";
    public static final String CAMPO_ATIVA = "ativa";
    public static final String CAMPO_DATA_CRIACAO = "dataCriacao";

    public static final String CAMPO_FIRESTORE_UID = "firestoreUid";
    public static final String CAMPO_EMAIL = "email";
    public static final String CAMPO_PERFIL = "perfil";
    public static final String CAMPO_ATIVO = "ativo";

    public static final String CAMPO_DESCRICAO = "descricao";

    public static final String CAMPO_DONO_UID = "donoUid";
    public static final String CAMPO_CATEGORIA_FIRESTORE_ID = "categoriaFirestoreId";
    public static final String CAMPO_NOME_SERVICO = "nomeServico";
    public static final String CAMPO_VALOR_ORIGINAL = "valorOriginal";
    public static final String CAMPO_MOEDA = "moeda";
    public static final String CAMPO_VALOR_TOTAL = "valorTotal";
    public static final String CAMPO_PERIODICIDADE = "periodicidade";
    public static final String CAMPO_TIPO_DO_USO = "tipoDoUso";
    public static final String CAMPO_DATA_PROXIMO_VENCIMENTO = "dataProximoVencimento";
    public static final String CAMPO_STATUS = "status";

    public static final String CAMPO_ASSINATURA_FIRESTORE_ID = "assinaturaFirestoreId";
    public static final String CAMPO_DATA_VENCIMENTO = "dataVencimento";
    public static final String CAMPO_VALOR_COBRADO = "valorCobrado";
    public static final String CAMPO_STATUS_PAGAMENTO = "statusPagamento";
    public static final String CAMPO_NOTIFICADO_24H = "notificado24h";

    private static final String MENSAGEM_FIREBASE_INDISPONIVEL =
            "Firebase indisponível neste dispositivo. Operando em modo offline.";

    private final Context contexto;

    public FirestoreSource(@NonNull Context contexto) {
        this.contexto = contexto.getApplicationContext();
    }

    public boolean isFirebaseDisponivel() {
        return !FirebaseApp.getApps(contexto).isEmpty();
    }

    @Nullable
    private FirebaseFirestore obterFirestore() {
        if (!isFirebaseDisponivel()) {
            return null;
        }
        return FirebaseFirestore.getInstance();
    }

    @NonNull
    private CollectionReference subcolecao(@NonNull FirebaseFirestore db,
                                           @NonNull String familiaId,
                                           @NonNull String colecao) {
        return db.collection(COLECAO_FAMILIAS).document(familiaId).collection(colecao);
    }

    @NonNull
    public Task<Void> criarFamilia(@NonNull String familiaId,
                                   @NonNull String nomeFamilia,
                                   @NonNull String criadorUid) {
        FirebaseFirestore db = obterFirestore();
        if (db == null) {
            return falhaFirebaseIndisponivel();
        }
        return db.collection(COLECAO_FAMILIAS).document(familiaId)
                .set(paraMapaFamilia(nomeFamilia, criadorUid, System.currentTimeMillis()));
    }

    @NonNull
    public Task<Void> criarFamiliaComGestor(@NonNull String familiaId,
                                            @NonNull String nomeFamilia,
                                            @NonNull Usuario gestor) {
        String criadorUid = gestor.getFirestoreUid();
        if (vazio(criadorUid)) {
            return Tasks.forException(
                    new IllegalArgumentException("Gestor sem firestoreUid definido."));
        }
        return criarFamilia(familiaId, nomeFamilia, criadorUid)
                .continueWithTask(tarefa -> {
                    if (!tarefa.isSuccessful()) {
                        return tarefa;
                    }
                    return salvarUsuario(familiaId, gestor);
                });
    }

    @NonNull
    public Task<String> buscarFamiliaIdPorCriador(@NonNull String uid) {
        FirebaseFirestore db = obterFirestore();
        if (db == null) {
            return falhaFirebaseIndisponivel();
        }
        return db.collection(COLECAO_FAMILIAS)
                .whereEqualTo(CAMPO_CRIADOR_UID, uid)
                .limit(1)
                .get()
                .continueWith(tarefa -> {
                    QuerySnapshot resultado = obterResultado(tarefa);
                    if (resultado.isEmpty()) {
                        return null;
                    }
                    return resultado.getDocuments().get(0).getId();
                });
    }

    @NonNull
    public Task<Void> salvarUsuario(@NonNull String familiaId, @NonNull Usuario usuario) {
        FirebaseFirestore db = obterFirestore();
        if (db == null) {
            return falhaFirebaseIndisponivel();
        }
        String uid = usuario.getFirestoreUid();
        if (vazio(uid)) {
            return Tasks.forException(
                    new IllegalArgumentException("Usuário sem firestoreUid definido."));
        }
        return subcolecao(db, familiaId, COLECAO_USUARIOS).document(uid)
                .set(paraMapaUsuario(familiaId, usuario));
    }

    @NonNull
    public Task<Usuario> buscarUsuario(@NonNull String familiaId, @NonNull String uid) {
        FirebaseFirestore db = obterFirestore();
        if (db == null) {
            return falhaFirebaseIndisponivel();
        }
        return subcolecao(db, familiaId, COLECAO_USUARIOS).document(uid).get()
                .continueWith(tarefa -> {
                    DocumentSnapshot documento = obterResultado(tarefa);
                    return documento.exists() ? paraUsuario(documento) : null;
                });
    }

    @NonNull
    public Task<List<Usuario>> listarUsuarios(@NonNull String familiaId) {
        FirebaseFirestore db = obterFirestore();
        if (db == null) {
            return falhaFirebaseIndisponivel();
        }
        return subcolecao(db, familiaId, COLECAO_USUARIOS).get()
                .continueWith(tarefa -> {
                    List<Usuario> usuarios = new ArrayList<>();
                    for (DocumentSnapshot documento : obterResultado(tarefa).getDocuments()) {
                        usuarios.add(paraUsuario(documento));
                    }
                    return usuarios;
                });
    }

    @NonNull
    public Task<Void> salvarCategoria(@NonNull String familiaId, @NonNull Categoria categoria) {
        FirebaseFirestore db = obterFirestore();
        if (db == null) {
            return falhaFirebaseIndisponivel();
        }
        if (vazio(categoria.getFirestoreId())) {
            return falhaSemFirestoreId("Categoria");
        }
        return subcolecao(db, familiaId, COLECAO_CATEGORIAS).document(categoria.getFirestoreId())
                .set(paraMapaCategoria(familiaId, categoria));
    }

    @NonNull
    public Task<Void> atualizarStatusCategoria(@NonNull String familiaId,
                                               @NonNull String categoriaFirestoreId,
                                               boolean ativa) {
        FirebaseFirestore db = obterFirestore();
        if (db == null) {
            return falhaFirebaseIndisponivel();
        }
        return subcolecao(db, familiaId, COLECAO_CATEGORIAS).document(categoriaFirestoreId)
                .update(CAMPO_ATIVA, ativa);
    }

    @NonNull
    public Task<List<Categoria>> listarCategorias(@NonNull String familiaId) {
        FirebaseFirestore db = obterFirestore();
        if (db == null) {
            return falhaFirebaseIndisponivel();
        }
        return subcolecao(db, familiaId, COLECAO_CATEGORIAS).get()
                .continueWith(tarefa -> {
                    List<Categoria> categorias = new ArrayList<>();
                    for (DocumentSnapshot documento : obterResultado(tarefa).getDocuments()) {
                        categorias.add(paraCategoria(documento));
                    }
                    return categorias;
                });
    }

    @NonNull
    public Task<Void> salvarAssinatura(@NonNull String familiaId,
                                       @NonNull Assinatura assinatura,
                                       @NonNull String donoUid,
                                       @Nullable String categoriaFirestoreId) {
        FirebaseFirestore db = obterFirestore();
        if (db == null) {
            return falhaFirebaseIndisponivel();
        }
        if (vazio(assinatura.getFirestoreId())) {
            return falhaSemFirestoreId("Assinatura");
        }
        if (vazio(donoUid)) {
            return Tasks.forException(
                    new IllegalArgumentException("Assinatura sem donoUid definido."));
        }
        return subcolecao(db, familiaId, COLECAO_ASSINATURAS).document(assinatura.getFirestoreId())
                .set(paraMapaAssinatura(familiaId, assinatura, donoUid, categoriaFirestoreId));
    }

    @NonNull
    public Task<Void> excluirAssinatura(@NonNull String familiaId,
                                        @NonNull String assinaturaFirestoreId,
                                        boolean isGestor) {
        FirebaseFirestore db = obterFirestore();
        if (db == null) {
            return falhaFirebaseIndisponivel();
        }
        if (vazio(assinaturaFirestoreId)) {
            return falhaSemFirestoreId("Assinatura");
        }
        Task<Void> deletarDocumentoAssinatura = subcolecao(db, familiaId, COLECAO_ASSINATURAS)
                .document(assinaturaFirestoreId)
                .delete();
        if (!isGestor) {
            return deletarDocumentoAssinatura;
        }
        return subcolecao(db, familiaId, COLECAO_COBRANCAS)
                .whereEqualTo(CAMPO_ASSINATURA_FIRESTORE_ID, assinaturaFirestoreId)
                .get()
                .continueWithTask(tarefa -> {
                    List<Task<Void>> delecoes = new ArrayList<>();
                    if (tarefa.isSuccessful() && tarefa.getResult() != null) {
                        for (DocumentSnapshot doc : tarefa.getResult().getDocuments()) {
                            delecoes.add(doc.getReference().delete());
                        }
                    }
                    delecoes.add(deletarDocumentoAssinatura);
                    return Tasks.whenAll(delecoes);
                });
    }

    @NonNull
    public Task<List<AssinaturaRemota>> listarAssinaturas(@NonNull String familiaId,
                                                          @NonNull String uidLogado,
                                                          boolean isGestor) {
        FirebaseFirestore db = obterFirestore();
        if (db == null) {
            return falhaFirebaseIndisponivel();
        }
        CollectionReference colecao = subcolecao(db, familiaId, COLECAO_ASSINATURAS);

        Task<List<DocumentSnapshot>> tarefaDocumentos;
        if (isGestor) {
            tarefaDocumentos = colecao.get()
                    .continueWith(tarefa -> obterResultado(tarefa).getDocuments());
        } else {
            Task<QuerySnapshot> consultaGerais = colecao
                    .whereEqualTo(CAMPO_TIPO_DO_USO, DomainConstants.USO_GERAL)
                    .get();
            Task<QuerySnapshot> consultaIndividuais = colecao
                    .whereEqualTo(CAMPO_DONO_UID, uidLogado)
                    .whereEqualTo(CAMPO_TIPO_DO_USO, DomainConstants.USO_INDIVIDUAL)
                    .get();
            tarefaDocumentos = unirConsultas(consultaGerais, consultaIndividuais);
        }

        return tarefaDocumentos.continueWith(tarefa -> {
            List<AssinaturaRemota> assinaturas = new ArrayList<>();
            for (DocumentSnapshot documento : obterResultado(tarefa)) {
                assinaturas.add(paraAssinaturaRemota(documento));
            }
            return assinaturas;
        });
    }

    @NonNull
    public Task<Void> salvarCobranca(@NonNull String familiaId,
                                     @NonNull Cobranca cobranca,
                                     @Nullable String assinaturaFirestoreId,
                                     @Nullable String donoUid,
                                     @Nullable String tipoDoUso) {
        FirebaseFirestore db = obterFirestore();
        if (db == null) {
            return falhaFirebaseIndisponivel();
        }
        if (vazio(cobranca.getFirestoreId())) {
            return falhaSemFirestoreId("Cobrança");
        }
        return subcolecao(db, familiaId, COLECAO_COBRANCAS).document(cobranca.getFirestoreId())
                .set(paraMapaCobranca(familiaId, cobranca, assinaturaFirestoreId, donoUid, tipoDoUso));
    }

    @NonNull
    public Task<Void> atualizarStatusCobranca(@NonNull String familiaId,
                                              @NonNull String cobrancaFirestoreId,
                                              @NonNull String statusPagamento,
                                              boolean notificado24h) {
        FirebaseFirestore db = obterFirestore();
        if (db == null) {
            return falhaFirebaseIndisponivel();
        }
        Map<String, Object> campos = new HashMap<>();
        campos.put(CAMPO_STATUS_PAGAMENTO, statusPagamento);
        campos.put(CAMPO_NOTIFICADO_24H, notificado24h);
        return subcolecao(db, familiaId, COLECAO_COBRANCAS).document(cobrancaFirestoreId)
                .update(campos);
    }

    @NonNull
    public Task<List<CobrancaRemota>> listarCobrancas(@NonNull String familiaId,
                                                      @NonNull String uidLogado,
                                                      boolean isGestor) {
        FirebaseFirestore db = obterFirestore();
        if (db == null) {
            return falhaFirebaseIndisponivel();
        }
        CollectionReference colecao = subcolecao(db, familiaId, COLECAO_COBRANCAS);

        Task<List<DocumentSnapshot>> tarefaDocumentos;
        if (isGestor) {
            tarefaDocumentos = colecao.get()
                    .continueWith(tarefa -> obterResultado(tarefa).getDocuments());
        } else {
            Task<QuerySnapshot> consultaGerais = colecao
                    .whereEqualTo(CAMPO_TIPO_DO_USO, DomainConstants.USO_GERAL)
                    .get();
            Task<QuerySnapshot> consultaDoUsuario = colecao
                    .whereEqualTo(CAMPO_DONO_UID, uidLogado)
                    .get();
            tarefaDocumentos = unirConsultas(consultaGerais, consultaDoUsuario);
        }

        return tarefaDocumentos.continueWith(tarefa -> {
            List<CobrancaRemota> cobrancas = new ArrayList<>();
            for (DocumentSnapshot documento : obterResultado(tarefa)) {
                cobrancas.add(paraCobrancaRemota(documento));
            }
            return cobrancas;
        });
    }

    @NonNull
    public static Map<String, Object> paraMapaFamilia(@NonNull String nomeFamilia,
                                                      @NonNull String criadorUid,
                                                      long dataCriacao) {
        Map<String, Object> mapa = new HashMap<>();
        mapa.put(CAMPO_NOME, nomeFamilia);
        mapa.put(CAMPO_CRIADOR_UID, criadorUid);
        mapa.put(CAMPO_ATIVA, true);
        mapa.put(CAMPO_DATA_CRIACAO, dataCriacao);
        return mapa;
    }

    @NonNull
    public static Map<String, Object> paraMapaUsuario(@NonNull String familiaId,
                                                      @NonNull Usuario usuario) {
        Map<String, Object> mapa = new HashMap<>();
        mapa.put(CAMPO_FIRESTORE_UID, usuario.getFirestoreUid());
        mapa.put(CAMPO_FAMILIA_ID, familiaId);
        mapa.put(CAMPO_NOME, usuario.getNome());
        mapa.put(CAMPO_EMAIL, usuario.getEmail());
        mapa.put(CAMPO_PERFIL, usuario.getPerfil());
        mapa.put(CAMPO_ATIVO, usuario.isAtivo());
        return mapa;
    }

    @NonNull
    public static Map<String, Object> paraMapaCategoria(@NonNull String familiaId,
                                                        @NonNull Categoria categoria) {
        Map<String, Object> mapa = new HashMap<>();
        mapa.put(CAMPO_FIRESTORE_ID, categoria.getFirestoreId());
        mapa.put(CAMPO_FAMILIA_ID, familiaId);
        mapa.put(CAMPO_NOME, categoria.getNome());
        mapa.put(CAMPO_DESCRICAO, categoria.getDescricao());
        mapa.put(CAMPO_ATIVA, categoria.isAtiva());
        return mapa;
    }

    @NonNull
    public static Map<String, Object> paraMapaAssinatura(@NonNull String familiaId,
                                                         @NonNull Assinatura assinatura,
                                                         @NonNull String donoUid,
                                                         @Nullable String categoriaFirestoreId) {
        Map<String, Object> mapa = new HashMap<>();
        mapa.put(CAMPO_FIRESTORE_ID, assinatura.getFirestoreId());
        mapa.put(CAMPO_FAMILIA_ID, familiaId);
        mapa.put(CAMPO_DONO_UID, donoUid);
        if (!vazio(categoriaFirestoreId)) {
            mapa.put(CAMPO_CATEGORIA_FIRESTORE_ID, categoriaFirestoreId);
        }
        mapa.put(CAMPO_NOME_SERVICO, assinatura.getNomeServico());
        mapa.put(CAMPO_VALOR_ORIGINAL, (double) assinatura.getValorOriginal());
        mapa.put(CAMPO_MOEDA, assinatura.getMoeda());
        mapa.put(CAMPO_VALOR_TOTAL, (double) assinatura.getValorTotal());
        mapa.put(CAMPO_PERIODICIDADE, assinatura.getPeriodicidade());
        mapa.put(CAMPO_TIPO_DO_USO, assinatura.getTipoDoUso());
        mapa.put(CAMPO_DATA_PROXIMO_VENCIMENTO, assinatura.getDataProximoVencimento());
        mapa.put(CAMPO_STATUS, assinatura.isStatus());
        return mapa;
    }

    @NonNull
    public static Map<String, Object> paraMapaCobranca(@NonNull String familiaId,
                                                       @NonNull Cobranca cobranca,
                                                       @Nullable String assinaturaFirestoreId,
                                                       @Nullable String donoUid,
                                                       @Nullable String tipoDoUso) {
        Map<String, Object> mapa = new HashMap<>();
        mapa.put(CAMPO_FIRESTORE_ID, cobranca.getFirestoreId());
        mapa.put(CAMPO_FAMILIA_ID, familiaId);
        if (!vazio(assinaturaFirestoreId)) {
            mapa.put(CAMPO_ASSINATURA_FIRESTORE_ID, assinaturaFirestoreId);
        }
        if (!vazio(donoUid)) {
            mapa.put(CAMPO_DONO_UID, donoUid);
        }
        if (!vazio(tipoDoUso)) {
            mapa.put(CAMPO_TIPO_DO_USO, tipoDoUso);
        }
        mapa.put(CAMPO_DATA_VENCIMENTO, cobranca.getDataVencimento());
        mapa.put(CAMPO_VALOR_COBRADO, (double) cobranca.getValorCobrado());
        mapa.put(CAMPO_STATUS_PAGAMENTO, cobranca.getStatusPagamento());
        mapa.put(CAMPO_NOTIFICADO_24H, cobranca.isNotificado24h());
        return mapa;
    }

    @NonNull
    public static Usuario paraUsuario(@NonNull DocumentSnapshot documento) {
        Usuario usuario = new Usuario();
        String uid = documento.getString(CAMPO_FIRESTORE_UID);
        usuario.setFirestoreUid(vazio(uid) ? documento.getId() : uid);
        usuario.setFamiliaId(documento.getString(CAMPO_FAMILIA_ID));
        usuario.setNome(documento.getString(CAMPO_NOME));
        usuario.setEmail(documento.getString(CAMPO_EMAIL));
        String perfil = documento.getString(CAMPO_PERFIL);
        usuario.setPerfil(vazio(perfil) ? DomainConstants.PERFIL_MEMBRO : perfil);
        usuario.setAtivo(lerBoolean(documento, CAMPO_ATIVO, true));
        usuario.setSincronizado(true);
        return usuario;
    }

    @NonNull
    public static Categoria paraCategoria(@NonNull DocumentSnapshot documento) {
        Categoria categoria = new Categoria();
        categoria.setFirestoreId(documento.getId());
        categoria.setFamiliaId(documento.getString(CAMPO_FAMILIA_ID));
        categoria.setNome(documento.getString(CAMPO_NOME));
        categoria.setDescricao(documento.getString(CAMPO_DESCRICAO));
        categoria.setAtiva(lerBoolean(documento, CAMPO_ATIVA, true));
        categoria.setSincronizado(true);
        return categoria;
    }

    @NonNull
    public static AssinaturaRemota paraAssinaturaRemota(@NonNull DocumentSnapshot documento) {
        Assinatura assinatura = new Assinatura();
        assinatura.setFirestoreId(documento.getId());
        assinatura.setFamiliaId(documento.getString(CAMPO_FAMILIA_ID));
        assinatura.setNomeServico(documento.getString(CAMPO_NOME_SERVICO));
        assinatura.setValorOriginal(lerFloat(documento, CAMPO_VALOR_ORIGINAL));
        assinatura.setMoeda(documento.getString(CAMPO_MOEDA));
        assinatura.setValorTotal(lerFloat(documento, CAMPO_VALOR_TOTAL));
        assinatura.setPeriodicidade(documento.getString(CAMPO_PERIODICIDADE));
        assinatura.setTipoDoUso(documento.getString(CAMPO_TIPO_DO_USO));
        assinatura.setDataProximoVencimento(lerLong(documento, CAMPO_DATA_PROXIMO_VENCIMENTO));
        assinatura.setStatus(lerBoolean(documento, CAMPO_STATUS, true));
        assinatura.setSincronizado(true);
        return new AssinaturaRemota(
                assinatura,
                documento.getString(CAMPO_DONO_UID),
                documento.getString(CAMPO_CATEGORIA_FIRESTORE_ID));
    }

    @NonNull
    public static CobrancaRemota paraCobrancaRemota(@NonNull DocumentSnapshot documento) {
        Cobranca cobranca = new Cobranca();
        cobranca.setFirestoreId(documento.getId());
        cobranca.setFamiliaId(documento.getString(CAMPO_FAMILIA_ID));
        cobranca.setDataVencimento(lerLong(documento, CAMPO_DATA_VENCIMENTO));
        cobranca.setValorCobrado(lerFloat(documento, CAMPO_VALOR_COBRADO));
        String status = documento.getString(CAMPO_STATUS_PAGAMENTO);
        cobranca.setStatusPagamento(vazio(status) ? DomainConstants.COB_PENDENTE : status);
        cobranca.setNotificado24h(lerBoolean(documento, CAMPO_NOTIFICADO_24H, false));
        cobranca.setSincronizado(true);
        return new CobrancaRemota(
                cobranca,
                documento.getString(CAMPO_ASSINATURA_FIRESTORE_ID),
                documento.getString(CAMPO_DONO_UID),
                documento.getString(CAMPO_TIPO_DO_USO));
    }

    public static final class AssinaturaRemota {
        private final Assinatura assinatura;
        private final String donoUid;
        private final String categoriaFirestoreId;

        public AssinaturaRemota(@NonNull Assinatura assinatura,
                                @Nullable String donoUid,
                                @Nullable String categoriaFirestoreId) {
            this.assinatura = assinatura;
            this.donoUid = donoUid;
            this.categoriaFirestoreId = categoriaFirestoreId;
        }

        @NonNull
        public Assinatura getAssinatura() {
            return assinatura;
        }

        @Nullable
        public String getDonoUid() {
            return donoUid;
        }

        @Nullable
        public String getCategoriaFirestoreId() {
            return categoriaFirestoreId;
        }
    }

    public static final class CobrancaRemota {
        private final Cobranca cobranca;
        private final String assinaturaFirestoreId;
        private final String donoUid;
        private final String tipoDoUso;

        public CobrancaRemota(@NonNull Cobranca cobranca,
                              @Nullable String assinaturaFirestoreId,
                              @Nullable String donoUid,
                              @Nullable String tipoDoUso) {
            this.cobranca = cobranca;
            this.assinaturaFirestoreId = assinaturaFirestoreId;
            this.donoUid = donoUid;
            this.tipoDoUso = tipoDoUso;
        }

        @NonNull
        public Cobranca getCobranca() {
            return cobranca;
        }

        @Nullable
        public String getAssinaturaFirestoreId() {
            return assinaturaFirestoreId;
        }

        @Nullable
        public String getDonoUid() {
            return donoUid;
        }

        @Nullable
        public String getTipoDoUso() {
            return tipoDoUso;
        }
    }

    @NonNull
    private static Task<List<DocumentSnapshot>> unirConsultas(@NonNull Task<QuerySnapshot> consultaA,
                                                              @NonNull Task<QuerySnapshot> consultaB) {
        return Tasks.whenAllComplete(consultaA, consultaB).continueWith(tarefa -> {
            Map<String, DocumentSnapshot> unidos = new LinkedHashMap<>();
            for (DocumentSnapshot documento : obterResultado(consultaA).getDocuments()) {
                unidos.put(documento.getId(), documento);
            }
            for (DocumentSnapshot documento : obterResultado(consultaB).getDocuments()) {
                unidos.put(documento.getId(), documento);
            }
            return new ArrayList<>(unidos.values());
        });
    }

    @NonNull
    private static <T> T obterResultado(@NonNull Task<T> tarefa) throws Exception {
        if (!tarefa.isSuccessful()) {
            Exception excecao = tarefa.getException();
            throw excecao != null ? excecao : new IllegalStateException("Falha na consulta ao Firestore.");
        }
        T resultado = tarefa.getResult();
        if (resultado == null) {
            throw new IllegalStateException("Resposta vazia do Firestore.");
        }
        return resultado;
    }

    private static float lerFloat(@NonNull DocumentSnapshot documento, @NonNull String campo) {
        Double valor = documento.getDouble(campo);
        return valor != null ? valor.floatValue() : 0f;
    }

    private static long lerLong(@NonNull DocumentSnapshot documento, @NonNull String campo) {
        Long valor = documento.getLong(campo);
        return valor != null ? valor : 0L;
    }

    private static boolean lerBoolean(@NonNull DocumentSnapshot documento,
                                      @NonNull String campo,
                                      boolean padrao) {
        Boolean valor = documento.getBoolean(campo);
        return valor != null ? valor : padrao;
    }

    private static boolean vazio(@Nullable String texto) {
        return texto == null || texto.trim().isEmpty();
    }

    @NonNull
    private static <T> Task<T> falhaSemFirestoreId(@NonNull String entidade) {
        return Tasks.forException(new IllegalArgumentException(
                entidade + " sem firestoreId. Gere um UUID no Repository antes de sincronizar."));
    }

    @NonNull
    private static <T> Task<T> falhaFirebaseIndisponivel() {
        return Tasks.forException(new IllegalStateException(MENSAGEM_FIREBASE_INDISPONIVEL));
    }
}
