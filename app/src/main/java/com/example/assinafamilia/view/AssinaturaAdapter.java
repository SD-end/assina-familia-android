package com.example.assinafamilia.view;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.TextUtils;
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
import com.example.assinafamilia.databinding.ItemAssinaturaBinding;
import com.example.assinafamilia.model.Assinatura;
import com.example.assinafamilia.util.DomainConstants;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AssinaturaAdapter
        extends ListAdapter<AssinaturaAdapter.ItemAssinatura, AssinaturaAdapter.AssinaturaViewHolder> {

    private static final Locale LOCALE_PT_BR = new Locale("pt", "BR");

    private static final DiffUtil.ItemCallback<ItemAssinatura> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<ItemAssinatura>() {
                @Override
                public boolean areItemsTheSame(@NonNull ItemAssinatura itemAntigo,
                                               @NonNull ItemAssinatura novoItem) {
                    return itemAntigo.id == novoItem.id;
                }

                @Override
                public boolean areContentsTheSame(@NonNull ItemAssinatura itemAntigo,
                                                  @NonNull ItemAssinatura novoItem) {
                    return TextUtils.equals(itemAntigo.nomeServico, novoItem.nomeServico)
                            && TextUtils.equals(itemAntigo.tipoDoUso, novoItem.tipoDoUso)
                            && Float.compare(itemAntigo.valorTotal, novoItem.valorTotal) == 0
                            && Float.compare(itemAntigo.valorOriginal,
                            novoItem.valorOriginal) == 0
                            && TextUtils.equals(itemAntigo.moeda, novoItem.moeda)
                            && TextUtils.equals(itemAntigo.periodicidade,
                            novoItem.periodicidade)
                            && itemAntigo.dataProximoVencimento
                            == novoItem.dataProximoVencimento
                            && itemAntigo.status == novoItem.status
                            && TextUtils.equals(itemAntigo.nomeDono, novoItem.nomeDono)
                            && TextUtils.equals(itemAntigo.nomeCategoria, novoItem.nomeCategoria)
                            && itemAntigo.isGestor == novoItem.isGestor
                            && itemAntigo.moradoresAtivos == novoItem.moradoresAtivos;
                }
            };

    @Nullable
    private final OnAssinaturaClickListener clickListener;

    public interface OnAssinaturaClickListener {
        void onAssinaturaClick(@NonNull Assinatura assinatura);
    }

    public AssinaturaAdapter(@Nullable OnAssinaturaClickListener clickListener) {
        super(DIFF_CALLBACK);
        this.clickListener = clickListener;
    }

    public void atualizarDados(@Nullable List<Assinatura> assinaturas,
                               @Nullable Map<Integer, String> mapaNomesDonos,
                               @Nullable Map<Integer, String> mapaNomesCategorias,
                               boolean isGestor,
                               int moradoresAtivos) {
        List<ItemAssinatura> itens = new ArrayList<>();
        if (assinaturas != null) {
            for (Assinatura assinatura : assinaturas) {
                if (assinatura == null) {
                    continue;
                }
                String nomeDono = mapaNomesDonos != null
                        ? mapaNomesDonos.get(assinatura.getDonoId())
                        : null;
                String nomeCategoria = mapaNomesCategorias != null
                        ? mapaNomesCategorias.get(assinatura.getCategoriaId())
                        : null;
                itens.add(new ItemAssinatura(
                        assinatura,
                        nomeDono,
                        nomeCategoria,
                        isGestor,
                        moradoresAtivos));
            }
        }
        submitList(itens);
    }

    @NonNull
    @Override
    public AssinaturaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemAssinaturaBinding binding = ItemAssinaturaBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new AssinaturaViewHolder(binding, clickListener);
    }

    @Override
    public void onBindViewHolder(@NonNull AssinaturaViewHolder holder, int position) {
        holder.vincular(getItem(position));
    }

    public static final class ItemAssinatura {

        private final Assinatura assinatura;

        private final int id;

        @Nullable
        private final String nomeServico;

        @Nullable
        private final String tipoDoUso;

        private final float valorTotal;

        private final float valorOriginal;

        @Nullable
        private final String moeda;

        @Nullable
        private final String periodicidade;

        private final long dataProximoVencimento;

        private final boolean status;

        @Nullable
        private final String nomeDono;

        @Nullable
        private final String nomeCategoria;

        private final boolean isGestor;

        private final int moradoresAtivos;

        private ItemAssinatura(@NonNull Assinatura assinatura,
                               @Nullable String nomeDono,
                               @Nullable String nomeCategoria,
                               boolean isGestor,
                               int moradoresAtivos) {
            this.assinatura = assinatura;
            this.id = assinatura.getId();
            this.nomeServico = assinatura.getNomeServico();
            this.tipoDoUso = assinatura.getTipoDoUso();
            this.valorTotal = assinatura.getValorTotal();
            this.valorOriginal = assinatura.getValorOriginal();
            this.moeda = assinatura.getMoeda();
            this.periodicidade = assinatura.getPeriodicidade();
            this.dataProximoVencimento = assinatura.getDataProximoVencimento();
            this.status = assinatura.isStatus();
            this.nomeDono = nomeDono;
            this.nomeCategoria = nomeCategoria;
            this.isGestor = isGestor;
            this.moradoresAtivos = moradoresAtivos;
        }

        public Assinatura getAssinatura() {
            return assinatura;
        }
    }

    static class AssinaturaViewHolder extends RecyclerView.ViewHolder {

        private final ItemAssinaturaBinding binding;

        @Nullable
        private final OnAssinaturaClickListener clickListener;

        AssinaturaViewHolder(@NonNull ItemAssinaturaBinding binding,
                             @Nullable OnAssinaturaClickListener clickListener) {
            super(binding.getRoot());
            this.binding = binding;
            this.clickListener = clickListener;
        }

        void vincular(@NonNull ItemAssinatura item) {
            Context contexto = binding.getRoot().getContext();
            Assinatura assinatura = item.assinatura;

            String nome = assinatura.getNomeServico() != null
                    ? assinatura.getNomeServico().trim()
                    : "";
            String inicial = nome.isEmpty()
                    ? "A"
                    : nome.substring(0, 1).toUpperCase(LOCALE_PT_BR);
            binding.txtNomeServico.setText(nome);
            binding.txtInicialAssinatura.setText(inicial);

            boolean usoGeral = DomainConstants.USO_GERAL.equalsIgnoreCase(
                    assinatura.getTipoDoUso());

            float valorExibido;
            if (!item.isGestor) {
                binding.txtRotuloSuaParte.setVisibility(View.VISIBLE);
                valorExibido = usoGeral
                        ? assinatura.getValorTotal() / Math.max(1, item.moradoresAtivos)
                        : assinatura.getValorTotal();
            } else {
                binding.txtRotuloSuaParte.setVisibility(View.GONE);
                valorExibido = assinatura.getValorTotal();
            }
            binding.txtValorAssinatura.setText(String.format(LOCALE_PT_BR,
                    contexto.getString(R.string.formato_valor_reais), valorExibido));

            Calendar calendario = Calendar.getInstance();
            calendario.setTimeInMillis(assinatura.getDataProximoVencimento());
            int diaVencimento = calendario.get(Calendar.DAY_OF_MONTH);
            binding.txtVencimentoAssinatura.setText(contexto.getString(
                    R.string.formato_vence_dia, diaVencimento));

            int corFundoAvatar;
            int corTextoAvatar;
            if (usoGeral) {
                binding.txtSeloTipoUso.setText(R.string.selo_uso_geral);
                binding.txtSeloTipoUso.setTextColor(
                        ContextCompat.getColor(contexto, R.color.cor_verde_escuro));
                ViewCompat.setBackgroundTintList(binding.txtSeloTipoUso,
                        ColorStateList.valueOf(
                                ContextCompat.getColor(contexto, R.color.cor_verde_claro)));
                corFundoAvatar = R.color.cor_verde_claro;
                corTextoAvatar = R.color.cor_verde_escuro;
            } else {
                if (item.isGestor && !TextUtils.isEmpty(item.nomeDono)) {
                    binding.txtSeloTipoUso.setText(contexto.getString(
                            R.string.formato_uso_individual_dono, item.nomeDono));
                } else {
                    binding.txtSeloTipoUso.setText(R.string.selo_uso_individual);
                }
                binding.txtSeloTipoUso.setTextColor(
                        ContextCompat.getColor(contexto, R.color.cor_selo_membro));
                ViewCompat.setBackgroundTintList(binding.txtSeloTipoUso,
                        ColorStateList.valueOf(
                                ContextCompat.getColor(contexto, R.color.cor_selo_membro_fundo)));
                corFundoAvatar = R.color.cor_selo_membro_fundo;
                corTextoAvatar = R.color.cor_selo_membro;
            }

            if (!assinatura.isStatus()) {
                corFundoAvatar = R.color.cor_fundo_secundario;
                corTextoAvatar = R.color.cor_texto_secundario;
                binding.txtSeloStatusAssinatura.setVisibility(View.VISIBLE);
                binding.txtSeloStatusAssinatura.setText(R.string.selo_inativa);
                binding.txtSeloStatusAssinatura.setTextColor(
                        ContextCompat.getColor(contexto, R.color.cor_texto_secundario));
                ViewCompat.setBackgroundTintList(binding.txtSeloStatusAssinatura,
                        ColorStateList.valueOf(
                                ContextCompat.getColor(contexto, R.color.cor_fundo_secundario)));
                binding.txtNomeServico.setTextColor(
                        ContextCompat.getColor(contexto, R.color.cor_texto_secundario));
                binding.txtValorAssinatura.setTextColor(
                        ContextCompat.getColor(contexto, R.color.cor_texto_secundario));
            } else {
                binding.txtSeloStatusAssinatura.setVisibility(View.GONE);
                binding.txtNomeServico.setTextColor(
                        ContextCompat.getColor(contexto, R.color.cor_texto_primario));
                binding.txtValorAssinatura.setTextColor(
                        ContextCompat.getColor(contexto, R.color.cor_texto_primario));
            }

            binding.cardAvatarAssinatura.setCardBackgroundColor(
                    ContextCompat.getColor(contexto, corFundoAvatar));
            binding.txtInicialAssinatura.setTextColor(
                    ContextCompat.getColor(contexto, corTextoAvatar));

            List<String> partesSubtitulo = new ArrayList<>();
            if (!TextUtils.isEmpty(item.nomeCategoria)) {
                partesSubtitulo.add(item.nomeCategoria);
            }
            if (DomainConstants.PER_ANUAL.equalsIgnoreCase(assinatura.getPeriodicidade())) {
                partesSubtitulo.add(contexto.getString(R.string.periodicidade_anual));
            }
            if (!TextUtils.isEmpty(assinatura.getMoeda())
                    && !DomainConstants.MOEDA_BRL.equalsIgnoreCase(assinatura.getMoeda())) {
                partesSubtitulo.add(String.format(LOCALE_PT_BR, "%s %.2f",
                        assinatura.getMoeda(), assinatura.getValorOriginal()));
            }
            if (partesSubtitulo.isEmpty()) {
                binding.txtCategoriaAssinatura.setVisibility(View.GONE);
            } else {
                binding.txtCategoriaAssinatura.setVisibility(View.VISIBLE);
                binding.txtCategoriaAssinatura.setText(
                        TextUtils.join(" • ", partesSubtitulo));
            }

            binding.cardAssinatura.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onAssinaturaClick(assinatura);
                }
            });
        }
    }
}
