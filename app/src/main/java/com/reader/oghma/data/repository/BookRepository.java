package com.reader.oghma.data.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.reader.oghma.data.dao.BookDao;
import com.reader.oghma.data.db.OghmaDatabase;
import com.reader.oghma.data.entity.Book;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BookRepository {

    private final BookDao bookDao;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    public BookRepository(Application application) {

        OghmaDatabase database =
                OghmaDatabase.getInstance(application);

        bookDao = database.bookDao();
    }

    public LiveData<List<Book>> getAllBooks() {
        return bookDao.getAll();
    }

    public void insertBook(Book book) {

        executor.execute(() -> {
            bookDao.insert(book);
        });
    }

    public void deleteBook(Book book) {

        executor.execute(() -> {
            bookDao.delete(book);
        });
    }

    public void deleteBookById(int bookId) {

        executor.execute(() -> {
            bookDao.deleteById(bookId);
        });
    }

    public interface BookCallback {
        void onBookLoaded(Book book);
    }

    public void getBook(
            int bookId,
            BookCallback callback
    ) {

        executor.execute(() -> {

            Book book =
                    bookDao.getById(bookId);

            callback.onBookLoaded(book);
        });
    }
}