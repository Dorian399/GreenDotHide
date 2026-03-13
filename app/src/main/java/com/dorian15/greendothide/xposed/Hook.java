package com.dorian15.greendothide.xposed;

import android.graphics.Rect;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import java.util.List;


import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_InitPackageResources;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class Hook implements IXposedHookLoadPackage, IXposedHookInitPackageResources {
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
                        protected void beforeHookedMethod(MethodHookParam param) {
                            param.setResult(null);
                        }
                    }
            );
            XposedBridge.log("[GreenDotHide] Hooked into: "+className+"."+methodName);
        }catch(Throwable e){
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
                        protected void beforeHookedMethod(MethodHookParam param) {
                            param.setResult(false);
                        }
                    }
            );
            XposedBridge.log("[GreenDotHide] Hooked into: "+className+"."+methodName);
        }catch(Throwable e){
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
                        protected void beforeHookedMethod(MethodHookParam param) {
                            param.setResult(false);
                        }
                    }
            );
            XposedBridge.log("[GreenDotHide] Hooked into: "+className+"."+methodName);
        }catch(Throwable e){
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
                        protected void beforeHookedMethod(MethodHookParam param) {
                            param.setResult(false);
                        }
                    }
            );
            XposedBridge.log("[GreenDotHide] Hooked into: "+className+"."+methodName);
        }catch(Throwable e){
            XposedBridge.log("[GreenDotHide] Ignoring hook: "+className+"."+methodName);
        }

        className = "com.android.systemui.privacy.PrivacyItemController";
        methodName = "updatePrivacyList";
        try{
            XposedHelpers.findAndHookMethod(
                    className,
                    lpparam.classLoader,
                    methodName,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            param.setResult(false);
                        }
                    }
            );
            XposedBridge.log("[GreenDotHide] Hooked into: "+className+"."+methodName);
        }catch(Throwable e){
            XposedBridge.log("[GreenDotHide] Ignoring hook: "+className+"."+methodName);
        }

        className = "com.android.systemui.statusbar.events.SystemEventChipAnimationControllerImpl";
        methodName = "onSystemEventAnimationBegin";

        try{
            XposedHelpers.findAndHookMethod(
                    className,
                    lpparam.classLoader,
                    methodName,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Rect chipBounds = new Rect(0,0,0,0);
                            XposedHelpers.setObjectField(param.thisObject,"chipBounds",chipBounds);
                            XposedHelpers.setObjectField(param.thisObject,"chipMinWidth",0);
                        }
                    }
            );
            XposedBridge.log("[GreenDotHide] Hooked into: "+className+"."+methodName);
        }catch(Throwable e){
            XposedBridge.log("[GreenDotHide] Ignoring hook: "+className+"."+methodName);
        }

        try{
            XposedHelpers.findAndHookConstructor(
                    className,
                    lpparam.classLoader,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Rect chipBounds = new Rect(0,0,0,0);
                            XposedHelpers.setObjectField(param.thisObject,"chipBounds",chipBounds);
                            XposedHelpers.setObjectField(param.thisObject,"chipMinWidth",0);
                        }
                    }
            );
            XposedBridge.log("[GreenDotHide] Hooked into: "+className+"."+methodName);
        }catch(Throwable e){
            XposedBridge.log("[GreenDotHide] Ignoring hook: "+className+"."+methodName);
        }

        className = "com.android.systemui.privacy.OngoingPrivacyChip";
        methodName = "setPrivacyList";

        try{
            XposedHelpers.findAndHookMethod(
                    className,
                    lpparam.classLoader,
                    methodName,
                    List.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            FrameLayout thisObj = (FrameLayout)param.thisObject;
                            FrameLayout parent = (FrameLayout) thisObj.getParent();
                            if(parent != null){
                                ViewGroup grandParent = (ViewGroup) parent.getParent();
                                if(grandParent != null){
                                    grandParent.removeView(parent);
                                }
                            }
                            param.setResult(false);
                        }
                    }
            );
            XposedBridge.log("[GreenDotHide] Hooked into: "+className+"."+methodName);
        }catch(Throwable e){
            XposedBridge.log("[GreenDotHide] Ignoring hook: "+className+"."+methodName);
        }

        className = "com.android.systemui.statusbar.phone.fragment.CollapsedStatusBarFragment";
        methodName = "onSystemEventAnimationBegin";

        try{
            XposedHelpers.findAndHookMethod(
                    className,
                    lpparam.classLoader,
                    methodName,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            param.setResult(null);
                        }
                    }
            );
            XposedBridge.log("[GreenDotHide] Hooked into: "+className+"."+methodName);
        }catch(Throwable e){
            XposedBridge.log("[GreenDotHide] Ignoring hook: "+className+"."+methodName);
        }

    }

    @Override
    public void handleInitPackageResources(XC_InitPackageResources.InitPackageResourcesParam resparam) throws Throwable {
        if (!resparam.packageName.equals("com.android.systemui"))
            return;

        try{
            resparam.res.setReplacement("com.android.systemui", "bool", "config_enablePrivacyDot", false);
            XposedBridge.log("[GreenDotHide] Hooked resource: config_enablePrivacyDot");
        }catch(RuntimeException e){
            XposedBridge.log("[GreenDotHide] Failed to hook resource: config_enablePrivacyDot");
        }

    }
}
