package core;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import simudyne.core.abm.Action;
import simudyne.core.abm.Agent;
import simudyne.core.graph.Message;
import simudyne.core.values.ValueRecord;

import java.util.*;
import java.util.stream.Collectors;

import static core.Utils.sortedCopyBySender;

/**
 * Coordinates processes which requires information from many or all agents, like infection spread
 * and testing.
 */
public class CentralAgent extends Agent<Globals> {

  public List<Test> testedAgents = new ArrayList<>();
  private final Map<Long, Double> agentIdToTestSelectionMultipliers = new LinkedHashMap<>();

  // Cases to monitor for when symptoms are reported. Elements
  private final Set<Long> casesToMonitor = new HashSet<>();
  private final SortedSet<Long> toInterviewThisStep = new TreeSet<>();
  private final PriorityQueue<QuarantineInfo> quarantineUntilQueue = new PriorityQueue<>();

  /**
   * Receives a {@link core.Messages.SymptomaticMsg} from {@link Person#reportSymptoms}, from which action may be taken
   * depending on contact tracing strategy.
   */
  public static Action<CentralAgent> receiveSymptomaticMsg =
      Action.create(
          CentralAgent.class,
          ca -> {
            ca.getMessagesOfType(Messages.SymptomaticMsg.class)
                .forEach(
                    msg -> {
                      if (!ca.casesToMonitor.contains(msg.getSender())) {
                        return;
                      }

                      ca.send(Messages.QuarantineOrderMsg.class).to(msg.getSender());
                      int protocol = ca.getGlobals().contactTracingProtocol;
                      if (protocol == Globals.ContactTracingProtocols.BEST_PRACTICE.ordinal()) {
                        ca.toInterviewThisStep.add(msg.getSender());
                        ca.casesToMonitor.remove(msg.getSender());
                      } else if (protocol == Globals.ContactTracingProtocols.CONSERVATIVE.ordinal()) {
                        ca.send(Messages.TestAdministeredMsg.class).to(msg.getSender());
                      } else if (protocol == Globals.ContactTracingProtocols.TEST_ONLY.ordinal()) {
                        // Do nothing
                      } else {
                        throw new IllegalStateException("Invalid contact tracing protocol.");
                      }
                    });
            while (!ca.quarantineUntilQueue.isEmpty()
                && ca.quarantineUntilQueue.peek().quarantineUntil() == ca.getGlobals().tStep) {
              ca.casesToMonitor.remove(ca.quarantineUntilQueue.poll().agentId());
            }
          });

  @VisibleForTesting
  void addToCasesToMonitor(long caseToMonitor) {
    casesToMonitor.add(caseToMonitor);
  }

  /**
   * Based on test selection multipliers, picks people for randomized testing, sending them a
   * {@link core.Messages.TestAdministeredMsg}. This will be processed by {@link Person#getTested}.
   */
  public static Action<CentralAgent> doRandomizedTesting =
      Action.create(
          CentralAgent.class,
          ca -> {
            long testsAvailable = ca.getGlobals().testsPerDay - ca.getLongAccumulator("numTestsThisStep").value();
            if(testsAvailable > 0) {
              Set<Long> agentsToTest =
                      ca.getGlobals()
                              .getAgentsToTest(
                                      ImmutableSet.of(),
                                      ImmutableMap.copyOf(ca.agentIdToTestSelectionMultipliers),
                                      ca.getPrng(),
                                      testsAvailable);
              agentsToTest.forEach(
                      agentId -> {
                        ca.getGlobals().totalTestsAdministered++;
                        ca.send(Messages.TestAdministeredMsg.class).to(agentId);
                      });
            }
          });

