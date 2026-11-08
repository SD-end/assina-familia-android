package com.example.assinafamilia.view.adapter;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.assinafamilia.R;
import com.example.assinafamilia.databinding.ItemCustoMoradorBinding;
import com.example.assinafamilia.model.ResumoDashboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MoradorResumoAdapter
        extends ListAdapter<MoradorResumoAdapter.ItemGrade, MoradorResumoAdapter.MoradorViewHolder> {

    private static final Locale LOCALE_PT_BR = new Locale("pt", "BR");

    private static final DiffUtil.ItemCallback<ItemGrade> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<ItemGrade>() {
                @Override
                public boolean areItemsTheSame(@NonNull ItemGrade itemAntigo, @NonNull ItemGrade novoItem) {
                    if (itemAntigo.isAdicionarMembro() && novoItem.isAdicionarMembro()) {
                        return true;
                    }
                    if (itemAntigo.isAdicionarMembro() || novoItem.isAdicionarMembro()) {
                        return false;
                    }
                    ResumoDashboard.CustoMorador moradorAntigo = itemAntigo.getMorador();
                    ResumoDashboard.CustoMorador moradorNovo = novoItem.getMorador();
                    return moradorAntigo != null
                            && moradorNovo != null
                            && moradorAntigo.getUsuarioId() == moradorNovo.getUsuarioId();
                }

                @Override
                public boolean areContentsTheSame(@NonNull ItemGrade itemAntigo, @NonNull ItemGrade novoItem) {
                    if (itemAntigo.isAdicionarMembro() && novoItem.isAdicionarMembro()) {
                        return true;
                    }
                    ResumoDashboard.CustoMorador moradorAntigo = itemAntigo.getMorador();
                    ResumoDashboard.CustoMorador moradorNovo = novoItem.getMorador();
                    if (moradorAntigo == null || moradorNovo == null) {
                        return false;
                    }
                    return TextUtils.equals(moradorAntigo.getNomeMorador(), moradorNovo.getNomeMorador())
                            && moradorAntigo.getCustoTotalMorador() == moradorNovo.getCustoTotalMorador()
                            && moradorAntigo.getTotalIndividual() == moradorNovo.getTotalIndividual()
                            && moradorAntigo.getFracaoGeral() == moradorNovo.getFracaoGeral();
                }
            };

    @Nullable
    private final OnMoradorResumoClickListener listener;

    public MoradorResumoAdapter(@Nullable OnMoradorResumoClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    public interface OnMoradorResumoClickListener {
        void onMoradorClick(@NonNull ResumoDashboard.CustoMorador morador);

        void onAdicionarMembroClick();
    }

    public void atualizarDados(@Nullable List<ResumoDashboard.CustoMorador> moradores,
                               boolean exibirAdicionarMembro) {
        List<ItemGrade> itens = new ArrayList<>();
        if (moradores != null) {
            for (ResumoDashboard.CustoMorador morador : moradores) {
                if (morador != null) {
                    itens.add(ItemGrade.paraMorador(morador));
                }
            }
        }
        if (exibirAdicionarMembro) {
            itens.add(ItemGrade.paraAdicionar());
        }
        submitList(itens);
    }

    @NonNull
    @Override
    public MoradorViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCustoMoradorBinding binding = ItemCustoMoradorBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new MoradorViewHolder(binding, listener);
    }

    @Override
    public void onBindViewHolder(@NonNull MoradorViewHolder holder, int position) {
        holder.vincular(getItem(position));
    }

    public static final class ItemGrade {

        private final boolean adicionarMembro;

        @Nullable
        private final ResumoDashboard.CustoMorador morador;

        private ItemGrade(boolean adicionarMembro, @Nullable ResumoDashboard.CustoMorador morador) {
            this.adicionarMembro = adicionarMembro;
            this.morador = morador;
        }

        public static ItemGrade paraMorador(@NonNull ResumoDashboard.CustoMorador morador) {
            return new ItemGrade(false, morador);
        }

        public static ItemGrade paraAdicionar() {
            return new ItemGrade(true, null);
        }

        public boolean isAdicionarMembro() {
            return adicionarMembro;
        }

        @Nullable
        public ResumoDashboard.CustoMorador getMorador() {
            return morador;
        }
    }

    static class MoradorViewHolder extends RecyclerView.ViewHolder {

        private final ItemCustoMoradorBinding binding;

        @Nullable
        private final OnMoradorResumoClickListener listener;

        MoradorViewHolder(@NonNull ItemCustoMoradorBinding binding,
                          @Nullable OnMoradorResumoClickListener listener) {
            super(binding.getRoot());
            this.binding = binding;
            this.listener = listener;
        }

        void vincular(@NonNull ItemGrade item) {
            Context contexto = binding.getRoot().getContext();

            if (item.isAdicionarMembro()) {
                binding.cardMorador.setCardBackgroundColor(
                        ContextCompat.getColor(contexto, R.color.cor_fundo_tela));
                binding.cardAvatar.setCardBackgroundColor(
                        ContextCompat.getColor(contexto, R.color.cor_fundo_secundario));
                binding.txtInicialAvatar.setText(R.string.simbolo_mais);
                binding.txtInicialAvatar.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
                binding.txtInicialAvatar.setTextColor(
                        ContextCompat.getColor(contexto, R.color.cor_texto_secundario));
                binding.txtNomeMorador.setText(R.string.acao_adicionar_membro_curto);
                binding.txtNomeMorador.setTextColor(
                        ContextCompat.getColor(contexto, R.color.cor_texto_secundario));
                binding.txtCustoMensal.setVisibility(View.GONE);
                binding.cardMorador.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onAdicionarMembroClick();
                    }
                });
                return;
            }

            ResumoDashboard.CustoMorador morador = item.getMorador();
            if (morador == null) {
                return;
            }

            binding.cardMorador.setCardBackgroundColor(
                    ContextCompat.getColor(contexto, R.color.cor_fundo_cartao));
            binding.cardAvatar.setCardBackgroundColor(
                    ContextCompat.getColor(contexto, R.color.cor_selo_membro_fundo));

            String nomeExibicao = morador.getNomeMorador() != null
                    ? morador.getNomeMorador().trim()
                    : "";
            String inicial = nomeExibicao.isEmpty()
                    ? "M"
                    : nomeExibicao.substring(0, 1).toUpperCase(Locale.ROOT);

            binding.txtInicialAvatar.setText(inicial);
            binding.txtInicialAvatar.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
            binding.txtInicialAvatar.setTextColor(
                    ContextCompat.getColor(contexto, R.color.cor_verde_escuro));
            binding.txtNomeMorador.setText(nomeExibicao);
            binding.txtNomeMorador.setTextColor(
                    ContextCompat.getColor(contexto, R.color.cor_texto_primario));
            binding.txtCustoMensal.setVisibility(View.VISIBLE);
            binding.txtCustoMensal.setText(String.format(LOCALE_PT_BR,
                    contexto.getString(R.string.formato_valor_mes),
                    morador.getCustoTotalMorador()));
            binding.cardMorador.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onMoradorClick(morador);
                }
            });
        }
    }
}
