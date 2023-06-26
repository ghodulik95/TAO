package core;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.junit.Before;
import org.junit.Test;
import simudyne.core.abm.testkit.TestKit;
import simudyne.core.abm.testkit.TestResult;
import simudyne.core.graph.Message;
import simudyne.core.rng.SeededRandom;

import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.truth.Truth.assertThat;
import static core.Globals.ContactTracingProtocols.TEST_ONLY;
import static org.junit.Assert.assertEquals;

public class CentralAgentTest {

  private TestKit<Globals> testKit;
  private CentralAgent centralAgent;
  private PlaceAgent pa1, pa2, pa3;

  @Before
  public void setUp() throws Exception {
    testKit = TestKit.create(Globals.class);
    testKit.createLongAccumulator("numPosTestsThisStep");
    testKit.createLongAccumulator("numTestsThisStep");
    testKit.createLongAccumulator("totTestsReturnedThisStep");
    testKit.createLongAccumulator("posTestsReturnedThisStep");

    centralAgent = testKit.addAgent(CentralAgent.class);
    pa1 = testKit.addAgent(PlaceAgent.class);
    pa2 = testKit.addAgent(PlaceAgent.class);
    pa3 = testKit.addAgent(PlaceAgent.class);
    testKit.getGlobals().centralAgentID = centralAgent.getID();
    PlaceInfo p1 = PlaceInfo.create("Place1", 0);
    p1.receivePlaceAgent(pa1.getID());
    PlaceInfo p2 = PlaceInfo.create("Place2", 0);
    p2.receivePlaceAgent(pa2.getID());
    PlaceInfo p3 = PlaceInfo.create("Place3", 0);
    p3.receivePlaceAgent(pa3.getID());

    testKit.getGlobals().baseInfectivity = 1.0;
  }

  @Test
  public void testDistributeVaccinesNoVaccines() {
    testKit.getGlobals().numToVaccinate = 0;

    for(int i = 0; i < 10; i++) {
      testKit.send(Messages.ReportForVaccineMsg.class)
              .to(centralAgent);
    }

    TestResult result = testKit.testAction(centralAgent, CentralAgent.distributeVaccines);

    assertThat(result.getMessagesOfType(Messages.VaccineAdministeredMsg.class).size()).isEqualTo(0);
  }

  @Test
  public void testDistributeVaccinesNoAgents() {
    testKit.getGlobals().numToVaccinate = 10;

    TestResult result = testKit.testAction(centralAgent, CentralAgent.distributeVaccines);

    assertThat(result.getMessagesOfType(Messages.VaccineAdministeredMsg.class).size()).isEqualTo(0);
  }

  @Test
  public void testDistributeVaccines() {
    testKit.getGlobals().numToVaccinate = 5;

    for(int i = 0; i < 10; i++) {
      testKit.send(Messages.ReportForVaccineMsg.class)
              .to(centralAgent);
    }

    TestResult result = testKit.testAction(centralAgent, CentralAgent.distributeVaccines);
    List<Messages.VaccineAdministeredMsg> msgs = result.getMessagesOfType(Messages.VaccineAdministeredMsg.class);
    assertThat(result.getMessagesOfType(Messages.VaccineAdministeredMsg.class).size()).isEqualTo(5);
  }

  @Test
  public void testTestSelection() {
    final Map<Long, Double> testSelectionMultipliersCollected = new HashMap<>();
    testKit
        .getGlobals()
        .setModules(
            new DefaultModulesImpl() {

              @Override
              public Set<Long> getAgentsToTest(
                  Set<Long> symptomaticAgentsToday,
                  Map<Long, Double> testSelectionMultipliers,
                  SeededRandom random,
                  long numAgentsToTest) {
                testSelectionMultipliersCollected.clear();
                testSelectionMultipliersCollected.putAll(testSelectionMultipliers);
                return ImmutableSet.of(5678L);
              }
            });
    testKit
        .send(Messages.TestSelectionMultiplierMessage.class, msg -> msg.setBody(12.34))
        .to(centralAgent);
    testKit.testAction(centralAgent, CentralAgent.receiveTestSelectionMultipliers);

    TestResult result = testKit.testAction(centralAgent, CentralAgent.doRandomizedTesting);

    assertEquals(1, result.getMessagesOfType(Messages.TestAdministeredMsg.class).size());
    assertEquals(
        5678L,
        (long)
            result.getMessagesOfType(Messages.TestAdministeredMsg.class).stream()
                .map(Message::getTo)
                .findFirst()
                .get());
    Optional<Double> multiplier = testSelectionMultipliersCollected.values().stream().findFirst();
    assertThat(multiplier.isPresent()).isTrue();
    assertThat(multiplier.get()).isEqualTo(12.34);
  }

