package com.example.assinafamilia.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.assinafamilia.data.local.Converters;
import com.example.assinafamilia.data.local.dao.CategoriaDao;
import com.example.assinafamilia.data.local.dao.CobrancaDao;
import com.example.assinafamilia.data.local.dao.CotacaoDao;
import com.example.assinafamilia.data.local.dao.UsuarioDao;
import com.example.assinafamilia.model.Assinatura;
import com.example.assinafamilia.model.Categoria;
import com.example.assinafamilia.model.Cobranca;
import com.example.assinafamilia.model.CotacaoCache;
import com.example.assinafamilia.model.Usuario;
import com.example.assinafamilia.util.DomainConstants;
import com.example.assinafamilia.util.SessionManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(
        entities = {
                Assinatura.class,
                Usuario.class,
                Categoria.class,
                Cobranca.class,
                CotacaoCache.class
        },
        version = 2,
        exportSchema = false
)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {

    private static final String NOME_BANCO = "banco_assina_familia";
    private static final int QUANTIDADE_THREADS = 4;
    public static final int ID_USUARIO_PADRAO = 1;
    public static final int ID_CATEGORIA_PADRAO = 1;
    public static final String FAMILIA_ID_PADRAO = "familia_local_padrao";
    private static final String EMAIL_USUARIO_PADRAO = "admin@assinafamilia.com";
    private static final String SENHA_USUARIO_PADRAO = "admin123";

    public static final ExecutorService databaseWriteExecutor =
            Executors.newFixedThreadPool(QUANTIDADE_THREADS);

    private static volatile AppDatabase INSTANCE;
    public abstract AssinaturaDao assinaturaDao();
    public abstract UsuarioDao usuarioDao();
    public abstract CategoriaDao categoriaDao();
    public abstract CobrancaDao cobrancaDao();
    public abstract CotacaoDao cotacaoDao();

    public static AppDatabase getInstance(@NonNull Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, NOME_BANCO)
                            .fallbackToDestructiveMigration()
                            .addCallback(callbackSemeadura)
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    private static final RoomDatabase.Callback callbackSemeadura = new RoomDatabase.Callback() {
        @Override
        public void onCreate(@NonNull SupportSQLiteDatabase db) {
            super.onCreate(db);
            databaseWriteExecutor.execute(() -> {
                AppDatabase banco = INSTANCE;
                if (banco == null) {
                    return;
                }
                banco.runInTransaction(() -> {
                    semearUsuarioPadrao(banco.usuarioDao());
                    semearCategoriasPadrao(banco.categoriaDao());
                });
            });
        }
    };

    private static void semearUsuarioPadrao(@NonNull UsuarioDao usuarioDao) {
        Usuario gestorPadrao = new Usuario(
                null,
                FAMILIA_ID_PADRAO,
                "Gestor Padrão",
                EMAIL_USUARIO_PADRAO,
                SessionManager.gerarHashSenha(SENHA_USUARIO_PADRAO),
                DomainConstants.PERFIL_GESTOR,
                true,
                false
        );
        gestorPadrao.setId(ID_USUARIO_PADRAO);
        usuarioDao.inserir(gestorPadrao);
    }

    private static void semearCategoriasPadrao(@NonNull CategoriaDao categoriaDao) {
        Categoria streaming = new Categoria(null, FAMILIA_ID_PADRAO,
                "Streaming", "Vídeo, música e entretenimento");
        streaming.setId(ID_CATEGORIA_PADRAO);
        categoriaDao.inserir(streaming);

        categoriaDao.inserir(new Categoria(null, FAMILIA_ID_PADRAO,
                "Internet & Casa", "Internet, telefonia e serviços residenciais"));
        categoriaDao.inserir(new Categoria(null, FAMILIA_ID_PADRAO,
                "Educação", "Cursos, plataformas de ensino e idiomas"));
        categoriaDao.inserir(new Categoria(null, FAMILIA_ID_PADRAO,
                "Software & Nuvem", "Aplicativos, licenças e armazenamento em nuvem"));
    }
}
