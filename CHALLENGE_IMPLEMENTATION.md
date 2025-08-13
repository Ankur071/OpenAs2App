# OpenAS2 Custom Message Logger + API Hook Implementation

## Challenge Solution - Java Dev Challenge – AS2 Logger + API Hook 

This implementation provides a complete solution for the challenge requirements:

### ✅ **Part 1: Build a Custom Message Logger**
- **Intercepts incoming AS2 messages**
- **Logs to `as2-logs/as2-message-log.txt`** with the exact required format:
  ```
  Timestamp (ISO format) | Message ID | Sender ID → Receiver ID | File size (bytes)
  ```
- **Example output:**
  ```
  2025-08-08T10:30:52 | 123456 | ACME → BIGBUY | 20480 bytes
  ```

### ✅ **Part 2: Call an External API When File is Received**
- **Sends POST request** to `https://lnkd.in/g-hyudKx` 
- **JSON payload** with required format:
  ```json
  {
    "filename": "order_234.xml",
    "path": "/as2/inbox/order_234.xml",
    "timestamp": "2025-08-08T10:30:52"
  }
  ```
- **Asynchronous execution** to avoid blocking message processing

---

## 📁 Implementation Overview

### Custom Modules Created:

1. **`MessageLoggerModule.java`** 
   - Location: `Server/src/main/java/org/openas2/processor/logger/`
   - Handles message logging to file system

2. **`ApiHookModule.java`**
   - Location: `Server/src/main/java/org/openas2/processor/hook/`
   - Handles external API calls

3. **Modified `AS2ReceiverHandler.java`**
   - Integrated both modules into the message processing flow
   - Added after successful message storage, before MDN processing

---

## 🚀 How to Run/Test

### 1. **Build the Project**
```bash
# Navigate to project directory
cd D:\KIS\OpenAs2App

# Build with Maven (Windows)
./mvnw.cmd clean package

# Or on Unix/Linux
./mvnw clean package
```

### 2. **Configuration** (Optional)
The modules work with default settings, but can be configured in `config.xml`:

```xml
<!-- Add to processor modules section -->
<module classname="org.openas2.processor.logger.MessageLoggerModule"
        module_action="log_message" />

<module classname="org.openas2.processor.hook.ApiHookModule"
        module_action="api_hook"
        api_url="https://lnkd.in/g-hyudKx"
        timeout="10000"
        async="true" />
```

### 3. **Run the AS2 Server**
```bash
cd Server/target/dist
# On Windows
start-openas2.bat
# On Unix/Linux  
./start-openas2.sh
```

### 4. **Test with AS2 Messages**
Send AS2 messages to the server and observe:

- **Log file created:** `as2-logs/as2-message-log.txt`
- **API calls sent** to the specified endpoint
- **Console logs** showing successful processing

---

## 📋 Where Implementation is Located

### **Custom Logger Module**
- **File:** `Server/src/main/java/org/openas2/processor/logger/MessageLoggerModule.java`
- **Function:** Logs AS2 messages with timestamp, message ID, sender/receiver, file size
- **Log location:** `as2-logs/as2-message-log.txt` (created automatically)

### **API Hook Module** 
- **File:** `Server/src/main/java/org/openas2/processor/hook/ApiHookModule.java`
- **Function:** Sends HTTP POST with JSON payload to external API
- **Endpoint:** `https://lnkd.in/g-hyudKx` (configurable)
- **Features:** Asynchronous execution, timeout handling, manual JSON creation

### **Integration Point**
- **File:** `Server/src/main/java/org/openas2/processor/receiver/AS2ReceiverHandler.java`
- **Lines:** ~246-260
- **Integration:** Added custom module calls after message storage, before MDN processing

---

## 🛠️ Technical Implementation Details

### **Message Flow Integration**
```
Incoming AS2 Message 
→ Parse & Validate 
→ Decrypt & Verify 
→ Store Message 
→ 🔄 **Custom Logger** (logs to file)
→ 🔄 **API Hook** (calls external API)
→ Send MDN Response
```

### **Error Handling**
- Custom modules use try-catch to avoid breaking main message flow
- Detailed logging for troubleshooting
- API calls are asynchronous to prevent blocking

### **Configuration Options**
- **api_url**: External API endpoint (default: challenge URL)
- **timeout**: HTTP timeout in milliseconds (default: 10000)
- **async**: Enable asynchronous API calls (default: true)

---

## 📊 Expected Output Examples

### **Log File Content** (`as2-logs/as2-message-log.txt`):
```
2025-08-13T18:30:52 | MSG123456 | ACME → BIGBUY | 20480 bytes
2025-08-13T18:31:15 | MSG123457 | SUPPLIER → CUSTOMER | 15230 bytes
2025-08-13T18:32:01 | MSG123458 | PARTNER1 → PARTNER2 | 8192 bytes
```

### **API Request JSON Payload**:
```json
{
  "filename": "order_234.xml",
  "path": "/as2/inbox/order_234.xml", 
  "timestamp": "2025-08-13T18:30:52"
}
```

### **Console Logs**:
```
INFO  o.o.p.logger.MessageLoggerModule - Logged AS2 message: MSG123456 from ACME to BIGBUY
INFO  o.o.p.hook.ApiHookModule - Successfully sent API hook for message: MSG123456 (HTTP 200)
```

---

## 🎯 Challenge Completion Status

- [x] **Part 1: Custom Message Logger** - ✅ Complete
  - [x] Intercepts incoming AS2 messages
  - [x] Logs to `as2-logs/as2-message-log.txt`
  - [x] Correct format: `Timestamp | MessageID | Sender → Receiver | FileSize`

- [x] **Part 2: External API Hook** - ✅ Complete
  - [x] HTTP POST to `https://lnkd.in/g-hyudKx`
  - [x] JSON payload with filename, path, timestamp
  - [x] Triggered when file is received

- [x] **Integration** - ✅ Complete
  - [x] Integrated into AS2ReceiverHandler
  - [x] No breaking changes to existing functionality
  - [x] Error handling to prevent message processing failures

---

## 💡 Key Features

- **🔄 Non-blocking**: Asynchronous API calls don't slow down message processing
- **📁 Auto-directory creation**: Log directory created automatically if missing
- **🛡️ Error resilient**: Failures in custom modules won't break AS2 processing
- **⚙️ Configurable**: API URL, timeouts, and sync/async modes are configurable
- **📝 Detailed logging**: Full logging for debugging and monitoring
- **🎯 Production ready**: Proper error handling and resource management

---

## 🚀 Quick Start Guide

1. **Clone/Fork the repository** (already done)
2. **Build the project**: `./mvnw clean package`
3. **Run tests**: `./mvnw test` (optional)
4. **Start the server**: Run from `Server/target/dist/`
5. **Send test AS2 message** to trigger logging and API calls
6. **Check results**:
   - Log file: `as2-logs/as2-message-log.txt`
   - Console output for API call confirmations

---

This implementation successfully fulfills all requirements of the AS2 Logger + API Hook challenge while maintaining the integrity and performance of the existing OpenAS2 system.
