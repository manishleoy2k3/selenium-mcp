package io.github.mcpselenium;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;

public final class SeleniumMcpServer {

    private static final BrowserSession BROWSER = new BrowserSession();

    private SeleniumMcpServer() {
    }

    public static void main(String[] args) throws InterruptedException {
        System.err.println("Starting Selenium MCP server");

        StdioServerTransportProvider transport =
                new StdioServerTransportProvider(McpJsonDefaults.getMapper());

        McpSyncServer server = McpServer.sync(transport)
                .serverInfo("selenium-mcp-java", "1.0.0")
                .capabilities(ServerCapabilities.builder()
                        .tools(true)
                        .logging()
                        .build())
                .build();

        server.addTool(startBrowserTool());
        server.addTool(navigateTool());
        server.addTool(pageInformationTool());
        server.addTool(clickTool());
        server.addTool(typeTool());
        server.addTool(getTextTool());
        server.addTool(getAttributeTool());
        server.addTool(findElementTool());
        server.addTool(findElementsTool());
        server.addTool(elementExistsTool());
        server.addTool(waitForElementTool());
        server.addTool(selectOptionTool());
        server.addTool(switchToFrameTool());
        server.addTool(switchToDefaultContentTool());
        server.addTool(getWindowHandlesTool());
        server.addTool(switchToWindowTool());
        server.addTool(executeScriptTool());
        server.addTool(scrollToElementTool());
        server.addTool(hoverElementTool());
        server.addTool(pressKeyTool());
        server.addTool(uploadFileTool());
        server.addTool(takeScreenshotTool());
        server.addTool(accessibilitySnapshotTool());
        server.addTool(closeBrowserTool());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                BROWSER.closeBrowser();
                server.close();
            } catch (Exception exception) {
                System.err.println("Shutdown error: " + exception.getMessage());
            }
        }));

        System.err.println("Selenium MCP server initialized");
        new CountDownLatch(1).await();
    }

    private static SyncToolSpecification startBrowserTool() {
        String schema = "{"
                + "\"type\":\"object\","
                + "\"properties\":{"
                + "\"browser\":{"
                + "\"type\":\"string\","
                + "\"enum\":[\"chrome\",\"firefox\"]"
                + "},"
                + "\"headless\":{"
                + "\"type\":\"boolean\","
                + "\"default\":false"
                + "}"
                + "},"
                + "\"required\":[\"browser\"],"
                + "\"additionalProperties\":false"
                + "}";

        return SyncToolSpecification.builder()
                .tool(Tool.builder("start_browser", schemaMap(schema))
                        .description("Starts a Chrome or Firefox Selenium session.")
                        .build())
                .callHandler((exchange, request) -> execute(() ->
                        BROWSER.startBrowser(
                                stringArgument(request.arguments(), "browser"),
                                booleanArgument(
                                        request.arguments(),
                                        "headless",
                                        false))))
                .build();
    }

    private static SyncToolSpecification navigateTool() {
        String schema = "{"
                + "\"type\":\"object\","
                + "\"properties\":{\"url\":{\"type\":\"string\"}},"
                + "\"required\":[\"url\"],"
                + "\"additionalProperties\":false"
                + "}";

        return SyncToolSpecification.builder()
                .tool(Tool.builder("navigate", schemaMap(schema))
                        .description("Navigates the active browser to a URL.")
                        .build())
                .callHandler((exchange, request) -> execute(() ->
                        BROWSER.navigate(
                                stringArgument(request.arguments(), "url"))))
                .build();
    }

    private static SyncToolSpecification pageInformationTool() {
        return noArgumentTool(
                "get_page_information",
                "Returns the active page title and URL.",
                BROWSER::getPageInformation);
    }

    private static SyncToolSpecification clickTool() {
        return elementTool(
                "click_element",
                "Clicks a visible and clickable page element.",
                request -> BROWSER.click(
                        stringArgument(request, "strategy"),
                        stringArgument(request, "value"),
                        intArgument(request, "timeoutSeconds", 15)));
    }

    private static SyncToolSpecification typeTool() {
        String schema = "{"
                + "\"type\":\"object\","
                + "\"properties\":{"
                + "\"strategy\":{\"type\":\"string\",\"enum\":[\"id\",\"name\",\"css\",\"xpath\",\"class\",\"tag\",\"linkText\",\"partialLinkText\"]},"
                + "\"value\":{\"type\":\"string\"},"
                + "\"text\":{\"type\":\"string\"},"
                + "\"clearFirst\":{\"type\":\"boolean\",\"default\":true},"
                + "\"timeoutSeconds\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":120,\"default\":15}"
                + "},\"required\":[\"strategy\",\"value\",\"text\"],\"additionalProperties\":false}";

        return SyncToolSpecification.builder()
                .tool(Tool.builder("type_text", schemaMap(schema))
                        .description("Types text into a visible form element.")
                        .build())
                .callHandler((exchange, request) -> execute(() ->
                        BROWSER.type(
                                stringArgument(request.arguments(), "strategy"),
                                stringArgument(request.arguments(), "value"),
                                stringArgument(request.arguments(), "text"),
                                booleanArgument(
                                        request.arguments(),
                                        "clearFirst",
                                        true),
                                intArgument(
                                        request.arguments(),
                                        "timeoutSeconds",
                                        15))))
                .build();
    }

    private static SyncToolSpecification getTextTool() {
        return elementTool(
                "get_element_text",
                "Returns visible text from a page element.",
                request -> BROWSER.getText(
                        stringArgument(request, "strategy"),
                        stringArgument(request, "value"),
                        intArgument(request, "timeoutSeconds", 15)));
    }

    private static SyncToolSpecification getAttributeTool() {
        String schema = "{\n"
                + "  \"type\": \"object\",\n"
                + "  \"properties\": {\n"
                + "    \"strategy\": { \"type\": \"string\", \"enum\": [\"id\", \"name\", \"css\", \"xpath\", \"class\", \"tag\", \"linkText\", \"partialLinkText\"] },\n"
                + "    \"value\": { \"type\": \"string\" },\n"
                + "    \"attribute\": { \"type\": \"string\" },\n"
                + "    \"timeoutSeconds\": { \"type\": \"integer\", \"minimum\": 1, \"maximum\": 120, \"default\": 15 }\n"
                + "  },\n"
                + "  \"required\": [\"strategy\", \"value\", \"attribute\"],\n"
                + "  \"additionalProperties\": false\n"
                + "}\n";

        return SyncToolSpecification.builder()
                .tool(Tool.builder("get_element_attribute", schemaMap(schema))
                        .description("Returns an attribute from a page element.")
                        .build())
                .callHandler((exchange, request) -> execute(() ->
                        BROWSER.getAttribute(
                                stringArgument(request.arguments(), "strategy"),
                                stringArgument(request.arguments(), "value"),
                                stringArgument(request.arguments(), "attribute"),
                                intArgument(
                                        request.arguments(),
                                        "timeoutSeconds",
                                        15))))
                .build();
    }

