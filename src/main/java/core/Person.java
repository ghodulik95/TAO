package core;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import simudyne.core.abm.Action;
import simudyne.core.abm.Agent;
import simudyne.core.annotations.Variable;
import simudyne.core.rng.SeededRandom;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static core.Utils.sortedCopyBySender;



/**
 * The base Person class contains all logic around disease spread, symptom reporting, isolating,
 * spatial location, and base human factors such as compliance.
 *
 * <p>The Person class aims to contain any information that describes any person in any potential
 * simulation.
 */
public class Person extends Agent<Globals> implements Suppressible {

  public long personID; // ID of the student agent
  public double age; // Person age
  /**
   * The place the person will be moving to when {@link #executeMovement} is ran.
   */
  protected ImmutableList<PlaceInfo> currentPlaceInfos = ImmutableList.of();
  protected ImmutableList<PlaceInfo> additionalPlaceInfos = ImmutableList.of();
  public boolean isVaccinated = false;
  // infection characteristics
  // Step of getting infected
  public int timeInfected = 0;
  // Number of steps that the person's illness will run
  public int illnessDuration;
  // Number of steps after being infected till symptoms present
  public int symptomOnset;
  // True if person is experiencing/ed an asymptomatic infection
  public boolean isAsymptomatic;
  // Number of steps after being infected that person becomes infectious
  // TODO This is not used at the moment
  public int tInfectious;
  public boolean isSymptomaticFromOtherIllness = false;
  public int becameSymptomaticFromOtherIllnessAtStep = -1;
  public int otherIllnessRecoveryTime = -1;
  // True when a test is being processed, or person has been tested and
  // tested positive
  // TODO Test this for situations where a person would need multiple tests
  //      i.e. tests negative the first time and is tested again later
  public boolean hasBeenTested;
  public InfectionStatus status; // status of the agent (DEAD, RECOVERED, INFECTED)
  // Non-Pharmaceutical Interventions (Variables):
  public MaskType maskType = MaskType.NONE;
  // True if the person is currently deciding to isolate
  // They still will deviate from isolating based on compliance
  public boolean isSelfIsolatingBecauseOfSymptoms = false;
  public boolean isSelfIsolatingBecauseOfContactTracing = false;
  // The places that the agent goes to when isolating
  // This can be empty if we want it to be impossible to infect while isolating
  public ImmutableList<PlaceInfo> isolationPlaceInfos;
  // Tracking when someone has decided to isolate
  // Initial value is MAX, since no one is contact notified initially
  public long startedIsolatingFromContactNotifyAt = Long.MIN_VALUE;
  public DailySchedule dailySchedule = DailySchedule.dummy();
  private boolean infectedFromSusceptibleThisStep = false;
  @VisibleForTesting
  List<ImmutableList<Long>> placeHistory = new ArrayList<>();

  // Compliances
  // TODO: Normalize compliance names
  // Likelihood to report when developing symptoms
  public double compSymptomsReport;
  // Likelihood to decide to isolate when symptomatic
  public double compQuarantineWhenSymptomatic;
  // Likelihood to decide to wear a mak
  // TODO Look into if compliance for mask should be called for each place or
  // once
  public double complianceMask;
  // Likelihood of a person to comply with staying at their isolationPlaces
  public double complianceIsolating;
  // Likelihood to decide to isolate when contact notified
  // TODO Do we need different compliance here for being notified about +test
  // vs symptomatic?
  public double complianceIsolateWhenContactNotified;
  public double compliancePhysicalDistancing;
  public int contactRate;
  // Likelihood of a person to go to a scheduled place that is optional
  public double probGoesToOptionalPlace;
  // Equity factors and equity-factor dependent factors
  public RaceEthnicity raceEthnicity;
  public SES ses;
  public boolean doesExternalJob;
  public boolean doesExternalActivities;
  public boolean usesPublicTransit;
  public boolean livesInMultigenerationalHousehold;

  public double probHostsAdditionalEvent;
  public double probAttendsAdditionalEvent;

  public int numPeopleInfected = 0;

  public boolean suppressed = false;

  /**
   * The color of the Person node in the UI.
   */
  @Variable
  public String _color() {
    if (status == InfectionStatus.SUSCEPTIBLE) {
      return isSelfIsolatingBecauseOfSymptoms ? "white" : "darkorange";
    } else if (status == InfectionStatus.INFECTED) {
      return isSelfIsolatingBecauseOfSymptoms ? "gray" : "darkred";
    } else if (status == InfectionStatus.RECOVERED) {
      return "darkgreen";
    } else if (status == InfectionStatus.DEAD) {
      return "black";
    }
    throw new IllegalStateException("All agents must have an infection status");
  }

  public static Action<Person> initPerson =
      Action.create(Person.class, Person::init);

  /**
   * Called at simulation start. Subclasses can override, but should call super.init().
   */
  public void init() {
    PersonInitializationInfo info = initializationInfo();
    // Generate agent characteristics
    this.personID = this.getID(); // Every agent is automatically assigned an ID upon initialisation
    this.age = info.ageSupplier().get(); // Generate age
    // Non-pharmaceutical interventions (NPI): mask wearing
    if (getGlobals().mandateMask) {
      // initialise mask wearing for compliant thiss
      this.complianceMask = info.maskComplianceSupplier().get();
      this.maskType = info.maskTypeSupplier().get();
    }

    // Generate compliance factor (social distancing)
    this.compQuarantineWhenSymptomatic = info.quarantineWhenSymptomaticComplianceSupplier().get();
    this.compSymptomsReport = info.symptomsReportComplianceSupplier().get();
    this.complianceIsolating = info.isolationComplianceSupplier().get();
    this.complianceIsolateWhenContactNotified = info.isolateWhenContactNotifiedSupplier().get();
    this.compliancePhysicalDistancing = info.compliancePhysicalDistancingSupplier().get();
    this.contactRate = info.contactRateSupplier().get();

    // Generate likelihood to go to an optional place
    this.probGoesToOptionalPlace = info.probGoesToOptionalPlaceSupplier().get();

    // Equity factors and equity-dependent factors
    this.raceEthnicity = info.raceEthnicitySupplier().get();
    this.ses = info.sesFunction().apply(this);
    this.doesExternalJob = info.doesExternalJobFunction().apply(this);
    this.doesExternalActivities = info.doesExternalActivityFunction().apply(this);
    this.usesPublicTransit = info.usesPublicTransitFunction().apply(this);
    this.livesInMultigenerationalHousehold = info.livesInMultigenerationalHouseholdFunction().apply(this);
    this.isVaccinated = info.isVaccinated().get();

    // Determine person that are initially infected
    this.status = info.initialInfectionStatusSupplier().get();
    if (this.status == InfectionStatus.INFECTED) {
      this.timeInfected = getPrng().discrete(-7, 0).sample();
    }

    this.suppressed = info.suppressionSupplier().get();

    if(this.suppressed) {
      this.status = InfectionStatus.SUPPRESSED;
    }
  }

  /**
   * Provides an {@link PersonInitializationInfo} which will initialize this kind of person.
   *
   * <p>Subclasses should extend this and user {@link
   * PersonInitializationInfo#builderSetWithGlobalDefaults(Globals, SeededRandom)} or {@link
   * PersonInitializationInfo.Builder} directly in order to set up their initialization valus.
   */
  public PersonInitializationInfo initializationInfo() {
    return PersonInitializationInfo.builderSetWithGlobalDefaults(getGlobals(), getPrng()).build();
  }

  /**
   * The simulation will start with when t=0, all people send themselves in a message to the {@link
   * CentralAgent}. This is a heavy operation which occurs only once.
   */
  public static Action<Person> sendSelfToCentralAgentForScheduleCreation =
      Action.create(
          Person.class,
          person -> {
            if (person.getGlobals().tStep != 0) {
              throw new IllegalStateException(
                  "Place and schedule initialization can only be done at step 0.");
            }
            person
                .send(Messages.PersonMessage.class, msg -> msg.person = person)
                .to(person.getGlobals().centralAgentID);
          });

