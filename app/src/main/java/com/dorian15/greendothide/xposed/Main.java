package com.dorian15.greendothide.xposed;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

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

        // HyperOS/MIUI
        String className = "com.android.systemui.statusbar.privacy.MiuiPrivacyControllerImpl";
        String methodName = "setStatus";
        try{
            XposedHelpers.findAndHookMethod(
                    className,
                    lpparam.classLoader,
                    methodName,
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
            XposedBridge.log("[GreenDotHide] Hooked into: "+className+"."+methodName);
        }catch(Exception e){
            XposedBridge.log("[GreenDotHide] Ignoring hook: "+className+"."+methodName);
        }

        // AOSP based SystemUI
        className = "com.android.systemui.privacy.PrivacyConfig";
        methodName = "isLocationEnabled";
        try{
            XposedHelpers.findAndHookMethod(
                    className,
                    lpparam.classLoader,
                    methodName,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            param.setResult(false);
                        }
                    }
            );
            XposedBridge.log("[GreenDotHide] Hooked into: "+className+"."+methodName);
        }catch(Exception e){
            XposedBridge.log("[GreenDotHide] Ignoring hook: "+className+"."+methodName);
        }

        methodName = "isMicCameraEnabled";
        try{
            XposedHelpers.findAndHookMethod(
                    className,
                    lpparam.classLoader,
                    methodName,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            param.setResult(false);
                        }
                    }
            );
            XposedBridge.log("[GreenDotHide] Hooked into: "+className+"."+methodName);
        }catch(Exception e){
            XposedBridge.log("[GreenDotHide] Ignoring hook: "+className+"."+methodName);
        }

        methodName = "isMediaProjectionEnabled";
        try{
            XposedHelpers.findAndHookMethod(
                    className,
                    lpparam.classLoader,
                    methodName,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            param.setResult(false);
                        }
                    }
            );
            XposedBridge.log("[GreenDotHide] Hooked into: "+className+"."+methodName);
        }catch(Exception e){
            XposedBridge.log("[GreenDotHide] Ignoring hook: "+className+"."+methodName);
        }

        className = "android.provider.DeviceConfig";
        methodName = "getBoolean";
        try{

            List<String> indicatorsList = Arrays.asList("camera_mic_icons_enabled", "location_indicators_enabled", "media_projection_indicators_enabled");
            HashSet<String> indicatorsSet = new HashSet<String>(indicatorsList);

            XposedHelpers.findAndHookMethod(
                    className,
                    lpparam.classLoader,
                    methodName,
                    String.class,
                    String.class,
                    boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            String namespace = (String)param.args[0];
                            String name = (String)param.args[1];
                            if(Objects.equals(namespace, "privacy") && indicatorsSet.contains(name)){
                                param.setResult(false);
                            }
                        }
                    }
            );
            XposedBridge.log("[GreenDotHide] Hooked into: "+className+"."+methodName);
        }catch(Exception e){
            XposedBridge.log("[GreenDotHide] Ignoring hook: "+className+"."+methodName);
        }
    }
}
