package com.example.assinafamilia.util;

import com.example.assinafamilia.model.Assinatura;
import com.example.assinafamilia.model.Cobranca;
import com.example.assinafamilia.model.ResumoDashboard;
import com.example.assinafamilia.model.Usuario;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class RateioCalculator {

    private RateioCalculator() {
        throw new UnsupportedOperationException("Classe utilitária não pode ser instanciada.");
    }

    public static ResumoDashboard calcularResumo(List<Assinatura> assinaturas,
                                                 List<Usuario> moradores,
                                                 List<Cobranca> cobrancas,
                                                 int usuarioLogadoId,
                                                 boolean isGestor) {
        ResumoDashboard resumo = new ResumoDashboard();

        List<Assinatura> assinaturasAtivas = new ArrayList<>();
        if (assinaturas != null) {
            for (Assinatura assinatura : assinaturas) {
                if (assinatura != null && assinatura.isStatus()) {
                    assinaturasAtivas.add(assinatura);
                }
            }
        }

        List<Usuario> moradoresAtivos = new ArrayList<>();
        if (moradores != null) {
            for (Usuario morador : moradores) {
                if (morador != null && morador.isAtivo()) {
                    moradoresAtivos.add(morador);
                }
            }
        }
        int divisorRateio = moradoresAtivos.isEmpty() ? 1 : moradoresAtivos.size();

        float totalGeralCasa = 0f;
        float totalTodasIndividuais = 0f;
        float meuTotalIndividual = 0f;
        int quantidadeGerais = 0;
        int quantidadeMinhasIndividuais = 0;

        for (Assinatura assinatura : assinaturasAtivas) {
            if (isUsoGeral(assinatura)) {
                totalGeralCasa += assinatura.getValorTotal();
                quantidadeGerais++;
            } else if (isUsoIndividual(assinatura)) {
                totalTodasIndividuais += assinatura.getValorTotal();
                if (assinatura.getDonoId() == usuarioLogadoId) {
                    meuTotalIndividual += assinatura.getValorTotal();
                    quantidadeMinhasIndividuais++;
                }
            }
        }

        float fracaoRateioPorMembro = totalGeralCasa / divisorRateio;

        List<ResumoDashboard.CustoMorador> detalhamento = new ArrayList<>();
        for (Usuario morador : moradoresAtivos) {
            if (!isGestor && morador.getId() != usuarioLogadoId) {
                continue;
            }

            float totalIndividualMorador = 0f;
            for (Assinatura assinatura : assinaturasAtivas) {
                if (isUsoIndividual(assinatura) && assinatura.getDonoId() == morador.getId()) {
                    totalIndividualMorador += assinatura.getValorTotal();
                }
            }

            detalhamento.add(new ResumoDashboard.CustoMorador(
                    morador.getId(),
                    morador.getNome(),
                    totalIndividualMorador,
                    fracaoRateioPorMembro,
                    totalIndividualMorador + fracaoRateioPorMembro));
        }

        float totalGastoFamilia;
        float meuCustoTotal;
        int quantidadeAssinaturasVisiveis;

        if (isGestor) {
            totalGastoFamilia = totalGeralCasa + totalTodasIndividuais;
            meuCustoTotal = totalGastoFamilia;
            quantidadeAssinaturasVisiveis = assinaturasAtivas.size();
        } else {
            totalGastoFamilia = totalGeralCasa + meuTotalIndividual;
            meuCustoTotal = meuTotalIndividual + fracaoRateioPorMembro;
            quantidadeAssinaturasVisiveis = quantidadeGerais + quantidadeMinhasIndividuais;
        }

        boolean possuiInadimplencia = verificarInadimplencia(
                cobrancas, assinaturas, usuarioLogadoId, isGestor);

        resumo.setTotalGeralCasa(totalGeralCasa);
        resumo.setTotalGastoFamilia(totalGastoFamilia);
        resumo.setFracaoRateioPorMembro(fracaoRateioPorMembro);
        resumo.setMeuCustoTotal(meuCustoTotal);
        resumo.setMeuTotalIndividual(meuTotalIndividual);
        resumo.setQuantidadeAssinaturasAtivas(quantidadeAssinaturasVisiveis);
        resumo.setQuantidadeMoradoresAtivos(moradoresAtivos.size());
        resumo.setPossuiInadimplencia(possuiInadimplencia);
        resumo.setDetalhamentoMoradores(detalhamento);

        return resumo;
    }

    private static boolean verificarInadimplencia(List<Cobranca> cobrancas,
                                                  List<Assinatura> assinaturas,
                                                  int usuarioLogadoId,
                                                  boolean isGestor) {
        if (cobrancas == null || cobrancas.isEmpty()) {
            return false;
        }

        Set<Integer> idsAssinaturasVisiveis = new HashSet<>();
        if (!isGestor && assinaturas != null) {
            for (Assinatura assinatura : assinaturas) {
                if (assinatura == null) {
                    continue;
                }
                if (isUsoGeral(assinatura)
                        || (isUsoIndividual(assinatura) && assinatura.getDonoId() == usuarioLogadoId)) {
                    idsAssinaturasVisiveis.add(assinatura.getId());
                }
            }
        }

        for (Cobranca cobranca : cobrancas) {
            if (cobranca == null
                    || !DomainConstants.COB_ATRASADA.equalsIgnoreCase(cobranca.getStatusPagamento())) {
                continue;
            }
            if (isGestor || idsAssinaturasVisiveis.contains(cobranca.getAssinaturaId())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isUsoGeral(Assinatura assinatura) {
        return DomainConstants.USO_GERAL.equalsIgnoreCase(assinatura.getTipoDoUso());
    }

    private static boolean isUsoIndividual(Assinatura assinatura) {
        return DomainConstants.USO_INDIVIDUAL.equalsIgnoreCase(assinatura.getTipoDoUso());
    }
}