  /**
   * At t=0, after all people send themselves to {@link CentralAgent}, {@link CentralAgent} will
   * send back a schedule and some secondary init function.
   *
   * <p>Note that we receive a {@link Consumer} of a person, since (I think) in the Simudyne sdk,
   * changes to agent's should be ran in their own thread if we want the changes to take. IOW, if we
   * made changes to a Person object in CentralAgent, it would not be guaranteed to actually change
   * the person.
   */
  public static Action<Person> receiveSchedule =
      Action.create(
          Person.class,
          person -> {
            if (person.getGlobals().tStep != 0) {
              throw new IllegalStateException(
                  "Place and schedule initialization can only be done at step 0.");
            }
            person
                .getMessagesOfType(Messages.ScheduleMessage.class)
                .forEach(
                    msg -> {
                      person.dailySchedule = msg.schedule;
                      person.isolationPlaceInfos = msg.schedule.isolationPlaces();
                      msg.schedule.secondaryInitialization().accept(person);
                    });
          });

  /**
   * May be called in child classes to set the current place.
   */
  public void setCurrentPlaces(List<PlaceInfo> currentPlaceInfos) {
    this.currentPlaceInfos = ImmutableList.copyOf(currentPlaceInfos);
  }

  public void setAdditionalPlaces(List<PlaceInfo> additionalPlaceInfos) {
    this.additionalPlaceInfos = ImmutableList.copyOf(additionalPlaceInfos);
  }

  /**
   * May be called in child classes to set the current place.
   */
  public void setCurrentPlaces(PlaceInfo currentPlaceInfo) {
    this.setCurrentPlaces(ImmutableList.of(currentPlaceInfo));
  }

  public void setAdditionalPlaces(PlaceInfo additionalPlaceInfo) {
    this.setAdditionalPlaces(ImmutableList.of(additionalPlaceInfo));
  }

  public ImmutableList<PlaceInfo> getCurrentAndAdditionalPlaceInfos() {
    return ImmutableList.<PlaceInfo>builder()
            .addAll(this.getCurrentPlaces())
            .addAll(this.getAdditionalPlaceInfos())
            .build();
  }

  public ImmutableList<PlaceInfo> getCurrentPlaces() {
    return ImmutableList.<PlaceInfo>builder()
        .addAll(this.currentPlaceInfos)
        .build();
  }

  public ImmutableList<PlaceInfo> getAdditionalPlaceInfos() {
    return ImmutableList.<PlaceInfo>builder()
            .addAll(this.additionalPlaceInfos)
            .build();
  }

  public boolean choosesToIsolate() {
    return ((isSelfIsolatingBecauseOfSymptoms || isSelfIsolatingBecauseOfContactTracing)
        && getPrng().uniform(0, 1).sample() < complianceIsolating)
        || getGlobals().forceAllAgentsToIsolate;
  }

  public boolean isDoneSelfIsolatingBecauseOfContactTracing() {
    return isSelfIsolatingBecauseOfContactTracing
        && startedIsolatingFromContactNotifyAt > 0
        && startedIsolatingFromContactNotifyAt
        + (getGlobals().contactNotifiedNumberOfDaysToIsolate * getGlobals().tOneDay)
        < getGlobals().tStep;
  }

  /**
   * Subclasses should override this method if there is any implementation specific schedule logic.
   */
  public void decideNextLocationIfNotIsolating() {
    setCurrentPlaces(
        ImmutableList.sortedCopyOf(
            Comparator.comparingLong(PlaceInfo::placeId),
            getScheduledPlaces().stream()
                .filter(this::isAttendingToday)
                .collect(Collectors.toList())));
  }

  public List<PlaceInfo> getScheduledPlaces() {
    doRandomSleepIfTesting();
    return dailySchedule.placesAtStepMap().get(getGlobals().tStep % dailySchedule.placesAtStepMap().size());
  }

  /**
   * Subclasses shold not override this method unless they are also changing the isolation behavior.
   * This method already takes into account isolation compliance, and the appropriate method to
   * override would be {@link #decideNextLocationIfNotIsolating()}, since we want to include the
   * base iolation logic.
   */
  public void decideNextLocation() {
    if(this.isSuppressed()) {
      setCurrentPlaces(ImmutableList.of());
      return;
    }
    if (isDoneSelfIsolatingBecauseOfContactTracing()) {
      // TODO This is going to cause an issue if the person is symptomatic and would decide to
      // continue isolating.
      isSelfIsolatingBecauseOfContactTracing = false;
    }

    if (choosesToIsolate()) {
      setCurrentPlaces(isolationPlaceInfos);
    } else {
      decideNextLocationIfNotIsolating();
    }
  }

  /**
   * This will be called at initialization t=0, in case there is more that needs to be ran on init.
   */
  public void initialiseFirstPlace() {
  }

  /**
   * Each time the agent goes to this place, they will wear a mask with this returned likelihood.
   * Add here or override in order to add place/place-type specific mask compliance, e.g. not
   * wearing masks in Suites.
   */
  protected double getLikelihoodOfWearingMaskAtPlace(PlaceInfo placeInfo) {
    return complianceMask;
  }

  /**
   * Determine if agent infected by COVID-19 is asymptomatic, symptomatic or severe. Severity is
   * calculated based on distribution returned by {@link #getInfectionTrajectoryDistribution()}.
   */
  public InfectionCharacteristics infectionSeverity(int stepInfected) {

    // Potential Addition of Inputs: Age of Agent
    // By having the age of an agent as input, we are able to change the proportion of asymptomatic,
    // symptomatic and severe cases
    double severity = getPrng().uniform(0, 1).sample();
    InfectionTrajectoryDistribution trajectoryDistribution = getInfectionTrajectoryDistribution();

    int tInfectious = 0;
    int illnessDuration = 0;
    int symptomsOnset = 0;
    boolean isAsymptomatic = false;

    // asymptomatic cases
    if (severity < trajectoryDistribution.percentageAsymptomaticCases()) {
      tInfectious =
          getPrng()
              .discrete(
                  trajectoryDistribution.infectiousRangeStart(),
                  trajectoryDistribution.infectiousRangeEnd())
              .sample()
              * getGlobals().tOneDay
              + stepInfected;
      illnessDuration =
          getPrng()
              .discrete(
                  trajectoryDistribution.illnessDurationNonSevereRangeStart(),
                  trajectoryDistribution.illnessDurationNonSevereRangeEnd())
              .sample()
              * getGlobals().tOneDay
              + stepInfected;
      symptomsOnset =
              getPrng()
                      .discrete(
                              trajectoryDistribution.symptomsOnsetRangeStart(),
                              trajectoryDistribution.symptomsOnsetRangeEnd())
                      .sample()
                      * getGlobals().tOneDay
                      + stepInfected;
      isAsymptomatic = true;
    }
    // symptomatic cases
    else if (severity >= trajectoryDistribution.percentageAsymptomaticCases()
        && severity
        < trajectoryDistribution.percentageAsymptomaticCases()
        + trajectoryDistribution.percentageNonSevereSymptomaticCases()) {
      tInfectious =
          getPrng()
              .discrete(
                  trajectoryDistribution.infectiousRangeStart(),
                  trajectoryDistribution.infectiousRangeEnd())
              .sample()
              * getGlobals().tOneDay
              + stepInfected;
      illnessDuration =
          getPrng()
              .discrete(
                  trajectoryDistribution.illnessDurationNonSevereRangeStart(),
                  trajectoryDistribution.illnessDurationNonSevereRangeEnd())
              .sample()
              * getGlobals().tOneDay
              + stepInfected;
      symptomsOnset =
          getPrng()
              .discrete(
                  trajectoryDistribution.symptomsOnsetRangeStart(),
                  trajectoryDistribution.symptomsOnsetRangeEnd())
              .sample()
              * getGlobals().tOneDay
              + stepInfected;
    }
    // severe cases
    else if (severity >= (1 - trajectoryDistribution.percentageSevereCases())) {
      tInfectious =
          getPrng()
              .discrete(
                  trajectoryDistribution.infectiousRangeStart(),
                  trajectoryDistribution.infectiousRangeEnd())
              .sample()
              * getGlobals().tOneDay
              + stepInfected;
      illnessDuration =
          getPrng()
              .discrete(
                  trajectoryDistribution.illnessDurationSevereRangeStart(),
                  trajectoryDistribution.illnessDurationSevereRangeEnd())
              .sample()
              * getGlobals().tOneDay
              + stepInfected;
      symptomsOnset =
          getPrng()
              .discrete(
                  trajectoryDistribution.symptomsOnsetRangeStart(),
                  trajectoryDistribution.symptomsOnsetRangeEnd())
              .sample()
              * getGlobals().tOneDay
              + stepInfected;
    } else {
      throw new IllegalArgumentException("The given InfectionTrajectoryDistribution is invalid.");
    }

    return InfectionCharacteristics.create(tInfectious, illnessDuration, symptomsOnset, isAsymptomatic);
  }

