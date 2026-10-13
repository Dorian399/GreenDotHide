package com.dorian15.greendothide;

import android.content.ComponentName;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.github.libxposed.service.XposedService;

public class MainActivity extends AppCompatActivity implements ModuleApplication.Listener {

    private static final String TAG = "GreenDotHide";
    private static final String SYSTEM_UI = "com.android.systemui";
    private static final String LOCAL_PREFS = "prefs_local";
    private static final long SERVICE_TIMEOUT_MS = 8000L;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private ModuleStatusView moduleStatus;
    private List<SwitchMaterial> indicatorSwitches;
    private SwitchMaterial hideHomescreenSwitch;
    private SharedPreferences remotePrefs;

    private final Runnable serviceTimeout = () -> {
        if (remotePrefs == null)
            showStatus(ModuleStatusView.Status.STATUS_INACTIVE);
    };

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

        moduleStatus = findViewById(R.id.moduleStatus);
        indicatorSwitches = new ArrayList<>();
        indicatorSwitches.add(findViewById(R.id.disable_location_indicator));
        indicatorSwitches.add(findViewById(R.id.disable_microphone_indicator));
        indicatorSwitches.add(findViewById(R.id.disable_camera_indicator));
        indicatorSwitches.add(findViewById(R.id.disable_media_projection_indicator));
        hideHomescreenSwitch = findViewById(R.id.hide_homescreen_icon);

        showStatus(ModuleStatusView.Status.STATUS_LOADING);
        setIndicatorSwitchesEnabled(false);

        // The icon toggle is local to this app, so it works even without the framework.
        SharedPreferences localPrefs = getSharedPreferences(LOCAL_PREFS, MODE_PRIVATE);
        hideHomescreenSwitch.setChecked(localPrefs.getBoolean("hide_homescreen_icon", false));
        hideHomescreenSwitch.setOnCheckedChangeListener((switchView, isChecked) -> {
            localPrefs.edit().putBoolean("hide_homescreen_icon", isChecked).apply();
            setLauncherIconEnabled(!isChecked);
        });

        Button restartSystemUIButton = findViewById(R.id.restart_systemui);
        restartSystemUIButton.setOnClickListener((View v) -> restartSystemUI());

        ModuleApplication.addListener(this);
        handler.postDelayed(serviceTimeout, SERVICE_TIMEOUT_MS);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(serviceTimeout);
        ModuleApplication.removeListener(this);
        super.onDestroy();
    }

    @Override
    public void onXposedService(XposedService service) {
        runOnUiThread(() -> bindService(service));
    }

    /**
     * The framework only sends its service when the module is enabled, which replaces the old
     * self-hook that used to flip {@code isModuleEnabled()} inside this very app.
     */
    private void bindService(XposedService service) {
        if (isFinishing() || isDestroyed())
            return;
        handler.removeCallbacks(serviceTimeout);
        remotePrefs = null;

        if (service == null) {
            showStatus(ModuleStatusView.Status.STATUS_INACTIVE);
            setIndicatorSwitchesEnabled(false);
            return;
        }

        try {
            if ((service.getFrameworkProperties() & XposedService.PROP_CAP_REMOTE) == 0) {
                Log.w(TAG, "Framework lacks remote preference support");
                showStatus(ModuleStatusView.Status.STATUS_INACTIVE);
                setIndicatorSwitchesEnabled(false);
                return;
            }

            remotePrefs = service.getRemotePreferences(Prefs.GROUP);
        } catch (Throwable t) {
            Log.w(TAG, "Cannot open remote preferences", t);
            remotePrefs = null;
            showStatus(ModuleStatusView.Status.STATUS_INACTIVE);
            setIndicatorSwitchesEnabled(false);
            return;
        }

        boolean inScope;
        try {
            inScope = service.getScope().contains(SYSTEM_UI);
        } catch (Throwable t) {
            Log.w(TAG, "Cannot read the module scope", t);
            inScope = false;
        }
        showStatus(inScope
                ? ModuleStatusView.Status.STATUS_ACTIVE
                : ModuleStatusView.Status.STATUS_NO_SCOPE);

        setIndicatorSwitchesEnabled(true);
        loadIndicatorSwitches(remotePrefs);
        addIndicatorSwitchListeners(remotePrefs);
    }

    /**
     * Mirrors the switches into the framework store. Keys are derived from the layout ids, which
     * are the same names the hook reads, and missing keys default to enabled. The write is skipped
     * when nothing differs so reopening the Activity costs no cross-process round trip.
     */
    private void loadIndicatorSwitches(SharedPreferences prefs) {
        SharedPreferences.Editor editor = prefs.edit();
        boolean seeding = false;
        for (SwitchMaterial toggle : indicatorSwitches) {
            String key = getResources().getResourceEntryName(toggle.getId());
            boolean value = prefs.getBoolean(key, true);
            toggle.setChecked(value);
            // Seed the framework store on first run so the hook never reads a bare default.
            if (!prefs.contains(key)) {
                editor.putBoolean(key, value);
                seeding = true;
            }
        }
        if (seeding)
            editor.apply();
    }

    private void addIndicatorSwitchListeners(SharedPreferences prefs) {
        SharedPreferences.Editor editor = prefs.edit();
        for (SwitchMaterial toggle : indicatorSwitches) {
            toggle.setOnCheckedChangeListener((switchView, isChecked) -> {
                String key = getResources().getResourceEntryName(switchView.getId());
                editor.putBoolean(key, isChecked).apply();
            });
        }
    }

    private void showStatus(int status) {
        moduleStatus.setStatus(status);
    }

    private void setIndicatorSwitchesEnabled(boolean enabled) {
        for (SwitchMaterial toggle : indicatorSwitches)
            toggle.setEnabled(enabled);
    }

    private void setLauncherIconEnabled(boolean enabled) {
        getPackageManager().setComponentEnabledSetting(
                new ComponentName(this, LauncherIcon.class),
                enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
        );
    }

    private void restartSystemUI() {
        try {
            Runtime.getRuntime().exec(new String[]{"su", "-c", "killall com.android.systemui"});
        } catch (IOException e) {
            Toast.makeText(this, R.string.restart_systemui_fail, Toast.LENGTH_LONG).show();
        }
    }
}