  @Test
  public void testExposureTimeAtInterview() {
    testKit.getGlobals().tStep = 10;
    // Agent 1 contacts agent 3 this step and agent 2 five steps ago
    testKit.send(Messages.InterviewResultsMsg.class,
        msg -> {
          msg.contacts = ImmutableList.of(
              ImmutableSet.of(1L, 2L),
              ImmutableSet.of(),
              ImmutableSet.of(),
              ImmutableSet.of(),
              ImmutableSet.of(1L, 3L)
          );
        },
        1L)
        .to(centralAgent);

    TestResult result = testKit.testAction(centralAgent, CentralAgent.processInterviewContacts);

    List<Messages.QuarantineOrderMsg> quarantineOrderMsgs
        = result.getMessagesOfType(Messages.QuarantineOrderMsg.class);
    Optional<Messages.QuarantineOrderMsg> agent2Msg = quarantineOrderMsgs.stream().filter(msg -> msg.getTo() == 2L).findFirst();
    assertThat(agent2Msg.isPresent()).isTrue();
    assertThat(agent2Msg.get().exposureTime).isEqualTo(10 - 5 + 1);

    Optional<Messages.QuarantineOrderMsg> agent3Msg =
        quarantineOrderMsgs.stream().filter(msg -> msg.getTo() == 3L).findFirst();
    assertThat(agent3Msg.isPresent()).isTrue();
    assertThat(agent3Msg.get().exposureTime).isEqualTo(10);
  }

  @Test
  public void testExposureTimeAtInterview_onlyMostRecentExposureSent() {
    testKit.getGlobals().tStep = 10;
    // Agent 1 contacts agent 3 every step for past 5 steps
    testKit.send(Messages.InterviewResultsMsg.class,
        msg -> {
          msg.contacts = ImmutableList.of(
              ImmutableSet.of(1L, 3L),
              ImmutableSet.of(1L, 3L),
              ImmutableSet.of(1L, 3L),
              ImmutableSet.of(1L, 3L),
              ImmutableSet.of(1L, 3L)
          );
        },
        1L)
        .to(centralAgent);

    TestResult result = testKit.testAction(centralAgent, CentralAgent.processInterviewContacts);

    List<Messages.QuarantineOrderMsg> quarantineOrderMsgs
        = result.getMessagesOfType(Messages.QuarantineOrderMsg.class);
    assertThat(quarantineOrderMsgs).hasSize(1);
    assertThat(quarantineOrderMsgs.get(0).exposureTime).isEqualTo(10);
    assertThat(quarantineOrderMsgs.get(0).getTo()).isEqualTo(3L);
  }

  @Test
  public void testProcessInterviews_onlyOneTestSent() {
    testKit.getGlobals().tStep = 10;
    // Agent 1 contacts agent 3 every step for past 5 steps
    testKit.send(Messages.InterviewResultsMsg.class,
        msg -> {
          msg.contacts = ImmutableList.of(
              ImmutableSet.of(1L, 3L),
              ImmutableSet.of(1L, 3L),
              ImmutableSet.of(1L, 3L),
              ImmutableSet.of(1L, 3L),
              ImmutableSet.of(1L, 3L)
          );
        },
        1L)
        .to(centralAgent);

    TestResult result = testKit.testAction(centralAgent, CentralAgent.processInterviewContacts);

    List<Messages.TestAdministeredMsg> testMsgs = result.getMessagesOfType(Messages.TestAdministeredMsg.class);
    assertThat(testMsgs).hasSize(1);
    assertThat(testMsgs.get(0).getTo()).isEqualTo(3L);
  }

