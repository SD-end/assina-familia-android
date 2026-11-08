package com.example.assinafamilia.view.adapter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.assinafamilia.R;
import com.example.assinafamilia.databinding.ItemCobrancaBinding;
import com.example.assinafamilia.model.Assinatura;
import com.example.assinafamilia.model.Cobranca;
import com.example.assinafamilia.util.DomainConstants;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class CobrancaAdapter
        extends ListAdapter<CobrancaAdapter.ItemCobranca, CobrancaAdapter.CobrancaViewHolder> {

    private static final Locale LOCALE_PT_BR = new Locale("pt", "BR");

    private static final DiffUtil.ItemCallback<ItemCobranca> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<ItemCobranca>() {
                @Override
                public boolean areItemsTheSame(@NonNull ItemCobranca itemAntigo,
                                               @NonNull ItemCobranca novoItem) {
                    return itemAntigo.cobranca.getId() == novoItem.cobranca.getId();
                }

                @Override
                public boolean areContentsTheSame(@NonNull ItemCobranca itemAntigo,
                                                  @NonNull ItemCobranca novoItem) {
                    return itemAntigo.cobranca.getValorCobrado() == novoItem.cobranca.getValorCobrado()
                            && itemAntigo.cobranca.getDataVencimento() == novoItem.cobranca.getDataVencimento()
                            && TextUtils.equals(itemAntigo.cobranca.getStatusPagamento(),
                            novoItem.cobranca.getStatusPagamento())
                            && TextUtils.equals(itemAntigo.nomeServico, novoItem.nomeServico)
                            && TextUtils.equals(itemAntigo.tipoDoUso, novoItem.tipoDoUso)
                            && itemAntigo.isGestor == novoItem.isGestor
                            && itemAntigo.moradoresAtivos == novoItem.moradoresAtivos
                            && itemAntigo.modoHistorico == novoItem.modoHistorico;
                }
            };

    @Nullable
    private final OnCobrancaClickListener listener;

    public CobrancaAdapter(@Nullable OnCobrancaClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    public interface OnCobrancaClickListener {
        void onConfirmarPagamentoClick(@NonNull Cobranca cobranca);

        void onCobrancaClick(@NonNull Cobranca cobranca);
    }

    public void atualizarDados(@Nullable List<Cobranca> cobrancas,
                               @Nullable Map<Integer, Assinatura> mapaAssinaturas,
                               boolean isGestor,
                               int quantidadeMoradoresAtivos,
                               boolean modoHistorico) {
        List<ItemCobranca> itens = new ArrayList<>();
        int moradores = Math.max(1, quantidadeMoradoresAtivos);
        if (cobrancas != null) {
            for (Cobranca cobranca : cobrancas) {
                if (cobranca == null) {
                    continue;
                }
                Assinatura assinatura = mapaAssinaturas != null
                        ? mapaAssinaturas.get(cobranca.getAssinaturaId())
                        : null;
                String nomeServico = assinatura != null ? assinatura.getNomeServico() : null;
                String tipoDoUso = assinatura != null ? assinatura.getTipoDoUso() : null;
                itens.add(new ItemCobranca(
                        cobranca,
                        nomeServico,
                        tipoDoUso,
                        isGestor,
                        moradores,
                        modoHistorico));
            }
        }
        submitList(itens);
    }

    public void atualizarHistorico(@Nullable List<Cobranca> cobrancas,
                                   @Nullable Assinatura assinatura,
                                   boolean isGestor,
                                   int quantidadeMoradoresAtivos) {
        Map<Integer, Assinatura> mapa = new HashMap<>();
        if (assinatura != null) {
            mapa.put(assinatura.getId(), assinatura);
        }
        atualizarDados(cobrancas, mapa, isGestor, quantidadeMoradoresAtivos, true);
    }

    @NonNull
    @Override
    public CobrancaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCobrancaBinding binding = ItemCobrancaBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new CobrancaViewHolder(binding, listener);
    }

    @Override
    public void onBindViewHolder(@NonNull CobrancaViewHolder holder, int position) {
        holder.vincular(getItem(position));
    }

    private static long calcularDiferencaDias(long dataVencimento) {
        Calendar hoje = Calendar.getInstance();
        hoje.set(Calendar.HOUR_OF_DAY, 0);
        hoje.set(Calendar.MINUTE, 0);
        hoje.set(Calendar.SECOND, 0);
        hoje.set(Calendar.MILLISECOND, 0);

        Calendar vencimento = Calendar.getInstance();
        vencimento.setTimeInMillis(dataVencimento);
        vencimento.set(Calendar.HOUR_OF_DAY, 0);
        vencimento.set(Calendar.MINUTE, 0);
        vencimento.set(Calendar.SECOND, 0);
        vencimento.set(Calendar.MILLISECOND, 0);

        return TimeUnit.MILLISECONDS.toDays(
                vencimento.getTimeInMillis() - hoje.getTimeInMillis());
    }

    public static final class ItemCobranca {

        private final Cobranca cobranca;

        @Nullable
        private final String nomeServico;

        @Nullable
        private final String tipoDoUso;

        private final boolean isGestor;

        private final int moradoresAtivos;

        private final boolean modoHistorico;

        private ItemCobranca(@NonNull Cobranca cobranca,
                             @Nullable String nomeServico,
                             @Nullable String tipoDoUso,
                             boolean isGestor,
                             int moradoresAtivos,
                             boolean modoHistorico) {
            this.cobranca = cobranca;
            this.nomeServico = nomeServico;
            this.tipoDoUso = tipoDoUso;
            this.isGestor = isGestor;
            this.moradoresAtivos = moradoresAtivos;
            this.modoHistorico = modoHistorico;
        }

        public Cobranca getCobranca() {
            return cobranca;
        }
    }

    static class CobrancaViewHolder extends RecyclerView.ViewHolder {

        private final ItemCobrancaBinding binding;

        @Nullable
        private final OnCobrancaClickListener listener;

        CobrancaViewHolder(@NonNull ItemCobrancaBinding binding,
                           @Nullable OnCobrancaClickListener listener) {
            super(binding.getRoot());
            this.binding = binding;
            this.listener = listener;
        }

        void vincular(@NonNull ItemCobranca item) {
            Context contexto = binding.getRoot().getContext();
            Cobranca cobranca = item.cobranca;

            if (item.modoHistorico) {
                SimpleDateFormat formatoMesAno = new SimpleDateFormat("MMMM yyyy", LOCALE_PT_BR);
                String mesAno = formatoMesAno.format(new Date(cobranca.getDataVencimento()));
                String titulo = mesAno.isEmpty()
                        ? mesAno
                        : mesAno.substring(0, 1).toUpperCase(LOCALE_PT_BR) + mesAno.substring(1);
                binding.txtTituloCobranca.setText(titulo);

                SimpleDateFormat formatoDiaMes = new SimpleDateFormat("dd/MM", LOCALE_PT_BR);
                binding.txtSubtituloCobranca.setText(contexto.getString(
                        R.string.formato_cobrado_em,
                        formatoDiaMes.format(new Date(cobranca.getDataVencimento()))));
            } else {
                String nomeExibicao = !TextUtils.isEmpty(item.nomeServico)
                        ? item.nomeServico
                        : contexto.getString(R.string.titulo_assinatura_generica);
                binding.txtTituloCobranca.setText(nomeExibicao);

                long diffDias = calcularDiferencaDias(cobranca.getDataVencimento());
                String subtitulo;
                if (diffDias == 0) {
                    subtitulo = contexto.getString(R.string.vence_hoje);
                } else if (diffDias == 1) {
                    subtitulo = contexto.getString(R.string.vence_amanha);
                } else if (diffDias > 1 && diffDias <= 30) {
                    subtitulo = contexto.getString(R.string.formato_vence_em_dias, (int) diffDias);
                } else if (diffDias > 30) {
                    SimpleDateFormat formatoDataCompleta =
                            new SimpleDateFormat("dd/MM/yyyy", LOCALE_PT_BR);
                    subtitulo = contexto.getString(R.string.formato_vence_em_data,
                            formatoDataCompleta.format(new Date(cobranca.getDataVencimento())));
                } else if (diffDias == -1) {
                    subtitulo = contexto.getString(R.string.venceu_ontem);
                } else {
                    subtitulo = contexto.getString(R.string.formato_venceu_ha_dias,
                            (int) Math.abs(diffDias));
                }
                binding.txtSubtituloCobranca.setText(subtitulo);
            }

            if (!item.isGestor && DomainConstants.USO_GERAL.equalsIgnoreCase(item.tipoDoUso)) {
                float minhaParte = cobranca.getValorCobrado()
                        / Math.max(1, item.moradoresAtivos);
                binding.txtValorCobranca.setText(String.format(LOCALE_PT_BR,
                        contexto.getString(R.string.formato_sua_parte_valor), minhaParte));
                binding.txtValorCobranca.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            } else {
                binding.txtValorCobranca.setText(String.format(LOCALE_PT_BR,
                        contexto.getString(R.string.formato_valor_reais),
                        cobranca.getValorCobrado()));
                binding.txtValorCobranca.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
            }

            String status = cobranca.getStatusPagamento();
            int textoStatus;
            int corTexto;
            int corFundo;
            if (DomainConstants.COB_PAGA.equals(status)) {
                textoStatus = R.string.status_pago;
                corTexto = R.color.cor_status_pago;
                corFundo = R.color.cor_status_pago_fundo;
                binding.btnConfirmarPagamento.setVisibility(View.GONE);
            } else if (DomainConstants.COB_ATRASADA.equals(status)) {
                textoStatus = R.string.status_atrasada;
                corTexto = R.color.cor_status_atrasado;
                corFundo = R.color.cor_status_atrasado_fundo;
                binding.btnConfirmarPagamento.setVisibility(View.VISIBLE);
            } else {
                textoStatus = R.string.status_pendente;
                corTexto = R.color.cor_status_pendente;
                corFundo = R.color.cor_status_pendente_fundo;
                binding.btnConfirmarPagamento.setVisibility(View.VISIBLE);
            }
            binding.txtStatusCobranca.setText(textoStatus);
            binding.txtStatusCobranca.setTextColor(ContextCompat.getColor(contexto, corTexto));
            ViewCompat.setBackgroundTintList(binding.txtStatusCobranca,
                    ColorStateList.valueOf(ContextCompat.getColor(contexto, corFundo)));

            binding.btnConfirmarPagamento.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onConfirmarPagamentoClick(cobranca);
                }
            });
            binding.cardCobranca.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCobrancaClick(cobranca);
                }
            });
        }
    }
}
