/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.wiktionary;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.util.Log;
import android.webkit.WebView;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

/**
 * Helper methods to simplify talking with and parsing responses from a
 * lightweight Wiktionary API. Before making any requests, you should call
 * {@link #prepareUserAgent(Context)} to generate a User-Agent string based on
 * your application package name and version.
 */
public class SimpleWikiHelper {
    private static final String TAG = "SimpleWikiHelper";
    
    /**
     * Partial URL to use when requesting the detailed entry for a specific
     * Wiktionary page. Use {@link String#format(String, Object...)} to insert
     * the desired page title after escaping it as needed.
     */
    private static final String WIKTIONARY_PAGE =
        "http://en.wiktionary.org/w/api.php?action=query&prop=revisions&titles=%s&" +
        "rvprop=content&format=json%s";

    /**
     * Partial URL to append to {@link #WIKTIONARY_PAGE} when you want to expand
     * any templates found on the requested page. This is useful when browsing
     * full entries, but may use more network bandwidth.
     */
    private static final String WIKTIONARY_EXPAND_TEMPLATES =
        "&rvexpandtemplates=true";

    /**
     * {@link StatusLine} HTTP status code when no server error has occurred.
     */
    private static final int HTTP_STATUS_OK = 200;

	/**
	 * Encoding to use when showing parsed results in a {@link WebView}.
	 */
	public static final String ENCODING = "utf-8";
    
    /**
     * Shared buffer used by {@link #getUrlContent(String)} when reading results
     * from an API request.
     */
    private static byte[] sBuffer = new byte[512];
    
    /**
     * User-agent string to use when making requests. Should be filled using
     * {@link #prepareUserAgent(Context)} before making any other calls.
     */
    private static String sUserAgent = null;
    
    /**
     * Thrown when there were problems contacting the remote API server, either
     * because of a network error, or the server returned a bad status code.
     */
    public static class ApiException extends Exception {
        public ApiException(String detailMessage, Throwable throwable) {
            super(detailMessage, throwable);
        }
        
        public ApiException(String detailMessage) {
            super(detailMessage);
        }
    }

    /**
     * Thrown when there were problems parsing the response to an API call,
     * either because the response was empty, or it was malformed.
     */
    public static class ParseException extends Exception {
        public ParseException(String detailMessage, Throwable throwable) {
            super(detailMessage, throwable);
        }
    }
    
    /**
     * Prepare the internal User-Agent string for use. This requires a
     * {@link Context} to pull the package name and version number for this
     * application.
     */
    public static void prepareUserAgent(Context context) {
        try {
            // Read package name and version number from manifest
            PackageManager manager = context.getPackageManager();
            PackageInfo info = manager.getPackageInfo(context.getPackageName(), 0);
            sUserAgent = String.format(context.getString(R.string.template_user_agent),
                    info.packageName, info.versionName);
            
        } catch(NameNotFoundException e) {
            Log.e(TAG, "Couldn't find package information in PackageManager", e);
        }
    }
    
    /**
     * Read and return the content for a specific Wiktionary page. This makes a
     * lightweight API call, and trims out just the page content returned.
     * Because this call blocks until results are available, it should not be
     * run from a UI thread.
     * 
     * @param title The exact title of the Wiktionary page requested.
     * @param expandTemplates If true, expand any wiki templates found.
     * @return Exact content of page.
     * @throws ApiException If any connection or server error occurs.
     * @throws ParseException If there are problems parsing the response.
     */
    public static String getPageContent(String title, boolean expandTemplates)
            throws ApiException, ParseException {
        // Encode page title and expand templates if requested
        String encodedTitle = Uri.encode(title);
        String expandClause = expandTemplates ? WIKTIONARY_EXPAND_TEMPLATES : "";
        
        // Query the API for content
        String url = String.format(WIKTIONARY_PAGE,
                encodedTitle, expandClause);
        Log.d("SimpleWikiHelper", "url: " + url);
        String content = null;
        try {
            content = downloadUrl(new URL("https://www.accc.gov.au/consumers/petrol-diesel-lpg/petrol-price-cycles"));
            return content;
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            // Drill into the JSON response to find the content body
            JSONObject response = new JSONObject(content);
            JSONObject query = response.getJSONObject("query");
            JSONObject pages = query.getJSONObject("pages");
            JSONObject page = pages.getJSONObject((String) pages.keys().next());
            JSONArray revisions = page.getJSONArray("revisions");
            JSONObject revision = revisions.getJSONObject(0);
            return revision.getString("*");
        } catch (JSONException e) {
            throw new ParseException("Problem parsing API response", e);
        }
    }

    /**
     * Pull the raw text content of the given URL. This call blocks until the
     * operation has completed, and is synchronized because it uses a shared
     * buffer {@link #sBuffer}.
     * 
     * @param url The exact URL to request.
     * @return The raw content returned by the server.
     * @throws ApiException If any connection or server error occurs.
     */

    private static String downloadUrl(URL url) throws IOException {
        InputStream stream = null;
        HttpsURLConnection connection = null;
        String result = null;
        try {
            connection = (HttpsURLConnection) url.openConnection();
            // Timeout for reading InputStream arbitrarily set to 3000ms.
            connection.setReadTimeout(3000);
            // Timeout for connection.connect() arbitrarily set to 3000ms.
            connection.setConnectTimeout(3000);
            // For this use case, set HTTP method to GET.
            connection.setRequestMethod("GET");
            // Already true by default but setting just in case; needs to be true since this request
            // is carrying an input (response) body.
            connection.setDoInput(true);
            // Open communications link (network traffic occurs here).
            connection.connect();
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpsURLConnection.HTTP_OK) {
                throw new IOException("HTTP error code: " + responseCode);
            }
            // Retrieve the response body as an InputStream.
            stream = connection.getInputStream();
            if (stream != null) {
                // Converts Stream to String with max length of 500.
                result = readStream(stream, 300000);

                int pos = result.indexOf("petrol-prices-in-sydney", 2);
                pos = result.indexOf("petrol-prices-in-sydney",pos + 10);
                pos = result.indexOf("petrol-prices-in-sydney",pos + 10);

                result = result.substring(pos + 294, pos + 468);
                result = result.replaceAll("<strong>","");
                result = result.replaceAll("</strong>","");
                result = result.replaceAll("</li>","");
                result = result.replaceAll("<li>","");
                result = result.replaceAll("\n","");
            }
        } finally {
            // Close Stream and disconnect HTTPS connection.
            if (stream != null) {
                stream.close();
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
        return result;
    }

    /**
     * Converts the contents of an InputStream to a String.
     */
    private static  String readStream(InputStream stream, int maxLength) throws IOException {
        String result = null;
        // Read InputStream using the UTF-8 charset.
        InputStreamReader reader = new InputStreamReader(stream, "UTF-8");
        // Create temporary buffer to hold Stream data with specified max length.
        char[] buffer = new char[maxLength];
        // Populate temporary buffer with Stream data.
        int numChars = 0;
        int readSize = 0;
        while (numChars < maxLength && readSize != -1) {
            numChars += readSize;
            int pct = (100 * numChars) / maxLength;
            readSize = reader.read(buffer, numChars, buffer.length - numChars);
        }
        if (numChars != -1) {
            // The stream was not empty.
            // Create String that is actual length of response body if actual length was less than
            // max length.
            numChars = Math.min(numChars, maxLength);
            result = new String(buffer, 0, numChars);
        }
        return result;
    }
}
    