  /**
   * The infection trajectory distribution for this agent. Override this for testing.
   */
  // TODO This should maybe go in Modules
  public InfectionTrajectoryDistribution getInfectionTrajectoryDistribution() {
    return getGlobals().getInfectionTrajectoryDistribution(this);
  }

  /**
   * Method to check if an agent should die.
   */
  public boolean checkDeath() {
    if(isAsymptomatic) {
      return false;
    }
    double pAgeDeathThres = getGlobals().getProbabilityOfDeathGivenSevereIllness(this);

    // Normalise pAgeDeathThres, (22- 4) = Expected value of (illness duration - symptoms onset) for
    // severely ill person.
    // Rationale: for asymptomatic agents, they won't die because asymptomaticness is checked first
    // pAgeDeathThres (symptomOnset > illnessDuration)
    // Rationale: symptomatic (but not severe) person has a lower probability of dying.
    double expectedIllnessDurationSevere =
        (getGlobals().getInfectionTrajectoryDistribution(this).illnessDurationSevereRangeStart()
            + getGlobals()
            .getInfectionTrajectoryDistribution(this)
            .illnessDurationSevereRangeEnd())
            / 2.0
            * getGlobals().tOneDay;
    double expectedSymptomsOnsetSevere =
        (getGlobals().getInfectionTrajectoryDistribution(this).symptomsOnsetRangeStart()
            + getGlobals().getInfectionTrajectoryDistribution(this).symptomsOnsetRangeEnd())
            / 2.0
            * getGlobals().tOneDay;
    pAgeDeathThres =
        (pAgeDeathThres / (expectedIllnessDurationSevere - expectedSymptomsOnsetSevere))
            * (illnessDuration - symptomOnset);

    // Random probability for death
    double pKilled = getPrng().uniform(0, 1).sample();

    return pKilled < pAgeDeathThres;
  }

  public void die() {
    status = InfectionStatus.DEAD; // change status to dead

    // Sever connections with other connected agents by sending message to connected agents
    getLinks(Links.PersonToPersonLink.class)
        .forEach(
            link -> {
              send(Messages.RIPmsg.class).to(link.getTo());
              link.remove();
            });

    send(Messages.RIPmsg.class).to(getGlobals().centralAgentID);
  }

  /**
   * Method to update statistics in the console.
   *
   * <p>Note: Updating accumlators can mess up tests, so it is better to update accumplators in this method only. You
   * can use class member flags to flag that a certain accumlator needs to be updated.
   */
  public void updateAccumulators() {
    if (status == InfectionStatus.SUSCEPTIBLE) {
      getLongAccumulator("totSusceptible").add(1);
      getGlobals().numSusceptible++;
      if (isSelfIsolatingBecauseOfSymptoms || isSelfIsolatingBecauseOfContactTracing) {
        getLongAccumulator("totQuarantineSusceptible").add(1);
        getGlobals().numQuarantineSusceptible++;
      }
    } else if (status == InfectionStatus.INFECTED) {
      getLongAccumulator("totInfected").add(1);
      getGlobals().numInfected++;
      if (isSelfIsolatingBecauseOfSymptoms || isSelfIsolatingBecauseOfContactTracing) {
        getLongAccumulator("totQuarantineInfected").add(1);
        getGlobals().numQuarantineInfected++;
      }
    } else if (status == InfectionStatus.DEAD) {
      getLongAccumulator("totDead").add(1);
      getGlobals().numDead++;
    } else if (status == InfectionStatus.RECOVERED) {
      getLongAccumulator("totRecovered").add(1);
      getGlobals().numRecovered++;
    }
    if (infectedFromSusceptibleThisStep) {
      getLongAccumulator("numInfectionsThisStep").add(1);
      infectedFromSusceptibleThisStep = false;
    }
  }

  // Used in the initialisation step t=0
  public static Action<Person> setupInitialInfectionState =
      ActionFactory.createSuppressibleAction(
          Person.class,
          person -> {

            // Determine if agent infected by COVID is asymptomatic, symptomatic or severe
            if (person.status == InfectionStatus.INFECTED) {
              person.setInfected();
            }
          });

  // core.Person decides where to move next
  public static Action<Person> setInitialLocation =
      Action.create(
          Person.class,
          person -> {
            person.initialiseFirstPlace();
          });

  // Person decides where to move next
  public static Action<Person> movePerson =
      ActionFactory.createSuppressibleAction(
          Person.class,
          person -> {
            if (person.status != InfectionStatus.DEAD) {
              person.decideNextLocation();
            }
          });

  protected ImmutableList<PlaceInfo> generateAdditionalPlace() {
    return ImmutableList.of();
  }

  /**
   * If the Person is hosting an event, sends Messages.PlaceInfoMessage
   * to everyone in the Person's social network (everyone the Person has
   * a Links.SocialLink to)
   */
  public static Action<Person> decideToHostAdditionalEvent =
      ActionFactory.createSuppressibleAction(
          Person.class,
          person -> {
            if (person.getPrng().uniform(0.0, 1.0).sample()
                < person.probHostsAdditionalEvent) {
              person.generateAdditionalPlace().forEach(
                  place -> {
                    person.getLinks(Links.SocialLink.class).send(Messages.PlaceInfoMessage.class, place);
                    person.send(Messages.PlaceInfoMessage.class, place).to(person.personID);
                  });
            }
          });

  public static Action<Person> decideToAttendAdditionalEvent =
      ActionFactory.createSuppressibleAction(
          Person.class,
          person -> {
            ImmutableList.Builder<PlaceInfo> builder = ImmutableList.builder();
            sortedCopyBySender(person.getMessagesOfType(Messages.PlaceInfoMessage.class))
                .forEach(msg -> {
                  if (person.getPrng().uniform(0.0, 1.0).sample() < person.probAttendsAdditionalEvent) {
                    builder.add(msg.getBody());
                  }
                });
            person.additionalPlaceInfos = builder.build();
          }
      );

  public static Action<Person> getRandomlyInfected =
          ActionFactory.createSuppressibleAction(
                  Person.class,
                  person -> {
                    if(person.getGlobals().numToRandomlyInfect == 0 ||
                        person.status == InfectionStatus.INFECTED) {
                      return;
                    }
                    long numActiveNotInfected = person.getGlobals().nActiveAgents -
                            person.getLongAccumulator("currentInfected").value();
                    double percInfected = person.getGlobals().numToRandomlyInfect /
                            (double)numActiveNotInfected;

                    if(person.getPrng().uniform(0.0, 1.0).sample() < percInfected) {
                      person.setInfected();
                    }
                  }
          );

  private void updatePlaceHistory() {
    ImmutableList.Builder<Long> builder = new ImmutableList.Builder<>();
    getCurrentAndAdditionalPlaceInfos().stream().map(PlaceInfo::placeId).forEach(builder::add);
    placeHistory.add(builder.build());
    if (placeHistory.size() > getGlobals().contactTracingNumberOfDaysTraceback * getGlobals().tOneDay) {
      placeHistory.remove(0);
    }
  }

  /**
   * Multiplies each compliance value by {@link Globals.complianceModifier}
   *
   * TODO: Change code so that we wouldn't have to add a compliance value here
   *       if we added one to Person
   * Perhaps compliance values could be in their own class with modify methods.
   * Then different model implementations could extend the Compliance class to
   * add model specific compliances.
   */
  public static Action<Person> modifyCompliance =
          Action.create(
                  Person.class,
                  person -> {
                    double compMultiplier = person.getGlobals().complianceModifier;
                    if(compMultiplier < 0 || compMultiplier == 1.0) {
                      return;
                    }

                    person.compSymptomsReport *= compMultiplier;
                    person.compQuarantineWhenSymptomatic *= compMultiplier;
                    person.complianceMask *= compMultiplier;
                    person.complianceIsolating *= compMultiplier;
                    person.complianceIsolateWhenContactNotified *= compMultiplier;
                    person.compliancePhysicalDistancing *= compMultiplier;
                  }
          );

