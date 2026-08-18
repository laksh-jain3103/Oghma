package com.reader.oghma.api.model;

import com.google.gson.annotations.SerializedName;

public class GoogleBookItem {

    @SerializedName("id")
    public String id;

    @SerializedName("volumeInfo")
    public GoogleBookInfo volumeInfo;
}