package com.dorian15.greendothide.com.dorian15.greendothide.xposed;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class Main implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.android.systemui"))
            return;

        XposedHelpers.findAndHookMethod(
                "com.android.systemui.statusbar.privacy.MiuiPrivacyControllerImpl",
                lpparam.classLoader,
                "setStatus",
                int.class,
                String.class,
                android.os.Bundle.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        param.setResult(null);
                    }
                }
        );
    }
}
