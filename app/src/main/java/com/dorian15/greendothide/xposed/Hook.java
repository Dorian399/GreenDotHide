package com.dorian15.greendothide.xposed;

import android.os.Bundle;
import android.widget.RemoteViews;

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
    private static final String PROMPT_STATE = "com.dorian15.greendothide.promptState";
    private static final String UPDATE_PROMPT = "action_update_prompt";
    private static final String UPDATE_TYPES = "action_control_center_update_prompt";

    private boolean hideLocation = true;
    private boolean hideMic = true;
    private boolean hideCamera = true;
    private boolean hideMedia = true;

    static int[] filterPromptTypes(int[] types, boolean[] hidden) {
        if (types == null)
            return null;
        int[] filtered = types.clone();
        for (int i = 0; i < Math.min(filtered.length, hidden.length); i++)
            if (hidden[i])
                filtered[i] = 0;
        return filtered;
    }

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
            XposedBridge.log(e);
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
        // copy, including empty updates so previously visible items get cleared.
        safeHook(lpparam, MIUI_PRIVACY_CONTROLLER, "onPrivacyItemsChanged", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                List<?> incoming = (List<?>) param.args[0];
                if (incoming == null)
                    return;
                param.args[0] = filterPrivacyList(incoming);
            }
        }, List.class);

        // MIUI can send RemoteViews and types separately, in either order. Keep
        // a controller-local copy so hiding one half cannot lose the other.
        safeHook(lpparam, MIUI_PRIVACY_CONTROLLER, "setStatus", new XC_MethodHook() {
            @Override
            @SuppressWarnings("unchecked")
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                String action = (String) param.args[1];
                if (!UPDATE_PROMPT.equals(action) && !UPDATE_TYPES.equals(action))
                    return;
                MiuiPromptState<Bundle> state = (MiuiPromptState<Bundle>)
                        XposedHelpers.getAdditionalInstanceField(param.thisObject, PROMPT_STATE);
                int status = (Integer) param.args[0];
                if (status == 0) {
                    if (state != null)
                        state.clear();
                    return;
                }
                Bundle bundleArg = (Bundle) param.args[2];
                if (bundleArg == null || !(hideCamera || hideMic || hideLocation || hideMedia))
                    return;
                boolean viewUpdate = UPDATE_PROMPT.equals(action);
                // Match the original method's no-op cases before caching input.
                if (viewUpdate) {
                    int priority = bundleArg.getInt("key_status_bar_priority", -1);
                    if (priority < 0 || priority > 3
                            || !(bundleArg.getParcelable("key_status_bar_mini_state") instanceof RemoteViews)
                            || !(bundleArg.getParcelable("key_status_bar_home_state") instanceof RemoteViews))
                        return;
                } else if (bundleArg.getIntArray("key_prompt_type") == null) {
                    return;
                }
                boolean[] hide = {hideCamera, hideMic, hideLocation, hideMedia};
                int[] filtered = filterPromptTypes(bundleArg.getIntArray("key_prompt_type"), hide);
                Bundle copy = new Bundle(bundleArg);
                if (filtered != null)
                    copy.putIntArray("key_prompt_type", filtered);
                param.args[2] = copy;
                if (state == null) {
                    state = new MiuiPromptState<>(hideCamera && hideMic && hideLocation && hideMedia);
                    XposedHelpers.setAdditionalInstanceField(param.thisObject, PROMPT_STATE, state);
                }
                boolean hidden = viewUpdate
                        ? state.onViews(status, copy, filtered)
                        : state.onTypes(filtered);
                if (hidden) {
                    // Let MIUI clear views, pending intents and queued visibility updates.
                    param.args[0] = 0;
                } else if (!viewUpdate) {
                    Bundle views = state.takeViewsToRestore();
                    if (views != null) {
                        // Bypass this hook. The current type update then finishes
                        // the pair before MIUI's queued visibility handler runs.
                        XposedBridge.invokeOriginalMethod(param.method, param.thisObject,
                                new Object[]{state.getViewStatus(), UPDATE_PROMPT, new Bundle(views)});
                    }
                }
            }
        }, int.class, String.class, Bundle.class);

        // Android 14 MIUI also calls this while only the RemoteViews half has
        // arrived. Preserve its normal cache update without dereferencing null.
        safeHook(lpparam, "com.android.systemui.statusbar.privacy.MiuiPrivacyFlaresDotController",
                "updateLastMiuiPromptInfo", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (param.args[1] == null)
                            param.args[1] = new int[4];
                    }
                }, RemoteViews.class, int[].class);

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
