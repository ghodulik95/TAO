package tau;

import core.Globals;
import org.junit.Before;
import org.junit.Test;
import simudyne.core.abm.testkit.TestKit;

import static com.google.common.truth.Truth.assertThat;

public class TAUExternalDataTest {
  private TestKit<Globals> testKit;

  @Before
  public void setUp() throws Exception {
    testKit = TestKit.create(Globals.class);
  }

  @Test
  public void testNoneDataSource() {
    testKit.getGlobals().externalDataSource = TAUExternalData.DataSource.NONE.ordinal();

    assertThat(testKit.getGlobals().getExternalInfectionRate(null))
        .isEqualTo(testKit.getGlobals().baseOffCampusExternalInfectionRate);

    TAUExternalData externalData = new TAUExternalData();
    testKit.getGlobals().overallExternalInfectionRateFromData =
        externalData.getExternalDataInfectionRate("stateFile",
            "county",
            "state",
            testKit.getGlobals().externalDataSource);

    assertThat(testKit.getGlobals().getExternalInfectionRate(null))
        .isEqualTo(testKit.getGlobals().baseOffCampusExternalInfectionRate);
  }
}
