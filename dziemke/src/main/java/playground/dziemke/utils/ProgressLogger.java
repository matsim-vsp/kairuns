package playground.dziemke.utils;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

public class ProgressLogger extends Logger {

    private long currentSize;
    private long currentCounter;
    private long nextCounterMsg;
    private String subject;

    protected ProgressLogger(String name) {
        super(name);
    }

    public static ProgressLogger getLogger(String name) {
        return (ProgressLogger)LogManager.getLogger(name);
    }

    public static ProgressLogger getLogger(Class clazz) {
        return (ProgressLogger)LogManager.getLogger(clazz.getName());
    }

    public void initializeProgress(String subject, long currentSize) {
        this.subject = subject;
        this.currentSize = currentSize;
        this.currentCounter = 0;
    }

    public void resetProgress() {
        this.currentCounter = 0;
        this.currentSize = 0;
        this.subject = "unknown";
    }

    public void processCounter() {
        this.currentCounter++;
        if (this.currentCounter == this.nextCounterMsg) {
            this.nextCounterMsg *= 4;
            Runtime rt = Runtime.getRuntime();
            this.info(subject + " # " + this.currentCounter + " ~ "
                    + (double)Math.round((((double)currentCounter/currentSize)*100)*100)/100 + "% "
                    + "Allocated Memory: " + rt.totalMemory() / 1000000 + " MB");
        }
    }

}
