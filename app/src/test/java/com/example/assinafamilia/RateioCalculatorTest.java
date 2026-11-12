package com.example.assinafamilia;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.assinafamilia.model.Assinatura;
import com.example.assinafamilia.model.Cobranca;
import com.example.assinafamilia.model.ResumoDashboard;
import com.example.assinafamilia.model.Usuario;
import com.example.assinafamilia.util.DomainConstants;
import com.example.assinafamilia.util.RateioCalculator;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RateioCalculatorTest {

    private static final float DELTA = 0.001f;

    @Test
    public void deveDividirAssinaturasGeraisIgualmenteEntreCincoMoradoresAtivos() {
        List<Assinatura> assinaturas = new ArrayList<>();
        assinaturas.add(criarAssinatura(1, 1, "Internet", 100.0f,
                DomainConstants.USO_GERAL, true));

        List<Usuario> moradores = new ArrayList<>();
        for (int indice = 1; indice <= 5; indice++) {
            moradores.add(criarMorador(indice, "Morador " + indice, indice == 1, true));
        }

        ResumoDashboard resumo = RateioCalculator.calcularResumo(
                assinaturas, moradores, Collections.emptyList(), 1, true);

        assertEquals(100.0f, resumo.getTotalGeralCasa(), DELTA);
        assertEquals(5, resumo.getQuantidadeMoradoresAtivos());
        assertEquals(20.0f, resumo.getFracaoRateioPorMembro(), DELTA);
    }

    @Test
    public void deveExcluiMoradoresInativosDoDivisorDoRateio() {
        List<Assinatura> assinaturas = new ArrayList<>();
        assinaturas.add(criarAssinatura(1, 1, "Internet", 120.0f,
                DomainConstants.USO_GERAL, true));

        List<Usuario> moradores = new ArrayList<>();
        moradores.add(criarMorador(1, "Gestor", true, true));
        moradores.add(criarMorador(2, "Membro Ativo", false, true));
        moradores.add(criarMorador(3, "Membro Inativo", false, false));

        ResumoDashboard resumo = RateioCalculator.calcularResumo(
                assinaturas, moradores, Collections.emptyList(), 1, true);

        assertEquals(2, resumo.getQuantidadeMoradoresAtivos());
        assertEquals(60.0f, resumo.getFracaoRateioPorMembro(), DELTA);
    }

    @Test
    public void deveProtegerContraDivisaoPorZeroQuandoListaDeMoradoresEstiverVaziaOuInativa() {
        List<Assinatura> assinaturas = new ArrayList<>();
        assinaturas.add(criarAssinatura(1, 1, "Internet", 80.0f,
                DomainConstants.USO_GERAL, true));

        ResumoDashboard resumo = RateioCalculator.calcularResumo(
                assinaturas, Collections.emptyList(), Collections.emptyList(), 1, true);

        assertEquals(80.0f, resumo.getFracaoRateioPorMembro(), DELTA);
        assertFalse(Float.isNaN(resumo.getFracaoRateioPorMembro()));
        assertFalse(Float.isInfinite(resumo.getFracaoRateioPorMembro()));
    }

    @Test
    public void deveIsolarAssinaturasIndividuaisDeOutrosMembrosNaVisaoDoMembro() {
        List<Usuario> moradores = new ArrayList<>();
        moradores.add(criarMorador(1, "Gestor", true, true));
        moradores.add(criarMorador(2, "Membro 2", false, true));
        moradores.add(criarMorador(3, "Membro 3", false, true));

        List<Assinatura> assinaturas = new ArrayList<>();
        assinaturas.add(criarAssinatura(1, 1, "Internet", 90.0f,
                DomainConstants.USO_GERAL, true));
        assinaturas.add(criarAssinatura(2, 2, "Streaming do Membro 2", 50.0f,
                DomainConstants.USO_INDIVIDUAL, true));
        assinaturas.add(criarAssinatura(3, 3, "Curso do Membro 3", 200.0f,
                DomainConstants.USO_INDIVIDUAL, true));

        ResumoDashboard resumo = RateioCalculator.calcularResumo(
                assinaturas, moradores, Collections.emptyList(), 2, false);

        assertEquals(30.0f, resumo.getFracaoRateioPorMembro(), DELTA);
        assertEquals(50.0f, resumo.getMeuTotalIndividual(), DELTA);
        assertEquals(80.0f, resumo.getMeuCustoTotal(), DELTA);
        assertEquals(2, resumo.getQuantidadeAssinaturasAtivas());
        assertEquals(1, resumo.getDetalhamentoMoradores().size());
    }

    @Test
    public void deveConsolidarCustoTotalDaFamiliaEDetalhamentoNoPainelDoGestor() {
        List<Usuario> moradores = new ArrayList<>();
        moradores.add(criarMorador(1, "Gestor", true, true));
        moradores.add(criarMorador(2, "Membro 2", false, true));
        moradores.add(criarMorador(3, "Membro 3", false, true));

        List<Assinatura> assinaturas = new ArrayList<>();
        assinaturas.add(criarAssinatura(1, 1, "Internet", 90.0f,
                DomainConstants.USO_GERAL, true));
        assinaturas.add(criarAssinatura(2, 2, "Streaming do Membro 2", 50.0f,
                DomainConstants.USO_INDIVIDUAL, true));
        assinaturas.add(criarAssinatura(3, 3, "Curso do Membro 3", 200.0f,
                DomainConstants.USO_INDIVIDUAL, true));

        ResumoDashboard resumo = RateioCalculator.calcularResumo(
                assinaturas, moradores, Collections.emptyList(), 1, true);

        assertEquals(340.0f, resumo.getTotalGastoFamilia(), DELTA);
        assertEquals(340.0f, resumo.getMeuCustoTotal(), DELTA);
        assertEquals(3, resumo.getQuantidadeAssinaturasAtivas());
        assertEquals(3, resumo.getDetalhamentoMoradores().size());
    }

    @Test
    public void deveIgnorarAssinaturasInativasNoCalculoDeCustos() {
        List<Assinatura> assinaturas = new ArrayList<>();
        assinaturas.add(criarAssinatura(1, 1, "Internet", 60.0f,
                DomainConstants.USO_GERAL, true));
        assinaturas.add(criarAssinatura(2, 1, "Internet Antiga", 100.0f,
                DomainConstants.USO_GERAL, false));

        List<Usuario> moradores = new ArrayList<>();
        moradores.add(criarMorador(1, "Gestor", true, true));
        moradores.add(criarMorador(2, "Membro", false, true));

        ResumoDashboard resumo = RateioCalculator.calcularResumo(
                assinaturas, moradores, Collections.emptyList(), 1, true);

        assertEquals(60.0f, resumo.getTotalGeralCasa(), DELTA);
        assertEquals(30.0f, resumo.getFracaoRateioPorMembro(), DELTA);
    }

    @Test
    public void deveIndicarInadimplenciaApenasParaCobrancasVisiveisAoPerfil() {
        List<Usuario> moradores = new ArrayList<>();
        moradores.add(criarMorador(1, "Gestor", true, true));
        moradores.add(criarMorador(2, "Membro 2", false, true));
        moradores.add(criarMorador(3, "Membro 3", false, true));

        List<Assinatura> assinaturas = new ArrayList<>();
        assinaturas.add(criarAssinatura(10, 3, "Curso do Membro 3", 200.0f,
                DomainConstants.USO_INDIVIDUAL, true));

        Cobranca cobranca = new Cobranca(10, 1700000000000L, 200.0f,
                "cob_10", "fam_1");
        cobranca.setStatusPagamento(DomainConstants.COB_ATRASADA);

        List<Cobranca> cobrancas = new ArrayList<>();
        cobrancas.add(cobranca);

        ResumoDashboard resumoMembro = RateioCalculator.calcularResumo(
                assinaturas, moradores, cobrancas, 2, false);
        assertFalse(resumoMembro.isPossuiInadimplencia());

        ResumoDashboard resumoGestor = RateioCalculator.calcularResumo(
                assinaturas, moradores, cobrancas, 1, true);
        assertTrue(resumoGestor.isPossuiInadimplencia());
    }

    private static Usuario criarMorador(int id, String nome, boolean gestor, boolean ativo) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setNome(nome);
        usuario.setPerfil(gestor
                ? DomainConstants.PERFIL_GESTOR
                : DomainConstants.PERFIL_MEMBRO);
        usuario.setAtivo(ativo);
        return usuario;
    }

    private static Assinatura criarAssinatura(int id, int donoId, String nome,
                                              float valorTotal, String tipoDoUso,
                                              boolean status) {
        Assinatura assinatura = new Assinatura();
        assinatura.setId(id);
        assinatura.setDonoId(donoId);
        assinatura.setNomeServico(nome);
        assinatura.setValorOriginal(valorTotal);
        assinatura.setValorTotal(valorTotal);
        assinatura.setMoeda(DomainConstants.MOEDA_BRL);
        assinatura.setPeriodicidade(DomainConstants.PER_MENSAL);
        assinatura.setTipoDoUso(tipoDoUso);
        assinatura.setDataProximoVencimento(1700000000000L);
        assinatura.setStatus(status);
        return assinatura;
    }
}
