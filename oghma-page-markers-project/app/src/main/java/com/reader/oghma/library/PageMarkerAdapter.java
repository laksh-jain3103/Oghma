package com.reader.oghma.library;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateUtils;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.reader.oghma.R;
import com.reader.oghma.data.entity.Book;
import com.reader.oghma.data.entity.PageMarker;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** The Bookmarks page: one row per marked place in a book. */
public class PageMarkerAdapter
        extends RecyclerView.Adapter<PageMarkerAdapter.MarkerViewHolder> {

    public interface Listener {

        void onOpen(PageMarker marker, Book book);

        void onDelete(PageMarker marker);
    }

    private final List<PageMarker> markers = new ArrayList<>();

    private final Map<Integer, Book> booksById = new HashMap<>();

    private Listener listener;

    private final LruCache<String, Bitmap> coverCache =
            new LruCache<String, Bitmap>(
                    (int) (Runtime.getRuntime().maxMemory() / 1024 / 32)
            ) {
                @Override
                protected int sizeOf(String key, Bitmap bitmap) {
                    return bitmap.getByteCount() / 1024;
                }
            };

    private final ExecutorService coverExecutor =
            Executors.newSingleThreadExecutor();

    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    public PageMarkerAdapter() {
        setHasStableIds(true);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setMarkers(List<PageMarker> newMarkers) {
        markers.clear();
        if (newMarkers != null) {
            markers.addAll(newMarkers);
        }
        notifyDataSetChanged();
    }

    /** The whole library, so each marker can show its book's title and cover. */
    public void setBooks(List<Book> books) {
        booksById.clear();
        if (books != null) {
            for (Book book : books) {
                if (book != null) {
                    booksById.put(book.bookId, book);
                }
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        return markers.get(position).markerId;
    }

    @NonNull
    @Override
    public MarkerViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_page_marker, parent, false);
        return new MarkerViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull MarkerViewHolder holder,
            int position
    ) {
        PageMarker marker = markers.get(position);
        Book book = booksById.get(marker.bookId);

        holder.title.setText(
                book != null && book.title != null
                        ? book.title
                        : "Unknown book"
        );

        String label =
                marker.label != null && !marker.label.trim().isEmpty()
                        ? marker.label.trim()
                        : "Saved place";

        holder.label.setText(
                label + " · " + Math.round(marker.percentage) + "%"
        );

        holder.date.setText(
                "Marked " + DateUtils.getRelativeTimeSpanString(
                        marker.createdAt,
                        System.currentTimeMillis(),
                        DateUtils.DAY_IN_MILLIS
                ).toString().toLowerCase(Locale.getDefault())
        );

        loadCover(holder.cover, book != null ? book.coverPath : null);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onOpen(marker, book);
            }
        });

        holder.delete.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDelete(marker);
            }
        });
    }

    @Override
    public int getItemCount() {
        return markers.size();
    }

    // -----------------------------------------------------------------
    // Cover (small, decoded off the main thread, cached)
    // -----------------------------------------------------------------

    private void loadCover(ImageView imageView, String coverPath) {

        imageView.setTag(coverPath);

        if (coverPath == null || coverPath.isEmpty()) {
            imageView.setImageDrawable(null);
            return;
        }

        Bitmap cached = coverCache.get(coverPath);
        if (cached != null) {
            imageView.setImageBitmap(cached);
            return;
        }

        imageView.setImageDrawable(null);

        coverExecutor.execute(() -> {
            try {
                File file = new File(coverPath);
                if (!file.exists()) {
                    return;
                }

                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);

                int sample = 1;
                while ((bounds.outWidth / (sample * 2)) >= 240
                        && (bounds.outHeight / (sample * 2)) >= 340) {
                    sample *= 2;
                }

                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = sample;
                Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);

                if (bitmap == null) {
                    return;
                }

                coverCache.put(coverPath, bitmap);

                mainHandler.post(() -> {
                    if (coverPath.equals(imageView.getTag())) {
                        imageView.setImageBitmap(bitmap);
                    }
                });

            } catch (Exception | OutOfMemoryError e) {
                android.util.Log.w("OghmaMarker", "Cover load failed", e);
            }
        });
    }

    // -----------------------------------------------------------------

    static class MarkerViewHolder extends RecyclerView.ViewHolder {

        final ImageView cover;
        final TextView title;
        final TextView label;
        final TextView date;
        final ImageButton delete;

        MarkerViewHolder(@NonNull View itemView) {
            super(itemView);
            cover = itemView.findViewById(R.id.markerCover);
            title = itemView.findViewById(R.id.markerBookTitle);
            label = itemView.findViewById(R.id.markerLabel);
            date = itemView.findViewById(R.id.markerDate);
            delete = itemView.findViewById(R.id.markerDeleteButton);
        }
    }
}
