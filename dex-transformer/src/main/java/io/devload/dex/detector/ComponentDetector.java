package io.devload.dex.detector;

import com.android.tools.smali.dexlib2.iface.ClassDef;

/**
 * Detects Android components by analyzing superclass hierarchy
 *
 * Supports:
 * - Activity
 * - Fragment (androidx and support library)
 * - Service
 * - ContentProvider
 * - BroadcastReceiver
 */
public class ComponentDetector {

    /**
     * Check if class is an Activity
     */
    public static boolean isActivity(ClassDef classDef) {
        String superclass = classDef.getSuperclass();
        if (superclass == null) {
            return false;
        }

        return superclass.contains("Activity") &&
               !superclass.contains("Fragment");  // Exclude FragmentActivity check for now
    }

    /**
     * Check if class is a Fragment
     */
    public static boolean isFragment(ClassDef classDef) {
        String superclass = classDef.getSuperclass();
        if (superclass == null) {
            return false;
        }

        return superclass.contains("Fragment") &&
               !superclass.contains("Activity");  // Exclude FragmentActivity
    }

    /**
     * Check if class is a Service
     */
    public static boolean isService(ClassDef classDef) {
        String superclass = classDef.getSuperclass();
        if (superclass == null) {
            return false;
        }

        return superclass.contains("Service");
    }

    /**
     * Check if class is a ContentProvider
     */
    public static boolean isContentProvider(ClassDef classDef) {
        String superclass = classDef.getSuperclass();
        if (superclass == null) {
            return false;
        }

        return superclass.contains("ContentProvider");
    }

    /**
     * Check if class is a BroadcastReceiver
     */
    public static boolean isBroadcastReceiver(ClassDef classDef) {
        String superclass = classDef.getSuperclass();
        if (superclass == null) {
            return false;
        }

        return superclass.contains("BroadcastReceiver");
    }

    /**
     * Get component type name for logging
     */
    public static String getComponentType(ClassDef classDef) {
        if (isActivity(classDef)) return "Activity";
        if (isFragment(classDef)) return "Fragment";
        if (isService(classDef)) return "Service";
        if (isContentProvider(classDef)) return "ContentProvider";
        if (isBroadcastReceiver(classDef)) return "BroadcastReceiver";
        return "Unknown";
    }
}