  @Test
  public void testReceiveSymptomaticMessage_conservative() {
    testKit.getGlobals().contactTracingProtocol = Globals.ContactTracingProtocols.CONSERVATIVE.ordinal();
    centralAgent.addToCasesToMonitor(1234L);
    testKit.send(Messages.SymptomaticMsg.class, 1234L).to(centralAgent);

    TestResult result = testKit.testAction(centralAgent, CentralAgent.receiveSymptomaticMsg);

    List<Messages.QuarantineOrderMsg> quarantineOrderMsgs = result.getMessagesOfType(Messages.QuarantineOrderMsg.class);
    assertThat(quarantineOrderMsgs).hasSize(1);
    assertThat(quarantineOrderMsgs.get(0).getTo()).isEqualTo(1234L);
    List<Messages.TestAdministeredMsg> testAdministeredMsgs =
        result.getMessagesOfType(Messages.TestAdministeredMsg.class);
    assertThat(testAdministeredMsgs).hasSize(1);
    assertThat(testAdministeredMsgs.get(0).getTo()).isEqualTo(1234L);
  }

  @Test
  public void testReceiveSymptomaticMessage_caseRemovedFromMonitorList() {
    testKit.getGlobals().testDelayTStep = 0;
    testKit.getGlobals().contactNotifiedNumberOfDaysToIsolate = 1;
    // Agent 1234L is exposed at step 5 and is told to isolate for 1 day
    testKit.getGlobals().tStep = 5;
    testKit.send(Messages.InterviewResultsMsg.class,
        msg -> {
          msg.contacts = ImmutableList.of(
              ImmutableSet.of(1234L)
          );
        },
        1L)
        .to(centralAgent);

    testKit.testAction(centralAgent, CentralAgent.processInterviewContacts);

    // Agent 4567L is exposed at step 6 and is told to isolate for one day.
    testKit.getGlobals().tStep = 6;
    testKit.send(Messages.InterviewResultsMsg.class,
        msg -> {
          msg.contacts = ImmutableList.of(
              ImmutableSet.of(4567L)
          );
        },
        1L)
        .to(centralAgent);

    testKit.testAction(centralAgent, CentralAgent.processInterviewContacts);

    // At step 6, neither report symptoms
    testKit.testAction(centralAgent, CentralAgent.receiveSymptomaticMsg);
    // Agent 1234L should have been removed from casesToMonitor

    // At step 7, both report symptoms
    testKit.getGlobals().tStep = 6;
    testKit.send(Messages.SymptomaticMsg.class, 1234L).to(centralAgent);
    testKit.send(Messages.SymptomaticMsg.class, 4567L).to(centralAgent);

    // Central agent should only test agent 4567L because it is no longer monitoring agent 1234L
    TestResult result = testKit.testAction(centralAgent, CentralAgent.receiveSymptomaticMsg);
    List<Messages.QuarantineOrderMsg> quarantineOrderMsgs = result.getMessagesOfType(Messages.QuarantineOrderMsg.class);
    assertThat(quarantineOrderMsgs).hasSize(1);
    assertThat(quarantineOrderMsgs.get(0).getTo()).isEqualTo(4567L);
  }

  @Test
  public void testNegativeTestReleasesFromQuarantine() {
    testKit.getGlobals().testDelayTStep = 0;
    testKit.getGlobals().contactNotifiedNumberOfDaysToIsolate = 1;
    testKit.getGlobals().contactTracingProtocol = TEST_ONLY.ordinal();
    testKit.getGlobals().tStep = 5;
    testKit.send(Messages.InfectionStatusMsg.class, msg -> {
          msg.infectedStatus = Person.InfectionStatus.SUSCEPTIBLE;
          msg.testAccuracy = 1.0;
        },
        4567L)
        .to(centralAgent);
    testKit.testAction(centralAgent, CentralAgent.processInfectionStatus);

    TestResult result = testKit.testAction(centralAgent, CentralAgent.releaseTestResults);
    List<Messages.QuarantineReleaseMsg> quarantineReleaseMsgs =
        result.getMessagesOfType(Messages.QuarantineReleaseMsg.class);
    assertThat(quarantineReleaseMsgs).hasSize(1);
    assertThat(quarantineReleaseMsgs.get(0).getTo()).isEqualTo(4567L);
  }

