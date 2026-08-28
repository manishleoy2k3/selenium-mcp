# Selenium MCP Server

A Java implementation of the Model Context Protocol (MCP) for Selenium WebDriver, enabling browser automation through standardized MCP clients.

## Overview

MCP Selenium provides a bridge between the Model Context Protocol and Selenium WebDriver. It allows AI assistants and other MCP-compatible clients to perform browser automation tasks using a standardized set of tools.

## Supported Browsers

- Chrome
- Firefox
- Headless mode for both browsers

## Project Structure

```
selenium-mcp/
├── src/
│   └── main/
│       └── java/
│           └── io/
│               └── github/
│                       └── mcpselenium/
│                           ├── BrowserSession.java       # The main MCP server implementation
│                           ├── SeleniumMCPServer.java    # Launcher script for the server

├── target/
│   └── selenium-mcp-java.jar         # Compiled JAR with dependencies
└── pom.xml                           # Maven project configuration
```

## Prerequisites

- Java 11 or higher
- Maven (for building)
- Chrome or Firefox browser installed

## Building the Project

```bash
# Clone the repository
git clone https://github.com/manishleoy2k3/selenium-mcp.git
cd selenium-mcp

# Build with Maven
mvn clean package
```

This will create the JAR file at `target/selenium-mcp-java.jar`.

## Usage Options

### Option 1: Command Line Usage

#### Starting the Server

Start the MCP Selenium server from the command line:

```bash
java -jar target/selenium-mcp-java.jar
```

The server will start and wait for commands on standard input. You'll see the server information as initial output.

## Available Tools

### Browser Lifecycle

- `start_browser` - Starts a Chrome or Firefox session.
- `close_browser` - Closes the active browser session.

### Navigation and Page Information

- `navigate` - Opens a URL.
- `get_page_information` - Returns the page title and current URL.
- `get_accessibility_snapshot` - Returns a compact accessibility-oriented DOM snapshot.
- `take_screenshot` - Returns a base64-encoded PNG screenshot.

### Element Interaction

- `find_element` - Finds the first matching element and returns its summary.
- `find_elements` - Finds all matching elements and returns compact summaries.
- `element_exists` - Checks whether a visible element exists.
- `wait_for_element` - Waits for an element to be present, visible, or clickable.
- `click_element` - Clicks a visible element.
- `type_text` - Types text into a form element.
- `get_element_text` - Returns visible element text.
- `get_element_attribute` - Returns an element attribute.
- `select_option` - Selects an option from a `<select>` element.
- `scroll_to_element` - Scrolls an element into view.
- `hover_element` - Moves the pointer over an element.
- `press_key` - Presses a supported keyboard key.
- `upload_file` - Uploads a local file through a file input.

### Frames and Windows

- `switch_to_frame` - Switches to a frame by locator, name, ID, or index.
- `switch_to_default_content` - Returns to the top-level document.
- `get_window_handles` - Returns open browser window handles.
- `switch_to_window` - Switches to a window by handle.

### JavaScript

- `execute_script` - Executes JavaScript in the active page or against a target element.

## Locator Strategies

Element tools support:

- `id`
- `name`
- `css`
- `xpath`
- `class`
- `tag`
- `linkText`
- `partialLinkText`

Most element operations support a configurable timeout between 1 and 120 seconds.


#### Closing the Server

To stop the server, use Ctrl+C in the terminal.


## Integration with AI Systems

MCP Selenium is designed to be used with AI systems that support the Model Context Protocol. To integrate with an AI assistant like Claude:

1. Start the MCP Selenium server
2. Configure the AI system to connect to the server via stdin/stdout
3. Send natural language commands to the AI, which will translate them to MCP commands


### Local Setup

Prerequisites:

- Java 17 or newer
- Maven
- Chrome installed (or set the agent code to bootstrap Firefox)
- An OpenAI-compatible model endpoint with tool-calling support

Build the MCP server and install the agent dependency:

```bash
mvn clean package
```

The agent automatically runs these MCP calls before the model receives the task:

1. `start_browser` with Chrome in visible mode.
2. `navigate` to `https://opensource-demo.orangehrmlive.com/web/index.php/auth/login`.

The model then calls tools such as `find_element`, `send_keys`, `click_element`,
`get_element_text`, and `take_screenshot` through the Java MCP server. Close the
agent with Ctrl+C if a run is interrupted; its cleanup handler closes the browser.


## Troubleshooting

### Common Issues

1. **Browser Not Starting**:
   - Ensure you have Chrome or Firefox installed
   - Try using `"headless":true` in options
   - Check server logs for detailed error messages

2. **Element Not Found**:
   - Verify your locator (by and value)
   - Increase the timeout value
   - Check if the element is in an iframe

3. **Server Not Responding**:
   - Ensure the JSON format is correct
   - Check that each command has a unique tool_call_id
   - Restart the server if it becomes unresponsive

4. **Screenshot Not Saving**:
   - Provide an absolute file path
   - Ensure the directory exists
   - Check file permissions


## Acknowledgements

- Built on Selenium WebDriver for browser automation
- Implements the Model Context Protocol standard
