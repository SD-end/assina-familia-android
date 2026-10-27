package com.example.assinafamilia.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.assinafamilia.model.Assinatura;
import com.example.assinafamilia.util.DomainConstants;

import java.util.List;

@Dao
public interface AssinaturaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long inserir(Assinatura assinatura);

    @Update
    void atualizar(Assinatura assinatura);

    @Delete
    void deletar(Assinatura assinatura);

    @Query("SELECT * FROM tabela_assinaturas WHERE id = :id LIMIT 1")
    LiveData<Assinatura> buscarPorId(int id);

    @Query("SELECT * FROM tabela_assinaturas WHERE id = :id LIMIT 1")
    Assinatura buscarPorIdSync(int id);

    @Query("SELECT * FROM tabela_assinaturas WHERE familiaId = :familiaId ORDER BY nomeServico ASC")
    LiveData<List<Assinatura>> listarTodasPorFamilia(String familiaId);

    @Query("SELECT * FROM tabela_assinaturas WHERE familiaId = :familiaId AND (tipoDoUso = '"
            + DomainConstants.USO_GERAL + "' OR donoId = :usuarioId) ORDER BY nomeServico ASC")
    LiveData<List<Assinatura>> listarParaMembro(String familiaId, int usuarioId);

    @Query("SELECT * FROM tabela_assinaturas WHERE familiaId = :familiaId AND status = 1")
    LiveData<List<Assinatura>> listarAtivasPorFamilia(String familiaId);

    @Query("SELECT COUNT(*) FROM tabela_assinaturas WHERE categoriaId = :categoriaId AND status = 1")
    int contarAssinaturasAtivasPorCategoria(int categoriaId);

    @Query("SELECT * FROM tabela_assinaturas WHERE sincronizado = 0")
    List<Assinatura> listarPendentesSincronizacao();

    @Query("UPDATE tabela_assinaturas SET sincronizado = 1 WHERE id = :id")
    void marcarComoSincronizado(int id);

    @Query("UPDATE tabela_assinaturas SET firestoreId = :firestoreId, sincronizado = 1 WHERE id = :id")
    void atualizarIdRemoto(int id, String firestoreId);

    @Deprecated
    @Query("SELECT * FROM tabela_assinaturas")
    List<Assinatura> getAllAssinaturas();
}
