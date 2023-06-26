package core;

import simudyne.core.abm.GlobalState;
import simudyne.core.annotations.Input;
import simudyne.core.rng.SeededRandom;
import tau.Faculty;
import tau.Staff;
import tau.Student;
import tau.UniversityConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.DoubleStream;

/**
 * Globals contains global final constants like inputs, helper methods, and overrideable helper
 * methods that can be overriden to change simulation dynamics.
 *
 * <p>Implementation specific constants, inputs, or helper methods can be added to the bottom of
 * this class.
 */
public final class Globals extends GlobalState {

  /**
   * Core model globals.
   *
   * <p>DO NO CHANGE IN NON-CORE MODEL BRANCHES.
   */
  Modules modules = DefaultModulesImpl.getInstance();

  public void setModules(Modules modules) {
    this.modules = modules;
  }

  /**
   * Mask wearing: If {@link Globals#mandateMask} is false, no agents will wear masks.
   *
   * <p>If {@link Globals#mandateMask} is true, agents are given a mask wearing compliance. Each
   * agent will choose whether or not to wear a mask based on this compliance each time they go to a
   * place.
   *
   * <p>If mandating masks, each agent is also picks a mask type, and that will be the mask type
   * that they will use for the duration of the simulation whenever they choose to wear a mask. The
   * mask type distribution is given by parameters {@link Globals#percHomemadeClothMasks}, {@link
   * Globals#percSurgicalMasks}, {@link Globals#percN95Masks}. Mask type affects the reduction in
   * transmissibility.
   */

  // NPI
  @Input(name = "NPI: Mandate Mask")
  public boolean mandateMask = true;

  @Input(name = "Percentage of homemade cloth masks")
  public double percHomemadeClothMasks = 0.5;

  @Input(name = "Percentage of surgical masks")
  public double percSurgicalMasks = 0.4;

  @Input(name = "Percentage of N95s")
  public double percN95Masks = 0.1;

  @Input(name = "Testing Capacity (tests/day)")
  public int testsPerDay = 50; // This should be changed to increase over time

  public double defaultCompSymptomsReportStart = 0.0;

  public double defaultCompSymptomsReportEnd = 1.0;

  @Input(name = "Number of days to isolate when contact notified")
  public int contactNotifiedNumberOfDaysToIsolate = 14;

  @Input(name = "Contact tracing number of days traceback")
  public int contactTracingNumberOfDaysTraceback = 14;

  @Input(name = "Contact Tracing Protocol: 1 = BEST_PRACTICE, 2 = CONSERVATIVE, 3 = TEST_ONLY")
  public int contactTracingProtocol = 1;

  @Input(name = "Tests available for tracing")
  public boolean testingAvailableForTracing = true;

  /*
    "BEST_PRACTICE" and "SYMPTOMS_ONLY" are based on:
    Centers for Disease Control and Prevention. (2020). "Contact tracing for COVID-19."
    https://www.cdc.gov/coronavirus/2019-ncov/php/contact-tracing/contact-tracing-plan/contact-tracing.html

    "CONSERVATIVE" is loosely based on:
    European Centre for Disease Prevention and Control (April 2020)
    https://www.ecdc.europa.eu/en/covid-19-contact-tracing-public-health-management

    "TEST_ONLY" is loosely based on the "Catch and Release" concept in:
    Firth, Josh A., et al, "Using a real-world network to model localized COVID-19 control strategies."
    Nature Medicine (August 2020): 1-7.
   */
  public enum ContactTracingProtocols {
    UNKNOWN,
    BEST_PRACTICE,
    CONSERVATIVE,
    TEST_ONLY
  }

  // Planned last step of simulation, where outputs will be written to output CSV.
  // This input can be ignored if we do not care about CSV output.
  @Input
  public int lastStep = 100;

  @Input
  public String csvOutputFilename = "csvOutput.csv";

  // TESTING VARIABLE. These should never be set to true outside of tests, and they can be ignored
  // from output.
  @Input
  public boolean addRandomLatency = false;

  public long createConnectionOfAgents(List<Person> allPeople) {
    return modules.createConnectionOfAgents(allPeople, this);
  }

  public Map<Long, Person.DailySchedule> createPlacesAndPersonDailySchedules() {
    return modules.createPlacesAndPersonDailySchedules(this);
  }

