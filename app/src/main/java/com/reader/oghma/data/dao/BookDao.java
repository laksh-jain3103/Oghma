package com.reader.oghma.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.reader.oghma.data.entity.Book;

import java.util.List;

@Dao
public interface BookDao {

    @Insert
    void insert(Book book);

    @Query("SELECT * FROM Book ORDER BY addedAt DESC")
    LiveData<List<Book>> getAll();

    @Query("SELECT * FROM Book WHERE bookId = :bookId")
    Book getById(int bookId);
}