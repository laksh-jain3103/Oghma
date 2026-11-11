package com.reader.oghma.api.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class GutendexResponse {
    @SerializedName("count")
    public int count;

    @SerializedName("next")
    public String next;

    @SerializedName("previous")
    public String previous;

    @SerializedName("results")
    public List<GutendexBook> results;
}