  /**
   * Sends {@link Messages.ReportForVaccineMsg} to {@link CentralAgent} to try and get
   * a vaccine.
   *
   * Here is where we can implement vaccine adoption logic.
   */
  public static Action<Person> reportForVaccine =
          ActionFactory.createSuppressibleAction(
                  Person.class,
                  person -> {
                    if (!person.isVaccinated) {
                      person.send(Messages.ReportForVaccineMsg.class).to(person.getGlobals().centralAgentID);
                    }
                  }
          );

  /**
   * Receives {@link Messages.VaccineAdministeredMsg} from {@link CentralAgent} if
   * this agent has been vaccinated.
   *
   * We could potentially add delayed vaccine effectiveness here.
   */
  public static Action<Person> getVaccinated =
          Action.create(
                  Person.class,
                  person -> {
                    if(person.hasMessageOfType(Messages.VaccineAdministeredMsg.class)) {
                      person.isVaccinated = true;
                    }
                  }
          );

  /**
   * Reports to {@link CentralAgent} where the agent will be going on this step/
   */
  public static Action<Person> executeMovement =
      ActionFactory.createSuppressibleAction(
          Person.class,
          person -> {
            if (person.status != InfectionStatus.DEAD) {
              person.updatePlaceHistory();
              person.getCurrentPlaces().forEach(
                  place -> {
                    boolean willWearMask =
                        person.getLikelihoodOfWearingMaskAtPlace(place)
                            > person.getPrng().uniform(0, 1).sample();
                    PersonTransmissibilityInfo transmissibilityInfo =
                        PersonTransmissibilityInfo.create(person, willWearMask);

                    person
                        .send(
                            Messages.IAmHereMsg.class,
                            msg -> {
                              msg.transmissibilityInfo = transmissibilityInfo;
                            })
                        .to(place.placeId());
                  });

              person.getAdditionalPlaceInfos().forEach(
                      place -> {
                        boolean willWearMask =
                                (person.getLikelihoodOfWearingMaskAtPlace(place) *
                                        person.getGlobals().additionalPlaceCompRed)
                                        > person.getPrng().uniform(0, 1).sample();
                        PersonTransmissibilityInfo transmissibilityInfo =
                                PersonTransmissibilityInfo.create(person, willWearMask,
                                        person.getGlobals().additionalPlaceCompRed);

                        person
                                .send(
                                        Messages.IAmHereMsg.class,
                                        msg -> {
                                          msg.transmissibilityInfo = transmissibilityInfo;
                                        })
                                .to(place.placeId());
                      });
            }
          });

  /**
   * Action to handle infection message from infected student to susceptible student
   */
  public static Action<Person> infectedByCOVID =
      ActionFactory.createSuppressibleAction(
          Person.class,
          person -> {
            if (person.hasMessagesOfType(Messages.InfectionMsg.class)) {
              // check if person is susceptible
              if (person.status == InfectionStatus.SUSCEPTIBLE) {
                person.setInfected();
              }
            }
          });

  /**
   * Receives {@link Messages.YouInfectedSomeoneMsg} from {@link PlaceAgent#generateContactsAndInfect}
   * Increments numPeopleInfected counter by the number of messages each step
   */
  public static Action<Person> infectedSomeoneElseWithCOVID =
      ActionFactory.createSuppressibleAction(
          Person.class,
          person -> {
            List<Messages.YouInfectedSomeoneMsg> youInfectedMsgs =
                person.getMessagesOfType(Messages.YouInfectedSomeoneMsg.class);
            person.numPeopleInfected += youInfectedMsgs.size();

            if (person.getGlobals().outputTransmissions) {
              List<String> transmissionInfos = person.generateTransmissionInfos(youInfectedMsgs);
              for (String transmissionInfo : transmissionInfos) {
                person.send(Messages.OutputWriterStringMessage.class, msg -> {
                  msg.key = OutputWriterAgent.KEY_TRANSMISSIONS;
                  msg.value = transmissionInfo;
                }).to(person.getGlobals().outputWriterAgentID);
              }
            }
          }
      );

  /*
    The full output we are building:
    "InfectingAgentId,isSymptomatic,stepExposure,stepSymptoms,stepRecover,isAsymptomatic," +
    "AgentType,comSymptomsReport,compQuarantineWhenSymptomatic,complianceMask,complianceIsolating," +
    "isSelfIsolatingBecauseOfSymptoms,isSelfIsolatingBecauseOfContactTracing," +
    "complianceIsolateWhenContactNotified,compliancePhysicalDistancing,contactRate," +
    "probHostsAdditionalEvent,probAttendsAdditionalEvent,maskType,placeType,placeId,newlyInfectedAgentId," +
    "newlyInfectedCompliancePhysicalDistancing,newlyInfectedMaskType\n"
   */

  private List<String> generateTransmissionInfos(List<Messages.YouInfectedSomeoneMsg> msgs) {
    String agentInfo = generateThisAgentTransmissionInfo();

    List<String> transmissionInfos = new ArrayList<>();
    for (Messages.YouInfectedSomeoneMsg msg : msgs) {
      StringBuilder sb = new StringBuilder();
      sb.append(agentInfo);
      sb.append(msg.infectedByMaskType.ordinal());
      sb.append(',');
      sb.append(msg.placeType);
      sb.append(',');
      sb.append(msg.placeId);
      sb.append(',');
      sb.append(msg.newlyInfectedAgentId);
      sb.append(',');
      sb.append(msg.newlyInfectedMaskType.ordinal());
      sb.append(',');
      sb.append(msg.newlyInfectedCompliancePhysicalDistancing);
      transmissionInfos.add(sb.toString());
    }
    return transmissionInfos;
  }

  private String generateThisAgentTransmissionInfo() {
    StringBuilder sb = new StringBuilder();

    sb.append(this.personID);
    sb.append(',');
    sb.append(this.isSymptomatic());
    sb.append(',');
    sb.append(this.timeInfected);
    sb.append(',');
    sb.append(this.symptomOnset);
    sb.append(',');
    sb.append(this.illnessDuration);
    sb.append(',');
    sb.append(this.isAsymptomatic);
    sb.append(',');
    sb.append(this.getClass().toString());
    sb.append(',');
    sb.append(this.compSymptomsReport);
    sb.append(',');
    sb.append(this.compQuarantineWhenSymptomatic);
    sb.append(',');
    sb.append(this.complianceMask);
    sb.append(',');
    sb.append(this.complianceIsolating);
    sb.append(',');
    sb.append(this.isSelfIsolatingBecauseOfSymptoms);
    sb.append(',');
    sb.append(this.isSelfIsolatingBecauseOfContactTracing);
    sb.append(',');
    sb.append(this.complianceIsolateWhenContactNotified);
    sb.append(',');
    sb.append(this.compliancePhysicalDistancing);
    sb.append(',');
    sb.append(this.contactRate);
    sb.append(',');
    sb.append(this.probHostsAdditionalEvent);
    sb.append(',');
    sb.append(this.probAttendsAdditionalEvent);
    sb.append(',');

    return sb.toString();
  }

  /**
   * Sends {@link Messages.NumPeopleInfectedMsg} to {@link CentralAgent#collectPersonInfectionStats}
   * These messages are used to compile a histogram of how many other people
   * each Person agent directly infected
   */
  public static Action<Person> sendNumPeopleInfected =
      ActionFactory.createSuppressibleAction(
          Person.class,
          person -> person.send(Messages.NumPeopleInfectedMsg.class,
              msg -> msg.setBody(person.numPeopleInfected))
              .to(person.getGlobals().centralAgentID)
      );