  public InfectionTrajectoryDistribution getInfectionTrajectoryDistribution(Person person) {
    return modules.getInfectionTrajectoryDistribution(person, this);
  }

  public double getProbabilityOfDeathGivenSevereIllness(Person person) {
    if (person.age >= 0 && person.age < 10) {
      return pAgeDeath[0];
    } else if (person.age >= 10 && person.age < 20) {
      return pAgeDeath[1];
    } else if (person.age >= 20 && person.age < 30) {
      return pAgeDeath[2];
    } else if (person.age >= 30 && person.age < 40) {
      return pAgeDeath[3];
    } else if (person.age >= 40 && person.age < 50) {
      return pAgeDeath[4];
    } else if (person.age >= 50 && person.age < 60) {
      return pAgeDeath[5];
    } else if (person.age >= 60 && person.age < 70) {
      return pAgeDeath[6];
    } else if (person.age >= 70 && person.age < 80) {
      return pAgeDeath[7];
    } else if (person.age >= 80) {
      return pAgeDeath[8];
    }
    throw new IllegalArgumentException("Invlaid person age given.");
  }

  // Probability of death according to age
  public double[] pAgeDeath = {
      0.000954, 0.00352, 0.00296, 0.00348, 0.00711, 0.0206, 0.0579, 0.127, 0.233
  };
  // pAgeDeath[0]: Age 0 - 9, p = 0.000954
  // pAgeDeath[1]: Age 10 - 19, p = 0.00352
  // pAgeDeath[2]: Age 20 - 29, p = 0.00296
  // pAgeDeath[3]: Age 30 - 39, p = 0.00348
  // pAgeDeath[4]: Age 40 - 49, p = 0.00711
  // pAgeDeath[5]: Age 50 - 59, p = 0.0206
  // pAgeDeath[6]: Age 60 - 69, p = 0.0579
  // pAgeDeath[7]: Age 70 - 79, p = 0.127
  // pAgeDeath[8]: Age > 80, p = 0.233

  public int tStep = 0;

  // Central Agent ID
  public long centralAgentID;

  public long outputWriterAgentID;

  // Infection Statistics
  public int numSusceptible = 0;
  public int numInfected = 0;
  public int numQuarantineInfected = 0;
  public int numQuarantineSusceptible = 0;
  public int numRecovered = 0;
  public int numDead = 0;
  public int numDetectedCases = 0;
  public int testPositivity = 0;

  public int totalPositiveTests = 0;
  public int totalTestsAdministered = 0;

  public double getInfectionRate(int placeType) {
    // All place types have same base infectivity for now
    return baseInfectivity;
  }

  public double getExternalInfectionRate(Person person) {
    return modules.getExternalInfectionRate(person, this);
  }

  public void resetInfectionStatistics() {
    numSusceptible = 0;
    numInfected = 0;
    numQuarantineInfected = 0;
    numQuarantineSusceptible = 0;
    numRecovered = 0;
    numDead = 0;
    numDetectedCases = 0;
    testPositivity = 0;
    totalPositiveTests = 0;
    totalTestsAdministered = 0;
  }

  // Parameters for visuals
  @Input(name = "Show dynamic agent network")
  public boolean showDynamicNetworkAsLinks = false;

  public Set<Long> getAgentsToTest(
          Set<Long> symptomaticAgentsToday,
          Map<Long, Double> testSelectionMultipliers,
          SeededRandom random,
          long numTestsToRun) {
    return modules.getAgentsToTest(symptomaticAgentsToday, testSelectionMultipliers, random, numTestsToRun);
  }

  @Input(name = "Perc initially vaccinated")
  public double percInitiallyVaccinated = 0.0;

  @Input(name = "Vaccine effectiveness")
  public double vaccineEfficacy = 0.95;

  // TODO Update this if more info is released
  // Per this study, being vaccinated reduces transmission by 40-50%:
  // https://depts.washington.edu/pandemicalliance/2021/04/29/impact-of-vaccination-on-household-transmission-of-sars-cov-2-in-england/
  public double vaccineOutEfficacy = 0.45;

  /** END CORE MODEL GLOBALS. */

