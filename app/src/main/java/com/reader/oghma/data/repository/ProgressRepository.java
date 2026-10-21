package com.reader.oghma.data.repository;

import android.app.Application;

import com.reader.oghma.data.dao.ProgressDao;
import com.reader.oghma.data.db.OghmaDatabase;
import com.reader.oghma.data.entity.Progress;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Saves and loads where the reader stopped in each book.
 *
 * Progress.locator holds:
 *  - EPUB: the Readium Locator JSON
 *  - PDF:  {"type":"pdf","page":N,"y":0..1}
 *
 * Progress.percentage is 0..100.
 */
public class ProgressRepository {

    private final ProgressDao progressDao;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    public ProgressRepository(Application application) {

        progressDao =
                OghmaDatabase
                        .getInstance(application)
                        .progressDao();
    }

    public void save(
            int bookId,
            String locator,
            float percentage
    ) {

        executor.execute(() -> {

            Progress progress = new Progress();

            progress.bookId = bookId;
            progress.locator = locator;
            progress.percentage = percentage;

            progressDao.insertOrUpdate(progress);
        });
    }

    public void get(
            int bookId,
            ProgressCallback callback
    ) {

        executor.execute(() ->
                callback.onProgressLoaded(
                        progressDao.getByBookId(bookId)
                )
        );
    }

    public interface ProgressCallback {

        /** [progress] is null when the book has never been opened. */
        void onProgressLoaded(Progress progress);
    }
}
