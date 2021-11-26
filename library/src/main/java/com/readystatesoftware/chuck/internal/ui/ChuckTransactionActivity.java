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

import static com.readystatesoftware.chuck.internal.ui.ChuckTransactionPayloadFragment.TYPE_REQUEST;
import static com.readystatesoftware.chuck.internal.ui.ChuckTransactionPayloadFragment.TYPE_RESPONSE;

import android.app.ProgressDialog;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import com.readystatesoftware.chuck.R;
import com.readystatesoftware.chuck.internal.data.ChuckContentProvider;
import com.readystatesoftware.chuck.internal.data.ChuckLocalCupboard;
import com.readystatesoftware.chuck.internal.data.HttpTransaction;
import com.readystatesoftware.chuck.internal.support.ChuckFormatUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ChuckTransactionActivity extends ChuckBaseActivity implements LoaderManager.LoaderCallbacks<Cursor> {

    private static final String ARG_TRANSACTION_ID = "transaction_id";

    private static int selectedTabPosition = 0;

    private HttpTransaction transaction;

    TextView title;
    Adapter adapter;

    private long transactionId;

    public static void start(Context context, long transactionId) {
        Intent intent = new Intent(context, ChuckTransactionActivity.class);
        intent.putExtra(ARG_TRANSACTION_ID, transactionId);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.chuck_activity_transaction);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        title = (TextView) findViewById(R.id.toolbar_title);

        final ActionBar ab = getSupportActionBar();
        ab.setDisplayHomeAsUpEnabled(true);

        ViewPager viewPager = (ViewPager) findViewById(R.id.viewpager);
        if (viewPager != null) {
            setupViewPager(viewPager);
        }

        TabLayout tabLayout = (TabLayout) findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(viewPager);

        transactionId = getIntent().getLongExtra(ARG_TRANSACTION_ID, 0);
        getSupportLoaderManager().initLoader(0, null, this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        getSupportLoaderManager().restartLoader(0, null, this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.chuck_transaction, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.share_text) {
            share(ChuckFormatUtils.getShareText(this, transaction));
            return true;
        } else if (item.getItemId() == R.id.share_curl) {
            share(ChuckFormatUtils.getShareCurlCommand(transaction));
            return true;
        } else if (item.getItemId() == R.id.share_link) {
            shareLink(ChuckFormatUtils.getShareText(this, transaction));
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        CursorLoader loader = new CursorLoader(this);
        loader.setUri(ContentUris.withAppendedId(ChuckContentProvider.TRANSACTION_URI, transactionId));
        return loader;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        transaction = ChuckLocalCupboard.getInstance().withCursor(data).get(HttpTransaction.class);
        populateUI();
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
    }

    private void populateUI() {
        if (transaction != null) {
            title.setText(transaction.getMethod() + " " + transaction.getPath());
            for (TransactionFragment fragment : adapter.fragments) {
                fragment.transactionUpdated(transaction);
            }
        }
    }

    private void setupViewPager(ViewPager viewPager) {
        adapter = new Adapter(getSupportFragmentManager());
        adapter.addFragment(new ChuckTransactionOverviewFragment(), getString(R.string.chuck_overview));
        adapter.addFragment(ChuckTransactionPayloadFragment.newInstance(TYPE_REQUEST), getString(R.string.chuck_request));
        adapter.addFragment(ChuckTransactionPayloadFragment.newInstance(TYPE_RESPONSE), getString(R.string.chuck_response));
        viewPager.setAdapter(adapter);
        viewPager.setOffscreenPageLimit(2);
        viewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                selectedTabPosition = position;
            }
        });
        viewPager.setCurrentItem(selectedTabPosition);
    }

    private void share(String content) {
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, content);
        sendIntent.setType("text/plain");
        startActivity(Intent.createChooser(sendIntent, null));
    }

    private void shareLink(final String content) {
        final ProgressDialog progress = ProgressDialog.show(this, null, "Create link...", true, false);
        OkHttpClient client = new OkHttpClient.Builder().build();
        Callback callback = new Callback() {
            @Override
            public void onFailure(Call call, final IOException e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progress.dismiss();
                        Toast.makeText(getApplicationContext(), "Create link error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                progress.dismiss();
                try {
                    if (!response.isSuccessful()) {
                        Log.e("Chuck", "Create link error: " + response);
                        throw new IOException(response.message());
                    }
                    String url = response.body().string().replaceFirst("com/", "com/raw/");
                    share(url);
                } catch (Throwable e) {
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getApplicationContext(), "Create link error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        };
        try {
            RequestBody formBody = new FormBody.Builder()
                    .add("api_dev_key", "1wKkohgQo_3e1ceW8gzEpWg8QXE0sMg0")
                    .add("api_option", "paste")
                    .add("api_paste_code", content)
                    .build();
            Request request = new Request.Builder()
                    .url("https://pastebin.com/api/api_post.php")
                    .post(formBody)
                    .build();
            Call call = client.newCall(request);
            call.enqueue(callback);
        } catch (Throwable e) {
            e.printStackTrace();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), "Create link error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    static class Adapter extends FragmentPagerAdapter {
        final List<TransactionFragment> fragments = new ArrayList<>();
        private final List<String> fragmentTitles = new ArrayList<>();

        Adapter(FragmentManager fm) {
            super(fm);
        }

        void addFragment(TransactionFragment fragment, String title) {
            fragments.add(fragment);
            fragmentTitles.add(title);
        }

        @Override
        public Fragment getItem(int position) {
            return (Fragment) fragments.get(position);
        }

        @Override
        public int getCount() {
            return fragments.size();
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return fragmentTitles.get(position);
        }
    }
}
