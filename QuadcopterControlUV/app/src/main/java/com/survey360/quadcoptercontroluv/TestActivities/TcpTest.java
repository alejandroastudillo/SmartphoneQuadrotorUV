package com.survey360.quadcoptercontroluv.TestActivities;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.survey360.quadcoptercontroluv.MenuActivities.TestsActivity;
import com.survey360.quadcoptercontroluv.R;
import com.survey360.quadcoptercontroluv.Utils.Communication.DataExchange;

import org.w3c.dom.Text;

import java.io.IOException;

public class TcpTest extends AppCompatActivity {

    private ToggleButton tb_tcpserver;
    private ProgressBar pb_xAxis, pb_yAxis, pb_zAxis, pb_zRot;
    private TextView tv_dPad, tv_button;
    DataExchange mDataExchange = null;
    Thread receive;
    Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tcp_test);

        try {
            mDataExchange = new DataExchange(this);
        } catch (IOException e) {
            e.printStackTrace();
        }

        pb_xAxis = (ProgressBar) findViewById(R.id.pb_xAxis);
        pb_yAxis = (ProgressBar) findViewById(R.id.pb_yAxis);
        pb_zAxis = (ProgressBar) findViewById(R.id.pb_zAxis);
        pb_zRot = (ProgressBar) findViewById(R.id.pb_zRotation);
        tv_button = (TextView) findViewById(R.id.tv_buttonPressed);
        tv_dPad = (TextView) findViewById(R.id.tv_dPad);

        tb_tcpserver = (ToggleButton) findViewById(R.id.tb_TCPserver);
        tb_tcpserver.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    // The toggle is enabled
                    Log.w("Toggle", "ON");
                    mDataExchange.startTCPserver();
                    Toast.makeText(TcpTest.this, "TCP Server Started", Toast.LENGTH_SHORT).show();
                    receive.start();

                } else {
                    // The toggle is disabled
                    Log.w("Toggle", "OFF");
                    mDataExchange.stopTCPserver();
                    Toast.makeText(TcpTest.this, "TCP Server Stopped", Toast.LENGTH_SHORT).show();
                }
            }
        });

        receive = new Thread() {
            public void run() {
                while (true){
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            pb_xAxis.setProgress(mDataExchange.rollJoystick);
                            pb_yAxis.setProgress(mDataExchange.pitchJoystick);
                            pb_zAxis.setProgress(mDataExchange.yawJoystick);
                            pb_zRot.setProgress(mDataExchange.throttleJoystick);
                        }
                    });
                }
            }
        };

    }

    protected void onDestroy(){
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        finish();
        Intent intentTest = new Intent(TcpTest.this, TestsActivity.class);
        startActivity(intentTest);
        return;
    }
}
