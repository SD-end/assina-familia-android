package com.example.assinafamilia.view.main;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.assinafamilia.R;
import com.example.assinafamilia.databinding.DialogFormularioCategoriaBinding;
import com.example.assinafamilia.databinding.FragmentCategoriasBinding;
import com.example.assinafamilia.model.Categoria;
import com.example.assinafamilia.util.Resource;
import com.example.assinafamilia.view.adapter.CategoriaAdapter;
import com.example.assinafamilia.viewmodel.CategoriaViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

public class CategoriasFragment extends Fragment {

    private FragmentCategoriasBinding binding;
    private CategoriaViewModel categoriaViewModel;
    private CategoriaAdapter categoriaAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentCategoriasBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        categoriaViewModel = new ViewModelProvider(this).get(CategoriaViewModel.class);

        configurarAcessoGestor();
        configurarAdaptador();
        configurarChips();
        observarCategorias();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void configurarAcessoGestor() {
        boolean isGestor = categoriaViewModel.isGestor();
        binding.fabAdicionarCategoria.setVisibility(isGestor ? View.VISIBLE : View.GONE);
        binding.fabAdicionarCategoria.setOnClickListener(v -> abrirDialogoFormulario(null));
    }

    private void configurarAdaptador() {
        categoriaAdapter = new CategoriaAdapter(new CategoriaAdapter.OnCategoriaClickListener() {
            @Override
            public void onEditarClick(@NonNull Categoria categoria) {
                if (categoriaViewModel.isGestor()) {
                    abrirDialogoFormulario(categoria);
                }
            }

            @Override
            public void onAlternarStatusClick(@NonNull Categoria categoria) {
                if (categoriaViewModel.isGestor()) {
                    alterarStatusCategoria(categoria, !categoria.isAtiva());
                }
            }

            @Override
            public void onExcluirClick(@NonNull Categoria categoria) {
                if (categoriaViewModel.isGestor()) {
                    confirmarExclusaoCategoria(categoria);
                }
            }
        });
        binding.rvCategorias.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvCategorias.setAdapter(categoriaAdapter);
    }

    private void configurarChips() {
        String filtroAtual = categoriaViewModel.obterFiltroStatusAtual();
        if (CategoriaViewModel.FILTRO_ATIVAS.equals(filtroAtual)) {
            binding.chipAtivasCategorias.setChecked(true);
        } else if (CategoriaViewModel.FILTRO_INATIVAS.equals(filtroAtual)) {
            binding.chipInativasCategorias.setChecked(true);
        } else {
            binding.chipTodasCategorias.setChecked(true);
        }

        binding.grupoChipsFiltroCategoria.setOnCheckedStateChangeListener((grupo, idsMarcados) -> {
            if (idsMarcados.isEmpty()) {
                return;
            }
            int idChip = idsMarcados.get(0);
            if (idChip == R.id.chipAtivasCategorias) {
                categoriaViewModel.definirFiltroStatus(CategoriaViewModel.FILTRO_ATIVAS);
            } else if (idChip == R.id.chipInativasCategorias) {
                categoriaViewModel.definirFiltroStatus(CategoriaViewModel.FILTRO_INATIVAS);
            } else if (idChip == R.id.chipTodasCategorias) {
                categoriaViewModel.definirFiltroStatus(CategoriaViewModel.FILTRO_TODAS);
            }
        });
    }

