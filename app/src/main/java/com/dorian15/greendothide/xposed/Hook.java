package com.dorian15.greendothide.xposed;

import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class Hook implements IXposedHookLoadPackage {

    private static final String MIUI_PRIVACY_CONTROLLER = "com.android.systemui.statusbar.privacy.MiuiPrivacyControllerImpl";
    private static final String PRIVACY_ITEM_CONTROLLER = "com.android.systemui.privacy.PrivacyItemController";
    private static final String PRIVACY_CONFIG = "com.android.systemui.privacy.PrivacyConfig";

    private boolean hideLocation = true;
    private boolean hideMic = true;
    private boolean hideCamera = true;
    private boolean hideMedia = true;

    private boolean shouldHide(String privacyType) {
        return (privacyType.equals("TYPE_MICROPHONE") && hideMic)
                || (privacyType.equals("TYPE_CAMERA") && hideCamera)
                || (privacyType.equals("TYPE_LOCATION") && hideLocation)
                || (privacyType.equals("TYPE_MEDIA_PROJECTION") && hideMedia);
    }

    /**
     * Builds a NEW list without hidden items. The input list is never mutated:
     * SystemUI components keep references to the same instance, and in-place
     * removal corrupts their state (root cause of SystemUI crashes).
     */
    private List<Object> filterPrivacyList(List<?> list) {
        List<Object> filtered = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item == null)
                continue;
            try {
                Object type = XposedHelpers.getObjectField(item, "privacyType");
                if (type != null && shouldHide(type.toString()))
                    continue;
            } catch (Throwable t) {
                XposedBridge.log("[GreenDotHide] " + t);
            }
            filtered.add(item);
        }
        return filtered;
    }

    /** Swaps the controller's cached privacyList for a filtered copy; never mutates the original. */
    private void filterControllerList(Object controller) {
        try {
            List<?> current = (List<?>) XposedHelpers.getObjectField(controller, "privacyList");
            if (current == null)
                return;
            List<Object> filtered = filterPrivacyList(current);
            if (filtered.size() != current.size())
                XposedHelpers.setObjectField(controller, "privacyList", filtered);
        } catch (Throwable t) {
            XposedBridge.log("[GreenDotHide] " + t);
        }
    }

    private void safeHook(LoadPackageParam lpparam, String cls, String method, XC_MethodHook cb, Object... paramTypes) {
        Object[] args = new Object[paramTypes.length + 1];
        System.arraycopy(paramTypes, 0, args, 0, paramTypes.length);
        args[paramTypes.length] = cb;
        try {
            XposedHelpers.findAndHookMethod(cls, lpparam.classLoader, method, args);
            XposedBridge.log("[GreenDotHide] Hooked into: " + cls + "." + method);
        } catch (Throwable e) {
            XposedBridge.log("[GreenDotHide] Ignoring hook: " + cls + "." + method);
        }
    }

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {

        // self hook to show module as enabled
        if (lpparam.packageName.equals("com.dorian15.greendothide"))
            XposedHelpers.findAndHookMethod("com.dorian15.greendothide.MainActivity", lpparam.classLoader, "isModuleEnabled",
                    XC_MethodReplacement.returnConstant(true));

        if (!lpparam.packageName.equals("com.android.systemui"))
            return;

        XSharedPreferences prefs = new XSharedPreferences("com.dorian15.greendothide", "prefs");
        prefs.reload();
        hideLocation = prefs.getBoolean("disable_location_indicator", true);
        hideMic = prefs.getBoolean("disable_microphone_indicator", true);
        hideCamera = prefs.getBoolean("disable_camera_indicator", true);
        hideMedia = prefs.getBoolean("disable_media_projection_indicator", true);

        // MIUI/HyperOS: entry point of privacy item updates; hand MIUI a filtered
        // copy instead of the original list, and skip updates left fully hidden.
        safeHook(lpparam, MIUI_PRIVACY_CONTROLLER, "onPrivacyItemsChanged", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                List<?> incoming = (List<?>) param.args[0];
                if (incoming == null)
                    return;
                List<Object> filtered = filterPrivacyList(incoming);
                if (filtered.isEmpty())
                    param.setResult(null);
                else
                    param.args[0] = filtered;
            }
        }, List.class);

        // HyperOS/MIUI status path; zero out hidden prompt types and skip the
        // original when nothing remains, so the pre-rendered dot RemoteViews are
        // never applied to the status bar.
        safeHook(lpparam, MIUI_PRIVACY_CONTROLLER, "setStatus", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                Bundle bundleArg = (Bundle) param.args[2];
                int[] dotType = bundleArg.getIntArray("key_prompt_type");
                if (dotType == null || dotType.length == 0)
                    return;
                boolean[] hide = {hideCamera, hideMic, hideLocation, hideMedia};
                boolean isEmpty = true;
                for (int i = 0; i < Math.min(dotType.length, hide.length); i++) {
                    if (hide[i])
                        dotType[i] = 0;
                    if (dotType[i] != 0)
                        isEmpty = false;
                }
                bundleArg.putIntArray("key_prompt_type", dotType);
                if (isEmpty)
                    param.setResult(null);
            }
        }, int.class, String.class, Bundle.class);

        // AOSP based SystemUI: kill indicators at the config level.
        Object[][] configSwitches = {
                {"isLocationEnabled", hideLocation},
                {"isMicCameraEnabled", hideMic && hideCamera},
                {"isMediaProjectionEnabled", hideMedia},
        };
        for (Object[] sw : configSwitches) {
            boolean enabled = (Boolean) sw[1];
            safeHook(lpparam, PRIVACY_CONFIG, (String) sw[0], new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (enabled)
                        param.setResult(false);
                }
            });
        }

        // Android 12-13
        safeHook(lpparam, PRIVACY_ITEM_CONTROLLER, "updatePrivacyList", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                filterControllerList(param.thisObject);
            }
        });

        // Android 15-16
        safeHook(lpparam, PRIVACY_ITEM_CONTROLLER,
                "getPrivacyList$frameworks__base__packages__SystemUI__android_common__SystemUI_core",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Object result = param.getResult();
                        if (result instanceof List)
                            param.setResult(filterPrivacyList((List<?>) result));
                    }
                });

        // Android 14: swap the cached list AFTER the original runnable completes;
        // the shared list object is never modified.
        safeHook(lpparam, PRIVACY_ITEM_CONTROLLER + "$updateListAndNotifyChanges$1", "run", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                filterControllerList(XposedHelpers.getObjectField(param.thisObject, "this$0"));
            }
        });
    }
}