  /**
   * core.Globals to override in extensions if appropriate.
   */
  @Input(name = "Steps per day")
  public int tOneDay = 1; // How many time steps represent one day?

  // When true, outputs each transmission to transmissions.csv
  @Input(name = "Output transmissions to transmissions.csv")
  public boolean outputTransmissions = false;

  /**
   * All of the range inputs, marked by having one *Start and one *End, each define a uniform
   * distribution for agents. Each agent draws from the uniform distribution at initialization and
   * will have that value for the corresponding agent parameter for the duration of the simulation.
   */
  public double defaultAgentAgeStart = 0.0;

  public double defaultAgentAgeEnd = 100.0;

  @Input(name = "student age start")
  public double studentAgentAgeStart = 17.0;

  @Input(name = "student age end")
  public double studentAgentAgeEnd = 23.0;

  @Input(name = "faculty/staff age start")
  public double facultyStaffAgentAgeStart = 18.0;

  @Input(name = "faculty/staff age end")
  public double facultyStaffAgentAgeEnd = 100.0;

  @Input(name = "faculty/staff age mean")
  public double facultyStaffAgentAgeMean = 45.0;

  @Input(name = "faculty/staff age stand. deviation")
  public double facultyStaffAgentAgeSD = 20.0;

  // Agent mask compliance: agent draws a value that will be their liklihood to wear a mask.
  public double defaultAgentMaskComplianceStart = 0.0;

  public double defaultAgentMaskComplianceEnd = 1.0;

  @Input(name = "student mask compliance start")
  public double studentAgentMaskComplianceStart = 0.0;

  @Input(name = "student mask compliance end")
  public double studentAgentMaskComplianceEnd = 1.0;

  @Input(name = "faculty/staff mask compliance start")
  public double facultyStaffAgentMaskComplianceStart = 0.0;

  @Input(name = "faculty/staff mask compliance end")
  public double facultyStaffAgentMaskComplianceEnd = 1.0;

  // Quarantine compliance: likelihood to quarantine when symptomatic.
  public double defaultAgentQuarantineWhenSymptomaticComplianceWhenStart = 0;

  public double defaultAgentQuarantineWhenSymptomaticComplianceEnd = 1.0;

  @Input(name = "student likelihood to quarantine when symptomatic start")
  public double studentAgentQuarantineWhenSymptomaticComplianceStart = 0;

  @Input(name = "student likelihood to quarantine when symptomatic end")
  public double studentAgentQuarantineWhenSymptomaticComplianceEnd = 1.0;

  @Input(name = "faculty/staff likelihood to quarantine when symptomatic start")
  public double facultyStaffAgentQuarantineWhenSymptomaticComplianceStart = 0;

  @Input(name = "faculty/staff likelihood to quarantine when symptomatic end")
  public double facultyStaffAgentQuarantineWhenSymptomaticComplianceEnd = 1.0;

  @Input(name = "Percentage of agents initially infected")
  public double percInitiallyInfected = 0.05;

  @Input(name = "Percentage of agents initially recovered")
  public double percInitiallyRecovered = 0.1;

  @Input(name = "student likelihood to report symptoms start")
  public double studentAgentReportSymptomsComplianceStart = 0.0;

  @Input(name = "student likelihood to report symptoms end")
  public double studentAgentReportSymptomsComplianceEnd = 1.0;

  @Input(name = "faculty/staff likelihood to report symptoms start")
  public double facultyStaffAgentReportSymptomsComplianceStart = 0.0;

  @Input(name = "faculty/staff likelihood to report symptoms end")
  public double facultyStaffAgentReportSymptomsComplianceEnd = 1.0;

  // Isolation compliance: Likelihood to successfully isolate. Each day the agent is supposed to
  // isolate, the agent
  // will decide whether they will actually isolate or go about their day as normal.
  public double defaultAgentIsolationComplianceStart = 0.0;

  public double defaultAgentIsolationComplianceEnd = 1.0;

  @Input(name = "student likelihood to isolate when they \"should\" start")
  public double studentAgentIsolationComplianceStart = 0.0;

  @Input(name = "student likelihood to isolate when they \"should\" end")
  public double studentAgentIsolationComplianceEnd = 1.0;

