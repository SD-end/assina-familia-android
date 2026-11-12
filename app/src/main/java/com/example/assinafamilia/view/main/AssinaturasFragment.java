package com.example.assinafamilia.view.main;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.assinafamilia.R;
import com.example.assinafamilia.databinding.FragmentAssinaturasBinding;
import com.example.assinafamilia.model.Assinatura;
import com.example.assinafamilia.model.Categoria;
import com.example.assinafamilia.model.Usuario;
import com.example.assinafamilia.view.AssinaturaAdapter;
import com.example.assinafamilia.view.NovaAssinaturaActivity;
import com.example.assinafamilia.viewmodel.AssinaturaViewModel;
import com.example.assinafamilia.worker.WorkScheduler;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AssinaturasFragment extends Fragment {

    private static final Locale LOCALE_PT_BR = new Locale("pt", "BR");

    private FragmentAssinaturasBinding binding;
    private AssinaturaViewModel assinaturaViewModel;
    private AssinaturaAdapter assinaturaAdapter;
    private final Map<Integer, String> mapaNomesDonos = new HashMap<>();
    private final Map<Integer, String> mapaNomesCategorias = new HashMap<>();
    private List<Assinatura> ultimasAssinaturas;
    private int quantidadeMoradoresAtivos = 1;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAssinaturasBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        assinaturaViewModel = new ViewModelProvider(this).get(AssinaturaViewModel.class);

        configurarCabecalho();
        configurarAdaptador();
        configurarAcoes();
        configurarBusca();
        configurarChips();
        observarDados();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void configurarCabecalho() {
        boolean isGestor = assinaturaViewModel.isGestor();
        binding.txtSobretituloGestao.setVisibility(isGestor ? View.VISIBLE : View.GONE);
        binding.tilFiltroMorador.setVisibility(isGestor ? View.VISIBLE : View.GONE);
    }

    private void configurarAdaptador() {
        assinaturaAdapter = new AssinaturaAdapter(assinatura -> {
            if (binding == null) {
                return;
            }
            try {
                Class<?> destino = Class.forName(
                        "com.example.assinafamilia.view.assinatura.DetalheAssinaturaActivity");
                Intent intent = new Intent(requireContext(), destino);
                intent.putExtra("EXTRA_ASSINATURA_ID", assinatura.getId());
                startActivity(intent);
            } catch (ClassNotFoundException e) {
                Snackbar.make(binding.layoutRaizAssinaturas,
                        R.string.aviso_tela_em_breve, Snackbar.LENGTH_SHORT).show();
            }
        });
        binding.rvAssinaturas.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvAssinaturas.setAdapter(assinaturaAdapter);
    }

    private void configurarAcoes() {
        binding.btnNotificacoesAssinaturas.setOnClickListener(v -> {
            WorkScheduler.executarVerificacaoVencimentosImediata(requireContext());
            Snackbar.make(binding.layoutRaizAssinaturas,
                    R.string.aviso_verificacao_vencimentos, Snackbar.LENGTH_SHORT).show();
        });

        binding.fabAdicionarAssinatura.setOnClickListener(v -> abrirNovaAssinatura());
    }

    private void configurarBusca() {
        binding.edtBuscaAssinatura.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                assinaturaViewModel.definirTermoBusca(s != null ? s.toString() : "");
            }
        });
    }

    private void configurarChips() {
        String filtroAtual = assinaturaViewModel.obterFiltroTipoUsoAtual();
        if (AssinaturaViewModel.FILTRO_USO_CASA.equals(filtroAtual)) {
            binding.chipCasaAssinaturas.setChecked(true);
        } else if (AssinaturaViewModel.FILTRO_USO_INDIVIDUAL.equals(filtroAtual)) {
            binding.chipIndividuaisAssinaturas.setChecked(true);
        } else {
            binding.chipTodasAssinaturas.setChecked(true);
        }
        binding.chipInativasAssinaturas.setChecked(assinaturaViewModel.isMostrarInativas());

        binding.grupoChipsUsoAssinaturas.setOnCheckedStateChangeListener((grupo, idsMarcados) -> {
            if (idsMarcados.isEmpty()) {
                return;
            }
            int idChip = idsMarcados.get(0);
            if (idChip == R.id.chipCasaAssinaturas) {
                assinaturaViewModel.definirFiltroTipoUso(AssinaturaViewModel.FILTRO_USO_CASA);
            } else if (idChip == R.id.chipIndividuaisAssinaturas) {
                assinaturaViewModel.definirFiltroTipoUso(
                        AssinaturaViewModel.FILTRO_USO_INDIVIDUAL);
            } else {
                assinaturaViewModel.definirFiltroTipoUso(AssinaturaViewModel.FILTRO_USO_TODAS);
            }
        });

        binding.chipInativasAssinaturas.setOnCheckedChangeListener(
                (botao, marcado) -> assinaturaViewModel.definirMostrarInativas(marcado));
    }

    private void observarDados() {
        assinaturaViewModel.listarMoradoresDaFamilia().observe(getViewLifecycleOwner(),
                moradores -> {
                    if (binding == null) {
                        return;
                    }

                    mapaNomesDonos.clear();
                    if (moradores != null) {
                        for (Usuario usuario : moradores) {
                            if (usuario != null) {
                                mapaNomesDonos.put(usuario.getId(),
                                        obterPrimeiroNome(usuario.getNome()));
                            }
                        }
                    }

                    if (assinaturaViewModel.isGestor()) {
                        configurarDropdownMoradores(moradores);
                    }

                    atualizarListaEestadoVazio();
                });

        assinaturaViewModel.listarTodasCategorias().observe(getViewLifecycleOwner(),
                categorias -> {
                    if (binding == null) {
                        return;
                    }

                    mapaNomesCategorias.clear();
                    if (categorias != null) {
                        for (Categoria categoria : categorias) {
                            if (categoria != null) {
                                mapaNomesCategorias.put(categoria.getId(), categoria.getNome());
                            }
                        }
                    }

                    atualizarListaEestadoVazio();
                });

        assinaturaViewModel.listarMoradoresAtivos().observe(getViewLifecycleOwner(), moradores -> {
            if (binding == null) {
                return;
            }

            quantidadeMoradoresAtivos = Math.max(1, moradores != null ? moradores.size() : 1);
            atualizarListaEestadoVazio();
        });

        assinaturaViewModel.observarAssinaturasFiltradas().observe(getViewLifecycleOwner(),
                assinaturas -> {
                    if (binding == null) {
                        return;
                    }

                    ultimasAssinaturas = assinaturas;
                    atualizarListaEestadoVazio();
                });

        assinaturaViewModel.observarTotalMensalFiltrado().observe(getViewLifecycleOwner(), total -> {
            if (binding == null) {
                return;
            }

            binding.txtTotalMensalAssinaturas.setText(String.format(LOCALE_PT_BR,
                    getString(R.string.formato_valor_reais), total != null ? total : 0f));
        });

        assinaturaViewModel.observarQuantidadeAtivasFiltradas().observe(getViewLifecycleOwner(),
                valor -> {
                    if (binding == null) {
                        return;
                    }

                    int quantidade = valor != null ? valor : 0;
                    if (assinaturaViewModel.isMostrarInativas()) {
                        binding.txtContadorAssinaturas.setText(
                                getString(R.string.formato_inativas_contador, quantidade));
                        binding.txtContadorAssinaturas.setTextColor(
                                ContextCompat.getColor(requireContext(),
                                        R.color.cor_texto_secundario));
                        ViewCompat.setBackgroundTintList(binding.txtContadorAssinaturas,
                                ColorStateList.valueOf(ContextCompat.getColor(requireContext(),
                                        R.color.cor_fundo_cartao)));
                    } else {
                        binding.txtContadorAssinaturas.setText(
                                getString(R.string.formato_ativas_contador, quantidade));
                        binding.txtContadorAssinaturas.setTextColor(
                                ContextCompat.getColor(requireContext(),
                                        R.color.cor_verde_escuro));
                        ViewCompat.setBackgroundTintList(binding.txtContadorAssinaturas,
                                ColorStateList.valueOf(ContextCompat.getColor(requireContext(),
                                        R.color.cor_verde_claro)));
                    }
                });
    }

    private void configurarDropdownMoradores(@Nullable List<Usuario> moradores) {
        List<String> rotulos = new ArrayList<>();
        List<Integer> idsDropdown = new ArrayList<>();

        rotulos.add(getString(R.string.formato_filtro_morador,
                getString(R.string.filtro_todos_membros)));
        idsDropdown.add(AssinaturaViewModel.MORADOR_TODOS);

        if (moradores != null) {
            for (Usuario usuario : moradores) {
                if (usuario == null) {
                    continue;
                }
                rotulos.add(getString(R.string.formato_filtro_morador,
                        usuario.getNome() != null ? usuario.getNome() : ""));
                idsDropdown.add(usuario.getId());
            }
        }

        ArrayAdapter<String> adaptador = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, rotulos);
        binding.dropdownFiltroMorador.setAdapter(adaptador);

        int posicaoSelecionada = idsDropdown.indexOf(assinaturaViewModel.obterMoradorFiltroId());
        if (posicaoSelecionada < 0) {
            posicaoSelecionada = 0;
        }
        binding.dropdownFiltroMorador.setText(rotulos.get(posicaoSelecionada), false);
        binding.dropdownFiltroMorador.setOnItemClickListener(
                (parent, view, position, id) -> assinaturaViewModel.definirMoradorFiltroId(
                        idsDropdown.get(position)));
    }

    private void atualizarListaEestadoVazio() {
        if (binding == null) {
            return;
        }

        assinaturaAdapter.atualizarDados(
                ultimasAssinaturas,
                mapaNomesDonos,
                mapaNomesCategorias,
                assinaturaViewModel.isGestor(),
                quantidadeMoradoresAtivos);

        if (ultimasAssinaturas == null || ultimasAssinaturas.isEmpty()) {
            binding.rvAssinaturas.setVisibility(View.GONE);
            binding.incluirEstadoVazioAssinaturas.getRoot().setVisibility(View.VISIBLE);
            binding.incluirEstadoVazioAssinaturas.txtTituloVazio.setText(
                    R.string.titulo_sem_assinaturas);
            binding.incluirEstadoVazioAssinaturas.txtDescricaoVazio.setText(
                    R.string.desc_sem_assinaturas);

            String busca = binding.edtBuscaAssinatura.getText() != null
                    ? binding.edtBuscaAssinatura.getText().toString().trim()
                    : "";
            boolean semFiltros = !assinaturaViewModel.isMostrarInativas()
                    && AssinaturaViewModel.FILTRO_USO_TODAS.equals(
                    assinaturaViewModel.obterFiltroTipoUsoAtual())
                    && assinaturaViewModel.obterMoradorFiltroId()
                    == AssinaturaViewModel.MORADOR_TODOS
                    && busca.isEmpty();

            if (semFiltros) {
                binding.incluirEstadoVazioAssinaturas.btnAcaoVazio.setVisibility(View.VISIBLE);
                binding.incluirEstadoVazioAssinaturas.btnAcaoVazio.setText(
                        R.string.acao_nova_assinatura);
                binding.incluirEstadoVazioAssinaturas.btnAcaoVazio.setOnClickListener(
                        v -> abrirNovaAssinatura());
            } else {
                binding.incluirEstadoVazioAssinaturas.btnAcaoVazio.setVisibility(View.GONE);
            }
        } else {
            binding.rvAssinaturas.setVisibility(View.VISIBLE);
            binding.incluirEstadoVazioAssinaturas.getRoot().setVisibility(View.GONE);
        }
    }

    private void abrirNovaAssinatura() {
        if (binding == null) {
            return;
        }
        startActivity(new Intent(requireContext(), NovaAssinaturaActivity.class));
    }

    @NonNull
    private static String obterPrimeiroNome(@Nullable String nomeCompleto) {
        if (nomeCompleto == null) {
            return "";
        }
        String limpo = nomeCompleto.trim();
        if (limpo.isEmpty()) {
            return "";
        }
        int posicaoEspaco = limpo.indexOf(' ');
        return posicaoEspaco > 0 ? limpo.substring(0, posicaoEspaco) : limpo;
    }
}
