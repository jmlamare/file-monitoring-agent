package jml;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.net.URL;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collection;
import java.util.StringTokenizer;
import java.util.jar.JarFile;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

import jml.classloaders.JarLoader;
import jml.impl.OpenedFileAgent;
import jml.samples.Spy;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;

import com.sun.jdmk.comm.HtmlAdaptorServer;
import com.sun.tools.attach.VirtualMachine;

/** Agent class */
public abstract class Agent
{


    /**
     * @return the jar file of the agent
     * @throws IOException
     *             if the connection cannot be opened
     */
    public static JarFile getAgentJarFile() throws IOException
    {
        return ((JarURLConnection)Spy.class.getResource(Spy.class.getSimpleName() + ".class").openConnection()).getJarFile();
    }

    /**
     * @return the jar url of the agent
     * @throws IOException
     *             if the connection cannot be opened
     */
    public static URL getAgentJarURL() throws IOException
    {
        return ((JarURLConnection)Spy.class.getResource(Spy.class.getSimpleName() + ".class").openConnection()).getJarFileURL();
    }


    private Writer getAgentOutputStream() throws FileNotFoundException
    {
        String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
        File temporaryDir = new File(System.getProperty("java.io.tmpdir"));
        File targetFile = new File(temporaryDir, getClass().getSimpleName() + "." + pid);
        System.out.println("Write " + targetFile.getAbsolutePath());
        return new PrintWriter(new FileOutputStream(targetFile));
    }

    private static final String ARGSEP = ";";

    /** parse the agent arguments */
    private static String[] parseAgentArgs(String agentArgs)
    {
        if (agentArgs == null || agentArgs.isEmpty())
        {
            return new String[0];
        }
        ArrayList<String> args = new ArrayList<String>();
        StringTokenizer tokenizer = new StringTokenizer(agentArgs, ARGSEP);
        StringBuilder tokenBuilder = new StringBuilder();

        while (true)
        {
            String token = "";
            while (tokenizer.hasMoreElements() && (token = tokenizer.nextToken()).isEmpty())
            {
                tokenBuilder.append(ARGSEP);
            }
            tokenBuilder.append(token);
            if (!tokenizer.hasMoreTokens())
            {
                args.add(tokenBuilder.toString());
                break;
            }
            token = tokenizer.nextToken();
            if (token.isEmpty())
            {
                tokenBuilder.append(ARGSEP);
                continue;
            }
            args.add(tokenBuilder.toString());
            tokenBuilder.setLength(0);
            tokenBuilder.append(token);
        }
        return args.toArray(new String[args.size()]);
    }

    MethodVisitor processMethod(final MethodVisitor methodVisitor, final String className, final String methodName,
        final int access, final String descriptor)
    {
        String methodKey = className + '/' + methodName + descriptor;

        class MethodAdapter extends AdviceAdapter
        {
            private final Label timeVarStart = new Label();
            private final Label timeVarEnd = new Label();
            private final Method method;
            private final Class<?> handler;
            private int size = 0;


            MethodAdapter(Class<?> handler, Method method)
            {
                super(Opcodes.ASM4, methodVisitor, access, methodName, descriptor);
                this.method = method;
                this.handler = handler;
            }


            @Override
            protected void onMethodEnter()
            {
                visitLabel(timeVarStart);

                super.visitFieldInsn(GETSTATIC, Type.getInternalName(handler), "instance", Type.getDescriptor(handler));

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
                            loadThis();
                            visitFieldInsn(GETFIELD, className, ((HijackGetField)ann).field(), Type.getDescriptor(paramClasses[i]));
                            break;
                        }
                        if (ann instanceof HijackGetArg)
                        {
                            visitVarInsn(paramTypes[i].getOpcode(ILOAD), ((HijackGetArg)ann).position());
                            break;
                        }
                    }
                }

