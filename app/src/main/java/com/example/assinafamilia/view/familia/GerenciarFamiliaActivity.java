package com.example.assinafamilia.view.familia;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.assinafamilia.R;
import com.example.assinafamilia.databinding.ActivityGerenciarFamiliaBinding;
import com.example.assinafamilia.model.Usuario;
import com.example.assinafamilia.util.Resource;
import com.example.assinafamilia.view.adapter.MoradorFamiliaAdapter;
import com.example.assinafamilia.viewmodel.FamiliaViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

public class GerenciarFamiliaActivity extends AppCompatActivity {

    private ActivityGerenciarFamiliaBinding binding;
    private FamiliaViewModel familiaViewModel;
    private MoradorFamiliaAdapter moradorAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityGerenciarFamiliaBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutRaizGerenciarFamilia,
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

        binding.btnVoltarFamilia.setOnClickListener(v -> finish());
        binding.fabNovoMorador.setOnClickListener(v -> abrirNovoMorador());

        moradorAdapter = new MoradorFamiliaAdapter(this::confirmarAlteracaoStatusMorador);
        binding.rvMoradoresFamilia.setLayoutManager(new LinearLayoutManager(this));
        binding.rvMoradoresFamilia.setAdapter(moradorAdapter);

        observarMoradores();
    }

    private void observarMoradores() {
        familiaViewModel.listarMoradoresDaFamilia().observe(this, moradores -> {
            int ativos = 0;
            if (moradores != null) {
                for (Usuario usuario : moradores) {
                    if (usuario != null && usuario.isAtivo()) {
                        ativos++;
                    }
                }
            }

            binding.txtContadorMembrosAtivos.setText(ativos == 1
                    ? getString(R.string.formato_membro_ativo_singular)
                    : getString(R.string.formato_membros_ativos, ativos));

            if (moradores == null || moradores.isEmpty()) {
                binding.rvMoradoresFamilia.setVisibility(View.GONE);
                binding.incluirEstadoVazioFamilia.getRoot().setVisibility(View.VISIBLE);
            } else {
                binding.rvMoradoresFamilia.setVisibility(View.VISIBLE);
                binding.incluirEstadoVazioFamilia.getRoot().setVisibility(View.GONE);
                moradorAdapter.atualizarMoradores(
                        moradores,
                        familiaViewModel.obterUsuarioId(),
                        familiaViewModel.isGestor());
            }
        });
    }

    private void abrirNovoMorador() {
        try {
            Class<?> destino = Class.forName(
                    "com.example.assinafamilia.view.familia.NovoMoradorActivity");
            startActivity(new Intent(this, destino));
        } catch (ClassNotFoundException excecao) {
            Snackbar.make(binding.layoutRaizGerenciarFamilia, R.string.aviso_tela_em_breve,
                    Snackbar.LENGTH_SHORT).show();
        }
    }

    private void confirmarAlteracaoStatusMorador(@NonNull Usuario morador,
                                                 boolean novoStatusAtivo) {
        String nome = morador.getNome() != null ? morador.getNome() : "";
        new MaterialAlertDialogBuilder(this)
                .setTitle(novoStatusAtivo
                        ? R.string.titulo_reativar_morador
                        : R.string.titulo_inativar_morador)
                .setMessage(getString(novoStatusAtivo
                        ? R.string.formato_msg_reativar_morador
                        : R.string.formato_msg_inativar_morador, nome))
                .setNegativeButton(R.string.acao_cancelar, null)
                .setPositiveButton(novoStatusAtivo
                                ? R.string.acao_ativar
                                : R.string.acao_inativar,
                        (dialog, which) -> executarAlteracaoStatusMorador(
                                morador, novoStatusAtivo))
                .show();
    }

    private void executarAlteracaoStatusMorador(@NonNull Usuario morador,
                                                boolean novoStatusAtivo) {
        familiaViewModel.alterarStatusMorador(morador, novoStatusAtivo)
                .observe(this, recurso -> {
                    if (recurso == null) {
                        return;
                    }

                    if (recurso.getStatus() == Resource.Status.CARREGANDO) {
                        binding.pbCarregandoFamilia.setVisibility(View.VISIBLE);
                    } else if (recurso.getStatus() == Resource.Status.SUCESSO) {
                        binding.pbCarregandoFamilia.setVisibility(View.GONE);
                        String mensagem = recurso.isFallbackOffline()
                                && recurso.getMensagem() != null
                                ? recurso.getMensagem()
                                : getString(novoStatusAtivo
                                ? R.string.sucesso_morador_reativado
                                : R.string.sucesso_morador_inativado);
                        Snackbar.make(binding.layoutRaizGerenciarFamilia, mensagem,
                                Snackbar.LENGTH_LONG).show();
                    } else {
                        binding.pbCarregandoFamilia.setVisibility(View.GONE);
                        String mensagem = recurso.getMensagem() != null
                                ? recurso.getMensagem()
                                : getString(R.string.erro_operacao_categoria);
                        Snackbar.make(binding.layoutRaizGerenciarFamilia, mensagem,
                                Snackbar.LENGTH_LONG).show();
                    }
                });
    }
}