  @Input(name = "faculty/staff likelihood to isolate when they \"should\" start")
  public double facultyStaffAgentIsolationComplianceStart = 0.0;

  @Input(name = "faculty/staff likelihood to isolate when they \"should\" end")
  public double facultyStaffAgentIsolationComplianceEnd = 1.0;

  // Likelihood of an agent to isolate when notified they have made contact with an infectious or
  // symptomatic person.
  public double defaultAgentComplianceIsolateWhenContactNotifiedStart = 0.0;

  public double defaultAgentComplianceIsolateWhenContactNotifiedEnd = 1.0;

  @Input(name = "student likelihood to isolate when contact notified start")
  public double studentAgentComplianceIsolateWhenContactNotifiedStart = 0.0;

  @Input(name = "student likelihood to isolate when contact notified end")
  public double studentAgentComplianceIsolateWhenContactNotifiedEnd = 1.0;

  @Input(name = "faculty/staff likelihood to isolate when contact notified start")
  public double facultyStaffAgentComplianceIsolateWhenContactNotifiedStart = 0.0;

  @Input(name = "faculty/staff likelihood to isolate when contact notified end")
  public double facultyStaffAgentComplianceIsolateWhenContactNotifiedEnd = 1.0;

  // Compliance to physically distance
  public double defaultAgentCompliancePhysicalDistancingStart = 0.5;

  public double defaultAgentCompliancePhysicalDistancingtEnd = 1.0;

  @Input(name = "student compliance to physical distance start")
  public double studentAgentCompliancePhysicalDistancingStart = 0.5;

  @Input(name = "student compliance to physical distance end")
  public double studentAgentCompliancePhysicalDistancingtEnd = 1.0;

  @Input(name = "faculty/staff compliance to physical distance start")
  public double facultyStaffAgentCompliancePhysicalDistancingStart = 0.5;

  @Input(name = "faculty/staff compliance to physical distance end")
  public double facultyStaffAgentCompliancePhysicalDistancingtEnd = 1.0;

  // Probability agent goes to an optional place
  public double defaultAgentProbGoesToOptionalPlace = 1.0;

  @Input(name = "student probability goes to optional place start")
  public double studentAgentProbGoesToOptionalPlaceStart = 0.01;

  @Input(name = "student probability goes to optional place end")
  public double studentAgentProbGoesToOptionalPlaceEnd = 1.0;

  @Input(name = "faculty/staff probability goes to optional place start")
  public double facultyStaffAgentProbGoesToOptionalPlaceStart = 0.5;

  @Input(name = "faculty/staff probability goes to optional place end")
  public double facultyStaffAgentProbGoesToOptionalPlaceEnd = 1.0;

  // Number of contacts an agent will make at each place they go to.
  @Input(name = "agent contact rate dist. range start")
  public int agentContactRateRangeStart = 3;

  @Input(name = "agent contact rate dist. range end")
  public int agentContactRateRangeEnd = 6;

  @Input(name = "Percent cases asymptomatic")
  public double percAsymptomatic = 0.5;

  @Input(name = "Percent cases severe")
  public double percSevere = 0.05;

  @Input(name = "Base external infection rate for people living off campus")
  public double baseOffCampusExternalInfectionRate = 0.0002; // Arbitrary set to 400 / million / 2

  @Input(name = "Base external infection rate for people living on campus")
  public double baseOnCampusExternalInfectionRate = 0.0002; // Arbitrary set to 400 / million / 2

  @Input(name = "Marginal public transit infection rate")
  public double marginalPublicTransitExternalInfectionRate = 0.0001; // Arbitrary set to 200 / million / 2

  @Input(name = "Place flat infection rate")
  public double placeTypeFlatInfectionRate = 0.0002; // Arbitrary set to 400 / million / 2

  @Input(name = "Likelihood of developing other illness symptoms")
  public double otherIllnessInfectionRate = 0.0002;

  // Flu usually lasts 3-7 days
  @Input(name = "Other illness duration start (days)")
  public int otherIllnessDurationStart = 3;

  @Input(name = "Other illness duration end (days)")
  public int otherIllnessDurationEnd = 7;

  public double overallExternalInfectionRateFromData = 0.0;

