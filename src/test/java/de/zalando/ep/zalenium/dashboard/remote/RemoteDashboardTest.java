package de.zalando.ep.zalenium.dashboard.remote;

import de.zalando.ep.zalenium.dashboard.Dashboard;
import de.zalando.ep.zalenium.dashboard.TestInformation;
import de.zalando.ep.zalenium.util.CommonProxyUtilities;
import de.zalando.ep.zalenium.util.TestUtils;
import java.util.Date;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class RemoteDashboardTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private List<RemoteDashboard> dashboardsToTest = new ArrayList<>();

    private TestInformation.TestInformationBuilder builder = TestInformation.builder()
            .seleniumSessionId("seleniumSessionId")
            .testName("testName")
            .fileExtension(".mp4")
            .proxyName("proxyName")
            .timestamp(new Date())
            .browser("browser")
            .browserVersion("browserVersion")
            .platform("platform")
            .proxyName("zalenium")
            .testStatus(TestInformation.TestStatus.COMPLETED);

    public RemoteDashboardTest() {
        dashboardsToTest.add(new RemoteVideoDashboard());
        dashboardsToTest.add(new RemoteLogDashboard("driverlog"));
        dashboardsToTest.add(new RemoteLogDashboard("seleniumlog"));
    }

    @Test
    public void remoteHostNotSet() throws Exception {
        FormPoster mockFormPoster = mock(FormPoster.class);
        when(mockFormPoster.getRemoteHost()).thenReturn(null);
        when(mockFormPoster.post(any())).thenThrow(new AssertionError("Remote dashboard classes may not Post() if Url is not set."));

        TestInformation ti = this.builder.build();
        for (RemoteDashboard d : this.dashboardsToTest) {
            d.setFormPoster(mockFormPoster);
            d.updateDashboard(ti);
        }
    }

    @Test
    public void filesDoNotExist() throws Exception {
        FormPoster mockFormPoster = mock(FormPoster.class);
        when(mockFormPoster.post(any())).thenThrow(new AssertionError("Remote dashboard may not Post() when file does not exist"));
        when(mockFormPoster.getRemoteHost()).thenReturn("http://localhost");

        CommonProxyUtilities proxyUtilities = TestUtils.mockCommonProxyUtilitiesForDashboardTesting(temporaryFolder);
        Dashboard.setCommonProxyUtilities(proxyUtilities);
        TestInformation ti = builder.build();
        ti.setVideoRecorded(true);

        for (RemoteDashboard d : this.dashboardsToTest) {
            d.setFormPoster(mockFormPoster);
            try {
                d.updateDashboard(ti);
                Assert.fail("An IOException was expected due to missing file");
            } catch (IOException e) {
                //ignore
            }
        }
    }


}