  @Test
  public void testReassignSuppression_decreaseNumSuppressed() {
    // 3 suppressed 2 active --> 2 suppressed 3 active
    testKit.getGlobals().nActiveAgents = 3;
    Map<Long, Boolean> idToSuppressionStatus =
        ImmutableMap.of(
            1L, true,
            2L, true,
            3L, true,
            4L, false,
            5L, false
        );
    idToSuppressionStatus.forEach((key, value) -> testKit.send(
        Messages.SupressionStatusMessage.class, m -> m.isSuppressed = value, key)
        .to(centralAgent));

    TestResult result = testKit.testAction(centralAgent, CentralAgent.reassignSuppression);

    List<Messages.SupressionStatusMessage> messages = result.getMessagesOfType(Messages.SupressionStatusMessage.class);
    assertThat(messages).hasSize(1);
    assertThat(messages.get(0).getTo()).isAnyOf(1L, 2L, 3L);
    assertThat(messages.get(0).isSuppressed).isFalse();
  }

  @Test
  public void testReassignSuppression_increaseNumSuppressed() {
    // 2 suppressed 3 active --> 3 suppressed 2 active
    testKit.getGlobals().nActiveAgents = 2;
    Map<Long, Boolean> idToSuppressionStatus =
        ImmutableMap.of(
            1L, true,
            2L, true,
            3L, false,
            4L, false,
            5L, false
        );
    idToSuppressionStatus.forEach((key, value) -> testKit.send(
        Messages.SupressionStatusMessage.class, m -> m.isSuppressed = value, key)
        .to(centralAgent));

    TestResult result = testKit.testAction(centralAgent, CentralAgent.reassignSuppression);

    List<Messages.SupressionStatusMessage> messages = result.getMessagesOfType(Messages.SupressionStatusMessage.class);
    assertThat(messages).hasSize(1);
    assertThat(messages.get(0).getTo()).isAnyOf(3L, 4L, 5L);
    assertThat(messages.get(0).isSuppressed).isTrue();
  }

  @Test
  public void testReassignSuppression_zeroToAll() {
    // 2 suppressed 3 active --> 3 suppressed 2 active
    testKit.getGlobals().nActiveAgents = 0;
    Map<Long, Boolean> idToSuppressionStatus =
        ImmutableMap.of(
            1L, false,
            2L, false,
            3L, false,
            4L, false,
            5L, false
        );
    idToSuppressionStatus.forEach((key, value) -> testKit.send(
        Messages.SupressionStatusMessage.class, m -> m.isSuppressed = value, key)
        .to(centralAgent));

    TestResult result = testKit.testAction(centralAgent, CentralAgent.reassignSuppression);

    List<Messages.SupressionStatusMessage> messages = result.getMessagesOfType(Messages.SupressionStatusMessage.class);
    assertThat(messages).hasSize(5);
    assertThat(messages.stream().map(Message::getTo).collect(Collectors.toList())).containsAllOf(1L, 2L, 3L, 4L, 5L);
    assertThat(messages.stream().map(m -> m.isSuppressed).collect(Collectors.toList())).containsExactly(true, true,
        true, true, true);
  }

  @Test
  public void testReassignSuppression_noChange() {
    // 2 suppressed 3 active --> 2 suppressed 3 active
    testKit.getGlobals().nActiveAgents = 3;
    Map<Long, Boolean> idToSuppressionStatus =
        ImmutableMap.of(
            1L, true,
            2L, true,
            3L, false,
            4L, false,
            5L, false
        );
    idToSuppressionStatus.forEach((key, value) -> testKit.send(
        Messages.SupressionStatusMessage.class, m -> m.isSuppressed = value, key)
        .to(centralAgent));

    TestResult result = testKit.testAction(centralAgent, CentralAgent.reassignSuppression);

    List<Messages.SupressionStatusMessage> messages = result.getMessagesOfType(Messages.SupressionStatusMessage.class);
    assertThat(messages).isEmpty();
  }