  @Input(name = "Student likelihood to throw a party start")
  public double studentAgentThrowsPartyStart = 0.0;
  @Input(name = "Student likelihood to throw a party end")
  public double studentAgentThrowsPartyEnd = 0.01;
  @Input(name = "Student likelihood to attend a party start")
  public double studentAgentAttendsPartyStart = 0.0;
  @Input(name = "Student likelihood to attend a party end")
  public double studentAgentAttendsPartyEnd = 0.05;

  @Input(name = "Faculty/Staff likelihood to throw a party start")
  public double facultyStaffAgentThrowsPartyStart = 0.0;
  @Input(name = "Faculty/Staff likelihood to throw a party end")
  public double facultyStaffAgentThrowsPartyEnd = 0.01;
  @Input(name = "Faculty/Staff likelihood to attend a party start")
  public double facultyStaffAgentAttendsPartyStart = 0.0;
  @Input(name = "Faculty/Staff likelihood to attend a party end")
  public double facultyStaffAgentAttendsPartyEnd = 0.02;

  @Input(name = "Agent interview recall")
  public double agentInterviewRecall = 1.0;

  @Input(name = "Percent initial infected agents ordered to quarantine")
  public double percInitialInfectedQuarantineOrder = 1.0;

  /** END OVERRIDE GLOBALS. */

  /**
   * Add branch model specific globals below.
   */
  public static final int DEFAULT_N_AGENTS = 400;

  @Input(name = "Number of agents")
  public int nAgents = DEFAULT_N_AGENTS;

  @Input(name = "Number of active agents")
  public int nActiveAgents = DEFAULT_N_AGENTS;

  /**
   * Which agent types to suppress if nActiveAgents != nAgents
   * 0: All equally
   * 1: Only students
   */
  @Input(name = "Suppress agent type")
  public int suppressAgentType = 0;

  @Input(name = "Include graduate students")
  public boolean includeGradStudents = true;

  @Input(name = "Hybrid classes enabled")
  public boolean hybridClassesEnabled = false;

  @Input(name = "Base infectivity")
  public double baseInfectivity = 0.05;

  public int universityProfile = 4; // Enforce Scalable

  @Input(name = "Close fitness center")
  public boolean closeFitnessCenter = false;

  @Input(name = "Cancel sports events")
  public boolean cancelSportEvents = false;

  @Input(name = "Force all agents to isolate")
  public boolean forceAllAgentsToIsolate = false;

  @Input(name = "Number of staff to student interactions per staff per day")
  public int numStaffToStudenContacts = 5;

  @Input(name = "Test selection likelihood multipler for student-facing staff")
  public double studentFacingStaffTestMultiplier = 1.0;

  @Input(name = "Percentage of off campus students who use public transit")
  public double percOffCampusStudentsWhoUsePublicTransit = 0.5;


  public UniversityProfile getUniversityProfile() {
    return UniversityProfile.values()[universityProfile];
  }

  public UniversityConfiguration getUniversityConfiguration() {
    return UniversityConfiguration.generate(this);
  }

  public enum UniversityProfile {
    SMALL,
    LARGE,
    VERY_SMALL,
    CUSTOM,
    SCALABLE
  }

  public String stateFile = "State/mystate.txt";

  @Input(name = "County")
  public String externalDataCounty = "Middlesex";
  @Input(name = "State")
  public String externalDataState = "Massachusetts";

  public int externalDataSource = 1; // Default to NYT

  @Input(name = "core.Test Delay (Time Steps)")
  public int testDelayTStep = 2;

  /**
   * Testing type:
   * 0 = perfect tests
   * 1 = non-perfect constant tests
   * 2 = non-perfect variable tests
   */
  @Input(name = "core.Test test types")
  public int testingType = 0;

  // TODO put better numbers here
  // 1 - specificity
  @Input(name = "core.Test false positives")
  public double testingFalsePositivePerc = 0.0;

  // 1 - sensitivity
  @Input(name = "core.Test false negatives")
  public double testingFalseNegativePerc = 0.0;

