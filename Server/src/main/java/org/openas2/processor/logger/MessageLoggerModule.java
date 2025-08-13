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

/**
 * Custom Message Logger Module that logs incoming AS2 messages to a specified log file
 * with format: Timestamp (ISO format) | Message ID | Sender ID → Receiver ID | File size (bytes)
 */
public class MessageLoggerModule extends BaseProcessorModule {
    
    private static final Logger LOG = LoggerFactory.getLogger(MessageLoggerModule.class);
    private static final String LOG_DIRECTORY = "as2-logs";
    private static final String LOG_FILENAME = "as2-message-log.txt";
    private static final SimpleDateFormat ISO_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    
    public static final String DO_LOG_MESSAGE = "log_message";
    
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
        } catch (IOException e) {
            throw new OpenAS2Exception("Failed to create AS2 log directory", e);
        }
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
    
    @Override
    public String getModuleAction() {
        return DO_LOG_MESSAGE;
    }
}
