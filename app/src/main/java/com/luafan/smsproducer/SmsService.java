package com.luafan.smsproducer;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

/**
 * Created by sam on 9/9/16.
 */
public class SmsService extends Service {
    private SmsObserver smsObserver;

    @Override
    public void onCreate() {
        Uri uri = Uri.parse("content://sms/");
        getContentResolver().registerContentObserver(uri, true,
                new SmsObserver(this, null));

        UploadHelper.uploadSMSQueue(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(Consts.LOGCAT_TAG, "onStartCommand");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {

    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(Consts.LOGCAT_TAG, "onBind");
        return null;
    }
}
