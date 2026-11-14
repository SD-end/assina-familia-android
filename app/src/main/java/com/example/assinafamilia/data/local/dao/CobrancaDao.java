package com.example.assinafamilia.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.assinafamilia.model.Cobranca;
import com.example.assinafamilia.util.DomainConstants;

import java.util.List;

@Dao
public interface CobrancaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long inserir(Cobranca cobranca);

    @Update
    void atualizar(Cobranca cobranca);

    @Query("SELECT * FROM cobrancas WHERE id = :id LIMIT 1")
    Cobranca buscarPorId(int id);

    @Query("SELECT * FROM cobrancas WHERE assinaturaId = :assinaturaId ORDER BY dataVencimento DESC")
    LiveData<List<Cobranca>> listarPorAssinatura(int assinaturaId);

    @Query("SELECT * FROM cobrancas WHERE familiaId = :familiaId AND statusPagamento != '"
            + DomainConstants.COB_PAGA + "' ORDER BY dataVencimento ASC")
    LiveData<List<Cobranca>> listarProximasCobrancasFamilia(String familiaId);

    @Query("SELECT * FROM cobrancas WHERE notificado24h = 0 AND ((statusPagamento = '"
            + DomainConstants.COB_PENDENTE
            + "' AND dataVencimento BETWEEN :agora AND :limite24h) OR statusPagamento = '"
            + DomainConstants.COB_ATRASADA + "') ORDER BY dataVencimento ASC")
    List<Cobranca> buscarCobrancasIminentesNaoNotificadas(long agora, long limite24h);

    @Query("SELECT * FROM cobrancas WHERE assinaturaId = :assinaturaId AND statusPagamento != '"
            + DomainConstants.COB_PAGA + "' ORDER BY dataVencimento DESC")
    List<Cobranca> buscarCobrancasAbertasPorAssinaturaSync(int assinaturaId);

    @Query("SELECT * FROM cobrancas WHERE familiaId = :familiaId AND (notificado24h = 1 OR statusPagamento = '"
            + DomainConstants.COB_PAGA + "' OR statusPagamento = '"
            + DomainConstants.COB_ATRASADA + "' OR (statusPagamento = '"
            + DomainConstants.COB_PENDENTE
            + "' AND dataVencimento <= :limiteAlerta)) ORDER BY dataVencimento DESC, id DESC")
    LiveData<List<Cobranca>> listarHistoricoNotificacoesFamilia(String familiaId, long limiteAlerta);

    @Query("UPDATE cobrancas SET notificado24h = 1 WHERE id = :id")
    void marcarComoNotificado(int id);

    @Query("UPDATE cobrancas SET statusPagamento = '" + DomainConstants.COB_ATRASADA
            + "', sincronizado = 0 WHERE statusPagamento = '" + DomainConstants.COB_PENDENTE
            + "' AND dataVencimento < :agora")
    int atualizarCobrancasVencidas(long agora);

    @Query("SELECT * FROM cobrancas WHERE sincronizado = 0")
    List<Cobranca> listarPendentesSincronizacao();

    @Query("UPDATE cobrancas SET sincronizado = 1 WHERE id = :id")
    void marcarComoSincronizado(int id);
}
