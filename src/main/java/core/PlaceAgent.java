package core;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import simudyne.core.abm.Action;
import simudyne.core.abm.Agent;
import simudyne.core.graph.Message;
import simudyne.core.rng.SeededRandom;
import tau.TAUModel;

import java.util.*;
import java.util.stream.Collectors;

import static core.Utils.sortedCopyBySender;

public class PlaceAgent extends Agent<Globals> {

  private long placeId;
  private PlaceInfo placeInfo;

  @VisibleForTesting
  List<ImmutableList<Long>> occupancyHistory = new ArrayList<>();

  public void init() {
    this.placeId = this.getID();
  }

  public long placeId() {
    return this.placeId;
  }

  public PlaceInfo place() {
    return this.placeInfo;
  }

  public static Action<PlaceAgent> initPlaceAgent =
      Action.create(
          PlaceAgent.class,
          pla -> {
            pla.init();
          }
      );

  public static Action<PlaceAgent> sendSelfToCentralAgent =
      Action.create(
          PlaceAgent.class,
          pla -> {
            pla.send(Messages.PlaceAgentMessage.class,
                msg -> msg.setBody(pla.getID()))
                .to(pla.getGlobals().centralAgentID);
          }
      );

  @VisibleForTesting
  void setPlaceInfo(PlaceInfo placeInfo) {
    this.placeInfo = placeInfo;
  }

  // Because the number of Places is not known at the start of the simulation, one PlaceAgent
  // is generated initially in VIVIDCoreModel#setup and then that PlaceAgent uses the created
  // places to initialize itself and then spawn and initialize all of the other PlaceAgents
  public static Action<PlaceAgent> receivePlace =
      Action.create(
          PlaceAgent.class,
          pla -> {
            List<PlaceInfo> placeInfoList = pla
                .getMessagesOfType(Messages.PlaceMessage.class)
                .stream()
                .map(msg -> msg.placeInfo)
                .collect(Collectors.toList());

            // This initializes the single PlaceAgent that gets generated in VIVIDCoreModel#setup
            // before spawning all of the other PlaceAgents
            pla.setPlaceInfo(placeInfoList.get(0));
            pla.placeInfo.receivePlaceAgent(pla.getID());

            for (int i = 1; i < placeInfoList.size(); i++) {
              int finalI = i;
              pla.spawn(PlaceAgent.class, agent -> {
                agent.setPlaceInfo(placeInfoList.get(finalI));
                agent.placeInfo.receivePlaceAgent(agent.getID());
              });
            }
          }
      );

  @VisibleForTesting
  void addToOccupancyHistory(ImmutableList<Long> peoplePresent) {
    if (getGlobals().modules.getPlaceTypesOmittedFromContactTracing().contains(this.placeInfo.placeType())) {
      return;
    }
    occupancyHistory.add(peoplePresent);
    if (occupancyHistory.size() > getGlobals().contactTracingNumberOfDaysTraceback * getGlobals().tOneDay) {
      occupancyHistory.remove(0);
    }
  }

