package com.luafan.smsproducer;

import android.content.Intent;
import android.content.Context;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import org.apache.commons.io.IOUtils;
import org.json.JSONObject;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.UUID;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private JSONObject config = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Intent intent = new Intent(getApplicationContext(), SmsService.class);
        startService(intent);

        final EditText keyEditText = (EditText) findViewById(R.id.publicKey);
        final EditText addressEditText = (EditText) findViewById(R.id.address);

        keyEditText.setHint(R.string.publickey_hint);
        addressEditText.setHint(R.string.address_hint);

        Button applyButton = (Button) findViewById(R.id.button);

        FileInputStream fin = null;
        try {
            fin = openFileInput(Consts.configPath);
            config = new JSONObject(IOUtils.toString(fin));
            keyEditText.setText(config.getString(Consts.publicKey));
            addressEditText.setText(config.getString(Consts.serviceURL));
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            IOUtils.closeQuietly(fin);
        }

        updateConfig();

        applyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateConfig();
            }
        });
    }

    public void updateConfig() {
        final EditText keyEditText = (EditText) findViewById(R.id.publicKey);
        final EditText addressEditText = (EditText) findViewById(R.id.address);

        FileOutputStream fout = null;
        try {
            if (config == null) {
                config = new JSONObject();
            }

            config.put(Consts.publicKey, keyEditText.getText().toString());
            config.put(Consts.serviceURL, addressEditText.getText().toString());

            if (!config.has(Consts.deviceID)) {
                config.put(Consts.deviceID, UUID.randomUUID().toString());
            }

            fout = openFileOutput(Consts.configPath, Context.MODE_PRIVATE);
            fout.write(config.toString().getBytes());
            fout.flush();

            Toast.makeText(MainActivity.this, R.string.config_update_success, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(MainActivity.this, R.string.config_update_failed, Toast.LENGTH_LONG).show();
            e.printStackTrace();
        } finally {
            IOUtils.closeQuietly(fout);
        }
    }

    public void sendSMS(String phoneNumber, String message) {
        android.telephony.SmsManager smsManager = android.telephony.SmsManager.getDefault();
        List<String> divideContents = smsManager.divideMessage(message);
        for (String text : divideContents) {
            smsManager.sendTextMessage(phoneNumber, null, text, null, null);
        }
    }
}
