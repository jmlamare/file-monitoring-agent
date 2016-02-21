package jml.samples;

import java.io.IOException;

import sun.misc.SharedSecrets;

public class Test
{
    private final static String[] signals =
        new String[] {
            "SIGSEGV", "SIGILL", "SIGFPE", "SIGBUS", "SIGSYS",
            "SIGXCPU", "SIGXFSZ", "SIGEMT", "SIGABRT", "SIGINT",
            "SIGTERM", "SIGHUP", "SIGUSR1", "SIGUSR2", "SIGQUIT",
            "SIGBREAK", "SIGTRAP", "SIGPIPE" };

    public static void main(String[] args) throws IOException, InterruptedException
    {
        SharedSecrets.getJavaLangAccess().registerShutdownHook(0, new Runnable()
        {

            @Override
            public void run()
            {
                System.out.println("First Shutdown hook ran!");

            }

        });
        Runtime.getRuntime().addShutdownHook(new Thread()
        {
            @Override
            public void run()
            {
                System.out.println("Last Shutdown hook ran!");
            }
        });

        while (true)
        {
            Thread.sleep(1000);
        }
/*
        for (String signal : signals)
        {
            try
            {
                final SignalHandler handler = Signal.handle(new Signal(signal.substring(3)), new SignalHandler()
                {
                    @Override
                    public void handle(Signal signal)
                    {
                        System.out.println(signal.getName());

                    }
                });
            }
            catch (Throwable t)
            {
                t.printStackTrace();
            }
        }

        FileInputStream fis = new FileInputStream(args[0]);
        ZipInputStream zis = new ZipInputStream(fis);

        System.in.read();

        // Signal.raise(new Signal("INT"));

        ZipEntry ze = null;
        while ((ze = zis.getNextEntry()) != null)
        {
            System.out.println(ze.getName());
        }
        zis.close();
        */
    }

}
