package de.zalando.ep.zalenium.dashboard;

import de.zalando.ep.zalenium.dashboard.remote.RemoteDashboard;
import de.zalando.ep.zalenium.dashboard.remote.RemoteLogDashboard;
import de.zalando.ep.zalenium.dashboard.remote.RemoteVideoDashboard;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import static java.lang.System.getenv;


/**
 * Class in charge of knowing which dashboard to maintain
 */
@SuppressWarnings("WeakerAccess")
@Slf4j
public class DashboardCollection {
    public static List<RemoteDashboard> remoteDashboards;
    public static DashboardInterface localDashboard = new Dashboard();
    public static boolean remoteDashboardsEnabled = getenv("REMOTE_DASHBOARD_HOST") != null;
    private static boolean initializedRemotes = false;

    private static void initRemoteDashboards() {
        initializedRemotes = true;
        if (remoteDashboardsEnabled) {
            log.info("Setting up dashboard");

            remoteDashboards = new ArrayList<>();
            remoteDashboards.add(new RemoteVideoDashboard());
            remoteDashboards.add(new RemoteLogDashboard("driverlog"));
            remoteDashboards.add(new RemoteLogDashboard("seleniumlog"));
            String host = getenv("REMOTE_DASHBOARD_HOST");
            for (RemoteDashboard dashboard : remoteDashboards) {
                dashboard.setUrl(host);
            }
        }
    }

    public static synchronized void updateDashboard(TestInformation testInformation) {
        String errMsg = "Error during update of dashboard: ";

        log.info("Updating dashboard with {}", testInformation.toString());

        if (!initializedRemotes) {
            initRemoteDashboards();
        }

        if (!remoteDashboardsEnabled) {
            try {
                localDashboard.updateDashboard(testInformation);
            } catch (Exception e) {
                log.warn(errMsg + e.toString());
            }
        } else {
            for (DashboardInterface dashboard : remoteDashboards) {
                try {
                    dashboard.updateDashboard(testInformation);
                } catch (Exception e) {
                    log.warn(errMsg, e);
                }
            }
        }
    }

    public static synchronized void resetDashboard() {
        String errMsg = "Error during cleanup of dashboard: ";

        if (!remoteDashboardsEnabled) {
            try {
                localDashboard.resetDashboard();
            } catch (Exception e) {
                log.warn(errMsg + e.toString());
            }
        } else {
            for (DashboardInterface dashboard : remoteDashboards) {
                try {
                    dashboard.resetDashboard();
                } catch (UnsupportedOperationException e) {
                    //ignore
                } catch (Exception e) {
                    log.warn(errMsg + e.toString());
                }
            }
        }
    }

    public static synchronized void cleanupDashboard() {
        String errMsg = "Error during cleanup of dashboard: ";

        if (!remoteDashboardsEnabled) {
            try {
                localDashboard.cleanupDashboard();
            } catch (Exception e) {
                log.warn(errMsg + e.toString());
            }
        } else {
            for (DashboardInterface dashboard : remoteDashboards) {
                try {
                    dashboard.cleanupDashboard();
                } catch (UnsupportedOperationException e) {
                    //ignore
                } catch (Exception e) {
                    log.warn(errMsg + e.toString());
                }
            }
        }
    }
}
