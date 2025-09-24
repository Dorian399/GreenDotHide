package com.dorian15.greendothide.xposed;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class Main implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.android.systemui"))
            return;
        try{
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
            XposedBridge.log("[GreenDotHide] Hooked into: com.android.systemui.statusbar.privacy.MiuiPrivacyControllerImpl.SetStatus");
        }catch(Exception e){
            XposedBridge.log("[GreenDotHide] Ignoring hook: com.android.systemui.statusbar.privacy.MiuiPrivacyControllerImpl.SetStatus");
        }
    }
}
