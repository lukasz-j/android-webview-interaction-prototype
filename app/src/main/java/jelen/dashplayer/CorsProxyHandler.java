package jelen.dashplayer;

import android.content.Context;
import android.webkit.JavascriptInterface;

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class CorsProxyHandler {
    private Context context;

    public CorsProxyHandler(Context context) {
        this.context = context;
    }

    @JavascriptInterface
    public String encode(String url) {
        // gather configuration strings
        String authSecret = context.getResources().getString(R.string.cors_proxy_secret);
        String proxyUrl = context.getResources().getString(R.string.cors_proxy_url);

        // prepare auth stream to hash
        String authStr = authSecret+url;
        byte[] authStrBytes = authStr.getBytes();

        // prepare auth hash
        HashFunction hf = Hashing.sha1();
        HashCode hc = hf.hashBytes(authStrBytes);
        String auth = hc.toString();

        // we have all we need, encode and build full proxy URL
        String urlEncoded = null;
        try {
            urlEncoded = URLEncoder.encode(url, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        return proxyUrl+"?url="+urlEncoded+"&auth="+auth;
    }
}
