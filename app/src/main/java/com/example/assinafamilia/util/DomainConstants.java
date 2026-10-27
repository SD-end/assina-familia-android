package com.example.assinafamilia.util;

public final class DomainConstants {

    private DomainConstants() {
        throw new UnsupportedOperationException("Classe utilitária não pode ser instanciada.");
    }

    public static final String PERFIL_GESTOR = "GESTOR";
    public static final String PERFIL_MEMBRO = "MEMBRO";
    public static final String USO_GERAL = "GERAL";
    public static final String USO_INDIVIDUAL = "INDIVIDUAL";
    public static final String COB_PENDENTE = "PENDENTE";
    public static final String COB_PAGA = "PAGA";
    public static final String COB_ATRASADA = "ATRASADA";
    public static final String PER_MENSAL = "MENSAL";
    public static final String PER_ANUAL = "ANUAL";
    public static final String MOEDA_BRL = "BRL";
    public static final String MOEDA_USD = "USD";
    public static final String MOEDA_EUR = "EUR";
    public static final String CANAL_NOTIFICACAO_ID = "canal_vencimentos_assina_familia";
    public static final String CANAL_NOTIFICACAO_NOME = "Lembretes de Vencimento";
}