                super.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(handler), method.getName(), Type.getMethodDescriptor(Type.VOID_TYPE, paramTypes));
            }

            @Override
            public void visitMaxs(int stack, int locals)
            {
                visitLabel(timeVarEnd);
                super.visitMaxs(Math.max(size, stack), locals);
            }
        }

        for (Class<?> handlerClass : handlerClasses)
        {
            HijackHandler handler = handlerClass.getAnnotation(HijackHandler.class);
            for (Method handlerMethod : handlerClass.getMethods())
            {
                HijackMethod ha = handlerMethod.getAnnotation(HijackMethod.class);
                if (ha != null && ha.method().equals(methodKey))
                {
                    System.out.println("Modified method: " + methodKey);
                    return new MethodAdapter(handler.handler(), handlerMethod);
                }
            }
        }

        return null;
    }

    private byte[] processClass(final String className, byte[] classfileBuffer)
    {
        class ClassAdapter extends ClassVisitor
        {
            boolean processed = false;

            private ClassAdapter(ClassVisitor visitor)
            {
                // super(Opcodes.ASM4, new TraceClassVisitor(visitor, new Textifier(), new PrintWriter(System.out)));
                super(Opcodes.ASM4, visitor);
            }

            @Override
            public MethodVisitor visitMethod(
                final int access, final String methodName, final String descriptor,
                final String signature, final String[] exceptions)
            {
                MethodVisitor methodVisitor = super.visitMethod(access, methodName, descriptor, signature, exceptions);
                MethodVisitor methodAdapter = processMethod(methodVisitor, className, methodName, access, descriptor);
                if (methodAdapter != null)
                {
                    processed = true;
                    return methodAdapter;
                }
                return methodVisitor;
            }
        }

        ClassWriter classWriter = new ClassWriter(0);
        ClassAdapter classAdpater = new ClassAdapter(classWriter);
        new ClassReader(classfileBuffer).accept(classAdpater, ClassReader.EXPAND_FRAMES);
        return classAdpater.processed ? classWriter.toByteArray() : null;
    }

    protected final Class<?>[] handlerClasses;
    protected final MBeanServer mBeanServer;

    protected Agent(Class<?>... handlerClasses)
    {
        this.handlerClasses = handlerClasses;
        mBeanServer = MBeanServerFactory.createMBeanServer();

    }

    private void startJMXServer(int port) throws IOException, InstanceAlreadyExistsException, MBeanRegistrationException,
        NotCompliantMBeanException, MalformedObjectNameException, NullPointerException
    {
        JMXServiceURL serviceUrl = new JMXServiceURL("rmi", null, port + 10);
        JMXConnectorServer service = JMXConnectorServerFactory.newJMXConnectorServer(serviceUrl, null, mBeanServer);


        HtmlAdaptorServer httpAdapter = new HtmlAdaptorServer(port);
        mBeanServer.registerMBean(httpAdapter, new ObjectName("SimpleAgent:name=htmladapter,port=" + port));
        URL httpAdapterUrl = new URL("http", httpAdapter.getHost(), httpAdapter.getPort(), "");

        service.start();
        System.out.append("Start JMX server on ").append(service.getAddress().toString()).append("\n");
        httpAdapter.start();
        System.out.append("Start HTTP adapter on ").append(httpAdapterUrl.toString()).append("\n");

    }


    private void writeFile()
    {
        IOException ioexception = null;
        Writer writer = null;
        try
        {
            dump(writer = getAgentOutputStream());
        }
        catch (IOException ioe)
        {
            ioexception = ioe;
        }
        finally
        {
            try
            {
                if (writer != null)
                    writer.close();
            }
            catch (IOException ioe)
            {
                if (ioexception == null)
                {
                    ioexception = ioe;
                }
            }
        }
        if (ioexception != null)
        {
            ioexception.printStackTrace();
        }

    }


    /**
     * dump the result
     *
     * @param app
     *            the appender where the result will be printed
     * @param <APPENDABLE>
     *            the appender type
     * @return the provided appender
     * @throws IOException
     *             thrown by call to appender
     * */
    protected abstract <APPENDABLE extends Appendable> APPENDABLE dump(APPENDABLE app) throws IOException;


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
            classBytes = processClass(Type.getInternalName(clazz), classBytes);
            if (classBytes != null)
            {
                result.add(new ClassDefinition(clazz, classBytes));
            }
        }
        return result;
    }

    /**
     * @param agentArgsStr
     *            agent arguments
     * @param instrumentation
     *            instrumentation tools
     * @throws Exception
     *             possible exception
     */
    public static void premain(String agentArgsStr, Instrumentation instrumentation) throws Exception
    {
        String[] agentArgs = parseAgentArgs(agentArgsStr);
        int httpPort = 9090;
        for(int i=0;i<agentArgs.length;i++)
        {
            if( agentArgs.equals("httpPort") && i+1<agentArgs.length )
            {
                httpPort = Integer.parseInt(agentArgs[++i]);
            }
        }

        System.out.append(ManagementFactory.getRuntimeMXBean().getName()).append("\n");
        JarLoader.loadExternalJars(instrumentation);

        final Agent agent = new OpenedFileAgent();
        ArrayList<ClassDefinition> classDefs = agent.findOverloadedClasses(new ArrayList<ClassDefinition>(), instrumentation.getAllLoadedClasses());
        instrumentation.redefineClasses(classDefs.toArray(new ClassDefinition[0]));
        instrumentation.addTransformer(new ClassFileTransformer()
        {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException
            {
                return agent.processClass(className, classfileBuffer);
            }
        });

        // SharedSecrets.getJavaLangAccess().registerShutdownHook(0, new Runnable()
        // {
        // @Override
        // public void run()
        // {
        // agent.writeFile();
        // }
        // });


        agent.startJMXServer(httpPort);
    }


    /**
     * @param agentArgs
     *            agent arguments
     * @param instrumentation
     *            instrumentation tools
     * @throws Exception
     *             possible exception
     */
    public static void agentmain(String agentArgs, Instrumentation instrumentation) throws Exception
    {
        premain(agentArgs, instrumentation);
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
        File agentFile = new File(getAgentJarURL().toURI());
        StringBuilder agentArgs = new StringBuilder();
        {
            String agentArgsSep = "";

            for (int i = 1; i < args.length; i++)
            {
                agentArgs.append(agentArgsSep).append(args[i].replaceAll(ARGSEP, ARGSEP + ARGSEP));
                agentArgsSep = ARGSEP;
            }
        }

        System.out.append("dynamically loading javaagent ").append(agentFile.getAbsolutePath()).append("\n");
        VirtualMachine vm = VirtualMachine.attach(args[0]);
        try
        {
            vm.loadAgent(agentFile.getAbsolutePath(), agentArgs.toString());
            vm.getAgentProperties();
        }
        finally
        {
            vm.detach();
        }
    }




}
