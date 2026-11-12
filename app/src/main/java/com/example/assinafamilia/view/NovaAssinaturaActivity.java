package com.example.assinafamilia.view;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.assinafamilia.R;
import com.example.assinafamilia.databinding.ActivityNovaAssinaturaBinding;
import com.example.assinafamilia.model.Assinatura;
import com.example.assinafamilia.model.Categoria;
import com.example.assinafamilia.util.DomainConstants;
import com.example.assinafamilia.util.Resource;
import com.example.assinafamilia.viewmodel.AssinaturaViewModel;
import com.example.assinafamilia.worker.WorkScheduler;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.snackbar.Snackbar;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class NovaAssinaturaActivity extends AppCompatActivity {

    public static final String EXTRA_ASSINATURA_ID = "EXTRA_ASSINATURA_ID";

    private static final Locale LOCALE_PT_BR = new Locale("pt", "BR");
    private static final String FUSO_UTC = "UTC";

    private ActivityNovaAssinaturaBinding binding;
    private AssinaturaViewModel assinaturaViewModel;

    private final List<Categoria> listaCategorias = new ArrayList<>();

    private String moedaSelecionada = DomainConstants.MOEDA_BRL;
    private float taxaCambioAplicada = 1.0f;
    private boolean carregandoCambio = false;
    private int categoriaSelecionadaId = -1;
    private String periodicidadeSelecionada = DomainConstants.PER_MENSAL;
    private String tipoUsoSelecionado = DomainConstants.USO_GERAL;
    private long dataVencimentoSelecionadaMillis;

    @Nullable
    private Assinatura assinaturaExistente;

    private boolean modoEdicao = false;
    private boolean formularioPreenchido = false;
    private boolean apoioCategoriaSolicitado = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityNovaAssinaturaBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutRaizNovaAssinatura,
                (view, windowInsets) -> {
                    Insets barras = windowInsets.getInsets(
                            WindowInsetsCompat.Type.systemBars());
                    view.setPadding(barras.left, barras.top, barras.right, barras.bottom);
                    return windowInsets;
                });

        binding.barraSuperiorNovaAssinatura.setNavigationOnClickListener(v -> finish());

        assinaturaViewModel = new ViewModelProvider(this).get(AssinaturaViewModel.class);

        configurarTipoUso();
        configurarMoeda();
        configurarPeriodicidade();
        configurarDataVencimento();
        configurarCategorias();
        configurarValorOriginal();
        configurarBotaoSalvar();
        carregarModoEdicao();
    }

    private void configurarTipoUso() {
        if (assinaturaViewModel.isGestor()) {
            binding.cardUsoCasa.setVisibility(View.VISIBLE);
            binding.cardUsoIndividual.setVisibility(View.VISIBLE);
            tipoUsoSelecionado = DomainConstants.USO_GERAL;
            binding.cardUsoCasa.setOnClickListener(v -> {
                tipoUsoSelecionado = DomainConstants.USO_GERAL;
                atualizarEstiloCardsUso();
            });
            binding.cardUsoIndividual.setOnClickListener(v -> {
                tipoUsoSelecionado = DomainConstants.USO_INDIVIDUAL;
                atualizarEstiloCardsUso();
            });
        } else {
            binding.cardUsoCasa.setVisibility(View.GONE);
            binding.cardUsoIndividual.setVisibility(View.VISIBLE);
            tipoUsoSelecionado = DomainConstants.USO_INDIVIDUAL;
        }
        atualizarEstiloCardsUso();
    }

    private void atualizarEstiloCardsUso() {
        boolean selecionouCasa = DomainConstants.USO_GERAL.equals(tipoUsoSelecionado);
        aplicarEstiloCardUso(binding.cardUsoCasa, binding.txtTituloUsoCasa, selecionouCasa);
        aplicarEstiloCardUso(binding.cardUsoIndividual, binding.txtTituloUsoIndividual,
                !selecionouCasa);
    }

    private void aplicarEstiloCardUso(@NonNull MaterialCardView card,
                                      @NonNull TextView titulo,
                                      boolean selecionado) {
        if (selecionado) {
            card.setStrokeColor(ContextCompat.getColor(this, R.color.cor_verde_primario));
            card.setStrokeWidth(converterDpParaPx(2));
            card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.cor_verde_claro));
            titulo.setTextColor(ContextCompat.getColor(this, R.color.cor_verde_escuro));
        } else {
            card.setStrokeColor(ContextCompat.getColor(this, R.color.cor_borda_sutil));
            card.setStrokeWidth(converterDpParaPx(1));
            card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.cor_fundo_cartao));
            titulo.setTextColor(ContextCompat.getColor(this, R.color.cor_texto_primario));
        }
    }

    private void configurarMoeda() {
        final List<String> moedas = Arrays.asList(
                DomainConstants.MOEDA_BRL,
                DomainConstants.MOEDA_USD,
                DomainConstants.MOEDA_EUR);
        List<String> rotulos = Arrays.asList(
                getString(R.string.moeda_brl),
                getString(R.string.moeda_usd),
                getString(R.string.moeda_eur));

        ArrayAdapter<String> adaptador = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, rotulos);
        binding.dropdownMoeda.setAdapter(adaptador);
        binding.dropdownMoeda.setText(rotulos.get(0), false);
        binding.dropdownMoeda.setOnItemClickListener(
                (parent, view, position, id) -> selecionarMoeda(moedas.get(position)));
    }

    private void selecionarMoeda(@Nullable String moeda) {
        String normalizada = moeda != null
                ? moeda.trim().toUpperCase(Locale.ROOT)
                : DomainConstants.MOEDA_BRL;

        if (DomainConstants.MOEDA_USD.equals(normalizada)) {
            moedaSelecionada = DomainConstants.MOEDA_USD;
            binding.dropdownMoeda.setText(getString(R.string.moeda_usd), false);
            binding.tilValorOriginal.setPrefixText(getString(R.string.prefixo_usd));
        } else if (DomainConstants.MOEDA_EUR.equals(normalizada)) {
            moedaSelecionada = DomainConstants.MOEDA_EUR;
            binding.dropdownMoeda.setText(getString(R.string.moeda_eur), false);
            binding.tilValorOriginal.setPrefixText(getString(R.string.prefixo_eur));
        } else {
            moedaSelecionada = DomainConstants.MOEDA_BRL;
            binding.dropdownMoeda.setText(getString(R.string.moeda_brl), false);
            binding.tilValorOriginal.setPrefixText(getString(R.string.prefixo_brl));
        }

        consultarTaxaCambio(moedaSelecionada);
    }

    private void consultarTaxaCambio(@NonNull String moeda) {
        if (DomainConstants.MOEDA_BRL.equals(moeda)) {
            taxaCambioAplicada = 1.0f;
            carregandoCambio = false;
            binding.txtValorConvertidoBrl.setVisibility(View.GONE);
            binding.cardAvisoFallbackCambio.setVisibility(View.GONE);
            binding.btnSalvarAssinatura.setEnabled(true);
            return;
        }

        assinaturaViewModel.obterTaxaCambio(moeda).observe(this, recurso -> {
            if (recurso == null || !moeda.equals(moedaSelecionada)) {
                return;
            }

            if (recurso.getStatus() == Resource.Status.CARREGANDO) {
                carregandoCambio = true;
                binding.btnSalvarAssinatura.setEnabled(false);
                binding.txtValorConvertidoBrl.setVisibility(View.VISIBLE);
                binding.txtValorConvertidoBrl.setText(R.string.aviso_consultando_cambio);
                binding.cardAvisoFallbackCambio.setVisibility(View.GONE);
            } else if (recurso.getStatus() == Resource.Status.SUCESSO) {
                carregandoCambio = false;
                binding.btnSalvarAssinatura.setEnabled(true);
                taxaCambioAplicada = recurso.getDados() != null ? recurso.getDados() : 1.0f;
                if (recurso.isFallbackOffline()) {
                    binding.cardAvisoFallbackCambio.setVisibility(View.VISIBLE);
                    binding.txtAvisoFallbackCambio.setText(recurso.getMensagem() != null
                            ? recurso.getMensagem()
                            : getString(R.string.aviso_fallback_cambio));
                } else {
                    binding.cardAvisoFallbackCambio.setVisibility(View.GONE);
                }
                atualizarPreviaConversaoBrl();
            } else {
                carregandoCambio = false;
                binding.btnSalvarAssinatura.setEnabled(true);
                String mensagem = recurso.getMensagem() != null
                        ? recurso.getMensagem()
                        : getString(R.string.aviso_fallback_cambio);
                Snackbar.make(binding.layoutRaizNovaAssinatura, mensagem,
                        Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void configurarValorOriginal() {
        binding.edtValorOriginal.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                atualizarPreviaConversaoBrl();
            }
        });
    }

    private void atualizarPreviaConversaoBrl() {
        if (DomainConstants.MOEDA_BRL.equals(moedaSelecionada) || carregandoCambio) {
            return;
        }

        String texto = binding.edtValorOriginal.getText() != null
                ? binding.edtValorOriginal.getText().toString()
                : "";
        float valor = AssinaturaViewModel.converterTextoParaValor(texto);

        binding.txtValorConvertidoBrl.setVisibility(View.VISIBLE);
        if (valor > 0f) {
            binding.txtValorConvertidoBrl.setText(String.format(LOCALE_PT_BR,
                    getString(R.string.formato_valor_convertido_brl),
                    valor * taxaCambioAplicada,
                    taxaCambioAplicada));
        } else {
            binding.txtValorConvertidoBrl.setText(String.format(LOCALE_PT_BR,
                    getString(R.string.formato_taxa_cambio_atual),
                    moedaSelecionada,
                    taxaCambioAplicada));
        }
    }

    private void configurarCategorias() {
        binding.dropdownCategoria.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < listaCategorias.size()) {
                Categoria categoria = listaCategorias.get(position);
                if (categoria != null) {
                    categoriaSelecionadaId = categoria.getId();
                    binding.tilCategoria.setError(null);
                }
            }
        });

        assinaturaViewModel.listarCategoriasAtivas().observe(this, categorias -> {
            listaCategorias.clear();
            if (categorias != null) {
                for (Categoria categoria : categorias) {
                    if (categoria != null) {
                        listaCategorias.add(categoria);
                    }
                }
            }

            List<String> nomes = new ArrayList<>();
            for (Categoria categoria : listaCategorias) {
                nomes.add(categoria.getNome() != null ? categoria.getNome() : "");
            }
            ArrayAdapter<String> adaptador = new ArrayAdapter<>(this,
                    android.R.layout.simple_dropdown_item_1line, nomes);
            binding.dropdownCategoria.setAdapter(adaptador);

            if (listaCategorias.isEmpty()) {
                Snackbar.make(binding.layoutRaizNovaAssinatura,
                        R.string.aviso_sem_categorias_ativas, Snackbar.LENGTH_LONG).show();
                return;
            }

            if (categoriaSelecionadaId > 0) {
                selecionarCategoriaPorId(categoriaSelecionadaId);
            }
        });
    }

    private void selecionarCategoriaPorId(int categoriaId) {
        for (Categoria categoria : listaCategorias) {
            if (categoria != null && categoria.getId() == categoriaId) {
                binding.dropdownCategoria.setText(
                        categoria.getNome() != null ? categoria.getNome() : "", false);
                return;
            }
        }

        if (categoriaId > 0 && !apoioCategoriaSolicitado) {
            apoioCategoriaSolicitado = true;
            assinaturaViewModel.listarTodasCategorias().observe(this, categorias -> {
                if (categorias == null) {
                    return;
                }
                for (Categoria categoria : categorias) {
                    if (categoria != null && categoria.getId() == categoriaId) {
                        binding.dropdownCategoria.setText(
                                categoria.getNome() != null ? categoria.getNome() : "", false);
                        return;
                    }
                }
            });
        }
    }

    private void configurarPeriodicidade() {
        List<String> rotulos = Arrays.asList(
                getString(R.string.periodicidade_mensal),
                getString(R.string.periodicidade_anual));

        ArrayAdapter<String> adaptador = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, rotulos);
        binding.dropdownPeriodicidade.setAdapter(adaptador);
        binding.dropdownPeriodicidade.setText(rotulos.get(0), false);
        binding.dropdownPeriodicidade.setOnItemClickListener((parent, view, position, id) -> {
            periodicidadeSelecionada = position == 1
                    ? DomainConstants.PER_ANUAL
                    : DomainConstants.PER_MENSAL;
        });
    }

    private void selecionarPeriodicidade(@Nullable String periodicidade) {
        if (DomainConstants.PER_ANUAL.equalsIgnoreCase(periodicidade)) {
            periodicidadeSelecionada = DomainConstants.PER_ANUAL;
            binding.dropdownPeriodicidade.setText(
                    getString(R.string.periodicidade_anual), false);
        } else {
            periodicidadeSelecionada = DomainConstants.PER_MENSAL;
            binding.dropdownPeriodicidade.setText(
                    getString(R.string.periodicidade_mensal), false);
        }
    }

    private void configurarDataVencimento() {
        Calendar hoje = Calendar.getInstance();
        hoje.set(Calendar.HOUR_OF_DAY, 12);
        hoje.set(Calendar.MINUTE, 0);
        hoje.set(Calendar.SECOND, 0);
        hoje.set(Calendar.MILLISECOND, 0);
        dataVencimentoSelecionadaMillis = hoje.getTimeInMillis();
        atualizarTextoData();

        View.OnClickListener abrirSeletor = v -> abrirSeletorData();
        binding.edtDataVencimento.setOnClickListener(abrirSeletor);
        binding.tilDataVencimento.setEndIconOnClickListener(abrirSeletor);
    }

    private void atualizarTextoData() {
        SimpleDateFormat formato = new SimpleDateFormat("dd/MM/yyyy", LOCALE_PT_BR);
        binding.edtDataVencimento.setText(
                formato.format(new Date(dataVencimentoSelecionadaMillis)));
    }

    private void abrirSeletorData() {
        Calendar referencia = Calendar.getInstance();
        referencia.setTimeInMillis(dataVencimentoSelecionadaMillis);

        Calendar selecaoUtc = Calendar.getInstance(TimeZone.getTimeZone(FUSO_UTC));
        selecaoUtc.clear();
        selecaoUtc.set(referencia.get(Calendar.YEAR), referencia.get(Calendar.MONTH),
                referencia.get(Calendar.DAY_OF_MONTH), 0, 0, 0);

        MaterialDatePicker<Long> seletor = MaterialDatePicker.Builder.datePicker()
                .setSelection(selecaoUtc.getTimeInMillis())
                .build();

        seletor.addOnPositiveButtonClickListener(selecao -> {
            Calendar leituraUtc = Calendar.getInstance(TimeZone.getTimeZone(FUSO_UTC));
            leituraUtc.setTimeInMillis(selecao);

            Calendar local = Calendar.getInstance();
            local.set(leituraUtc.get(Calendar.YEAR), leituraUtc.get(Calendar.MONTH),
                    leituraUtc.get(Calendar.DAY_OF_MONTH), 12, 0, 0);
            local.set(Calendar.MILLISECOND, 0);

            dataVencimentoSelecionadaMillis = local.getTimeInMillis();
            atualizarTextoData();
            binding.tilDataVencimento.setError(null);
        });

        seletor.show(getSupportFragmentManager(), "seletor_data_vencimento");
    }

    private void configurarBotaoSalvar() {
        binding.btnSalvarAssinatura.setOnClickListener(v -> salvarAssinatura());
    }

    private void carregarModoEdicao() {
        int assinaturaId = getIntent().getIntExtra(EXTRA_ASSINATURA_ID, -1);
        if (assinaturaId <= 0) {
            return;
        }

        modoEdicao = true;
        binding.barraSuperiorNovaAssinatura.setTitle(R.string.titulo_editar_assinatura);
        binding.txtRotuloDataVencimento.setText(R.string.dica_data_vencimento);
        binding.swAssinaturaAtiva.setVisibility(View.VISIBLE);

        assinaturaViewModel.buscarAssinaturaPorId(assinaturaId).observe(this, assinatura -> {
            if (assinatura == null || formularioPreenchido) {
                return;
            }

            if (!assinaturaViewModel.podeGerenciarAssinatura(assinatura)) {
                Toast.makeText(this, R.string.erro_sem_permissao_assinatura,
                        Toast.LENGTH_LONG).show();
                finish();
                return;
            }

            formularioPreenchido = true;
            assinaturaExistente = assinatura;

            binding.edtNomeServico.setText(assinatura.getNomeServico() != null
                    ? assinatura.getNomeServico()
                    : "");
            selecionarMoeda(assinatura.getMoeda());
            binding.edtValorOriginal.setText(
                    String.format(LOCALE_PT_BR, "%.2f", assinatura.getValorOriginal()));

            categoriaSelecionadaId = assinatura.getCategoriaId();
            selecionarCategoriaPorId(categoriaSelecionadaId);

            selecionarPeriodicidade(assinatura.getPeriodicidade());

            tipoUsoSelecionado = DomainConstants.USO_INDIVIDUAL.equalsIgnoreCase(
                    assinatura.getTipoDoUso())
                    ? DomainConstants.USO_INDIVIDUAL
                    : DomainConstants.USO_GERAL;
            atualizarEstiloCardsUso();

            Calendar calendario = Calendar.getInstance();
            calendario.setTimeInMillis(assinatura.getDataProximoVencimento());
            calendario.set(Calendar.HOUR_OF_DAY, 12);
            calendario.set(Calendar.MINUTE, 0);
            calendario.set(Calendar.SECOND, 0);
            calendario.set(Calendar.MILLISECOND, 0);
            dataVencimentoSelecionadaMillis = calendario.getTimeInMillis();
            atualizarTextoData();

            binding.swAssinaturaAtiva.setChecked(assinatura.isStatus());
        });
    }

    private void salvarAssinatura() {
        binding.tilNomeServico.setError(null);
        binding.tilValorOriginal.setError(null);
        binding.tilCategoria.setError(null);
        binding.tilDataVencimento.setError(null);

        if (carregandoCambio) {
            return;
        }

        String nome = binding.edtNomeServico.getText() != null
                ? binding.edtNomeServico.getText().toString().trim()
                : "";
        if (nome.isEmpty() || nome.length() > 120) {
            binding.tilNomeServico.setError(getString(R.string.erro_nome_servico_obrigatorio));
            return;
        }

        String valorTexto = binding.edtValorOriginal.getText() != null
                ? binding.edtValorOriginal.getText().toString().trim()
                : "";
        if (AssinaturaViewModel.converterTextoParaValor(valorTexto) <= 0f) {
            binding.tilValorOriginal.setError(getString(R.string.erro_valor_invalido));
            return;
        }

        if (categoriaSelecionadaId <= 0) {
            binding.tilCategoria.setError(getString(R.string.erro_categoria_obrigatoria));
            return;
        }

        if (dataVencimentoSelecionadaMillis <= 0L) {
            binding.tilDataVencimento.setError(
                    getString(R.string.erro_data_vencimento_obrigatoria));
            return;
        }

        boolean statusAtiva = !modoEdicao || binding.swAssinaturaAtiva.isChecked();

        assinaturaViewModel.salvarAssinatura(
                        assinaturaExistente,
                        nome,
                        categoriaSelecionadaId,
                        valorTexto,
                        moedaSelecionada,
                        taxaCambioAplicada,
                        periodicidadeSelecionada,
                        tipoUsoSelecionado,
                        dataVencimentoSelecionadaMillis,
                        statusAtiva)
                .observe(this, recurso -> {
                    if (recurso == null) {
                        return;
                    }

                    if (recurso.getStatus() == Resource.Status.CARREGANDO) {
                        binding.btnSalvarAssinatura.setEnabled(false);
                        binding.pbSalvandoAssinatura.setVisibility(View.VISIBLE);
                    } else if (recurso.getStatus() == Resource.Status.SUCESSO) {
                        binding.btnSalvarAssinatura.setEnabled(true);
                        binding.pbSalvandoAssinatura.setVisibility(View.GONE);
                        WorkScheduler.executarVerificacaoVencimentosImediata(
                                NovaAssinaturaActivity.this);
                        String mensagem = recurso.isFallbackOffline()
                                && recurso.getMensagem() != null
                                ? recurso.getMensagem()
                                : getString(R.string.sucesso_assinatura_salva);
                        Toast.makeText(this, mensagem, Toast.LENGTH_LONG).show();
                        finish();
                    } else {
                        binding.btnSalvarAssinatura.setEnabled(true);
                        binding.pbSalvandoAssinatura.setVisibility(View.GONE);
                        String mensagem = recurso.getMensagem() != null
                                ? recurso.getMensagem()
                                : getString(R.string.erro_operacao_categoria);
                        Snackbar.make(binding.layoutRaizNovaAssinatura, mensagem,
                                Snackbar.LENGTH_LONG).show();
                    }
                });
    }

    private int converterDpParaPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
