package com.example.assinafamilia.worker;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.assinafamilia.data.AppDatabase;
import com.example.assinafamilia.data.AssinaturaDao;
import com.example.assinafamilia.data.local.dao.CobrancaDao;
import com.example.assinafamilia.model.Assinatura;
import com.example.assinafamilia.model.Cobranca;
import com.example.assinafamilia.util.DomainConstants;
import com.example.assinafamilia.util.NotificationHelper;
import com.example.assinafamilia.util.SessionManager;

import java.util.Calendar;
import java.util.List;

public class VencimentoWorker extends Worker {

    public static final long JANELA_24H_MILLIS = 86400000L;

    public VencimentoWorker(@NonNull Context contexto, @NonNull WorkerParameters parametros) {
        super(contexto, parametros);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            Context contexto = getApplicationContext();
            SessionManager sessionManager = new SessionManager(contexto);
            if (!sessionManager.isLogado()) {
                return Result.success();
            }

            AppDatabase banco = AppDatabase.getInstance(contexto);
            CobrancaDao cobrancaDao = banco.cobrancaDao();
            AssinaturaDao assinaturaDao = banco.assinaturaDao();

            String familiaId = sessionManager.obterFamiliaId();
            if (familiaId == null || familiaId.trim().isEmpty()) {
                familiaId = AppDatabase.FAMILIA_ID_PADRAO;
            }

            Calendar inicioDia = Calendar.getInstance();
            inicioDia.set(Calendar.HOUR_OF_DAY, 0);
            inicioDia.set(Calendar.MINUTE, 0);
            inicioDia.set(Calendar.SECOND, 0);
            inicioDia.set(Calendar.MILLISECOND, 0);
            long inicioDeHoje = inicioDia.getTimeInMillis();

            Calendar fimAmanha = Calendar.getInstance();
            fimAmanha.setTimeInMillis(inicioDeHoje);
            fimAmanha.add(Calendar.DAY_OF_MONTH, 1);
            fimAmanha.set(Calendar.HOUR_OF_DAY, 23);
            fimAmanha.set(Calendar.MINUTE, 59);
            fimAmanha.set(Calendar.SECOND, 59);
            fimAmanha.set(Calendar.MILLISECOND, 999);
            long fimDeAmanha = fimAmanha.getTimeInMillis();

            cobrancaDao.atualizarCobrancasVencidas(inicioDeHoje);

            List<Cobranca> cobrancasIminentes =
                    cobrancaDao.buscarCobrancasIminentesNaoNotificadas(inicioDeHoje, fimDeAmanha);
            if (cobrancasIminentes == null || cobrancasIminentes.isEmpty()) {
                return Result.success();
            }

            boolean isGestor = sessionManager.isGestor();
            int usuarioLogadoId = sessionManager.obterUsuarioId();
            int moradoresAtivos = Math.max(1, banco.usuarioDao().contarMoradoresAtivos(familiaId));

            for (Cobranca cobranca : cobrancasIminentes) {
                Assinatura assinatura = assinaturaDao.buscarPorIdSync(cobranca.getAssinaturaId());
                if (assinatura == null || !assinatura.isStatus()) {
                    continue;
                }
                if (!podeNotificar(assinatura, isGestor, usuarioLogadoId)) {
                    continue;
                }
                float valorNotificacao = cobranca.getValorCobrado();
                if (!isGestor && DomainConstants.USO_GERAL.equals(assinatura.getTipoDoUso())) {
                    valorNotificacao = cobranca.getValorCobrado() / moradoresAtivos;
                }
                boolean exibida = NotificationHelper.exibirNotificacaoVencimento(
                        contexto,
                        cobranca.getId(),
                        assinatura.getNomeServico(),
                        valorNotificacao,
                        cobranca.getDataVencimento(),
                        cobranca.getStatusPagamento());
                if (exibida) {
                    cobrancaDao.marcarComoNotificado(cobranca.getId());
                }
            }
            return Result.success();
        } catch (Exception excecao) {
            return Result.retry();
        }
    }

    private boolean podeNotificar(@NonNull Assinatura assinatura, boolean isGestor, int usuarioLogadoId) {
        if (isGestor) {
            return true;
        }
        return DomainConstants.USO_GERAL.equals(assinatura.getTipoDoUso())
                || assinatura.getDonoId() == usuarioLogadoId;
    }
}
