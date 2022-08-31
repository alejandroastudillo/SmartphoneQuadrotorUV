package com.survey360.quadcoptercontroluv.TestActivities;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ToggleButton;

import com.survey360.quadcoptercontroluv.MenuActivities.TestsActivity;
import com.survey360.quadcoptercontroluv.R;
import com.survey360.quadcoptercontroluv.Utils.Communication.CommunicationManager;

public class CommunicationTest extends AppCompatActivity {

    CommunicationManager mCommManager = null;

    private Button bt_ConfigureHotspot;
    private EditText et_SSID, et_Password;
    private ToggleButton tb_hotspot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_communication_test);
        mCommManager = new CommunicationManager(this, this);

        bt_ConfigureHotspot = (Button) findViewById(R.id.bt_configureHotspot);
        bt_ConfigureHotspot.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick (View view){
                configureHotspot();
            }

        });

        et_SSID = (EditText) findViewById(R.id.et_SSID);
        et_Password = (EditText) findViewById(R.id.et_password);

        tb_hotspot = (ToggleButton) findViewById(R.id.tb_HotspotON);
        tb_hotspot.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    // The toggle is enabled
                    Log.w("Toggle", "ON");
                    mCommManager.startHotspot();

                } else {
                    // The toggle is disabled
                    Log.w("Toggle", "OFF");
                    mCommManager.stopHotspot();
                }
            }
        });
    }

    public void configureHotspot(){
        String SSID = et_SSID.getText().toString();
        String password1 = et_Password.getText().toString();
        mCommManager.configureHotspot(SSID, password1);
        Log.w("SSID config: ","SSID: "+SSID+"; Password: "+password1);
    }

    protected void onDestroy(){
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        finish();
        Intent intentTest = new Intent(CommunicationTest.this, TestsActivity.class);
        startActivity(intentTest);
        return;
    }
}