  /**
   * Receives {@link Messages.IAmHereMsg} from {@link Person#executeMovement}
   * Generates contacts and infections from the people present
   * Sends {@link Messages.InfectionMsg} to {@link Person#infectedByCOVID}
   * Send {@link Messages.YouInfectedSomeoneMsg} to {@link Person#infectedSomeoneElseWithCOVID}
   * Send {@link Messages.PlaceInfections} to {@link CentralAgent#processPlaceInfectionRates}
   */
  public static Action<PlaceAgent> generateContactsAndInfect =
      Action.create(
          PlaceAgent.class,
          pla -> {
            ImmutableList<Long> peoplePresent = ImmutableList.of();
            if (pla.hasMessagesOfType(Messages.IAmHereMsg.class)) {
              PlaceInfo pl = pla.place();
              List<Messages.IAmHereMsg> msgs = pla.getMessagesOfType(Messages.IAmHereMsg.class);
              ImmutableList.Builder<Long> builder = new ImmutableList.Builder<>();
              msgs.stream().map(msg -> msg.getSender()).sorted().distinct().forEach(builder::add);
              peoplePresent = builder.build();

              int totalInPlace = peoplePresent.size();
              Collection<ContactEventInfo> contacts = pla.getWhoToInfect(
                  sortedCopyBySender(msgs),
                  pla.getGlobals(), pla.getPrng());

              contacts.stream()
                  .filter(ContactEventInfo::resultedInTransmission)
                  .forEachOrdered(
                      transmission -> {
                        pla.send(Messages.InfectionMsg.class).to(transmission.infected());
                        final boolean outputTransmissions = pla.getGlobals().outputTransmissions;
                        transmission.infectedBy().ifPresent(infectedBy -> {
                          pla.send(Messages.YouInfectedSomeoneMsg.class, msg -> {
                            if (outputTransmissions) {
                              msg.newlyInfectedAgentId = transmission.infected();
                              msg.newlyInfectedMaskType =
                                      transmission.infectedTransmiissibilityInfo().get().wearsMask();
                              msg.newlyInfectedCompliancePhysicalDistancing =
                                      transmission.infectedTransmiissibilityInfo().get().physicalDistCompliance();
                              msg.infectedByMaskType =
                                      transmission.infectedByTransmiissibilityInfo().get().wearsMask();
                              msg.placeId = transmission.placeId();
                              msg.placeType = transmission.placeType();
                            }
                          }).to(infectedBy);
                        });
                      }
                  );
              int numStartedInfected = (int) msgs.stream()
                  .filter(msg -> msg.transmissibilityInfo.status() == Person.InfectionStatus.INFECTED)
                  .count();
              int numGotInfected = (int) contacts.stream()
                  .filter(ContactEventInfo::resultedInTransmission)
                  .count();

              pla.send(
                  Messages.PlaceInfections.class,
                  msg -> {
                    msg.placeType = pl.placeType();
                    msg.numGotInfected = numGotInfected;
                    msg.numStartedInfected = numStartedInfected;
                    msg.totalInPlace = totalInPlace;
                  })
                  .to(pla.getGlobals().centralAgentID);

            }
            pla.addToOccupancyHistory(peoplePresent);
          }
      );

  /**
   * Recieves occupancy request {@link core.Messages.RequestOccupancyMsg} from {@link Person#requestOccupancyFromPlacesVisited}
   * and sends back a {@link core.Messages.OccupancyMsg} to
   * {@link Person#receiveOccupancyHistoriesAndSendToCentralAgent}.
   */
  public static Action<PlaceAgent> sendOccupancy =
      Action.create(
          PlaceAgent.class,
          pla -> {
            final ImmutableList<ImmutableList<Long>> occupancy = ImmutableList.copyOf(pla.occupancyHistory);
            pla.getMessagesOfType(Messages.RequestOccupancyMsg.class).stream()
                .map(Message::getSender)
                .distinct()
                .sorted()
                .forEach(personId -> {
                  pla.send(Messages.OccupancyMsg.class, occupancyMsg -> {
                    occupancyMsg.peoplePresent = occupancy;
                  }).to(personId);
                });
          }
      );

