package com.example.assinafamilia.data.repository;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.assinafamilia.data.AppDatabase;
import com.example.assinafamilia.data.local.dao.UsuarioDao;
import com.example.assinafamilia.data.remote.firebase.FirebaseAuthSource;
import com.example.assinafamilia.data.remote.firebase.FirestoreSource;
import com.example.assinafamilia.model.Usuario;
import com.example.assinafamilia.util.DomainConstants;
import com.example.assinafamilia.util.NetworkUtils;
import com.example.assinafamilia.util.Resource;
import com.example.assinafamilia.util.SessionManager;
import com.example.assinafamilia.worker.WorkScheduler;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class FamiliaRepository {

    private static final long TEMPO_LIMITE_SEGUNDOS = 30L;
    private static final int TAMANHO_MINIMO_SENHA = 6;

    private final Context contexto;
    private final UsuarioDao usuarioDao;
    private final FirebaseAuthSource firebaseAuthSource;
    private final FirestoreSource firestoreSource;
    private final SessionManager sessionManager;

    public FamiliaRepository(@NonNull Context contexto) {
        this.contexto = contexto.getApplicationContext();
        AppDatabase banco = AppDatabase.getInstance(this.contexto);
        this.usuarioDao = banco.usuarioDao();
        this.firebaseAuthSource = new FirebaseAuthSource(this.contexto);
        this.firestoreSource = new FirestoreSource(this.contexto);
        this.sessionManager = new SessionManager(this.contexto);
    }

    @NonNull
    public LiveData<List<Usuario>> listarMoradoresDaFamilia(@NonNull String familiaId) {
        return usuarioDao.listarMoradoresDaFamilia(familiaId);
    }

    @NonNull
    public LiveData<List<Usuario>> listarMoradoresAtivos(@NonNull String familiaId) {
        return usuarioDao.listarMoradoresAtivos(familiaId);
    }

    @NonNull
    public LiveData<Resource<Usuario>> adicionarMorador(@NonNull String nome,
                                                        @NonNull String email,
                                                        @NonNull String senhaProvisoria) {
        MutableLiveData<Resource<Usuario>> resultado =
                new MutableLiveData<>(Resource.carregando(null));

        AppDatabase.databaseWriteExecutor.execute(() -> {
            if (!sessionManager.isLogado() || !sessionManager.isGestor()) {
                resultado.postValue(Resource.erro(
                        "Apenas o gestor da família pode adicionar moradores.", null));
                return;
            }

            String familiaId = sessionManager.obterFamiliaId();
            if (TextUtils.isEmpty(familiaId)) {
                resultado.postValue(Resource.erro(
                        "Sessão inválida. Faça login novamente.", null));
                return;
            }

            String nomeLimpo = nome != null ? nome.trim() : "";
            String emailLimpo = email != null ? email.trim() : "";
            String senhaInformada = senhaProvisoria != null ? senhaProvisoria : "";

            if (nomeLimpo.isEmpty() || emailLimpo.isEmpty() || senhaInformada.isEmpty()) {
                resultado.postValue(Resource.erro(
                        "Preencha nome, e-mail e senha provisória.", null));
                return;
            }

            if (usuarioDao.buscarPorEmail(emailLimpo) != null) {
                resultado.postValue(Resource.erro("Este e-mail já está cadastrado.", null));
                return;
            }

            String senhaHash = SessionManager.gerarHashSenha(senhaInformada);
            Usuario novoMorador = new Usuario(
                    null,
                    familiaId,
                    nomeLimpo,
                    emailLimpo,
                    senhaHash,
                    DomainConstants.PERFIL_MEMBRO,
                    true,
                    false
            );

            if (!podeSincronizarComNuvem(familiaId)) {
                salvarMoradorLocalmente(novoMorador, resultado,
                        "Morador salvo localmente em modo offline.");
                return;
            }

            try {
                aguardar(firebaseAuthSource.criarContaMorador(
                        novoMorador, senhaInformada, firestoreSource));
                int idGerado = (int) usuarioDao.inserir(novoMorador);
                novoMorador.setId(idGerado);

                if (!novoMorador.isSincronizado()) {
                    WorkScheduler.enfileirarSincronizacaoQuandoOnline(contexto);
                    resultado.postValue(Resource.sucessoComFallbackOffline(
                            novoMorador,
                            "Conta criada. Sincronização com a nuvem pendente."));
                } else {
                    resultado.postValue(Resource.sucesso(novoMorador));
                }
            } catch (Exception excecao) {
                Exception causa = extrairCausa(excecao);
                if (causa instanceof FirebaseNetworkException || causa instanceof TimeoutException) {
                    salvarMoradorLocalmente(novoMorador, resultado,
                            "Sem conexão estável. Morador salvo localmente para sincronização.");
                } else {
                    resultado.postValue(Resource.erro(
                            FirebaseAuthSource.obterMensagemErro(causa), null));
                }
            }
        });

        return resultado;
    }

    @NonNull
    public LiveData<Resource<Boolean>> alterarStatusMorador(@NonNull Usuario morador,
                                                            boolean ativo) {
        MutableLiveData<Resource<Boolean>> resultado =
                new MutableLiveData<>(Resource.carregando(null));

        AppDatabase.databaseWriteExecutor.execute(() -> {
            if (morador == null) {
                resultado.postValue(Resource.erro("Morador inválido.", false));
                return;
            }
            if (!sessionManager.isLogado() || !sessionManager.isGestor()) {
                resultado.postValue(Resource.erro(
                        "Apenas o gestor da família pode alterar o status de moradores.", false));
                return;
            }

            Usuario alvo = morador.getId() > 0 ? usuarioDao.buscarPorId(morador.getId()) : null;
            if (alvo == null) {
                alvo = morador;
            }

            if (!ativo && ehProprioGestor(alvo)) {
                resultado.postValue(Resource.erro(
                        "Não é permitido inativar a conta do próprio gestor.", false));
                return;
            }

            alvo.setAtivo(ativo);
            alvo.setSincronizado(false);
            usuarioDao.atualizar(alvo);
            morador.setAtivo(ativo);
            morador.setSincronizado(false);

            String familiaId = !TextUtils.isEmpty(alvo.getFamiliaId())
                    ? alvo.getFamiliaId()
                    : sessionManager.obterFamiliaId();

            if (podeSincronizarComNuvem(familiaId) && !TextUtils.isEmpty(alvo.getFirestoreUid())) {
                try {
                    aguardar(firestoreSource.salvarUsuario(familiaId, alvo));
                    usuarioDao.marcarComoSincronizado(alvo.getId());
                    alvo.setSincronizado(true);
                    morador.setSincronizado(true);
                    resultado.postValue(Resource.sucesso(true));
                } catch (Exception excecao) {
                    WorkScheduler.enfileirarSincronizacaoQuandoOnline(contexto);
                    resultado.postValue(Resource.sucessoComFallbackOffline(
                            true, "Status atualizado localmente. Sincronização pendente."));
                }
            } else {
                WorkScheduler.enfileirarSincronizacaoQuandoOnline(contexto);
                if (!NetworkUtils.temConexaoInternet(contexto)) {
                    resultado.postValue(Resource.sucessoComFallbackOffline(
                            true, "Status atualizado localmente em modo offline."));
                } else {
                    resultado.postValue(Resource.sucesso(true));
                }
            }
        });

        return resultado;
    }

    @NonNull
    public LiveData<Resource<Usuario>> atualizarMeusDados(@NonNull String novoNome,
                                                          @Nullable String novaSenhaOpcional) {
        MutableLiveData<Resource<Usuario>> resultado =
                new MutableLiveData<>(Resource.carregando(null));

        AppDatabase.databaseWriteExecutor.execute(() -> {
            if (!sessionManager.isLogado()) {
                resultado.postValue(Resource.erro(
                        "Sessão expirada. Faça login novamente.", null));
                return;
            }

            String nomeLimpo = novoNome != null ? novoNome.trim() : "";
            if (nomeLimpo.isEmpty()) {
                resultado.postValue(Resource.erro("O nome não pode ficar vazio.", null));
                return;
            }

            boolean alterarSenha = novaSenhaOpcional != null && !novaSenhaOpcional.trim().isEmpty();
            if (alterarSenha && novaSenhaOpcional.length() < TAMANHO_MINIMO_SENHA) {
                resultado.postValue(Resource.erro(
                        "A nova senha deve ter ao menos 6 caracteres.", null));
                return;
            }

            Usuario usuario = obterUsuarioSessaoLocal();
            if (usuario == null) {
                resultado.postValue(Resource.erro(
                        "Usuário não encontrado neste dispositivo.", null));
                return;
            }

            String familiaId = !TextUtils.isEmpty(usuario.getFamiliaId())
                    ? usuario.getFamiliaId()
                    : sessionManager.obterFamiliaId();
            boolean sincronizarNuvem = podeSincronizarComNuvem(familiaId);
            boolean senhaSincronizadaNaNuvem = true;

            if (alterarSenha && sincronizarNuvem) {
                FirebaseUser firebaseUser = firebaseAuthSource.obterUsuarioAtual();
                if (firebaseUser != null) {
                    try {
                        aguardar(firebaseUser.updatePassword(novaSenhaOpcional));
                    } catch (Exception excecao) {
                        Exception causa = extrairCausa(excecao);
                        if (causa instanceof FirebaseNetworkException
                                || causa instanceof TimeoutException) {
                            senhaSincronizadaNaNuvem = false;
                        } else if (causa instanceof FirebaseAuthRecentLoginRequiredException) {
                            resultado.postValue(Resource.erro(
                                    "Por segurança, saia e entre novamente antes de alterar sua senha.",
                                    null));
                            return;
                        } else {
                            resultado.postValue(Resource.erro(
                                    FirebaseAuthSource.obterMensagemErro(causa), null));
                            return;
                        }
                    }
                }
            }

            usuario.setNome(nomeLimpo);
            if (alterarSenha) {
                usuario.setSenhaHash(SessionManager.gerarHashSenha(novaSenhaOpcional));
            }
            usuario.setSincronizado(false);
            usuarioDao.atualizar(usuario);

            sessionManager.salvarSessao(
                    usuario.getId(),
                    usuario.getFirestoreUid(),
                    familiaId,
                    usuario.getNome(),
                    usuario.getEmail(),
                    usuario.getPerfil()
            );

            if (sincronizarNuvem
                    && senhaSincronizadaNaNuvem
                    && !TextUtils.isEmpty(usuario.getFirestoreUid())) {
                try {
                    FirebaseUser firebaseUser = firebaseAuthSource.obterUsuarioAtual();
                    if (firebaseUser != null) {
                        UserProfileChangeRequest.Builder construtorPerfil =
                                new UserProfileChangeRequest.Builder().setDisplayName(nomeLimpo);
                        if (FirebaseAuthSource.extrairFamiliaId(firebaseUser) == null
                                && !TextUtils.isEmpty(familiaId)) {
                            construtorPerfil.setPhotoUri(
                                    FirebaseAuthSource.montarUriFamilia(familiaId));
                        }
                        aguardar(firebaseUser.updateProfile(construtorPerfil.build()));
                    }
                    aguardar(firestoreSource.salvarUsuario(familiaId, usuario));
                    usuarioDao.marcarComoSincronizado(usuario.getId());
                    usuario.setSincronizado(true);
                    resultado.postValue(Resource.sucesso(usuario));
                } catch (Exception excecao) {
                    WorkScheduler.enfileirarSincronizacaoQuandoOnline(contexto);
                    resultado.postValue(Resource.sucessoComFallbackOffline(
                            usuario, "Dados salvos localmente. Sincronização pendente."));
                }
            } else {
                WorkScheduler.enfileirarSincronizacaoQuandoOnline(contexto);
                if (!NetworkUtils.temConexaoInternet(contexto) || !senhaSincronizadaNaNuvem) {
                    resultado.postValue(Resource.sucessoComFallbackOffline(
                            usuario, "Dados atualizados localmente em modo offline."));
                } else {
                    resultado.postValue(Resource.sucesso(usuario));
                }
            }
        });

        return resultado;
    }

    private void salvarMoradorLocalmente(@NonNull Usuario morador,
                                         @NonNull MutableLiveData<Resource<Usuario>> resultado,
                                         @NonNull String mensagem) {
        morador.setSincronizado(false);
        int idGerado = (int) usuarioDao.inserir(morador);
        morador.setId(idGerado);
        WorkScheduler.enfileirarSincronizacaoQuandoOnline(contexto);
        resultado.postValue(Resource.sucessoComFallbackOffline(morador, mensagem));
    }

    @Nullable
    private Usuario obterUsuarioSessaoLocal() {
        Usuario porId = usuarioDao.buscarPorId(sessionManager.obterUsuarioId());
        if (porId != null) {
            return porId;
        }
        String emailSessao = sessionManager.obterEmail();
        if (!TextUtils.isEmpty(emailSessao)) {
            return usuarioDao.buscarPorEmail(emailSessao);
        }
        return null;
    }

    private boolean ehProprioGestor(@NonNull Usuario alvo) {
        if (alvo.isGestor()) {
            return true;
        }
        if (alvo.getId() > 0 && alvo.getId() == sessionManager.obterUsuarioId()) {
            return true;
        }
        String uidSessao = sessionManager.obterFirestoreUid();
        return !TextUtils.isEmpty(uidSessao) && uidSessao.equals(alvo.getFirestoreUid());
    }

    private boolean podeSincronizarComNuvem(@Nullable String familiaId) {
        return !TextUtils.isEmpty(familiaId)
                && !AppDatabase.FAMILIA_ID_PADRAO.equals(familiaId)
                && NetworkUtils.temConexaoInternet(contexto)
                && firebaseAuthSource.isFirebaseDisponivel()
                && firebaseAuthSource.obterUsuarioAtual() != null;
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
