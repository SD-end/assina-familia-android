package com.example.assinafamilia.viewmodel;

import android.app.Application;
import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.assinafamilia.data.AppDatabase;
import com.example.assinafamilia.data.repository.CategoriaRepository;
import com.example.assinafamilia.model.Categoria;
import com.example.assinafamilia.util.Resource;
import com.example.assinafamilia.util.SessionManager;

import java.util.ArrayList;
import java.util.List;

public class CategoriaViewModel extends AndroidViewModel {

    public static final String FILTRO_TODAS = "TODAS";
    public static final String FILTRO_ATIVAS = "ATIVAS";
    public static final String FILTRO_INATIVAS = "INATIVAS";

    private static final int TAMANHO_MAXIMO_NOME = 100;
    private static final int TAMANHO_MAXIMO_DESCRICAO = 300;

    private final CategoriaRepository categoriaRepository;
    private final SessionManager sessionManager;
    private final MutableLiveData<String> filtroStatusLiveData = new MutableLiveData<>(FILTRO_TODAS);
    private final LiveData<List<Categoria>> todasCategoriasLiveData;
    private final LiveData<List<Categoria>> categoriasAtivasLiveData;
    private final MediatorLiveData<List<Categoria>> categoriasFiltradasLiveData = new MediatorLiveData<>();

    public CategoriaViewModel(@NonNull Application application) {
        super(application);
        Context contexto = application.getApplicationContext();
        this.categoriaRepository = new CategoriaRepository(contexto);
        this.sessionManager = new SessionManager(contexto);

        String familiaId = obterFamiliaIdAtivo();
        this.todasCategoriasLiveData = categoriaRepository.listarTodas(familiaId);
        this.categoriasAtivasLiveData = categoriaRepository.listarAtivas(familiaId);

        categoriasFiltradasLiveData.addSource(todasCategoriasLiveData,
                categorias -> atualizarListaFiltrada(categorias, obterFiltroStatusAtual()));
        categoriasFiltradasLiveData.addSource(filtroStatusLiveData,
                filtro -> atualizarListaFiltrada(todasCategoriasLiveData.getValue(), filtro));
    }

    @NonNull
    public LiveData<List<Categoria>> observarCategoriasFiltradas() {
        return categoriasFiltradasLiveData;
    }

    @NonNull
    public LiveData<List<Categoria>> listarCategoriasAtivas() {
        return categoriasAtivasLiveData;
    }

    public void definirFiltroStatus(@Nullable String filtro) {
        String novoFiltro = normalizarFiltro(filtro);
        if (!novoFiltro.equals(filtroStatusLiveData.getValue())) {
            filtroStatusLiveData.setValue(novoFiltro);
        }
    }

    @NonNull
    public String obterFiltroStatusAtual() {
        return normalizarFiltro(filtroStatusLiveData.getValue());
    }

    @NonNull
    public LiveData<Resource<Categoria>> salvarCategoria(@Nullable Categoria categoriaExistente,
                                                         @Nullable String nome,
                                                         @Nullable String descricao) {
        String nomeLimpo = nome != null ? nome.trim() : "";
        String descricaoLimpa = descricao != null ? descricao.trim() : "";

        if (nomeLimpo.isEmpty() || nomeLimpo.length() > TAMANHO_MAXIMO_NOME) {
            return new MutableLiveData<>(
                    Resource.erro("O nome da categoria é obrigatório (até 100 caracteres).", null));
        }
        if (descricaoLimpa.length() > TAMANHO_MAXIMO_DESCRICAO) {
            return new MutableLiveData<>(
                    Resource.erro("A descrição deve ter no máximo 300 caracteres.", null));
        }

        Categoria alvo;
        if (categoriaExistente != null) {
            alvo = categoriaExistente;
            alvo.setNome(nomeLimpo);
            alvo.setDescricao(descricaoLimpa);
        } else {
            alvo = new Categoria(null, obterFamiliaIdAtivo(), nomeLimpo, descricaoLimpa);
            alvo.setAtiva(true);
        }

        return categoriaRepository.salvarCategoria(alvo);
    }

    @NonNull
    public LiveData<Resource<Boolean>> alterarStatusCategoria(@NonNull Categoria categoria,
                                                              boolean ativa) {
        return categoriaRepository.alterarStatusCategoria(categoria, ativa);
    }

    @NonNull
    public LiveData<Resource<Boolean>> excluirCategoria(@NonNull Categoria categoria) {
        return categoriaRepository.excluirCategoria(categoria);
    }

    public boolean isGestor() {
        return sessionManager.isGestor();
    }

    private void atualizarListaFiltrada(@Nullable List<Categoria> categorias,
                                        @Nullable String filtro) {
        if (categorias == null) {
            categoriasFiltradasLiveData.setValue(new ArrayList<>());
            return;
        }

        String filtroNormalizado = normalizarFiltro(filtro);
        if (FILTRO_TODAS.equals(filtroNormalizado)) {
            categoriasFiltradasLiveData.setValue(new ArrayList<>(categorias));
            return;
        }

        boolean exigirAtiva = FILTRO_ATIVAS.equals(filtroNormalizado);
        List<Categoria> resultado = new ArrayList<>();
        for (Categoria categoria : categorias) {
            if (categoria != null && categoria.isAtiva() == exigirAtiva) {
                resultado.add(categoria);
            }
        }
        categoriasFiltradasLiveData.setValue(resultado);
    }

    @NonNull
    private static String normalizarFiltro(@Nullable String filtro) {
        if (FILTRO_ATIVAS.equalsIgnoreCase(filtro)) {
            return FILTRO_ATIVAS;
        }
        if (FILTRO_INATIVAS.equalsIgnoreCase(filtro)) {
            return FILTRO_INATIVAS;
        }
        return FILTRO_TODAS;
    }

    @NonNull
    private String obterFamiliaIdAtivo() {
        String familiaId = sessionManager.obterFamiliaId();
        return !TextUtils.isEmpty(familiaId) ? familiaId : AppDatabase.FAMILIA_ID_PADRAO;
    }
}
