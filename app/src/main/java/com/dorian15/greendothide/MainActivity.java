package com.dorian15.greendothide;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.Switch;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private boolean isModuleEnabled(){
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        ModuleStatusView moduleStatus = findViewById(R.id.moduleStatus);

        if(isModuleEnabled()) {
            moduleStatus.setStatus(ModuleStatusView.Status.STATUS_ACTIVE);
        }else{
            moduleStatus.setStatus(ModuleStatusView.Status.STATUS_INACTIVE);
        }

        List<SwitchMaterial> mainSwitches = new ArrayList<>();
        mainSwitches.add(findViewById(R.id.disable_location_indicator));
        mainSwitches.add(findViewById(R.id.disable_microphone_indicator));
        mainSwitches.add(findViewById(R.id.disable_camera_indicator));
        mainSwitches.add(findViewById(R.id.disable_media_projection_indicator));
        mainSwitches.add(findViewById(R.id.hide_homescreen_icon));

        SharedPreferences sharedPrefs = getSharedPreferences("prefs",MODE_WORLD_READABLE);

        loadSwitchesSettings(sharedPrefs,mainSwitches);
        addSwitchListeners(sharedPrefs, mainSwitches);
    }

    private void loadSwitchesSettings(SharedPreferences sharedPrefs,List<SwitchMaterial> switches){
        switches.forEach((switchMaterial -> {
            String idString = getResources().getResourceEntryName(switchMaterial.getId());
            String tag = String.valueOf(switchMaterial.getTag());
            boolean defBool = tag.equals("no_logic");
            switchMaterial.setChecked(sharedPrefs.getBoolean(idString,defBool));
        }));
    }

    private void addSwitchListeners(SharedPreferences sharedPrefs, List<SwitchMaterial> switches){
        SharedPreferences.Editor editor = sharedPrefs.edit();

        switches.forEach((switchMaterial -> {
            String tag = String.valueOf(switchMaterial.getTag());
            boolean shouldSkip = !tag.equals("no_logic");
            if( !shouldSkip ) {
                switchMaterial.setOnCheckedChangeListener(((switchView, isChecked) -> {
                    String idString = getResources().getResourceEntryName(switchView.getId());
                    editor.putBoolean(idString,isChecked);
                    editor.apply();
                }));
            }
        }));

        SwitchMaterial hideHomescreenSwitch = findViewById(R.id.hide_homescreen_icon);
        hideHomescreenSwitch.setOnCheckedChangeListener(((switchView, isChecked) -> {
            String idString = getResources().getResourceEntryName(switchView.getId());
            editor.putBoolean(idString,isChecked);
            editor.apply();

            // Logic here
        }));

    }
}