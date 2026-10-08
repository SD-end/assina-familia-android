package com.example.assinafamilia.view;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.assinafamilia.data.AppDatabase;
import com.example.assinafamilia.data.AssinaturaDao;
import com.example.assinafamilia.databinding.ActivityNovaAssinaturaBinding;
import com.example.assinafamilia.model.Assinatura;

public class NovaAssinaturaActivity extends AppCompatActivity {

    private ActivityNovaAssinaturaBinding binding;
    private AppDatabase database;
    private AssinaturaDao dao;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNovaAssinaturaBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        database = AppDatabase.getInstance(this);
        dao = database.assinaturaDao();

        binding.btnSalvar.setOnClickListener(v -> {

            String nome = binding.editNomeServico.getText().toString();
            String valorString = binding.editValorTotal.getText().toString();
            String tipo = binding.editTipoUso.getText().toString();

            if (nome.isEmpty() || valorString.isEmpty() || tipo.isEmpty()) {
                Toast.makeText(this, "Preencha todos os campos!", Toast.LENGTH_SHORT).show();
                return;
            }

            float valor = Float.parseFloat(valorString);

            Assinatura novaAssinatura = new Assinatura(nome, valor, tipo);

            dao.insert(novaAssinatura);

            Toast.makeText(this, "Assinatura salva com sucesso!", Toast.LENGTH_LONG).show();
            finish();
        });
    }
}