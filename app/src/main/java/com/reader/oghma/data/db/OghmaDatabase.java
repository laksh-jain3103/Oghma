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
import com.reader.oghma.data.dao.TagSuppressionDao;

import com.reader.oghma.data.entity.Book;
import com.reader.oghma.data.entity.BookTag;
import com.reader.oghma.data.entity.Bookmark;
import com.reader.oghma.data.entity.Progress;
import com.reader.oghma.data.entity.Tag;
import com.reader.oghma.data.entity.TagSuppression;


@Database(
        entities = {
                Book.class,
                Progress.class,
                Bookmark.class,
                Tag.class,
                BookTag.class,
                TagSuppression.class
        },
        version = 6,
        exportSchema = true
)
public abstract class OghmaDatabase
        extends RoomDatabase {

    public abstract BookDao bookDao();

    public abstract ProgressDao progressDao();

    public abstract BookmarkDao bookmarkDao();

    public abstract TagDao tagDao();

    public abstract BookTagDao bookTagDao();

    public abstract TagSuppressionDao tagSuppressionDao();


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


    /*
     * Version 4 → Version 5
     *
     * Adds tagStatus and tagAttempts to Book.
     * Rebuilds Tag to add normalizedName with UNIQUE index.
     * Rebuilds BookTag to add foreign keys, tagId index, and source column.
     * Preserves and dedupes all existing data.
     */
    private static final Migration MIGRATION_4_5 =
            new Migration(4, 5) {

                @Override
                public void migrate(
                        @NonNull SupportSQLiteDatabase database
                ) {

                    database.execSQL(
                            "ALTER TABLE `Book` " +
                                    "ADD COLUMN `tagStatus` TEXT NOT NULL DEFAULT 'PENDING'"
                    );

                    database.execSQL(
                            "ALTER TABLE `Book` " +
                                    "ADD COLUMN `tagAttempts` INTEGER NOT NULL DEFAULT 0"
                    );

                    database.execSQL(
                            "CREATE TABLE IF NOT EXISTS `Tag_new` (" +
                                    "`tagId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                    "`name` TEXT, " +
                                    "`normalizedName` TEXT)"
                    );

                    android.database.Cursor cursor = database.query("SELECT `tagId`, `name` FROM `Tag`");
                    java.util.Map<Integer, Integer> tagMap = new java.util.HashMap<>();
                    java.util.Map<String, Integer> normalizedToCanonical = new java.util.HashMap<>();

                    if (cursor != null) {
                        while (cursor.moveToNext()) {
                            int oldTagId = cursor.getInt(0);
                            String name = cursor.getString(1);
                            String normalized = name != null ? name.trim().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT) : "";
                            if (normalizedToCanonical.containsKey(normalized)) {
                                tagMap.put(oldTagId, normalizedToCanonical.get(normalized));
                            } else {
                                android.content.ContentValues cv = new android.content.ContentValues();
                                cv.put("name", name);
                                cv.put("normalizedName", normalized);
                                long newId = database.insert("Tag_new", android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE, cv);
                                int canonicalId = (newId != -1) ? (int) newId : oldTagId;
                                normalizedToCanonical.put(normalized, canonicalId);
                                tagMap.put(oldTagId, canonicalId);
                            }
                        }
                        cursor.close();
                    }

                    database.execSQL("DROP TABLE `Tag`");
                    database.execSQL("ALTER TABLE `Tag_new` RENAME TO `Tag`");
                    database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_Tag_normalizedName` ON `Tag` (`normalizedName`)");

                    database.execSQL(
                            "CREATE TABLE IF NOT EXISTS `BookTag_new` (" +
                                    "`bookId` INTEGER NOT NULL, " +
                                    "`tagId` INTEGER NOT NULL, " +
                                    "`source` TEXT NOT NULL DEFAULT 'AUTO', " +
                                    "PRIMARY KEY(`bookId`, `tagId`), " +
                                    "FOREIGN KEY(`bookId`) REFERENCES `Book`(`bookId`) ON DELETE CASCADE, " +
                                    "FOREIGN KEY(`tagId`) REFERENCES `Tag`(`tagId`) ON DELETE CASCADE)"
                    );

                    android.database.Cursor btCursor = database.query("SELECT `bookId`, `tagId` FROM `BookTag`");
                    if (btCursor != null) {
                        while (btCursor.moveToNext()) {
                            int bookId = btCursor.getInt(0);
                            int oldTagId = btCursor.getInt(1);
                            Integer canonicalTagId = tagMap.get(oldTagId);
                            if (canonicalTagId == null) canonicalTagId = oldTagId;
                            android.content.ContentValues cv = new android.content.ContentValues();
                            cv.put("bookId", bookId);
                            cv.put("tagId", canonicalTagId);
                            cv.put("source", "USER");
                            database.insert("BookTag_new", android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE, cv);
                        }
                        btCursor.close();
                    }

                    database.execSQL("DROP TABLE `BookTag`");
                    database.execSQL("ALTER TABLE `BookTag_new` RENAME TO `BookTag`");
                    database.execSQL("CREATE INDEX IF NOT EXISTS `index_BookTag_tagId` ON `BookTag` (`tagId`)");
                }
            };


    /*
     * Version 5 → Version 6
     *
     * Creates TagSuppression table for tracking user-removed auto tags.
     */
    private static final Migration MIGRATION_5_6 =
            new Migration(5, 6) {

                @Override
                public void migrate(
                        @NonNull SupportSQLiteDatabase database
                ) {

                    database.execSQL(
                            "CREATE TABLE IF NOT EXISTS `TagSuppression` (" +
                                    "`bookId` INTEGER NOT NULL, " +
                                    "`normalizedName` TEXT NOT NULL, " +
                                    "PRIMARY KEY(`bookId`, `normalizedName`), " +
                                    "FOREIGN KEY(`bookId`) REFERENCES `Book`(`bookId`) ON DELETE CASCADE)"
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
                                            MIGRATION_3_4,
                                            MIGRATION_4_5,
                                            MIGRATION_5_6
                                    )
                                    .build();
                }
            }
        }


        return INSTANCE;
    }
}