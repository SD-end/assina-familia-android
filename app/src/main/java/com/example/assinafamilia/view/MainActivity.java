package com.example.assinafamilia.view;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.assinafamilia.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnEntrar.setOnClickListener(v -> {
            String email = binding.editEmail.getText().toString();
            String senha = binding.editSenha.getText().toString();

            if (email.equals("admin@admin.com") && senha.equals("1234")) {
                Intent intent = new Intent(MainActivity.this, DashboardActivity.class);

                startActivity(intent);
                finish();
            }else {
                Toast.makeText(this, "E-mail ou senha incorretos!", Toast.LENGTH_SHORT).show();
            }
        });

    }
}