  /**
   * Everything that needs to be done when a person is getting infected. The characteristics of this
   * infection are drawn, and infection tracking values are set.
   */
  public void setInfected() {
    if(this.status == InfectionStatus.SUPPRESSED) {
      throw new IllegalStateException("A suppressed person should not be set to infected.");
    }

    // change status to infected
    this.status = InfectionStatus.INFECTED;
    this.infectedFromSusceptibleThisStep = true;

    // record time infected
    if (this.getGlobals().tStep > 0) {
      this.timeInfected = this.getGlobals().tStep;
    }
    // (1) Determine if infection is asymptomatic, symptomatic or severe
    // (2) Get illness duration, symptoms onset and infectious period
    InfectionCharacteristics infectionCharacteristics = this.infectionSeverity(this.timeInfected);
    this.tInfectious = infectionCharacteristics.tInfectious();
    this.illnessDuration = infectionCharacteristics.illnessDuration();
    this.symptomOnset = infectionCharacteristics.symptomsOnset();
    this.isAsymptomatic = infectionCharacteristics.isAsymptomatic();

    if (this.getGlobals().tStep == 0) {

      // Initially infected person may be isolating due to symptoms
      if (this.isSymptomatic()) {
        double pIsolate = getPrng().uniform(0, 1).sample();
        if (pIsolate < compQuarantineWhenSymptomatic) {
          isSelfIsolatingBecauseOfSymptoms = true;
        }
      }

      // Initially infected agents may be ordered to quarantine, and then they will based on comp
      double pOrderedToQuarnatine = this.getPrng().uniform(0, 1).sample();
      if (pOrderedToQuarnatine < getGlobals().percInitialInfectedQuarantineOrder) {
        double pIsolateFromContractNotify = this.getPrng().uniform(0, 1).sample();
        if (pIsolateFromContractNotify < this.complianceIsolateWhenContactNotified) {
          this.isSelfIsolatingBecauseOfContactTracing = true;
          this.startedIsolatingFromContactNotifyAt = 0;
        }
      }
    }
  }

  /**
   * Report symptoms when symptomatic based on compliance compliance. Also choose to isolate based
   * on compliance.
   */
  public static Action<Person> reportSymptoms =
      ActionFactory.createSuppressibleAction(
          Person.class,
          person -> {
            double pReport = person.getPrng().uniform(0, 1).sample();

            // TODO This should probably only happen once per infection trajectory,
            //      Not at each times step.
            if (person.isSymptomatic()
                && !person.hasBeenTested
                && pReport < person.compSymptomsReport) {
              person.send(Messages.SymptomaticMsg.class).to(person.getGlobals().centralAgentID);
            }

            // TODO This should probably only happen once per infection trajectory,
            //      Not at each times step.
            double pIsolate = person.getPrng().uniform(0, 1).sample();
            if (person.isFirstTimeSymptomatic()
                && pIsolate < person.compQuarantineWhenSymptomatic) {
              person.isSelfIsolatingBecauseOfSymptoms = true;
            }
          });

  /**
   * Receives messages {@link core.Messages.QuarantineOrderMsg}, {@link core.Messages.QuarantineReleaseMsg}, and/or
   * {@link core.Messages.TestAdministeredMsg}, and quarantines, stops quarantining, or takes a test accordingly.
   * If getting tested, will send {@link core.Messages.InfectionStatusMsg} to
   * {@link CentralAgent#processInfectionStatus}.
   */
  public static Action<Person> receiveQuarantineStartOrStopAndAdministerTest =
      ActionFactory.createSuppressibleAction(
          Person.class,
          person -> {
            person.startQuarantineIfOrdered();
            person.stopQuarantineIfOrdered();
            person.administerTestIfOrdered();
          });

  private void startQuarantineIfOrdered() {
    if (this.hasMessagesOfType(Messages.QuarantineOrderMsg.class)) {
      List<Messages.QuarantineOrderMsg> quarantineOrderMsgs
          = this.getMessagesOfType(Messages.QuarantineOrderMsg.class);
      Optional<Messages.QuarantineOrderMsg> msgWithExposureTime = quarantineOrderMsgs
          .stream()
          .filter(msg -> msg.exposureTime != null)
          .max(Comparator.comparingLong(msg -> msg.exposureTime));

      double pIsolateFromContractNotify = this.getPrng().uniform(0, 1).sample();
      if (pIsolateFromContractNotify
          < this.complianceIsolateWhenContactNotified) {
        this.isSelfIsolatingBecauseOfContactTracing = true;
        this.startedIsolatingFromContactNotifyAt = msgWithExposureTime.isPresent() ?
            msgWithExposureTime.get().exposureTime : this.getGlobals().tStep;
      }
    }
  }

  private void stopQuarantineIfOrdered() {
    if (this.hasMessagesOfType(Messages.QuarantineReleaseMsg.class)) {
      this.isSelfIsolatingBecauseOfContactTracing = false;
      this.startedIsolatingFromContactNotifyAt = Long.MIN_VALUE;
    }
  }

  /**
   * Returns true if the person has symptoms.
   */
  public boolean isSymptomatic() {
    if(status == InfectionStatus.INFECTED &&
            symptomOnset <= getGlobals().tStep &&
            !this.isAsymptomatic) {
      return true;
    }
    if(isSymptomaticFromOtherIllness) {
      return true;
    }
    return false;
  }

  public static Action<Person> countInfected =
          ActionFactory.createSuppressibleAction(
                  Person.class,
                  p -> {
                    if(p.status == InfectionStatus.INFECTED) {
                      p.getLongAccumulator("currentInfected").add(1);
                      if(p.isInfectious()) {
                        p.getLongAccumulator("currentInfectious").add(1);
                      }
                    }
                  });

  /**
   * Returns true if the person is infectious.
   */
  public boolean isInfectious() {
    return status == InfectionStatus.INFECTED && tInfectious <= getGlobals().tStep;
  }

  /**
   * The person will decide whether to self quarantine the first time they are symptomatic.
   */
  public boolean isFirstTimeSymptomatic() {
    if(!this.isSymptomatic()) {
      return false;
    }
    boolean infectedPreviouslyCovid =
        status == InfectionStatus.INFECTED && symptomOnset < getGlobals().tStep && !this.isAsymptomatic;
    boolean infectedPreviouslyOther =
        isSymptomaticFromOtherIllness
            && becameSymptomaticFromOtherIllnessAtStep < getGlobals().tStep;

    if (infectedPreviouslyCovid || infectedPreviouslyOther) {
      return false;
    }

    boolean infectedByCovidThisStep =
        status == InfectionStatus.INFECTED && symptomOnset == getGlobals().tStep && !this.isAsymptomatic;
    boolean infectedByOtherIllnessThisStep =
        isSymptomaticFromOtherIllness
            && becameSymptomaticFromOtherIllnessAtStep == getGlobals().tStep;

    return infectedByCovidThisStep || infectedByOtherIllnessThisStep;
  }

  private void setInfectedOtherIllness() {
    isSymptomaticFromOtherIllness = true;
    becameSymptomaticFromOtherIllnessAtStep = getGlobals().tStep;
    otherIllnessRecoveryTime =
        getGlobals().tStep
            + getGlobals().tOneDay
            * getPrng()
            .discrete(
                getGlobals().otherIllnessDurationStart,
                getGlobals().otherIllnessDurationEnd)
            .sample();
  }

  public static Action<Person> getInfectedByOtherIllness =
      ActionFactory.createSuppressibleAction(
          Person.class,
          person -> {
            if(person.status == InfectionStatus.SUPPRESSED) {
              return;
            }
            double coin = person.getPrng().uniform(0, 1).sample();
            if (coin < person.getGlobals().otherIllnessInfectionRate) {
              person.setInfectedOtherIllness();
            }
          });

  /**
   * Person gets a message that they were tested and sends their sample with the full truth to
   * {@link CentralAgent}.
   */
  public static Action<Person> getTested =
      ActionFactory.createSuppressibleAction(
          Person.class,
          Person::administerTestIfOrdered);

  private double currentTestingAccuracy() {
    if(getGlobals().testingType == 0) {
      return 1.0;
    }
    else if(getGlobals().testingType == 1) {
      if(this.status == InfectionStatus.INFECTED) {
        return 1.0 - getGlobals().testingFalseNegativePerc;
      }
      else {
        return 1.0 - getGlobals().testingFalsePositivePerc;
      }
    }
    else {
      if (this.status == InfectionStatus.SUSCEPTIBLE) {
        return 1.0 - getGlobals().testingFalsePositivePerc;
      }
      // This may be changed later
      else if (this.status == InfectionStatus.RECOVERED) {
        return 1.0 - getGlobals().testingFalsePositivePerc;
      }
      /**
       * Currently an inverted parabola with a vertex at (h, k)
       * h = symptom onset time + 3 Days =
       *            person#symptomOnset - person#timeInfected + (3 * globals#tOneDay)
       * k = max test accuracy = 1 - globals#testingFalseNegativePerc
       *
       * Equation before h is ACCURACY = a(TIME - h)^2 + k
       * a = -k/h^2 so that ACCURACY = 0 at tStep = person#timeInfected
       *
       * Equation after h is ACCURACY = b(TIME - h)^2 + k
       * b = -k/(d^2 - 2dh + h^2)
       * d = person#illnessDuration - person#timeInfected +
       *                      (globals#daysAfterInfectionToDetect * globals#tOneDay)
       * So that ACCURACY = 0 at tStep = person#illnessDuration + globals#daysAfterInfectionToDetect
       */
      else if (this.status == InfectionStatus.INFECTED) {
        double h = this.symptomOnset - this.timeInfected + (3*getGlobals().tOneDay);
        double k = 1 - getGlobals().testingFalseNegativePerc;
        double time = getGlobals().tStep - this.timeInfected;
        if (time < h) {
          double a = -k / (h * h);
          return a * (time - h) * (time - h) + k;
        } else {
          double d = this.illnessDuration - this.timeInfected +
                  (getGlobals().daysAfterInfectionToDetect * getGlobals().tOneDay);
          double b = -k / (d * d - 2 * d * h + h * h);
          return b * (time - h) * (time - h) + k;
        }
      } else {
        return 1.0;
      }
    }
  }

