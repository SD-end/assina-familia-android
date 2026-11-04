package com.example.assinafamilia.data.repository;

import android.content.Context;
import android.database.Cursor;
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
import com.example.assinafamilia.data.remote.firebase.FirebaseAuthSource;
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
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class AuthRepository {

    private static final long TEMPO_LIMITE_SEGUNDOS = 30L;
    private static final int TAMANHO_MAXIMO_NOME_FAMILIA = 120;

    private final Context contexto;
    private final AppDatabase banco;
    private final UsuarioDao usuarioDao;
    private final CategoriaDao categoriaDao;
    private final AssinaturaDao assinaturaDao;
    private final CobrancaDao cobrancaDao;
    private final FirebaseAuthSource firebaseAuthSource;
    private final FirestoreSource firestoreSource;
    private final SessionManager sessionManager;

    public AuthRepository(@NonNull Context contexto) {
        this.contexto = contexto.getApplicationContext();
        this.banco = AppDatabase.getInstance(this.contexto);
        this.usuarioDao = banco.usuarioDao();
        this.categoriaDao = banco.categoriaDao();
        this.assinaturaDao = banco.assinaturaDao();
        this.cobrancaDao = banco.cobrancaDao();
        this.firebaseAuthSource = new FirebaseAuthSource(this.contexto);
        this.firestoreSource = new FirestoreSource(this.contexto);
        this.sessionManager = new SessionManager(this.contexto);
    }

    @NonNull
    public LiveData<Resource<Usuario>> entrar(@NonNull String email, @NonNull String senha) {
        MutableLiveData<Resource<Usuario>> resultado =
                new MutableLiveData<>(Resource.carregando(null));

        AppDatabase.databaseWriteExecutor.execute(() -> {
            String emailLimpo = email != null ? email.trim() : "";
            String senhaInformada = senha != null ? senha : "";

            if (emailLimpo.isEmpty() || senhaInformada.isEmpty()) {
                resultado.postValue(Resource.erro("Informe e-mail e senha.", null));
                return;
            }

            if (!NetworkUtils.temConexaoInternet(contexto) || !firebaseAuthSource.isFirebaseDisponivel()) {
                autenticarModoOffline(emailLimpo, senhaInformada, resultado);
                return;
            }

            FirebaseUser firebaseUser;
            try {
                firebaseUser = aguardar(firebaseAuthSource.entrar(emailLimpo, senhaInformada));
            } catch (Exception excecao) {
                Exception causa = extrairCausa(excecao);
                if (deveUsarFallbackOfflineNoLogin(causa, emailLimpo, senhaInformada)) {
                    autenticarModoOffline(emailLimpo, senhaInformada, resultado);
                } else {
                    resultado.postValue(Resource.erro(FirebaseAuthSource.obterMensagemErro(causa), null));
                }
                return;
            }

            String uid = firebaseUser.getUid();
            String senhaHash = SessionManager.gerarHashSenha(senhaInformada);
            Usuario localExistente = buscarUsuarioLocal(uid, emailLimpo);

            try {
                String familiaId = FirebaseAuthSource.extrairFamiliaId(firebaseUser);
                if (TextUtils.isEmpty(familiaId)) {
                    familiaId = aguardar(firestoreSource.buscarFamiliaIdPorCriador(uid));
                }
                if (TextUtils.isEmpty(familiaId) && localExistente != null) {
                    familiaId = localExistente.getFamiliaId();
                }
                if (TextUtils.isEmpty(familiaId)) {
                    firebaseAuthSource.sair();
                    resultado.postValue(Resource.erro(
                            "Não foi possível localizar a família vinculada a esta conta.", null));
                    return;
                }

                Usuario usuarioRemoto = aguardar(firestoreSource.buscarUsuario(familiaId, uid));
                if (usuarioRemoto != null && !usuarioRemoto.isAtivo()) {
                    if (localExistente != null) {
                        localExistente.setAtivo(false);
                        usuarioDao.atualizar(localExistente);
                    }
                    firebaseAuthSource.sair();
                    sessionManager.limparSessao();
                    resultado.postValue(Resource.erro(
                            "Este acesso foi desativado pelo gestor da família.", null));
                    return;
                }

                boolean isGestor = usuarioRemoto != null
                        ? usuarioRemoto.isGestor()
                        : (localExistente != null && localExistente.isGestor());
                String nomeFallback = obterNomePreferencial(usuarioRemoto, firebaseUser, localExistente, emailLimpo);

                sincronizarDadosRemotosFamilia(familiaId, uid, isGestor, emailLimpo, nomeFallback);

                Usuario usuarioLogado = buscarUsuarioLocal(uid, emailLimpo);
                if (usuarioLogado == null) {
                    usuarioLogado = new Usuario(
                            uid,
                            familiaId,
                            nomeFallback,
                            emailLimpo,
                            senhaHash,
                            isGestor ? DomainConstants.PERFIL_GESTOR : DomainConstants.PERFIL_MEMBRO,
                            true,
                            usuarioRemoto != null
                    );
                    int idGerado = (int) usuarioDao.inserir(usuarioLogado);
                    usuarioLogado.setId(idGerado);
                } else {
                    usuarioLogado.setFirestoreUid(uid);
                    usuarioLogado.setFamiliaId(familiaId);
                    usuarioLogado.setSenhaHash(senhaHash);
                    usuarioDao.atualizar(usuarioLogado);
                }

                salvarSessaoEAgendar(usuarioLogado);
                sessionManager.salvarTokenSync(System.currentTimeMillis());
                WorkScheduler.enfileirarSincronizacaoQuandoOnline(contexto);
                resultado.postValue(Resource.sucesso(usuarioLogado));
            } catch (Exception excecao) {
                Exception causa = extrairCausa(excecao);
                if (localExistente != null) {
                    if (!localExistente.isAtivo()) {
                        firebaseAuthSource.sair();
                        sessionManager.limparSessao();
                        resultado.postValue(Resource.erro(
                                "Este acesso foi desativado pelo gestor da família.", null));
                        return;
                    }
                    localExistente.setFirestoreUid(uid);
                    localExistente.setSenhaHash(senhaHash);
                    usuarioDao.atualizar(localExistente);
                    salvarSessaoEAgendar(localExistente);
                    WorkScheduler.enfileirarSincronizacaoQuandoOnline(contexto);
                    resultado.postValue(Resource.sucessoComFallbackOffline(
                            localExistente,
                            "Autenticado com dados locais. Sincronização em segundo plano agendada."));
                } else {
                    resultado.postValue(Resource.erro(FirebaseAuthSource.obterMensagemErro(causa), null));
                }
            }
        });

        return resultado;
    }

    @NonNull
    public LiveData<Resource<Usuario>> cadastrarGestor(@NonNull String nome,
                                                       @NonNull String email,
                                                       @NonNull String senha) {
        MutableLiveData<Resource<Usuario>> resultado =
                new MutableLiveData<>(Resource.carregando(null));

        AppDatabase.databaseWriteExecutor.execute(() -> {
            String nomeLimpo = nome != null ? nome.trim() : "";
            String emailLimpo = email != null ? email.trim() : "";
            String senhaInformada = senha != null ? senha : "";

            if (nomeLimpo.isEmpty() || emailLimpo.isEmpty() || senhaInformada.isEmpty()) {
                resultado.postValue(Resource.erro("Preencha nome, e-mail e senha.", null));
                return;
            }

            if (usuarioDao.buscarPorEmail(emailLimpo) != null) {
                resultado.postValue(Resource.erro("Este e-mail já está cadastrado.", null));
                return;
            }

            String senhaHash = SessionManager.gerarHashSenha(senhaInformada);

            if (!NetworkUtils.temConexaoInternet(contexto) || !firebaseAuthSource.isFirebaseDisponivel()) {
                cadastrarGestorModoOffline(nomeLimpo, emailLimpo, senhaHash, resultado);
                return;
            }

            FirebaseUser novoFirebaseUser;
            try {
                novoFirebaseUser = aguardar(
                        firebaseAuthSource.cadastrarGestor(nomeLimpo, emailLimpo, senhaInformada));
            } catch (Exception excecao) {
                Exception causa = extrairCausa(excecao);
                if (causa instanceof FirebaseNetworkException || causa instanceof TimeoutException) {
                    cadastrarGestorModoOffline(nomeLimpo, emailLimpo, senhaHash, resultado);
                } else {
                    resultado.postValue(Resource.erro(FirebaseAuthSource.obterMensagemErro(causa), null));
                }
                return;
            }

            String uid = novoFirebaseUser.getUid();
            String familiaIdExtraido = FirebaseAuthSource.extrairFamiliaId(novoFirebaseUser);
            final String familiaId = TextUtils.isEmpty(familiaIdExtraido)
                    ? UUID.randomUUID().toString()
                    : familiaIdExtraido;

            Usuario gestor = new Usuario(
                    uid,
                    familiaId,
                    nomeLimpo,
                    emailLimpo,
                    senhaHash,
                    DomainConstants.PERFIL_GESTOR,
                    true,
                    false
            );
            List<Categoria> categoriasPadrao = criarCategoriasPadrao(familiaId);
            persistirGestorECategoriasLocais(gestor, categoriasPadrao);

            try {
                aguardar(firestoreSource.criarFamiliaComGestor(
                        familiaId, montarNomeFamilia(nomeLimpo), gestor));
                usuarioDao.marcarComoSincronizado(gestor.getId());
                gestor.setSincronizado(true);

                for (Categoria categoria : categoriasPadrao) {
                    aguardar(firestoreSource.salvarCategoria(familiaId, categoria));
                    categoriaDao.marcarComoSincronizado(categoria.getId());
                    categoria.setSincronizado(true);
                }

                salvarSessaoEAgendar(gestor);
                sessionManager.salvarTokenSync(System.currentTimeMillis());
                resultado.postValue(Resource.sucesso(gestor));
            } catch (Exception excecao) {
                salvarSessaoEAgendar(gestor);
                WorkScheduler.enfileirarSincronizacaoQuandoOnline(contexto);
                resultado.postValue(Resource.sucessoComFallbackOffline(
                        gestor,
                        "Conta criada. Sincronização inicial agendada para segundo plano."));
            }
        });

        return resultado;
    }

    @NonNull
    public LiveData<Resource<Boolean>> recuperarSenha(@NonNull String email) {
        MutableLiveData<Resource<Boolean>> resultado =
                new MutableLiveData<>(Resource.carregando(null));

        AppDatabase.databaseWriteExecutor.execute(() -> {
            String emailLimpo = email != null ? email.trim() : "";
            if (emailLimpo.isEmpty()) {
                resultado.postValue(Resource.erro("Informe o e-mail para recuperação.", false));
                return;
            }
            if (!NetworkUtils.temConexaoInternet(contexto)) {
                resultado.postValue(Resource.erro(
                        "Sem conexão com a internet. Tente novamente quando estiver conectado.", false));
                return;
            }
            if (!firebaseAuthSource.isFirebaseDisponivel()) {
                resultado.postValue(Resource.erro(
                        "Serviço de recuperação indisponível neste dispositivo.", false));
                return;
            }

            try {
                aguardar(firebaseAuthSource.enviarEmailRecuperacaoSenha(emailLimpo));
                resultado.postValue(Resource.sucesso(true));
            } catch (Exception excecao) {
                Exception causa = extrairCausa(excecao);
                resultado.postValue(Resource.erro(FirebaseAuthSource.obterMensagemErro(causa), false));
            }
        });

        return resultado;
    }

    @NonNull
    public LiveData<Resource<Boolean>> sincronizarFamiliaAtual() {
        MutableLiveData<Resource<Boolean>> resultado =
                new MutableLiveData<>(Resource.carregando(null));

        AppDatabase.databaseWriteExecutor.execute(() -> {
            if (!sessionManager.isLogado()) {
                resultado.postValue(Resource.erro("Nenhum usuário autenticado.", false));
                return;
            }

            String familiaId = sessionManager.obterFamiliaId();
            String uid = sessionManager.obterFirestoreUid();
            boolean isGestor = sessionManager.isGestor();

            WorkScheduler.enfileirarSincronizacaoQuandoOnline(contexto);

            if (TextUtils.isEmpty(familiaId)
                    || TextUtils.isEmpty(uid)
                    || AppDatabase.FAMILIA_ID_PADRAO.equals(familiaId)
                    || !firestoreSource.isFirebaseDisponivel()) {
                resultado.postValue(Resource.sucessoComFallbackOffline(
                        true, "Conta em modo local neste dispositivo."));
                return;
            }

            if (!NetworkUtils.temConexaoInternet(contexto)) {
                resultado.postValue(Resource.erro(
                        "Sem conexão com a internet. A sincronização ocorrerá automaticamente quando reconectar.",
                        false));
                return;
            }

            try {
                Usuario usuarioRemoto = aguardar(firestoreSource.buscarUsuario(familiaId, uid));
                if (usuarioRemoto != null && !usuarioRemoto.isAtivo()) {
                    Usuario local = buscarUsuarioLocal(uid, sessionManager.obterEmail());
                    if (local != null) {
                        local.setAtivo(false);
                        usuarioDao.atualizar(local);
                    }
                    sair();
                    resultado.postValue(Resource.erro(
                            "Seu acesso foi desativado pelo gestor da família.", false));
                    return;
                }

                if (usuarioRemoto == null && isGestor) {
                    Usuario gestorLocal = buscarUsuarioLocal(uid, sessionManager.obterEmail());
                    if (gestorLocal != null) {
                        gestorLocal.setFirestoreUid(uid);
                        aguardar(firestoreSource.criarFamiliaComGestor(
                                familiaId, montarNomeFamilia(gestorLocal.getNome()), gestorLocal));
                        usuarioDao.marcarComoSincronizado(gestorLocal.getId());
                    }
                }

                boolean perfilGestorAtualizado = usuarioRemoto != null ? usuarioRemoto.isGestor() : isGestor;
                sincronizarDadosRemotosFamilia(
                        familiaId,
                        uid,
                        perfilGestorAtualizado,
                        sessionManager.obterEmail(),
                        sessionManager.obterNome()
                );

                Usuario atualizadoLocal = buscarUsuarioLocal(uid, sessionManager.obterEmail());
                if (atualizadoLocal != null) {
                    sessionManager.salvarSessao(
                            atualizadoLocal.getId(),
                            atualizadoLocal.getFirestoreUid(),
                            atualizadoLocal.getFamiliaId(),
                            atualizadoLocal.getNome(),
                            atualizadoLocal.getEmail(),
                            atualizadoLocal.getPerfil()
                    );
                }

                sessionManager.salvarTokenSync(System.currentTimeMillis());
                WorkScheduler.enfileirarSincronizacaoQuandoOnline(contexto);
                resultado.postValue(Resource.sucesso(true));
            } catch (Exception excecao) {
                Exception causa = extrairCausa(excecao);
                resultado.postValue(Resource.erro(FirebaseAuthSource.obterMensagemErro(causa), false));
            }
        });

        return resultado;
    }

    public void sair() {
        firebaseAuthSource.sair();
        sessionManager.limparSessao();
    }

    public boolean isLogado() {
        return sessionManager.isLogado();
    }

    private void autenticarModoOffline(@NonNull String email,
                                       @NonNull String senha,
                                       @NonNull MutableLiveData<Resource<Usuario>> resultado) {
        String senhaHash = SessionManager.gerarHashSenha(senha);
        Usuario usuarioLocal = usuarioDao.autenticarOffline(email, senhaHash);

        if (usuarioLocal == null) {
            boolean emailConhecido = usuarioDao.buscarPorEmail(email) != null;
            String mensagem = emailConhecido
                    ? "E-mail ou senha inválidos."
                    : "Sem conexão com a internet para autenticar esta conta neste dispositivo.";
            resultado.postValue(Resource.erro(mensagem, null));
            return;
        }

        if (!usuarioLocal.isAtivo()) {
            sessionManager.limparSessao();
            resultado.postValue(Resource.erro(
                    "Este acesso foi desativado pelo gestor da família.", null));
            return;
        }

        salvarSessaoEAgendar(usuarioLocal);
        resultado.postValue(Resource.sucessoComFallbackOffline(
                usuarioLocal,
                "Modo offline ativo. Exibindo dados salvos neste dispositivo."));
    }

    private void cadastrarGestorModoOffline(@NonNull String nome,
                                            @NonNull String email,
                                            @NonNull String senhaHash,
                                            @NonNull MutableLiveData<Resource<Usuario>> resultado) {
        String familiaId = UUID.randomUUID().toString();
        Usuario gestor = new Usuario(
                null,
                familiaId,
                nome,
                email,
                senhaHash,
                DomainConstants.PERFIL_GESTOR,
                true,
                false
        );
        List<Categoria> categoriasPadrao = criarCategoriasPadrao(familiaId);
        persistirGestorECategoriasLocais(gestor, categoriasPadrao);

        salvarSessaoEAgendar(gestor);
        WorkScheduler.enfileirarSincronizacaoQuandoOnline(contexto);
        resultado.postValue(Resource.sucessoComFallbackOffline(
                gestor,
                "Conta criada localmente em modo offline."));
    }

    private void persistirGestorECategoriasLocais(@NonNull Usuario gestor,
                                                  @NonNull List<Categoria> categorias) {
        banco.runInTransaction(() -> {
            int idGestor = (int) usuarioDao.inserir(gestor);
            gestor.setId(idGestor);
            for (Categoria categoria : categorias) {
                int idCategoria = (int) categoriaDao.inserir(categoria);
                categoria.setId(idCategoria);
            }
        });
    }

    private void sincronizarDadosRemotosFamilia(@NonNull String familiaId,
                                                @NonNull String uidLogado,
                                                boolean isGestor,
                                                @Nullable String emailFallback,
                                                @Nullable String nomeFallback) throws Exception {
        List<Usuario> usuariosRemotos = aguardar(firestoreSource.listarUsuarios(familiaId));
        List<Categoria> categoriasRemotas = aguardar(firestoreSource.listarCategorias(familiaId));
        List<FirestoreSource.AssinaturaRemota> assinaturasRemotas =
                aguardar(firestoreSource.listarAssinaturas(familiaId, uidLogado, isGestor));
        List<FirestoreSource.CobrancaRemota> cobrancasRemotas =
                aguardar(firestoreSource.listarCobrancas(familiaId, uidLogado, isGestor));

        banco.runInTransaction(() -> {
            Map<String, Integer> mapaUsuariosPorUid = sincronizarUsuariosLocais(
                    familiaId, uidLogado, isGestor, emailFallback, nomeFallback, usuariosRemotos);
            Integer idUsuarioLogado = mapaUsuariosPorUid.get(uidLogado);
            int donoPadraoId = idUsuarioLogado != null ? idUsuarioLogado : 0;

            Map<String, Integer> mapaCategoriasPorFirestoreId = new HashMap<>();
            int categoriaPadraoId = sincronizarCategoriasLocais(
                    familiaId, isGestor, categoriasRemotas, assinaturasRemotas, mapaCategoriasPorFirestoreId);

            Map<String, Integer> mapaAssinaturasPorFirestoreId = sincronizarAssinaturasLocais(
                    familiaId, donoPadraoId, categoriaPadraoId, assinaturasRemotas,
                    mapaUsuariosPorUid, mapaCategoriasPorFirestoreId);

            sincronizarCobrancasLocais(familiaId, cobrancasRemotas, mapaAssinaturasPorFirestoreId);
        });
    }

    @NonNull
    private Map<String, Integer> sincronizarUsuariosLocais(@NonNull String familiaId,
                                                           @NonNull String uidLogado,
                                                           boolean isGestor,
                                                           @Nullable String emailFallback,
                                                           @Nullable String nomeFallback,
                                                           @NonNull List<Usuario> usuariosRemotos) {
        Map<String, Integer> mapaUsuariosPorUid = new HashMap<>();

        for (Usuario remoto : usuariosRemotos) {
            if (remoto == null || TextUtils.isEmpty(remoto.getEmail())) {
                continue;
            }
            Usuario existente = buscarUsuarioLocal(remoto.getFirestoreUid(), remoto.getEmail());
            int idLocal;
            if (existente != null) {
                if (existente.isSincronizado()) {
                    existente.setFirestoreUid(remoto.getFirestoreUid());
                    existente.setFamiliaId(familiaId);
                    existente.setNome(remoto.getNome());
                    existente.setEmail(remoto.getEmail());
                    existente.setPerfil(remoto.getPerfil());
                    existente.setAtivo(remoto.isAtivo());
                    existente.setSincronizado(true);
                    usuarioDao.atualizar(existente);
                } else if (TextUtils.isEmpty(existente.getFirestoreUid())
                        && !TextUtils.isEmpty(remoto.getFirestoreUid())) {
                    existente.setFirestoreUid(remoto.getFirestoreUid());
                    usuarioDao.atualizar(existente);
                }
                idLocal = existente.getId();
            } else {
                remoto.setFamiliaId(familiaId);
                remoto.setSincronizado(true);
                idLocal = (int) usuarioDao.inserir(remoto);
            }

            if (!TextUtils.isEmpty(remoto.getFirestoreUid())) {
                mapaUsuariosPorUid.put(remoto.getFirestoreUid(), idLocal);
            }
        }

        if (!mapaUsuariosPorUid.containsKey(uidLogado)) {
            Usuario localLogado = buscarUsuarioLocal(uidLogado, emailFallback);
            if (localLogado != null) {
                localLogado.setFirestoreUid(uidLogado);
                localLogado.setFamiliaId(familiaId);
                usuarioDao.atualizar(localLogado);
                mapaUsuariosPorUid.put(uidLogado, localLogado.getId());
            } else if (!TextUtils.isEmpty(emailFallback)) {
                Usuario novoLogado = new Usuario(
                        uidLogado,
                        familiaId,
                        !TextUtils.isEmpty(nomeFallback) ? nomeFallback : emailFallback,
                        emailFallback,
                        null,
                        isGestor ? DomainConstants.PERFIL_GESTOR : DomainConstants.PERFIL_MEMBRO,
                        true,
                        false
                );
                int novoId = (int) usuarioDao.inserir(novoLogado);
                mapaUsuariosPorUid.put(uidLogado, novoId);
            }
        }

        return mapaUsuariosPorUid;
    }

    private int sincronizarCategoriasLocais(@NonNull String familiaId,
                                            boolean isGestor,
                                            @NonNull List<Categoria> categoriasRemotas,
                                            @NonNull List<FirestoreSource.AssinaturaRemota> assinaturasRemotas,
                                            @NonNull Map<String, Integer> mapaCategorias) {
        int primeiraCategoriaId = 0;

        for (Categoria remota : categoriasRemotas) {
            if (remota == null || TextUtils.isEmpty(remota.getNome())) {
                continue;
            }
            int idExistente = 0;
            if (!TextUtils.isEmpty(remota.getFirestoreId())) {
                idExistente = buscarIdPorConsulta(
                        "SELECT id FROM categorias WHERE firestoreId = ? LIMIT 1",
                        new Object[]{remota.getFirestoreId()});
            }
            if (idExistente <= 0) {
                idExistente = buscarIdPorConsulta(
                        "SELECT id FROM categorias WHERE familiaId = ? AND nome = ? LIMIT 1",
                        new Object[]{familiaId, remota.getNome()});
            }

            int idLocal;
            if (idExistente > 0) {
                Categoria existente = categoriaDao.buscarPorId(idExistente);
                if (existente != null && existente.isSincronizado()) {
                    existente.setFirestoreId(remota.getFirestoreId());
                    existente.setFamiliaId(familiaId);
                    existente.setNome(remota.getNome());
                    existente.setDescricao(remota.getDescricao());
                    existente.setAtiva(remota.isAtiva());
                    existente.setSincronizado(true);
                    categoriaDao.atualizar(existente);
                } else if (existente != null && TextUtils.isEmpty(existente.getFirestoreId())) {
                    existente.setFirestoreId(remota.getFirestoreId());
                    categoriaDao.atualizar(existente);
                }
                idLocal = idExistente;
            } else {
                remota.setFamiliaId(familiaId);
                remota.setSincronizado(true);
                idLocal = (int) categoriaDao.inserir(remota);
            }

            if (primeiraCategoriaId <= 0) {
                primeiraCategoriaId = idLocal;
            }
            if (!TextUtils.isEmpty(remota.getFirestoreId())) {
                mapaCategorias.put(remota.getFirestoreId(), idLocal);
            }
        }

        if (primeiraCategoriaId <= 0) {
            primeiraCategoriaId = buscarIdPorConsulta(
                    "SELECT id FROM categorias WHERE familiaId = ? LIMIT 1",
                    new Object[]{familiaId});
        }

        if (primeiraCategoriaId <= 0 && isGestor) {
            for (Categoria padrao : criarCategoriasPadrao(familiaId)) {
                int idGerado = (int) categoriaDao.inserir(padrao);
                if (primeiraCategoriaId <= 0) {
                    primeiraCategoriaId = idGerado;
                }
                mapaCategorias.put(padrao.getFirestoreId(), idGerado);
            }
        } else if (primeiraCategoriaId <= 0 && !assinaturasRemotas.isEmpty()) {
            Categoria reserva = new Categoria(
                    UUID.randomUUID().toString(),
                    familiaId,
                    "Streaming",
                    "Vídeo, música e entretenimento");
            reserva.setSincronizado(true);
            primeiraCategoriaId = (int) categoriaDao.inserir(reserva);
        }

        return primeiraCategoriaId;
    }

    @NonNull
    private Map<String, Integer> sincronizarAssinaturasLocais(
            @NonNull String familiaId,
            int donoPadraoId,
            int categoriaPadraoId,
            @NonNull List<FirestoreSource.AssinaturaRemota> assinaturasRemotas,
            @NonNull Map<String, Integer> mapaUsuariosPorUid,
            @NonNull Map<String, Integer> mapaCategorias) {
        Map<String, Integer> mapaAssinaturas = new HashMap<>();

        for (FirestoreSource.AssinaturaRemota item : assinaturasRemotas) {
            Assinatura remota = item.getAssinatura();
            int donoId = resolverDonoIdLocal(item.getDonoUid(), donoPadraoId, mapaUsuariosPorUid);
            int categoriaId = resolverCategoriaIdLocal(
                    item.getCategoriaFirestoreId(), categoriaPadraoId, mapaCategorias);

            if (donoId <= 0 || categoriaId <= 0) {
                continue;
            }

            int idExistente = !TextUtils.isEmpty(remota.getFirestoreId())
                    ? buscarIdPorConsulta(
                            "SELECT id FROM tabela_assinaturas WHERE firestoreId = ? LIMIT 1",
                            new Object[]{remota.getFirestoreId()})
                    : 0;

            int idLocal;
            if (idExistente > 0) {
                Assinatura existente = assinaturaDao.buscarPorIdSync(idExistente);
                if (existente != null && existente.isSincronizado()) {
                    remota.setId(idExistente);
                    remota.setFamiliaId(familiaId);
                    remota.setDonoId(donoId);
                    remota.setCategoriaId(categoriaId);
                    remota.setSincronizado(true);
                    assinaturaDao.atualizar(remota);
                }
                idLocal = idExistente;
            } else {
                remota.setFamiliaId(familiaId);
                remota.setDonoId(donoId);
                remota.setCategoriaId(categoriaId);
                remota.setSincronizado(true);
                idLocal = (int) assinaturaDao.inserir(remota);
            }

            if (!TextUtils.isEmpty(remota.getFirestoreId())) {
                mapaAssinaturas.put(remota.getFirestoreId(), idLocal);
            }
        }

        return mapaAssinaturas;
    }

    private void sincronizarCobrancasLocais(
            @NonNull String familiaId,
            @NonNull List<FirestoreSource.CobrancaRemota> cobrancasRemotas,
            @NonNull Map<String, Integer> mapaAssinaturas) {
        for (FirestoreSource.CobrancaRemota item : cobrancasRemotas) {
            Cobranca remota = item.getCobranca();
            int assinaturaId = resolverAssinaturaIdLocal(
                    item.getAssinaturaFirestoreId(), mapaAssinaturas);
            if (assinaturaId <= 0) {
                continue;
            }

            int idExistente = !TextUtils.isEmpty(remota.getFirestoreId())
                    ? buscarIdPorConsulta(
                            "SELECT id FROM cobrancas WHERE firestoreId = ? LIMIT 1",
                            new Object[]{remota.getFirestoreId()})
                    : 0;

            if (idExistente > 0) {
                Cobranca existente = cobrancaDao.buscarPorId(idExistente);
                if (existente != null && existente.isSincronizado()) {
                    existente.setFirestoreId(remota.getFirestoreId());
                    existente.setFamiliaId(familiaId);
                    existente.setAssinaturaId(assinaturaId);
                    existente.setDataVencimento(remota.getDataVencimento());
                    existente.setValorCobrado(remota.getValorCobrado());
                    existente.atualizarStatus(remota.getStatusPagamento());
                    existente.setNotificado24h(existente.isNotificado24h() || remota.isNotificado24h());
                    existente.setSincronizado(true);
                    cobrancaDao.atualizar(existente);
                }
            } else {
                remota.setFamiliaId(familiaId);
                remota.setAssinaturaId(assinaturaId);
                remota.setSincronizado(true);
                cobrancaDao.inserir(remota);
            }
        }
    }

    private int resolverDonoIdLocal(@Nullable String donoUid,
                                    int donoPadraoId,
                                    @NonNull Map<String, Integer> mapaUsuariosPorUid) {
        if (!TextUtils.isEmpty(donoUid)) {
            Integer idMapeado = mapaUsuariosPorUid.get(donoUid);
            if (idMapeado != null && idMapeado > 0) {
                return idMapeado;
            }
            int idBanco = buscarIdPorConsulta(
                    "SELECT id FROM usuarios WHERE firestoreUid = ? LIMIT 1",
                    new Object[]{donoUid});
            if (idBanco > 0) {
                return idBanco;
            }
        }
        return donoPadraoId;
    }

    private int resolverCategoriaIdLocal(@Nullable String categoriaFirestoreId,
                                         int categoriaPadraoId,
                                         @NonNull Map<String, Integer> mapaCategorias) {
        if (!TextUtils.isEmpty(categoriaFirestoreId)) {
            Integer idMapeado = mapaCategorias.get(categoriaFirestoreId);
            if (idMapeado != null && idMapeado > 0) {
                return idMapeado;
            }
            int idBanco = buscarIdPorConsulta(
                    "SELECT id FROM categorias WHERE firestoreId = ? LIMIT 1",
                    new Object[]{categoriaFirestoreId});
            if (idBanco > 0) {
                return idBanco;
            }
        }
        return categoriaPadraoId;
    }

    private int resolverAssinaturaIdLocal(@Nullable String assinaturaFirestoreId,
                                          @NonNull Map<String, Integer> mapaAssinaturas) {
        if (TextUtils.isEmpty(assinaturaFirestoreId)) {
            return 0;
        }
        Integer idMapeado = mapaAssinaturas.get(assinaturaFirestoreId);
        if (idMapeado != null && idMapeado > 0) {
            return idMapeado;
        }
        return buscarIdPorConsulta(
                "SELECT id FROM tabela_assinaturas WHERE firestoreId = ? LIMIT 1",
                new Object[]{assinaturaFirestoreId});
    }

    @Nullable
    private Usuario buscarUsuarioLocal(@Nullable String firestoreUid, @Nullable String email) {
        if (!TextUtils.isEmpty(firestoreUid)) {
            int idPorUid = buscarIdPorConsulta(
                    "SELECT id FROM usuarios WHERE firestoreUid = ? LIMIT 1",
                    new Object[]{firestoreUid});
            if (idPorUid > 0) {
                Usuario porUid = usuarioDao.buscarPorId(idPorUid);
                if (porUid != null) {
                    return porUid;
                }
            }
        }
        if (!TextUtils.isEmpty(email)) {
            return usuarioDao.buscarPorEmail(email);
        }
        return null;
    }

    private int buscarIdPorConsulta(@NonNull String sql, @NonNull Object[] argumentos) {
        try (Cursor cursor = banco.query(sql, argumentos)) {
            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
        }
        return 0;
    }

    private boolean deveUsarFallbackOfflineNoLogin(@NonNull Exception causa,
                                                   @NonNull String email,
                                                   @NonNull String senha) {
        if (causa instanceof FirebaseNetworkException || causa instanceof TimeoutException) {
            return true;
        }
        Usuario local = usuarioDao.autenticarOffline(email, SessionManager.gerarHashSenha(senha));
        return local != null && TextUtils.isEmpty(local.getFirestoreUid());
    }

    private void salvarSessaoEAgendar(@NonNull Usuario usuario) {
        sessionManager.salvarSessao(
                usuario.getId(),
                usuario.getFirestoreUid(),
                usuario.getFamiliaId(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getPerfil()
        );
        WorkScheduler.agendarVerificacaoVencimentos(contexto);
    }

    @NonNull
    private List<Categoria> criarCategoriasPadrao(@NonNull String familiaId) {
        List<Categoria> categorias = new ArrayList<>(4);
        categorias.add(new Categoria(UUID.randomUUID().toString(), familiaId,
                "Streaming", "Vídeo, música e entretenimento"));
        categorias.add(new Categoria(UUID.randomUUID().toString(), familiaId,
                "Internet & Casa", "Internet, telefonia e serviços residenciais"));
        categorias.add(new Categoria(UUID.randomUUID().toString(), familiaId,
                "Educação", "Cursos, plataformas de ensino e idiomas"));
        categorias.add(new Categoria(UUID.randomUUID().toString(), familiaId,
                "Software & Nuvem", "Aplicativos, licenças e armazenamento em nuvem"));
        return categorias;
    }

    @NonNull
    private static String montarNomeFamilia(@NonNull String nomeGestor) {
        String nomeFamilia = "Família de " + nomeGestor;
        if (nomeFamilia.length() > TAMANHO_MAXIMO_NOME_FAMILIA) {
            return nomeFamilia.substring(0, TAMANHO_MAXIMO_NOME_FAMILIA);
        }
        return nomeFamilia;
    }

    @NonNull
    private static String obterNomePreferencial(@Nullable Usuario usuarioRemoto,
                                                @NonNull FirebaseUser firebaseUser,
                                                @Nullable Usuario localExistente,
                                                @NonNull String emailPadrao) {
        if (usuarioRemoto != null && !TextUtils.isEmpty(usuarioRemoto.getNome())) {
            return usuarioRemoto.getNome();
        }
        if (!TextUtils.isEmpty(firebaseUser.getDisplayName())) {
            return firebaseUser.getDisplayName();
        }
        if (localExistente != null && !TextUtils.isEmpty(localExistente.getNome())) {
            return localExistente.getNome();
        }
        return emailPadrao;
    }

    private static <T> T aguardar(@NonNull Task<T> tarefa) throws Exception {
        try {
            return Tasks.await(tarefa, TEMPO_LIMITE_SEGUNDOS, TimeUnit.SECONDS);
        } catch (InterruptedException excecao) {
            Thread.currentThread().interrupt();
            throw excecao;
        }
    }

    @NonNull
    private static Exception extrairCausa(@NonNull Exception excecao) {
        if (excecao instanceof ExecutionException && excecao.getCause() instanceof Exception) {
            return (Exception) excecao.getCause();
        }
        return excecao;
    }
}
