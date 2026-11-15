package com.reader.oghma.data.repository;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;

import com.reader.oghma.data.dao.BookmarkDao;
import com.reader.oghma.data.db.OghmaDatabase;
import com.reader.oghma.data.entity.Bookmark;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BookmarkRepository {

    private final BookmarkDao bookmarkDao;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());


    public BookmarkRepository(
            Application application
    ) {

        OghmaDatabase database =
                OghmaDatabase.getInstance(
                        application
                );

        bookmarkDao =
                database.bookmarkDao();
    }


    public void insertBookmark(
            Bookmark bookmark
    ) {

        executor.execute(() ->
                bookmarkDao.insert(
                        bookmark
                )
        );
    }


    /** Live list of every highlight and note, newest first. */
    public LiveData<List<Bookmark>> observeAll() {
        return bookmarkDao.observeAll();
    }

    /**
     * Saves a highlight and reports the real outcome on the main thread, so the screen
     * only says "saved" when the row is actually in the database.
     */
    public void insertBookmark(
            Bookmark bookmark,
            SaveCallback callback
    ) {
        executor.execute(() -> {
            try {
                bookmarkDao.insert(bookmark);
                if (callback != null) {
                    mainHandler.post(callback::onSaved);
                }
            } catch (Exception e) {
                android.util.Log.e("OghmaHighlight", "Bookmark insert failed", e);
                if (callback != null) {
                    mainHandler.post(() -> callback.onError(e));
                }
            }
        });
    }

    public interface SaveCallback {
        void onSaved();

        void onError(Exception error);
    }

    public void deleteBookmark(
            Bookmark bookmark
    ) {

        executor.execute(() ->
                bookmarkDao.delete(
                        bookmark
                )
        );
    }


    public void updateNoteAndColor(
            int bookmarkId,
            String note,
            int color
    ) {

        executor.execute(() ->
                bookmarkDao.updateNoteAndColor(
                        bookmarkId,
                        note,
                        color
                )
        );
    }


    public void deleteBookmarkById(
            int bookmarkId
    ) {

        executor.execute(() ->
                bookmarkDao.deleteById(
                        bookmarkId
                )
        );
    }


    public void getBookmarksForBook(
            int bookId,
            BookmarkCallback callback
    ) {

        executor.execute(() -> {

            List<Bookmark> bookmarks =
                    bookmarkDao.getForBook(
                            bookId
                    );

            callback.onBookmarksLoaded(
                    bookmarks
            );
        });
    }


    public void getAllBookmarks(
            BookmarkCallback callback
    ) {

        executor.execute(() -> {

            List<Bookmark> bookmarks =
                    bookmarkDao.getAll();

            callback.onBookmarksLoaded(
                    bookmarks
            );
        });
    }


    public interface BookmarkCallback {

        void onBookmarksLoaded(
                List<Bookmark> bookmarks
        );
    }
}