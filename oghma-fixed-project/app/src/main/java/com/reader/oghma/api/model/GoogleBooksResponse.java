package com.reader.oghma.api.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class GoogleBooksResponse {

    @SerializedName("items")
    public List<GoogleBookItem> items;

    @SerializedName("totalItems")
    public int totalItems;
}