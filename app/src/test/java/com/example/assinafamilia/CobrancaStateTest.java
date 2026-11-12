package com.example.assinafamilia;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.assinafamilia.model.Cobranca;
import com.example.assinafamilia.util.DomainConstants;

import org.junit.Test;

public class CobrancaStateTest {

    @Test
    public void novaCobrancaDeveIniciarComStatusPendente() {
        Cobranca cobranca = criarCobrancaPadrao();
        assertEquals(DomainConstants.COB_PENDENTE, cobranca.getStatusPagamento());
    }

    @Test
    public void devePermitirTransicaoDePendenteParaAtrasada() {
        Cobranca cobranca = criarCobrancaPadrao();
        assertTrue(cobranca.atualizarStatus(DomainConstants.COB_ATRASADA));
        assertEquals(DomainConstants.COB_ATRASADA, cobranca.getStatusPagamento());
    }

    @Test
    public void devePermitirTransicaoDePendenteParaPaga() {
        Cobranca cobranca = criarCobrancaPadrao();
        assertTrue(cobranca.atualizarStatus(DomainConstants.COB_PAGA));
        assertEquals(DomainConstants.COB_PAGA, cobranca.getStatusPagamento());
    }

    @Test
    public void devePermitirTransicaoDeAtrasadaParaPaga() {
        Cobranca cobranca = criarCobrancaPadrao();
        assertTrue(cobranca.atualizarStatus(DomainConstants.COB_ATRASADA));
        assertTrue(cobranca.atualizarStatus(DomainConstants.COB_PAGA));
        assertEquals(DomainConstants.COB_PAGA, cobranca.getStatusPagamento());
    }

    @Test
    public void deveBloquearTransicaoProibidaDePagaParaAtrasada() {
        Cobranca cobranca = criarCobrancaPadrao();
        assertTrue(cobranca.atualizarStatus(DomainConstants.COB_PAGA));

        boolean alterou = cobranca.atualizarStatus(DomainConstants.COB_ATRASADA);

        assertFalse(alterou);
        assertEquals(DomainConstants.COB_PAGA, cobranca.getStatusPagamento());
    }

    private static Cobranca criarCobrancaPadrao() {
        return new Cobranca(1, 1700000000000L, 55.90f, "cob_1", "fam_1");
    }
}
