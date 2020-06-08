/*
 * Copyright (C) 2017 Jeff Gilfelt.
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
package com.readystatesoftware.chuck.internal.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.Keep;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.util.ObjectsCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import com.readystatesoftware.chuck.R;
import com.readystatesoftware.chuck.internal.data.HttpTransaction;

public class ChuckTransactionPayloadFragment extends Fragment implements TransactionFragment {

    public static final int TYPE_REQUEST = 0;
    public static final int TYPE_RESPONSE = 1;

    private static final String ARG_TYPE = "type";

    WebView webView;

    private int type;
    private HttpTransaction transaction;

    public ChuckTransactionPayloadFragment() {
    }

    public static ChuckTransactionPayloadFragment newInstance(int type) {
        ChuckTransactionPayloadFragment fragment = new ChuckTransactionPayloadFragment();
        Bundle b = new Bundle();
        b.putInt(ARG_TYPE, type);
        fragment.setArguments(b);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        type = getArguments().getInt(ARG_TYPE);
        setRetainInstance(true);
    }

    private boolean isLoaded;

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        populateUI();
    }

    private String headers, raw;

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.chuck_fragment_transaction_payload, container, false);
        webView = view.findViewById(R.id.webView);
        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.addJavascriptInterface(new JavaScriptInterface(), "android");
        return view;
    }

    private int lastHash;

    @Override
    public void transactionUpdated(HttpTransaction transaction) {
        this.transaction = transaction;
        populateUI();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        isLoaded = false;
        lastHash = 0;
    }

    private void populateUI() {
        if (isAdded() && transaction != null) {
            switch (type) {
                case TYPE_REQUEST:
                    headers = transaction.getRequestHeadersString(true);
                    raw = transaction.requestBodyIsPlainText()
                            ? transaction.getRequestBody() : getString(R.string.chuck_body_omitted);
                    break;
                case TYPE_RESPONSE:
                    headers = transaction.getResponseHeadersString(true);
                    raw = transaction.responseBodyIsPlainText()
                            ? transaction.getResponseBody() : getString(R.string.chuck_body_omitted);
                    break;
            }
            int hash = ObjectsCompat.hash(headers, raw);
            if (isLoaded) {
                if (lastHash != hash) {
                    webView.reload();
                }
            } else {
                webView.loadUrl("file:///android_asset/json_viewer.html");
                isLoaded = true;
            }
            lastHash = hash;
        }
    }

    @Keep
    private class JavaScriptInterface {

        @JavascriptInterface
        public String getHeaders() {
            return headers;
        }

        @JavascriptInterface
        public String getRaw() {
            return raw;
        }
    }
}