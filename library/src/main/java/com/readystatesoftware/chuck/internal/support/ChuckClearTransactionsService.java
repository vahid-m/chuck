package com.readystatesoftware.chuck.internal.support;

import android.app.IntentService;
import android.content.Intent;
import android.support.annotation.Nullable;

import com.readystatesoftware.chuck.internal.data.ChuckContentProvider;

public class ChuckClearTransactionsService extends IntentService {

    public ChuckClearTransactionsService() {
        super("Chuck-ClearTransactionsService");
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        getContentResolver().delete(ChuckContentProvider.TRANSACTION_URI, null, null);
        ChuckNotificationHelper.clearBuffer();
        ChuckNotificationHelper notificationHelper = new ChuckNotificationHelper(this);
        notificationHelper.dismiss();
    }
}