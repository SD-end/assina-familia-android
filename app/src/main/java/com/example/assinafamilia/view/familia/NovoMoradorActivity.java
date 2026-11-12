package com.example.assinafamilia.view.familia;

import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.assinafamilia.R;
import com.example.assinafamilia.databinding.ActivityNovoMoradorBinding;
import com.example.assinafamilia.util.Resource;
import com.example.assinafamilia.viewmodel.FamiliaViewModel;
import com.google.android.material.snackbar.Snackbar;

public class NovoMoradorActivity extends AppCompatActivity {

    private ActivityNovoMoradorBinding binding;
    private FamiliaViewModel familiaViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityNovoMoradorBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutRaizNovoMorador,
                (view, windowInsets) -> {
                    Insets barras = windowInsets.getInsets(
                            WindowInsetsCompat.Type.systemBars());
                    view.setPadding(barras.left, barras.top, barras.right, barras.bottom);
                    return windowInsets;
                });

        familiaViewModel = new ViewModelProvider(this).get(FamiliaViewModel.class);

        if (!familiaViewModel.isGestor()) {
            Toast.makeText(this, R.string.erro_acesso_restrito_gestor, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        binding.btnVoltarNovoMorador.setOnClickListener(v -> finish());
        binding.btnAdicionarMorador.setOnClickListener(v -> salvarMorador());
    }

    private void salvarMorador() {
        binding.tilNomeMorador.setError(null);
        binding.tilEmailMorador.setError(null);
        binding.tilSenhaProvisoria.setError(null);

        String nome = binding.edtNomeMorador.getText() != null
                ? binding.edtNomeMorador.getText().toString().trim()
                : "";
        String email = binding.edtEmailMorador.getText() != null
                ? binding.edtEmailMorador.getText().toString().trim()
                : "";
        String senha = binding.edtSenhaProvisoria.getText() != null
                ? binding.edtSenhaProvisoria.getText().toString()
                : "";

        if (nome.isEmpty() || nome.length() > 120) {
            binding.tilNomeMorador.setError(getString(R.string.erro_nome_morador_obrigatorio));
            return;
        }

        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmailMorador.setError(getString(R.string.erro_email_invalido_morador));
            return;
        }

        if (senha.length() < 6) {
            binding.tilSenhaProvisoria.setError(
                    getString(R.string.erro_senha_provisoria_curta));
            return;
        }

        familiaViewModel.adicionarMorador(nome, email, senha).observe(this, recurso -> {
            if (recurso == null) {
                return;
            }

            if (recurso.getStatus() == Resource.Status.CARREGANDO) {
                binding.pbCarregandoNovoMorador.setVisibility(View.VISIBLE);
                binding.btnAdicionarMorador.setEnabled(false);
            } else if (recurso.getStatus() == Resource.Status.SUCESSO) {
                binding.pbCarregandoNovoMorador.setVisibility(View.GONE);
                binding.btnAdicionarMorador.setEnabled(true);
                String mensagem = recurso.isFallbackOffline() && recurso.getMensagem() != null
                        ? recurso.getMensagem()
                        : getString(R.string.sucesso_morador_adicionado);
                Toast.makeText(this, mensagem, Toast.LENGTH_LONG).show();
                finish();
            } else {
                binding.pbCarregandoNovoMorador.setVisibility(View.GONE);
                binding.btnAdicionarMorador.setEnabled(true);
                String mensagem = recurso.getMensagem() != null
                        ? recurso.getMensagem()
                        : getString(R.string.erro_operacao_categoria);
                Snackbar.make(binding.layoutRaizNovoMorador, mensagem,
                        Snackbar.LENGTH_LONG).show();
            }
        });
    }
}