private static SyncToolSpecification findElementTool() {
        return elementTool(
            "find_element",
            "Finds the first matching page element and returns a compact summary. Fails if no element matches.",
            request -> BROWSER.findElement(
                    stringArgument(request, "strategy"),
                    stringArgument(request, "value"),
                    intArgument(request, "timeoutSeconds", 15)));
    }
    private static SyncToolSpecification findElementsTool() {
        return elementTool(
                "find_elements",
                "Finds matching page elements and returns a compact summary.",
                request -> BROWSER.findElements(
                        stringArgument(request, "strategy"),
                        stringArgument(request, "value"),
                        intArgument(request, "timeoutSeconds", 15)));
    }

    private static SyncToolSpecification elementExistsTool() {
        return elementTool(
                "element_exists",
                "Returns true when a visible matching element exists.",
                request -> BROWSER.elementExists(
                        stringArgument(request, "strategy"),
                        stringArgument(request, "value"),
                        intArgument(request, "timeoutSeconds", 5)));
    }

    private static SyncToolSpecification waitForElementTool() {
        String schema = "{\n"
                + "  \"type\": \"object\",\n"
                + "  \"properties\": {\n"
                + "    \"strategy\": { \"type\": \"string\", \"enum\": [\"id\", \"name\", \"css\", \"xpath\", \"class\", \"tag\", \"linkText\", \"partialLinkText\"] },\n"
                + "    \"value\": { \"type\": \"string\" },\n"
                + "    \"state\": { \"type\": \"string\", \"enum\": [\"present\", \"visible\", \"clickable\"], \"default\": \"visible\" },\n"
                + "    \"timeoutSeconds\": { \"type\": \"integer\", \"minimum\": 1, \"maximum\": 120, \"default\": 15 }\n"
                + "  },\n"
                + "  \"required\": [\"strategy\", \"value\"],\n"
                + "  \"additionalProperties\": false\n"
                + "}\n";

        return SyncToolSpecification.builder()
                .tool(Tool.builder("wait_for_element", schemaMap(schema))
                        .description("Waits for an element to become present, visible, or clickable.")
                        .build())
                .callHandler((exchange, request) -> execute(() ->
                        BROWSER.waitForElement(
                                stringArgument(request.arguments(), "strategy"),
                                stringArgument(request.arguments(), "value"),
                                stringArgument(
                                        request.arguments(),
                                        "state",
                                        "visible"),
                                intArgument(
                                        request.arguments(),
                                        "timeoutSeconds",
                                        15))))
                .build();
    }

    private static SyncToolSpecification selectOptionTool() {
        String schema = "{\n"
                + "  \"type\": \"object\",\n"
                + "  \"properties\": {\n"
                + "    \"strategy\": { \"type\": \"string\", \"enum\": [\"id\", \"name\", \"css\", \"xpath\", \"class\", \"tag\", \"linkText\", \"partialLinkText\"] },\n"
                + "    \"value\": { \"type\": \"string\" },\n"
                + "    \"selectBy\": { \"type\": \"string\", \"enum\": [\"text\", \"value\", \"index\"] },\n"
                + "    \"option\": { \"type\": \"string\" },\n"
                + "    \"timeoutSeconds\": { \"type\": \"integer\", \"minimum\": 1, \"maximum\": 120, \"default\": 15 }\n"
                + "  },\n"
                + "  \"required\": [\"strategy\", \"value\", \"selectBy\", \"option\"],\n"
                + "  \"additionalProperties\": false\n"
                + "}\n";

        return SyncToolSpecification.builder()
                .tool(Tool.builder("select_option", schemaMap(schema))
                        .description("Selects an option from a select element.")
                        .build())
                .callHandler((exchange, request) -> execute(() ->
                        BROWSER.selectOption(
                                stringArgument(request.arguments(), "strategy"),
                                stringArgument(request.arguments(), "value"),
                                stringArgument(request.arguments(), "selectBy"),
                                stringArgument(request.arguments(), "option"),
                                intArgument(
                                        request.arguments(),
                                        "timeoutSeconds",
                                        15))))
                .build();
    }

    private static SyncToolSpecification switchToFrameTool() {
        String schema = "{\n"
                + "  \"type\": \"object\",\n"
                + "  \"properties\": {\n"
                + "    \"strategy\": { \"type\": \"string\", \"enum\": [\"id\", \"name\", \"css\", \"xpath\", \"class\", \"tag\", \"linkText\", \"partialLinkText\"] },\n"
                + "    \"value\": { \"type\": \"string\" },\n"
                + "    \"nameOrId\": { \"type\": \"string\" },\n"
                + "    \"index\": { \"type\": \"integer\", \"minimum\": 0 },\n"
                + "    \"timeoutSeconds\": { \"type\": \"integer\", \"minimum\": 1, \"maximum\": 120, \"default\": 15 }\n"
                + "  },\n"
                + "  \"additionalProperties\": false\n"
                + "}\n";

        return SyncToolSpecification.builder()
                .tool(Tool.builder("switch_to_frame", schemaMap(schema))
                        .description("Switches into a frame by locator, name/id, or index.")
                        .build())
                .callHandler((exchange, request) -> execute(() ->
                        BROWSER.switchToFrame(
                                stringArgumentOrNull(
                                        request.arguments(),
                                        "strategy"),
                                stringArgumentOrNull(
                                        request.arguments(),
                                        "value"),
                                stringArgumentOrNull(
                                        request.arguments(),
                                        "nameOrId"),
                                integerArgumentOrNull(
                                        request.arguments(),
                                        "index"),
                                intArgument(
                                        request.arguments(),
                                        "timeoutSeconds",
                                        15))))
                .build();
    }

    private static SyncToolSpecification switchToDefaultContentTool() {
        return noArgumentTool(
                "switch_to_default_content",
                "Switches back to the top-level document.",
                BROWSER::switchToDefaultContent);
    }

    private static SyncToolSpecification getWindowHandlesTool() {
        return noArgumentTool(
                "get_window_handles",
                "Returns open browser window handles.",
                BROWSER::getWindowHandles);
    }

    private static SyncToolSpecification switchToWindowTool() {
        String schema = "{\n"
                + "  \"type\": \"object\",\n"
                + "  \"properties\": {\n"
                + "    \"handle\": { \"type\": \"string\" }\n"
                + "  },\n"
                + "  \"required\": [\"handle\"],\n"
                + "  \"additionalProperties\": false\n"
                + "}\n";

        return SyncToolSpecification.builder()
                .tool(Tool.builder("switch_to_window", schemaMap(schema))
                        .description("Switches to a browser window by handle.")
                        .build())
                .callHandler((exchange, request) -> execute(() ->
                        BROWSER.switchToWindow(
                                stringArgument(request.arguments(), "handle"))))
                .build();
    }

    private static SyncToolSpecification executeScriptTool() {
        String schema = "{\n"
                + "  \"type\": \"object\",\n"
                + "  \"properties\": {\n"
                + "    \"script\": { \"type\": \"string\", \"maxLength\": 4000 },\n"
                + "    \"strategy\": { \"type\": \"string\", \"enum\": [\"id\", \"name\", \"css\", \"xpath\", \"class\", \"tag\", \"linkText\", \"partialLinkText\"] },\n"
                + "    \"value\": { \"type\": \"string\" },\n"
                + "    \"timeoutSeconds\": { \"type\": \"integer\", \"minimum\": 1, \"maximum\": 120, \"default\": 15 }\n"
                + "  },\n"
                + "  \"required\": [\"script\"],\n"
                + "  \"additionalProperties\": false\n"
                + "}\n";

        return SyncToolSpecification.builder()
                .tool(Tool.builder("execute_script", schemaMap(schema))
                        .description("Executes browser JavaScript in the active page only. Does not execute Java or shell commands.")
                        .build())
                .callHandler((exchange, request) -> execute(() ->
                        BROWSER.executeScript(
                                stringArgument(request.arguments(), "script"),
                                stringArgumentOrNull(
                                        request.arguments(),
                                        "strategy"),
                                stringArgumentOrNull(
                                        request.arguments(),
                                        "value"),
                                intArgument(
                                        request.arguments(),
                                        "timeoutSeconds",
                                        15))))
                .build();
    }

    private static SyncToolSpecification scrollToElementTool() {
        return elementTool(
                "scroll_to_element",
                "Scrolls a visible element into view.",
                request -> BROWSER.scrollToElement(
                        stringArgument(request, "strategy"),
                        stringArgument(request, "value"),
                        intArgument(request, "timeoutSeconds", 15)));
    }

    private static SyncToolSpecification hoverElementTool() {
        return elementTool(
                "hover_element",
                "Moves the pointer over a visible element.",
                request -> BROWSER.hoverElement(
                        stringArgument(request, "strategy"),
                        stringArgument(request, "value"),
                        intArgument(request, "timeoutSeconds", 15)));
    }

    private static SyncToolSpecification pressKeyTool() {
        String schema = "{\n"
                + "  \"type\": \"object\",\n"
                + "  \"properties\": {\n"
                + "    \"key\": { \"type\": \"string\" },\n"
                + "    \"strategy\": { \"type\": \"string\" },\n"
                + "    \"value\": { \"type\": \"string\" },\n"
                + "    \"timeoutSeconds\": { \"type\": \"integer\", \"minimum\": 1, \"maximum\": 120, \"default\": 15 }\n"
                + "  },\n"
                + "  \"required\": [\"key\"],\n"
                + "  \"additionalProperties\": false\n"
                + "}\n";

        return SyncToolSpecification.builder()
                .tool(Tool.builder("press_key", schemaMap(schema))
                        .description("Presses a supported keyboard key globally or on a target element.")
                        .build())
                .callHandler((exchange, request) -> execute(() ->
                        BROWSER.pressKey(
                                stringArgument(request.arguments(), "key"),
                                stringArgumentOrNull(
                                        request.arguments(),
                                        "strategy"),
                                stringArgumentOrNull(
                                        request.arguments(),
                                        "value"),
                                intArgument(
                                        request.arguments(),
                                        "timeoutSeconds",
                                        15))))
                .build();
    }

    private static SyncToolSpecification uploadFileTool() {
        String schema = "{\n"
                + "  \"type\": \"object\",\n"
                + "  \"properties\": {\n"
                + "    \"strategy\": { \"type\": \"string\" },\n"
                + "    \"value\": { \"type\": \"string\" },\n"
                + "    \"filePath\": { \"type\": \"string\" },\n"
                + "    \"timeoutSeconds\": { \"type\": \"integer\", \"minimum\": 1, \"maximum\": 120, \"default\": 15 }\n"
                + "  },\n"
                + "  \"required\": [\"strategy\", \"value\", \"filePath\"],\n"
                + "  \"additionalProperties\": false\n"
                + "}\n";

        return SyncToolSpecification.builder()
                .tool(Tool.builder("upload_file", schemaMap(schema))
                        .description("Uploads a local file through an input[type=file] element.")
                        .build())
                .callHandler((exchange, request) -> execute(() ->
                        BROWSER.uploadFile(
                                stringArgument(request.arguments(), "strategy"),
                                stringArgument(request.arguments(), "value"),
                                stringArgument(request.arguments(), "filePath"),
                                intArgument(
                                        request.arguments(),
                                        "timeoutSeconds",
                                        15))))
                .build();
    }

    private static SyncToolSpecification takeScreenshotTool() {
        return noArgumentTool(
                "take_screenshot",
                "Returns a base64-encoded PNG screenshot.",
                BROWSER::takeScreenshotBase64);
    }

    private static SyncToolSpecification accessibilitySnapshotTool() {
        return noArgumentTool(
                "get_accessibility_snapshot",
                "Returns a compact accessibility-oriented DOM snapshot.",
                BROWSER::getAccessibilitySnapshot);
    }

    private static SyncToolSpecification closeBrowserTool() {
        return noArgumentTool(
                "close_browser",
                "Closes the active Selenium browser session.",
                BROWSER::closeBrowser);
    }

    private static SyncToolSpecification elementTool(
            String name,
            String description,
            ToolOperation operation) {
                String schema = "{\n"
                        + "  \"type\": \"object\",\n"
                        + "  \"properties\": {\n"
                        + "    \"strategy\": { \"type\": \"string\", \"enum\": [\"id\", \"name\", \"css\", \"xpath\", \"class\", \"tag\", \"linkText\", \"partialLinkText\"] },\n"
                        + "    \"value\": { \"type\": \"string\" },\n"
                        + "    \"timeoutSeconds\": { \"type\": \"integer\", \"minimum\": 1, \"maximum\": 120, \"default\": 15 }\n"
                        + "  },\n"
                        + "  \"required\": [\"strategy\", \"value\"],\n"
                        + "  \"additionalProperties\": false\n"
                        + "}\n";

        return SyncToolSpecification.builder()
                .tool(Tool.builder(name, schemaMap(schema))
                        .description(description)
                        .build())
                .callHandler((exchange, request) -> execute(() ->
                        operation.run(request.arguments())))
                .build();
    }

    private static SyncToolSpecification noArgumentTool(
            String name,
            String description,
            ToolAction action) {

        String schema = "{\n"
                + "  \"type\": \"object\",\n"
                + "  \"properties\": {},\n"
                + "  \"additionalProperties\": false\n"
                + "}\n";

        return SyncToolSpecification.builder()
                .tool(Tool.builder(name, schemaMap(schema))
                        .description(description)
                        .build())
                .callHandler((exchange, request) -> execute(action))
                .build();
    }

    private static CallToolResult execute(ToolAction toolAction) {
        try {
            String result = toolAction.run();

            return CallToolResult.builder()
                    .content(List.of(new TextContent(result)))
                    .isError(false)
                    .build();
        } catch (Exception exception) {
            System.err.println(
                    "Tool execution failed: " + exception.getMessage());

            return CallToolResult.builder()
                    .content(List.of(new TextContent(
                            "Tool failed: " + exception.getMessage())))
                    .isError(true)
                    .build();
        }
    }

    private static String stringArgument(
            Map<String, Object> arguments,
            String name) {

        Object value = arguments.get(name);

        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }

        return value.toString();
    }

    private static String stringArgument(
            Map<String, Object> arguments,
            String name,
            String defaultValue) {

        Object value = arguments.get(name);

        return value == null || value.toString().isBlank()
                ? defaultValue
                : value.toString();
    }

    private static String stringArgumentOrNull(
            Map<String, Object> arguments,
            String name) {

        Object value = arguments.get(name);

        return value == null || value.toString().isBlank()
                ? null
                : value.toString();
    }

    private static boolean booleanArgument(
            Map<String, Object> arguments,
            String name,
            boolean defaultValue) {

        Object value = arguments.get(name);

        return value == null
                ? defaultValue
                : Boolean.parseBoolean(value.toString());
    }

    private static int intArgument(
            Map<String, Object> arguments,
            String name,
            int defaultValue) {

        Object value = arguments.get(name);

        if (value == null) {
            return defaultValue;
        }

                if (value instanceof Number) {
                        return ((Number) value).intValue();
        }

        return Integer.parseInt(value.toString());
    }

    private static Integer integerArgumentOrNull(
            Map<String, Object> arguments,
            String name) {

        Object value = arguments.get(name);

        if (value == null) {
            return null;
        }

                if (value instanceof Number) {
                        return ((Number) value).intValue();
        }

        return Integer.parseInt(value.toString());
    }

    private static Map<String, Object> schemaMap(String schema) {
        try {
            return new ObjectMapper().readValue(
                    schema,
                    new TypeReference<Map<String, Object>>() {
                    });
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "Invalid tool schema",
                    exception);
        }
    }

    @FunctionalInterface
    private interface ToolAction {
        String run() throws Exception;
    }

    @FunctionalInterface
    private interface ToolOperation {
        String run(Map<String, Object> arguments) throws Exception;
    }
}
