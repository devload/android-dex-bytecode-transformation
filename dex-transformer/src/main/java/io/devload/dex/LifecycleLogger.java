package io.devload.dex;

import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.DexFile;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MethodImplementation;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.ThreeRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction21c;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction3rc;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction11n;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction11x;
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction12x;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction21c;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction23x;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction35c;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction3rc;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference;
import com.android.tools.smali.dexlib2.builder.BuilderInstruction;
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation;
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder;
import com.android.tools.smali.dexlib2.writer.io.FileDataStore;
import com.android.tools.smali.dexlib2.writer.pool.DexPool;
import io.devload.dex.detector.ComponentDetector;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.android.tools.smali.dexlib2.Opcode.CONST_STRING;
import static com.android.tools.smali.dexlib2.Opcode.INVOKE_STATIC;

/**
 * Part 4: Complete Lifecycle Logger Tool
 *
 * Automatically adds Log.d() statements to all lifecycle methods of:
 * - Activity (onCreate, onStart, onResume, onPause, onStop, onDestroy)
 * - Fragment (onAttach, onCreate, onCreateView, onStart, onResume, onPause, onStop, onDestroyView, onDetach)
 * - Service (onCreate, onStartCommand, onDestroy)
 * - ContentProvider (onCreate)
 *
 * Usage:
 *   java -jar lifecycle-logger.jar input.dex output.dex
 */
public class LifecycleLogger {

    private static final String LOG_TAG = "LifecycleLogger";

    // Lifecycle methods to instrument
    private static final Set<String> ACTIVITY_LIFECYCLE_METHODS = new HashSet<>(Arrays.asList(
        "onCreate", "onStart", "onResume", "onPause", "onStop", "onDestroy"
    ));

    private static final Set<String> FRAGMENT_LIFECYCLE_METHODS = new HashSet<>(Arrays.asList(
        "onAttach", "onCreate", "onCreateView", "onStart", "onResume",
        "onPause", "onStop", "onDestroyView", "onDetach"
    ));

    private static final Set<String> SERVICE_LIFECYCLE_METHODS = new HashSet<>(Arrays.asList(
        "onCreate", "onStartCommand", "onDestroy"
    ));

