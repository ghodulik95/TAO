package core;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.junit.Before;
import org.junit.Test;
import simudyne.core.abm.testkit.TestKit;
import simudyne.core.abm.testkit.TestResult;
import simudyne.core.graph.Message;

import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.truth.Truth.assertThat;

public class ContactTracingTests {
  private TestKit<Globals> testKit;
  private CentralAgent centralAgent;

  @Before
  public void setUp() throws Exception {
    testKit = TestKit.create(Globals.class);
    centralAgent = testKit.addAgent(CentralAgent.class);
    testKit.getGlobals().centralAgentID = centralAgent.getID();
    testKit.createLongAccumulator("totSusceptible", 0);
    testKit.createLongAccumulator("totInfected", 0);
    testKit.createLongAccumulator("totQuarantineInfected", 0);
    testKit.createLongAccumulator("totQuarantineSusceptible", 0);
    testKit.createLongAccumulator("totDead", 0);
    testKit.createLongAccumulator("totRecovered", 0);
    testKit.createLongAccumulator("totDetectedCases", 0);
    testKit.createDoubleAccumulator("testPositivity", 0);
    testKit.createLongAccumulator("numInfectionsThisStep", 0);
    testKit.createLongAccumulator("numPosTestsThisStep", 0);
    testKit.createLongAccumulator("numTestsThisStep", 0);
  }

  private void sendAllMessagesFromTestResult(TestResult result) {
    Iterator<? extends Message> it = result.getMessageIterator();
    while (it.hasNext()) {
      Message msg = it.next();
      if (msg instanceof Messages.Copyable) {
        Messages.Copyable copy = (Messages.Copyable) msg;
        testKit.send(msg.getClass(), copy::copyInto, msg.getSender()).to(msg.getTo());
      } else {
        testKit.send(msg.getClass(), msg.getSender()).to(msg.getTo());
      }
    }
  }

  @Test
  public void testRunnerTest() {
    TestRunner runner = new TestRunner(testKit, centralAgent);
    // Set there to be no exposure period
    runner.setInfectionTrajectoryDistributionForNewPeople(InfectionTrajectoryDistribution.builder()
        .infectiousRangeStart(0)
        .infectiousRangeEnd(0)
        .build());
    testKit.getGlobals().baseInfectivity = 1.0;

    PlaceAgent place1 = runner.newPlaceAgent("Place1", 1);

    TestPerson person1 = runner.newTestPerson();
    person1.setInfected();
    person1.setCurrentPlaces(place1.place());

    TestPerson person2 = runner.newTestPerson();
    person2.setCurrentPlaces(place1.place());

    runner.moveAndInfect();
    runner.moveAndInfect();
    runner.moveAndInfect();
    runner.moveAndInfect();
    runner.moveAndInfect();
    runner.moveAndInfect();
    runner.moveAndInfect();

    assertThat(person2.status).isEqualTo(Person.InfectionStatus.INFECTED);
  }

  @Test
  public void contactTracingIntegrationTest() {
    TestRunner runner = new TestRunner(testKit, centralAgent);
    // Set there to be no exposure period
    runner.setInfectionTrajectoryDistributionForNewPeople(InfectionTrajectoryDistribution.builder()
        .infectiousRangeStart(0)
        .infectiousRangeEnd(0)
        .build());
    // We will spoof transmission so set infectivity to zero
    testKit.getGlobals().baseInfectivity = 0.0;
    testKit.getGlobals().testDelayTStep = 1;
    testKit.getGlobals().contactTracingProtocol = 1;
    testKit.getGlobals().testsPerDay = 0;

    PlaceAgent place1 = runner.newPlaceAgent("Place1", 1);
    PlaceAgent place2 = runner.newPlaceAgent("Place2", 1);
    PlaceAgent place3 = runner.newPlaceAgent("Place3", 1);
    PlaceAgent place4 = runner.newPlaceAgent("Place4", 1);

    TestPerson person1 = runner.newTestPerson();
    TestPerson person2 = runner.newTestPerson();
    TestPerson person3 = runner.newTestPerson();
    TestPerson person4 = runner.newTestPerson();

    // Person 1 visits person 2 and person 3, and infects person 3
    // Person 4 goes to place 4 alone
    person1.setInfected();
    person1.setCurrentPlaces(ImmutableList.of(place1.place(), place2.place()));
    person2.setCurrentPlaces(place1.place());
    person3.setCurrentPlaces(ImmutableList.of(place2.place(), place3.place()));
    person4.setCurrentPlaces(place4.place());
    runner.moveAndInfect();
    person3.setInfected();

    // Person 1 gets tested
    testKit.send(Messages.TestAdministeredMsg.class).to(person1);
    runner.run(person1, Person.getTested);
    runner.run(centralAgent, CentralAgent.processInfectionStatus);

    assertThat(person1.isSelfIsolatingBecauseOfContactTracing).isFalse();
    assertThat(person2.isSelfIsolatingBecauseOfContactTracing).isFalse();
    assertThat(person3.isSelfIsolatingBecauseOfContactTracing).isFalse();
    assertThat(person4.isSelfIsolatingBecauseOfContactTracing).isFalse();

    runner.incrementStep();

    // People all see same people
    // Test results are released after a day. Person 1 is found positive and does interviews.
    // This causes 2 and 3 to get tested and isolate.
    runner.step();
    assertThat(person1.isSelfIsolatingBecauseOfContactTracing).isTrue();
    assertThat(person2.isSelfIsolatingBecauseOfContactTracing).isTrue();
    assertThat(person3.isSelfIsolatingBecauseOfContactTracing).isTrue();
    assertThat(person4.isSelfIsolatingBecauseOfContactTracing).isFalse();

    person4.setCurrentPlaces(place3.place());
    runner.step();

    // Test results come back. It is positive for person 3, so they are interviewed and cause
    // person 4 to isolate.
    assertThat(person1.isSelfIsolatingBecauseOfContactTracing).isTrue();
    assertThat(person2.isSelfIsolatingBecauseOfContactTracing).isTrue();
    assertThat(person3.isSelfIsolatingBecauseOfContactTracing).isTrue();
    assertThat(person4.isSelfIsolatingBecauseOfContactTracing).isTrue();
  }

