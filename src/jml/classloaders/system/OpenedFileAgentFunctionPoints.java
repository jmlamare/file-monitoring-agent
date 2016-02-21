package jml.classloaders.system;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;


/** The function hijacked by the agent */
public abstract class OpenedFileAgentFunctionPoints
{

    /** singleton */
    public static OpenedFileAgentFunctionPoints instance = null;


    /**
     * @param that
     *            a file input stream
     * @param file
     *            a file
     */
    public abstract void fileInputOpen(FileInputStream that, File file);

    /**
     * @param that
     *            a file input stream
     */
    public abstract void fileInputClose(FileInputStream that);

    /**
     * @param that
     *            a file output stream
     * @param file
     *            a file
     */
    public abstract void fileOutputOpen(FileOutputStream that, File file);

    /**
     * @param that
     *            a file output stream
     */
    public abstract void fileInputClose(FileOutputStream that);

    /**
     * @param that
     *            a random access file
     * @param file
     *            a file
     */
    public abstract void fileRandomOpen(RandomAccessFile that, File file);

    /**
     * @param that
     *            a random access file
     */
    public abstract void fileRandomClose(RandomAccessFile that);

}
