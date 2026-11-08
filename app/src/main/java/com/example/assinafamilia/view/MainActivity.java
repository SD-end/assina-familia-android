package com.example.assinafamilia.view;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;

import com.example.assinafamilia.R;
import com.example.assinafamilia.databinding.ActivityMainBinding;
import com.example.assinafamilia.model.Usuario;
import com.example.assinafamilia.util.NetworkUtils;
import com.example.assinafamilia.util.Resource;
import com.example.assinafamilia.viewmodel.AuthViewModel;
import com.google.android.material.snackbar.Snackbar;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private AuthViewModel authViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        if (getIntent() != null && !Intent.ACTION_MAIN.equals(getIntent().getAction())) {
            authViewModel.sair();
        }

        if (authViewModel.isLogado()) {
            navegarParaPosLogin();
            return;
        }

        binding.btnEntrar.setOnClickListener(v -> {
            binding.tilEmail.setError(null);
            binding.tilSenha.setError(null);

            String email = binding.edtEmail.getText() != null
                    ? binding.edtEmail.getText().toString()
                    : "";
            String senha = binding.edtSenha.getText() != null
                    ? binding.edtSenha.getText().toString()
                    : "";

            LiveData<Resource<Usuario>> resultado = authViewModel.entrar(email, senha);
            resultado.observe(this, recurso -> {
                if (recurso == null) {
                    return;
                }

                if (recurso.getStatus() == Resource.Status.CARREGANDO) {
                    binding.pbCarregando.setVisibility(View.VISIBLE);
                    binding.btnEntrar.setEnabled(false);
                } else if (recurso.getStatus() == Resource.Status.SUCESSO) {
                    binding.pbCarregando.setVisibility(View.GONE);
                    binding.btnEntrar.setEnabled(true);
                    navegarParaPosLogin();
                } else if (recurso.getStatus() == Resource.Status.ERRO) {
                    binding.pbCarregando.setVisibility(View.GONE);
                    binding.btnEntrar.setEnabled(true);

                    String mensagem = recurso.getMensagem() != null
                            ? recurso.getMensagem()
                            : getString(R.string.estado_vazio_descricao);

                    if (email.trim().isEmpty()
                            || !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
                        binding.tilEmail.setError(mensagem);
                    } else {
                        Snackbar.make(binding.getRoot(), mensagem, Snackbar.LENGTH_LONG).show();
                    }
                }
            });
        });

        binding.txtCriarConta.setOnClickListener(v -> {
            if (!NetworkUtils.temConexaoInternet(this)) {
                Snackbar.make(binding.getRoot(), R.string.aviso_sem_internet_cadastro, Snackbar.LENGTH_LONG).show();
                return;
            }
            abrirAtividadePorNome("com.example.assinafamilia.view.auth.CadastroActivity");
        });

        binding.txtEsqueciSenha.setOnClickListener(v -> {
            if (!NetworkUtils.temConexaoInternet(this)) {
                Snackbar.make(binding.getRoot(), R.string.aviso_sem_internet_recuperacao, Snackbar.LENGTH_LONG).show();
                return;
            }
            abrirAtividadePorNome("com.example.assinafamilia.view.auth.RecuperarSenhaActivity");
        });
    }

    private void navegarParaPosLogin() {
        Class<?> destino;
        try {
            destino = Class.forName("com.example.assinafamilia.view.main.MainContainerActivity");
        } catch (ClassNotFoundException e) {
            destino = DashboardActivity.class;
        }
        startActivity(new Intent(this, destino));
        finish();
    }

    private void abrirAtividadePorNome(String nomeClasse) {
        try {
            Class<?> destino = Class.forName(nomeClasse);
            startActivity(new Intent(this, destino));
        } catch (ClassNotFoundException e) {
            Snackbar.make(binding.getRoot(), R.string.aviso_tela_em_breve, Snackbar.LENGTH_SHORT).show();
        }
    }
}