  @Test
  public void testInterview_hasContacts() {
    // Set up people going to places
    testKit.getGlobals().contactTracingNumberOfDaysTraceback = 1;
    TestPerson person1 = testKit.addAgent(TestPerson.class);
    TestPerson person2 = testKit.addAgent(TestPerson.class);

    PlaceAgent place1 = testKit.addAgent(PlaceAgent.class, PlaceAgent::init);
    PlaceInfo placeInfo1 = PlaceInfo.create("Place1", 1);
    placeInfo1.receivePlaceAgent(place1.getID());
    place1.setPlaceInfo(placeInfo1);
    PlaceAgent place2 = testKit.addAgent(PlaceAgent.class, PlaceAgent::init);
    PlaceInfo placeInfo2 = PlaceInfo.create("Place2", 1);
    place2.setPlaceInfo(placeInfo2);
    placeInfo2.receivePlaceAgent(place2.getID());

    person1.setCurrentPlaces(ImmutableList.of(placeInfo1, placeInfo2));
    sendAllMessagesFromTestResult(
        testKit.testAction(person1, Person.executeMovement));

    person2.setCurrentPlaces(placeInfo1);
    sendAllMessagesFromTestResult(
        testKit.testAction(person2, Person.executeMovement));

    testKit.testAction(place1, PlaceAgent.generateContactsAndInfect);
    testKit.testAction(place2, PlaceAgent.generateContactsAndInfect);

    // Do interview
    testKit.send(Messages.StartInterviewMsg.class).to(person1);

    sendAllMessagesFromTestResult(
        testKit.testAction(person1, Person.requestOccupancyFromPlacesVisited));
    sendAllMessagesFromTestResult(
        testKit.testAction(place1, PlaceAgent.sendOccupancy));
    sendAllMessagesFromTestResult(
        testKit.testAction(place2, PlaceAgent.sendOccupancy));
    TestResult result = testKit.testAction(person1, Person.receiveOccupancyHistoriesAndSendToCentralAgent);

    assertThat(result.getMessagesOfType(Messages.InterviewResultsMsg.class)).hasSize(1);
    Messages.InterviewResultsMsg interviewResultsMsg =
        result.getMessagesOfType(Messages.InterviewResultsMsg.class).get(0);
    assertThat(interviewResultsMsg.contacts).hasSize(1);
    Set<Long> allContacts =
        interviewResultsMsg.contacts.stream().flatMap(ImmutableSet::stream).collect(Collectors.toSet());
    assertThat(allContacts).containsExactly(person1.personID, person2.personID);
  }

