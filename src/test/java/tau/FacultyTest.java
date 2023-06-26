package tau;

import com.google.common.collect.ImmutableList;
import core.Globals;
import core.Person;
import core.PlaceInfo;
import org.junit.Before;
import org.junit.Test;
import simudyne.core.abm.testkit.TestKit;

import java.util.ArrayList;

import static com.google.common.truth.Truth.assertThat;

public class FacultyTest {

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

    Faculty f = testKit.addAgent(Faculty.class, Faculty::init);

    assertThat(f.age).isGreaterThan(100.0);
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

    Faculty f = testKit.addAgent(Faculty.class, Faculty::init);

    assertThat(f.complianceMask).isGreaterThan(999.0);
    assertThat(f.compQuarantineWhenSymptomatic).isGreaterThan(999.0);
    assertThat(f.compSymptomsReport).isGreaterThan(999.0);
    assertThat(f.complianceIsolating).isGreaterThan(999.0);
    assertThat(f.complianceIsolateWhenContactNotified).isGreaterThan(999.0);
    assertThat(f.compliancePhysicalDistancing).isGreaterThan(999.0);
  }

  @Test
  public void testGetCurrentAndAdditionalPlaces() {
    Faculty f = testKit.addAgent(Faculty.class, Faculty::init);

    ArrayList<PlaceInfo> regularPlaces = new ArrayList<>();
    ArrayList<PlaceInfo> additionalPlaces = new ArrayList<>();

    regularPlaces.add(PlaceInfo.create("p1", 0));
    regularPlaces.add(PlaceInfo.create("p2", 0));

    f.setCurrentPlaces(regularPlaces);

    ImmutableList<PlaceInfo> places = f.getCurrentAndAdditionalPlaceInfos();

    assertThat(places.size()).isEqualTo(2);

    additionalPlaces.add(PlaceInfo.create("p3", 0));
    additionalPlaces.add(PlaceInfo.create("p4", 0));

    f.setAdditionalPlaces(additionalPlaces);

    places = f.getCurrentAndAdditionalPlaceInfos();

    assertThat(places.size()).isEqualTo(4);
  }

  @Test
  public void testSuppression() {
    Faculty f = testKit.addAgent(Faculty.class, Faculty::init);
    testKit.getGlobals().suppressAgentType = 2;
    testKit.getGlobals().nAgents = 100;
    testKit.getGlobals().nActiveAgents = 100;
    Person.PersonInitializationInfo init = f.initializationInfo();

    assertThat(init.suppressionSupplier().get()).isFalse();

    testKit.getGlobals().nActiveAgents = 0;
    init = f.initializationInfo();

    assertThat(init.suppressionSupplier().get()).isFalse();

    testKit.getGlobals().suppressAgentType = 0;
    testKit.getGlobals().nActiveAgents = 100;
    init = f.initializationInfo();

    assertThat(init.suppressionSupplier().get()).isFalse();


    testKit.getGlobals().nActiveAgents = 0;
    init = f.initializationInfo();

    assertThat(init.suppressionSupplier().get()).isTrue();

    testKit.getGlobals().suppressAgentType = 1;
    testKit.getGlobals().nAgents = 100;
    testKit.getGlobals().nActiveAgents = 100;
    init = f.initializationInfo();

    assertThat(init.suppressionSupplier().get()).isFalse();

    testKit.getGlobals().nActiveAgents = 0;
    init = f.initializationInfo();

    assertThat(init.suppressionSupplier().get()).isFalse();

  }
}