    private void observarCategorias() {
        categoriaViewModel.observarCategoriasFiltradas().observe(getViewLifecycleOwner(),
                categorias -> {
                    if (binding == null) {
                        return;
                    }

                    categoriaAdapter.atualizarDados(categorias, categoriaViewModel.isGestor());

                    if (categorias == null || categorias.isEmpty()) {
                        binding.rvCategorias.setVisibility(View.GONE);
                        binding.incluirEstadoVazioCategorias.getRoot().setVisibility(View.VISIBLE);
                        binding.incluirEstadoVazioCategorias.txtTituloVazio.setText(
                                R.string.titulo_sem_categorias);
                        binding.incluirEstadoVazioCategorias.txtDescricaoVazio.setText(
                                R.string.desc_sem_categorias);

                        if (categoriaViewModel.isGestor()
                                && CategoriaViewModel.FILTRO_TODAS.equals(
                                categoriaViewModel.obterFiltroStatusAtual())) {
                            binding.incluirEstadoVazioCategorias.btnAcaoVazio.setVisibility(
                                    View.VISIBLE);
                            binding.incluirEstadoVazioCategorias.btnAcaoVazio.setText(
                                    R.string.acao_nova_categoria);
                            binding.incluirEstadoVazioCategorias.btnAcaoVazio.setOnClickListener(
                                    v -> abrirDialogoFormulario(null));
                        } else {
                            binding.incluirEstadoVazioCategorias.btnAcaoVazio.setVisibility(
                                    View.GONE);
                        }
                    } else {
                        binding.rvCategorias.setVisibility(View.VISIBLE);
                        binding.incluirEstadoVazioCategorias.getRoot().setVisibility(View.GONE);
                    }
                });
    }

    private void abrirDialogoFormulario(@Nullable Categoria categoriaExistente) {
        if (binding == null || !categoriaViewModel.isGestor()) {
            return;
        }

        DialogFormularioCategoriaBinding dialogBinding =
                DialogFormularioCategoriaBinding.inflate(getLayoutInflater());

        if (categoriaExistente != null) {
            if (categoriaExistente.getNome() != null) {
                dialogBinding.edtNomeCategoria.setText(categoriaExistente.getNome());
            }
            if (categoriaExistente.getDescricao() != null) {
                dialogBinding.edtDescricaoCategoria.setText(categoriaExistente.getDescricao());
            }
        }

        AlertDialog dialogo = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(categoriaExistente == null
                        ? R.string.titulo_nova_categoria
                        : R.string.titulo_editar_categoria)
                .setView(dialogBinding.getRoot())
                .setNegativeButton(R.string.acao_cancelar, null)
                .setPositiveButton(R.string.acao_salvar, null)
                .create();

        dialogo.setOnShowListener(dialogoAberto -> {
            Button botaoSalvar = dialogo.getButton(DialogInterface.BUTTON_POSITIVE);
            botaoSalvar.setOnClickListener(v -> {
                dialogBinding.tilNomeCategoria.setError(null);
                dialogBinding.tilDescricaoCategoria.setError(null);

                String nome = dialogBinding.edtNomeCategoria.getText() != null
                        ? dialogBinding.edtNomeCategoria.getText().toString().trim()
                        : "";
                String descricao = dialogBinding.edtDescricaoCategoria.getText() != null
                        ? dialogBinding.edtDescricaoCategoria.getText().toString().trim()
                        : "";

                if (nome.isEmpty() || nome.length() > 100) {
                    dialogBinding.tilNomeCategoria.setError(
                            getString(R.string.erro_nome_categoria_obrigatorio));
                    return;
                }
                if (descricao.length() > 300) {
                    dialogBinding.tilDescricaoCategoria.setError(
                            getString(R.string.erro_descricao_categoria_tamanho));
                    return;
                }

                categoriaViewModel.salvarCategoria(categoriaExistente, nome, descricao)
                        .observe(getViewLifecycleOwner(), recurso -> {
                            if (recurso == null) {
                                return;
                            }

                            if (recurso.getStatus() == Resource.Status.CARREGANDO) {
                                botaoSalvar.setEnabled(false);
                                if (binding != null) {
                                    binding.pbCarregandoCategorias.setVisibility(View.VISIBLE);
                                }
                            } else if (recurso.getStatus() == Resource.Status.SUCESSO) {
                                botaoSalvar.setEnabled(true);
                                if (binding != null) {
                                    binding.pbCarregandoCategorias.setVisibility(View.GONE);
                                }
                                dialogo.dismiss();
                                if (binding != null) {
                                    String mensagem = recurso.isFallbackOffline()
                                            && recurso.getMensagem() != null
                                            ? recurso.getMensagem()
                                            : getString(R.string.sucesso_categoria_salva);
                                    Snackbar.make(binding.layoutRaizCategorias, mensagem,
                                            Snackbar.LENGTH_LONG).show();
                                }
                            } else {
                                botaoSalvar.setEnabled(true);
                                if (binding != null) {
                                    binding.pbCarregandoCategorias.setVisibility(View.GONE);
                                }
                                String mensagem = recurso.getMensagem() != null
                                        ? recurso.getMensagem()
                                        : getString(R.string.erro_operacao_categoria);
                                dialogBinding.tilNomeCategoria.setError(mensagem);
                            }
                        });
            });
        });

        dialogo.show();
    }

