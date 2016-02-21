package jml.classloaders;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.instrument.Instrumentation;
import java.util.jar.JarFile;

/** load some classes into bootstrap or system class loader */
public class JarLoader
{

    private final static String SYSTEM_JAR = "system";
    private final static String BOOTSTRAP_JAR = "bootstrap";

    /**
     * @param instrumentation
     *            the agent instrumentation
     * @throws IOException
     *             if a jar cannot be created
     */
    public static void loadExternalJars(Instrumentation instrumentation) throws IOException
    {
        JarFile bootstrapJar = loadJar(BOOTSTRAP_JAR);
        if (bootstrapJar != null)
        {
            instrumentation.appendToBootstrapClassLoaderSearch(bootstrapJar);
        }
        JarFile systemJar = loadJar(SYSTEM_JAR);
        if (systemJar != null)
        {
            instrumentation.appendToBootstrapClassLoaderSearch(systemJar);
        }
    }

    private static JarFile loadJar(String jarResourceName) throws IOException
    {
        File jarFile = File.createTempFile(jarResourceName, ".jar");
        OutputStream os = null;
        InputStream is = null;
        try
        {

            is = JarLoader.class.getResourceAsStream(jarResourceName + ".jar");
            if (is == null)
                return null;
            os = new FileOutputStream(jarFile);
            int len = 0;
            byte[] buffer = new byte[1024];
            while ((len = is.read(buffer)) >= 0)
            {
                os.write(buffer, 0, len);
            }
        }
        finally
        {
            try
            {
                if (is != null) is.close();
            }
            finally
            {
                if (os != null) os.close();
            }
        }
        return new JarFile(jarFile, true);
    }

}
