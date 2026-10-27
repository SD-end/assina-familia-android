package com.example.assinafamilia.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.assinafamilia.model.Usuario;

import java.util.List;


@Dao
public interface UsuarioDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    long inserir(Usuario usuario);

    @Update
    void atualizar(Usuario usuario);

    @Query("SELECT * FROM usuarios WHERE id = :id LIMIT 1")
    Usuario buscarPorId(int id);

    @Query("SELECT * FROM usuarios WHERE email = :email LIMIT 1")
    Usuario buscarPorEmail(String email);

    @Query("SELECT * FROM usuarios WHERE email = :email AND senhaHash = :senhaHash LIMIT 1")
    Usuario autenticarOffline(String email, String senhaHash);

    @Query("SELECT * FROM usuarios WHERE familiaId = :familiaId ORDER BY nome ASC")
    LiveData<List<Usuario>> listarMoradoresDaFamilia(String familiaId);

    @Query("SELECT * FROM usuarios WHERE familiaId = :familiaId AND ativo = 1 ORDER BY nome ASC")
    LiveData<List<Usuario>> listarMoradoresAtivos(String familiaId);

    @Query("SELECT COUNT(*) FROM usuarios WHERE familiaId = :familiaId AND ativo = 1")
    int contarMoradoresAtivos(String familiaId);

    @Query("SELECT * FROM usuarios WHERE sincronizado = 0")
    List<Usuario> listarPendentesSincronizacao();

    @Query("UPDATE usuarios SET sincronizado = 1 WHERE id = :id")
    void marcarComoSincronizado(int id);
}
