package com.example.assinafamilia.view;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.assinafamilia.data.AppDatabase;
import com.example.assinafamilia.data.AssinaturaDao;
import com.example.assinafamilia.databinding.ActivityDashboardBinding;
import com.example.assinafamilia.model.Assinatura;
import java.util.ArrayList;
import java.util.List;

public class DashboardActivity extends AppCompatActivity {

    private ActivityDashboardBinding binding;
    private AppDatabase database;
    private AssinaturaDao dao;
    private AssinaturaAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        database = AppDatabase.getInstance(this);
        dao = database.assinaturaDao();

        binding.recyclerViewAssinaturas.setLayoutManager(new LinearLayoutManager(this));

        adapter = new AssinaturaAdapter(new ArrayList<>(), assinatura -> {

            new AlertDialog.Builder(this)
                    .setTitle("Apagar Assinatura")
                    .setMessage("Deseja realmente apagar '" + assinatura.getNomeServico() + "'?")
                    .setPositiveButton("Sim", (dialog, which) -> {

                        dao.delete(assinatura);
                        Toast.makeText(this, "Assinatura apagada!", Toast.LENGTH_SHORT).show();

                        carregarDadosDoBanco();
                    })
                    .setNegativeButton("Não", null)
                    .show();
        });

        binding.recyclerViewAssinaturas.setAdapter(adapter);

        // Botões
        binding.btnIrParaNovaAssinatura.setOnClickListener(v -> {
            startActivity(new Intent(DashboardActivity.this, NovaAssinaturaActivity.class));
        });

        binding.btnSair.setOnClickListener(v -> {
            startActivity(new Intent(DashboardActivity.this, MainActivity.class));
            finish();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        carregarDadosDoBanco();
    }

    private void carregarDadosDoBanco() {
        List<Assinatura> listaSalva = dao.getAllAssinaturas();
        adapter.setAssinaturas(listaSalva);

        float totalGasto = 0;
        for (Assinatura ass : listaSalva) {
            totalGasto += ass.getValorTotal();
        }
        binding.txtGastoTotal.setText(String.format("R$ %.2f", totalGasto));
    }
}