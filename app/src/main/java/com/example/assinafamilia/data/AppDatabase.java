package com.example.assinafamilia.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.assinafamilia.data.local.Converters;
import com.example.assinafamilia.model.Assinatura;
import com.example.assinafamilia.model.Categoria;
import com.example.assinafamilia.model.Cobranca;
import com.example.assinafamilia.model.CotacaoCache;
import com.example.assinafamilia.model.Usuario;
import com.example.assinafamilia.util.DomainConstants;

@Database(
        entities = {
                Usuario.class,
                Categoria.class,
                Assinatura.class,
                Cobranca.class,
                CotacaoCache.class
        },
        version = 2,
        exportSchema = false
)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {

    public static final int ID_USUARIO_PADRAO = 1;
    public static final int ID_CATEGORIA_PADRAO = 1;

    public abstract AssinaturaDao assinaturaDao();

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "banco_assina_familia")
                            .allowMainThreadQueries()
                            .fallbackToDestructiveMigration()
                            .addCallback(callbackRegistrosPadrao)
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    private static final RoomDatabase.Callback callbackRegistrosPadrao = new RoomDatabase.Callback() {
        @Override
        public void onOpen(@NonNull SupportSQLiteDatabase db) {
            super.onOpen(db);
            db.execSQL(
                    "INSERT OR IGNORE INTO usuarios (id, nome, email, perfil, ativo, sincronizado) "
                            + "VALUES (?, ?, ?, ?, 1, 0)",
                    new Object[]{ID_USUARIO_PADRAO, "Gestor Padrão", "admin.com",
                            DomainConstants.PERFIL_GESTOR});
            db.execSQL(
                    "INSERT OR IGNORE INTO categorias (id, nome, descricao, ativa, sincronizado) "
                            + "VALUES (?, ?, ?, 1, 0)",
                    new Object[]{ID_CATEGORIA_PADRAO, "Geral", "Categoria padrão"});
        }
    };
}