  /**
   * Store test results to send back to agents after delay. Receives {@link core.Messages.InfectionStatusMsg from
   * {@link Person#getTested}.
   */
  public static Action<CentralAgent> processInfectionStatus =
      Action.create(
          CentralAgent.class,
          ca -> {
            long totalPositiveTests = 0;
            List<Messages.InfectionStatusMsg> msgs = sortedCopyBySender(ca.getMessagesOfType(Messages.InfectionStatusMsg.class));
            long totalTests = msgs.size();
            for(Messages.InfectionStatusMsg msg : msgs) {
              Test test = ca.generateTest(msg);
              ca.testedAgents.add(test);
              if(test.positive()) {
                totalPositiveTests++;
              }
            }
            ca.getLongAccumulator("numPosTestsThisStep").add(totalPositiveTests);
            ca.getLongAccumulator("numTestsThisStep").add(totalTests);
          });

  /**
   * Send quarantine start/end orders depending on test results and contact tracing strategy.
   * Sends messages {@link core.Messages.QuarantineOrderMsg} to {@link Person#receiveQuarantineStartOrStopAndAdministerTest}
   */
  public static Action<CentralAgent> releaseTestResults =
      Action.create(
          CentralAgent.class,
          ca -> {
            ca.testedAgents.stream()
                .filter(x -> x.tStepReturn() == ca.getGlobals().tStep)
                .forEach(
                    result -> {
                      ca.getLongAccumulator("totTestsReturnedThisStep").add(1);
                      if (result.positive()) {
                        ca.getLongAccumulator("posTestsReturnedThisStep").add(1);
                        ca.toInterviewThisStep.add(result.testedPersonID());
                        ca.send(Messages.QuarantineOrderMsg.class).to(result.testedPersonID());
                        ca.casesToMonitor.remove(result.testedPersonID());
                        ca.quarantineUntilQueue.add(QuarantineInfo.create(result.testedPersonID(),
                            ca.getGlobals().tStep + ca.getGlobals().contactNotifiedNumberOfDaysToIsolate * ca.getGlobals().tOneDay));
                      } else if (ca.getGlobals().contactTracingProtocol
                            == Globals.ContactTracingProtocols.TEST_ONLY.ordinal()) {
                        ca.casesToMonitor.remove(result.testedPersonID());
                        ca.send(Messages.QuarantineReleaseMsg.class).to(result.testedPersonID());
                      }
                    });
            ca.testedAgents.removeIf(x -> x.tStepReturn() == ca.getGlobals().tStep);
          });

  /**
   * People to interview have accumulated in the step. At this action, we send
   * {@link core.Messages.StartInterviewMsg} to {@link Person#requestOccupancyFromPlacesVisited}.
   */
  public static Action<CentralAgent> startInterviews =
      Action.create(CentralAgent.class, ca -> {
        ca.toInterviewThisStep.forEach(id -> {
          ca.send(Messages.StartInterviewMsg.class).to(id);
        });
        ca.toInterviewThisStep.clear();
      });

