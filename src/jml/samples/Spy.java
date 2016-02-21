package jml.samples;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.lang.annotation.Annotation;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import jml.HijackGetArg;
import jml.HijackGetField;
import jml.HijackMethod;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;

import sun.misc.SharedSecrets;
import sun.misc.Signal;
import sun.misc.SignalHandler;

import com.sun.tools.attach.VirtualMachine;

/** Spy */
public class Spy implements ClassFileTransformer, SignalHandler
{

    /** singleton */
    public final static Spy instance = new Spy();
    private final static File temporaryDir = new File(System.getProperty("java.io.tmpdir"));
    private static boolean debug = false;
    private final Map<String, Method> hijackedMethods = new HashMap<String, Method>();
    private Signal signal = null;
    private boolean signalInvoke = false;
    private SignalHandler signalHandler = null;

    private static String getPID()
    {
        return ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
    }

    private static JarURLConnection getAgentUrlConnection() throws IOException
    {
        return ((JarURLConnection)Spy.class.getResource(Spy.class.getSimpleName() + ".class").openConnection());
    }

    private Spy()
    {
        new ClassHijacker("empty");
        for (Method m : getClass().getMethods())
        {
            HijackMethod ha = m.getAnnotation(HijackMethod.class);
            if (ha != null)
                hijackedMethods.put(ha.method(), m);
        }
    }

    private void init(String[] agentArgs, Instrumentation inst)
        throws IOException, ClassNotFoundException, UnmodifiableClassException
    {
        signal = new Signal("INT");
        for (String agentArg : agentArgs)
        {
            if (agentArg.startsWith("intercept="))
            {
                signal = new Signal(agentArg.substring("intercept=".length()).trim());
                signalInvoke = true;
            }
            if (agentArg.equals("signal="))
            {
                signal = new Signal(agentArg.substring("signal=".length()).trim());
                signalInvoke = false;
            }
        }
        if (signal.getName().equals("INT"))
        {
            Runtime.getRuntime().addShutdownHook(new Thread()
            {
                @Override
                public void run()
                {
                    handle(signal);
                }
            });
        }
        else
        {
            signalHandler = Signal.handle(signal, this);
        }

        ArrayList<ClassDefinition> classDefs = new ArrayList<ClassDefinition>();
        findOverloadedClasses(classDefs, inst.getAllLoadedClasses());
        inst.redefineClasses(classDefs.toArray(new ClassDefinition[0]));
        inst.addTransformer(this);

    }

    private <RESULT extends Collection<ClassDefinition>> RESULT findOverloadedClasses(RESULT result, Class<?>[] classes)
        throws IOException
    {
        for (Class<?> clazz : classes)
        {
            String classResName = clazz.getName().replace('.', '/') + ".class";
            ClassLoader classLoader = clazz.getClassLoader();
            if (classLoader == null)
            {
                classLoader = ClassLoader.getSystemClassLoader();
            }
            InputStream is = classLoader.getResourceAsStream(classResName);
            if (is == null)
            {
                continue;
            }
            byte[] classBytes = sun.misc.IOUtils.readFully(is, -1, false);
            classBytes = new ClassHijacker(Type.getInternalName(clazz)).process(classBytes);
            if (classBytes != null)
            {
                result.add(new ClassDefinition(clazz, classBytes));
            }
        }
        return result;
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
        ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException
    {
        return new ClassHijacker(className).process(classfileBuffer);
    }


    @Override
    public void handle(Signal paramSignal)
    {
        try
        {
            File filePath = new File(temporaryDir, getClass().getSimpleName() + "." + getPID());
            StringBuilder sb = dump(new StringBuilder());
            FileOutputStream fos = new FileOutputStream(filePath);
            try
            {
                fos.write(sb.toString().getBytes());
            }
            finally
            {
                fos.close();
            }
        }
        catch (IOException ioe)
        {
            ioe.printStackTrace();
        }
        finally
        {
            if (signalHandler != null && signalInvoke)
            {
                signalHandler.handle(paramSignal);
            }
        }
    }

    /**
     * @param agentArgs
     *            agent arguments
     * @param inst
     *            instrumentation tools
     * @throws Exception
     *             possible exception
     */
    public static void premain(String agentArgs, Instrumentation inst) throws Exception
    {
        {
            inst.appendToBootstrapClassLoaderSearch(getAgentUrlConnection().getJarFile());
        }
        System.out.append(ManagementFactory.getRuntimeMXBean().getName()).append("\n");
        instance.init(agentArgs == null ? new String[0] : agentArgs.split(";"), inst);
    }

    /**
     * @param agentArgs
     *            agent arguments
     * @param inst
     *            instrumentation tools
     * @throws Exception
     *             possible exception
     */
    public static void agentmain(String agentArgs, Instrumentation inst) throws Exception
    {
        premain(agentArgs, inst);
    }

    /**
     * load dynamically the agent without using -javaagent:${resource_loc:/jvmagent/jvmagent.jar}
     *
     * @param args
     *            the arguments
     * @throws Exception
     *             possible exception
     */
    public static void main(String[] args) throws Exception
    {
        StringBuilder agentArgs = new StringBuilder();
        String agentArgsSet = "";
        File agentFile = new File(getAgentUrlConnection().getJarFileURL().toURI());
        for (int i = 1; i < args.length; i++)
        {
            agentArgs.append(agentArgsSet).append(args[i]);
            agentArgsSet = ";";
        }
        System.out.append("dynamically loading javaagent ").append(agentFile.getAbsolutePath()).append("\n");
        VirtualMachine vm = VirtualMachine.attach(args[0]);
        try
        {
            vm.loadAgent(agentFile.getAbsolutePath(), agentArgs.toString());
        }
        finally
        {
            vm.detach();
        }
    }

    class ClassHijacker extends ClassVisitor
    {
        private final String className;
        private boolean processed = false;

        ClassHijacker(String className)
        {
            super(Opcodes.ASM4, new ClassWriter(0));
            this.className = className;
        }

        byte[] process(byte[] classfileBuffer)
        {
            new ClassReader(classfileBuffer).accept(this, 0);
            if (!processed)
                return null;

            byte[] classArray = ((ClassWriter)cv).toByteArray();
            if (debug)
            {
                TraceClassVisitor traces = new TraceClassVisitor(this, new Textifier(), new PrintWriter(System.out));
                new ClassReader(classArray).accept(traces, 0);
            }
            return classArray;
        }

        @Override
        public MethodVisitor visitMethod(
            final int access, final String methodName, final String desc,
            final String signature, final String[] exceptions)
        {
            final MethodVisitor methodVisitor = super.visitMethod(access, methodName, desc, signature, exceptions);
            final Method method = hijackedMethods.get(className + '/' + methodName + desc);

            if (method == null)
            {
                return methodVisitor;
            }
            System.out.println("Modified method: " + className + '/' + methodName + desc);

            processed = true;
            return new AdviceAdapter(Opcodes.ASM4, methodVisitor, access, methodName, desc)
            {
                private final Label timeVarStart = new Label();
                private final Label timeVarEnd = new Label();
                private int size = 0;

                @Override
                protected void onMethodEnter()
                {
                    visitLabel(timeVarStart);

                    super.visitFieldInsn(GETSTATIC, Type.getInternalName(Spy.class), "instance", Type.getDescriptor(Spy.class));

                    Class<?>[] paramClasses = method.getParameterTypes();
                    Type[] paramTypes = new Type[paramClasses.length];
                    for (int i = 0; i < paramTypes.length; i++)
                    {
                        paramTypes[i] = Type.getType(paramClasses[i]);
                        size += paramTypes[i].getSize();

                        for (Annotation ann : method.getParameterAnnotations()[i])
                        {
                            if (ann instanceof HijackGetField)
                            {
                                super.loadThis();
                                super.visitFieldInsn(GETFIELD, className, ((HijackGetField)ann).field(), Type.getDescriptor(paramClasses[i]));
                                break;
                            }
                            if (ann instanceof HijackGetArg)
                            {
                                super.visitVarInsn(paramTypes[i].getOpcode(ILOAD),((HijackGetArg)ann).position());
                                // this method is buggy with constructor
                                // super.loadArg(((HijackGetArg)ann).position());
                                break;
                            }
                        }
                    }

                    super.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(Spy.class), method.getName(),
                        Type.getMethodDescriptor(Type.VOID_TYPE, paramTypes));
                }

                @Override
                public void visitMaxs(int stack, int locals)
                {
                    visitLabel(timeVarEnd);
                    super.visitMaxs(Math.max(size, stack), locals);
                }

            };
        }

    }