  // TODO May be removing the per building ratios. If so, remove this test.
//  @Test
//  public void testPerPlaceOutput() {
//    testKit.getGlobals().tOneDay = 2;
//    testKit.getGlobals().tStep = 0;
//    testKit.getGlobals().initBuildingInfectionArrays(3);
//    testKit.getGlobals().places.put("p0", Place.create("p0", 0));
//    testKit.getGlobals().places.put("p1", Place.create("p1", 1));
//    testKit.getGlobals().places.put("p2", Place.create("p2", 2));
//
//    testKit
//        .send(
//            Messages.IAmHereMsg.class,
//            msg -> {
//              msg.placeId = "p0";
//              msg.agentId = 1;
//              msg.transmissibilityInfo = Person.PersonTransmissibilityInfo.dummyInfected();
//            })
//        .to(centralAgent);
//    testKit
//        .send(
//            Messages.IAmHereMsg.class,
//            msg -> {
//              msg.placeId = "p1";
//              msg.agentId = 2;
//              msg.transmissibilityInfo = Person.PersonTransmissibilityInfo.dummyInfected();
//            })
//        .to(centralAgent);
//    testKit
//        .send(
//            Messages.IAmHereMsg.class,
//            msg -> {
//              msg.placeId = "p1";
//              msg.agentId = 3;
//              msg.transmissibilityInfo = Person.PersonTransmissibilityInfo.dummy();
//            })
//        .to(centralAgent);
//
//    testKit.testAction(centralAgent, CentralAgent.generateAgentLocationsAndInfect);
//
//    assertThat(testKit.getGlobals().buildingInfectionsBeginningOfStep.get(0)).isEqualTo(1);
//    assertThat(testKit.getGlobals().buildingInfectionsBeginningOfStep.get(1)).isEqualTo(1);
//
//    testKit
//        .send(
//            Messages.IAmHereMsg.class,
//            msg -> {
//              msg.placeId = "p0";
//              msg.agentId = 1;
//              msg.transmissibilityInfo = Person.PersonTransmissibilityInfo.dummyInfected();
//            })
//        .to(centralAgent);
//    testKit
//        .send(
//            Messages.IAmHereMsg.class,
//            msg -> {
//              msg.placeId = "p1";
//              msg.agentId = 2;
//              msg.transmissibilityInfo = Person.PersonTransmissibilityInfo.dummyInfected();
//            })
//        .to(centralAgent);
//    testKit
//        .send(
//            Messages.IAmHereMsg.class,
//            msg -> {
//              msg.placeId = "p1";
//              msg.agentId = 3;
//              msg.transmissibilityInfo = Person.PersonTransmissibilityInfo.dummyInfected();
//            })
//        .to(centralAgent);
//
//    testKit.testAction(centralAgent, CentralAgent.processPlaceInfectionRates);
//
//    assertThat(testKit.getGlobals().buildingInfectionsEndOfStep.get(0)).isEqualTo(1);
//    assertThat(testKit.getGlobals().buildingInfectionsEndOfStep.get(1)).isEqualTo(2);
//
//    centralAgent.updatePerBuildingInfectionRatios();
//
//    assertThat(testKit.getGlobals().buildingInfectionRatioStepSum.get(0)).isEqualTo(0.0);
//    assertThat(testKit.getGlobals().buildingInfectionRatioStepSum.get(1)).isEqualTo(1.0);
//
//    testKit.getGlobals().tStep = 1;
//
//    testKit
//        .send(
//            Messages.IAmHereMsg.class,
//            msg -> {
//              msg.placeId = "p0";
//              msg.agentId = 1;
//              msg.transmissibilityInfo = Person.PersonTransmissibilityInfo.dummyInfected();
//            })
//        .to(centralAgent);
//    testKit
//        .send(
//            Messages.IAmHereMsg.class,
//            msg -> {
//              msg.placeId = "p1";
//              msg.agentId = 2;
//              msg.transmissibilityInfo = Person.PersonTransmissibilityInfo.dummyInfected();
//            })
//        .to(centralAgent);
//    testKit
//        .send(
//            Messages.IAmHereMsg.class,
//            msg -> {
//              msg.placeId = "p1";
//              msg.agentId = 3;
//              msg.transmissibilityInfo = Person.PersonTransmissibilityInfo.dummyInfected();
//            })
//        .to(centralAgent);
//
//    testKit.testAction(centralAgent, CentralAgent.generateAgentLocationsAndInfect);
//
//    assertThat(testKit.getGlobals().buildingInfectionsBeginningOfStep.get(0)).isEqualTo(1);
//    assertThat(testKit.getGlobals().buildingInfectionsBeginningOfStep.get(1)).isEqualTo(2);
//    assertThat(testKit.getGlobals().buildingInfectionsBeginningOfDay.get(0)).isEqualTo(2);
//    assertThat(testKit.getGlobals().buildingInfectionsBeginningOfDay.get(1)).isEqualTo(3);
//
//    testKit
//        .send(
//            Messages.IAmHereMsg.class,
//            msg -> {
//              msg.placeId = "p0";
//              msg.agentId = 1;
//              msg.transmissibilityInfo = Person.PersonTransmissibilityInfo.dummyInfected();
//            })
//        .to(centralAgent);
//    testKit
//        .send(
//            Messages.IAmHereMsg.class,
//            msg -> {
//              msg.placeId = "p1";
//              msg.agentId = 2;
//              msg.transmissibilityInfo = Person.PersonTransmissibilityInfo.dummyInfected();
//            })
//        .to(centralAgent);
//    testKit
//        .send(
//            Messages.IAmHereMsg.class,
//            msg -> {
//              msg.placeId = "p1";
//              msg.agentId = 3;
//              msg.transmissibilityInfo = Person.PersonTransmissibilityInfo.dummyInfected();
//            })
//        .to(centralAgent);
//    testKit
//        .send(
//            Messages.IAmHereMsg.class,
//            msg -> {
//              msg.placeId = "p1";
//              msg.agentId = 4;
//              msg.transmissibilityInfo = Person.PersonTransmissibilityInfo.dummyInfected();
//            })
//        .to(centralAgent);
//    testKit
//        .send(
//            Messages.IAmHereMsg.class,
//            msg -> {
//              msg.placeId = "p1";
//              msg.agentId = 5;
//              msg.transmissibilityInfo = Person.PersonTransmissibilityInfo.dummyInfected();
//            })
//        .to(centralAgent);
//    testKit
//        .send(
//            Messages.IAmHereMsg.class,
//            msg -> {
//              msg.placeId = "p1";
//              msg.agentId = 6;
//              msg.transmissibilityInfo = Person.PersonTransmissibilityInfo.dummyInfected();
//            })
//        .to(centralAgent);
//
//    testKit.testAction(centralAgent, CentralAgent.processPlaceInfectionRates);
//
//    assertThat(testKit.getGlobals().buildingInfectionsEndOfStep.get(0)).isEqualTo(1);
//    assertThat(testKit.getGlobals().buildingInfectionsEndOfStep.get(1)).isEqualTo(5);
//    assertThat(testKit.getGlobals().buildingInfectionsEndOfDay.get(0)).isEqualTo(2);
//    assertThat(testKit.getGlobals().buildingInfectionsEndOfDay.get(1)).isEqualTo(7);
//
//    centralAgent.updatePerBuildingInfectionRatios();
//
//    assertThat(testKit.getGlobals().buildingInfectionRatioStepSum.get(0)).isEqualTo(0.0);
//    assertThat(testKit.getGlobals().buildingInfectionRatioStepSum.get(1)).isEqualTo(2.5);
//    assertThat(testKit.getGlobals().buildingInfectionRatioDaySum.get(0)).isEqualTo(0.0);
//    assertThat(testKit.getGlobals().buildingInfectionRatioDaySum.get(1)).isWithin(0.004).of(1.33);
//  }

  /**
   * This main method is here to let us use IntelliJ's debugger.
   * To use it, make a new run configuration with the main class pointed here
   */
  public static void main(String[] args) {
    CentralAgentTest centralAgentTest = new CentralAgentTest();
    try {
      centralAgentTest.setUp();
      centralAgentTest.testDistributeVaccines();
//      centralAgentTest.testTestSelection();
      // centralAgentTest.testNegativeTestReleasesFromQuarantine();
    } catch(Exception e) {
      e.printStackTrace();
    }
  }
}