  /**
   * Recieves {@link core.Messages.QuarantineOrderMsg} from {@link Person#requestOccupancyFromPlacesVisited},
   * which contain the contacts that interviewed people remember contacting. We process contacts
   * here according to our contact tracing methods, tracking the cases internally, testing, and/or
   * ordering to quarantine for 14 days since exposure time.
   *
   * <p>This method may send {@link core.Messages.QuarantineOrderMsg}, {@link core.Messages.TestAdministeredMsg}
   * to {@link Person#receiveQuarantineStartOrStopAndAdministerTest}.
   */
  public static Action<CentralAgent> processInterviewContacts =
      Action.create(CentralAgent.class, ca -> {
        sortedCopyBySender(ca.getMessagesOfType(Messages.InterviewResultsMsg.class)).forEach(
            msg -> {
              Set<Long> alreadyQuarantineOrdered = new HashSet<>();
              Set<Long> alreadyTested = new HashSet<>();
              long testsAvailable = ca.getGlobals().testsPerDay - ca.getLongAccumulator("numTestsThisStep").value();

              for (int i = msg.contacts.size() - 1; i >= 0; i--) {
                long exposureTime = ca.getGlobals().tStep - (msg.contacts.size() - (i + 1));
                for (long contact : msg.contacts.get(i)) {
                  if (contact == msg.getSender() || ca.casesToMonitor.contains(contact)) {
                    continue;
                  }
                  ca.casesToMonitor.add(contact);
                  if (!alreadyQuarantineOrdered.contains(contact)) {
                    ca.send(Messages.QuarantineOrderMsg.class,
                        quarantineOrderMsg -> quarantineOrderMsg.exposureTime = exposureTime)
                        .to(contact);
                    alreadyQuarantineOrdered.add(contact);
                    ca.quarantineUntilQueue.add(QuarantineInfo.create(contact,
                        exposureTime + ca.getGlobals().contactNotifiedNumberOfDaysToIsolate * ca.getGlobals().tOneDay));
                  }
                  if ((ca.getGlobals().contactTracingProtocol == Globals.ContactTracingProtocols.BEST_PRACTICE.ordinal()
                      || ca.getGlobals().contactTracingProtocol == Globals.ContactTracingProtocols.TEST_ONLY.ordinal())
                      && ca.getGlobals().testingAvailableForTracing) {
                    if (!alreadyTested.contains(contact) && testsAvailable > 0) {
                      ca.send(Messages.TestAdministeredMsg.class).to(contact);
                      alreadyTested.add(contact);
                      testsAvailable--;
                    }
                  }
                }
              }
            }
        );
      });

  public static Action<CentralAgent> agentsDied =
      Action.create(
          CentralAgent.class,
          ca -> {
            sortedCopyBySender(ca.getMessagesOfType(Messages.RIPmsg.class))
                .forEach(
                    msg -> {
                      ca.agentIdToTestSelectionMultipliers.remove(msg.getSender());
                    });
          });

  public static Action<CentralAgent> processPlaceInfectionRates =
      Action.create(
          CentralAgent.class,
          ca -> {
            for (int i = 0; i < ca.getGlobals().buildingInfectionsBeginningOfStep.size(); i++) {
              ca.getGlobals().buildingInfectionsBeginningOfStep.set(i, 0);
              ca.getGlobals().buildingInfectionsOverStep.set(i, 0);
              ca.getGlobals().peopleWentToPlaceTypeStep.set(i, false);

              if (ca.getGlobals().tStep % ca.getGlobals().tOneDay == 0) {
                ca.getGlobals().buildingInfectionsBeginningOfDay.set(i, 0);
                ca.getGlobals().buildingInfectionsOverDay.set(i, 0);
                ca.getGlobals().peopleWentToPlaceTypeDay.set(i, false);
              }
            }

            ca.getMessagesOfType(Messages.PlaceInfections.class)
                .forEach(msg -> {
                  ca.getGlobals().buildingTotalInfections
                      .set(msg.placeType, ca.getGlobals().buildingTotalInfections
                          .get(msg.placeType) + msg.numGotInfected);
                  ca.getGlobals().buildingTotalPeople
                      .set(msg.placeType, ca.getGlobals().buildingTotalPeople
                          .get(msg.placeType) + msg.totalInPlace);

                  ca.getGlobals().buildingInfectionsBeginningOfStep.set(msg.placeType,
                      ca.getGlobals().buildingInfectionsBeginningOfStep.get(msg.placeType)
                          + msg.numStartedInfected);
                  ca.getGlobals().buildingInfectionsBeginningOfDay.set(msg.placeType,
                      ca.getGlobals().buildingInfectionsBeginningOfDay.get(msg.placeType)
                          + msg.numStartedInfected);
                  ca.getGlobals().buildingInfectionsOverStep.set(msg.placeType,
                      ca.getGlobals().buildingInfectionsOverStep.get(msg.placeType)
                          + msg.numGotInfected);
                  ca.getGlobals().buildingInfectionsOverDay.set(msg.placeType,
                      ca.getGlobals().buildingInfectionsOverDay.get(msg.placeType)
                          + msg.numGotInfected);

                  ca.getGlobals().peopleWentToPlaceTypeStep.set(msg.placeType, true);
                  ca.getGlobals().peopleWentToPlaceTypeDay.set(msg.placeType, true);

                });
          });

