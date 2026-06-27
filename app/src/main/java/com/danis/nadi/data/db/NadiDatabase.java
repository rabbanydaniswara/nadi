package com.danis.nadi.data.db;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import com.danis.nadi.data.db.dao.ChatMessageDao;
import com.danis.nadi.data.db.dao.SharedFileDao;
import com.danis.nadi.data.db.entity.ChatMessageEntity;
import com.danis.nadi.data.db.entity.SharedFileEntity;

@Database(
    entities = {ChatMessageEntity.class, SharedFileEntity.class},
    version = 1,
    exportSchema = false
)
public abstract class NadiDatabase extends RoomDatabase {

    public abstract ChatMessageDao chatMessageDao();
    public abstract SharedFileDao sharedFileDao();

    private static volatile NadiDatabase INSTANCE;

    public static NadiDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (NadiDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                        context.getApplicationContext(),
                        NadiDatabase.class,
                        "nadi_database"
                    )
                        .fallbackToDestructiveMigration()
                        .build();
                }
            }
        }
        return INSTANCE;
    }
}
