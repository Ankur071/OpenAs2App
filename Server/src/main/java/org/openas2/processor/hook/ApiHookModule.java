package org.openas2.processor.hook;

// Manual JSON creation to avoid dependency issues
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.message.Message;
import org.openas2.processor.BaseProcessorModule;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * API Hook Module that sends HTTP POST request to external API when a file is received
 * Sends JSON payload with filename, path, and timestamp
 */
public class ApiHookModule extends BaseProcessorModule {
    
    private static final Logger LOG = LoggerFactory.getLogger(ApiHookModule.class);
    private static final SimpleDateFormat ISO_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    
    public static final String DO_API_HOOK = "api_hook";
    
    // Parameter keys for configuration
    public static final String PARAM_API_URL = "api_url";
    public static final String PARAM_TIMEOUT = "timeout";
    public static final String PARAM_ASYNC = "async";
    public static final String PARAM_MAX_RETRIES = "max_retries";
    public static final String PARAM_RETRY_DELAY = "retry_delay_ms";
    
    private String apiUrl;
    private int timeout;
    private boolean async;
    private int maxRetries;
    private int retryDelayMs;
    // Removed ObjectMapper to use manual JSON creation
    
    @Override
    public void init(Session session, Map<String, String> options) throws OpenAS2Exception {
        super.init(session, options);
        
        // Get API URL from configuration
        this.apiUrl = getParameter(PARAM_API_URL, false);
        if (this.apiUrl == null || this.apiUrl.trim().isEmpty()) {
            // Use default API URL from challenge requirement
            this.apiUrl = "https://lnkd.in/g-hyudKx";
        }
        
        // Get timeout configuration (default 10 seconds)
        String timeoutStr = getParameter(PARAM_TIMEOUT, false);
        this.timeout = (timeoutStr != null) ? Integer.parseInt(timeoutStr) : 10000;
        
        // Get async configuration (default true to avoid blocking message processing)
        String asyncStr = getParameter(PARAM_ASYNC, false);
        this.async = !"false".equalsIgnoreCase(asyncStr);
        
        // Get retry configuration (default 3 retries)
        String maxRetriesStr = getParameter(PARAM_MAX_RETRIES, false);
        this.maxRetries = (maxRetriesStr != null) ? Integer.parseInt(maxRetriesStr) : 3;
        
        // Get retry delay configuration (default 1000ms)
        String retryDelayStr = getParameter(PARAM_RETRY_DELAY, false);
        this.retryDelayMs = (retryDelayStr != null) ? Integer.parseInt(retryDelayStr) : 1000;
        
        // Manual JSON creation - no ObjectMapper needed
        
        LOG.info("API Hook Module initialized - URL: {}, Timeout: {}ms, Async: {}, Retries: {}, Delay: {}ms", 
                apiUrl, timeout, async, maxRetries, retryDelayMs);
    }
    
    @Override
    public boolean canHandle(String action, Message msg, Map<String, Object> options) {
        return DO_API_HOOK.equals(action);
    }
    
    @Override
    public void handle(String action, Message msg, Map<String, Object> options) throws OpenAS2Exception {
        if (!DO_API_HOOK.equals(action)) {
            return;
        }
        
        if (async) {
            // Send API call asynchronously to avoid blocking message processing
            CompletableFuture.runAsync(() -> {
                try {
                    sendApiRequestWithRetry(msg);
                } catch (Exception e) {
                    LOG.error("Failed to send async API hook for message: " + msg.getMessageID(), e);
                }
            });
        } else {
            // Send API call synchronously
            try {
                sendApiRequestWithRetry(msg);
            } catch (Exception e) {
                LOG.error("Failed to send API hook for message: " + msg.getMessageID(), e);
                // Don't throw exception to avoid breaking the message processing flow
            }
        }
    }
    
