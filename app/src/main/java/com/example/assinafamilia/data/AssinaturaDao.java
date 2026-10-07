package com.example.assinafamilia.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.assinafamilia.model.Assinatura;

import java.util.List;

@Dao
public interface AssinaturaDao {
    @Insert
    void insert(Assinatura assinatura);

    @Query("SELECT * FROM tabela_assinaturas")
    List<Assinatura> getAllAssinaturas();
}
