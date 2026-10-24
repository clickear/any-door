package io.github.lgp547.anydoorplugin.util;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

public class HttpUtil {

    public static void postAsyncByJdk(String urlStr, String reqBody, BiConsumer<String, Exception> errHandle) {
        postAsync(urlStr, reqBody).whenComplete((ignored, failure) -> {
            if (failure != null) errHandle.accept(urlStr, unwrap(failure));
        });
    }

    public static CompletableFuture<String> postAsync(String urlStr, String reqBody) {
        return postAsync(urlStr, reqBody, 0);
    }

    public static CompletableFuture<String> postAsync(String urlStr, String reqBody, int readTimeoutMillis) {
        return CompletableFuture.supplyAsync(() -> {
            try { return postByJdk(urlStr, reqBody, readTimeoutMillis); }
            catch (Exception e) { throw new java.util.concurrent.CompletionException(e); }
        });
    }

    public static String postByJdk(String urlStr, String reqBody) throws Exception {
        return postByJdk(urlStr, reqBody, 10000);
    }

    private static String postByJdk(String urlStr, String reqBody, int readTimeout) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(urlStr).openConnection();
        try {
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            con.setConnectTimeout(5000);
            con.setReadTimeout(readTimeout);
            con.setDoOutput(true);
            con.setUseCaches(false);
            try (OutputStream os = con.getOutputStream()) {
                os.write(reqBody.getBytes(StandardCharsets.UTF_8));
            }
            if (con.getResponseCode() != 200) {
                String body = "";
                InputStream error = con.getErrorStream();
                if (error != null) try (InputStream input = error) { body = new String(input.readAllBytes(), StandardCharsets.UTF_8); }
                throw new IllegalArgumentException("response {code:" + con.getResponseCode() + ", msg:" + con.getResponseMessage() + "}"
                        + (body.isBlank() ? "" : "\n" + body));
            }
            try (InputStream input = con.getInputStream()) {
                return new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
        } finally {
            con.disconnect();
        }
    }

    private static Exception unwrap(Throwable failure) {
        Throwable value = failure instanceof java.util.concurrent.CompletionException && failure.getCause() != null ? failure.getCause() : failure;
        return value instanceof Exception ? (Exception) value : new IllegalStateException(value);
    }
}
