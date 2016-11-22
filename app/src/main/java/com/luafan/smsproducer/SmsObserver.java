package com.luafan.smsproducer;

import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;

public class SmsObserver extends ContentObserver {
    private final Context context;

    public SmsObserver(Context context, Handler handler) {
        super(handler);

        this.context = context;
    }

    @Override
    public void onChange(boolean selfChange, Uri uri) {
        super.onChange(selfChange, uri);

        UploadHelper.uploadSMSQueue(context);
    }
}