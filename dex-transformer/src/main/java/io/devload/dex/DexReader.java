package io.devload.dex;

import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.DexFile;
import com.android.tools.smali.dexlib2.iface.Method;

import java.io.File;

/**
 * Part 1 Example: Reading DEX files with dexlib2
 *
 * This class demonstrates how to:
 * - Load a DEX file
 * - Iterate through classes
 * - Find specific classes (MainActivity)
 * - List methods
 */
public class DexReader {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Usage: java DexReader <classes.dex>");
            System.out.println();
            System.out.println("Example:");
            System.out.println("  java DexReader /path/to/classes.dex");
            return;
        }

        String dexPath = args[0];
        readDex(new File(dexPath));
    }

    public static void readDex(File dexFile) throws Exception {
        System.out.println("=".repeat(60));
        System.out.println("DEX Reader - Part 1 Example");
        System.out.println("=".repeat(60));
        System.out.println();

        // 1. Load DEX file
        System.out.println("[STEP 1] Loading DEX file...");
        DexFile dex = DexFileFactory.loadDexFile(dexFile, Opcodes.getDefault());
        System.out.println("✓ Loaded: " + dexFile.getName());
        System.out.println("✓ Total classes: " + dex.getClasses().size());
        System.out.println();

        // 2. Find MainActivity
        System.out.println("[STEP 2] Searching for MainActivity...");
        for (ClassDef classDef : dex.getClasses()) {
            String className = classDef.getType();

            if (className.contains("MainActivity")) {
                System.out.println("✓ Found: " + className);
                System.out.println("  Superclass: " + classDef.getSuperclass());
                System.out.println();

                // 3. List methods
                System.out.println("  Methods:");
                for (Method method : classDef.getMethods()) {
                    String methodSig = method.getName() +
                                     formatParameters(method) +
                                     method.getReturnType();
                    System.out.println("    - " + methodSig);
                }
                System.out.println();
            }
        }

        System.out.println("=".repeat(60));
        System.out.println("Done!");
        System.out.println("=".repeat(60));
    }

    private static String formatParameters(Method method) {
        StringBuilder sb = new StringBuilder("(");
        for (CharSequence param : method.getParameterTypes()) {
            if (sb.length() > 1) sb.append(", ");
            sb.append(param);
        }
        sb.append(")");
        return sb.toString();
    }
}
