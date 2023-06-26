package tau;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import core.*;
import org.junit.Before;
import org.junit.Test;
import simudyne.core.abm.testkit.TestKit;
import simudyne.core.abm.testkit.TestResult;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;

public class StudentTest {

  private TestKit<Globals> testKit;

  @Before
  public void setUp() {
    testKit = TestKit.create(Globals.class);
  }

  @Test
  public void testAgeDistribution() {
    testKit.getGlobals().studentAgentAgeStart = 200;
    testKit.getGlobals().studentAgentAgeEnd = 300;

    Student s = testKit.addAgent(Student.class, Student::init);

    assertThat(s.age).isGreaterThan(100.0);
  }

  @Test
  public void testComplianceDistribution() {
    testKit.getGlobals().studentAgentMaskComplianceStart = 1000;
    testKit.getGlobals().studentAgentMaskComplianceEnd = 2000;
    testKit.getGlobals().studentAgentQuarantineWhenSymptomaticComplianceStart = 1000;
    testKit.getGlobals().studentAgentQuarantineWhenSymptomaticComplianceEnd = 2000;
    testKit.getGlobals().studentAgentReportSymptomsComplianceStart = 1000;
    testKit.getGlobals().studentAgentReportSymptomsComplianceEnd = 2000;
    testKit.getGlobals().studentAgentIsolationComplianceStart = 1000;
    testKit.getGlobals().studentAgentIsolationComplianceEnd = 2000;
    testKit.getGlobals().studentAgentComplianceIsolateWhenContactNotifiedStart = 1000;
    testKit.getGlobals().studentAgentComplianceIsolateWhenContactNotifiedEnd = 2000;
    testKit.getGlobals().studentAgentCompliancePhysicalDistancingStart = 1000;
    testKit.getGlobals().studentAgentCompliancePhysicalDistancingtEnd = 2000;

    Student s = testKit.addAgent(Student.class, Student::init);

    assertThat(s.complianceMask).isGreaterThan(999.0);
    assertThat(s.compQuarantineWhenSymptomatic).isGreaterThan(999.0);
    assertThat(s.compSymptomsReport).isGreaterThan(999.0);
    assertThat(s.complianceIsolating).isGreaterThan(999.0);
    assertThat(s.complianceIsolateWhenContactNotified).isGreaterThan(999.0);
    assertThat(s.compliancePhysicalDistancing).isGreaterThan(999.0);
  }

  @Test
  public void testGenerateAdditionalPlace() {
    Student s = testKit.addAgent(Student.class, Student::init);
    ImmutableList<PlaceInfo> additionalPlaces = s.generateAdditionalPlace();

    assertThat(additionalPlaces.size()).isEqualTo(0);


    s.setHomePlaceInfo(PlaceInfo.create("p1", 0));

    additionalPlaces = s.generateAdditionalPlace();

    assertThat(additionalPlaces.size()).isEqualTo(1);
    assertThat(additionalPlaces.get(0).placeName()).isEqualTo("p1");
  }