    private final Map<Object, Throwable> openedStreams = new HashMap<Object, Throwable>();

    private <APPENDABLE extends Appendable> APPENDABLE dump(APPENDABLE app) throws IOException
    {
        if (openedStreams.size() == 0)
        {
            app.append("No file opened");
            return app;
        }
        for (Throwable t : openedStreams.values())
        {
            int depth = SharedSecrets.getJavaLangAccess().getStackTraceDepth(t);
            app.append(t.getMessage()).append(" opened by ");
            for (int j = 0; j < depth; j++)
            {
                StackTraceElement ste = SharedSecrets.getJavaLangAccess().getStackTraceElement(t, j);
                app.append("[").append(ste.toString()).append("]");
            }
            app.append("\n");
        }
        return app;
    }

    /**
     * @param that
     *            a file input stream
     * @param file
     *            a file
     */
    @HijackMethod(method = "java/io/FileInputStream/<init>(Ljava/io/File;)V")
    public void fileInputOpen(
        @HijackGetArg(position = 0) FileInputStream that,
        @HijackGetArg(position = 1) File file)
    {
        openedStreams.put(that, new Throwable(file.getAbsolutePath()));
    }

    /**
     * @param that
     *            a file input stream
     */
    @HijackMethod(method = "java/io/FileInputStream/close()V")
    public void fileInputClose(@HijackGetArg(position = 0) FileInputStream that)
    {
        openedStreams.remove(that);
    }

    /**
     * @param that
     *            a file output stream
     * @param file
     *            a file
     */
    @HijackMethod(method = "java/io/FileOutputStream/<init>(Ljava/io/File;)V")
    public void fileOutputOpen(
        @HijackGetArg(position = 0) FileOutputStream that,
        @HijackGetArg(position = 1) File file)
    {
        openedStreams.put(that, new Throwable(file.getAbsolutePath()));
    }

    /**
     * @param that
     *            a file output stream
     */
    @HijackMethod(method = "java/io/FileOutputStream/close()V")
    public void fileInputClose(@HijackGetArg(position = 0) FileOutputStream that)
    {
        openedStreams.remove(that);
    }

    /**
     * @param that
     *            a random access file
     * @param file
     *            a file
     */
    @HijackMethod(method = "java/io/RandomAccessFile/<init>(Ljava/io/File;Ljava/lang/String;)V")
    public void fileRandomOpen(
        @HijackGetArg(position = 0) RandomAccessFile that,
        @HijackGetArg(position = 1) File file)
    {
        openedStreams.put(that, new Throwable(file.getAbsolutePath()));
    }

    /**
     * @param that
     *            a random access file
     */
    @HijackMethod(method = "java/io/RandomAccessFile/close()V")
    public void fileRandomClose(@HijackGetArg(position = 0) RandomAccessFile that)
    {
        openedStreams.remove(that);
    }


}