  /**
   * This is a very rough number.
   * The CDC states that COVID-19 can be detected up to three months
   * after diagnosis:
   * https://www.cdc.gov/media/releases/2020/s0814-updated-isolation-guidance.html
   * https://www.cdc.gov/coronavirus/2019-ncov/hcp/duration-isolation.html
   *
   * Other studies show reasonably high positive test rates for several weeks
   * after symptoms end:
   * https://jamanetwork.com/journals/jama/fullarticle/2765837
   * https://www.medrxiv.org/content/10.1101/2020.08.21.20177808v3
   */
  @Input(name = "core.Test days after infection to detect")
  public int daysAfterInfectionToDetect = 25;

  public List<PlaceInfo> uninitializedPlaceInfos = new ArrayList<>();

  public ArrayList<Integer> buildingInfectionsBeginningOfStep = new ArrayList<>();
  public ArrayList<Integer> buildingInfectionsOverStep = new ArrayList<>();
  public ArrayList<Integer> buildingInfectionsBeginningOfDay = new ArrayList<>();
  public ArrayList<Integer> buildingInfectionsOverDay = new ArrayList<>();
  public ArrayList<Double> buildingInfectionRatioStepSum = new ArrayList<>();
  public ArrayList<Double> buildingInfectionRatioDaySum = new ArrayList<>();
  public ArrayList<Integer> buildingExcludeStepsCount = new ArrayList<>();
  public ArrayList<Integer> buildingExcludeDaysCount = new ArrayList<>();
  public ArrayList<Boolean> peopleWentToPlaceTypeStep = new ArrayList<>();
  public ArrayList<Boolean> peopleWentToPlaceTypeDay = new ArrayList<>();

  public ArrayList<Integer> buildingTotalInfections = new ArrayList<>();
  public ArrayList<Integer> buildingTotalPeople = new ArrayList<>();

  public void initBuildingInfectionArrays(int numPlaceType) {
    if (buildingInfectionsBeginningOfStep.size() < numPlaceType) {
      while (buildingInfectionsBeginningOfStep.size() < numPlaceType) {
        buildingInfectionsBeginningOfStep.add(0);
        buildingInfectionsOverStep.add(0);
        buildingInfectionsBeginningOfDay.add(0);
        buildingInfectionsOverDay.add(0);
        buildingInfectionRatioDaySum.add(0.0);
        buildingInfectionRatioStepSum.add(0.0);
        buildingTotalInfections.add(0);
        buildingTotalPeople.add(0);
        buildingExcludeStepsCount.add(0);
        buildingExcludeDaysCount.add(0);
        peopleWentToPlaceTypeStep.add(false);
        peopleWentToPlaceTypeDay.add(false);
      }
    }
  }

  private final double[] studentPFitnessTimesPerWeek = {0.01, 0.06, 0.14, 0.18, 0.14, 0.06, 0.01};
  private final double[] facultyAndStaffPFitnessTimesPerWeek = {0.03, 0.05, 0.07, 0.03, 0.02, 0, 0};

  public Supplier<Integer> getFitnessTimesPerWeek(Person p) {
    if (p instanceof Student) {
      return () -> drawFromDistribution(studentPFitnessTimesPerWeek, p.getPrng());
    } else if (p instanceof Staff || p instanceof Faculty) {
      return () -> drawFromDistribution(facultyAndStaffPFitnessTimesPerWeek, p.getPrng());
    }
    throw new IllegalStateException("Not given a TAU Person.");
  }

  public static Integer drawFromDistribution(double[] distribution, SeededRandom random) {
    double draw = random.uniform(0, 1).sample();

    double cumulativeSum = 1 - (DoubleStream.of(distribution).sum());
    if (cumulativeSum >= draw) {
      return 0;
    }

    for (int i = 0; i < distribution.length; i++) {
      cumulativeSum += distribution[i];
      if (cumulativeSum >= draw) {
        return i;
      }
    }
    throw new IllegalStateException("Given invalid distribution: " + distribution);
  }

  public ArrayList<Integer> personInfectionHist = new ArrayList<>();

  @Input
  public String runID = "";

  @Input(name = "Additional Place Compliance Reduction")
  public double additionalPlaceCompRed = 0.5;

  @Input(name = "Number of agents to randomly infect this step")
  public int numToRandomlyInfect = 0;

  @Input(name = "Number of agents to vaccinate this step")
  public int numToVaccinate = 0;

  @Input(name = "Compliance Modifier")
  public double complianceModifier = 1.0;

}
