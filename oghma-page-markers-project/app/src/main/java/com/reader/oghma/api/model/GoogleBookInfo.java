package com.reader.oghma.api.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class GoogleBookInfo {

    @SerializedName("title")
    public String title;

    @SerializedName("authors")
    public List<String> authors;

    @SerializedName("description")
    public String description;

    @SerializedName("publisher")
    public String publisher;

    @SerializedName("publishedDate")
    public String publishedDate;

    @SerializedName("categories")
    public List<String> categories;

    @SerializedName("industryIdentifiers")
    public List<IndustryIdentifier> industryIdentifiers;

    @SerializedName("previewLink")
    public String previewLink;

    public static class IndustryIdentifier {
        @SerializedName("type")
        public String type;

        @SerializedName("identifier")
        public String identifier;
    }

    @SerializedName("imageLinks")
    public ImageLinks imageLinks;

    public static class ImageLinks {

        @SerializedName("thumbnail")
        public String thumbnail;

        @SerializedName("smallThumbnail")
        public String smallThumbnail;
    }
}
