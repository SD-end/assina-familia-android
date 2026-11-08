package com.example.assinafamilia.view.adapter;

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
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.assinafamilia.R;
import com.example.assinafamilia.databinding.ItemCategoriaBinding;
import com.example.assinafamilia.model.Categoria;

import java.util.ArrayList;
import java.util.List;

public class CategoriaAdapter
        extends ListAdapter<CategoriaAdapter.ItemCategoria, CategoriaAdapter.CategoriaViewHolder> {

    private static final DiffUtil.ItemCallback<ItemCategoria> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<ItemCategoria>() {
                @Override
                public boolean areItemsTheSame(@NonNull ItemCategoria itemAntigo,
                                               @NonNull ItemCategoria novoItem) {
                    return itemAntigo.categoria.getId() == novoItem.categoria.getId();
                }

                @Override
                public boolean areContentsTheSame(@NonNull ItemCategoria itemAntigo,
                                                  @NonNull ItemCategoria novoItem) {
                    return TextUtils.equals(itemAntigo.categoria.getNome(), novoItem.categoria.getNome())
                            && TextUtils.equals(itemAntigo.categoria.getDescricao(),
                            novoItem.categoria.getDescricao())
                            && itemAntigo.categoria.isAtiva() == novoItem.categoria.isAtiva()
                            && itemAntigo.isGestor == novoItem.isGestor;
                }
            };

    @Nullable
    private final OnCategoriaClickListener listener;

    public CategoriaAdapter(@Nullable OnCategoriaClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    public interface OnCategoriaClickListener {
        void onEditarClick(@NonNull Categoria categoria);

        void onAlternarStatusClick(@NonNull Categoria categoria);

        void onExcluirClick(@NonNull Categoria categoria);
    }

    public void atualizarDados(@Nullable List<Categoria> categorias, boolean isGestor) {
        List<ItemCategoria> itens = new ArrayList<>();
        if (categorias != null) {
            for (Categoria categoria : categorias) {
                if (categoria == null) {
                    continue;
                }
                itens.add(new ItemCategoria(categoria, isGestor));
            }
        }
        submitList(itens);
    }

    @NonNull
    @Override
    public CategoriaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCategoriaBinding binding = ItemCategoriaBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new CategoriaViewHolder(binding, listener);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoriaViewHolder holder, int position) {
        holder.vincular(getItem(position));
    }

    public static final class ItemCategoria {

        private final Categoria categoria;

        private final boolean isGestor;

        private ItemCategoria(@NonNull Categoria categoria, boolean isGestor) {
            this.categoria = categoria;
            this.isGestor = isGestor;
        }

        public Categoria getCategoria() {
            return categoria;
        }

        public boolean isGestor() {
            return isGestor;
        }
    }

    static class CategoriaViewHolder extends RecyclerView.ViewHolder {

        private final ItemCategoriaBinding binding;

        @Nullable
        private final OnCategoriaClickListener listener;

        CategoriaViewHolder(@NonNull ItemCategoriaBinding binding,
                            @Nullable OnCategoriaClickListener listener) {
            super(binding.getRoot());
            this.binding = binding;
            this.listener = listener;
        }

        void vincular(@NonNull ItemCategoria item) {
            Context contexto = binding.getRoot().getContext();
            Categoria categoria = item.categoria;

            binding.txtNomeCategoria.setText(categoria.getNome() != null ? categoria.getNome() : "");

            String descricao = categoria.getDescricao() != null
                    ? categoria.getDescricao().trim()
                    : "";
            if (descricao.isEmpty()) {
                binding.txtDescricaoCategoria.setVisibility(View.GONE);
            } else {
                binding.txtDescricaoCategoria.setVisibility(View.VISIBLE);
                binding.txtDescricaoCategoria.setText(descricao);
            }

            if (categoria.isAtiva()) {
                binding.cardIconeCategoria.setCardBackgroundColor(
                        ContextCompat.getColor(contexto, R.color.cor_verde_claro));
                ImageViewCompat.setImageTintList(binding.imgIconeCategoria,
                        ColorStateList.valueOf(
                                ContextCompat.getColor(contexto, R.color.cor_verde_escuro)));
                binding.txtNomeCategoria.setTextColor(
                        ContextCompat.getColor(contexto, R.color.cor_texto_primario));
                binding.txtStatusCategoria.setText(R.string.selo_ativa);
                binding.txtStatusCategoria.setTextColor(
                        ContextCompat.getColor(contexto, R.color.cor_verde_escuro));
                ViewCompat.setBackgroundTintList(binding.txtStatusCategoria,
                        ColorStateList.valueOf(
                                ContextCompat.getColor(contexto, R.color.cor_verde_claro)));
                binding.btnAlternarStatusCategoria.setText(R.string.acao_inativar);
                binding.btnAlternarStatusCategoria.setTextColor(
                        ContextCompat.getColor(contexto, R.color.cor_status_pendente));
            } else {
                binding.cardIconeCategoria.setCardBackgroundColor(
                        ContextCompat.getColor(contexto, R.color.cor_fundo_secundario));
                ImageViewCompat.setImageTintList(binding.imgIconeCategoria,
                        ColorStateList.valueOf(
                                ContextCompat.getColor(contexto, R.color.cor_texto_secundario)));
                binding.txtNomeCategoria.setTextColor(
                        ContextCompat.getColor(contexto, R.color.cor_texto_secundario));
                binding.txtStatusCategoria.setText(R.string.selo_inativa);
                binding.txtStatusCategoria.setTextColor(
                        ContextCompat.getColor(contexto, R.color.cor_texto_secundario));
                ViewCompat.setBackgroundTintList(binding.txtStatusCategoria,
                        ColorStateList.valueOf(
                                ContextCompat.getColor(contexto, R.color.cor_fundo_secundario)));
                binding.btnAlternarStatusCategoria.setText(R.string.acao_ativar);
                binding.btnAlternarStatusCategoria.setTextColor(
                        ContextCompat.getColor(contexto, R.color.cor_verde_escuro));
            }

            binding.layoutAcoesGestor.setVisibility(item.isGestor ? View.VISIBLE : View.GONE);

            binding.btnEditarCategoria.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onEditarClick(categoria);
                }
            });
            binding.btnAlternarStatusCategoria.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onAlternarStatusClick(categoria);
                }
            });
            binding.btnExcluirCategoria.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onExcluirClick(categoria);
                }
            });
        }
    }
}