  @Test
  public void testGetCurrentPlaces() {
    Student s = testKit.addAgent(Student.class, Student::init);

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

  @Test
  public void testDecideNotToHostAdditionalEvent() {
    Student s = testKit.addAgent(Student.class, Student::init);
    s.setHomePlaceInfo(PlaceInfo.create("p1", 0));
    Student s2 = testKit.addAgent(Student.class, Student::init);

    testKit.registerLinkTypes(Links.SocialLink.class);
    s.addLink(s2.getID(), Links.SocialLink.class);

    s.probHostsAdditionalEvent = 0;
    TestResult result = testKit.testAction(s, Student.decideToHostAdditionalEvent);
    assertThat(result.getMessagesOfType(Messages.PlaceInfoMessage.class)).isEmpty();
  }

  /**
   * This is a test of Core.Person functionality but using tau.Student's
   * generateAdditionalPlace() logic
   */
  @Test
  public void testDecideToHostAdditionalEvent() {
    Student s = testKit.addAgent(Student.class, Student::init);
    s.setHomePlaceInfo(PlaceInfo.create("p1", 0));
    Student s2 = testKit.addAgent(Student.class, Student::init);

    testKit.registerLinkTypes(Links.SocialLink.class);
    s.addLink(s2.getID(), Links.SocialLink.class);

    s.probHostsAdditionalEvent = 2;
    TestResult result = testKit.testAction(s, Student.decideToHostAdditionalEvent);
    assertThat(result.getMessagesOfType(Messages.PlaceInfoMessage.class).size())
        .isEqualTo(2);
    assertThat(result.getMessagesOfType(Messages.PlaceInfoMessage.class).get(0)
        .getBody().placeName())
        .isEqualTo("p1");
  }

  @Test
  public void testGoesToSportEvent() {
    Student s = testKit.addAgent(Student.class, Student::init);
    testKit.getGlobals().tStep = 0;
    testKit.getGlobals().tOneDay = 1;

    ImmutableList<PlaceInfo> l1 = ImmutableList.of(core.TestUtils.createPlaceInfoWithAgent("A", TAUModel.PlaceType.SPORT_EVENT.ordinal(), testKit));
    ImmutableList<PlaceInfo> l2 = ImmutableList.of(core.TestUtils.createPlaceInfoWithAgent("B", TAUModel.PlaceType.SPORT_EVENT.ordinal(), testKit));
    Person.DailySchedule schedule =
            Person.DailySchedule.create(
                    ImmutableMap.of(
                            0, l1,
                            1, l2),
                    ImmutableList.of(core.TestUtils.createPlaceInfoWithAgent("ISOLATION", 3, testKit)));

    s.dailySchedule = schedule;

    testKit.testAction(s, Student.movePerson);
    s.attendsSportsEventPerc = 1.0;
    List<PlaceInfo> currentPlaces = s.getCurrentAndAdditionalPlaceInfos();
    assertThat(currentPlaces).isNotEmpty();
    assertThat(currentPlaces.get(0).placeName()).isEqualTo("A");

    testKit.getGlobals().tStep = 1;
    testKit.testAction(s, Student.movePerson);
    s.attendsSportsEventPerc = 0;
    currentPlaces = s.getCurrentAndAdditionalPlaceInfos();
    assertThat(currentPlaces).isEmpty();

    testKit.getGlobals().tStep = 0;
    testKit.getGlobals().cancelSportEvents = true;
    testKit.testAction(s, Student.movePerson);
    s.attendsSportsEventPerc = 1.0;
    currentPlaces = s.getCurrentAndAdditionalPlaceInfos();
    assertThat(currentPlaces).isEmpty();
  }

  @Test
  public void testSuppression() {
    Student s = testKit.addAgent(Student.class, Student::init);
    testKit.getGlobals().suppressAgentType = 2;
    testKit.getGlobals().nAgents = 100;
    testKit.getGlobals().nActiveAgents = 100;
    testKit.getGlobals().includeGradStudents = false;
    Person.PersonInitializationInfo init = s.initializationInfo();

    assertThat(init.suppressionSupplier().get()).isFalse();

    testKit.getGlobals().nActiveAgents = 0;
    init = s.initializationInfo();

    assertThat(init.suppressionSupplier().get()).isFalse();

    testKit.getGlobals().suppressAgentType = 0;
    testKit.getGlobals().nActiveAgents = 100;
    init = s.initializationInfo();

    assertThat(init.suppressionSupplier().get()).isFalse();


    testKit.getGlobals().nActiveAgents = 0;
    init = s.initializationInfo();

    assertThat(init.suppressionSupplier().get()).isTrue();

    testKit.getGlobals().suppressAgentType = 1;
    testKit.getGlobals().nAgents = 100;
    testKit.getGlobals().nActiveAgents = 100;
    init = s.initializationInfo();

    assertThat(init.suppressionSupplier().get()).isFalse();

    // Note, this will throw an exception if set to <0 active students (<44 active agents).
    // The number 44 is just based on the automatic scaling of the university
    testKit.getGlobals().nActiveAgents = 44;
    init = s.initializationInfo();

    assertThat(init.suppressionSupplier().get()).isTrue();

  }

  /**
   * This main method is here to let us use IntelliJ's debugger.
   * To use it, make a new run configuration with the main class pointed here
   */
  public static void main(String[] args) {
    StudentTest studentTest = new StudentTest();
    try {
      studentTest.setUp();
      //studentTest.testSuppression();
      studentTest.testGoesToSportEvent();
    } catch(Exception e) {
      e.printStackTrace();
    }
  }

}
