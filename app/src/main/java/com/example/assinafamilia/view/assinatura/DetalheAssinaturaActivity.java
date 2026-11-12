package com.example.assinafamilia.view.assinatura;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.assinafamilia.R;
import com.example.assinafamilia.databinding.ActivityDetalheAssinaturaBinding;
import com.example.assinafamilia.model.Assinatura;
import com.example.assinafamilia.model.Categoria;
import com.example.assinafamilia.model.Cobranca;
import com.example.assinafamilia.model.Usuario;
import com.example.assinafamilia.util.DomainConstants;
import com.example.assinafamilia.util.Resource;
import com.example.assinafamilia.view.NovaAssinaturaActivity;
import com.example.assinafamilia.view.adapter.CobrancaAdapter;
import com.example.assinafamilia.viewmodel.AssinaturaViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DetalheAssinaturaActivity extends AppCompatActivity {

    public static final String EXTRA_ASSINATURA_ID = "EXTRA_ASSINATURA_ID";

    private static final Locale LOCALE_PT_BR = new Locale("pt", "BR");

    private ActivityDetalheAssinaturaBinding binding;
    private AssinaturaViewModel assinaturaViewModel;
    private CobrancaAdapter cobrancaAdapter;
    private final Map<Integer, String> mapaNomesCategorias = new HashMap<>();
    private final Map<Integer, String> mapaNomesDonos = new HashMap<>();
    @Nullable
    private Assinatura assinaturaAtual;
    @Nullable
    private List<Cobranca> ultimasCobrancas;
    private int quantidadeMoradoresAtivos = 1;
    private boolean exclusaoEmAndamento = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityDetalheAssinaturaBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutRaizDetalheAssinatura,
                (view, windowInsets) -> {
                    Insets barras = windowInsets.getInsets(
                            WindowInsetsCompat.Type.systemBars());
                    view.setPadding(barras.left, barras.top, barras.right, barras.bottom);
                    return windowInsets;
                });

        binding.btnVoltarDetalhe.setOnClickListener(v -> finish());

        assinaturaViewModel = new ViewModelProvider(this).get(AssinaturaViewModel.class);

        int assinaturaId = getIntent().getIntExtra(EXTRA_ASSINATURA_ID, -1);
        if (assinaturaId <= 0) {
            Toast.makeText(this, R.string.erro_assinatura_nao_encontrada, Toast.LENGTH_LONG)
                    .show();
            finish();
            return;
        }

        configurarAdaptador();
        observarDados(assinaturaId);
    }

    private void configurarAdaptador() {
        cobrancaAdapter = new CobrancaAdapter(new CobrancaAdapter.OnCobrancaClickListener() {
            @Override
            public void onConfirmarPagamentoClick(@NonNull Cobranca cobranca) {
                abrirDialogoPagamento(cobranca);
            }

            @Override
            public void onCobrancaClick(@NonNull Cobranca cobranca) {
                if (!DomainConstants.COB_PAGA.equals(cobranca.getStatusPagamento())) {
                    abrirDialogoPagamento(cobranca);
                }
            }
        });
        binding.rvHistoricoCobrancas.setLayoutManager(new LinearLayoutManager(this));
        binding.rvHistoricoCobrancas.setAdapter(cobrancaAdapter);
    }

    private void observarDados(int assinaturaId) {
        assinaturaViewModel.listarTodasCategorias().observe(this, categorias -> {
            mapaNomesCategorias.clear();
            if (categorias != null) {
                for (Categoria categoria : categorias) {
                    if (categoria != null) {
                        mapaNomesCategorias.put(categoria.getId(), categoria.getNome());
                    }
                }
            }
            if (assinaturaAtual != null) {
                renderizarAssinatura(assinaturaAtual);
            }
        });

        assinaturaViewModel.listarMoradoresDaFamilia().observe(this, moradores -> {
            mapaNomesDonos.clear();
            if (moradores != null) {
                for (Usuario usuario : moradores) {
                    if (usuario != null) {
                        mapaNomesDonos.put(usuario.getId(), obterPrimeiroNome(usuario.getNome()));
                    }
                }
            }
            if (assinaturaAtual != null) {
                renderizarAssinatura(assinaturaAtual);
            }
        });

        assinaturaViewModel.listarMoradoresAtivos().observe(this, moradores -> {
            quantidadeMoradoresAtivos = Math.max(1, moradores != null ? moradores.size() : 1);
            if (assinaturaAtual != null) {
                renderizarAssinatura(assinaturaAtual);
            }
            atualizarHistoricoUI();
        });

        assinaturaViewModel.buscarAssinaturaPorId(assinaturaId).observe(this, assinatura -> {
            if (assinatura == null) {
                if (!exclusaoEmAndamento) {
                    Toast.makeText(this, R.string.erro_assinatura_nao_encontrada,
                            Toast.LENGTH_LONG).show();
                    finish();
                }
                return;
            }
            assinaturaAtual = assinatura;
            renderizarAssinatura(assinatura);
            atualizarHistoricoUI();
        });

        assinaturaViewModel.listarCobrancasPorAssinatura(assinaturaId).observe(this, cobrancas -> {
            ultimasCobrancas = cobrancas;
            atualizarHistoricoUI();
        });
    }

    private void renderizarAssinatura(@NonNull Assinatura assinatura) {
        String nome = assinatura.getNomeServico() != null
                ? assinatura.getNomeServico().trim()
                : "";
        String inicial = nome.isEmpty()
                ? "A"
                : nome.substring(0, 1).toUpperCase(LOCALE_PT_BR);

        binding.txtInicialDetalhe.setText(inicial);
        binding.txtNomeServicoDetalhe.setText(nome);
        binding.txtValorPrincipalDetalhe.setText(String.format(LOCALE_PT_BR,
                getString(R.string.formato_valor_reais), assinatura.getValorTotal()));

        boolean anual = DomainConstants.PER_ANUAL.equalsIgnoreCase(assinatura.getPeriodicidade());
        binding.txtSufixoPeriodicidadeDetalhe.setText(anual
                ? R.string.sufixo_barra_ano
                : R.string.sufixo_barra_mes);

        if (assinatura.isStatus()) {
            binding.txtSeloStatusDetalhe.setText(R.string.selo_ativa_ponto);
            binding.txtSeloStatusDetalhe.setTextColor(
                    ContextCompat.getColor(this, R.color.cor_verde_escuro));
            ViewCompat.setBackgroundTintList(binding.txtSeloStatusDetalhe,
                    ColorStateList.valueOf(
                            ContextCompat.getColor(this, R.color.cor_verde_claro)));
        } else {
            binding.txtSeloStatusDetalhe.setText(R.string.selo_inativa_ponto);
            binding.txtSeloStatusDetalhe.setTextColor(
                    ContextCompat.getColor(this, R.color.cor_texto_secundario));
            ViewCompat.setBackgroundTintList(binding.txtSeloStatusDetalhe,
                    ColorStateList.valueOf(
                            ContextCompat.getColor(this, R.color.cor_fundo_secundario)));
        }

        String nomeCategoria = mapaNomesCategorias.get(assinatura.getCategoriaId());
        if (nomeCategoria == null) {
            nomeCategoria = "";
        }
        SimpleDateFormat formatoData = new SimpleDateFormat("dd/MM/yyyy", LOCALE_PT_BR);
        String dataVencimento = formatoData.format(
                new Date(assinatura.getDataProximoVencimento()));
        binding.txtInfoCategoriaVencimento.setText(getString(
                R.string.formato_info_categoria_vencimento, nomeCategoria, dataVencimento));

        if (!TextUtils.isEmpty(assinatura.getMoeda())
                && !DomainConstants.MOEDA_BRL.equalsIgnoreCase(assinatura.getMoeda())) {
            binding.txtInfoMoedaOriginal.setVisibility(View.VISIBLE);
            binding.txtInfoMoedaOriginal.setText(String.format(LOCALE_PT_BR,
                    getString(R.string.formato_info_moeda_original),
                    assinatura.getMoeda(),
                    assinatura.getValorOriginal()));
        } else {
            binding.txtInfoMoedaOriginal.setVisibility(View.GONE);
        }

        boolean podeGerenciar = assinaturaViewModel.podeGerenciarAssinatura(assinatura);
        binding.btnOpcoesDetalhe.setVisibility(podeGerenciar ? View.VISIBLE : View.GONE);
        binding.layoutAcoesDetalhe.setVisibility(podeGerenciar ? View.VISIBLE : View.GONE);

        if (podeGerenciar) {
            binding.btnAlternarStatusAssinatura.setText(
                    assinatura.isStatus() ? R.string.acao_inativar : R.string.acao_ativar);
            binding.btnAlternarStatusAssinatura.setTextColor(
                    ContextCompat.getColor(this, assinatura.isStatus()
                            ? R.color.cor_status_pendente
                            : R.color.cor_verde_escuro));

            binding.btnEditarAssinatura.setOnClickListener(v -> abrirEdicao(assinatura));
            binding.btnAlternarStatusAssinatura.setOnClickListener(
                    v -> alterarStatusAssinatura());
            binding.btnExcluirAssinatura.setOnClickListener(
                    v -> confirmarExclusaoAssinatura());
            binding.btnOpcoesDetalhe.setOnClickListener(v -> abrirMenuOpcoes(assinatura));
        }

        renderizarDivisao(assinatura);
    }

    private void renderizarDivisao(@NonNull Assinatura assinatura) {
        if (DomainConstants.USO_GERAL.equalsIgnoreCase(assinatura.getTipoDoUso())) {
            binding.txtTipoDivisao.setText(R.string.divisao_uso_geral_casa);
            binding.txtSubtituloDivisao.setText(quantidadeMoradoresAtivos == 1
                    ? getString(R.string.formato_compartilhado_membro_singular)
                    : getString(R.string.formato_compartilhado_membros,
                    quantidadeMoradoresAtivos));
            binding.txtRotuloCustoMembro.setText(R.string.rotulo_custo_por_membro_curto);
            float custoPorMembro = assinatura.getValorTotal()
                    / Math.max(1, quantidadeMoradoresAtivos);
            binding.txtValorCustoMembro.setText(String.format(LOCALE_PT_BR,
                    getString(R.string.formato_valor_reais), custoPorMembro));
        } else {
            binding.txtTipoDivisao.setText(R.string.divisao_uso_individual);
            String nomeDono = mapaNomesDonos.get(assinatura.getDonoId());
            binding.txtSubtituloDivisao.setText(!TextUtils.isEmpty(nomeDono)
                    ? getString(R.string.formato_exclusivo_dono, nomeDono)
                    : getString(R.string.desc_exclusivo_individual));
            binding.txtRotuloCustoMembro.setText(R.string.rotulo_custo_titular);
            binding.txtValorCustoMembro.setText(String.format(LOCALE_PT_BR,
                    getString(R.string.formato_valor_reais), assinatura.getValorTotal()));
        }
    }

    private void abrirMenuOpcoes(@NonNull Assinatura assinatura) {
        PopupMenu menu = new PopupMenu(this, binding.btnOpcoesDetalhe);
        menu.getMenu().add(0, 1, 0, R.string.acao_editar);
        menu.getMenu().add(0, 2, 1,
                assinatura.isStatus() ? R.string.acao_inativar : R.string.acao_ativar);
        menu.getMenu().add(0, 3, 2, R.string.acao_excluir);
        menu.setOnMenuItemClickListener(item -> {
            int idItem = item.getItemId();
            if (idItem == 1) {
                abrirEdicao(assinatura);
                return true;
            }
            if (idItem == 2) {
                alterarStatusAssinatura();
                return true;
            }
            if (idItem == 3) {
                confirmarExclusaoAssinatura();
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void abrirEdicao(@NonNull Assinatura assinatura) {
        Intent intent = new Intent(this, NovaAssinaturaActivity.class);
        intent.putExtra(NovaAssinaturaActivity.EXTRA_ASSINATURA_ID, assinatura.getId());
        startActivity(intent);
    }

    private void atualizarHistoricoUI() {
        if (binding == null) {
            return;
        }

        int anoReferencia = Calendar.getInstance().get(Calendar.YEAR);
        if (ultimasCobrancas != null && !ultimasCobrancas.isEmpty()) {
            long dataMaisRecente = 0L;
            for (Cobranca cobranca : ultimasCobrancas) {
                if (cobranca != null && cobranca.getDataVencimento() > dataMaisRecente) {
                    dataMaisRecente = cobranca.getDataVencimento();
                }
            }
            if (dataMaisRecente > 0L) {
                Calendar calendario = Calendar.getInstance();
                calendario.setTimeInMillis(dataMaisRecente);
                anoReferencia = calendario.get(Calendar.YEAR);
            }
        }
        binding.txtAnoHistorico.setText(
                getString(R.string.formato_ano_historico, anoReferencia));

        if (ultimasCobrancas == null || ultimasCobrancas.isEmpty()) {
            binding.rvHistoricoCobrancas.setVisibility(View.GONE);
            binding.incluirEstadoVazioHistorico.getRoot().setVisibility(View.VISIBLE);
            binding.incluirEstadoVazioHistorico.txtTituloVazio.setText(
                    R.string.titulo_sem_historico_cobrancas);
            binding.incluirEstadoVazioHistorico.txtDescricaoVazio.setText(
                    R.string.desc_sem_historico_cobrancas);
            binding.incluirEstadoVazioHistorico.btnAcaoVazio.setVisibility(View.GONE);
        } else {
            binding.rvHistoricoCobrancas.setVisibility(View.VISIBLE);
            binding.incluirEstadoVazioHistorico.getRoot().setVisibility(View.GONE);
            cobrancaAdapter.atualizarHistorico(
                    ultimasCobrancas,
                    assinaturaAtual,
                    assinaturaViewModel.isGestor(),
                    quantidadeMoradoresAtivos);
        }
    }

    private void abrirDialogoPagamento(@NonNull Cobranca cobranca) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.titulo_confirmar_pagamento)
                .setMessage(R.string.msg_confirmar_pagamento)
                .setNegativeButton(R.string.acao_cancelar, null)
                .setPositiveButton(R.string.acao_confirmar,
                        (dialog, which) -> confirmarPagamento(cobranca))
                .show();
    }

    private void confirmarPagamento(@NonNull Cobranca cobranca) {
        binding.pbCarregandoDetalhe.setVisibility(View.VISIBLE);
        assinaturaViewModel.confirmarPagamentoCobranca(cobranca)
                .observe(this, recurso -> {
                    if (recurso == null) {
                        return;
                    }

                    if (recurso.getStatus() == Resource.Status.CARREGANDO) {
                        binding.pbCarregandoDetalhe.setVisibility(View.VISIBLE);
                    } else if (recurso.getStatus() == Resource.Status.SUCESSO) {
                        binding.pbCarregandoDetalhe.setVisibility(View.GONE);
                        String mensagem = recurso.isFallbackOffline()
                                && recurso.getMensagem() != null
                                ? recurso.getMensagem()
                                : getString(R.string.sucesso_pagamento_confirmado);
                        Snackbar.make(binding.layoutRaizDetalheAssinatura, mensagem,
                                Snackbar.LENGTH_LONG).show();
                    } else {
                        binding.pbCarregandoDetalhe.setVisibility(View.GONE);
                        String mensagem = recurso.getMensagem() != null
                                ? recurso.getMensagem()
                                : getString(R.string.erro_operacao_categoria);
                        Snackbar.make(binding.layoutRaizDetalheAssinatura, mensagem,
                                Snackbar.LENGTH_LONG).show();
                    }
                });
    }

    private void alterarStatusAssinatura() {
        if (assinaturaAtual == null) {
            return;
        }

        boolean novaAtiva = !assinaturaAtual.isStatus();
        binding.pbCarregandoDetalhe.setVisibility(View.VISIBLE);

        assinaturaViewModel.alterarStatusAssinatura(assinaturaAtual, novaAtiva)
                .observe(this, recurso -> {
                    if (recurso == null) {
                        return;
                    }

                    if (recurso.getStatus() == Resource.Status.CARREGANDO) {
                        binding.pbCarregandoDetalhe.setVisibility(View.VISIBLE);
                    } else if (recurso.getStatus() == Resource.Status.SUCESSO) {
                        binding.pbCarregandoDetalhe.setVisibility(View.GONE);
                        String mensagem = recurso.isFallbackOffline()
                                && recurso.getMensagem() != null
                                ? recurso.getMensagem()
                                : getString(novaAtiva
                                ? R.string.sucesso_assinatura_ativada
                                : R.string.sucesso_assinatura_inativada);
                        Snackbar.make(binding.layoutRaizDetalheAssinatura, mensagem,
                                Snackbar.LENGTH_LONG).show();
                    } else {
                        binding.pbCarregandoDetalhe.setVisibility(View.GONE);
                        String mensagem = recurso.getMensagem() != null
                                ? recurso.getMensagem()
                                : getString(R.string.erro_operacao_categoria);
                        Snackbar.make(binding.layoutRaizDetalheAssinatura, mensagem,
                                Snackbar.LENGTH_LONG).show();
                    }
                });
    }

    private void confirmarExclusaoAssinatura() {
        if (assinaturaAtual == null) {
            return;
        }

        String nome = assinaturaAtual.getNomeServico() != null
                ? assinaturaAtual.getNomeServico()
                : "";
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.titulo_excluir_assinatura)
                .setMessage(getString(R.string.formato_msg_excluir_assinatura, nome))
                .setNegativeButton(R.string.acao_cancelar, null)
                .setPositiveButton(R.string.acao_excluir,
                        (dialog, which) -> excluirAssinatura())
                .show();
    }

    private void excluirAssinatura() {
        if (assinaturaAtual == null) {
            return;
        }

        exclusaoEmAndamento = true;
        binding.pbCarregandoDetalhe.setVisibility(View.VISIBLE);

        assinaturaViewModel.excluirAssinatura(assinaturaAtual)
                .observe(this, recurso -> {
                    if (recurso == null) {
                        return;
                    }

                    if (recurso.getStatus() == Resource.Status.CARREGANDO) {
                        binding.pbCarregandoDetalhe.setVisibility(View.VISIBLE);
                    } else if (recurso.getStatus() == Resource.Status.SUCESSO) {
                        binding.pbCarregandoDetalhe.setVisibility(View.GONE);
                        if (Boolean.TRUE.equals(recurso.getDados())) {
                            Toast.makeText(this, R.string.sucesso_assinatura_excluida,
                                    Toast.LENGTH_LONG).show();
                            finish();
                        } else {
                            exclusaoEmAndamento = false;
                            String mensagem = recurso.getMensagem() != null
                                    ? recurso.getMensagem()
                                    : getString(R.string.erro_operacao_categoria);
                            Snackbar.make(binding.layoutRaizDetalheAssinatura, mensagem,
                                    Snackbar.LENGTH_LONG).show();
                        }
                    } else {
                        binding.pbCarregandoDetalhe.setVisibility(View.GONE);
                        exclusaoEmAndamento = false;
                        String mensagem = recurso.getMensagem() != null
                                ? recurso.getMensagem()
                                : getString(R.string.erro_operacao_categoria);
                        Snackbar.make(binding.layoutRaizDetalheAssinatura, mensagem,
                                Snackbar.LENGTH_LONG).show();
                    }
                });
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
