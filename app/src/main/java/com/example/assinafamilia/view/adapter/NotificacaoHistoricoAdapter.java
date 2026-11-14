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
import com.example.assinafamilia.databinding.ItemNotificacaoHistoricoBinding;
import com.example.assinafamilia.model.Assinatura;
import com.example.assinafamilia.model.Cobranca;
import com.example.assinafamilia.util.DomainConstants;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class NotificacaoHistoricoAdapter
        extends ListAdapter<NotificacaoHistoricoAdapter.ItemNotificacao,
        NotificacaoHistoricoAdapter.NotificacaoViewHolder> {

    private static final Locale LOCALE_PT_BR = new Locale("pt", "BR");
    private static final String SECAO_PENDENTES = "Pendentes e a resolver";
    private static final String SECAO_RESOLVIDAS = "Resolvidas e pagas";

    private static final DiffUtil.ItemCallback<ItemNotificacao> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<ItemNotificacao>() {
                @Override
                public boolean areItemsTheSame(@NonNull ItemNotificacao itemAntigo,
                                               @NonNull ItemNotificacao novoItem) {
                    return itemAntigo.id == novoItem.id;
                }

                @Override
                public boolean areContentsTheSame(@NonNull ItemNotificacao itemAntigo,
                                                  @NonNull ItemNotificacao novoItem) {
                    return itemAntigo.dataVencimento == novoItem.dataVencimento
                            && Float.compare(itemAntigo.valorCobrado, novoItem.valorCobrado) == 0
                            && TextUtils.equals(itemAntigo.statusPagamento,
                            novoItem.statusPagamento)
                            && TextUtils.equals(itemAntigo.nomeServico, novoItem.nomeServico)
                            && TextUtils.equals(itemAntigo.tipoDoUso, novoItem.tipoDoUso)
                            && itemAntigo.naoLida == novoItem.naoLida
                            && itemAntigo.isGestor == novoItem.isGestor
                            && itemAntigo.moradoresAtivos == novoItem.moradoresAtivos
                            && TextUtils.equals(itemAntigo.tituloSecao, novoItem.tituloSecao)
                            && itemAntigo.exibirDivisorAcima == novoItem.exibirDivisorAcima;
                }
            };

    @Nullable
    private final OnNotificacaoClickListener listener;

    public NotificacaoHistoricoAdapter(@Nullable OnNotificacaoClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    public interface OnNotificacaoClickListener {
        void onNotificacaoClick(@NonNull Cobranca cobranca);
    }

    public void atualizarDados(@Nullable List<Cobranca> cobrancas,
                               @Nullable Map<Integer, Assinatura> mapaAssinaturas,
                               @Nullable Set<Integer> idsNaoLidas,
                               boolean isGestor,
                               int quantidadeMoradoresAtivos) {
        List<ItemNotificacao> itens = new ArrayList<>();
        int moradores = Math.max(1, quantidadeMoradoresAtivos);
        if (cobrancas != null) {
            List<Cobranca> pendentes = new ArrayList<>();
            List<Cobranca> resolvidas = new ArrayList<>();
            for (Cobranca cobranca : cobrancas) {
                if (cobranca == null) {
                    continue;
                }
                if (DomainConstants.COB_PAGA.equals(cobranca.getStatusPagamento())) {
                    resolvidas.add(cobranca);
                } else {
                    pendentes.add(cobranca);
                }
            }

            for (int indice = 0; indice < pendentes.size(); indice++) {
                Cobranca cobranca = pendentes.get(indice);
                itens.add(montarItem(cobranca, mapaAssinaturas, idsNaoLidas, isGestor, moradores,
                        indice == 0 ? SECAO_PENDENTES : null, false));
            }

            for (int indice = 0; indice < resolvidas.size(); indice++) {
                Cobranca cobranca = resolvidas.get(indice);
                boolean primeiro = indice == 0;
                itens.add(montarItem(cobranca, mapaAssinaturas, idsNaoLidas, isGestor, moradores,
                        primeiro ? SECAO_RESOLVIDAS : null,
                        primeiro && !pendentes.isEmpty()));
            }
        }
        submitList(itens);
    }

    @NonNull
    private ItemNotificacao montarItem(@NonNull Cobranca cobranca,
                                       @Nullable Map<Integer, Assinatura> mapaAssinaturas,
                                       @Nullable Set<Integer> idsNaoLidas,
                                       boolean isGestor,
                                       int moradores,
                                       @Nullable String tituloSecao,
                                       boolean exibirDivisorAcima) {
        Assinatura assinatura = mapaAssinaturas != null
                ? mapaAssinaturas.get(cobranca.getAssinaturaId())
                : null;
        String nomeServico = assinatura != null ? assinatura.getNomeServico() : null;
        String tipoDoUso = assinatura != null ? assinatura.getTipoDoUso() : null;
        boolean naoLida = idsNaoLidas != null && idsNaoLidas.contains(cobranca.getId());
        return new ItemNotificacao(
                cobranca,
                nomeServico,
                tipoDoUso,
                naoLida,
                isGestor,
                moradores,
                tituloSecao,
                exibirDivisorAcima);
    }

    @NonNull
    @Override
    public NotificacaoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemNotificacaoHistoricoBinding binding = ItemNotificacaoHistoricoBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new NotificacaoViewHolder(binding, listener);
    }

    @Override
    public void onBindViewHolder(@NonNull NotificacaoViewHolder holder, int position) {
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

    public static final class ItemNotificacao {

        private final Cobranca cobranca;

        private final int id;

        private final long dataVencimento;

        private final float valorCobrado;

        @Nullable
        private final String statusPagamento;

        @Nullable
        private final String nomeServico;

        @Nullable
        private final String tipoDoUso;

        private final boolean naoLida;

        private final boolean isGestor;

        private final int moradoresAtivos;

        @Nullable
        private final String tituloSecao;

        private final boolean exibirDivisorAcima;

        private ItemNotificacao(@NonNull Cobranca cobranca,
                                @Nullable String nomeServico,
                                @Nullable String tipoDoUso,
                                boolean naoLida,
                                boolean isGestor,
                                int moradoresAtivos,
                                @Nullable String tituloSecao,
                                boolean exibirDivisorAcima) {
            this.cobranca = cobranca;
            this.id = cobranca.getId();
            this.dataVencimento = cobranca.getDataVencimento();
            this.valorCobrado = cobranca.getValorCobrado();
            this.statusPagamento = cobranca.getStatusPagamento();
            this.nomeServico = nomeServico;
            this.tipoDoUso = tipoDoUso;
            this.naoLida = naoLida;
            this.isGestor = isGestor;
            this.moradoresAtivos = moradoresAtivos;
            this.tituloSecao = tituloSecao;
            this.exibirDivisorAcima = exibirDivisorAcima;
        }
    }

    static class NotificacaoViewHolder extends RecyclerView.ViewHolder {

        private final ItemNotificacaoHistoricoBinding binding;

        @Nullable
        private final OnNotificacaoClickListener listener;

        NotificacaoViewHolder(@NonNull ItemNotificacaoHistoricoBinding binding,
                              @Nullable OnNotificacaoClickListener listener) {
            super(binding.getRoot());
            this.binding = binding;
            this.listener = listener;
        }

        void vincular(@NonNull ItemNotificacao item) {
            Context contexto = binding.getRoot().getContext();
            Cobranca cobranca = item.cobranca;

            if (!TextUtils.isEmpty(item.tituloSecao)) {
                binding.layoutCabecalhoSecaoNotificacao.setVisibility(View.VISIBLE);
                binding.txtTituloSecaoNotificacao.setText(item.tituloSecao);
                binding.divisorSecaoNotificacao.setVisibility(
                        item.exibirDivisorAcima ? View.VISIBLE : View.GONE);
            } else {
                binding.layoutCabecalhoSecaoNotificacao.setVisibility(View.GONE);
                binding.divisorSecaoNotificacao.setVisibility(View.GONE);
            }

            String nomeExibicao = !TextUtils.isEmpty(item.nomeServico)
                    ? item.nomeServico
                    : contexto.getString(R.string.titulo_assinatura_generica);
            binding.txtTituloNotificacao.setText(nomeExibicao);

            binding.txtSeloNovaNotificacao.setVisibility(
                    item.naoLida && !DomainConstants.COB_PAGA.equals(item.statusPagamento)
                            ? View.VISIBLE
                            : View.GONE);
            binding.cardItemNotificacao.setStrokeColor(ContextCompat.getColor(contexto,
                    item.naoLida ? R.color.cor_verde_primario : R.color.cor_borda_sutil));

            long diffDias = calcularDiferencaDias(item.dataVencimento);
            int tituloAlerta;
            if (DomainConstants.COB_ATRASADA.equals(item.statusPagamento) || diffDias < 0) {
                tituloAlerta = R.string.notificacao_titulo_atrasada;
            } else if (diffDias == 0) {
                tituloAlerta = R.string.notificacao_titulo_vence_hoje;
            } else if (diffDias == 1) {
                tituloAlerta = R.string.notificacao_titulo_vence_amanha;
            } else {
                tituloAlerta = R.string.notificacao_titulo_lembrete_enviado;
            }
            String textoRelativo = montarTextoRelativo(contexto, item, diffDias);
            binding.txtMensagemNotificacao.setText(contexto.getString(
                    R.string.formato_notificacao_resumo,
                    contexto.getString(tituloAlerta),
                    textoRelativo));

            int corIconeFundo;
            int icone;
            int corIcone;
            if (DomainConstants.COB_ATRASADA.equals(item.statusPagamento)) {
                corIconeFundo = R.color.cor_status_atrasado_fundo;
                icone = android.R.drawable.ic_dialog_alert;
                corIcone = R.color.cor_status_atrasado;
            } else if (DomainConstants.COB_PAGA.equals(item.statusPagamento)) {
                corIconeFundo = R.color.cor_status_pago_fundo;
                icone = android.R.drawable.ic_popup_reminder;
                corIcone = R.color.cor_status_pago;
            } else {
                corIconeFundo = R.color.cor_status_pendente_fundo;
                icone = android.R.drawable.ic_popup_reminder;
                corIcone = R.color.cor_status_pendente;
            }
            binding.cardIconeNotificacao.setCardBackgroundColor(
                    ContextCompat.getColor(contexto, corIconeFundo));
            binding.imgIconeNotificacao.setImageResource(icone);
            ImageViewCompat.setImageTintList(binding.imgIconeNotificacao,
                    ColorStateList.valueOf(ContextCompat.getColor(contexto, corIcone)));

            if (!item.isGestor && DomainConstants.USO_GERAL.equalsIgnoreCase(item.tipoDoUso)) {
                float minhaParte = item.valorCobrado / Math.max(1, item.moradoresAtivos);
                binding.txtValorNotificacao.setText(String.format(LOCALE_PT_BR,
                        contexto.getString(R.string.formato_sua_parte_valor), minhaParte));
            } else {
                binding.txtValorNotificacao.setText(String.format(LOCALE_PT_BR,
                        contexto.getString(R.string.formato_valor_reais), item.valorCobrado));
            }

            int textoStatus;
            int corTexto;
            int corFundo;
            if (DomainConstants.COB_PAGA.equals(item.statusPagamento)) {
                textoStatus = R.string.status_pago;
                corTexto = R.color.cor_status_pago;
                corFundo = R.color.cor_status_pago_fundo;
            } else if (DomainConstants.COB_ATRASADA.equals(item.statusPagamento)) {
                textoStatus = R.string.status_atrasada;
                corTexto = R.color.cor_status_atrasado;
                corFundo = R.color.cor_status_atrasado_fundo;
            } else {
                textoStatus = R.string.status_pendente;
                corTexto = R.color.cor_status_pendente;
                corFundo = R.color.cor_status_pendente_fundo;
            }
            binding.txtStatusNotificacao.setText(textoStatus);
            binding.txtStatusNotificacao.setTextColor(
                    ContextCompat.getColor(contexto, corTexto));
            ViewCompat.setBackgroundTintList(binding.txtStatusNotificacao,
                    ColorStateList.valueOf(ContextCompat.getColor(contexto, corFundo)));

            binding.cardItemNotificacao.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onNotificacaoClick(cobranca);
                }
            });
        }

        @NonNull
        private String montarTextoRelativo(@NonNull Context contexto,
                                           @NonNull ItemNotificacao item,
                                           long diffDias) {
            if (DomainConstants.COB_PAGA.equals(item.statusPagamento)) {
                SimpleDateFormat formatoData = new SimpleDateFormat("dd/MM/yyyy", LOCALE_PT_BR);
                return contexto.getString(R.string.formato_cobrado_em,
                        formatoData.format(new Date(item.dataVencimento)));
            }
            if (diffDias == 0) {
                return contexto.getString(R.string.vence_hoje);
            }
            if (diffDias == 1) {
                return contexto.getString(R.string.vence_amanha);
            }
            if (diffDias == -1) {
                return contexto.getString(R.string.venceu_ontem);
            }
            if (diffDias < -1) {
                return contexto.getString(R.string.formato_venceu_ha_dias,
                        (int) Math.abs(diffDias));
            }
            return contexto.getString(R.string.formato_vence_em_dias, (int) diffDias);
        }
    }
}