  // TODO A lot of this test logic needs to be reworked
  public Test generateTest(Messages.InfectionStatusMsg infectionMsg) {
    boolean personInfected = infectionMsg.infectedStatus == Person.InfectionStatus.INFECTED;
    int tReturnResults = getGlobals().tStep + getGlobals().testDelayTStep;

    double testResult = getPrng().uniform(0.0, 1.0).sample();
    if(testResult > infectionMsg.testAccuracy) {
      personInfected = !personInfected;
    }

    return Test.create(infectionMsg.getSender(), tReturnResults, personInfected);

  }

  // Update statistics
  public static Action<CentralAgent> updateInfectionStatistics =
      Action.create(
          CentralAgent.class,
          ca -> {
            ca.calcTestPositivity();
            ca.getDoubleAccumulator("testPositivity").add(ca.getGlobals().testPositivity);
            ca.updatePerBuildingInfectionRatios();

            InfectionStatistics infectionStatistics =
                new InfectionStatistics(
                    ca.getGlobals().tStep,
                    ca.getGlobals().numSusceptible,
                    ca.getGlobals().numInfected,
                    ca.getGlobals().numQuarantineInfected,
                    ca.getGlobals().numQuarantineSusceptible,
                    ca.getGlobals().numRecovered,
                    ca.getGlobals().numDead,
                    ca.getGlobals().numDetectedCases,
                    ca.getGlobals().testPositivity);
            ValueRecord infectionOutput = infectionStatistics.getValue();
            ca.getContext()
                .getChannels()
                .getOutputChannelWriterById("infection-output")
                .write(infectionOutput);
          });

  public void calcTestPositivity() {
    if (getGlobals().totalTestsAdministered == 0) {
      getGlobals().testPositivity = 0;
    } else {
      getGlobals().testPositivity =
          getGlobals().totalPositiveTests / getGlobals().totalTestsAdministered * 100;
    }
  }

  public void updatePerBuildingInfectionRatios() {
    for (int type = 0; type < getGlobals().buildingInfectionRatioStepSum.size(); type++) {
      int iBeginningOfStep = getGlobals().buildingInfectionsBeginningOfStep.get(type);
      int numInfections = getGlobals().buildingInfectionsOverStep.get(type);

      if (iBeginningOfStep == 0 || !getGlobals().peopleWentToPlaceTypeStep.get(type)) {
        getGlobals().buildingExcludeStepsCount.set(type,
            getGlobals().buildingExcludeStepsCount.get(type) + 1);
      }
      if (iBeginningOfStep != 0) {
        getGlobals()
            .buildingInfectionRatioStepSum
            .set(type,
                getGlobals().buildingInfectionRatioStepSum.get(type) +
                    (numInfections / (double) iBeginningOfStep));
      }
      // update day ratio at end of day
      if ((getGlobals().tStep + 1) % getGlobals().tOneDay == 0) {
        int iBeginningOfDay = getGlobals().buildingInfectionsBeginningOfDay.get(type);
        int numInfectionsDay = getGlobals().buildingInfectionsOverDay.get(type);

        if (iBeginningOfDay == 0 || !getGlobals().peopleWentToPlaceTypeDay.get(type)) {
          getGlobals().buildingExcludeDaysCount.set(type,
              getGlobals().buildingExcludeDaysCount.get(type) + 1);
        }

        if (iBeginningOfDay != 0) {
          getGlobals()
              .buildingInfectionRatioDaySum
              .set(type,
                  getGlobals().buildingInfectionRatioDaySum.get(type) +
                      (numInfectionsDay / (double) iBeginningOfDay));
        }
      }
    }
  }

