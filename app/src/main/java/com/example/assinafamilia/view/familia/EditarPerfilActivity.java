package com.example.assinafamilia.view.familia;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.assinafamilia.R;
import com.example.assinafamilia.databinding.ActivityEditarPerfilBinding;
import com.example.assinafamilia.util.Resource;
import com.example.assinafamilia.viewmodel.FamiliaViewModel;
import com.google.android.material.snackbar.Snackbar;

public class EditarPerfilActivity extends AppCompatActivity {

    private ActivityEditarPerfilBinding binding;
    private FamiliaViewModel familiaViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityEditarPerfilBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutRaizEditarPerfil,
                (view, windowInsets) -> {
                    Insets barras = windowInsets.getInsets(
                            WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
                    view.setPadding(barras.left, barras.top, barras.right, barras.bottom);
                    return windowInsets;
                });

        familiaViewModel = new ViewModelProvider(this).get(FamiliaViewModel.class);

        String nomeAtual = familiaViewModel.obterNomeUsuario();
        binding.edtNomeCompletoPerfil.setText(nomeAtual != null ? nomeAtual : "");

        String emailAtual = familiaViewModel.obterEmailUsuario();
        binding.edtEmailPerfil.setText(emailAtual != null ? emailAtual : "");

        binding.btnVoltarEditarPerfil.setOnClickListener(v -> finish());
        binding.btnSalvarAlteracoesPerfil.setOnClickListener(v -> salvarAlteracoes());
    }

    private void salvarAlteracoes() {
        binding.tilNomeCompletoPerfil.setError(null);
        binding.tilNovaSenhaPerfil.setError(null);
        binding.tilConfirmarNovaSenhaPerfil.setError(null);

        String novoNome = binding.edtNomeCompletoPerfil.getText() != null
                ? binding.edtNomeCompletoPerfil.getText().toString().trim()
                : "";
        String novaSenha = binding.edtNovaSenhaPerfil.getText() != null
                ? binding.edtNovaSenhaPerfil.getText().toString()
                : "";
        String confirmacaoSenha = binding.edtConfirmarNovaSenhaPerfil.getText() != null
                ? binding.edtConfirmarNovaSenhaPerfil.getText().toString()
                : "";

        if (novoNome.isEmpty() || novoNome.length() > 120) {
            binding.tilNomeCompletoPerfil.setError(
                    getString(R.string.erro_nome_completo_obrigatorio));
            return;
        }

        if (!novaSenha.isEmpty() || !confirmacaoSenha.isEmpty()) {
            if (novaSenha.length() < 6) {
                binding.tilNovaSenhaPerfil.setError(
                        getString(R.string.erro_nova_senha_curta));
                return;
            }
            if (!novaSenha.equals(confirmacaoSenha)) {
                binding.tilConfirmarNovaSenhaPerfil.setError(
                        getString(R.string.erro_confirmacao_nova_senha_diferente));
                return;
            }
        }

        familiaViewModel.atualizarMeusDados(novoNome, novaSenha, confirmacaoSenha)
                .observe(this, recurso -> {
                    if (recurso == null) {
                        return;
                    }

                    if (recurso.getStatus() == Resource.Status.CARREGANDO) {
                        binding.pbCarregandoEditarPerfil.setVisibility(View.VISIBLE);
                        binding.btnSalvarAlteracoesPerfil.setEnabled(false);
                    } else if (recurso.getStatus() == Resource.Status.SUCESSO) {
                        binding.pbCarregandoEditarPerfil.setVisibility(View.GONE);
                        binding.btnSalvarAlteracoesPerfil.setEnabled(true);
                        String mensagem = recurso.isFallbackOffline()
                                && recurso.getMensagem() != null
                                ? recurso.getMensagem()
                                : getString(R.string.sucesso_dados_atualizados);
                        Toast.makeText(this, mensagem, Toast.LENGTH_LONG).show();
                        finish();
                    } else {
                        binding.pbCarregandoEditarPerfil.setVisibility(View.GONE);
                        binding.btnSalvarAlteracoesPerfil.setEnabled(true);
                        String mensagem = recurso.getMensagem() != null
                                ? recurso.getMensagem()
                                : getString(R.string.erro_operacao_categoria);
                        Snackbar.make(binding.layoutRaizEditarPerfil, mensagem,
                                Snackbar.LENGTH_LONG).show();
                    }
                });
    }
}
