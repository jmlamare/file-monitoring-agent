package jml.impl;

import java.io.File;
import java.io.IOException;
import java.util.Hashtable;

import javax.management.MBeanServer;
import javax.management.ObjectInstance;
import javax.management.ObjectName;

import sun.misc.SharedSecrets;

/** OpenedFile */
public class OpenedFile implements OpenedFileMBean /* , MBeanRegistration */
{
    private static int count = 0;
    private File file;
    private Object owner;
    private String trace;
    private ObjectInstance mBeanInstance;

    static void unregister(MBeanServer mBeanServer, OpenedFile openedFile)
    {
        if (openedFile == null)
        {
            return;
        }
        try
        {
            mBeanServer.unregisterMBean(openedFile.mBeanInstance.getObjectName());
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    static OpenedFile register(MBeanServer mBeanServer, Object owner, File file)
    {
        if (file == null || !file.exists())
        {
            return null;
        }
        try
        {
            Hashtable<String, String> mBeanIds = new Hashtable<String, String>();
            mBeanIds.put("owner", owner.getClass().getSimpleName());
            mBeanIds.put("id", Integer.toString(count++));
            mBeanIds.put("file", file.getName());

            OpenedFile myMBean = (OpenedFile)mBeanServer.instantiate(OpenedFile.class.getName());
            {

                Throwable traceException = new Throwable();
                int traceDepth = SharedSecrets.getJavaLangAccess().getStackTraceDepth(traceException);
                StringBuilder traceBuff = new StringBuilder();
                for (int i = 2; i < traceDepth; i++)
                {
                    StackTraceElement ste = SharedSecrets.getJavaLangAccess().getStackTraceElement(traceException, i);
                    traceBuff.append("[").append(ste.toString()).append("]\n");
                }
                myMBean.trace = traceBuff.toString();
                myMBean.owner = owner;
                myMBean.file = file;
            }
            ObjectName mBeanName = ObjectName.getInstance(OpenedFile.class.getSimpleName(), mBeanIds);
            myMBean.mBeanInstance = mBeanServer.registerMBean(myMBean, mBeanName);

            return myMBean;
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return null;
        }
    }

    <APPENDABLE extends Appendable> APPENDABLE dump(APPENDABLE app) throws IOException
    {
        app.append(file.getAbsolutePath()).append(" opened by ").append(trace);
        return app;
    }

    @Override
    public String getFilePath()
    {
        return file.getAbsolutePath();
    }

    @Override
    public String getUsingClass()
    {
        return owner.getClass().getSimpleName();
    }

    @Override
    public String getStackTrace()
    {
        return trace;
    }

}
