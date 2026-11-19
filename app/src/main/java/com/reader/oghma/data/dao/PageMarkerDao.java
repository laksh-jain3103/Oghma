package com.reader.oghma.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.reader.oghma.data.entity.PageMarker;

import java.util.List;

@Dao
public interface PageMarkerDao {

    @Insert
    long insert(PageMarker marker);

    @Query("DELETE FROM PageMarker WHERE markerId = :markerId")
    void delete(int markerId);

    /** Every marker in the library, newest first. */
    @Query("SELECT * FROM PageMarker ORDER BY createdAt DESC")
    LiveData<List<PageMarker>> observeAll();

    /** Markers of one book, in reading order. */
    @Query("SELECT * FROM PageMarker WHERE bookId = :bookId ORDER BY percentage ASC")
    LiveData<List<PageMarker>> observeForBook(int bookId);
}