  public Collection<ContactEventInfo> getWhoToInfect(
      List<Messages.IAmHereMsg> occupants, Globals globals, SeededRandom random) {
    HashMap<Long, ContactEventInfo> toInfect = new HashMap<>();

    if (occupants.size() <= 1) {
      return ImmutableList.of();
    }

    final Optional<Messages.IAmHereMsg> centerOccupant =
        occupants.stream().filter(msg -> msg.getSender() == this.place().center()).findFirst();
    if (this.place().networkType() == PlaceInfo.NetworkType.FULLY_CONNECTED_DEPENDENT_ON_CENTER
        || this.place().networkType() == PlaceInfo.NetworkType.STAR) {
      // The center agent is a no-show, so the event technically does not happen.
      // No infections.
      if (!centerOccupant.isPresent()) {
        return ImmutableList.of();
      }
    }

    double baseInfectionRate =
        globals.getInfectionRate(this.place().placeType())
            / globals.tOneDay;

    if (this.place().networkType() == PlaceInfo.NetworkType.STAR) {
      assert centerOccupant.isPresent();
      List<Messages.IAmHereMsg> contactedAgents =
          DefaultModulesImpl.sample(
              DefaultModulesImpl.getAllExcept(occupants, centerOccupant.get()),
              globals.numStaffToStudenContacts,
              random);
      for (Messages.IAmHereMsg occupant : contactedAgents) {
        if (occupant.getSender() == centerOccupant.get().getSender()) {
          continue;
        }
        Messages.IAmHereMsg infected;
        Messages.IAmHereMsg infectee;
        if (centerOccupant.get().transmissibilityInfo.isInfectious()) {
          infected = centerOccupant.get();
          infectee = occupant;
        } else if (occupant.transmissibilityInfo.isInfectious()) {
          infected = occupant;
          infectee = centerOccupant.get();
        } else {
          continue;
        }
        toInfect.put(
            infectee.getSender(),
            ContactEventInfo.create(
                // TODO I think infected and infectee need to be swapped in these first two parameters
                infected.getSender(),
                Optional.of(infectee.getSender()),
                this.placeId(),
                DefaultModulesImpl.willInfect(infected, infectee, baseInfectionRate, random),
                this.placeInfo.placeType(),
                globals.outputTransmissions ? infectee.transmissibilityInfo : null,
                globals.outputTransmissions ? infected.transmissibilityInfo : null));
      }
    }

    if (this.place().networkType() == PlaceInfo.NetworkType.FULLY_CONNECTED
        || this.place().networkType() == PlaceInfo.NetworkType.FULLY_CONNECTED_DEPENDENT_ON_CENTER
        || this.place().networkType() == PlaceInfo.NetworkType.FULLY_CONNECTED_WITH_FLAT_INFECTION_RATE) {
      for (int i = 0; i < occupants.size(); i++) {
        if (occupants.get(i).transmissibilityInfo.isInfectious()) {
          List<Messages.IAmHereMsg> contactedAgents =
              DefaultModulesImpl.sample(
                  DefaultModulesImpl.getAllExcept(occupants, occupants.get(i)),
                  occupants.get(i).transmissibilityInfo.contactRate(),
                  random);
          for (Messages.IAmHereMsg otherAgent : contactedAgents) {
            toInfect.put(
                otherAgent.getSender(),
                ContactEventInfo.create(
                    otherAgent.getSender(),
                    Optional.of(occupants.get(i).getSender()),
                    this.placeId(),
                    DefaultModulesImpl.willInfect(occupants.get(i), otherAgent, baseInfectionRate, random),
                    this.placeInfo.placeType(),
                    globals.outputTransmissions ? otherAgent.transmissibilityInfo : null,
                    globals.outputTransmissions ? occupants.get(i).transmissibilityInfo : null));
          }
        }
      }
    }

    if (this.place().networkType() == PlaceInfo.NetworkType.FLAT_INFECTION_RATE
          || this.place().networkType() == PlaceInfo.NetworkType.FULLY_CONNECTED_WITH_FLAT_INFECTION_RATE) {
      for(Messages.IAmHereMsg occupant : occupants) {
        toInfect.put(
                occupant.getSender(),
                ContactEventInfo.create(
                        occupant.getSender(),
                        Optional.empty(),
                        this.placeId(),
                        random.uniform(0.0, 1.0).sample() < getGlobals().placeTypeFlatInfectionRate,
                        this.placeInfo.placeType(),
                        globals.outputTransmissions ? occupant.transmissibilityInfo : null,
                        globals.outputTransmissions ? Person.PersonTransmissibilityInfo.dummyInfected() : null
                )
        );
      }
    }
    return ImmutableList.sortedCopyOf(
        Comparator.comparingLong(ContactEventInfo::infected), toInfect.values());
  }
}
