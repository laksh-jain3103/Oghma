package com.reader.oghma.api.model;

import com.google.gson.annotations.SerializedName;

public class GoogleBookItem {

    @SerializedName("id")
    public String id;

    @SerializedName("volumeInfo")
    public GoogleBookInfo volumeInfo;

    @SerializedName("accessInfo")
    public AccessInfo accessInfo;

    public static class AccessInfo {

        @SerializedName("country")
        public String country;

        @SerializedName("viewability")
        public String viewability;

        @SerializedName("accessViewStatus")
        public String accessViewStatus;

        @SerializedName("embeddable")
        public boolean embeddable;

        @SerializedName("publicDomain")
        public boolean publicDomain;

        @SerializedName("textToSpeechPermission")
        public String textToSpeechPermission;

        @SerializedName("epub")
        public DownloadInfo epub;

        @SerializedName("pdf")
        public DownloadInfo pdf;

        @SerializedName("webReaderLink")
        public String webReaderLink;
    }

    public static class DownloadInfo {

        @SerializedName("isAvailable")
        public boolean isAvailable;

        @SerializedName("acsTokenLink")
        public String acsTokenLink;

        @SerializedName("downloadLink")
        public String downloadLink;
    }
}
