package com.reader.oghma.data.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.reader.oghma.data.dao.BookDao;
import com.reader.oghma.data.db.OghmaDatabase;
import com.reader.oghma.data.entity.Book;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BookRepository {

    private final BookDao bookDao;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();


    public BookRepository(
            Application application
    ) {

        OghmaDatabase database =
                OghmaDatabase.getInstance(
                        application
                );

        bookDao =
                database.bookDao();
    }


    // ---------------------------------------------------------
    // Get all books
    //
    // IMPORTANT:
    // BookDao returns LiveData<List<Book>>.
    // Do not convert this to List<Book>.
    // ---------------------------------------------------------

    public LiveData<java.util.List<Book>> getAllBooks() {

        return bookDao.getAll();
    }


    // ---------------------------------------------------------
    // Insert book
    // ---------------------------------------------------------

    public void insertBook(
            Book book
    ) {

        executor.execute(() ->
                bookDao.insert(
                        book
                )
        );
    }


    // ---------------------------------------------------------
    // Get one book
    // ---------------------------------------------------------

    public void getBook(
            int bookId,
            SingleBookCallback callback
    ) {

        executor.execute(() -> {

            Book book =
                    bookDao.getById(
                            bookId
                    );

            callback.onBookLoaded(
                    book
            );
        });
    }


    // ---------------------------------------------------------
    // Delete book
    // ---------------------------------------------------------

    public void deleteBook(
            Book book
    ) {

        executor.execute(() ->
                bookDao.delete(
                        book
                )
        );
    }


    // ---------------------------------------------------------
    // Callback for a single book
    // ---------------------------------------------------------

    public interface SingleBookCallback {

        void onBookLoaded(
                Book book
        );
    }
}