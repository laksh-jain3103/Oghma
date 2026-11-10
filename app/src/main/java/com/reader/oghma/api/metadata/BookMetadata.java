package com.reader.oghma.api.metadata;

import java.util.ArrayList;
import java.util.List;

public class BookMetadata {
    public String title;
    public List<String> authors = new ArrayList<>();
    public String isbn;
    public List<String> rawTags = new ArrayList<>();
    public String source;

    public BookMetadata() {}

    public BookMetadata(String title, List<String> authors, String isbn, List<String> rawTags, String source) {
        this.title = title;
        if (authors != null) this.authors = authors;
        this.isbn = isbn;
        if (rawTags != null) this.rawTags = rawTags;
        this.source = source;
    }
}