  public static Action<CentralAgent> initializeConnectionOfAgents =
      Action.create(
          CentralAgent.class,
          ca -> {
            if (ca.getGlobals().tStep != 0) {
              throw new IllegalStateException(
                  "Place and schedule initialization can only be done at step 0.");
            }
            List<Person> allPeople =
                sortedCopyBySender(ca.getMessagesOfType(Messages.PersonMessage.class)).stream()
                    .map(msg -> msg.person)
                    .collect(Collectors.toList());
            ca.getGlobals().createConnectionOfAgents(allPeople);
          }
      );

  /**
   * Secondary initialization, which has access to all Persons after being primarily initialized.
   */
  public static Action<CentralAgent> initializePlacesAndAssignSchedules =
      Action.create(
          CentralAgent.class,
          ca -> {
            if (ca.getGlobals().tStep != 0) {
              throw new IllegalStateException(
                  "Place and schedule initialization can only be done at step 0.");
            }

            ca.getGlobals()
                .createPlacesAndPersonDailySchedules()
                .forEach(
                    (personId, schedule) -> {
                      ca.send(
                          Messages.ScheduleMessage.class,
                          scheduleMessage -> scheduleMessage.schedule = schedule)
                          .to(personId);
                    });

            List<Messages.PlaceAgentMessage> msgs = ca.getMessagesOfType(Messages.PlaceAgentMessage.class);
            if (msgs.size() != 1) {
              throw new IllegalStateException("There should only be one PlaceAgent at this point.");
            }
            ca.getGlobals().uninitializedPlaceInfos.forEach(place -> {
              ca.send(Messages.PlaceMessage.class, placeMsg -> placeMsg.placeInfo = place)
                  .to(msgs.get(0).getBody());
            });
            ca.getGlobals().uninitializedPlaceInfos = null;
          });

  public static Action<CentralAgent> receiveTestSelectionMultipliers =
      Action.create(
          CentralAgent.class,
          ca -> {
            sortedCopyBySender(
                ca.getMessagesOfType(Messages.TestSelectionMultiplierMessage.class))
                .forEach(
                    msg ->
                        ca.agentIdToTestSelectionMultipliers.put(msg.getSender(), msg.getBody()));
          });

  /**
   * Receives {@link Messages.NumPeopleInfectedMsg} from {@link Person#sendNumPeopleInfected}
   * Compiles messages into a histogram of how many people each Person agent
   * directly infected
   */
  public static Action<CentralAgent> collectPersonInfectionStats =
      Action.create(
          CentralAgent.class,
          ca -> {
            List<Integer> msgs = ca.getMessagesOfType(Messages.NumPeopleInfectedMsg.class)
                .stream()
                .map(Message.Integer::getBody)
                .collect(Collectors.toList());
            int arraySize = msgs.stream()
                .max(Integer::compare).orElse(0);

            ca.getGlobals().personInfectionHist = new ArrayList<>();
            for (int i = 0; i < arraySize + 1; i++) {
              ca.getGlobals().personInfectionHist.add(0);
            }
            msgs.forEach(i -> ca.getGlobals().personInfectionHist.set(i,
                ca.getGlobals().personInfectionHist.get(i) + 1));
          }
      );