    /**
     * Sends API request with retry mechanism and exponential backoff
     */
    private void sendApiRequestWithRetry(Message msg) throws IOException {
        Exception lastException = null;
        
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                sendApiRequest(msg);
                return; // Success - exit retry loop
                
            } catch (IOException e) {
                lastException = e;
                
                if (attempt == maxRetries) {
                    LOG.error("API hook failed after {} attempts for message: {}", 
                             maxRetries + 1, msg.getMessageID());
                    throw e;
                }
                
                // Calculate delay with exponential backoff: delay * (2^attempt)
                long delayMs = retryDelayMs * (long) Math.pow(2, attempt);
                
                LOG.warn("API hook attempt {} failed for message: {}. Retrying in {}ms. Error: {}", 
                        attempt + 1, msg.getMessageID(), delayMs, e.getMessage());
                
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    LOG.warn("Retry sleep interrupted for message: {}", msg.getMessageID());
                    throw new IOException("API hook retry interrupted", ie);
                }
            }
        }
        
        // This should never be reached, but just in case
        if (lastException instanceof IOException) {
            throw (IOException) lastException;
        } else {
            throw new IOException("API hook failed after retries", lastException);
        }
    }
    
    private void sendApiRequest(Message msg) throws IOException {
        // Prepare JSON payload
        Map<String, String> payload = new HashMap<>();
        
        // Get filename from message
        String filename = msg.getPayloadFilename();
        if (filename == null || filename.trim().isEmpty()) {
            filename = "order_" + System.currentTimeMillis() + ".xml"; // fallback filename
        }
        
        // Construct path (simulating inbox path structure)
        String path = "/as2/inbox/" + filename;
        
        // Get timestamp
        String timestamp = ISO_DATE_FORMAT.format(new Date());
        
        payload.put("filename", filename);
        payload.put("path", path);
        payload.put("timestamp", timestamp);
        
        // Convert to JSON manually
        String jsonPayload = createJsonPayload(payload);
        
        LOG.debug("Sending API hook request for message: {} to URL: {} with payload: {}", 
                msg.getMessageID(), apiUrl, jsonPayload);
        
        // Send HTTP POST request
        HttpURLConnection connection = null;
        try {
            URL url = new URL(apiUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("User-Agent", "OpenAS2-ApiHook/1.0");
            connection.setDoOutput(true);
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);
            
            // Write JSON payload
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            // Get response
            int responseCode = connection.getResponseCode();
            
            if (responseCode >= 200 && responseCode < 300) {
                LOG.info("Successfully sent API hook for message: {} (HTTP {})", 
                        msg.getMessageID(), responseCode);
            } else {
                LOG.warn("API hook returned non-success status for message: {} (HTTP {})", 
                        msg.getMessageID(), responseCode);
            }
            
        } catch (IOException e) {
            LOG.error("Failed to send API hook for message: " + msg.getMessageID() + 
                     " to URL: " + apiUrl, e);
            throw e;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    
    /**
     * Creates JSON payload manually to avoid external dependencies
     * @param payload Map containing key-value pairs for JSON
     * @return JSON string
     */
    private String createJsonPayload(Map<String, String> payload) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        
        boolean first = true;
        for (Map.Entry<String, String> entry : payload.entrySet()) {
            if (!first) {
                json.append(",");
            }
            json.append('"').append(escapeJson(entry.getKey())).append('"');
            json.append(':');
            json.append('"').append(escapeJson(entry.getValue())).append('"');
            first = false;
        }
        
        json.append("}");
        return json.toString();
    }
    
    /**
     * Escapes special characters for JSON
     * @param value String to escape
     * @return Escaped string
     */
    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        // Basic JSON escaping - replace problematic characters
        String escaped = value;
        escaped = escaped.replace("\\", "\\\\");
        escaped = escaped.replace("\"", "\\\"");
        escaped = escaped.replace("\r", "\\r");
        escaped = escaped.replace("\n", "\\n");
        escaped = escaped.replace("\t", "\\t");
        return escaped;
    }
    
    @Override
    public String getModuleAction() {
        return DO_API_HOOK;
    }
}
