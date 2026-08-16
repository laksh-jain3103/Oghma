package com.reader.oghma.data.repository;

import android.app.Application;

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


    public void deleteBookmark(
            Bookmark bookmark
    ) {

        executor.execute(() ->
                bookmarkDao.delete(
                        bookmark
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