  private void administerTestIfOrdered() {
    if (!this.hasBeenTested && this.hasMessagesOfType(Messages.TestAdministeredMsg.class)) {
      this.send(
          Messages.InfectionStatusMsg.class,
          msg -> {
            msg.infectedStatus = this.status;
            msg.testAccuracy = this.currentTestingAccuracy();
          })
          .to(this.getGlobals().centralAgentID);

      // Condition to restrict this from requesting multiple tests before results are
      // returned.
      this.hasBeenTested = true;
    }
  }

  /**
   * If it is time to recover or die based on trajector, do so.
   */
  public static Action<Person> recoverOrDieOrStep =
      ActionFactory.createSuppressibleAction(
          Person.class,
          person -> {

            // person has a probability of dying between symptoms onset and end of illness
            // (determined by illnessDuration)
            // For severe cases, the illness duration is much longer, hence, having a higher
            // probability of dying
            if ((person.status == InfectionStatus.INFECTED)
                && (person.isSymptomatic())
                && (person.getGlobals().tStep < person.illnessDuration)) {

              // check to see if the person will die in this step (age dependent)
              if (person.checkDeath()) {
                person.die();
              }

              // check if student is infected (not dead or recovered) and their illness duration is
              // over
            } else if ((person.status == InfectionStatus.INFECTED)
                && person.illnessDuration == person.getGlobals().tStep) {

              // change status to recovered
              person.status = InfectionStatus.RECOVERED;
              person.isSelfIsolatingBecauseOfSymptoms = false;
            }

            if (person.isSymptomaticFromOtherIllness && person.otherIllnessRecoveryTime == person.getGlobals().tStep) {
              person.isSelfIsolatingBecauseOfSymptoms = false;
              person.isSymptomaticFromOtherIllness = false;
            }

            // update accumulators for console
            person.updateAccumulators();
          });

  public static Action<Person> externalInfections =
      ActionFactory.createSuppressibleAction(
          Person.class,
          person -> {
            if (person.status == InfectionStatus.SUSCEPTIBLE) {
              double pExternalInfection = person.getPrng().uniform(0, 1).sample();

              if (pExternalInfection < person.getGlobals().getExternalInfectionRate(person)) {
                person.setInfected();
                person.getLongAccumulator("numExtInfectionsThisStep").add(1);
              }
            }
          });

  public void resetForNextStep() {
    this.hasBeenTested = false;
  }
  public static Action<Person> resetForNextStep =
          ActionFactory.createSuppressibleAction(
                  Person.class,
                  Person::resetForNextStep
          );

  public String getName() {
    return this.getClass().getName() + "_" + personID;
  }

  public void addIsolationPlace(PlaceInfo p) {
    isolationPlaceInfos = ImmutableList.<PlaceInfo>builder().addAll(isolationPlaceInfos).add(p).build();
  }

  public double getTestSelectionMultiplier() {
    return 1.0;
  }

  public static Action<Person> sendTestSelectionMultiplierToCentralAgent =
      ActionFactory.createSuppressibleAction(
          Person.class,
          person -> {
            person
                .send(
                    Messages.TestSelectionMultiplierMessage.class,
                    msg -> msg.setBody(person.getTestSelectionMultiplier()))
                .to(person.getGlobals().centralAgentID);
          });

  /*
   *****************************
   * Contact Interviewing code *
   *****************************
   */
  /**
   * Receives {@link core.Messages.StartInterviewMsg} from {@link CentralAgent#startInterviews}, and sends
   * {@link core.Messages.RequestOccupancyMsg} to {@link PlaceAgent#sendOccupancy}.
   *
   * <p>A more realistic model would store an agent's contacts in the agent itself. However, without a more
   * concrete definition of "contact", it makes more sense to store occupancy histories for each place in its
   * PlaceAgent, then have agents call for that history when needed. This way, only one occupant history is
   * stored per place, rather than many copies in each person. We can have people remove occupants as
   * needed to represent "contacts", or to represent poor memory of interviewees.
   */
  public static Action<Person> requestOccupancyFromPlacesVisited =
      ActionFactory.createSuppressibleAction(Person.class, person -> {
        // Place history should contain up to contact traceback time
        if (person.hasMessagesOfType(Messages.StartInterviewMsg.class)) {
          person.placeHistory.stream().flatMap(ImmutableList::stream).distinct().sorted().forEach(placeId -> {
            person.send(Messages.RequestOccupancyMsg.class).to(placeId);
          });
        }
      });

  /**
   * Receives occupancies from places via {@link core.Messages.OccupancyMsg} and {@link PlaceAgent#sendOccupancy}.
   * Processes occupancies into one contact history {@link core.Messages.InterviewResultsMsg}, to be processed
   * in {@link CentralAgent#processInterviewContacts}
   */
  public static Action<Person> receiveOccupancyHistoriesAndSendToCentralAgent =
      ActionFactory.createSuppressibleAction(Person.class, person -> {
        if (!person.hasMessagesOfType(Messages.OccupancyMsg.class)) {
          return;
        }
        ImmutableList.Builder<ImmutableSet<java.lang.Long>> allContactsBuilder = new ImmutableList.Builder<>();

        List<Messages.OccupancyMsg> occupancyMsgs =
            sortedCopyBySender(person.getMessagesOfType(Messages.OccupancyMsg.class));

        Random r = new Random(person.getPrng().discrete(Integer.MIN_VALUE, Integer.MAX_VALUE).sample());
        for (int i = 0; i < person.getGlobals().contactTracingNumberOfDaysTraceback * person.getGlobals().tOneDay; i++) {
          Set<Long> contactsStepI = new HashSet<>();
          for (Messages.OccupancyMsg msg : occupancyMsgs) {
            if (msg.peoplePresent.size() <= i) {
              continue;
            }

            contactsStepI.addAll(msg.peoplePresent.get(i));
          }

          if (contactsStepI.isEmpty()) {
            continue;
          }

          ArrayList<Long> contactsCopy = new ArrayList<>(contactsStepI);
          Collections.shuffle(contactsCopy, r);
          int numContactsToRecall = (int) Math.rint(person.getGlobals().agentInterviewRecall * contactsCopy.size());
          List<Long> recalledAgents = contactsCopy.subList(0, numContactsToRecall);
          allContactsBuilder.add(ImmutableSet.copyOf(recalledAgents));
        }

        person.send(Messages.InterviewResultsMsg.class, msg -> {
          msg.contacts = allContactsBuilder.build();
        }).to(person.getGlobals().centralAgentID);
      });

  private void doRandomSleepIfTesting() {
    // For testing only! We can add random latency to see if that affects determinism.
    if (getGlobals().addRandomLatency) {
      try {
        Thread.sleep((long) new Random().nextDouble() * 10);
      } catch (InterruptedException e) {
        System.out.println(e.getMessage());
      }
    }
  }

  @Override
  public boolean isSuppressed() {
    return this.suppressed;
  }

  /**
   * Sends {@link Messages.SupressionStatusMessage messages} to {@link CentralAgent#reassignSuppression}.
   */
  public static Action<Person> reportSuppression =
      new Action<>(Person.class,
        p -> {
          p.send(Messages.SupressionStatusMessage.class, m -> m.isSuppressed = p.isSuppressed())
              .to(p.getGlobals().centralAgentID);
        }
      );

