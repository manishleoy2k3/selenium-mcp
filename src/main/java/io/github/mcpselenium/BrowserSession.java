package io.github.mcpselenium;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

public final class BrowserSession {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(15);
    private static final int MAX_SCRIPT_LENGTH = 4000;

    private WebDriver driver;

    public synchronized String startBrowser(String browser, boolean headless) {
        closeBrowser();

        String selectedBrowser = browser == null
                ? "chrome"
                : browser.toLowerCase(Locale.ROOT);

        switch (selectedBrowser) {
            case "chrome": {
                ChromeOptions options = new ChromeOptions();

                if (headless) {
                    options.addArguments("--headless=new");
                }

                options.addArguments(
                        "--disable-notifications",
                        "--disable-popup-blocking",
                        "--start-maximized");

                driver = new ChromeDriver(options);
                break;
            }

            case "firefox": {
                FirefoxOptions options = new FirefoxOptions();

                if (headless) {
                    options.addArguments("-headless");
                }

                driver = new FirefoxDriver(options);
                break;
            }

            default:
                throw new IllegalArgumentException(
                        "Unsupported browser: " + selectedBrowser);
        }

        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(60));

        return selectedBrowser + " browser started successfully";
    }

    public synchronized String navigate(String url) {
        requireDriver();

        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url is required");
        }

        driver.navigate().to(url);

        return "Navigated to " + url
                + ". Title: " + driver.getTitle()
                + ". Current URL: " + driver.getCurrentUrl();
    }

    public synchronized String getPageInformation() {
        requireDriver();

        return "Title: " + driver.getTitle()
                + System.lineSeparator()
                + "URL: " + driver.getCurrentUrl();
    }

    public synchronized String click(
            String strategy,
            String value,
            int timeoutSeconds) {

        WebElement element = waitForClickable(
                locator(strategy, value),
                timeoutSeconds);

        element.click();

        return "Clicked element using " + strategy + "=" + value;
    }

    public synchronized String type(
            String strategy,
            String value,
            String text,
            boolean clearFirst,
            int timeoutSeconds) {

        WebElement element = waitForVisible(
                locator(strategy, value),
                timeoutSeconds);

        if (clearFirst) {
            element.clear();
        }

        element.sendKeys(text);

        return "Entered text into element using " + strategy + "=" + value;
    }

    public synchronized String getText(
            String strategy,
            String value,
            int timeoutSeconds) {

        WebElement element = waitForVisible(
                locator(strategy, value),
                timeoutSeconds);

        return element.getText();
    }

    public synchronized String getAttribute(
            String strategy,
            String value,
            String attribute,
            int timeoutSeconds) {

        WebElement element = waitForVisible(
                locator(strategy, value),
                timeoutSeconds);

        return element.getAttribute(attribute);
    }

    public synchronized String findElement(
        String strategy,
        String value,
        int timeoutSeconds) {

        WebElement element = waitForLocatedElement(
            strategy,
            value,
            timeoutSeconds);

        return summarizeElement(element);
    }
    public synchronized String findElements(
            String strategy,
            String value,
            int timeoutSeconds) {

        By by = locator(strategy, value);

        try {
            new WebDriverWait(driver, timeout(timeoutSeconds))
                    .until(currentDriver ->
                            !currentDriver.findElements(by).isEmpty());
        } catch (TimeoutException ignored) {
            return "Found 0 elements using " + strategy + "=" + value;
        }

        List<WebElement> elements = driver.findElements(by);
        StringBuilder result = new StringBuilder();

        result.append("Found ")
                .append(elements.size())
                .append(" elements using ")
                .append(strategy)
                .append("=")
                .append(value);

        for (int index = 0; index < elements.size(); index++) {
            WebElement element = elements.get(index);
            result.append(System.lineSeparator())
                    .append(index)
                    .append(": tag=")
                    .append(element.getTagName())
                    .append(", displayed=")
                    .append(element.isDisplayed())
                    .append(", enabled=")
                    .append(element.isEnabled())
                    .append(", text=")
                    .append(trim(element.getText(), 120));
        }

        return result.toString();
    }

    public synchronized String elementExists(
            String strategy,
            String value,
            int timeoutSeconds) {

        try {
            waitForVisible(locator(strategy, value), timeoutSeconds);
            return "true";
        } catch (TimeoutException exception) {
            return "false";
        }
    }

    public synchronized String waitForElement(
            String strategy,
            String value,
            String state,
            int timeoutSeconds) {

        By by = locator(strategy, value);
        String expectedState = state == null || state.isBlank()
                ? "visible"
                : state.toLowerCase(Locale.ROOT);

        switch (expectedState) {
            case "present":
                new WebDriverWait(driver, timeout(timeoutSeconds))
                        .until(ExpectedConditions.presenceOfElementLocated(by));
                break;
            case "visible":
                waitForVisible(by, timeoutSeconds);
                break;
            case "clickable":
                waitForClickable(by, timeoutSeconds);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported element state: " + state);
        }

        return "Element is " + expectedState
                + " using " + strategy + "=" + value;
    }

    public synchronized String selectOption(
            String strategy,
            String value,
            String selectBy,
            String option,
            int timeoutSeconds) {

        Select select = new Select(waitForVisible(
                locator(strategy, value),
                timeoutSeconds));

        String method = selectBy.toLowerCase(Locale.ROOT);

        switch (method) {
            case "text":
                select.selectByVisibleText(option);
                break;
            case "value":
                select.selectByValue(option);
                break;
            case "index":
                select.selectByIndex(Integer.parseInt(option));
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported selectBy value: " + selectBy);
        }

        return "Selected option by " + selectBy
                + " from element using " + strategy + "=" + value;
    }

    public synchronized String switchToFrame(
            String strategy,
            String value,
            String nameOrId,
            Integer index,
            int timeoutSeconds) {

        requireDriver();

        if (index != null) {
            driver.switchTo().frame(index);
            return "Switched to frame index " + index;
        }

        if (nameOrId != null && !nameOrId.isBlank()) {
            driver.switchTo().frame(nameOrId);
            return "Switched to frame " + nameOrId;
        }

        WebElement frame = waitForVisible(
                locator(strategy, value),
                timeoutSeconds);

        driver.switchTo().frame(frame);

        return "Switched to frame using " + strategy + "=" + value;
    }

    public synchronized String switchToDefaultContent() {
        requireDriver();
        driver.switchTo().defaultContent();
        return "Switched to default content";
    }

    public synchronized String getWindowHandles() {
        requireDriver();

        String currentHandle = driver.getWindowHandle();
        Set<String> handles = driver.getWindowHandles();

        return "Current window: " + currentHandle
                + System.lineSeparator()
                + "Window handles: " + handles;
    }

    public synchronized String switchToWindow(String handle) {
        requireDriver();

        if (handle == null || handle.isBlank()) {
            throw new IllegalArgumentException("handle is required");
        }

        driver.switchTo().window(handle);

        return "Switched to window " + handle
                + ". Title: " + driver.getTitle()
                + ". URL: " + driver.getCurrentUrl();
    }

    public synchronized String executeScript(
            String script,
            String strategy,
            String value,
            int timeoutSeconds) {

        requireDriver();

        if (script == null || script.isBlank()) {
            throw new IllegalArgumentException("script is required");
        }

        if (script.length() > MAX_SCRIPT_LENGTH) {
            throw new IllegalArgumentException(
                    "script must be " + MAX_SCRIPT_LENGTH
                            + " characters or fewer");
        }

        if (!(driver instanceof JavascriptExecutor)) {
            throw new IllegalStateException(
                    "Current driver does not support JavaScript execution");
        }

        JavascriptExecutor executor = (JavascriptExecutor) driver;

        Object result;

        if (strategy != null && !strategy.isBlank()) {
            WebElement element = waitForVisible(
                    locator(strategy, value),
                    timeoutSeconds);
            result = executor.executeScript(script, element);
        } else {
            result = executor.executeScript(script);
        }

        return result == null ? "null" : result.toString();
    }

    public synchronized String scrollToElement(
            String strategy,
            String value,
            int timeoutSeconds) {

        WebElement element = waitForVisible(
                locator(strategy, value),
                timeoutSeconds);

        javascriptExecutor().executeScript(
                "arguments[0].scrollIntoView({block:'center',inline:'nearest'});",
                element);

        return "Scrolled to element using " + strategy + "=" + value;
    }

    public synchronized String hoverElement(
            String strategy,
            String value,
            int timeoutSeconds) {

        WebElement element = waitForVisible(
                locator(strategy, value),
                timeoutSeconds);

        new Actions(driver).moveToElement(element).perform();

        return "Hovered over element using " + strategy + "=" + value;
    }

    public synchronized String pressKey(
            String key,
            String strategy,
            String value,
            int timeoutSeconds) {

        requireDriver();

        Keys seleniumKey = keyValue(key);

        if (strategy != null && !strategy.isBlank()) {
            WebElement element = waitForVisible(
                    locator(strategy, value),
                    timeoutSeconds);
            element.sendKeys(seleniumKey);
        } else {
            new Actions(driver).sendKeys(seleniumKey).perform();
        }

        return "Pressed key " + key;
    }

    public synchronized String uploadFile(
            String strategy,
            String value,
            String filePath,
            int timeoutSeconds) {

        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("filePath is required");
        }

        Path path = Path.of(filePath).toAbsolutePath();

        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException(
                    "File does not exist: " + path);
        }

        WebElement element = waitForVisible(
                locator(strategy, value),
                timeoutSeconds);

        element.sendKeys(path.toString());

        return "Uploaded file " + path;
    }

    public synchronized String takeScreenshotBase64() {
        requireDriver();

        if (!(driver instanceof TakesScreenshot)) {
            throw new IllegalStateException(
                    "Current driver does not support screenshots");
        }

        TakesScreenshot screenshotDriver = (TakesScreenshot) driver;
        byte[] bytes = screenshotDriver.getScreenshotAs(OutputType.BYTES);

        return Base64.getEncoder().encodeToString(bytes);
    }

    public synchronized String getAccessibilitySnapshot() {
        requireDriver();

        String script =
            "return JSON.stringify((() => {\n"
            + "  const visible = element => {\n"
            + "    const style = window.getComputedStyle(element);\n"
            + "    const rect = element.getBoundingClientRect();\n"
            + "    return style.visibility !== 'hidden'\n"
            + "      && style.display !== 'none'\n"
            + "      && rect.width > 0\n"
            + "      && rect.height > 0;\n"
            + "  };\n\n"
            + "  const nameOf = element =>\n"
            + "    element.getAttribute('aria-label')\n"
            + "    || element.getAttribute('alt')\n"
            + "    || element.getAttribute('title')\n"
            + "    || element.innerText\n"
            + "    || element.value\n"
            + "    || '';\n\n"
            + "  const roleOf = element =>\n"
            + "    element.getAttribute('role')\n"
            + "    || ({ A: 'link', BUTTON: 'button', INPUT: element.type || 'input',\n"
            + "         SELECT: 'select', TEXTAREA: 'textbox', IMG: 'image' })[element.tagName]\n"
            + "    || '';\n\n"
            + "  const selector = 'a,button,input,select,textarea,img,[role],'\n"
            + "    + '[aria-label],[aria-labelledby],h1,h2,h3,h4,h5,h6';\n\n"
            + "  const nodes = Array.from(document.body.querySelectorAll(selector))\n"
            + "    .filter(visible).slice(0, 200)\n"
            + "    .map((element, index) => ({\n"
            + "      index, tag: element.tagName.toLowerCase(), role: roleOf(element),\n"
            + "      name: nameOf(element).replace(/\\s+/g, ' ').trim().slice(0, 160),\n"
            + "      disabled: element.disabled === true, href: element.href || null\n"
            + "    }));\n\n"
            + "  return { title: document.title, url: location.href, count: nodes.length, nodes };\n"
            + "})(), null, 2);\n";

        Object result = javascriptExecutor().executeScript(script);

        return result == null ? "{}" : result.toString();
    }

    public synchronized String getPageSource() {
        requireDriver();
        return driver.getPageSource();
    }

    public synchronized byte[] takeScreenshot() {
        requireDriver();

        if (!(driver instanceof TakesScreenshot)) {
            throw new IllegalStateException(
                    "Current driver does not support screenshots");
        }

        TakesScreenshot screenshotDriver = (TakesScreenshot) driver;
        return screenshotDriver.getScreenshotAs(OutputType.BYTES);
    }

    public synchronized String closeBrowser() {
        if (driver != null) {
            try {
                driver.quit();
            } finally {
                driver = null;
            }

            return "Browser closed";
        }

        return "No browser session was active";
    }

    private WebElement waitForLocatedElement(
        String strategy,
        String value,
        int timeoutSeconds) {

        requireDriver();

        return new WebDriverWait(driver, timeout(timeoutSeconds))
            .until(ExpectedConditions.presenceOfElementLocated(
                    locator(strategy, value)));
    }

    private String summarizeElement(WebElement element) {
        return "Element: tag="
            + element.getTagName()
            + ", displayed="
            + element.isDisplayed()
            + ", enabled="
            + element.isEnabled()
            + ", text="
            + trim(element.getText(), 120);
    }

    private WebElement waitForVisible(By by, int timeoutSeconds) {
        requireDriver();

        return new WebDriverWait(driver, timeout(timeoutSeconds))
                .until(ExpectedConditions.visibilityOfElementLocated(by));
    }

    private WebElement waitForClickable(By by, int timeoutSeconds) {
        requireDriver();

        return new WebDriverWait(driver, timeout(timeoutSeconds))
                .until(ExpectedConditions.elementToBeClickable(by));
    }

    private JavascriptExecutor javascriptExecutor() {
        requireDriver();

        if (!(driver instanceof JavascriptExecutor)) {
            throw new IllegalStateException(
                    "Current driver does not support JavaScript execution");
        }

        return (JavascriptExecutor) driver;
    }

    private Duration timeout(int timeoutSeconds) {
        return timeoutSeconds > 0
                ? Duration.ofSeconds(timeoutSeconds)
                : DEFAULT_TIMEOUT;
    }

    private By locator(String strategy, String value) {
        if (strategy == null || strategy.isBlank()) {
            throw new IllegalArgumentException(
                    "locator strategy is required");
        }

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "locator value is required");
        }

        switch (strategy.toLowerCase(Locale.ROOT)) {
            case "id":
                return By.id(value);
            case "name":
                return By.name(value);
            case "css":
            case "cssselector":
                return By.cssSelector(value);
            case "xpath":
                return By.xpath(value);
            case "class":
            case "classname":
                return By.className(value);
            case "tag":
            case "tagname":
                return By.tagName(value);
            case "linktext":
                return By.linkText(value);
            case "partiallinktext":
                return By.partialLinkText(value);
            default:
                throw new IllegalArgumentException(
                        "Unsupported locator strategy: " + strategy);
        }
    }

    private Keys keyValue(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key is required");
        }

        switch (key.toUpperCase(Locale.ROOT)) {
            case "ENTER":
                return Keys.ENTER;
            case "TAB":
                return Keys.TAB;
            case "ESCAPE":
                return Keys.ESCAPE;
            case "SPACE":
                return Keys.SPACE;
            case "BACKSPACE":
                return Keys.BACK_SPACE;
            case "DELETE":
                return Keys.DELETE;
            case "ARROW_UP":
                return Keys.ARROW_UP;
            case "ARROW_DOWN":
                return Keys.ARROW_DOWN;
            case "ARROW_LEFT":
                return Keys.ARROW_LEFT;
            case "ARROW_RIGHT":
                return Keys.ARROW_RIGHT;
            case "HOME":
                return Keys.HOME;
            case "END":
                return Keys.END;
            case "PAGE_UP":
                return Keys.PAGE_UP;
            case "PAGE_DOWN":
                return Keys.PAGE_DOWN;
            default:
                throw new IllegalArgumentException(
                        "Unsupported key: " + key);
        }
    }

    private String trim(String value, int maxLength) {
        if (value == null) {
            return "";
        }

        String normalized = value.replaceAll("\\s+", " ").trim();

        return normalized.length() <= maxLength
                ? normalized
                : normalized.substring(0, maxLength) + "...";
    }

    private void requireDriver() {
        if (driver == null) {
            throw new IllegalStateException(
                    "No browser session. Call start_browser first.");
        }
    }
}
