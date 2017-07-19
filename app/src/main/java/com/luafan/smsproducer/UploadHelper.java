package com.luafan.smsproducer;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

/**
 * Created by sam on 9/13/16.
 */
public class UploadHelper {
    private static Thread daemonThread;
    private static Object taskWait = new Object();

    private static long latest_date = 0;
    private static Random random = new Random(System.currentTimeMillis());

    private static String publicKey = null;
    private static String serviceURL = null;
    private static String deviceId = null;
    private static long max_date = 0;
    private static int count = 0;
    private static Context context;

    static {
        daemonThread = new Thread(new Runnable() {
            public void run() {
                while (true) {
                    try {
                        if (context == null) {
                            synchronized (taskWait) {
                                taskWait.wait();
                            }
                            continue;
                        }

                        JSONObject map = buildMap(context);

                        if (map == null || map.getJSONArray("list").length() == 0) {
                            synchronized (taskWait) {
                                taskWait.wait(10 * 1000);
                            }
                            continue;
                        }

                        uploadSMS(map);

                        Thread.sleep(5000);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        daemonThread.start();
    }

    private static void fillEncryptField(String fieldKey, SecretKey secretKey, IvParameterSpec iv, JSONObject obj, String value) throws Exception {
        if (value == null) {
            value = "";
        }
        Cipher des_cipher = Cipher.getInstance("DESede/CBC/PKCS5Padding");
        des_cipher.init(Cipher.ENCRYPT_MODE, secretKey, iv);
        byte[] data = des_cipher.doFinal(value.getBytes());
        obj.put(fieldKey, new String(Base64.encode(data, Base64.NO_WRAP)));
    }

    public static void uploadSMSQueue(final Context context) {
        UploadHelper.context = context;

        synchronized (taskWait) {
            taskWait.notifyAll();
        }
    }

    private static JSONObject buildMap(Context context) {
        PublicKey pubKey = null;
        FileInputStream fin = null;
        try {
            fin = context.openFileInput(Consts.configPath);

            JSONObject config = new JSONObject(IOUtils.toString(fin));

            publicKey = config.getString(Consts.publicKey);
            serviceURL = config.getString(Consts.serviceURL);
            deviceId = config.getString(Consts.deviceID);

            publicKey = publicKey.replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "").replace("\n", "").replace("\r", "").trim();
            X509EncodedKeySpec spec = new X509EncodedKeySpec(Base64.decode(publicKey, Base64.DEFAULT));
            KeyFactory kf = KeyFactory.getInstance("RSA");
            pubKey = kf.generatePublic(spec);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            IOUtils.closeQuietly(fin);
        }

        Log.d(Consts.LOGCAT_TAG, "preparing sms ...");

        ContentResolver cr = context.getContentResolver();
        String[] projection = new String[]{"_id", "thread_id", "date", "type", "read", "address", "person", "body"};
        Cursor cur = null;

        try {
            JSONObject map = new JSONObject();
            map.put("publickey", publicKey);
            map.put("deviceid", deviceId);

            Uri uri = Uri.parse("content://sms/");
            cur = cr.query(uri, projection, "date>?", new String[]{String.valueOf(latest_date)}, "date desc");

            ArrayList<JSONObject> list = new ArrayList<JSONObject>();

            while (cur.moveToNext()) {
                JSONObject obj = new JSONObject();

                KeyGenerator kg = KeyGenerator.getInstance("DESede");
                kg.init(168);
                SecretKey secretKey = kg.generateKey();

                obj.put("msg_id", cur.getLong(cur.getColumnIndex("_id")));
                obj.put("msg_threadid", cur.getLong(cur.getColumnIndex("thread_id")));

                long date = cur.getLong(cur.getColumnIndex("date"));
                if (date > max_date) {
                    max_date = date;
                }
                obj.put("msg_date", date);
                obj.put("msg_type", cur.getInt(cur.getColumnIndex("type")));
                obj.put("msg_read", cur.getInt(cur.getColumnIndex("read")));

                byte[] rand = new byte[8];
                random.nextBytes(rand);

                IvParameterSpec iv = new IvParameterSpec(rand);

                fillEncryptField("msg_address", secretKey, iv, obj, cur.getString(cur.getColumnIndex("address")));
                fillEncryptField("msg_person", secretKey, iv, obj, cur.getString(cur.getColumnIndex("person")));
                fillEncryptField("msg_body", secretKey, iv, obj, cur.getString(cur.getColumnIndex("body")));

                obj.put("msg_iv", new String(Base64.encode(rand, Base64.NO_WRAP)));

                byte[] desKey = secretKey.getEncoded();

                Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1PADDING");
                cipher.init(Cipher.ENCRYPT_MODE, pubKey);
                byte[] data = cipher.doFinal(desKey);
                obj.put("msg_deskey", new String(Base64.encode(data, Base64.NO_WRAP)));

                list.add(obj);

                if (++count > Consts.maxUploadCount) {
                    break;
                }
            }

            Collections.reverse(list);

            map.put("list", new JSONArray(list));

            return map;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cur != null) {
                cur.close();
            }
        }

        return null;
    }

    private static void uploadSMS(JSONObject map) {
        if (map == null) {
            return;
        }

        String body = map.toString();
        System.out.println(body);

        if (count > 0) {
            HttpURLConnection urlConnection = null;
            try {
                urlConnection = (HttpURLConnection) new URL(serviceURL).openConnection();
                urlConnection.setDoOutput(true);
                urlConnection.setChunkedStreamingMode(0);
                urlConnection.setRequestMethod("POST");
                urlConnection.setRequestProperty("Content-Type", "text/json; charset=utf-8");
                urlConnection.setRequestProperty("Content-Length", String.valueOf(body.getBytes().length));
                OutputStream out = urlConnection.getOutputStream();
                out.write(body.getBytes());
                out.close();
                int responseCode = urlConnection.getResponseCode();
                System.out.println("responseCode = " + responseCode);
                if (responseCode == 200) {
                    latest_date = max_date;
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
            }
        }
    }
}
