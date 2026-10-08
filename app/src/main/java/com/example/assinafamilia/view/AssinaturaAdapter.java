package com.example.assinafamilia.view;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.assinafamilia.R;
import com.example.assinafamilia.model.Assinatura;
import java.util.List;

public class AssinaturaAdapter extends RecyclerView.Adapter<AssinaturaAdapter.ViewHolder> {

    private List<Assinatura> assinaturas;

    public AssinaturaAdapter(List<Assinatura> assinaturas) {
        this.assinaturas = assinaturas;
    }

    public void setAssinaturas(List<Assinatura> novasAssinaturas) {
        this.assinaturas = novasAssinaturas;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_assinatura, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Assinatura assinatura = assinaturas.get(position);
        holder.txtNome.setText(assinatura.getNomeServico());
        holder.txtTipo.setText(assinatura.getTipoDoUso());
        holder.txtValor.setText(String.format("R$ %.2f", assinatura.getValorTotal()));
    }

    @Override
    public int getItemCount() {
        return assinaturas != null ? assinaturas.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtNome;
        TextView txtTipo;
        TextView txtValor;

        ViewHolder(View itemView) {
            super(itemView);
            txtNome = itemView.findViewById(R.id.txtNomeServico);
            txtTipo = itemView.findViewById(R.id.txtTipoUso);
            txtValor = itemView.findViewById(R.id.txtValor);
        }
    }
}