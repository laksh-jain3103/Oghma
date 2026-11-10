package com.reader.oghma.api.metadata;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class OpenLibraryResponse {

    @SerializedName("numFound")
    public int numFound;

    @SerializedName("docs")
    public List<OpenLibraryDoc> docs;
}
