package de.zalando.ep.zalenium.dashboard;


import com.google.common.base.Strings;
import com.google.gson.JsonObject;

import de.zalando.ep.zalenium.proxy.RemoteLogFile;
import de.zalando.ep.zalenium.util.CommonProxyUtilities;
import lombok.Builder;
import lombok.Data;
import lombok.With;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * The purpose of this class is to gather the test information that can be used to render the dashboard.
 */
@SuppressWarnings("WeakerAccess")
@Data
@Builder
public class TestInformation {
    private static final String TEST_FILE_NAME_TEMPLATE = "{proxyName}_{testName}_{browser}_{platform}_{timestamp}_{testStatus}";
    private static final String FILE_NAME_TEMPLATE = "{fileName}{fileExtension}";
    private static final String ZALENIUM_PROXY_NAME = "Zalenium";
    private static final String SAUCE_LABS_PROXY_NAME = "SauceLabs";
    private static final String BROWSER_STACK_PROXY_NAME = "BrowserStack";
    private static final String LAMBDA_TEST_PROXY_NAME = "LambdaTest";
    private static final CommonProxyUtilities commonProxyUtilities = new CommonProxyUtilities();
    private final String seleniumSessionId;
    private String testName;
    private Date timestamp;
    private long addedToDashboardTime;
    private String proxyName;
    private String browser;
    private String browserVersion;
    private String platform;
    private String platformVersion;
    private String fileName;
    private String fileExtension;
    private String videoUrl;
    private List<String> logUrls;
    private List<RemoteLogFile> remoteLogFiles;
    private String videoFolderPath;
    private String logsFolderPath;
    private String testNameNoExtension;
    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private String screenDimension;
    private String timeZone;
    private String build;
    private String testFileNameTemplate;
    private String seleniumLogFileName;
    private String browserDriverLogFileName;
    private Date retentionDate;
    private TestStatus testStatus;
    private boolean videoRecorded;
    private JsonObject metadata;

    public boolean isVideoRecorded() {
        return videoRecorded;
    }

    public String getTestName() {
        return Optional.ofNullable(testName).orElse(seleniumSessionId);
    }

    public List<RemoteLogFile> getRemoteLogFiles() {
        return remoteLogFiles == null ? new ArrayList<>() : remoteLogFiles;
    }

    public void buildSeleniumLogFileName() {
        String fileName = Dashboard.LOGS_FOLDER_NAME + "/" + testNameNoExtension + "/";
        if (ZALENIUM_PROXY_NAME.equalsIgnoreCase(proxyName)) {
            seleniumLogFileName = fileName.concat("selenium-multinode-stderr.log");
        } else if (SAUCE_LABS_PROXY_NAME.equalsIgnoreCase(proxyName)) {
            seleniumLogFileName = fileName.concat("selenium-server.log");
        } else if (BROWSER_STACK_PROXY_NAME.equalsIgnoreCase(proxyName)){
            seleniumLogFileName = fileName.concat("selenium.log");
        } else if (LAMBDA_TEST_PROXY_NAME.equalsIgnoreCase(proxyName)){
            seleniumLogFileName = fileName.concat("selenium.log");
        } else {
            seleniumLogFileName = fileName.concat("not_implemented.log");
        }
    }

    public String getSeleniumLogFileName() {
        if (Strings.isNullOrEmpty(seleniumLogFileName)) {
            buildSeleniumLogFileName();
        }
        return seleniumLogFileName;
    }

    public void buildBrowserDriverLogFileName() {
        String fileName = Dashboard.LOGS_FOLDER_NAME + "/" + testNameNoExtension + "/";
        if (ZALENIUM_PROXY_NAME.equalsIgnoreCase(proxyName)) {
            browserDriverLogFileName = fileName.concat(String.format("%s_driver.log", browser.toLowerCase()));
        } else if (SAUCE_LABS_PROXY_NAME.equalsIgnoreCase(proxyName)) {
            browserDriverLogFileName = fileName.concat("log.json");
        } else if (BROWSER_STACK_PROXY_NAME.equalsIgnoreCase(proxyName)){
            browserDriverLogFileName = fileName.concat("browserstack.log");
        }  else if (LAMBDA_TEST_PROXY_NAME.equalsIgnoreCase(proxyName)){
            browserDriverLogFileName = fileName.concat("lambdatest.log");
        } else {
            browserDriverLogFileName = fileName.concat("not_implemented.log");
        }

    }

    public String getBrowserDriverLogFileName() {
        if (Strings.isNullOrEmpty(browserDriverLogFileName)) {
            buildBrowserDriverLogFileName();
        }
        return browserDriverLogFileName;
    }

    @SuppressWarnings("SameParameterValue")
    public void setFileExtension(String fileExtension) {
        this.fileExtension = fileExtension;
        buildVideoFileName();
    }

    public void buildVideoFileName() {
        String buildName;
        if ("N/A".equalsIgnoreCase(this.build) || Strings.isNullOrEmpty(this.build)) {
            buildName = "";
        } else {
            buildName = "/" + this.build.replaceAll("[^a-zA-Z0-9]", "_");
        }

        if(Strings.isNullOrEmpty(this.testFileNameTemplate)) {
            this.testFileNameTemplate = TEST_FILE_NAME_TEMPLATE;
        }

        this.testNameNoExtension = this.testFileNameTemplate
                .replace("{proxyName}", this.proxyName.toLowerCase())
                .replace("{seleniumSessionId}", this.seleniumSessionId)
                .replace("{testName}", getTestName())
                .replace("{browser}", this.browser)
                .replace("{platform}", this.platform)
                .replace("{timestamp}", commonProxyUtilities.getDateAndTimeFormatted(this.timestamp))
                .replace("{testStatus}", getTestStatus().toString())
                .replaceAll("[^a-zA-Z0-9/\\-]", "_");

        this.fileName = FILE_NAME_TEMPLATE.replace("{fileName}", testNameNoExtension)
                .replace("{fileExtension}", fileExtension);

        this.videoFolderPath = commonProxyUtilities.currentLocalPath() + "/" + Dashboard.VIDEOS_FOLDER_NAME + buildName;
        this.logsFolderPath = commonProxyUtilities.currentLocalPath() + "/" + Dashboard.VIDEOS_FOLDER_NAME +
                buildName + "/" + Dashboard.LOGS_FOLDER_NAME + "/" + testNameNoExtension;
    }

    public String getBrowserAndPlatform() {
        if (BROWSER_STACK_PROXY_NAME.equalsIgnoreCase(proxyName)) {
            return String.format("%s %s, %s %s", browser, browserVersion, platform, platformVersion);
        }
        return String.format("%s %s, %s", browser, browserVersion, platform);
    }

    public JsonObject getMetadata() { return this.metadata;}
    public void setMetadata(JsonObject metadata) { this.metadata = metadata;}

    public enum TestStatus {
        COMPLETED(" 'Zalenium', 'TEST COMPLETED', --icon=/home/seluser/images/completed.png"),
        TIMEOUT(" 'Zalenium', 'TEST TIMED OUT', --icon=/home/seluser/images/timeout.png"),
        SUCCESS(" 'Zalenium', 'TEST PASSED', --icon=/home/seluser/images/success.png"),
        FAILED(" 'Zalenium', 'TEST FAILED', --icon=/home/seluser/images/failure.png");

        private String testNotificationMessage;

        TestStatus(String testNotificationMessage) {
            this.testNotificationMessage = testNotificationMessage;
        }

        public String getTestNotificationMessage() {
            return testNotificationMessage;
        }
    }

}
