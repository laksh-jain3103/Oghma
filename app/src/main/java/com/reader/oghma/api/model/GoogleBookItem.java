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

        @SerializedName("explicitOfflineLicenseManagement")
        public boolean explicitOfflineLicenseManagement;

        @SerializedName("downloadAccess")
        public DownloadAccess downloadAccess;
    }

    public static class DownloadAccess {
        @SerializedName("restricted")
        public boolean restricted;

        @SerializedName("deviceAllowed")
        public boolean deviceAllowed;

        @SerializedName("justAcquired")
        public boolean justAcquired;

        @SerializedName("maxDownloadDevices")
        public int maxDownloadDevices;

        @SerializedName("downloadsAcquired")
        public int downloadsAcquired;

        @SerializedName("nonce")
        public String nonce;

        @SerializedName("source")
        public String source;

        @SerializedName("reasonCode")
        public String reasonCode;

        @SerializedName("message")
        public String message;

        @SerializedName("signature")
        public String signature;
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
