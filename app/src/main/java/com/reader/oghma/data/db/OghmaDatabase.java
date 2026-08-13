package com.reader.oghma.data.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.reader.oghma.data.dao.BookDao;
import com.reader.oghma.data.dao.ProgressDao;
import com.reader.oghma.data.entity.Book;
import com.reader.oghma.data.entity.Progress;

@Database(
        entities = {
                Book.class,
                Progress.class
        },
        version = 1,
        exportSchema = false
)
public abstract class OghmaDatabase extends RoomDatabase {

    public abstract BookDao bookDao();

    public abstract ProgressDao progressDao();

    private static volatile OghmaDatabase INSTANCE;

    public static OghmaDatabase getInstance(Context context) {

        INSTANCE = Room.databaseBuilder(
                context.getApplicationContext(),
                OghmaDatabase.class,
                "oghma_database"
        )
                .fallbackToDestructiveMigration()
                .build();

        if (INSTANCE == null) {

            synchronized (OghmaDatabase.class) {

                if (INSTANCE == null) {

                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            OghmaDatabase.class,
                            "oghma_database"
                    ).build();
                }
            }
        }

        return INSTANCE;
    }
}