  /**
   * Receives {@link Messages.SupressionStatusMessage messages} from {@link CentralAgent#reassignSuppression}
   * and reassings suppression status accordingly.
   */
  public static Action<Person> updateSuppression =
      new Action<>(Person.class,
            p -> {
              List<Messages.SupressionStatusMessage> suppressionStatusMessages =
                  p.getMessagesOfType(Messages.SupressionStatusMessage.class);
              if (suppressionStatusMessages.isEmpty()) {
                return;
              }
              boolean assignToSuppressed = suppressionStatusMessages.get(0).isSuppressed;
              if (assignToSuppressed == p.isSuppressed()) {
                throw new IllegalStateException("Agents should only be receiving suppression reassignment if they " +
                    "need to change.");
              }
              if (p.suppressed && !assignToSuppressed) {
                p.status = InfectionStatus.SUSCEPTIBLE;
              } else if (!p.suppressed && assignToSuppressed) {
                p.status = InfectionStatus.SUPPRESSED;
              }

              p.suppressed = assignToSuppressed;
            }
          );

  // Enum for tracking status of agent
  public enum InfectionStatus {
    SUSCEPTIBLE,
    INFECTED,
    RECOVERED,
    DEAD,
    SUPPRESSED
  }

  protected boolean isAttendingToday(PlaceInfo placeInfo) {
    if (placeInfo.placeOptionality() == PlaceInfo.Optionality.MANDATORY) {
      return true;
    }
    return getPrng().uniform(0, 1).sample() < probGoesToOptionalPlace;
  }

  /**
   * All Person initialization factors that can be present in any implementation. Simulation can
   * provide different instances of these infos for different agent types.
   */
  @AutoValue
  public abstract static class PersonInitializationInfo {
    public abstract Supplier<Double> ageSupplier();

    public abstract Supplier<Double> maskComplianceSupplier();

    public abstract Supplier<Double> quarantineWhenSymptomaticComplianceSupplier();

    public abstract Supplier<InfectionStatus> initialInfectionStatusSupplier();

    public abstract Supplier<Double> symptomsReportComplianceSupplier();

    public abstract Supplier<Double> isolationComplianceSupplier();

    public abstract Supplier<Double> isolateWhenContactNotifiedSupplier();

    public abstract Supplier<Double> compliancePhysicalDistancingSupplier();

    public abstract Supplier<Integer> contactRateSupplier();

    public abstract Supplier<Double> probGoesToOptionalPlaceSupplier();

    public abstract Supplier<MaskType> maskTypeSupplier();

    public abstract Supplier<RaceEthnicity> raceEthnicitySupplier();

    public abstract Supplier<Double> probHostsAdditionalPlaceSupplier();

    public abstract Supplier<Double> probAttendsAdditionalPlaceSupplier();

    public abstract Function<Person, Gender> genderFunction();

    // Depends on RaceEthnicity being set
    public abstract Function<Person, SES> sesFunction();

    // Depends on RaceEthnicity AND SES being set
    public abstract Function<Person, Boolean> usesPublicTransitFunction();

    // Depends on RaceEthnicity AND SES being set
    public abstract Function<Person, Boolean> doesExternalJobFunction();

    // Depends on RaceEthnicity AND SES being set
    public abstract Function<Person, Boolean> doesExternalActivityFunction();

    // Depends on RaceEthnicity AND SES being set
    public abstract Function<Person, Boolean> livesInMultigenerationalHouseholdFunction();

    public abstract Supplier<Boolean> suppressionSupplier();

    public abstract Supplier<Boolean> isVaccinated();

    public static Builder builderSetWithGlobalDefaults(
        final Globals globals, final SeededRandom random) {
      return new AutoValue_Person_PersonInitializationInfo.Builder()
          .ageSupplier(uniform(globals.defaultAgentAgeStart, globals.defaultAgentAgeEnd, random))
          .maskComplianceSupplier(
              uniform(
                  globals.defaultAgentMaskComplianceStart,
                  globals.defaultAgentMaskComplianceEnd,
                  random))
          .quarantineWhenSymptomaticComplianceSupplier(
              uniform(
                  globals.defaultAgentQuarantineWhenSymptomaticComplianceWhenStart,
                  globals.defaultAgentQuarantineWhenSymptomaticComplianceEnd,
                  random))
          .initialInfectionStatusSupplier(
              () -> {
                if (random.uniform(0, 1).sample() < globals.percInitiallyRecovered) {
                  return InfectionStatus.RECOVERED;
                } else if (random.uniform(0, 1).sample()
                    < (globals.percInitiallyInfected / (1 - globals.percInitiallyRecovered))) {
                  return InfectionStatus.INFECTED;
                }
                return InfectionStatus.SUSCEPTIBLE;
              })
          .symptomsReportComplianceSupplier(
              uniform(
                  globals.defaultCompSymptomsReportStart,
                  globals.defaultCompSymptomsReportEnd,
                  random))
          .isolationComplianceSupplier(
              uniform(
                  globals.defaultAgentIsolationComplianceStart,
                  globals.defaultAgentIsolationComplianceEnd,
                  random))
          .isolateWhenContactNotifiedSupplier(
              uniform(
                  globals.defaultAgentComplianceIsolateWhenContactNotifiedStart,
                  globals.defaultAgentComplianceIsolateWhenContactNotifiedEnd,
                  random))
          .compliancePhysicalDistancingSupplier(
              uniform(
                  globals.defaultAgentCompliancePhysicalDistancingStart,
                  globals.defaultAgentCompliancePhysicalDistancingtEnd,
                  random))
          .contactRateSupplier(
              () ->
                  random
                      .discrete(
                          globals.agentContactRateRangeStart, globals.agentContactRateRangeEnd)
                      .sample())
          .probGoesToOptionalPlaceSupplier(() -> globals.defaultAgentProbGoesToOptionalPlace)
          .probHostsAdditionalPlaceSupplier(() -> 0.0)
          .probAttendsAdditionalPlaceSupplier(() -> 0.0)
          .maskTypeSupplier(
              distribution(
                  new double[]{
                      globals.percHomemadeClothMasks, globals.percSurgicalMasks, globals.percN95Masks
                  },
                  new MaskType[]{MaskType.HOMEMADE_CLOTH, MaskType.SURGICAL, MaskType.N95},
                  random))
          .raceEthnicitySupplier(() -> RaceEthnicity.UNKNOWN)
          .sesFunction(p -> SES.UNKNOWN)
          .doesExternalActivityFunction(person -> false)
          .doesExternalJobFunction(person -> false)
          .livesInMultigenerationalHouseholdFunction(person -> false)
          .usesPublicTransitFunction(person -> false)
          .genderFunction(person -> Gender.UNKNOWN)
          .suppressionSupplier(() -> false)
          .isVaccinated(coinFlip(globals.percInitiallyVaccinated, random));
    }

    public static Builder dummyBuilder() {
      return new AutoValue_Person_PersonInitializationInfo.Builder()
          .ageSupplier(() -> 0.0)
          .maskComplianceSupplier(() -> 0.0)
          .quarantineWhenSymptomaticComplianceSupplier(() -> 0.0)
          .initialInfectionStatusSupplier(() -> InfectionStatus.SUSCEPTIBLE)
          .symptomsReportComplianceSupplier(() -> 0.0)
          .isolationComplianceSupplier(() -> 0.0)
          .isolateWhenContactNotifiedSupplier(() -> 0.0)
          .compliancePhysicalDistancingSupplier(() -> 0.0)
          .contactRateSupplier(() -> 0)
          .probGoesToOptionalPlaceSupplier(() -> 0.0)
          .probHostsAdditionalPlaceSupplier(() -> 0.0)
          .probAttendsAdditionalPlaceSupplier(() -> 0.0)
          .maskTypeSupplier(() -> MaskType.NONE)
          .raceEthnicitySupplier(() -> RaceEthnicity.UNKNOWN)
          .sesFunction(p -> SES.UNKNOWN)
          .doesExternalActivityFunction(person -> false)
          .doesExternalJobFunction(person -> false)
          .livesInMultigenerationalHouseholdFunction(person -> false)
          .usesPublicTransitFunction(person -> false)
          .genderFunction(person -> Gender.UNKNOWN)
          .suppressionSupplier(() -> false)
          .isVaccinated(() -> false);
    }

