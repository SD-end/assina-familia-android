package com.example.assinafamilia.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.assinafamilia.model.CotacaoCache;

@Dao
public interface CotacaoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void salvarCotacao(CotacaoCache cotacao);

    @Query("SELECT * FROM cotacoes_cache WHERE parMoeda = :parMoeda LIMIT 1")
    CotacaoCache obterUltimaCotacao(String parMoeda);

    @Query("SELECT * FROM cotacoes_cache WHERE parMoeda = :parMoeda LIMIT 1")
    LiveData<CotacaoCache> observarCotacao(String parMoeda);
}
