package com.reader.oghma.data.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.reader.oghma.data.dao.BookDao;
import com.reader.oghma.data.dao.BookTagDao;
import com.reader.oghma.data.dao.BookmarkDao;
import com.reader.oghma.data.dao.ProgressDao;
import com.reader.oghma.data.dao.TagDao;

import com.reader.oghma.data.entity.Book;
import com.reader.oghma.data.entity.BookTag;
import com.reader.oghma.data.entity.Bookmark;
import com.reader.oghma.data.entity.Progress;
import com.reader.oghma.data.entity.Tag;


@Database(
        entities = {
                Book.class,
                Progress.class,
                Bookmark.class,
                Tag.class,
                BookTag.class
        },
        version = 4,
        exportSchema = false
)
public abstract class OghmaDatabase
        extends RoomDatabase {

    public abstract BookDao bookDao();

    public abstract ProgressDao progressDao();

    public abstract BookmarkDao bookmarkDao();

    public abstract TagDao tagDao();

    public abstract BookTagDao bookTagDao();


    private static volatile OghmaDatabase INSTANCE;


    /*
     * Database migration:
     *
     * Version 1 → Version 2
     *
     * Adds:
     * - Bookmark
     * - Tag
     * - BookTag
     *
     * Existing books and progress are preserved.
     */

    private static final Migration MIGRATION_1_2 =
            new Migration(1, 2) {

                @Override
                public void migrate(
                        @NonNull SupportSQLiteDatabase database
                ) {

                    database.execSQL(
                            "CREATE TABLE IF NOT EXISTS `Bookmark` (" +
                                    "`bookmarkId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                    "`bookId` INTEGER NOT NULL, " +
                                    "`selectedText` TEXT, " +
                                    "`locatorJson` TEXT, " +
                                    "`chapterTitle` TEXT, " +
                                    "`createdAt` INTEGER NOT NULL" +
                                    ")"
                    );


                    database.execSQL(
                            "CREATE TABLE IF NOT EXISTS `Tag` (" +
                                    "`tagId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                    "`name` TEXT" +
                                    ")"
                    );


                    database.execSQL(
                            "CREATE TABLE IF NOT EXISTS `BookTag` (" +
                                    "`bookId` INTEGER NOT NULL, " +
                                    "`tagId` INTEGER NOT NULL, " +
                                    "PRIMARY KEY(`bookId`, `tagId`)" +
                                    ")"
                    );
                }
            };


    /*
     * Version 2 → Version 3
     *
     * Bookmark gets a highlight colour and an optional note.
     * Existing bookmarks are kept (colour 0 = default yellow, no note).
     */

    private static final Migration MIGRATION_2_3 =
            new Migration(2, 3) {

                @Override
                public void migrate(
                        @NonNull SupportSQLiteDatabase database
                ) {

                    database.execSQL(
                            "ALTER TABLE `Bookmark` " +
                                    "ADD COLUMN `color` INTEGER NOT NULL DEFAULT 0"
                    );

                    database.execSQL(
                            "ALTER TABLE `Bookmark` " +
                                    "ADD COLUMN `note` TEXT"
                    );
                }
            };


    /*
     * Version 3 → Version 4
     *
     * Book gets an isBookmarked column for book-level bookmarks.
     * Existing books keep isBookmarked = 0 (false).
     */

    private static final Migration MIGRATION_3_4 =
            new Migration(3, 4) {

                @Override
                public void migrate(
                        @NonNull SupportSQLiteDatabase database
                ) {

                    database.execSQL(
                            "ALTER TABLE `Book` " +
                                    "ADD COLUMN `isBookmarked` INTEGER NOT NULL DEFAULT 0"
                    );
                }
            };


    public static OghmaDatabase getInstance(
            Context context
    ) {

        if (INSTANCE == null) {

            synchronized (OghmaDatabase.class) {

                if (INSTANCE == null) {

                    INSTANCE =
                            Room.databaseBuilder(
                                            context.getApplicationContext(),
                                            OghmaDatabase.class,
                                            "oghma_database"
                                    )
                                    .addMigrations(
                                            MIGRATION_1_2,
                                            MIGRATION_2_3,
                                            MIGRATION_3_4
                                    )
                                    .fallbackToDestructiveMigration()
                                    .build();
                }
            }
        }


        return INSTANCE;
    }
}