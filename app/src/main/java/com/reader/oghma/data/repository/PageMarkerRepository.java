package com.reader.oghma.data.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.reader.oghma.data.dao.PageMarkerDao;
import com.reader.oghma.data.db.OghmaDatabase;
import com.reader.oghma.data.entity.PageMarker;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PageMarkerRepository {

    private final PageMarkerDao dao;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    public PageMarkerRepository(Application application) {
        dao = OghmaDatabase.getInstance(application).pageMarkerDao();
    }

    public LiveData<List<PageMarker>> observeAll() {
        return dao.observeAll();
    }

    public LiveData<List<PageMarker>> observeForBook(int bookId) {
        return dao.observeForBook(bookId);
    }

    public void add(PageMarker marker) {
        executor.execute(() -> {
            try {
                dao.insert(marker);
            } catch (Exception e) {
                android.util.Log.e("OghmaMarker", "Could not save page marker", e);
            }
        });
    }

    public void remove(int markerId) {
        executor.execute(() -> {
            try {
                dao.delete(markerId);
            } catch (Exception e) {
                android.util.Log.e("OghmaMarker", "Could not remove page marker", e);
            }
        });
    }
}
