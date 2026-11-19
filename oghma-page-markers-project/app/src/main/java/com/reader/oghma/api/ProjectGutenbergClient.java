package com.reader.oghma.api;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import java.util.concurrent.TimeUnit;
import retrofit2.Retrofit;

public final class ProjectGutenbergClient {

    private static final String BASE_URL = "https://www.gutenberg.org/";

    // Project Gutenberg requires OPDS clients to identify the application and
    // provide a contact address. The repository URL is Oghma's contact page.
    private static final String USER_AGENT =
            "Oghma/1.0 (+https://github.com/laksh-jain3103/Oghma)";

    private static final OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(75, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(chain -> {
                Request request = chain.request().newBuilder()
                        .header("User-Agent", USER_AGENT)
                        .build();
                return chain.proceed(request);
            })
            .build();

    private static final Retrofit retrofit = new Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .build();

    private static final ProjectGutenbergApi api =
            retrofit.create(ProjectGutenbergApi.class);

    private ProjectGutenbergClient() {
    }

    public static ProjectGutenbergApi getApi() {
        return api;
    }
}
