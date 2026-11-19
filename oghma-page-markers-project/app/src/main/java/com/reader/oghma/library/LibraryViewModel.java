package com.reader.oghma.library;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.reader.oghma.data.entity.Book;
import com.reader.oghma.data.repository.BookRepository;

import java.util.List;

public class LibraryViewModel extends AndroidViewModel {

    private final BookRepository repository;

    private final LiveData<List<Book>> books;

    private final LiveData<List<Book>> bookmarkedBooks;

    public LibraryViewModel(@NonNull Application application) {

        super(application);

        repository = new BookRepository(application);

        books = repository.getAllBooks();

        bookmarkedBooks = repository.getBookmarkedBooks();
    }

    public LiveData<List<Book>> getBooks() {
        return books;
    }

    public LiveData<List<Book>> getBookmarkedBooks() {
        return bookmarkedBooks;
    }

    public BookRepository getRepository() {
        return repository;
    }
}