    private static final Set<String> PROVIDER_LIFECYCLE_METHODS = new HashSet<>(Arrays.asList(
        "onCreate"
    ));

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            printUsage();
            return;
        }

        String inputPath = args[0];
        String outputPath = args[1];

        System.out.println("=" .repeat(60));
        System.out.println("Lifecycle Logger - DEX Transformation Tool");
        System.out.println("=".repeat(60));
        System.out.println();

        transform(new File(inputPath), new File(outputPath));
    }

    private static void printUsage() {
        System.out.println("Lifecycle Logger - Automatically add lifecycle logging");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  java -jar lifecycle-logger.jar <input.dex> <output.dex>");
        System.out.println();
        System.out.println("Example:");
        System.out.println("  java -jar lifecycle-logger.jar classes.dex classes-logged.dex");
        System.out.println();
        System.out.println("Supported Components:");
        System.out.println("  - Activity (onCreate, onStart, onResume, ...)");
        System.out.println("  - Fragment (onAttach, onCreate, onCreateView, ...)");
        System.out.println("  - Service (onCreate, onStartCommand, onDestroy)");
        System.out.println("  - ContentProvider (onCreate)");
    }

    public static void transform(File inputDex, File outputDex) throws Exception {
        // Load DEX
        System.out.println("[STEP 1] Loading DEX file...");
        DexFile dexFile = DexFileFactory.loadDexFile(inputDex, Opcodes.getDefault());
        System.out.println("✓ Loaded: " + inputDex.getName());
        System.out.println("✓ Total classes: " + dexFile.getClasses().size());
        System.out.println();

        // Detect and transform components
        System.out.println("[STEP 2] Detecting Android components...");
        DexPool dexPool = new DexPool(Opcodes.getDefault());

        int activityCount = 0;
        int fragmentCount = 0;
        int serviceCount = 0;
        int providerCount = 0;
        int totalMethodsModified = 0;

        for (ClassDef classDef : dexFile.getClasses()) {
            boolean isComponent = false;
            Set<String> lifecycleMethods = null;

            if (ComponentDetector.isActivity(classDef)) {
                isComponent = true;
                lifecycleMethods = ACTIVITY_LIFECYCLE_METHODS;
                activityCount++;
                System.out.println("  ✓ Activity: " + getSimpleClassName(classDef.getType()));
            } else if (ComponentDetector.isFragment(classDef)) {
                isComponent = true;
                lifecycleMethods = FRAGMENT_LIFECYCLE_METHODS;
                fragmentCount++;
                System.out.println("  ✓ Fragment: " + getSimpleClassName(classDef.getType()));
            } else if (ComponentDetector.isService(classDef)) {
                isComponent = true;
                lifecycleMethods = SERVICE_LIFECYCLE_METHODS;
                serviceCount++;
                System.out.println("  ✓ Service: " + getSimpleClassName(classDef.getType()));
            } else if (ComponentDetector.isContentProvider(classDef)) {
                isComponent = true;
                lifecycleMethods = PROVIDER_LIFECYCLE_METHODS;
                providerCount++;
                System.out.println("  ✓ ContentProvider: " + getSimpleClassName(classDef.getType()));
            }

            if (isComponent && lifecycleMethods != null) {
                ClassDef modifiedClass = instrumentComponent(classDef, lifecycleMethods);
                totalMethodsModified += countModifiedMethods(classDef, lifecycleMethods);
                dexPool.internClass(modifiedClass);
            } else {
                dexPool.internClass(classDef);
            }
        }

        System.out.println();
        System.out.println("Summary:");
        System.out.println("  - Activities: " + activityCount);
        System.out.println("  - Fragments: " + fragmentCount);
        System.out.println("  - Services: " + serviceCount);
        System.out.println("  - ContentProviders: " + providerCount);
        System.out.println("  - Total methods modified: " + totalMethodsModified);
        System.out.println();

        // Write modified DEX
        System.out.println("[STEP 3] Writing modified DEX...");
        dexPool.writeTo(new FileDataStore(outputDex));
        System.out.println("✓ Output: " + outputDex.getAbsolutePath());
        System.out.println();

        System.out.println("=".repeat(60));
        System.out.println("SUCCESS!");
        System.out.println("=".repeat(60));
        System.out.println();
        System.out.println("Next steps:");
        System.out.println("  1. Replace DEX in APK");
        System.out.println("  2. Rebuild and sign APK");
        System.out.println("  3. Install: adb install app.apk");
        System.out.println("  4. Check logs: adb logcat | grep " + LOG_TAG);
    }

    private static ClassDef instrumentComponent(ClassDef classDef, Set<String> lifecycleMethods) {
        List<Method> modifiedMethods = new ArrayList<>();

        for (Method method : classDef.getMethods()) {
            if (lifecycleMethods.contains(method.getName())) {
                // Use the fixed version that works with methods that have parameters
                Method instrumented = addLifecycleLogFixed(method, classDef.getType());
                modifiedMethods.add(instrumented);
            } else {
                modifiedMethods.add(method);
            }
        }

        return new com.android.tools.smali.dexlib2.immutable.ImmutableClassDef(
            classDef.getType(),
            classDef.getAccessFlags(),
            classDef.getSuperclass(),
            classDef.getInterfaces(),
            classDef.getSourceFile(),
            classDef.getAnnotations(),
            classDef.getFields(),
            modifiedMethods
        );
    }

    private static Method addLifecycleLog(Method method, String className) {
        MethodImplementation impl = method.getImplementation();
        if (impl == null) {
            return method;
        }

        // Create Log.d() instructions
        String simpleClassName = getSimpleClassName(className);
        String message = simpleClassName + "." + method.getName() + "() START";

        // ⚠️ KNOWN ISSUE: This approach causes VerifyError for methods with parameters!
        // This is kept as a learning example. See addLifecycleLogFixed() for the working solution.
        //
        // PROBLEM: When inserting instructions at the beginning of a method, we're adding
        // new registers BEFORE the existing code. However, in Dalvik bytecode:
        //
        // 1. Method parameters are stored in the LAST registers (highest register numbers)
        // 2. Local variables use the FIRST registers (lowest register numbers)
        //
        // Example for onCreate(Bundle savedInstanceState):
        //   - Original: v0-v2 = locals, v3 = this, v4 = savedInstanceState
        //   - After adding 2 registers: v0-v4 = locals + new, v5 = this, v6 = savedInstanceState
        //   - But existing instructions still reference v3, v4 → VerifyError!
        //
        // WORKS: ContentProvider.onCreate() - no parameters (only 'this')
        // FAILS: MainActivity.onCreate(Bundle) - has parameter
        //
        // Error: "tried to get class from non-reference register v3 (type=Undefined)"
        //
        // SOLUTION NEEDED:
        // - Option 1: Insert AFTER super() call instead of at beginning
        // - Option 2: Use existing high registers without increasing count
        // - Option 3: Properly remap all register references in existing instructions
        //
        // TODO: Implement one of the solutions above for production use

        // Allocate 2 new registers for logging
        int registerCount = impl.getRegisterCount();
        int logTagRegister = registerCount;      // First new register
        int logMessageRegister = registerCount + 1;  // Second new register

        List<Instruction> logInstructions = createLogInstructions(message, logTagRegister, logMessageRegister);

        // Get existing instructions
        List<Instruction> existingInstructions = StreamSupport
            .stream(impl.getInstructions().spliterator(), false)
            .collect(Collectors.toList());

        // Insert log at beginning
        List<Instruction> newInstructions = new ArrayList<>();
        newInstructions.addAll(logInstructions);
        newInstructions.addAll(existingInstructions);

        // Create new MethodImplementation (Increase register count by 2)
        ImmutableMethodImplementation newImpl = new ImmutableMethodImplementation(
            registerCount + 2,  // ⚠️ This causes VerifyError for methods with parameters!
            newInstructions,
            impl.getTryBlocks(),
            impl.getDebugItems()
        );

        return new ImmutableMethod(
            method.getDefiningClass(),
            method.getName(),
            method.getParameters(),
            method.getReturnType(),
            method.getAccessFlags(),
            method.getAnnotations(),
            method.getHiddenApiRestrictions(),
            newImpl
        );
    }

    private static List<Instruction> createLogInstructions(String message, int tagReg, int msgReg) {
        List<Instruction> instructions = new ArrayList<>();

        // const-string vN, "LifecycleLogger"
        instructions.add(new ImmutableInstruction21c(
            CONST_STRING,
            tagReg,
            new ImmutableStringReference(LOG_TAG)
        ));

        // const-string vN+1, message
        instructions.add(new ImmutableInstruction21c(
            CONST_STRING,
            msgReg,
            new ImmutableStringReference(message)
        ));

        // invoke-static {vN, vN+1}, Landroid/util/Log;->d(...)
        instructions.add(new ImmutableInstruction35c(
            INVOKE_STATIC,
            2,
            tagReg, msgReg, 0, 0, 0,
            new ImmutableMethodReference(
                "Landroid/util/Log;",
                "d",
                List.of("Ljava/lang/String;", "Ljava/lang/String;"),
                "I"
            )
        ));

        return instructions;
    }

    /**
     * Adds lifecycle logging to lifecycle methods
     *
     * Register Allocation Strategy:
     * Dalvik register model:
     * - Total registers = registerCount
     * - Parameters occupy the LAST registers (indices: registerCount - ins to registerCount - 1)
     * - Local variables occupy the FIRST registers (indices: 0 to registerCount - ins - 1)
     *
     * When adding new registers:
     * - CASE 1 (2+ locals): Reuse existing locals - NO registerCount increase needed
     * - CASE 2-3 (0-1 locals): Would need to increase registerCount, but this requires
     *   remapping ALL instruction register operands (complex). Skip these edge cases.
     *
     * Impact:
     * - ✓ Logs most lifecycle methods (those with 2+ local variables)
     * - ⚠️ Skips methods with 0-1 locals (rare in practice)
     * - ⚠️ Better to skip than risk VerifyError from incorrect register remapping
     */
    private static Method addLifecycleLogFixed(Method method, String className) {
        MethodImplementation impl = method.getImplementation();
        if (impl == null) {
            return method;
        }

        String simpleClassName = getSimpleClassName(className);
        String message = simpleClassName + "." + method.getName() + "() START";

        int registerCount = impl.getRegisterCount();

        // Calculate how many registers are used for parameters
        // Non-static methods: 1 (this) + count of explicit parameters
        int parameterCount = 1;  // 'this' pointer is always present
        for (@SuppressWarnings("unused") var param : method.getParameters()) {
            parameterCount++;
        }

        // Local registers = v0 to v(registerCount - parameterCount - 1)
        // Parameter registers = v(registerCount - parameterCount) to v(registerCount - 1)
        int localRegisterCount = registerCount - parameterCount;

        // Determine which registers to use and whether we need to increase registerCount
        int newRegisterCount = registerCount;
        int logTagRegister, logMessageRegister;

        if (localRegisterCount >= 2) {
            // CASE 1: Have at least 2 local registers available
            // Use the HIGHEST local registers (just before parameter zone)
            // These were probably locals in the original code, safe to reuse
            logTagRegister = localRegisterCount - 2;
            logMessageRegister = localRegisterCount - 1;
            // ✅ NO NEED TO INCREASE registerCount - parameters don't shift!
        } else {
            // CASE 2 & 3: Not enough local registers (0 or 1)
            //
            // ✅ SOLUTION: Add registers AND remap existing instructions
            //
            // Strategy:
            // 1. Add 2 new local registers at v0, v1
            // 2. Shift existing parameter registers by +2
            // 3. Remap all register operands in existing instructions
            //
            // Example (onStart):
            // Old: registerCount=1, ins=1
            //   v0 = this (parameter)
            //   invoke-super {v0}
            //
            // New: registerCount=3, ins=1
            //   v0, v1 = new locals for logging
            //   v2 = this (parameter, shifted)
            //   invoke-super {v2}  ← Remapped!
            //   v0 = "LifecycleLogger"
            //   v1 = "MainActivity.onStart() START"
            //   invoke-static {v0, v1}, Log.d

            logTagRegister = 0;
            logMessageRegister = 1;
            newRegisterCount = registerCount + 2;
        }

        List<Instruction> logInstructions = createLogInstructions(
            message, logTagRegister, logMessageRegister
        );

        // Get existing instructions
        List<Instruction> existingInstructions = StreamSupport
            .stream(impl.getInstructions().spliterator(), false)
            .collect(Collectors.toList());

        // CASE 2-3: Need to remap registers
        int registersAdded = newRegisterCount - registerCount;
        if (registersAdded > 0) {
            // Remap all existing instructions to shift parameter registers
            // IMPORTANT: Only parameter registers need shifting, not locals!
            // Old parameter start index: registerCount - parameterCount
            // New parameter start index: newRegisterCount - parameterCount
            int oldParamStart = registerCount - parameterCount;
            existingInstructions = remapRegisters(existingInstructions, registersAdded, oldParamStart);
        }

        // Find safe position to insert logging code
        int insertPosition = findSafeInsertionPosition(
            existingInstructions,
            registersAdded
        );

        // Insert log instructions at safe position
        List<Instruction> newInstructions = new ArrayList<>();
        newInstructions.addAll(existingInstructions.subList(0, insertPosition));
        newInstructions.addAll(logInstructions);
        newInstructions.addAll(existingInstructions.subList(insertPosition, existingInstructions.size()));

        // ⚠️ CRITICAL: Debug items must be handled carefully when registerCount changes
        //
        // Debug items contain register indices for local variables.
        // When we add registers (CASE 2-3), debug info becomes invalid:
        // - Old: reg=0 this
        // - New (wrong): reg=2 this (because registerCount increased by 2)
        //
        // This causes VerifyError: "tried to get class from non-reference register v0"
        // because debug info says 'this' is in v2, but invoke-super still uses v0.
        //
        // Solution: Remove debug items when we add registers (CASE 2-3)
        // CASE 1 (no register addition) keeps debug items intact.
        boolean registersWereAdded = (newRegisterCount > registerCount);

        ImmutableMethodImplementation newImpl = new ImmutableMethodImplementation(
            newRegisterCount,
            newInstructions,
            impl.getTryBlocks(),
            registersWereAdded ? null : impl.getDebugItems()  // ✓ Remove debug info if registers added
        );

        return new ImmutableMethod(
            method.getDefiningClass(),
            method.getName(),
            method.getParameters(),
            method.getReturnType(),
            method.getAccessFlags(),
            method.getAnnotations(),
            method.getHiddenApiRestrictions(),
            newImpl
        );
    }

    /**
     * Find the SAFE position to insert logging code.
     *
     * The Dalvik verifier requires that when you reference a register,
     * it must have been previously initialized with an appropriate type.
     *
     * Rules:
     * 1. If no registers were added (registersAdded == 0):
     *    - Can insert anywhere, register references don't change
     *
     * 2. If registers were added (registersAdded > 0):
     *    - Must insert AFTER super() call / object initialization
     *    - This ensures parameters have been loaded and reference types are stable
     *    - Original code (before logging) has parameter references which were valid
     *      in original registerCount, still valid after we increase it
     *
     * Strategy: Find super() or constructor call, insert after it
     */
    private static int findSafeInsertionPosition(List<Instruction> instructions,
                                                  int registersAdded) {
        // If we didn't add any registers, can insert at beginning
        if (registersAdded == 0) {
            return 0;
        }

        // Registers were added - find super() call (invoke-super or invoke-direct)
        int superCallIndex = -1;
        for (int i = 0; i < instructions.size(); i++) {
            Instruction instruction = instructions.get(i);
            String opcodeName = instruction.getOpcode().name;

            if (opcodeName.startsWith("invoke-super") ||
                opcodeName.startsWith("invoke-direct")) {
                superCallIndex = i;
                break;
            }
        }

        if (superCallIndex == -1) {
            // No super() call found (e.g., ContentProvider.onCreate or interface method)
            // Insert at very beginning
            return 0;
        }

        // Found super() call - insert after it and any move-result instructions
        int insertPos = superCallIndex + 1;
        while (insertPos < instructions.size()) {
            Instruction inst = instructions.get(insertPos);
            String opcodeName = inst.getOpcode().name;

            // Skip move-result* instructions
            if (opcodeName.startsWith("move-result")) {
                insertPos++;
                continue;
            }

            // Stop at the first real instruction
            break;
        }

        return insertPos;
    }

    /**
     * Remap registers in existing instructions when we add new locals.
     *
     * When we add N new local registers at the beginning, parameter registers shift by +N.
     * ONLY parameter registers need shifting - local registers stay the same!
     *
     * Example: registerCount=1, ins=1, adding 2 registers
     * - Old: v0 = this (parameter, index >= 0)
     * - New: v0, v1 = new locals, v2 = this (parameter, index >= 2)
     * - Parameter references: v0 → v2 (shift if register >= oldParamStart)
     * - Local references: unchanged
     *
     * Strategy: Only shift registers that are >= oldParamStart (parameter zone)
     */
    private static List<Instruction> remapRegisters(List<Instruction> instructions,
                                                     int registerShift,
                                                     int oldParamStart) {
        List<Instruction> remapped = new ArrayList<>();

        for (Instruction instruction : instructions) {
            try {
                Instruction newInstruction = remapSingleInstruction(instruction, registerShift, oldParamStart);
                remapped.add(newInstruction);
            } catch (Exception e) {
                // If remapping fails for this instruction, keep original
                // (This shouldn't happen, but safer than crashing)
                System.err.println("⚠️ Failed to remap instruction: " + instruction.getOpcode().name);
                remapped.add(instruction);
            }
        }

        return remapped;
    }

    /**
     * Helper: Conditionally shift a register if it's in the parameter zone.
     * Only registers >= oldParamStart need shifting (they're parameters).
     * Registers < oldParamStart are locals and stay unchanged.
     */
    private static int shiftRegisterIfParameter(int register, int shift, int oldParamStart) {
        return (register >= oldParamStart) ? (register + shift) : register;
    }

    /**
     * Remap a single instruction's register operands.
     *
     * Handles different instruction formats by:
     * 1. Identifying the instruction type (OneRegister, TwoRegister, etc.)
     * 2. Extracting all register operands
     * 3. Adding 'shift' ONLY to parameter registers (>= oldParamStart)
     * 4. Creating a new Instruction with remapped registers
     *
     * Instruction formats handled:
     * - OneRegisterInstruction: move-result, const/4, return, etc.
     * - TwoRegisterInstruction: move, add-int, etc.
     * - ThreeRegisterInstruction: add-int/2addr, etc.
     * - FiveRegisterInstruction (35c): invoke-super, invoke-static, etc.
     * - RegisterRangeInstruction (3rc): invoke/range, etc.
     *
     * Note: Reference operands (method, type, string) are preserved unchanged.
     */
    private static Instruction remapSingleInstruction(Instruction instruction, int shift, int oldParamStart) {
        try {
            // Handle RegisterRangeInstruction first (covers invoke/range, etc.)
            // These use the 3rc format (Instruction3rc)
            if (instruction instanceof RegisterRangeInstruction) {
                RegisterRangeInstruction rangeInst = (RegisterRangeInstruction) instruction;

                // Get the starting register and count
                int startReg = rangeInst.getStartRegister();
                int regCount = rangeInst.getRegisterCount();

                // Shift only if starting register is in parameter zone
                int newStartReg = shiftRegisterIfParameter(startReg, shift, oldParamStart);

                // Check if it has a reference (method, type, etc.)
                if (rangeInst instanceof Instruction3rc) {
                    Instruction3rc inst3rc = (Instruction3rc) rangeInst;

                    return new com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction3rc(
                        rangeInst.getOpcode(),
                        regCount,
                        newStartReg,
                        inst3rc.getReference()
                    );
                } else {
                    // RegisterRange without reference (rare, but handle gracefully)
                    // Return original instruction as we can't reconstruct without proper API
                    return instruction;
                }
            }

            // Handle FiveRegisterInstruction (35c format: invoke with up to 5 registers)
            // Note: FiveRegisterInstruction only has getRegisterC/D/E/F/G
            // getRegisterA and reference come from Instruction35c interface
            if (instruction instanceof Instruction35c && instruction instanceof FiveRegisterInstruction) {
                Instruction35c inst35c = (Instruction35c) instruction;
                FiveRegisterInstruction fiveInst = (FiveRegisterInstruction) instruction;

                int count = fiveInst.getRegisterCount();
                int regC = shiftRegisterIfParameter(fiveInst.getRegisterC(), shift, oldParamStart);
                int regD = (count >= 2) ? shiftRegisterIfParameter(fiveInst.getRegisterD(), shift, oldParamStart) : fiveInst.getRegisterD();
                int regE = (count >= 3) ? shiftRegisterIfParameter(fiveInst.getRegisterE(), shift, oldParamStart) : fiveInst.getRegisterE();
                int regF = (count >= 4) ? shiftRegisterIfParameter(fiveInst.getRegisterF(), shift, oldParamStart) : fiveInst.getRegisterF();
                int regG = (count >= 5) ? shiftRegisterIfParameter(fiveInst.getRegisterG(), shift, oldParamStart) : fiveInst.getRegisterG();

                // Create immutable FiveRegisterInstruction with shifted registers
                return new com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction35c(
                    inst35c.getOpcode(),
                    count,
                    regC, regD, regE, regF, regG,
                    inst35c.getReference()
                );
            }

            // Handle ThreeRegisterInstruction
            if (instruction instanceof ThreeRegisterInstruction) {
                ThreeRegisterInstruction threeInst = (ThreeRegisterInstruction) instruction;

                int regA = shiftRegisterIfParameter(threeInst.getRegisterA(), shift, oldParamStart);
                int regB = shiftRegisterIfParameter(threeInst.getRegisterB(), shift, oldParamStart);
                int regC = shiftRegisterIfParameter(threeInst.getRegisterC(), shift, oldParamStart);

                return new ImmutableInstruction23x(
                    threeInst.getOpcode(),
                    regA, regB, regC
                );
            }

            // Handle TwoRegisterInstruction
            if (instruction instanceof TwoRegisterInstruction) {
                TwoRegisterInstruction twoInst = (TwoRegisterInstruction) instruction;

                int regA = shiftRegisterIfParameter(twoInst.getRegisterA(), shift, oldParamStart);
                int regB = shiftRegisterIfParameter(twoInst.getRegisterB(), shift, oldParamStart);

                return new ImmutableInstruction12x(
                    twoInst.getOpcode(),
                    regA, regB
                );
            }

            // For instructions with references (21c format)
            // MUST check BEFORE OneRegisterInstruction because Instruction21c extends OneRegisterInstruction!
            if (instruction instanceof Instruction21c) {
                Instruction21c inst21c = (Instruction21c) instruction;

                // Instruction21c extends OneRegisterInstruction
                int reg = shiftRegisterIfParameter(((OneRegisterInstruction) inst21c).getRegisterA(), shift, oldParamStart);

                return new ImmutableInstruction21c(
                    inst21c.getOpcode(),
                    reg,
                    inst21c.getReference()
                );
            }

            // Handle OneRegisterInstruction (11x or 11n format)
            // This includes: return, move-result, const/4, etc.
            if (instruction instanceof OneRegisterInstruction) {
                OneRegisterInstruction oneInst = (OneRegisterInstruction) instruction;

                int reg = shiftRegisterIfParameter(oneInst.getRegisterA(), shift, oldParamStart);

                // Check if it's a narrow literal instruction (11n format like const/4)
                if (instruction instanceof NarrowLiteralInstruction) {
                    NarrowLiteralInstruction narrowInst = (NarrowLiteralInstruction) instruction;
                    return new ImmutableInstruction11n(
                        narrowInst.getOpcode(),
                        reg,
                        narrowInst.getNarrowLiteral()
                    );
                }

                // Otherwise it's 11x format (return, move-result, etc.)
                return new ImmutableInstruction11x(
                    oneInst.getOpcode(),
                    reg
                );
            }

            // For all other instruction types, return as-is
            // (These typically don't have register operands or are rare)
            return instruction;

        } catch (Exception e) {
            // On any error, return the original instruction to prevent crashes
            System.err.println("⚠️ Exception remapping instruction " + instruction.getOpcode().name + ": " + e.getMessage());
            return instruction;
        }
    }

    private static String getSimpleClassName(String fullClassName) {
        // Lcom/example/MainActivity; -> MainActivity
        String withoutPrefix = fullClassName.substring(1, fullClassName.length() - 1);
        int lastSlash = withoutPrefix.lastIndexOf('/');
        return lastSlash >= 0 ? withoutPrefix.substring(lastSlash + 1) : withoutPrefix;
    }

    private static int countModifiedMethods(ClassDef classDef, Set<String> lifecycleMethods) {
        int count = 0;
        for (Method method : classDef.getMethods()) {
            if (lifecycleMethods.contains(method.getName()) && method.getImplementation() != null) {
                count++;
            }
        }
        return count;
    }

}
