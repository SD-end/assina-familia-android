package com.example.assinafamilia.view.main;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.assinafamilia.R;
import com.example.assinafamilia.databinding.ActivityMainContainerBinding;
import com.example.assinafamilia.util.NetworkUtils;
import com.example.assinafamilia.util.NotificationHelper;
import com.example.assinafamilia.viewmodel.AuthViewModel;
import com.example.assinafamilia.worker.WorkScheduler;

public class MainContainerActivity extends AppCompatActivity {

    private ActivityMainContainerBinding binding;
    private AuthViewModel authViewModel;
    private ConnectivityManager.NetworkCallback callbackRede;

    private final ActivityResultLauncher<String> solicitacaoPermissaoNotificacao =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), concedida -> {
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainContainerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutRaizContainer, (view, windowInsets) -> {
            androidx.core.graphics.Insets barras =
                    windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(barras.left, barras.top, barras.right, 0);
            return windowInsets;
        });

        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        if (!authViewModel.isLogado()) {
            Intent intent = new Intent(this, com.example.assinafamilia.view.MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }

        NotificationHelper.criarCanalNotificacao(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && !NotificationHelper.possuiPermissaoNotificacao(this)) {
            solicitacaoPermissaoNotificacao.launch(Manifest.permission.POST_NOTIFICATIONS);
        }

        WorkScheduler.agendarVerificacaoVencimentos(this);
        WorkScheduler.executarVerificacaoVencimentosImediata(this);

        if (NetworkUtils.temConexaoInternet(this)) {
            WorkScheduler.enfileirarSincronizacaoQuandoOnline(this);
            if (savedInstanceState == null) {
                authViewModel.sincronizarFamiliaAtual().observe(this, recurso -> {
                });
            }
        }

        atualizarBannerConectividade(NetworkUtils.temConexaoInternet(this));

        binding.barraNavegacaoInferior.setOnItemSelectedListener(item -> {
            int idItem = item.getItemId();
            if (idItem == R.id.nav_inicio) {
                carregarFragmentoPorNome(
                        "com.example.assinafamilia.view.main.HomeFragment",
                        getString(R.string.menu_inicio));
            } else if (idItem == R.id.nav_assinaturas) {
                carregarFragmentoPorNome(
                        "com.example.assinafamilia.view.main.AssinaturasFragment",
                        getString(R.string.menu_assinaturas));
            } else if (idItem == R.id.nav_categorias) {
                carregarFragmentoPorNome(
                        "com.example.assinafamilia.view.main.CategoriasFragment",
                        getString(R.string.menu_categorias));
            } else if (idItem == R.id.nav_perfil) {
                carregarFragmentoPorNome(
                        "com.example.assinafamilia.view.main.PerfilFragment",
                        getString(R.string.menu_perfil));
            }
            return true;
        });

        if (savedInstanceState == null) {
            binding.barraNavegacaoInferior.setSelectedItemId(R.id.nav_inicio);
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (binding.barraNavegacaoInferior.getSelectedItemId() != R.id.nav_inicio) {
                    binding.barraNavegacaoInferior.setSelectedItemId(R.id.nav_inicio);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        try {
            ConnectivityManager gerenciador =
                    (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (gerenciador != null) {
                callbackRede = new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(@NonNull Network network) {
                        runOnUiThread(() -> {
                            atualizarBannerConectividade(true);
                            WorkScheduler.enfileirarSincronizacaoQuandoOnline(MainContainerActivity.this);
                        });
                    }

                    @Override
                    public void onLost(@NonNull Network network) {
                        runOnUiThread(() -> atualizarBannerConectividade(
                                NetworkUtils.temConexaoInternet(MainContainerActivity.this)));
                    }
                };
                gerenciador.registerDefaultNetworkCallback(callbackRede);
            }
        } catch (Exception e) {
            callbackRede = null;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        try {
            ConnectivityManager gerenciador =
                    (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (gerenciador != null && callbackRede != null) {
                gerenciador.unregisterNetworkCallback(callbackRede);
            }
        } catch (Exception e) {
        }
        callbackRede = null;
    }

    public void navegarParaAba(int menuItemId) {
        binding.barraNavegacaoInferior.setSelectedItemId(menuItemId);
    }

    private void atualizarBannerConectividade(boolean online) {
        binding.bannerConectividade.setVisibility(online ? View.GONE : View.VISIBLE);
    }

    private void carregarFragmentoPorNome(String nomeClasse, String tituloAba) {
        Fragment fragmento;
        try {
            fragmento = (Fragment) Class.forName(nomeClasse).getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            fragmento = FragmentAbaProvisoria.novaInstancia(tituloAba);
        }
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.containerFragmentos, fragmento)
                .commit();
    }

    public static class FragmentAbaProvisoria extends Fragment {

        public FragmentAbaProvisoria() {
        }

        public static FragmentAbaProvisoria novaInstancia(String titulo) {
            FragmentAbaProvisoria fragmento = new FragmentAbaProvisoria();
            Bundle argumentos = new Bundle();
            argumentos.putString("titulo", titulo);
            fragmento.setArguments(argumentos);
            return fragmento;
        }

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater,
                                 @Nullable ViewGroup container,
                                 @Nullable Bundle savedInstanceState) {
            TextView texto = new TextView(inflater.getContext());
            texto.setGravity(Gravity.CENTER);
            int padding = (int) (24 * inflater.getContext().getResources()
                    .getDisplayMetrics().density);
            texto.setPadding(padding, padding, padding, padding);
            texto.setTextColor(ContextCompat.getColor(
                    inflater.getContext(), R.color.cor_texto_secundario));
            texto.setTextSize(16f);
            String titulo = getArguments() != null
                    ? getArguments().getString("titulo", "")
                    : "";
            texto.setText(getString(R.string.formato_aba_em_construcao, titulo));
            return texto;
        }
    }
}
