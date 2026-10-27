package com.example.assinafamilia.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.assinafamilia.model.Categoria;

import java.util.List;

@Dao
public interface CategoriaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long inserir(Categoria categoria);

    @Update
    void atualizar(Categoria categoria);

    @Delete
    void deletar(Categoria categoria);

    @Query("SELECT * FROM categorias WHERE id = :id LIMIT 1")
    Categoria buscarPorId(int id);

    @Query("SELECT * FROM categorias WHERE familiaId = :familiaId ORDER BY nome ASC")
    LiveData<List<Categoria>> listarTodas(String familiaId);

    @Query("SELECT * FROM categorias WHERE familiaId = :familiaId AND ativa = 1 ORDER BY nome ASC")
    LiveData<List<Categoria>> listarAtivas(String familiaId);

    @Query("UPDATE categorias SET ativa = :ativa, sincronizado = 0 WHERE id = :id")
    void alterarStatusAtiva(int id, boolean ativa);

    @Query("SELECT * FROM categorias WHERE sincronizado = 0")
    List<Categoria> listarPendentesSincronizacao();

    @Query("UPDATE categorias SET sincronizado = 1 WHERE id = :id")
    void marcarComoSincronizado(int id);
}
