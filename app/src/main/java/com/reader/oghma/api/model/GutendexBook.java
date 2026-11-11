package com.reader.oghma.api.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

public class GutendexBook {
    @SerializedName("id")
    public int id;

    @SerializedName("title")
    public String title;

    @SerializedName("authors")
    public List<Author> authors;

    @SerializedName("languages")
    public List<String> languages;

    @SerializedName("copyright")
    public Boolean copyright;

    @SerializedName("formats")
    public Map<String, String> formats;

    public static class Author {
        @SerializedName("name")
        public String name;

        @SerializedName("birth_year")
        public Integer birthYear;

        @SerializedName("death_year")
        public Integer deathYear;
    }

    public String getEpubDownloadUrl() {
        if (formats == null) return null;
        for (String key : formats.keySet()) {
            if (key != null && key.startsWith("application/epub+zip")) {
                return formats.get(key);
            }
        }
        return null;
    }

    public String getCoverUrl() {
        if (formats == null) return null;
        for (String key : formats.keySet()) {
            if (key != null && key.startsWith("image/jpeg")) {
                return formats.get(key);
            }
        }
        return null;
    }
}