    // Quick helper method for slightly cleaner code
    public static Supplier<Double> uniform(
        final double start, final double end, final SeededRandom r) {
      if (start == end) {
        return () -> end;
      }
      return () -> r.uniform(start, end).sample();
    }

    public static Supplier<Double> truncNormal(
        final double start,
        final double end,
        final double mean,
        final double sd,
        final SeededRandom r) {
      if (start == end) {
        return () -> end;
      }
      return () -> {
        double d = 0.0;
        do {
          d = r.normal(mean, sd).sample();
        } while (d < start || d >= end);
        return d;
      };
    }

    public static <T> Supplier<T> distribution(
        final double[] distribution, final T[] values, final SeededRandom seededRandom) {

      return () -> {
        double draw = seededRandom.uniform(0, 1).sample();

        double cumulativeSum = 1 - (Arrays.stream(distribution).sum());
        if (cumulativeSum >= draw) {
          return values[0];
        }

        for (int i = 0; i < distribution.length; i++) {
          cumulativeSum += distribution[i];
          if (cumulativeSum >= draw) {
            return values[i];
          }
        }
        throw new IllegalStateException("Given invalid distribution: " + distribution);
      };
    }

    public static Supplier<Boolean> coinFlip(final double trueChance, final SeededRandom seededRandom) {
      return () -> {
        double coin = seededRandom.uniform(0, 1).sample();
        return coin <= trueChance;
      };
    }

    public static Builder builder() {
      return new AutoValue_Person_PersonInitializationInfo.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
      public abstract Builder ageSupplier(Supplier<Double> ageSupplier);

      public abstract Builder maskComplianceSupplier(Supplier<Double> maskComplianceSupplier);

      public abstract Builder quarantineWhenSymptomaticComplianceSupplier(
          Supplier<Double> quarantineComplianceSupplier);

      public abstract Builder initialInfectionStatusSupplier(
          Supplier<InfectionStatus> initialInfectionStatusSupplier);

      public abstract Builder symptomsReportComplianceSupplier(
          Supplier<Double> symptomsReportComplianceSupplier);

      public abstract Builder isolationComplianceSupplier(
          Supplier<Double> isolationComplianceSupplier);

      public abstract Builder isolateWhenContactNotifiedSupplier(
          Supplier<Double> isolateWhenContactNotifiedSupplier);

      public abstract Builder compliancePhysicalDistancingSupplier(
          Supplier<Double> compliancePhysicalDistancingSupplier);

      public abstract Builder contactRateSupplier(Supplier<Integer> contactRateSupplier);

      public abstract Builder probGoesToOptionalPlaceSupplier(
          Supplier<Double> probGoesToOptionalPlaceSupplier);

      public abstract Builder maskTypeSupplier(Supplier<MaskType> maskTypeSupplier);

      public abstract Builder raceEthnicitySupplier(Supplier<RaceEthnicity> raceEthnicitySupplier);

      public abstract Builder sesFunction(Function<Person, SES> sesFunction);

      public abstract Builder usesPublicTransitFunction(Function<Person, Boolean> usesPublicTransitFunction);

      public abstract Builder doesExternalJobFunction(Function<Person, Boolean> doesExternalJobFunction);

      public abstract Builder doesExternalActivityFunction(Function<Person, Boolean> doesExternalActivityFunction);

      public abstract Builder livesInMultigenerationalHouseholdFunction(Function<Person, Boolean> livesInMultigenerationalHouseholdFunction);

      public abstract Builder genderFunction(Function<Person, Gender> genderFunction);

      public abstract Builder probHostsAdditionalPlaceSupplier(
          Supplier<Double> probHostsAdditionalPlaceSupplier);

      public abstract Builder probAttendsAdditionalPlaceSupplier(
          Supplier<Double> probAttendsAdditionalPlaceSupplier);

      public abstract Builder suppressionSupplier(
              Supplier<Boolean> suppressionSupplier);

      public abstract Builder isVaccinated(Supplier<Boolean> isVaccinated);

      public abstract PersonInitializationInfo build();
    }
  }

  public enum RaceEthnicity {
    UNKNOWN, BLACK, NON_BLACK_HISPANIC, OTHER
  }

  // Socioeconomic status
  public enum SES {
    UNKNOWN, LOW, MODERATELY_LOW, MODERATE_OR_HIGH
  }

  public enum Gender {
    UNKNOWN, MALE, FEMALE, OTHER
  }

  /**
   * Minimally relevant info about a person who occupied a space, in order to contribute to
   * infection spread calculation.
   */
  @AutoValue
  public abstract static class PersonTransmissibilityInfo {
    public abstract InfectionStatus status();

    public abstract boolean isInfectious();

    public abstract boolean isSymptomatic();

    public abstract MaskType wearsMask();

    public abstract double physicalDistCompliance();

    public abstract int contactRate();

    public abstract double inTransmissionImmunity();
    
    public abstract double outTransmissionImmunity();

    public static PersonTransmissibilityInfo create(
        InfectionStatus status,
        boolean isInfectious,
        boolean isSymptomatic,
        MaskType wearsMask,
        double physicalDistCompliance,
        int contactRate,
        double inTransmissionImmunity,
        double outTransmissionImmunity) {
      return new AutoValue_Person_PersonTransmissibilityInfo(
          status, isInfectious, isSymptomatic, wearsMask, physicalDistCompliance, contactRate, 
              inTransmissionImmunity, outTransmissionImmunity);
    }

    public static PersonTransmissibilityInfo dummy() {
      return create(InfectionStatus.SUSCEPTIBLE, false, false, MaskType.NONE, 0, 0, 0, 0);
    }

    public static PersonTransmissibilityInfo dummyInfected() {
      return create(InfectionStatus.INFECTED, true, true, MaskType.NONE, 0, 0, 0, 0);
    }

    public static PersonTransmissibilityInfo create(Person person, boolean willWearMask) {
      return create(
          person.status,
          person.isInfectious(),
          person.isSymptomatic(),
          willWearMask ? person.maskType : MaskType.NONE,
          person.compliancePhysicalDistancing,
          person.contactRate,
          (person.isVaccinated) ? person.getGlobals().vaccineEfficacy : 0,
          (person.isVaccinated) ? person.getGlobals().vaccineOutEfficacy : 0
      );
    }

    public static PersonTransmissibilityInfo create(Person person, boolean willWearMask, double reduction) {
      return create(
              person.status,
              person.isInfectious(),
              person.isSymptomatic(),
              willWearMask ? person.maskType : MaskType.NONE,
              person.compliancePhysicalDistancing * reduction,
              person.contactRate,
              (person.isVaccinated) ? person.getGlobals().vaccineEfficacy : 0,
              (person.isVaccinated) ? person.getGlobals().vaccineOutEfficacy : 0
      );
    }
  }

  // TODO Check if these mask type names correctly align with data, and add reference
  public enum MaskType {
    NONE,
    HOMEMADE_CLOTH,
    SURGICAL,
    N95
  }

  /**
   * Secondary initialization information after {@link CentralAgent} runs secondary init.
   */
  // TODO Make this name better. DailySchedule alone no longer seems accurate
  @AutoValue
  public abstract static class DailySchedule {
    /**
     * The places the person will go to at different tSteps.
     */
    public abstract ImmutableMap<Integer, List<PlaceInfo>> placesAtStepMap();

    /**
     * The places the person goes to when isolating.
     */
    public abstract ImmutableList<PlaceInfo> isolationPlaces();

    /**
     * A {@link Consumer} to consume the person after secondary init. This may set some additional
     * values which were only available after the initial initialization.
     */
    public abstract Consumer<Person> secondaryInitialization();

    public static DailySchedule create(
        ImmutableMap<Integer, List<PlaceInfo>> placesAtStepMap,
        ImmutableList<PlaceInfo> isolationPlaceInfos,
        Consumer<Person> secondaryInitialization) {
      return new AutoValue_Person_DailySchedule(
          placesAtStepMap, isolationPlaceInfos, secondaryInitialization);
    }

    public static DailySchedule create(
        ImmutableMap<Integer, List<PlaceInfo>> placesAtStepMap, ImmutableList<PlaceInfo> isolationPlaceInfos) {
      return new AutoValue_Person_DailySchedule(placesAtStepMap, isolationPlaceInfos, p -> {
      });
    }

    public static DailySchedule dummy() {
      return create(ImmutableMap.of(), ImmutableList.of(), person -> {
      });
    }
  }
}