  /**
   * Accepts {@link Messages.SupressionStatusMessage} from {@link Person} agents from action
   * {@link Person#reportSuppression}. Compares these messages to {@link Globals#nActiveAgents},
   * and reassigns some agents (randomly selected) in to align with the new number of active agents.
   */
  public static Action<CentralAgent> reassignSuppression =
      new Action<>(CentralAgent.class,
            ca -> {
              List<Messages.SupressionStatusMessage> suppressionStatusList =
                  ca.getMessagesOfType(Messages.SupressionStatusMessage.class);
              long numActive = suppressionStatusList.stream().filter(m -> !m.isSuppressed).count();
              long numToBeActive = ca.getGlobals().nActiveAgents;

              if (numActive == numToBeActive) {
                // No reassignment necessary
                return;
              }

              // Need to convert some suppressed to active
              if (numActive < numToBeActive) {
                long numSuppressedToTurn = numToBeActive - numActive;
                ArrayList<Messages.SupressionStatusMessage> suppressed =
                    suppressionStatusList.stream().filter(m -> m.isSuppressed).collect(Collectors.toCollection(ArrayList::new));
                List<Messages.SupressionStatusMessage> agentsToTurn = suppressed;
                if (numSuppressedToTurn < suppressed.size()) {
                  Random r = new Random((long)(ca.getPrng().uniform(0, 1).sample() * Long.MAX_VALUE - 1));
                  Collections.shuffle(suppressed, r);
                  agentsToTurn = suppressed.subList(0, (int) numSuppressedToTurn);
                }
                agentsToTurn.forEach(m -> {
                  ca.send(Messages.SupressionStatusMessage.class, sup -> sup.isSuppressed = false).to(m.getSender());
                });
              }
              // Need to convert some active to suppressed
              else if (numActive > numToBeActive){
                long numActiveToTurn = numActive - numToBeActive;
                ArrayList<Messages.SupressionStatusMessage> active =
                    suppressionStatusList.stream().filter(m -> !m.isSuppressed).collect(Collectors.toCollection(ArrayList::new));
                List<Messages.SupressionStatusMessage> agentsToTurn = active;
                if (numActiveToTurn < active.size()) {
                  Random r = new Random((long)(ca.getPrng().uniform(0, 1).sample() * Long.MAX_VALUE - 1));
                  Collections.shuffle(active, r);
                  agentsToTurn = active.subList(0, (int) numActiveToTurn);
                }
                agentsToTurn.forEach(m -> {
                  ca.send(Messages.SupressionStatusMessage.class, sup -> sup.isSuppressed = true).to(m.getSender());
                });
              }
            }
          );

  /**
   * Receives {@link Messages.ReportForVaccineMsg} from {@link Person} and returns
   * {@link Messages.VaccineAdministeredMsg} to the agents receiving vaccines.
   *
   * Currently gives vaccines randomly to all reporting agents, but we could
   * add vaccine priority logic here.
   */
  public static Action<CentralAgent> distributeVaccines =
          new Action<>(CentralAgent.class,
                  ca -> {
                    if(ca.getGlobals().numToVaccinate == 0) {
                      return;
                    }
                    if(!ca.hasMessageOfType(Messages.ReportForVaccineMsg.class)) {
                      return;
                    }
                    ArrayList<Messages.ReportForVaccineMsg> agents = new ArrayList<>(ca.getMessagesOfType(Messages.ReportForVaccineMsg.class));
                    Random r = new Random((long)(ca.getPrng().uniform(0, 1).sample() * Long.MAX_VALUE - 1));
                    Collections.shuffle(agents, r);
                    agents.subList(0,ca.getGlobals().numToVaccinate).forEach(msg -> {
                      ca.send(Messages.VaccineAdministeredMsg.class).to(msg.getSender());
                    });
          }
          );

  @AutoValue
  public static abstract class QuarantineInfo implements Comparable<QuarantineInfo> {
    public abstract long agentId();

    public abstract long quarantineUntil();

    public static QuarantineInfo create(long agentId, long quarantineUntil) {
      return new AutoValue_CentralAgent_QuarantineInfo(agentId, quarantineUntil);
    }

    @Override
    public int compareTo(QuarantineInfo o) {
      long quarantineInfo = this.quarantineUntil() - o.quarantineUntil();
      if (quarantineInfo != 0) {
        return quarantineInfo > 0 ? 1 : -1;
      }
      return this.agentId() - o.agentId() > 0 ? 1 : -1;
    }
  }
}
