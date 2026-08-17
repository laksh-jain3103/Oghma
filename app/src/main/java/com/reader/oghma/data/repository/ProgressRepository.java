package com.reader.oghma.data.repository;

import android.app.Application;

import com.reader.oghma.data.dao.ProgressDao;
import com.reader.oghma.data.db.OghmaDatabase;
import com.reader.oghma.data.entity.Progress;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProgressRepository {

    private final ProgressDao progressDao;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();


    public ProgressRepository(
            Application application
    ) {

        OghmaDatabase database =
                OghmaDatabase.getInstance(
                        application
                );

        progressDao =
                database.progressDao();
    }


    // ---------------------------------------------------------
    // Save progress
    // ---------------------------------------------------------

    public void saveProgress(
            Progress progress
    ) {

        executor.execute(() ->
                progressDao.insertOrUpdate(
                        progress
                )
        );
    }


    // ---------------------------------------------------------
    // Get progress
    // ---------------------------------------------------------

    public void getProgress(
            int bookId,
            ProgressCallback callback
    ) {

        executor.execute(() -> {

            Progress progress =
                    progressDao.getByBookId(
                            bookId
                    );

            callback.onProgressLoaded(
                    progress
            );
        });
    }


    // ---------------------------------------------------------
    // Callback
    // ---------------------------------------------------------

    public interface ProgressCallback {

        void onProgressLoaded(
                Progress progress
        );
    }
}