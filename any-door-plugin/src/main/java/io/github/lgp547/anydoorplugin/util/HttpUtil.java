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
        CompletableFuture.runAsync(() -> {
            try {
                postByJdk(urlStr, reqBody, 0);
            } catch (Exception e) {
                errHandle.accept(urlStr, e);
            }
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
                throw new IllegalArgumentException("response {code:" + con.getResponseCode() + ", msg:" + con.getResponseMessage() + "}");
            }
            try (InputStream input = con.getInputStream()) {
                return new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
        } finally {
            con.disconnect();
        }
    }
}
