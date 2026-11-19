package com.reader.oghma.api.metadata;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class OpenLibraryDoc {

    @SerializedName("key")
    public String key;

    @SerializedName("title")
    public String title;

    @SerializedName("author_name")
    public List<String> authorName;

    @SerializedName("subject")
    public List<String> subject;

    @SerializedName("isbn")
    public List<String> isbn;
}
