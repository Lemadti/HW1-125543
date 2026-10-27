package pt.ua.deti.tqs.orbit.bdd;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

/**
 * Starts Cucumber for the acceptance features in {@code src/test/resources/features} (one per user story, plus the
 * time-based rules). They start the app and drive a browser, so this is an {@code *IT}, run by Maven Failsafe with
 * {@code mvn verify}, as the browser tests. HTML report: {@code target/cucumber-report.html}.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "pt.ua.deti.tqs.orbit.bdd")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty, html:target/cucumber-report.html")
public class CucumberIT {
}
