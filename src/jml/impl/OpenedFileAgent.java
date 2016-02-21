package jml.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Map;

import jml.Agent;
import jml.HijackGetArg;
import jml.HijackHandler;
import jml.HijackMethod;
import jml.classloaders.system.OpenedFileAgentFunctionPoints;

/** OpenedFileAgent */
public class OpenedFileAgent extends Agent
{

    /** OpenedFileAgent */
    public OpenedFileAgent()
    {
        super(SystemFunctionPoints.class);
        new SystemFunctionPoints(this);
    }


    @Override
    public <APPENDABLE extends Appendable> APPENDABLE dump(APPENDABLE app) throws IOException
    {
        Map<Object, OpenedFile> openedStreams =
            ((SystemFunctionPoints)(OpenedFileAgentFunctionPoints.instance)).openedStreams;

        if (openedStreams.size() == 0)
        {
            app.append("No file opened");
            return app;
        }
        for (OpenedFile of : openedStreams.values())
        {
            of.dump(app).append("\n");
        }
        return app;
    }

    @HijackHandler(handler = OpenedFileAgentFunctionPoints.class)
    static class SystemFunctionPoints extends OpenedFileAgentFunctionPoints
    {
        private final Map<Object, OpenedFile> openedStreams = new HashMap<Object, OpenedFile>();
        private final OpenedFileAgent owner;

        SystemFunctionPoints(OpenedFileAgent owner)
        {
            OpenedFileAgentFunctionPoints.instance = this;
            this.owner = owner;
        }

        @Override
        @HijackMethod(method = "java/io/FileInputStream/<init>(Ljava/io/File;)V")
        public void fileInputOpen(
            @HijackGetArg(position = 0) FileInputStream that,
            @HijackGetArg(position = 1) File file)
        {
            openedStreams.put(that, OpenedFile.register(owner.mBeanServer, that, file));
        }

        @Override
        @HijackMethod(method = "java/io/FileInputStream/close()V")
        public void fileInputClose(@HijackGetArg(position = 0) FileInputStream that)
        {
            OpenedFile.unregister(owner.mBeanServer, openedStreams.remove(that));
        }

        @Override
        @HijackMethod(method = "java/io/FileOutputStream/<init>(Ljava/io/File;)V")
        public void fileOutputOpen(
            @HijackGetArg(position = 0) FileOutputStream that,
            @HijackGetArg(position = 1) File file)
        {
            openedStreams.put(that, OpenedFile.register(owner.mBeanServer, that, file));
        }

        @Override
        @HijackMethod(method = "java/io/FileOutputStream/close()V")
        public void fileInputClose(@HijackGetArg(position = 0) FileOutputStream that)
        {
            OpenedFile.unregister(owner.mBeanServer, openedStreams.remove(that));
        }

        @Override
        @HijackMethod(method = "java/io/RandomAccessFile/<init>(Ljava/io/File;Ljava/lang/String;)V")
        public void fileRandomOpen(
            @HijackGetArg(position = 0) RandomAccessFile that,
            @HijackGetArg(position = 1) File file)
        {
            openedStreams.put(that, OpenedFile.register(owner.mBeanServer, that, file));
        }

        @Override
        @HijackMethod(method = "java/io/RandomAccessFile/close()V")
        public void fileRandomClose(@HijackGetArg(position = 0) RandomAccessFile that)
        {
            OpenedFile.unregister(owner.mBeanServer, openedStreams.remove(that));
        }

    }

}
