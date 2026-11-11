package com.reader.oghma.search;

import com.reader.oghma.api.model.GoogleBookItem;
import com.reader.oghma.api.model.GutendexBook;
import com.reader.oghma.api.model.ProjectGutenbergBook;

public class UnifiedBookItem {
    public enum Source {
        GUTENBERG,
        GOOGLE_BOOKS
    }

    public Source source;
    public String title;
    public String author;
    public String publishedDate;
    public String coverUrl;
    public boolean isDownloadable;
    public String downloadUrl;
    public String previewUrl;
    public GoogleBookItem googleBookItem;
    public GutendexBook gutendexBook;
    public ProjectGutenbergBook projectGutenbergBook;
    public int gutenbergId;
}