  @Test
  public void testInterview_hasContactsAndHalfRecall() {
    // Set up people going to places
    testKit.getGlobals().contactTracingNumberOfDaysTraceback = 1;
    testKit.getGlobals().agentInterviewRecall = 0.5;
    TestPerson person1 = testKit.addAgent(TestPerson.class);
    TestPerson person2 = testKit.addAgent(TestPerson.class);
    TestPerson person3 = testKit.addAgent(TestPerson.class);

    PlaceAgent place1 = testKit.addAgent(PlaceAgent.class, PlaceAgent::init);
    PlaceInfo placeInfo1 = PlaceInfo.create("Place1", 1);
    placeInfo1.receivePlaceAgent(place1.getID());
    place1.setPlaceInfo(placeInfo1);
    PlaceAgent place2 = testKit.addAgent(PlaceAgent.class, PlaceAgent::init);
    PlaceInfo placeInfo2 = PlaceInfo.create("Place2", 1);
    place2.setPlaceInfo(placeInfo2);
    placeInfo2.receivePlaceAgent(place2.getID());

    person1.setCurrentPlaces(ImmutableList.of(placeInfo1, placeInfo2));
    sendAllMessagesFromTestResult(
        testKit.testAction(person1, Person.executeMovement));

    person2.setCurrentPlaces(placeInfo1);
    sendAllMessagesFromTestResult(
        testKit.testAction(person2, Person.executeMovement));

    person3.setCurrentPlaces(placeInfo1);
    sendAllMessagesFromTestResult(
        testKit.testAction(person3, Person.executeMovement));

    testKit.testAction(place1, PlaceAgent.generateContactsAndInfect);
    testKit.testAction(place2, PlaceAgent.generateContactsAndInfect);

    // Do interview
    testKit.send(Messages.StartInterviewMsg.class).to(person1);

    sendAllMessagesFromTestResult(
        testKit.testAction(person1, Person.requestOccupancyFromPlacesVisited));
    sendAllMessagesFromTestResult(
        testKit.testAction(place1, PlaceAgent.sendOccupancy));
    sendAllMessagesFromTestResult(
        testKit.testAction(place2, PlaceAgent.sendOccupancy));
    TestResult result = testKit.testAction(person1, Person.receiveOccupancyHistoriesAndSendToCentralAgent);

    assertThat(result.getMessagesOfType(Messages.InterviewResultsMsg.class)).hasSize(1);
    Messages.InterviewResultsMsg interviewResultsMsg =
        result.getMessagesOfType(Messages.InterviewResultsMsg.class).get(0);
    assertThat(interviewResultsMsg.contacts).hasSize(1);
    Set<Long> allContacts =
        interviewResultsMsg.contacts.stream().flatMap(ImmutableSet::stream).collect(Collectors.toSet());
    assertThat(allContacts).hasSize(2);
    assertThat(allContacts).contains(person1.personID);
    assertThat(allContacts).containsAnyOf(person2.personID, person3.personID);
  }

  @Test
  public void testInterview_contactsBeyondTraceback() {
    // Set up people going to places
    testKit.getGlobals().contactTracingNumberOfDaysTraceback = 1;
    TestPerson person1 = testKit.addAgent(TestPerson.class);
    TestPerson person2 = testKit.addAgent(TestPerson.class);

    PlaceAgent place1 = testKit.addAgent(PlaceAgent.class, PlaceAgent::init);
    PlaceInfo placeInfo1 = PlaceInfo.create("Place1", 1);
    placeInfo1.receivePlaceAgent(place1.getID());
    place1.setPlaceInfo(placeInfo1);
    PlaceAgent place2 = testKit.addAgent(PlaceAgent.class, PlaceAgent::init);
    PlaceInfo placeInfo2 = PlaceInfo.create("Place2", 1);
    place2.setPlaceInfo(placeInfo2);
    placeInfo2.receivePlaceAgent(place2.getID());

    // People contacted each other
    person1.setCurrentPlaces(ImmutableList.of(placeInfo1, placeInfo2));
    sendAllMessagesFromTestResult(
        testKit.testAction(person1, Person.executeMovement));

    person2.setCurrentPlaces(placeInfo1);
    sendAllMessagesFromTestResult(
        testKit.testAction(person2, Person.executeMovement));

    testKit.testAction(place1, PlaceAgent.generateContactsAndInfect);
    testKit.testAction(place2, PlaceAgent.generateContactsAndInfect);

    // People more recently did not contact each other
    person1.setCurrentPlaces(placeInfo2);
    sendAllMessagesFromTestResult(
        testKit.testAction(person1, Person.executeMovement));

    person2.setCurrentPlaces(placeInfo1);
    sendAllMessagesFromTestResult(
        testKit.testAction(person2, Person.executeMovement));

    testKit.testAction(place1, PlaceAgent.generateContactsAndInfect);
    testKit.testAction(place2, PlaceAgent.generateContactsAndInfect);

    // Do interview
    testKit.send(Messages.StartInterviewMsg.class).to(person1);

    sendAllMessagesFromTestResult(
        testKit.testAction(person1, Person.requestOccupancyFromPlacesVisited));
    sendAllMessagesFromTestResult(
        testKit.testAction(place1, PlaceAgent.sendOccupancy));
    sendAllMessagesFromTestResult(
        testKit.testAction(place2, PlaceAgent.sendOccupancy));
    TestResult result = testKit.testAction(person1, Person.receiveOccupancyHistoriesAndSendToCentralAgent);


    assertThat(result.getMessagesOfType(Messages.InterviewResultsMsg.class)).hasSize(1);
    Messages.InterviewResultsMsg interviewResultsMsg =
        result.getMessagesOfType(Messages.InterviewResultsMsg.class).get(0);
    Set<Long> allContacts =
        interviewResultsMsg.contacts.stream().flatMap(ImmutableSet::stream).collect(Collectors.toSet());
    assertThat(allContacts).containsExactly(person1.personID);
  }
}
