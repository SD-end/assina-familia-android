package com.example.assinafamilia.view.main;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.assinafamilia.R;
import com.example.assinafamilia.databinding.FragmentPerfilBinding;
import com.example.assinafamilia.model.Usuario;
import com.example.assinafamilia.util.Resource;
import com.example.assinafamilia.view.MainActivity;
import com.example.assinafamilia.viewmodel.FamiliaViewModel;
import com.example.assinafamilia.worker.WorkScheduler;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

public class PerfilFragment extends Fragment {

    private FragmentPerfilBinding binding;
    private FamiliaViewModel familiaViewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentPerfilBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        familiaViewModel = new ViewModelProvider(this).get(FamiliaViewModel.class);

        atualizarDadosCabecalho();
        configurarAcoes();
        observarMoradores();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (binding != null) {
            atualizarDadosCabecalho();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void atualizarDadosCabecalho() {
        if (binding == null) {
            return;
        }

        binding.txtIniciaisPerfil.setText(familiaViewModel.obterIniciaisUsuario());

        String nome = familiaViewModel.obterNomeUsuario();
        binding.txtNomeUsuarioPerfil.setText(nome != null ? nome : "");

        String email = familiaViewModel.obterEmailUsuario();
        binding.txtEmailUsuarioPerfil.setText(email != null ? email : "");

        boolean gestor = familiaViewModel.isGestor();
        binding.txtSeloPerfilUsuario.setText(
                gestor ? R.string.selo_gestor : R.string.selo_membro);
        binding.txtSeloPerfilUsuario.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.cor_verde_escuro));
        ViewCompat.setBackgroundTintList(binding.txtSeloPerfilUsuario,
                ColorStateList.valueOf(
                        ContextCompat.getColor(requireContext(), R.color.cor_verde_claro)));

        binding.layoutOpcaoGerenciarFamilia.setVisibility(gestor ? View.VISIBLE : View.GONE);
        binding.divisorGerenciarFamilia.setVisibility(gestor ? View.VISIBLE : View.GONE);
    }

    private void observarMoradores() {
        familiaViewModel.listarMoradoresDaFamilia().observe(getViewLifecycleOwner(),
                moradores -> {
                    if (binding == null || moradores == null) {
                        return;
                    }

                    int usuarioId = familiaViewModel.obterUsuarioId();
                    for (Usuario usuario : moradores) {
                        if (usuario != null && usuario.getId() == usuarioId) {
                            binding.txtNomeUsuarioPerfil.setText(
                                    usuario.getNome() != null ? usuario.getNome() : "");
                            binding.txtEmailUsuarioPerfil.setText(
                                    usuario.getEmail() != null ? usuario.getEmail() : "");
                            binding.txtIniciaisPerfil.setText(
                                    familiaViewModel.obterIniciaisUsuario());
                            return;
                        }
                    }
                });
    }

    private void configurarAcoes() {
        binding.btnNotificacoesPerfil.setOnClickListener(v -> {
            WorkScheduler.executarVerificacaoVencimentosImediata(requireContext());
            Snackbar.make(binding.getRoot(), R.string.aviso_verificacao_vencimentos,
                    Snackbar.LENGTH_SHORT).show();
        });

        binding.layoutOpcaoEditarDados.setOnClickListener(
                v -> abrirAtividadePorNome(
                        "com.example.assinafamilia.view.familia.EditarPerfilActivity"));
        binding.cardAtalhoEditarAvatar.setOnClickListener(
                v -> abrirAtividadePorNome(
                        "com.example.assinafamilia.view.familia.EditarPerfilActivity"));
        binding.layoutOpcaoGerenciarFamilia.setOnClickListener(
                v -> abrirAtividadePorNome(
                        "com.example.assinafamilia.view.familia.GerenciarFamiliaActivity"));

        binding.layoutOpcaoSincronizarNuvem.setOnClickListener(
                v -> executarSincronizacaoManual());

        binding.btnSairContaPerfil.setOnClickListener(v -> confirmarSaidaConta());
    }

    private void abrirAtividadePorNome(@NonNull String nomeClasse) {
        try {
            Class<?> destino = Class.forName(nomeClasse);
            startActivity(new Intent(requireContext(), destino));
        } catch (ClassNotFoundException excecao) {
            if (binding != null) {
                Snackbar.make(binding.getRoot(), R.string.aviso_tela_em_breve,
                        Snackbar.LENGTH_SHORT).show();
            }
        }
    }

    private void executarSincronizacaoManual() {
        if (binding == null) {
            return;
        }

        familiaViewModel.sincronizarAgora().observe(getViewLifecycleOwner(), recurso -> {
            if (binding == null || recurso == null) {
                return;
            }

            if (recurso.getStatus() == Resource.Status.CARREGANDO) {
                binding.pbCarregandoPerfil.setVisibility(View.VISIBLE);
                binding.layoutOpcaoSincronizarNuvem.setEnabled(false);
            } else if (recurso.getStatus() == Resource.Status.SUCESSO) {
                binding.pbCarregandoPerfil.setVisibility(View.GONE);
                binding.layoutOpcaoSincronizarNuvem.setEnabled(true);
                atualizarDadosCabecalho();
                Snackbar.make(binding.getRoot(),
                        R.string.sucesso_sincronizacao_concluida, Snackbar.LENGTH_LONG).show();
            } else {
                binding.pbCarregandoPerfil.setVisibility(View.GONE);
                binding.layoutOpcaoSincronizarNuvem.setEnabled(true);
                String mensagem = recurso.getMensagem() != null
                        ? recurso.getMensagem()
                        : getString(R.string.erro_operacao_categoria);
                Snackbar.make(binding.getRoot(), mensagem, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void confirmarSaidaConta() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.titulo_sair_conta)
                .setMessage(R.string.msg_confirmar_sair_conta)
                .setNegativeButton(R.string.acao_cancelar, null)
                .setPositiveButton(R.string.sair_da_conta, (dialog, which) -> {
                    familiaViewModel.sair();
                    Intent intent = new Intent(requireContext(), MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    requireActivity().finish();
                })
                .show();
    }
}
