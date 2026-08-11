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

    public Book getBook(int bookId) {

        return bookDao.getById(bookId);
    }

    public void insertTestBook() {

        Book book = new Book();

        book.title = "The Great Test Book";
        book.author = "Oghma";
        book.filePath = "";
        book.coverPath = "";
        book.format = "EPUB";
        book.addedAt = System.currentTimeMillis();

        executor.execute(() -> {
            bookDao.insert(book);
        });
    }
}