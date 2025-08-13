package org.openas2.processor.logger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.message.Message;
import org.openas2.processor.BaseProcessorModule;
import org.openas2.processor.ProcessorModule;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Custom Message Logger Module that logs incoming AS2 messages to a specified log file
 * with format: Timestamp (ISO format) | Message ID | Sender ID → Receiver ID | File size (bytes)
 */
public class MessageLoggerModule extends BaseProcessorModule {
    
    private static final Logger LOG = LoggerFactory.getLogger(MessageLoggerModule.class);
    private static final String LOG_DIRECTORY = "as2-logs";
    private static final String LOG_FILENAME = "as2-message-log.txt";
    private static final String ARCHIVE_DIRECTORY = "as2-logs/archive";
    private static final SimpleDateFormat ISO_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    private static final SimpleDateFormat FILE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    
    public static final String DO_LOG_MESSAGE = "log_message";
    
    private ScheduledExecutorService archiveScheduler;
    
    @Override
    public void init(Session session, Map<String, String> options) throws OpenAS2Exception {
        super.init(session, options);
        
        // Create log directory if it doesn't exist
        try {
            Path logDirPath = Paths.get(LOG_DIRECTORY);
            if (!Files.exists(logDirPath)) {
                Files.createDirectories(logDirPath);
                LOG.info("Created AS2 log directory: " + logDirPath.toAbsolutePath());
            }
            
            // Create archive directory
            Path archiveDirPath = Paths.get(ARCHIVE_DIRECTORY);
            if (!Files.exists(archiveDirPath)) {
                Files.createDirectories(archiveDirPath);
                LOG.info("Created AS2 archive directory: " + archiveDirPath.toAbsolutePath());
            }
        } catch (IOException e) {
            throw new OpenAS2Exception("Failed to create AS2 log directories", e);
        }
        
        // Start daily archive scheduler (runs every day at midnight)
        startArchiveScheduler();
    }
    
    @Override
    public boolean canHandle(String action, Message msg, Map<String, Object> options) {
        return DO_LOG_MESSAGE.equals(action);
    }
    
    @Override
    public void handle(String action, Message msg, Map<String, Object> options) throws OpenAS2Exception {
        if (!DO_LOG_MESSAGE.equals(action)) {
            return;
        }
        
        try {
            logMessage(msg);
        } catch (Exception e) {
            LOG.error("Failed to log AS2 message: " + e.getMessage(), e);
            // Don't throw exception to avoid breaking the message processing flow
        }
    }
    
    private void logMessage(Message msg) throws IOException {
        // Get message details
        String timestamp = ISO_DATE_FORMAT.format(new Date());
        String messageId = msg.getMessageID();
        String senderId = msg.getPartnership().getSenderID("as2_id");
        String receiverId = msg.getPartnership().getReceiverID("as2_id");
        long fileSize = getMessageSize(msg);
        
        // Handle null values
        if (messageId == null) messageId = "UNKNOWN";
        if (senderId == null) senderId = "UNKNOWN";
        if (receiverId == null) receiverId = "UNKNOWN";
        
        // Format: 2025-08-08T10:30:52 | 123456 | ACME → BIGBUY | 20480 bytes
        String logEntry = String.format("%s | %s | %s → %s | %d bytes%n", 
            timestamp, messageId, senderId, receiverId, fileSize);
        
        // Write to log file
        File logFile = new File(LOG_DIRECTORY, LOG_FILENAME);
        try (FileWriter writer = new FileWriter(logFile, true)) {
            writer.write(logEntry);
            writer.flush();
        }
        
        LOG.info("Logged AS2 message: " + messageId + " from " + senderId + " to " + receiverId);
    }
    
    private long getMessageSize(Message msg) {
        try {
            if (msg.getData() != null) {
                return msg.getData().getSize();
            }
        } catch (Exception e) {
            LOG.debug("Could not determine message size: " + e.getMessage());
        }
        return 0;
    }
    
    /**
     * Starts the daily archive scheduler
     */
    private void startArchiveScheduler() {
        archiveScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AS2-Log-Archiver");
            t.setDaemon(true);
            return t;
        });
        
        // Schedule to run every 24 hours (daily at midnight would be better, but this is simpler)
        archiveScheduler.scheduleAtFixedRate(this::archiveLogFile, 24, 24, TimeUnit.HOURS);
        
        LOG.info("Started daily log archive scheduler");
    }
    
    /**
     * Archives the current log file by moving it to archive directory with date suffix
     */
    private void archiveLogFile() {
        try {
            File currentLogFile = new File(LOG_DIRECTORY, LOG_FILENAME);
            
            if (currentLogFile.exists() && currentLogFile.length() > 0) {
                String dateString = FILE_DATE_FORMAT.format(new Date());
                String archiveFileName = "as2-message-log-" + dateString + ".txt";
                File archiveFile = new File(ARCHIVE_DIRECTORY, archiveFileName);
                
                // Move current log to archive
                if (currentLogFile.renameTo(archiveFile)) {
                    LOG.info("Successfully archived log file to: " + archiveFile.getAbsolutePath());
                } else {
                    LOG.warn("Failed to archive log file: " + currentLogFile.getAbsolutePath());
                }
            }
        } catch (Exception e) {
            LOG.error("Error during log file archiving: " + e.getMessage(), e);
        }
    }
    
    /**
     * Cleanup method to shutdown the archive scheduler
     */
    @Override
    public void destroy() throws Exception {
        if (archiveScheduler != null && !archiveScheduler.isShutdown()) {
            archiveScheduler.shutdown();
            try {
                if (!archiveScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    archiveScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                archiveScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            LOG.info("Archive scheduler shutdown completed");
        }
        super.destroy();
    }
    
    @Override
    public String getModuleAction() {
        return DO_LOG_MESSAGE;
    }
}
