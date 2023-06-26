package tau;

import com.google.common.collect.ImmutableList;
import core.Globals;
import core.PlaceInfo;
import org.junit.Before;
import org.junit.Test;
import simudyne.core.abm.testkit.TestKit;

import java.util.ArrayList;

import static com.google.common.truth.Truth.assertThat;

public class StaffTest {

  private TestKit<Globals> testKit;

  @Before
  public void setUp() throws Exception {
    testKit = TestKit.create(Globals.class);
  }

  @Test
  public void testAgeDistribution() {
    testKit.getGlobals().facultyStaffAgentAgeStart = 200;
    testKit.getGlobals().facultyStaffAgentAgeEnd = 300;
    testKit.getGlobals().facultyStaffAgentAgeMean = 250;

    Staff s = testKit.addAgent(Staff.class, Staff::init);

    assertThat(s.age).isGreaterThan(100.0);
  }

  @Test
  public void testComplianceDistribution() {
    testKit.getGlobals().facultyStaffAgentMaskComplianceStart = 1000;
    testKit.getGlobals().facultyStaffAgentMaskComplianceEnd = 2000;
    testKit.getGlobals().facultyStaffAgentQuarantineWhenSymptomaticComplianceStart = 1000;
    testKit.getGlobals().facultyStaffAgentQuarantineWhenSymptomaticComplianceEnd = 2000;
    testKit.getGlobals().facultyStaffAgentReportSymptomsComplianceStart = 1000;
    testKit.getGlobals().facultyStaffAgentReportSymptomsComplianceEnd = 2000;
    testKit.getGlobals().facultyStaffAgentIsolationComplianceStart = 1000;
    testKit.getGlobals().facultyStaffAgentIsolationComplianceEnd = 2000;
    testKit.getGlobals().facultyStaffAgentComplianceIsolateWhenContactNotifiedStart = 1000;
    testKit.getGlobals().facultyStaffAgentComplianceIsolateWhenContactNotifiedEnd = 2000;
    testKit.getGlobals().facultyStaffAgentCompliancePhysicalDistancingStart = 1000;
    testKit.getGlobals().facultyStaffAgentCompliancePhysicalDistancingtEnd = 2000;

    Staff s = testKit.addAgent(Staff.class, Staff::init);

    assertThat(s.complianceMask).isGreaterThan(999.0);
    assertThat(s.compQuarantineWhenSymptomatic).isGreaterThan(999.0);
    assertThat(s.compSymptomsReport).isGreaterThan(999.0);
    assertThat(s.complianceIsolating).isGreaterThan(999.0);
    assertThat(s.complianceIsolateWhenContactNotified).isGreaterThan(999.0);
    assertThat(s.compliancePhysicalDistancing).isGreaterThan(999.0);
  }

  @Test
  public void testGetCurrentAndAdditionalPlaces() {
    Staff s = testKit.addAgent(Staff.class, Staff::init);

    ArrayList<PlaceInfo> regularPlaces = new ArrayList<>();
    ArrayList<PlaceInfo> additionalPlaces = new ArrayList<>();

    regularPlaces.add(PlaceInfo.create("p1", 0));
    regularPlaces.add(PlaceInfo.create("p2", 0));

    s.setCurrentPlaces(regularPlaces);

    ImmutableList<PlaceInfo> places = s.getCurrentAndAdditionalPlaceInfos();

    assertThat(places.size()).isEqualTo(2);

    additionalPlaces.add(PlaceInfo.create("p3", 0));
    additionalPlaces.add(PlaceInfo.create("p4", 0));

    s.setAdditionalPlaces(additionalPlaces);

    places = s.getCurrentAndAdditionalPlaceInfos();

    assertThat(places.size()).isEqualTo(4);
  }
}
