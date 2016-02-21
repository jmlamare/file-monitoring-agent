package jml.samples;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.security.ProtectionDomain;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;

public class Sample implements ClassFileTransformer
{
    public static Sample instance;

    private Sample(String[] args)
    {
        instance = this;
    }

    private final void start()
    {

    }

    /**
     * @param agentArgs
     *            agent arguments
     * @param inst
     *            instrumentation tools
     */
    public static void premain(String agentArgs, Instrumentation inst)
    {
        inst.addTransformer(new Sample(agentArgs == null ? null : agentArgs.split(";")));
    }

    /**
     * @param agentArgs
     *            agent arguments
     * @param inst
     *            instrumentation tools
     */
    public static void agentmain(String agentArgs, Instrumentation inst)
    {
        instance.start();
    }

    @Override
    public byte[] transform(ClassLoader loader,
        String className,
        Class<?> classBeingRedefined,
        ProtectionDomain protectionDomain,
        byte[] classfileBuffer) throws IllegalClassFormatException
    {
//        {
//            ClassReader cr = new ClassReader(classfileBuffer);
//
//            System.out.println(className);
//            cr.accept(new TraceClassVisitor(null, new Textifier(), new PrintWriter(System.out)), 0);
//            System.out.println();
//        }

        ManagementFactory.getPlatformMBeanServer();

        if (className.equals("jml/Test"))
        {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(0);
            ClassVisitor cv = new ClassVisitor(Opcodes.ASM4, cw)
            {
                @Override
                public MethodVisitor visitMethod(final int access, final String name, final String desc, final String signature, final String[] exceptions)
                {
                    final MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                    if (!name.equals("display"))
                    {
                        return mv;
                    }

                    return new AdviceAdapter(Opcodes.ASM4, mv, access, name, desc)
                    {
                        private int timeVar;
                        private final Label timeVarStart = new Label();
                        private final Label timeVarEnd = new Label();

                        @Override
                        protected void onMethodEnter()
                        {
                            visitLabel(timeVarStart);
                            timeVar = newLocal(Type.getType("J"));
                            visitLocalVariable("timeVar", "J", null, timeVarStart, timeVarEnd, timeVar);
                            super.visitFieldInsn(GETSTATIC, "jml/Sample", "instance", "Ljml/Sample;");
                            super.visitLdcInsn("Entering " + name);
                            super.visitMethodInsn(INVOKEVIRTUAL, "jml/Sample", "println", "(Ljava/lang/String;)V");
                        }

                        @Override
                        public void visitMaxs(int stack, int locals)
                        {
                            visitLabel(timeVarEnd);
                            super.visitMaxs(stack, locals);
                        }

                    };
                }
            };
            cr.accept(cv, 0);
            return cw.toByteArray();
        }
        return null;
    }

    public void println(String text)
    {
        System.out.println(text);
    }
}
