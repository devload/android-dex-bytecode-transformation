package io.devload.dex;

import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.DexFile;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MethodImplementation;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction21c;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction35c;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference;
import com.android.tools.smali.dexlib2.writer.io.FileDataStore;
import com.android.tools.smali.dexlib2.writer.pool.DexPool;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.android.tools.smali.dexlib2.Opcode.CONST_STRING;
import static com.android.tools.smali.dexlib2.Opcode.INVOKE_STATIC;

/**
 * Part 2 Example: Hello World DEX Transformation
 *
 * This class demonstrates:
 * - Finding MainActivity.onCreate()
 * - Creating Log.d() instructions
 * - Inserting instructions into method
 * - Writing modified DEX file
 */
public class HelloWorldTransformer {

    private static final String LOG_TAG = "HELLO_DEX";

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: java HelloWorldTransformer <input.dex> <output.dex>");
            System.out.println();
            System.out.println("Example:");
            System.out.println("  java HelloWorldTransformer classes.dex classes-modified.dex");
            return;
        }

        String inputPath = args[0];
        String outputPath = args[1];

        transform(new File(inputPath), new File(outputPath));
    }

    public static void transform(File inputDex, File outputDex) throws Exception {
        System.out.println("=".repeat(60));
        System.out.println("Hello World Transformer - Part 2 Example");
        System.out.println("=".repeat(60));
        System.out.println();

        // Load DEX
        System.out.println("[STEP 1] Loading DEX file...");
        DexFile dexFile = DexFileFactory.loadDexFile(inputDex, Opcodes.getDefault());
        System.out.println("✓ Loaded: " + inputDex.getName());
        System.out.println();

        // Transform
        System.out.println("[STEP 2] Transforming MainActivity.onCreate()...");
        DexPool dexPool = new DexPool(Opcodes.getDefault());

        int modifiedCount = 0;
        for (ClassDef classDef : dexFile.getClasses()) {
            if (classDef.getType().contains("MainActivity")) {
                System.out.println("✓ Found MainActivity: " + classDef.getType());

                // Transform methods
                List<Method> modifiedMethods = new ArrayList<>();
                for (Method method : classDef.getMethods()) {
                    if (method.getName().equals("onCreate")) {
                        System.out.println("  ✓ Found onCreate() method");
                        Method transformed = addLogToMethod(method, classDef.getType());
                        modifiedMethods.add(transformed);
                        modifiedCount++;
                    } else {
                        modifiedMethods.add(method);
                    }
                }

                // Create new ClassDef with modified methods
                ClassDef modifiedClass = new com.android.tools.smali.dexlib2.immutable.ImmutableClassDef(
                    classDef.getType(),
                    classDef.getAccessFlags(),
                    classDef.getSuperclass(),
                    classDef.getInterfaces(),
                    classDef.getSourceFile(),
                    classDef.getAnnotations(),
                    classDef.getFields(),
                    modifiedMethods
                );

                dexPool.internClass(modifiedClass);
            } else {
                dexPool.internClass(classDef);
            }
        }

        System.out.println("✓ Modified " + modifiedCount + " method(s)");
        System.out.println();

        // Write DEX
        System.out.println("[STEP 3] Writing modified DEX...");
        dexPool.writeTo(new FileDataStore(outputDex));
        System.out.println("✓ Output: " + outputDex.getAbsolutePath());
        System.out.println();

        System.out.println("=".repeat(60));
        System.out.println("SUCCESS! Modified DEX file created.");
        System.out.println("=".repeat(60));
    }

    private static Method addLogToMethod(Method method, String className) {
        MethodImplementation impl = method.getImplementation();
        if (impl == null) {
            return method;
        }

        // ⚠️ WARNING: This simplified example works ONLY for methods without parameters!
        //
        // This code demonstrates basic DEX transformation but has a limitation:
        // - WORKS: Methods with no parameters (only 'this')
        // - FAILS: Methods with parameters (e.g., onCreate(Bundle))
        //
        // Reason: Adding new registers shifts the parameter register positions,
        // causing VerifyError. See LifecycleLogger.java for detailed explanation.
        //
        // For production use, implement proper register remapping or insert
        // instructions after super() call instead of at the beginning.

        // Allocate 2 new registers for logging
        int registerCount = impl.getRegisterCount();
        int logTagRegister = registerCount;
        int logMessageRegister = registerCount + 1;

        // Create Log.d() instructions
        List<Instruction> logInstructions = createLogInstructions(className, method.getName(), logTagRegister, logMessageRegister);

        // Get existing instructions
        List<Instruction> existingInstructions = StreamSupport
            .stream(impl.getInstructions().spliterator(), false)
            .collect(Collectors.toList());

        // Combine: Log instructions + existing instructions
        List<Instruction> newInstructions = new ArrayList<>();
        newInstructions.addAll(logInstructions);
        newInstructions.addAll(existingInstructions);

        System.out.println("    Added " + logInstructions.size() + " instructions");

        // Create new MethodImplementation
        ImmutableMethodImplementation newImpl = new ImmutableMethodImplementation(
            registerCount + 2,  // ⚠️ This causes VerifyError for methods with parameters!
            newInstructions,
            impl.getTryBlocks(),
            impl.getDebugItems()
        );

        // Create new Method
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

    private static List<Instruction> createLogInstructions(String className, String methodName, int tagReg, int msgReg) {
        List<Instruction> instructions = new ArrayList<>();

        String message = className + "." + methodName + "() called";

        // const-string vN, "HELLO_DEX"
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
            2,  // register count
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
}
