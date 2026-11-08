package com.example.assinafamilia.view.auth;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;

import com.example.assinafamilia.R;
import com.example.assinafamilia.databinding.ActivityCadastroBinding;
import com.example.assinafamilia.model.Usuario;
import com.example.assinafamilia.util.Resource;
import com.example.assinafamilia.view.DashboardActivity;
import com.example.assinafamilia.viewmodel.AuthViewModel;
import com.google.android.material.snackbar.Snackbar;

public class CadastroActivity extends AppCompatActivity {

    private ActivityCadastroBinding binding;
    private AuthViewModel authViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityCadastroBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        binding.barraSuperior.setNavigationOnClickListener(v -> finish());
        binding.txtEntrar.setOnClickListener(v -> finish());

        binding.btnCriarFamilia.setOnClickListener(v -> {
            binding.tilNome.setError(null);
            binding.tilEmail.setError(null);
            binding.tilSenha.setError(null);
            binding.tilConfirmarSenha.setError(null);

            String nome = binding.edtNome.getText() != null
                    ? binding.edtNome.getText().toString()
                    : "";
            String email = binding.edtEmail.getText() != null
                    ? binding.edtEmail.getText().toString()
                    : "";
            String senha = binding.edtSenha.getText() != null
                    ? binding.edtSenha.getText().toString()
                    : "";
            String confirmacaoSenha = binding.edtConfirmarSenha.getText() != null
                    ? binding.edtConfirmarSenha.getText().toString()
                    : "";

            LiveData<Resource<Usuario>> resultado =
                    authViewModel.cadastrarGestor(nome, email, senha, confirmacaoSenha);

            resultado.observe(this, recurso -> {
                if (recurso == null) {
                    return;
                }

                if (recurso.getStatus() == Resource.Status.CARREGANDO) {
                    binding.pbCarregando.setVisibility(View.VISIBLE);
                    binding.btnCriarFamilia.setEnabled(false);
                } else if (recurso.getStatus() == Resource.Status.SUCESSO) {
                    binding.pbCarregando.setVisibility(View.GONE);
                    binding.btnCriarFamilia.setEnabled(true);
                    navegarParaPosLogin();
                } else if (recurso.getStatus() == Resource.Status.ERRO) {
                    binding.pbCarregando.setVisibility(View.GONE);
                    binding.btnCriarFamilia.setEnabled(true);

                    String mensagem = recurso.getMensagem() != null
                            ? recurso.getMensagem()
                            : getString(R.string.estado_vazio_descricao);

                    String nomeLimpo = nome.trim();
                    String emailLimpo = email.trim();

                    if (nomeLimpo.isEmpty() || nomeLimpo.length() > 120) {
                        binding.tilNome.setError(mensagem);
                    } else if (emailLimpo.isEmpty()
                            || !Patterns.EMAIL_ADDRESS.matcher(emailLimpo).matches()) {
                        binding.tilEmail.setError(mensagem);
                    } else if (senha.length() < 6) {
                        binding.tilSenha.setError(mensagem);
                    } else if (!senha.equals(confirmacaoSenha)) {
                        binding.tilConfirmarSenha.setError(mensagem);
                    } else {
                        Snackbar.make(binding.getRoot(), mensagem, Snackbar.LENGTH_LONG).show();
                    }
                }
            });
        });
    }

    private void navegarParaPosLogin() {
        Class<?> destino;
        try {
            destino = Class.forName("com.example.assinafamilia.view.main.MainContainerActivity");
        } catch (ClassNotFoundException e) {
            destino = DashboardActivity.class;
        }
        Intent intent = new Intent(this, destino);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
