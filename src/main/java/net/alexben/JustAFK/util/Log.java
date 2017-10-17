package net.alexben.JustAFK.util;

import java.util.logging.Level;
import java.util.logging.Logger;

public class Log {
    private static final Logger LOG = Logger.getLogger("Minecraft");
    private static final String PREFIX = "[scoutlinkHub] ";
    
    public static void info(String output) {
        LOG.log(Level.INFO,PREFIX + "{0}", output);
    }
    
    public static void severe(String output) {
        LOG.log(Level.SEVERE,PREFIX + "{0}", output);
    }
    
    public static void warning(String output) {
        LOG.log(Level.WARNING,PREFIX + "{0}", output);
    }
}
