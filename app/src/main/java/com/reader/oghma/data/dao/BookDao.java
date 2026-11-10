package com.reader.oghma.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import com.reader.oghma.data.entity.Book;

import java.util.List;

@Dao
public interface BookDao {

    @Insert
    long insert(Book book);

    @Delete
    void delete(Book book);

    @Query("DELETE FROM Book WHERE bookId = :bookId")
    void deleteById(int bookId);

    @Query("SELECT * FROM Book ORDER BY addedAt DESC")
    LiveData<List<Book>> getAll();

    @Query("SELECT * FROM Book WHERE isBookmarked = 1 ORDER BY addedAt DESC")
    LiveData<List<Book>> getBookmarkedBooks();

    @Query("UPDATE Book SET isBookmarked = :isBookmarked WHERE bookId = :bookId")
    void updateBookmarkStatus(int bookId, boolean isBookmarked);

    @Query("SELECT * FROM Book WHERE bookId = :bookId")
    Book getById(int bookId);

    @Query("UPDATE Book SET tagStatus = :status, tagAttempts = :attempts WHERE bookId = :bookId")
    void updateTagStatus(int bookId, String status, int attempts);

    @Query("SELECT * FROM Book WHERE tagStatus = 'PENDING'")
    List<Book> getPendingTagBooks();
}