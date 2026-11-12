package com.example.assinafamilia.view.adapter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.assinafamilia.R;
import com.example.assinafamilia.databinding.ItemMoradorFamiliaBinding;
import com.example.assinafamilia.model.Usuario;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MoradorFamiliaAdapter
        extends ListAdapter<MoradorFamiliaAdapter.ItemMorador,
        MoradorFamiliaAdapter.MoradorViewHolder> {

    private static final DiffUtil.ItemCallback<ItemMorador> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<ItemMorador>() {
                @Override
                public boolean areItemsTheSame(@NonNull ItemMorador itemAntigo,
                                               @NonNull ItemMorador novoItem) {
                    return itemAntigo.id == novoItem.id;
                }

                @Override
                public boolean areContentsTheSame(@NonNull ItemMorador itemAntigo,
                                                  @NonNull ItemMorador novoItem) {
                    return TextUtils.equals(itemAntigo.nome, novoItem.nome)
                            && TextUtils.equals(itemAntigo.email, novoItem.email)
                            && itemAntigo.gestor == novoItem.gestor
                            && itemAntigo.ativo == novoItem.ativo
                            && itemAntigo.usuarioAtual == novoItem.usuarioAtual
                            && itemAntigo.permitirGerenciar == novoItem.permitirGerenciar;
                }
            };

    @Nullable
    private final OnMoradorActionListener listener;

    public MoradorFamiliaAdapter(@Nullable OnMoradorActionListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    public interface OnMoradorActionListener {
        void onAlternarStatusMorador(@NonNull Usuario morador, boolean novoStatusAtivo);
    }

    public void atualizarMoradores(@Nullable List<Usuario> moradores,
                                   int usuarioLogadoId,
                                   boolean gestorLogado) {
        List<ItemMorador> itens = new ArrayList<>();
        if (moradores != null) {
            for (Usuario usuario : moradores) {
                if (usuario == null) {
                    continue;
                }
                boolean usuarioAtual = usuario.getId() == usuarioLogadoId;
                boolean permitirGerenciar = gestorLogado
                        && !usuario.isGestor()
                        && !usuarioAtual;
                ItemMorador item = new ItemMorador(usuario, usuarioAtual, permitirGerenciar);
                if (usuario.isGestor()) {
                    itens.add(0, item);
                } else {
                    itens.add(item);
                }
            }
        }
        submitList(itens);
    }

    @NonNull
    @Override
    public MoradorViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemMoradorFamiliaBinding binding = ItemMoradorFamiliaBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new MoradorViewHolder(binding, listener);
    }

    @Override
    public void onBindViewHolder(@NonNull MoradorViewHolder holder, int position) {
        holder.vincular(getItem(position));
    }

    public static final class ItemMorador {

        @NonNull
        private final Usuario usuario;

        private final int id;

        @NonNull
        private final String nome;

        @NonNull
        private final String email;

        private final boolean gestor;

        private final boolean ativo;

        private final boolean usuarioAtual;

        private final boolean permitirGerenciar;

        private ItemMorador(@NonNull Usuario usuario,
                            boolean usuarioAtual,
                            boolean permitirGerenciar) {
            this.usuario = usuario;
            this.id = usuario.getId();
            this.nome = usuario.getNome() != null ? usuario.getNome() : "";
            this.email = usuario.getEmail() != null ? usuario.getEmail() : "";
            this.gestor = usuario.isGestor();
            this.ativo = usuario.isAtivo();
            this.usuarioAtual = usuarioAtual;
            this.permitirGerenciar = permitirGerenciar;
        }

        public Usuario getUsuario() {
            return usuario;
        }
    }

    static class MoradorViewHolder extends RecyclerView.ViewHolder {

        private final ItemMoradorFamiliaBinding binding;

        @Nullable
        private final OnMoradorActionListener listener;

        MoradorViewHolder(@NonNull ItemMoradorFamiliaBinding binding,
                          @Nullable OnMoradorActionListener listener) {
            super(binding.getRoot());
            this.binding = binding;
            this.listener = listener;
        }

        void vincular(@NonNull ItemMorador item) {
            Context contexto = binding.getRoot().getContext();

            binding.txtIniciaisMorador.setText(obterIniciais(item.nome, item.gestor));
            binding.txtNomeMorador.setText(item.usuarioAtual
                    ? contexto.getString(R.string.formato_nome_voce, item.nome)
                    : item.nome);

            if (item.gestor) {
                binding.cardMoradorFamilia.setAlpha(1.0f);
                binding.cardAvatarMorador.setCardBackgroundColor(
                        ContextCompat.getColor(contexto, R.color.cor_verde_claro));
                binding.cardAvatarMorador.setStrokeWidth(converterDpParaPx(contexto, 1));
                binding.cardAvatarMorador.setStrokeColor(
                        ContextCompat.getColor(contexto, R.color.cor_primaria));
                binding.txtIniciaisMorador.setTextColor(
                        ContextCompat.getColor(contexto, R.color.cor_verde_escuro));
                binding.txtSubtituloMorador.setText(R.string.subtitulo_proprietario_gestor);
                binding.txtSubtituloMorador.setTextColor(
                        ContextCompat.getColor(contexto, R.color.cor_texto_secundario));
                binding.txtSeloPerfilMorador.setVisibility(View.VISIBLE);
                binding.txtSeloPerfilMorador.setText(R.string.selo_gestor);
                binding.txtSeloPerfilMorador.setTextColor(
                        ContextCompat.getColor(contexto, R.color.cor_verde_escuro));
                ViewCompat.setBackgroundTintList(binding.txtSeloPerfilMorador,
                        ColorStateList.valueOf(
                                ContextCompat.getColor(contexto, R.color.cor_verde_claro)));
                binding.btnOpcoesMorador.setVisibility(View.GONE);
                binding.cardMoradorFamilia.setOnClickListener(null);
                return;
            }

            binding.cardAvatarMorador.setStrokeWidth(0);

            if (item.ativo) {
                binding.cardMoradorFamilia.setAlpha(1.0f);
                binding.cardAvatarMorador.setCardBackgroundColor(
                        ContextCompat.getColor(contexto, R.color.cor_azul_claro));
                binding.txtIniciaisMorador.setTextColor(
                        ContextCompat.getColor(contexto, R.color.cor_azul_escuro));
                binding.txtSubtituloMorador.setText(R.string.subtitulo_membro_ativo);
                binding.txtSubtituloMorador.setTextColor(
                        ContextCompat.getColor(contexto, R.color.cor_texto_secundario));
                binding.txtSeloPerfilMorador.setVisibility(View.GONE);
            } else {
                binding.cardMoradorFamilia.setAlpha(0.75f);
                binding.cardAvatarMorador.setCardBackgroundColor(
                        ContextCompat.getColor(contexto, R.color.cor_fundo_secundario));
                binding.txtIniciaisMorador.setTextColor(
                        ContextCompat.getColor(contexto, R.color.cor_texto_secundario));
                binding.txtSubtituloMorador.setText(R.string.subtitulo_membro_inativo);
                binding.txtSubtituloMorador.setTextColor(
                        ContextCompat.getColor(contexto, R.color.cor_status_pendente));
                binding.txtSeloPerfilMorador.setVisibility(View.VISIBLE);
                binding.txtSeloPerfilMorador.setText(R.string.selo_inativo);
                binding.txtSeloPerfilMorador.setTextColor(
                        ContextCompat.getColor(contexto, R.color.cor_texto_secundario));
                ViewCompat.setBackgroundTintList(binding.txtSeloPerfilMorador,
                        ColorStateList.valueOf(
                                ContextCompat.getColor(contexto, R.color.cor_fundo_secundario)));
            }

            if (item.permitirGerenciar) {
                binding.btnOpcoesMorador.setVisibility(View.VISIBLE);
                View.OnClickListener abrirOpcoes = v -> abrirMenuOpcoes(contexto, item);
                binding.btnOpcoesMorador.setOnClickListener(abrirOpcoes);
                binding.cardMoradorFamilia.setOnClickListener(abrirOpcoes);
            } else {
                binding.btnOpcoesMorador.setVisibility(View.GONE);
                binding.btnOpcoesMorador.setOnClickListener(null);
                binding.cardMoradorFamilia.setOnClickListener(null);
            }
        }

        private void abrirMenuOpcoes(@NonNull Context contexto, @NonNull ItemMorador item) {
            PopupMenu menu = new PopupMenu(contexto, binding.btnOpcoesMorador);
            menu.getMenu().add(0, 1, 0, item.ativo
                    ? R.string.acao_inativar_morador
                    : R.string.acao_reativar_morador);
            menu.setOnMenuItemClickListener(menuItem -> {
                if (menuItem.getItemId() == 1) {
                    if (listener != null) {
                        listener.onAlternarStatusMorador(item.usuario, !item.ativo);
                    }
                    return true;
                }
                return false;
            });
            menu.show();
        }

        @NonNull
        private static String obterIniciais(@NonNull String nome, boolean gestor) {
            String limpo = nome.trim();
            if (limpo.isEmpty()) {
                return "U";
            }
            String[] partes = limpo.split("\\s+");
            if (gestor && partes.length >= 2) {
                String primeira = partes[0].substring(0, 1);
                String ultima = partes[partes.length - 1].substring(0, 1);
                return (primeira + ultima).toUpperCase(Locale.ROOT);
            }
            return limpo.substring(0, 1).toUpperCase(Locale.ROOT);
        }

        private static int converterDpParaPx(@NonNull Context contexto, int dp) {
            return (int) (dp * contexto.getResources().getDisplayMetrics().density);
        }
    }
}
