package com.example.assinafamilia.view.auth;

import android.os.Bundle;
import android.util.Patterns;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;

import com.example.assinafamilia.R;
import com.example.assinafamilia.databinding.ActivityRecuperarSenhaBinding;
import com.example.assinafamilia.util.Resource;
import com.example.assinafamilia.viewmodel.AuthViewModel;
import com.google.android.material.snackbar.Snackbar;

public class RecuperarSenhaActivity extends AppCompatActivity {

    private ActivityRecuperarSenhaBinding binding;
    private AuthViewModel authViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityRecuperarSenhaBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        binding.btnVoltar.setOnClickListener(v -> finish());
        binding.txtVoltarLogin.setOnClickListener(v -> finish());

        binding.btnEnviarLink.setOnClickListener(v -> {
            binding.tilEmail.setError(null);

            String email = binding.edtEmail.getText() != null
                    ? binding.edtEmail.getText().toString()
                    : "";

            LiveData<Resource<Boolean>> resultado = authViewModel.recuperarSenha(email);
            resultado.observe(this, recurso -> {
                if (recurso == null) {
                    return;
                }

                if (recurso.getStatus() == Resource.Status.CARREGANDO) {
                    binding.pbCarregando.setVisibility(View.VISIBLE);
                    binding.btnEnviarLink.setEnabled(false);
                } else if (recurso.getStatus() == Resource.Status.SUCESSO) {
                    binding.pbCarregando.setVisibility(View.GONE);
                    binding.btnEnviarLink.setEnabled(true);
                    binding.edtEmail.setText("");
                    Snackbar.make(binding.getRoot(), R.string.sucesso_link_recuperacao, Snackbar.LENGTH_LONG).show();
                } else if (recurso.getStatus() == Resource.Status.ERRO) {
                    binding.pbCarregando.setVisibility(View.GONE);
                    binding.btnEnviarLink.setEnabled(true);

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
    }
}
