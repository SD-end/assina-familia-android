package com.example.assinafamilia.view.main;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.assinafamilia.R;
import com.example.assinafamilia.databinding.DialogHistoricoNotificacoesBinding;
import com.example.assinafamilia.databinding.FragmentHomeBinding;
import com.example.assinafamilia.model.Assinatura;
import com.example.assinafamilia.model.Cobranca;
import com.example.assinafamilia.model.ResumoDashboard;
import com.example.assinafamilia.util.Resource;
import com.example.assinafamilia.view.adapter.CobrancaAdapter;
import com.example.assinafamilia.view.adapter.MoradorResumoAdapter;
import com.example.assinafamilia.view.adapter.NotificacaoHistoricoAdapter;
import com.example.assinafamilia.viewmodel.DashboardViewModel;
import com.example.assinafamilia.worker.WorkScheduler;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class HomeFragment extends Fragment {

    private static final Locale LOCALE_PT_BR = new Locale("pt", "BR");

    private FragmentHomeBinding binding;
    private DashboardViewModel dashboardViewModel;
    private MoradorResumoAdapter moradorResumoAdapter;
    private CobrancaAdapter cobrancaAdapter;
    private final Map<Integer, Assinatura> mapaAssinaturas = new HashMap<>();
    private ResumoDashboard ultimoResumo;
    private List<Cobranca> ultimasCobrancas;
    private List<Cobranca> historicoNotificacoes;
    private final Set<Integer> idsNaoLidasSessaoModal = new HashSet<>();

    @Nullable
    private AlertDialog dialogNotificacoes;

    @Nullable
    private DialogHistoricoNotificacoesBinding bindingDialogNotificacoes;

    @Nullable
    private NotificacaoHistoricoAdapter notificacaoHistoricoAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        dashboardViewModel = new ViewModelProvider(this).get(DashboardViewModel.class);

        configurarCabecalho();
        configurarAdaptadores();
        configurarAcoes();
        observarDados();
    }

    @Override
    public void onDestroyView() {
        if (dialogNotificacoes != null && dialogNotificacoes.isShowing()) {
            dialogNotificacoes.dismiss();
        }
        dialogNotificacoes = null;
        bindingDialogNotificacoes = null;
        notificacaoHistoricoAdapter = null;
        binding = null;
        super.onDestroyView();
    }

    private void configurarCabecalho() {
        binding.txtSaudacao.setText(getString(
                R.string.formato_ola_usuario,
                dashboardViewModel.obterPrimeiroNomeUsuario()));

        if (dashboardViewModel.isGestor()) {
            binding.txtSeloPerfil.setText(R.string.selo_gestor);
            binding.txtSeloPerfil.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.cor_verde_escuro));
            binding.txtSeloPerfil.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.cor_verde_claro)));
            binding.layoutSecaoGestor.setVisibility(View.VISIBLE);
            binding.layoutSecaoMembro.setVisibility(View.GONE);
        } else {
            binding.txtSeloPerfil.setText(R.string.selo_membro);
            binding.txtSeloPerfil.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.cor_selo_membro));
            binding.txtSeloPerfil.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.cor_selo_membro_fundo)));
            binding.layoutSecaoGestor.setVisibility(View.GONE);
            binding.layoutSecaoMembro.setVisibility(View.VISIBLE);
        }
    }

    private void configurarAdaptadores() {
        moradorResumoAdapter = new MoradorResumoAdapter(
                new MoradorResumoAdapter.OnMoradorResumoClickListener() {
                    @Override
                    public void onMoradorClick(@NonNull ResumoDashboard.CustoMorador morador) {
                        if (binding == null) {
                            return;
                        }
                        Snackbar.make(binding.getRoot(), String.format(LOCALE_PT_BR,
                                getString(R.string.formato_detalhe_custo_morador),
                                morador.getNomeMorador(),
                                morador.getCustoTotalMorador(),
                                morador.getTotalIndividual(),
                                morador.getFracaoGeral()), Snackbar.LENGTH_LONG).show();
                    }

                    @Override
                    public void onAdicionarMembroClick() {
                        if (binding == null) {
                            return;
                        }
                        try {
                            Class<?> destino = Class.forName(
                                    "com.example.assinafamilia.view.familia.NovoMoradorActivity");
                            startActivity(new Intent(requireContext(), destino));
                        } catch (ClassNotFoundException e) {
                            Snackbar.make(binding.getRoot(),
                                    R.string.aviso_tela_em_breve, Snackbar.LENGTH_SHORT).show();
                        }
                    }
                });
        binding.rvMoradoresResumo.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        binding.rvMoradoresResumo.setAdapter(moradorResumoAdapter);

        cobrancaAdapter = new CobrancaAdapter(new CobrancaAdapter.OnCobrancaClickListener() {
            @Override
            public void onConfirmarPagamentoClick(@NonNull Cobranca cobranca) {
                if (binding == null) {
                    return;
                }
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.titulo_confirmar_pagamento)
                        .setMessage(R.string.msg_confirmar_pagamento)
                        .setNegativeButton(R.string.acao_cancelar, null)
                        .setPositiveButton(R.string.acao_confirmar,
                                (dialog, which) -> confirmarPagamento(cobranca))
                        .show();
            }

            @Override
            public void onCobrancaClick(@NonNull Cobranca cobranca) {
                if (binding == null) {
                    return;
                }
                try {
                    Class<?> destino = Class.forName(
                            "com.example.assinafamilia.view.assinatura.DetalheAssinaturaActivity");
                    Intent intent = new Intent(requireContext(), destino);
                    intent.putExtra("EXTRA_ASSINATURA_ID", cobranca.getAssinaturaId());
                    startActivity(intent);
                } catch (ClassNotFoundException e) {
                    Snackbar.make(binding.getRoot(),
                            R.string.aviso_tela_em_breve, Snackbar.LENGTH_SHORT).show();
                }
            }
        });
        binding.rvProximasCobrancas.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvProximasCobrancas.setAdapter(cobrancaAdapter);
    }

    private void configurarAcoes() {
        binding.btnNotificacoes.setOnClickListener(v -> abrirModalHistoricoNotificacoes());

        binding.cardIndividuaisMembro.setOnClickListener(v -> navegarParaAssinaturas());
        binding.cardParteCasaMembro.setOnClickListener(v -> navegarParaAssinaturas());

        binding.swipeAtualizarHome.setColorSchemeResources(R.color.cor_verde_primario);
        binding.swipeAtualizarHome.setOnRefreshListener(this::sincronizarAgora);
    }

    private void navegarParaAssinaturas() {
        if (requireActivity() instanceof MainContainerActivity) {
            ((MainContainerActivity) requireActivity()).navegarParaAba(R.id.nav_assinaturas);
        }
    }

    private void observarDados() {
        dashboardViewModel.observarResumoDashboard().observe(getViewLifecycleOwner(), resumo -> {
            if (binding == null || resumo == null) {
                return;
            }
            ultimoResumo = resumo;

            if (dashboardViewModel.isGestor()) {
                binding.txtTituloCardPrincipal.setText(R.string.titulo_gasto_familia);
                binding.txtValorPrincipal.setText(String.format(LOCALE_PT_BR,
                        getString(R.string.formato_valor_reais),
                        resumo.getTotalGastoFamilia()));
            } else {
                binding.txtTituloCardPrincipal.setText(R.string.titulo_seu_custo_mensal);
                binding.txtValorPrincipal.setText(String.format(LOCALE_PT_BR,
                        getString(R.string.formato_valor_reais),
                        resumo.getMeuCustoTotal()));
            }

            int quantidadeAtivas = resumo.getQuantidadeAssinaturasAtivas();
            binding.txtQuantidadeAtivas.setText(quantidadeAtivas == 1
                    ? getString(R.string.formato_assinatura_ativa_singular)
                    : getString(R.string.formato_assinaturas_ativas, quantidadeAtivas));

            if (dashboardViewModel.isGestor()) {
                moradorResumoAdapter.atualizarDados(resumo.getDetalhamentoMoradores(), true);
            } else {
                binding.txtValorIndividuaisMembro.setText(String.format(LOCALE_PT_BR,
                        getString(R.string.formato_valor_mes),
                        resumo.getMeuTotalIndividual()));
                binding.txtValorParteCasaMembro.setText(String.format(LOCALE_PT_BR,
                        getString(R.string.formato_valor_mes),
                        resumo.getFracaoRateioPorMembro()));
            }

            atualizarListaCobrancas();
            atualizarConteudoModalNotificacoes();
        });

        dashboardViewModel.observarAssinaturasVisiveis().observe(getViewLifecycleOwner(), assinaturas -> {
            if (binding == null) {
                return;
            }
            mapaAssinaturas.clear();
            if (assinaturas != null) {
                for (Assinatura assinatura : assinaturas) {
                    if (assinatura != null) {
                        mapaAssinaturas.put(assinatura.getId(), assinatura);
                    }
                }
            }
            atualizarListaCobrancas();
            atualizarConteudoModalNotificacoes();
        });

        dashboardViewModel.observarProximasCobrancasVisiveis().observe(getViewLifecycleOwner(),
                cobrancas -> {
                    if (binding == null) {
                        return;
                    }
                    ultimasCobrancas = cobrancas;
                    atualizarListaCobrancas();
                });

        dashboardViewModel.observarHistoricoNotificacoesVisiveis().observe(
                getViewLifecycleOwner(), cobrancas -> {
                    historicoNotificacoes = cobrancas;
                    atualizarConteudoModalNotificacoes();
                });

        dashboardViewModel.observarQuantidadeNotificacoesNaoLidas().observe(
                getViewLifecycleOwner(), quantidade -> {
                    if (binding == null) {
                        return;
                    }
                    if (quantidade != null && quantidade > 0) {
                        binding.txtBadgeNotificacoes.setVisibility(View.VISIBLE);
                        binding.txtBadgeNotificacoes.setText(
                                quantidade > 99 ? "99+" : String.valueOf(quantidade));
                    } else {
                        binding.txtBadgeNotificacoes.setVisibility(View.GONE);
                    }
                });

        dashboardViewModel.observarQuantidadeCobrancasAtrasadas().observe(
                getViewLifecycleOwner(), atrasadas -> {
                    if (binding == null) {
                        return;
                    }
                    if (atrasadas != null && atrasadas > 0) {
                        binding.txtBadgeAtrasadas.setVisibility(View.VISIBLE);
                        binding.txtBadgeAtrasadas.setText(
                                atrasadas > 99 ? "99+" : String.valueOf(atrasadas));
                    } else {
                        binding.txtBadgeAtrasadas.setVisibility(View.GONE);
                    }
                });
    }

    private void atualizarListaCobrancas() {
        if (binding == null) {
            return;
        }

        int moradoresAtivos = ultimoResumo != null
                ? Math.max(1, ultimoResumo.getQuantidadeMoradoresAtivos())
                : 1;
        boolean vazia = ultimasCobrancas == null || ultimasCobrancas.isEmpty();

        binding.txtContadorCobrancas.setText(getString(
                R.string.formato_cobrancas_agendadas, vazia ? 0 : ultimasCobrancas.size()));

        if (vazia) {
            binding.rvProximasCobrancas.setVisibility(View.GONE);
            binding.incluirEstadoVazioCobrancas.getRoot().setVisibility(View.VISIBLE);
            binding.incluirEstadoVazioCobrancas.txtTituloVazio.setText(
                    R.string.titulo_sem_cobrancas_pendentes);
            binding.incluirEstadoVazioCobrancas.txtDescricaoVazio.setText(
                    R.string.desc_sem_cobrancas_pendentes);
            binding.incluirEstadoVazioCobrancas.btnAcaoVazio.setVisibility(View.GONE);
        } else {
            binding.rvProximasCobrancas.setVisibility(View.VISIBLE);
            binding.incluirEstadoVazioCobrancas.getRoot().setVisibility(View.GONE);
            cobrancaAdapter.atualizarDados(
                    ultimasCobrancas,
                    mapaAssinaturas,
                    dashboardViewModel.isGestor(),
                    moradoresAtivos,
                    false);
        }
    }

    private void abrirModalHistoricoNotificacoes() {
        if (binding == null) {
            return;
        }
        if (dialogNotificacoes != null && dialogNotificacoes.isShowing()) {
            return;
        }

        WorkScheduler.executarVerificacaoVencimentosImediata(requireContext());

        idsNaoLidasSessaoModal.clear();
        if (historicoNotificacoes != null) {
            for (Cobranca cobranca : historicoNotificacoes) {
                if (cobranca != null && !dashboardViewModel.isNotificacaoVisualizada(cobranca)) {
                    idsNaoLidasSessaoModal.add(cobranca.getId());
                }
            }
        }
        dashboardViewModel.marcarNotificacoesComoVisualizadas();

        bindingDialogNotificacoes = DialogHistoricoNotificacoesBinding.inflate(getLayoutInflater());
        notificacaoHistoricoAdapter = new NotificacaoHistoricoAdapter(cobranca -> {
            if (dialogNotificacoes != null) {
                dialogNotificacoes.dismiss();
            }
            try {
                Class<?> destino = Class.forName(
                        "com.example.assinafamilia.view.assinatura.DetalheAssinaturaActivity");
                Intent intent = new Intent(requireContext(), destino);
                intent.putExtra("EXTRA_ASSINATURA_ID", cobranca.getAssinaturaId());
                startActivity(intent);
            } catch (ClassNotFoundException e) {
                if (binding != null) {
                    Snackbar.make(binding.getRoot(),
                            R.string.aviso_tela_em_breve, Snackbar.LENGTH_SHORT).show();
                }
            }
        });

        bindingDialogNotificacoes.rvHistoricoNotificacoes.setLayoutManager(
                new LinearLayoutManager(requireContext()));
        bindingDialogNotificacoes.rvHistoricoNotificacoes.setAdapter(notificacaoHistoricoAdapter);

        bindingDialogNotificacoes.btnVerificarVencimentosDialog.setOnClickListener(v -> {
            WorkScheduler.executarVerificacaoVencimentosImediata(requireContext());
            if (binding != null) {
                Snackbar.make(binding.getRoot(),
                        R.string.aviso_verificacao_vencimentos, Snackbar.LENGTH_SHORT).show();
            }
        });
        bindingDialogNotificacoes.btnFecharDialogNotificacoes.setOnClickListener(v -> {
            if (dialogNotificacoes != null) {
                dialogNotificacoes.dismiss();
            }
        });

        atualizarConteudoModalNotificacoes();

        dialogNotificacoes = new MaterialAlertDialogBuilder(requireContext())
                .setView(bindingDialogNotificacoes.getRoot())
                .create();
        dialogNotificacoes.setOnDismissListener(dialog -> {
            bindingDialogNotificacoes = null;
            notificacaoHistoricoAdapter = null;
            dialogNotificacoes = null;
            idsNaoLidasSessaoModal.clear();
        });
        dialogNotificacoes.show();
    }

    private void atualizarConteudoModalNotificacoes() {
        if (bindingDialogNotificacoes == null || notificacaoHistoricoAdapter == null) {
            return;
        }

        int total = historicoNotificacoes != null ? historicoNotificacoes.size() : 0;
        bindingDialogNotificacoes.txtContadorDialogNotificacoes.setText(total == 1
                ? getString(R.string.formato_contador_notificacao_singular)
                : getString(R.string.formato_contador_notificacoes, total));

        if (total == 0) {
            bindingDialogNotificacoes.rvHistoricoNotificacoes.setVisibility(View.GONE);
            bindingDialogNotificacoes.incluirEstadoVazioNotificacoes.getRoot()
                    .setVisibility(View.VISIBLE);
            bindingDialogNotificacoes.incluirEstadoVazioNotificacoes.txtTituloVazio.setText(
                    R.string.titulo_sem_notificacoes);
            bindingDialogNotificacoes.incluirEstadoVazioNotificacoes.txtDescricaoVazio.setText(
                    R.string.desc_sem_notificacoes);
            bindingDialogNotificacoes.incluirEstadoVazioNotificacoes.btnAcaoVazio
                    .setVisibility(View.GONE);
        } else {
            bindingDialogNotificacoes.rvHistoricoNotificacoes.setVisibility(View.VISIBLE);
            bindingDialogNotificacoes.incluirEstadoVazioNotificacoes.getRoot()
                    .setVisibility(View.GONE);
            int moradoresAtivos = ultimoResumo != null
                    ? Math.max(1, ultimoResumo.getQuantidadeMoradoresAtivos())
                    : 1;
            notificacaoHistoricoAdapter.atualizarDados(
                    historicoNotificacoes,
                    mapaAssinaturas,
                    idsNaoLidasSessaoModal,
                    dashboardViewModel.isGestor(),
                    moradoresAtivos);
        }
    }

    private void confirmarPagamento(@NonNull Cobranca cobranca) {
        if (binding == null) {
            return;
        }
        binding.pbCarregandoHome.setVisibility(View.VISIBLE);

        dashboardViewModel.confirmarPagamentoCobranca(cobranca)
                .observe(getViewLifecycleOwner(), recurso -> {
                    if (binding == null || recurso == null) {
                        return;
                    }

                    if (recurso.getStatus() == Resource.Status.CARREGANDO) {
                        binding.pbCarregandoHome.setVisibility(View.VISIBLE);
                    } else if (recurso.getStatus() == Resource.Status.SUCESSO) {
                        binding.pbCarregandoHome.setVisibility(View.GONE);
                        if (recurso.isFallbackOffline()) {
                            String mensagem = recurso.getMensagem() != null
                                    ? recurso.getMensagem()
                                    : getString(R.string.sucesso_pagamento_confirmado);
                            Snackbar.make(binding.getRoot(), mensagem, Snackbar.LENGTH_LONG).show();
                        } else {
                            Snackbar.make(binding.getRoot(),
                                    R.string.sucesso_pagamento_confirmado, Snackbar.LENGTH_LONG).show();
                        }
                    } else if (recurso.getStatus() == Resource.Status.ERRO) {
                        binding.pbCarregandoHome.setVisibility(View.GONE);
                        String mensagem = recurso.getMensagem() != null
                                ? recurso.getMensagem()
                                : getString(R.string.estado_vazio_descricao);
                        Snackbar.make(binding.getRoot(), mensagem, Snackbar.LENGTH_LONG).show();
                    }
                });
    }

    private void sincronizarAgora() {
        if (binding == null) {
            return;
        }
        binding.swipeAtualizarHome.setRefreshing(true);

        dashboardViewModel.sincronizarDados().observe(getViewLifecycleOwner(), recurso -> {
            if (binding == null || recurso == null) {
                return;
            }

            if (recurso.getStatus() == Resource.Status.CARREGANDO) {
                binding.swipeAtualizarHome.setRefreshing(true);
            } else if (recurso.getStatus() == Resource.Status.SUCESSO) {
                binding.swipeAtualizarHome.setRefreshing(false);
                if (recurso.isFallbackOffline() && recurso.getMensagem() != null) {
                    Snackbar.make(binding.getRoot(), recurso.getMensagem(),
                            Snackbar.LENGTH_LONG).show();
                }
            } else if (recurso.getStatus() == Resource.Status.ERRO) {
                binding.swipeAtualizarHome.setRefreshing(false);
                String mensagem = recurso.getMensagem() != null
                        ? recurso.getMensagem()
                        : getString(R.string.estado_vazio_descricao);
                Snackbar.make(binding.getRoot(), mensagem, Snackbar.LENGTH_LONG).show();
            }
        });
    }
}