    private void alterarStatusCategoria(@NonNull Categoria categoria, boolean novaAtiva) {
        if (binding == null || !categoriaViewModel.isGestor()) {
            return;
        }

        categoriaViewModel.alterarStatusCategoria(categoria, novaAtiva)
                .observe(getViewLifecycleOwner(), recurso -> {
                    if (binding == null || recurso == null) {
                        return;
                    }

                    if (recurso.getStatus() == Resource.Status.CARREGANDO) {
                        binding.pbCarregandoCategorias.setVisibility(View.VISIBLE);
                    } else if (recurso.getStatus() == Resource.Status.SUCESSO) {
                        binding.pbCarregandoCategorias.setVisibility(View.GONE);
                        String mensagem = recurso.isFallbackOffline()
                                && recurso.getMensagem() != null
                                ? recurso.getMensagem()
                                : getString(novaAtiva
                                ? R.string.sucesso_categoria_ativada
                                : R.string.sucesso_categoria_inativada);
                        Snackbar.make(binding.layoutRaizCategorias, mensagem,
                                Snackbar.LENGTH_LONG).show();
                    } else {
                        binding.pbCarregandoCategorias.setVisibility(View.GONE);
                        String mensagem = recurso.getMensagem() != null
                                ? recurso.getMensagem()
                                : getString(R.string.erro_operacao_categoria);
                        Snackbar.make(binding.layoutRaizCategorias, mensagem,
                                Snackbar.LENGTH_LONG).show();
                    }
                });
    }

    private void confirmarExclusaoCategoria(@NonNull Categoria categoria) {
        if (binding == null || !categoriaViewModel.isGestor()) {
            return;
        }

        String nome = categoria.getNome() != null ? categoria.getNome() : "";
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.titulo_excluir_categoria)
                .setMessage(getString(R.string.formato_msg_excluir_categoria, nome))
                .setNegativeButton(R.string.acao_cancelar, null)
                .setPositiveButton(R.string.acao_excluir,
                        (dialog, which) -> excluirCategoria(categoria))
                .show();
    }

    private void excluirCategoria(@NonNull Categoria categoria) {
        if (binding == null || !categoriaViewModel.isGestor()) {
            return;
        }

        categoriaViewModel.excluirCategoria(categoria)
                .observe(getViewLifecycleOwner(), recurso -> {
                    if (binding == null || recurso == null) {
                        return;
                    }

                    if (recurso.getStatus() == Resource.Status.CARREGANDO) {
                        binding.pbCarregandoCategorias.setVisibility(View.VISIBLE);
                    } else if (recurso.getStatus() == Resource.Status.SUCESSO) {
                        binding.pbCarregandoCategorias.setVisibility(View.GONE);
                        String mensagem;
                        if (Boolean.TRUE.equals(recurso.getDados())) {
                            mensagem = getString(R.string.sucesso_categoria_excluida);
                        } else if (recurso.isFallbackOffline() && recurso.getMensagem() != null) {
                            mensagem = recurso.getMensagem();
                        } else {
                            mensagem = getString(R.string.aviso_categoria_inativada_por_vinculo);
                        }
                        Snackbar.make(binding.layoutRaizCategorias, mensagem,
                                Snackbar.LENGTH_LONG).show();
                    } else {
                        binding.pbCarregandoCategorias.setVisibility(View.GONE);
                        String mensagem = recurso.getMensagem() != null
                                ? recurso.getMensagem()
                                : getString(R.string.erro_operacao_categoria);
                        Snackbar snackbar = Snackbar.make(binding.layoutRaizCategorias, mensagem,
                                Snackbar.LENGTH_LONG);
                        if (categoria.isAtiva()) {
                            snackbar.setAction(R.string.acao_inativar,
                                    v -> alterarStatusCategoria(categoria, false));
                        }
                        snackbar.show();
                    }
                });
    }
}
