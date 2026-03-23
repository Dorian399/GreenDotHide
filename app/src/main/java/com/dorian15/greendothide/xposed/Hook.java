package com.dorian15.greendothide.xposed;

import android.graphics.Rect;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.util.List;


import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_InitPackageResources;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class Hook implements IXposedHookLoadPackage, IXposedHookInitPackageResources {
    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {

        // self hook to show module as enabled
        if(lpparam.packageName.equals("com.dorian15.greendothide"))
            XposedHelpers.findAndHookMethod("com.dorian15.greendothide.MainActivity", lpparam.classLoader, "isModuleEnabled",
                    XC_MethodReplacement.returnConstant(true));

        if (!lpparam.packageName.equals("com.android.systemui"))
            return;

        XSharedPreferences sharedPrefs = new XSharedPreferences("com.dorian15.greendothide","prefs");
        sharedPrefs.reload();
        boolean hideLocation = sharedPrefs.getBoolean("disable_location_indicator",true);
        boolean hideMic = sharedPrefs.getBoolean("disable_microphone_indicator",true);
        boolean hideCamera = sharedPrefs.getBoolean("disable_camera_indicator",true);
        boolean hideMedia = sharedPrefs.getBoolean("disable_media_projection_indicator",true);

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
                            Bundle bundleArg = (Bundle)param.args[2];
                            int[] dotType = bundleArg.getIntArray("key_prompt_type");

                            if(hideCamera)
                                dotType[0]=0;
                            if(hideMic)
                                dotType[1]=0;
                            if(hideLocation)
                                dotType[2]=0;
                            if(hideMedia)
                                dotType[3]=0;

                            bundleArg.putIntArray("key_prompt_type",dotType);

                            boolean isEmpty=true;
                            for(int i=0; i<dotType.length;i++){
                                if(dotType[i]==1){
                                    isEmpty=false;
                                    break;
                                }
                            }

                            if(isEmpty)
                                param.setResult(null);
                        }
                    }
            );
            XposedBridge.log("[GreenDotHide] Hooked into: "+className+"."+methodName);
        }catch(Throwable e){
            XposedBridge.log("[GreenDotHide] Ignoring hook: "+className+"."+methodName);
        }

        // AOSP based SystemUI
//        className = "com.android.systemui.privacy.PrivacyConfig";
//        methodName = "isLocationEnabled";
//        try{
//            XposedHelpers.findAndHookMethod(
//                    className,
//                    lpparam.classLoader,
//                    methodName,
//                    new XC_MethodHook() {
//                        @Override
//                        protected void beforeHookedMethod(MethodHookParam param) {
//                            if(hideLocation)
//                                param.setResult(false);
//                        }
//                    }
//            );
//            XposedBridge.log("[GreenDotHide] Hooked into: "+className+"."+methodName);
//        }catch(Throwable e){
//            XposedBridge.log("[GreenDotHide] Ignoring hook: "+className+"."+methodName);
//        }
//
//        methodName = "isMicCameraEnabled";
//        try{
//            XposedHelpers.findAndHookMethod(
//                    className,
//                    lpparam.classLoader,
//                    methodName,
//                    new XC_MethodHook() {
//                        @Override
//                        protected void beforeHookedMethod(MethodHookParam param) {
//                            if (hideMic && hideCamera)
//                                param.setResult(false);
//                        }
//                    }
//            );
//            XposedBridge.log("[GreenDotHide] Hooked into: "+className+"."+methodName);
//        }catch(Throwable e){
//            XposedBridge.log("[GreenDotHide] Ignoring hook: "+className+"."+methodName);
//        }
//
//        methodName = "isMediaProjectionEnabled";
//        try{
//            XposedHelpers.findAndHookMethod(
//                    className,
//                    lpparam.classLoader,
//                    methodName,
//                    new XC_MethodHook() {
//                        @Override
//                        protected void beforeHookedMethod(MethodHookParam param) {
//                            if(hideMedia)
//                                param.setResult(false);
//                        }
//                    }
//            );
//            XposedBridge.log("[GreenDotHide] Hooked into: "+className+"."+methodName);
//        }catch(Throwable e){
//            XposedBridge.log("[GreenDotHide] Ignoring hook: "+className+"."+methodName);
//        }

        className = "com.android.systemui.privacy.PrivacyItemController";
        methodName = "updatePrivacyList";
        try{
            XposedHelpers.findAndHookMethod(
                    className,
                    lpparam.classLoader,
                    methodName,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            List<?> privacyList = (List<?>) XposedHelpers.getObjectField(param.thisObject, "privacyList");
                            privacyList.removeIf(privacyItem -> {
                                String privacyType = XposedHelpers.getObjectField(privacyItem, "privacyType").toString();
                                return (privacyType.equals("TYPE_MICROPHONE") && hideMic) ||
                                        (privacyType.equals("TYPE_CAMERA") && hideCamera) ||
                                        (privacyType.equals("TYPE_LOCATION") && hideLocation) ||
                                        (privacyType.equals("TYPE_MEDIA_PROJECTION") && hideMedia);
                            });
                            XposedHelpers.setObjectField(param.thisObject,"privacyList",privacyList);
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
