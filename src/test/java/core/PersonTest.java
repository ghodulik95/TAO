package core;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Test;
import simudyne.core.abm.testkit.TestKit;
import simudyne.core.abm.testkit.TestResult;

import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static core.Person.InfectionStatus.INFECTED;
import static core.Person.InfectionStatus.RECOVERED;

public class PersonTest {

  private TestPerson testPerson;
  private TestKit<Globals> testKit;
  private CentralAgent centralAgent;
  private PlaceAgent placeAgent;

  @Before
  public void setUp() throws Exception {
    testKit = TestKit.create(Globals.class);
    testPerson = testKit.addAgent(TestPerson.class);
    centralAgent = testKit.addAgent(CentralAgent.class);
    placeAgent = testKit.addAgent(PlaceAgent.class);
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
  }

  @Test
  public void testModifyCompliancesZeroToOne() {
    testPerson.complianceMask = 1.0;
    testPerson.compSymptomsReport = 0.5;

    // test value 0 < x < 1
    testKit.getGlobals().complianceModifier = 0.5;
    testKit.testAction(testPerson, Person.modifyCompliance);
    assertThat(testPerson.complianceMask).isEqualTo(0.5);
    assertThat(testPerson.compSymptomsReport).isEqualTo(0.25);
  }

  @Test
  public void testModifyCompliancesGreaterThanZero() {
    testPerson.complianceMask = 1.0;
    testPerson.compSymptomsReport = 0.5;

    // test value x > 1
    testKit.getGlobals().complianceModifier = 2.0;
    testKit.testAction(testPerson, Person.modifyCompliance);
    assertThat(testPerson.complianceMask).isEqualTo(2.0);
    assertThat(testPerson.compSymptomsReport).isEqualTo(1.0);
  }

  @Test
  public void testModifyCompliancesLessZero() {
    testPerson.complianceMask = 1.0;
    testPerson.compSymptomsReport = 0.5;

    // test value x < 0
    testKit.getGlobals().complianceModifier = -2.0;
    testKit.testAction(testPerson, Person.modifyCompliance);
    assertThat(testPerson.complianceMask).isEqualTo(1.0);
    assertThat(testPerson.compSymptomsReport).isEqualTo(0.5);

  }

  @Test
  public void testModifyCompliancesZero() {
    testPerson.complianceMask = 1.0;
    testPerson.compSymptomsReport = 0.5;

    // test value x = 0
    testKit.getGlobals().complianceModifier = 0.0;
    testKit.testAction(testPerson, Person.modifyCompliance);
    assertThat(testPerson.complianceMask).isEqualTo(0.0);
    assertThat(testPerson.compSymptomsReport).isEqualTo(0.0);
  }

  @Test
  public void testReportForVaccine() {
    testPerson.isVaccinated = true;
    TestResult result = testKit.testAction(testPerson, Person.reportForVaccine);

    assertThat(result.getMessagesOfType(Messages.ReportForVaccineMsg.class).size()).isEqualTo(0);

    testPerson.isVaccinated = false;
    result = testKit.testAction(testPerson, Person.reportForVaccine);

    assertThat(result.getMessagesOfType(Messages.ReportForVaccineMsg.class).size()).isEqualTo(1);
  }

  @Test
  public void testGetVaccinated() {
    testPerson.isVaccinated = false;
    testKit.send(Messages.VaccineAdministeredMsg.class).to(testPerson);

    testKit.testAction(testPerson, Person.getVaccinated);

    assertThat(testPerson.isVaccinated).isTrue();
  }

  @Test
  public void testInit_ValuesProperlySetFromProvidedInitializationInfo() {
    testKit.getGlobals().mandateMask = true;
    testPerson.setInitializationBuilder(
        Person.PersonInitializationInfo.builderSetWithGlobalDefaults(
            testKit.getGlobals(), testPerson.getPrng())
            .ageSupplier(() -> 1.0)
            .maskComplianceSupplier(() -> 0.2)
            .quarantineWhenSymptomaticComplianceSupplier(() -> 0.3)
            .initialInfectionStatusSupplier(() -> RECOVERED)
            .symptomsReportComplianceSupplier(() -> 0.4)
            .isolationComplianceSupplier(() -> 0.5)
            .isolateWhenContactNotifiedSupplier(() -> 0.6)
            .contactRateSupplier(() -> 7)
            .maskTypeSupplier(() -> Person.MaskType.N95)
            .isVaccinated(() -> true));

    testPerson.init();

    assertThat(testPerson.age).isEqualTo(1.0);
    assertThat(testPerson.complianceMask).isEqualTo(0.2);
    assertThat(testPerson.compQuarantineWhenSymptomatic).isEqualTo(0.3);
    assertThat(testPerson.status).isEqualTo(RECOVERED);
    assertThat(testPerson.compSymptomsReport).isEqualTo(0.4);
    assertThat(testPerson.complianceIsolating).isEqualTo(0.5);
    assertThat(testPerson.complianceIsolateWhenContactNotified).isEqualTo(0.6);
    assertThat(testPerson.contactRate).isEqualTo(7);
    assertThat(testPerson.maskType).isEqualTo(Person.MaskType.N95);
    assertThat(testPerson.isVaccinated).isTrue();
  }

  @Test
  public void testInit_NoMaskMandateMakesMaskComplianceZero() {
    testKit.getGlobals().mandateMask = false;
    testPerson.setInitializationBuilder(
        Person.PersonInitializationInfo.builderSetWithGlobalDefaults(
            testKit.getGlobals(), testPerson.getPrng())
            .ageSupplier(() -> 1.0)
            .maskComplianceSupplier(() -> 0.2)
            .quarantineWhenSymptomaticComplianceSupplier(() -> 0.3)
            .initialInfectionStatusSupplier(() -> RECOVERED)
            .symptomsReportComplianceSupplier(() -> 0.4)
            .isolationComplianceSupplier(() -> 0.5)
            .maskTypeSupplier(() -> Person.MaskType.N95));

    testPerson.init();

    assertThat(testPerson.complianceMask).isEqualTo(0.0);
  }

  @Test
  public void testInit_ValuesProperlySetFromGlobals() {
    // Arrange: set up the data to pass to the test.
    testKit.getGlobals().mandateMask = true;

    // Set global inputs to non-overlapping ranges
    testKit.getGlobals().defaultAgentAgeStart = 0.0;
    testKit.getGlobals().defaultAgentAgeEnd = 0.0001;

    testKit.getGlobals().defaultAgentMaskComplianceStart = 0.0001;
    testKit.getGlobals().defaultAgentMaskComplianceEnd = 0.0002;

    testKit.getGlobals().defaultAgentQuarantineWhenSymptomaticComplianceWhenStart = 0.0002;
    testKit.getGlobals().defaultAgentQuarantineWhenSymptomaticComplianceEnd = 0.0003;

    testKit.getGlobals().defaultCompSymptomsReportStart = 0.0003;
    testKit.getGlobals().defaultCompSymptomsReportEnd = 0.0004;

    testKit.getGlobals().defaultAgentIsolationComplianceStart = 0.0004;
    testKit.getGlobals().defaultAgentIsolationComplianceEnd = 0.0005;

    testKit.getGlobals().defaultAgentComplianceIsolateWhenContactNotifiedStart = 0.0005;
    testKit.getGlobals().defaultAgentComplianceIsolateWhenContactNotifiedEnd = 0.0006;

    testKit.getGlobals().defaultAgentCompliancePhysicalDistancingStart = 0.0007;
    testKit.getGlobals().defaultAgentCompliancePhysicalDistancingtEnd = 0.0008;

    testKit.getGlobals().percInitiallyInfected = 0;
    testKit.getGlobals().percInitiallyRecovered = 0;

    testKit.getGlobals().percN95Masks = 1.0;
    testKit.getGlobals().percSurgicalMasks = 0.0;
    testKit.getGlobals().percHomemadeClothMasks = 0.0;

    testKit.getGlobals().percInitiallyVaccinated = 1.0;

    // Act
    Person p = testKit.addAgent(Person.class, Person::init);

    // Assert
    assertThat(p.age).isAtLeast(0.0);
    assertThat(p.age).isAtMost(0.0001);

    assertThat(p.complianceMask).isAtLeast(0.0001);
    assertThat(p.complianceMask).isAtMost(0.0002);

    assertThat(p.compQuarantineWhenSymptomatic).isAtLeast(0.0002);
    assertThat(p.compQuarantineWhenSymptomatic).isAtMost(0.0003);

    assertThat(p.compSymptomsReport).isAtLeast(0.0003);
    assertThat(p.compSymptomsReport).isAtMost(0.0004);

    assertThat(p.complianceIsolating).isAtLeast(0.0004);
    assertThat(p.complianceIsolating).isAtMost(0.0005);

    assertThat(p.complianceIsolateWhenContactNotified).isAtLeast(0.0005);
    assertThat(p.complianceIsolateWhenContactNotified).isAtMost(0.0006);

    assertThat(p.compliancePhysicalDistancing).isAtLeast(0.0007);
    assertThat(p.compliancePhysicalDistancing).isAtMost(0.0008);

    assertThat(p.status).isEqualTo(Person.InfectionStatus.SUSCEPTIBLE);

    assertThat(p.maskType).isEqualTo(Person.MaskType.N95);

    assertThat(p.isVaccinated).isTrue();
  }

  /**
   * This test is flaky. It is stochastic in nature, so it technically should fail a small
   * percentage of the time. If it fails occasionally, run it again. If it fails consistently, this
   * might be a problem.
   */
  @Test
  public void testInit_initialRatiosForSIRStatusOfAgents() {
    testKit.getGlobals().percInitiallyInfected = 0.05;
    testKit.getGlobals().percInitiallyRecovered = 0.10;

    int numSusceptible = 0;
    int numInfected = 0;
    int numRecovered = 0;
    for (int i = 0; i < 1000; i++) {
      Person p = testKit.addAgent(Person.class, Person::init);
      if (p.status == Person.InfectionStatus.SUSCEPTIBLE) {
        numSusceptible++;
      } else if (p.status == Person.InfectionStatus.INFECTED) {
        numInfected++;
      } else if (p.status == RECOVERED) {
        numRecovered++;
      }
    }
    assertThat(numSusceptible + numInfected + numRecovered).isEqualTo(1000);
    assertThat(numSusceptible / 1000.0).isWithin(0.1).of(0.85);
    assertThat(numInfected / 1000.0).isWithin(0.1).of(0.05);
    assertThat(numRecovered / 1000.0).isWithin(0.1).of(0.10);
  }

  @Test
  public void testInfectionSeverityCalculation_asymptomatic() {
    testKit.getGlobals().tOneDay = 2;
    testPerson.setInfectionTrajectoryDistribution(
        InfectionTrajectoryDistribution.dummyBuilder()
            .percentageAsymptomaticCases(1)
            .percentageNonSevereSymptomaticCases(0)
            .percentageSevereCases(0)
            .infectiousRangeStart(2)
            .infectiousRangeEnd(2)
            .illnessDurationNonSevereRangeStart(7)
            .illnessDurationNonSevereRangeEnd(7)
            .symptomsOnsetRangeStart(5)
            .symptomsOnsetRangeEnd(5)
            .illnessDurationSevereRangeStart(14)
            .illnessDurationSevereRangeEnd(14)
            .build());

    // Include 1 as timeInfected to test that add
    InfectionCharacteristics characteristics = testPerson.infectionSeverity(1);

    assertThat(characteristics.isAsymptomatic()).isTrue();
    assertThat(characteristics.tInfectious()).isEqualTo(4 + 1);
    assertThat(characteristics.illnessDuration()).isEqualTo(14 + 1);
  }

  @Test
  public void testInfectionSeverityCalculation_symptomaticNonSevere() {
    testKit.getGlobals().tOneDay = 2;
    testPerson.setInfectionTrajectoryDistribution(
        InfectionTrajectoryDistribution.dummyBuilder()
            .percentageAsymptomaticCases(0)
            .percentageNonSevereSymptomaticCases(1)
            .percentageSevereCases(0)
            .infectiousRangeStart(2)
            .infectiousRangeEnd(2)
            .illnessDurationNonSevereRangeStart(7)
            .illnessDurationNonSevereRangeEnd(7)
            .symptomsOnsetRangeStart(5)
            .symptomsOnsetRangeEnd(5)
            .illnessDurationSevereRangeStart(14)
            .illnessDurationSevereRangeEnd(14)
            .build());

    InfectionCharacteristics characteristics = testPerson.infectionSeverity(1);

    assertThat(characteristics.symptomsOnset()).isEqualTo(10 + 1);
    assertThat(characteristics.tInfectious()).isEqualTo(4 + 1);
    assertThat(characteristics.illnessDuration()).isEqualTo(14 + 1);
  }

  @Test
  public void testInfectionSeverityCalculation_severe() {
    testKit.getGlobals().tOneDay = 2;
    testPerson.setInfectionTrajectoryDistribution(
        InfectionTrajectoryDistribution.dummyBuilder()
            .percentageAsymptomaticCases(0)
            .percentageNonSevereSymptomaticCases(0)
            .percentageSevereCases(1)
            .infectiousRangeStart(2)
            .infectiousRangeEnd(2)
            .illnessDurationNonSevereRangeStart(7)
            .illnessDurationNonSevereRangeEnd(7)
            .symptomsOnsetRangeStart(5)
            .symptomsOnsetRangeEnd(5)
            .illnessDurationSevereRangeStart(14)
            .illnessDurationSevereRangeEnd(14)
            .build());

    InfectionCharacteristics characteristics = testPerson.infectionSeverity(1);

    assertThat(characteristics.symptomsOnset()).isEqualTo(10 + 1);
    assertThat(characteristics.tInfectious()).isEqualTo(4 + 1);
    assertThat(characteristics.illnessDuration()).isEqualTo(28 + 1);
  }

  // TODO Look into death logic and make sure this test is correct
  //  @Test
  //  public void testCheckDeath() {
  //    testKit.getGlobals().tStep = 0;
  //    testPerson.setInfectionTrajectoryDistribution(
  //        InfectionTrajectoryDistribution.dummyBuilder()
  //            .percentageAsymptomaticCases(0)
  //            .percentageNonSevereSymptomaticCases(0)
  //            .percentageSevereCases(1)
  //            .infectiousRangeStart(2)
  //            .infectiousRangeEnd(2)
  //            .illnessDurationNonSevereRangeStart(7)
  //            .illnessDurationNonSevereRangeEnd(7)
  //            .symptomsOnsetRangeStart(5)
  //            .symptomsOnsetRangeEnd(5)
  //            .illnessDurationSevereRangeStart(14)
  //            .illnessDurationSevereRangeEnd(14)
  //            .build());
  //
  //    for (int i = 0; i < testKit.getGlobals().pAgeDeath.length; i++) {
  //      testKit.getGlobals().pAgeDeath = new double[testKit.getGlobals().pAgeDeath.length];
  //      testKit.getGlobals().pAgeDeath[i] = 0.5;
  //      testPerson.age = (i + 1) * 10 - 1;
  //      testPerson.setInfected();
  //      int numDead = 0;
  //      for (int j = 0; j < 1000; j++) {
  //        if (testPerson.checkDeath()) {
  //          numDead++;
  //        }
  //      }
  //      assertThat(Math.abs(numDead - 500)).isLessThan(50);
  //
  //      if (testPerson.age < 80) {
  //        testPerson.age++;
  //        testPerson.setInfected();
  //        numDead = 0;
  //        for (int j = 0; j < 1000; j++) {
  //          if (testPerson.checkDeath()) {
  //            numDead++;
  //          }
  //        }
  //        assertThat(numDead).isEqualTo(0);
  //      }
  //    }
  //  }

  @Test
  public void testReportSymptoms_notInfectedDoesNotSendMessage() {
    testKit.getGlobals().tStep = 0;
    testPerson.symptomOnset = 0;
    testPerson.hasBeenTested = false;
    testPerson.compSymptomsReport = 1.0;
    testPerson.status = Person.InfectionStatus.SUSCEPTIBLE;
    testPerson.isSelfIsolatingBecauseOfSymptoms = false;
    testPerson.compQuarantineWhenSymptomatic = 1;

    TestResult result = testKit.testAction(testPerson, Person.reportSymptoms);

    List<Messages.SymptomaticMsg> msgs = result.getMessagesOfType(Messages.SymptomaticMsg.class);
    assertThat(msgs).isEmpty();
    assertThat(testPerson.isSelfIsolatingBecauseOfSymptoms).isFalse();
  }

  @Test
  public void testReportSymptoms_noSymptomsInfectedDoesNotSendMessage() {
    testKit.getGlobals().tStep = 0;
    testPerson.symptomOnset = 100;
    testPerson.hasBeenTested = false;
    testPerson.compSymptomsReport = 1;
    testPerson.status = Person.InfectionStatus.INFECTED;
    testPerson.isSelfIsolatingBecauseOfSymptoms = false;
    testPerson.compQuarantineWhenSymptomatic = 1;

    TestResult result = testKit.testAction(testPerson, Person.reportSymptoms);

    List<Messages.SymptomaticMsg> msgs = result.getMessagesOfType(Messages.SymptomaticMsg.class);
    assertThat(msgs).isEmpty();
    assertThat(testPerson.isSelfIsolatingBecauseOfSymptoms).isFalse();
  }

  @Test
  public void testReportSymptoms_hasBeenTestedDoesNotSendMessage() {
    testKit.getGlobals().tStep = 0;
    testPerson.symptomOnset = 0;
    testPerson.hasBeenTested = true;
    testPerson.compSymptomsReport = 1;
    testPerson.status = Person.InfectionStatus.INFECTED;
    testPerson.isSelfIsolatingBecauseOfSymptoms = false;
    testPerson.compQuarantineWhenSymptomatic = 1;

    TestResult result = testKit.testAction(testPerson, Person.reportSymptoms);

    List<Messages.SymptomaticMsg> msgs = result.getMessagesOfType(Messages.SymptomaticMsg.class);
    assertThat(msgs).isEmpty();
    assertThat(testPerson.isSelfIsolatingBecauseOfSymptoms).isTrue();
  }

  @Test
  public void testReportSymptoms_lowComplianceDoesNotSendMessage() {
    testKit.getGlobals().tStep = 0;
    testPerson.symptomOnset = 0;
    testPerson.hasBeenTested = false;
    testPerson.compSymptomsReport = -0.00001;
    testPerson.status = Person.InfectionStatus.INFECTED;
    testPerson.isSelfIsolatingBecauseOfSymptoms = false;
    testPerson.compQuarantineWhenSymptomatic = -0.0001;

    TestResult result = testKit.testAction(testPerson, Person.reportSymptoms);

    List<Messages.SymptomaticMsg> msgs = result.getMessagesOfType(Messages.SymptomaticMsg.class);
    assertThat(msgs).isEmpty();
    assertThat(testPerson.isSelfIsolatingBecauseOfSymptoms).isFalse();
  }

  @Test
  public void testReportSymptoms_shouldSendMessage() {
    testKit.getGlobals().tStep = 0;
    testPerson.symptomOnset = 0;
    testPerson.hasBeenTested = false;
    testPerson.compSymptomsReport = 1;
    testPerson.status = Person.InfectionStatus.INFECTED;
    testPerson.isSelfIsolatingBecauseOfSymptoms = false;
    testPerson.compQuarantineWhenSymptomatic = 1;

    TestResult result = testKit.testAction(testPerson, Person.reportSymptoms);

    List<Messages.SymptomaticMsg> msgs = result.getMessagesOfType(Messages.SymptomaticMsg.class);
    assertThat(msgs).hasSize(1);
    assertThat(testPerson.isSelfIsolatingBecauseOfSymptoms).isTrue();
  }

  @Test
  public void testGetsInfectedFromCovid() {
    testKit.getGlobals().tStep = 5;
    testPerson.setInfectionTrajectoryDistribution(
        InfectionTrajectoryDistribution.dummyBuilder()
            .percentageAsymptomaticCases(0)
            .percentageNonSevereSymptomaticCases(1)
            .percentageSevereCases(0)
            .infectiousRangeStart(2)
            .infectiousRangeEnd(2)
            .illnessDurationNonSevereRangeStart(7)
            .illnessDurationNonSevereRangeEnd(7)
            .symptomsOnsetRangeStart(5)
            .symptomsOnsetRangeEnd(5)
            .illnessDurationSevereRangeStart(14)
            .illnessDurationSevereRangeEnd(14)
            .build());

    testKit.send(Messages.InfectionMsg.class).to(testPerson);
    testKit.testAction(testPerson, Person.infectedByCOVID);

    assertThat(testPerson.isSymptomatic()).isFalse();
    assertThat(testPerson.status).isEqualTo(Person.InfectionStatus.INFECTED);
    assertThat(testPerson.timeInfected).isEqualTo(5);
    assertThat(testPerson.tInfectious).isEqualTo(5 + 2);
    assertThat(testPerson.illnessDuration).isEqualTo(5 + 7);
    assertThat(testPerson.symptomOnset).isEqualTo(5 + 5);
  }

  @Test
  public void testCovidIllnessTrajectory() {
    testKit.getGlobals().tStep = 5;
    testPerson.setInfectionTrajectoryDistribution(
        InfectionTrajectoryDistribution.dummyBuilder()
            .percentageAsymptomaticCases(0)
            .percentageNonSevereSymptomaticCases(1)
            .percentageSevereCases(0)
            .infectiousRangeStart(2)
            .infectiousRangeEnd(2)
            .illnessDurationNonSevereRangeStart(7)
            .illnessDurationNonSevereRangeEnd(7)
            .symptomsOnsetRangeStart(5)
            .symptomsOnsetRangeEnd(5)
            .illnessDurationSevereRangeStart(14)
            .illnessDurationSevereRangeEnd(14)
            .build());

    testKit.send(Messages.InfectionMsg.class).to(testPerson);
    testKit.testAction(testPerson, Person.infectedByCOVID);
    assertThat(testPerson.status).isEqualTo(Person.InfectionStatus.INFECTED);
    assertThat(testPerson.isInfectious()).isFalse();
    assertThat(testPerson.isSymptomatic()).isFalse();
    assertThat(testPerson.isFirstTimeSymptomatic()).isFalse();

    testKit.getGlobals().tStep = 5 + 1;

    testKit.send(Messages.InfectionMsg.class).to(testPerson);
    testKit.testAction(testPerson, Person.infectedByCOVID);
    assertThat(testPerson.status).isEqualTo(Person.InfectionStatus.INFECTED);
    assertThat(testPerson.isInfectious()).isFalse();
    assertThat(testPerson.isSymptomatic()).isFalse();
    assertThat(testPerson.isFirstTimeSymptomatic()).isFalse();

    testKit.getGlobals().tStep = 5 + 2;

    testKit.send(Messages.InfectionMsg.class).to(testPerson);
    testKit.testAction(testPerson, Person.infectedByCOVID);
    assertThat(testPerson.status).isEqualTo(Person.InfectionStatus.INFECTED);
    assertThat(testPerson.isInfectious()).isTrue();
    assertThat(testPerson.isSymptomatic()).isFalse();
    assertThat(testPerson.isFirstTimeSymptomatic()).isFalse();

    testKit.getGlobals().tStep = 5 + 3;

    testKit.send(Messages.InfectionMsg.class).to(testPerson);
    testKit.testAction(testPerson, Person.infectedByCOVID);
    assertThat(testPerson.status).isEqualTo(Person.InfectionStatus.INFECTED);
    assertThat(testPerson.isInfectious()).isTrue();
    assertThat(testPerson.isSymptomatic()).isFalse();
    assertThat(testPerson.isFirstTimeSymptomatic()).isFalse();

    testKit.getGlobals().tStep = 5 + 4;

    testKit.send(Messages.InfectionMsg.class).to(testPerson);
    testKit.testAction(testPerson, Person.infectedByCOVID);
    assertThat(testPerson.status).isEqualTo(Person.InfectionStatus.INFECTED);
    assertThat(testPerson.isInfectious()).isTrue();
    assertThat(testPerson.isSymptomatic()).isFalse();
    assertThat(testPerson.isFirstTimeSymptomatic()).isFalse();

    testKit.getGlobals().tStep = 5 + 5;
    testKit.testAction(testPerson, Person.recoverOrDieOrStep);

    assertThat(testPerson.isSymptomatic()).isTrue();
    assertThat(testPerson.isFirstTimeSymptomatic()).isTrue();

    testKit.getGlobals().tStep = 5 + 6;
    testKit.testAction(testPerson, Person.recoverOrDieOrStep);

    assertThat(testPerson.isSymptomatic()).isTrue();
    assertThat(testPerson.isFirstTimeSymptomatic()).isFalse();

    testKit.getGlobals().tStep = 5 + 7;
    testKit.testAction(testPerson, Person.recoverOrDieOrStep);

    assertThat(testPerson.isSymptomatic()).isFalse();
    assertThat(testPerson.isFirstTimeSymptomatic()).isFalse();
    assertThat(testPerson.status).isEqualTo(RECOVERED);
  }

  @Test
  public void testGetsInfectedFromOtherIllness() {
    testKit.getGlobals().tStep = 5;
    testKit.getGlobals().otherIllnessInfectionRate = 1.0;
    testKit.getGlobals().otherIllnessDurationStart = 7;
    testKit.getGlobals().otherIllnessDurationStart = 7;

    testKit.testAction(testPerson, Person.getInfectedByOtherIllness);

    assertThat(testPerson.isSymptomatic()).isTrue();
    assertThat(testPerson.isFirstTimeSymptomatic()).isTrue();
    assertThat(testPerson.isSymptomaticFromOtherIllness).isTrue();
    assertThat(testPerson.becameSymptomaticFromOtherIllnessAtStep).isEqualTo(5);
    assertThat(testPerson.otherIllnessRecoveryTime).isEqualTo(5 + 7);
  }

  @Test
  public void testGetsInfectedFromOtherIllness_noIllness() {
    testKit.getGlobals().tStep = 5;
    testKit.getGlobals().otherIllnessInfectionRate = 0.0;
    testKit.getGlobals().otherIllnessDurationStart = 7;
    testKit.getGlobals().otherIllnessDurationStart = 7;

    testKit.testAction(testPerson, Person.getInfectedByOtherIllness);

    assertThat(testPerson.isSymptomatic()).isFalse();
    assertThat(testPerson.isFirstTimeSymptomatic()).isFalse();
    assertThat(testPerson.isSymptomaticFromOtherIllness).isFalse();
  }

  @Test
  public void testOtherIllnessTrajectory() {
    testKit.getGlobals().tStep = 5;
    testKit.getGlobals().otherIllnessInfectionRate = 1.0;
    testKit.getGlobals().otherIllnessDurationStart = 7;
    testKit.getGlobals().otherIllnessDurationStart = 7;
    testKit.testAction(testPerson, Person.getInfectedByOtherIllness);

    for (int i = 0; i < 6; i++) {
      testKit.getGlobals().tStep++;
      testKit.testAction(testPerson, Person.recoverOrDieOrStep);
      assertThat(testPerson.isSymptomatic()).isTrue();
      assertThat(testPerson.isFirstTimeSymptomatic()).isFalse();
    }

    testKit.getGlobals().tStep = 5 + 7;
    testKit.testAction(testPerson, Person.recoverOrDieOrStep);

    assertThat(testPerson.isSymptomatic()).isFalse();
    assertThat(testPerson.isSymptomaticFromOtherIllness).isFalse();
  }

  @Test
  public void testGetTested() {
    testPerson.status = Person.InfectionStatus.INFECTED;
    testPerson.hasBeenTested = false;

    testKit.send(Messages.TestAdministeredMsg.class).to(testPerson);
    TestResult result = testKit.testAction(testPerson, Person.getTested);

    assertThat(testPerson.hasBeenTested).isTrue();
    List<Messages.InfectionStatusMsg> msgs =
        result.getMessagesOfType(Messages.InfectionStatusMsg.class);
    assertThat(msgs).hasSize(1);
  }

  @Test
  public void testQuarantineOrder() {
    testPerson.isSelfIsolatingBecauseOfContactTracing = false;
    testKit.getGlobals().tStep = 5;
    testPerson.complianceIsolateWhenContactNotified = 1.0;

    testKit
        .send(
            Messages.QuarantineOrderMsg.class)
        .to(testPerson);
    testKit.testAction(testPerson, Person.receiveQuarantineStartOrStopAndAdministerTest);

    assertThat(testPerson.isSelfIsolatingBecauseOfContactTracing).isTrue();
    assertThat(testPerson.startedIsolatingFromContactNotifyAt).isEqualTo(5);
  }

  @Test
  public void testQuarantineOrder_zeroComplianceDoesntQuarantine() {
    testPerson.isSelfIsolatingBecauseOfContactTracing = false;
    testKit.getGlobals().tStep = 5;
    testPerson.complianceIsolateWhenContactNotified = 0.0;

    testKit
        .send(
            Messages.QuarantineOrderMsg.class)
        .to(testPerson);
    testKit.testAction(testPerson, Person.receiveQuarantineStartOrStopAndAdministerTest);

    assertThat(testPerson.isSelfIsolatingBecauseOfContactTracing).isFalse();
  }

  @Test
  public void testQuarantineOrder_testFailsWithoutMessage() {
    testPerson.isSelfIsolatingBecauseOfContactTracing = false;
    testKit.getGlobals().tStep = 5;
    testPerson.complianceIsolateWhenContactNotified = 1.0;

    testKit.testAction(testPerson, Person.receiveQuarantineStartOrStopAndAdministerTest);

    assertThat(testPerson.isSelfIsolatingBecauseOfContactTracing).isFalse();
  }


  @Test
  public void testQuarantineOrder_exposureTime() {
    testPerson.isSelfIsolatingBecauseOfContactTracing = false;
    testKit.getGlobals().tStep = 5;
    testPerson.complianceIsolateWhenContactNotified = 1.0;

    testKit
        .send(
            Messages.QuarantineOrderMsg.class,
            msg -> msg.exposureTime = 10L)
        .to(testPerson);
    testKit.testAction(testPerson, Person.receiveQuarantineStartOrStopAndAdministerTest);

    assertThat(testPerson.isSelfIsolatingBecauseOfContactTracing).isTrue();
    assertThat(testPerson.startedIsolatingFromContactNotifyAt).isEqualTo(10);
  }

  @Test
  public void testQuarantineRelease() {
    testPerson.isSelfIsolatingBecauseOfContactTracing = true;

    testKit
        .send(
            Messages.QuarantineReleaseMsg.class)
        .to(testPerson);
    testKit.testAction(testPerson, Person.receiveQuarantineStartOrStopAndAdministerTest);

    assertThat(testPerson.isSelfIsolatingBecauseOfContactTracing).isFalse();
    assertThat(testPerson.startedIsolatingFromContactNotifyAt).isEqualTo(Long.MIN_VALUE);
  }

  @Test
  public void testQuarantineRelease_testFailsWithoutMessage() {
    testPerson.isSelfIsolatingBecauseOfContactTracing = true;

    testKit.testAction(testPerson, Person.receiveQuarantineStartOrStopAndAdministerTest);

    assertThat(testPerson.isSelfIsolatingBecauseOfContactTracing).isTrue();
  }

  @Test
  public void testChooseToIsolate() {
    testPerson.isSelfIsolatingBecauseOfSymptoms = true;
    testPerson.complianceIsolating = 1;

    assertThat(testPerson.choosesToIsolate()).isTrue();
  }

  @Test
  public void testNotChooseToIsolate_highComplianceButNotSelfIsolating() {
    testPerson.isSelfIsolatingBecauseOfSymptoms = false;
    testPerson.complianceIsolating = 1;

    assertThat(testPerson.choosesToIsolate()).isFalse();
  }

  @Test
  public void testNotChooseToIsolate_lowCompliance() {
    testPerson.isSelfIsolatingBecauseOfSymptoms = true;
    testPerson.complianceIsolating = -0.0001;

    assertThat(testPerson.choosesToIsolate()).isFalse();
  }

  @Test
  public void testIsolateFromContactNotify() {
    Person p = testKit.addAgent(Person.class);
    p.isolationPlaceInfos = ImmutableList.of(TestUtils.createPlaceInfoWithAgent("Isolation", 0, testKit));
    p.dailySchedule =
        Person.DailySchedule.create(
            ImmutableMap.of(
                0,
                ImmutableList.of(TestUtils.createPlaceInfoWithAgent("NotIsolation1", 0, testKit)),
                1,
                ImmutableList.of(TestUtils.createPlaceInfoWithAgent("NotIsolation2", 0, testKit))),
            ImmutableList.of(TestUtils.createPlaceInfoWithAgent("Isolation", 0, testKit)));
    p.complianceIsolateWhenContactNotified = 1.0;
    testKit.getGlobals().tOneDay = 2;
    testKit.getGlobals().tStep = 1;
    testKit.getGlobals().contactNotifiedNumberOfDaysToIsolate = 14;
    p.isSelfIsolatingBecauseOfSymptoms = false;

    testKit.send(Messages.QuarantineOrderMsg.class).to(p);
    testKit.testAction(p, Person.receiveQuarantineStartOrStopAndAdministerTest);

    assertThat(p.isSelfIsolatingBecauseOfContactTracing).isTrue();

    for (int i = 0; i < 14 * 2; i++) {
      testKit.getGlobals().tStep++;
      p.decideNextLocation();
      assertThat(p.isSelfIsolatingBecauseOfContactTracing).isTrue();
    }
    testKit.getGlobals().tStep++;
    p.decideNextLocation();
    assertThat(p.isSelfIsolatingBecauseOfContactTracing).isFalse();
  }

  @Test
  public void testNoIsolateFromContactNotify_NonCompliant() {
    Person p = testKit.addAgent(Person.class);
    p.isolationPlaceInfos = ImmutableList.of(TestUtils.createPlaceInfoWithAgent("", 0, testKit));
    p.complianceIsolateWhenContactNotified = -0.0001;
    testKit.getGlobals().tOneDay = 2;
    testKit.getGlobals().tStep = 1;
    testKit.getGlobals().contactNotifiedNumberOfDaysToIsolate = 14;
    p.isSelfIsolatingBecauseOfSymptoms = false;

    testKit.send(Messages.QuarantineOrderMsg.class).to(p);
    testKit.testAction(p, Person.receiveQuarantineStartOrStopAndAdministerTest);

    assertThat(p.isSelfIsolatingBecauseOfSymptoms).isFalse();
  }

  @Test
  public void testStaysInIsolation() {
    testKit.createLongAccumulator("totInfected");
    testKit.createLongAccumulator("totQuarantineInfected");
    testKit.createLongAccumulator("totRecovered");
    testKit.getGlobals().tStep = 0;
    Person p = testKit.addAgent(Person.class, Person::init);
    p.isolationPlaceInfos = ImmutableList.of(TestUtils.createPlaceInfoWithAgent("ISOLATION", 0, testKit));
    p.dailySchedule =
        Person.DailySchedule.create(
            ImmutableMap.of(0, ImmutableList.of(TestUtils.createPlaceInfoWithAgent("A", 0, testKit))), p.isolationPlaceInfos);
    p.compQuarantineWhenSymptomatic = 1.0;
    p.complianceIsolating = 1.0;

    // Avoid calling Person.setInfected because of the accumulator call
    p.status = Person.InfectionStatus.INFECTED;
    p.timeInfected = 0;
    p.illnessDuration = 2;
    p.symptomOnset = 1;

    testKit.testAction(p, Person.reportSymptoms);
    assertThat(p.isSelfIsolatingBecauseOfSymptoms).isFalse();

    testKit.testAction(p, Person.recoverOrDieOrStep);
    p.decideNextLocation();
    assertThat(p.getCurrentPlaces().get(0).placeName()).isEqualTo("A");

    testKit.getGlobals().tStep = 1;
    testKit.testAction(p, Person.reportSymptoms);
    assertThat(p.isSelfIsolatingBecauseOfSymptoms).isTrue();
    testKit.testAction(p, Person.recoverOrDieOrStep);
    p.decideNextLocation();

    assertThat(p.getCurrentPlaces().get(0).placeName()).isEqualTo("ISOLATION");

    testKit.getGlobals().tStep = 2;
    testKit.testAction(p, Person.recoverOrDieOrStep);
    p.decideNextLocation();

    assertThat(p.getCurrentPlaces().get(0).placeName()).isEqualTo("A");
  }

  @Test
  public void testPerson() {
    testPerson.setTestingMultiplier(2.34);

    TestResult result =
        testKit.testAction(testPerson, Person.sendTestSelectionMultiplierToCentralAgent);

    assertThat(result.getMessagesOfType(Messages.TestSelectionMultiplierMessage.class))
        .isNotEmpty();
    assertThat(
        result
            .getMessagesOfType(Messages.TestSelectionMultiplierMessage.class)
            .get(0)
            .getBody())
        .isEqualTo(2.34);
  }

  @Test
  public void testMoveFromSchedule() {
    Person p = testKit.addAgent(Person.class, Person::init);
    testKit.getGlobals().tOneDay = 2;
    testKit.getGlobals().tStep = 0;

    ImmutableList<PlaceInfo> l1 = ImmutableList.of(TestUtils.createPlaceInfoWithAgent("A", 0, testKit));
    ImmutableList<PlaceInfo> l2 = ImmutableList.of(TestUtils.createPlaceInfoWithAgent("B", 1, testKit));
    Person.DailySchedule s =
        Person.DailySchedule.create(
            ImmutableMap.of(
                0, l1,
                1, l2),
            ImmutableList.of(TestUtils.createPlaceInfoWithAgent("ISOLATION", 3, testKit)));

    p.dailySchedule = s;
    p.isolationPlaceInfos = s.isolationPlaces();
    assertThat(p.getScheduledPlaces().get(0).placeName()).isEqualTo("A");

    testKit.testAction(p, Person.movePerson);

    assertThat(p.getCurrentPlaces().get(0).placeName()).isEqualTo("A");

    testKit.getGlobals().tStep = 1;
    p.decideNextLocation();

    assertThat(p.getCurrentPlaces().get(0).placeName()).isEqualTo("B");
  }

  @Test
  public void testReceiveSchedule() {
    Person p = testKit.addAgent(Person.class, Person::init);

    ImmutableList<PlaceInfo> l1 = ImmutableList.of(TestUtils.createPlaceInfoWithAgent("A", 0, testKit));
    ImmutableList<PlaceInfo> l2 = ImmutableList.of(TestUtils.createPlaceInfoWithAgent("B", 1, testKit));
    Person.DailySchedule s =
        Person.DailySchedule.create(
            ImmutableMap.of(
                0, l1,
                1, l2),
            ImmutableList.of(TestUtils.createPlaceInfoWithAgent("ISOLATION", 3, testKit)));

    testKit
        .send(Messages.ScheduleMessage.class, scheduleMessage -> scheduleMessage.schedule = s)
        .to(p);

    testKit.testAction(p, Person.receiveSchedule);
    assertThat(p.dailySchedule).isEqualTo(s);
    assertThat(p.isolationPlaceInfos).isEqualTo(s.isolationPlaces());
  }

  @Test(expected = IllegalStateException.class)
  public void testReceiveScheduleException() {
    testKit.getGlobals().tStep = 1;
    testKit.testAction(testPerson, Person.receiveSchedule);
  }

  @Test
  public void testIsolationMovement() {
    Person p = testKit.addAgent(Person.class, Person::init);
    testKit.getGlobals().tStep = 0;

    Person.DailySchedule s =
        Person.DailySchedule.create(
            ImmutableMap.of(0, ImmutableList.of(TestUtils.createPlaceInfoWithAgent("A", 0, testKit))),
            ImmutableList.of(TestUtils.createPlaceInfoWithAgent("ISOLATION", 1, testKit)));

    p.dailySchedule = s;
    p.isolationPlaceInfos = s.isolationPlaces();
    p.decideNextLocation();

    assertThat(p.getCurrentPlaces().get(0).placeName()).isEqualTo("A");

    testKit.getGlobals().forceAllAgentsToIsolate = true;
    p.decideNextLocation();

    assertThat(p.getCurrentPlaces().get(0).placeName()).isEqualTo("ISOLATION");

    testKit.getGlobals().forceAllAgentsToIsolate = false;
    p.decideNextLocation();

    assertThat(p.getCurrentPlaces().get(0).placeName()).isEqualTo("A");
  }

  @Test
  public void testExecuteMovement_wearsMask() {
    PlaceInfo placeInfo = TestUtils.createPlaceInfoWithAgent("A", 0, testKit);
    placeInfo.receivePlaceAgent(placeAgent.getID());
    testPerson.setNextPlace(placeInfo);
    testPerson.status = Person.InfectionStatus.SUSCEPTIBLE;
    testPerson.decideNextLocation();
    testPerson.maskType = Person.MaskType.N95;
    testPerson.complianceMask = 1;

    TestResult result = testKit.testAction(testPerson, Person.executeMovement);
    assertThat(result.getMessagesOfType(Messages.IAmHereMsg.class)).isNotEmpty();
    assertThat(
        result
            .getMessagesOfType(Messages.IAmHereMsg.class)
            .get(0)
            .transmissibilityInfo
            .wearsMask())
        .isEqualTo(Person.MaskType.N95);
  }

  @Test
  public void testExecuteMovement_doesNotWearMask() {
    PlaceInfo placeInfo = TestUtils.createPlaceInfoWithAgent("A", 0, testKit);
    placeInfo.receivePlaceAgent(placeAgent.getID());
    testPerson.setNextPlace(placeInfo);
    testPerson.status = Person.InfectionStatus.SUSCEPTIBLE;
    testPerson.decideNextLocation();
    testPerson.maskType = Person.MaskType.N95;
    testPerson.complianceMask = 0;

    TestResult result = testKit.testAction(testPerson, Person.executeMovement);
    assertThat(result.getMessagesOfType(Messages.IAmHereMsg.class)).isNotEmpty();
    assertThat(
        result
            .getMessagesOfType(Messages.IAmHereMsg.class)
            .get(0)
            .transmissibilityInfo
            .wearsMask())
        .isEqualTo(Person.MaskType.NONE);
  }

  @Test
  public void testGenerateAdditionalPlace() {
    List<PlaceInfo> additionalPlaces = testPerson.generateAdditionalPlace();
    assertThat(additionalPlaces).isEmpty();
  }

  /**
   * This test is reimplemented in TAU.StudentTest.
   * The base Person does not generate any additional places, so nothing would
   * happen as a result of Person#decideToHostAdditionalEvent
   */
  @Test
  public void testDecideToHostAdditionalEvent() {
  }

  @Test
  public void testDecideToAttendAdditionalEvent() {
    testPerson.probAttendsAdditionalEvent = 0;

    testKit.send(Messages.PlaceInfoMessage.class, msg ->
        msg.setBody(PlaceInfo.create("p1", 0)))
        .to(testPerson);

    testKit.testAction(testPerson, Person.decideToAttendAdditionalEvent);

    assertThat(testPerson.getAdditionalPlaceInfos().size()).isEqualTo(0);

    testPerson.probAttendsAdditionalEvent = 2;
    testKit.send(Messages.PlaceInfoMessage.class, msg ->
        msg.setBody(PlaceInfo.create("p2", 0)))
        .to(testPerson);
    testKit.send(Messages.PlaceInfoMessage.class, msg ->
        msg.setBody(PlaceInfo.create("p3", 0)))
        .to(testPerson);

    testKit.testAction(testPerson, Person.decideToAttendAdditionalEvent);

    assertThat(testPerson.getAdditionalPlaceInfos().size()).isEqualTo(2);
  }

  @Test
  public void testPlaceHistory() {
    testKit.getGlobals().contactTracingNumberOfDaysTraceback = 3;

    PlaceInfo place1 = TestUtils.createPlaceInfoWithAgent("Place1", 1, testKit);
    testPerson.setCurrentPlaces(place1);
    testKit.testAction(testPerson, Person.executeMovement);

    PlaceInfo place2 = TestUtils.createPlaceInfoWithAgent("Place2", 1, testKit);
    testPerson.setCurrentPlaces(place2);
    testKit.testAction(testPerson, Person.executeMovement);

    PlaceInfo place3 = TestUtils.createPlaceInfoWithAgent("Place3", 1, testKit);
    testPerson.setCurrentPlaces(place3);
    testKit.testAction(testPerson, Person.executeMovement);

    PlaceInfo place4 = TestUtils.createPlaceInfoWithAgent("Place4", 1, testKit);
    testPerson.setCurrentPlaces(place4);
    testKit.testAction(testPerson, Person.executeMovement);

    assertThat(testPerson.placeHistory).hasSize(3);
    assertThat(testPerson.placeHistory.get(0)).hasSize(1);
    assertThat(testPerson.placeHistory.get(0).get(0)).isEqualTo(place2.placeId());
    assertThat(testPerson.placeHistory.get(1)).hasSize(1);
    assertThat(testPerson.placeHistory.get(1).get(0)).isEqualTo(place3.placeId());
    assertThat(testPerson.placeHistory.get(2)).hasSize(1);
    assertThat(testPerson.placeHistory.get(2).get(0)).isEqualTo(place4.placeId());
  }

  @Test
  public void testStartQuarantineIfOrdered_multipleOrders() {
    // Person is sent several quarantine orders. Make sure exposure time is set to
    // the latest
    testPerson.complianceIsolateWhenContactNotified = 1.0;
    testKit.send(Messages.QuarantineOrderMsg.class, msg -> msg.exposureTime = 1L)
        .to(testPerson);
    testKit.send(Messages.QuarantineOrderMsg.class, msg -> msg.exposureTime = 2L)
        .to(testPerson);
    testKit.send(Messages.QuarantineOrderMsg.class, msg -> msg.exposureTime = 3L)
        .to(testPerson);
    testKit.send(Messages.QuarantineOrderMsg.class, msg -> msg.exposureTime = null)
        .to(testPerson);

    testKit.testAction(testPerson, Person.receiveQuarantineStartOrStopAndAdministerTest);

    assertThat(testPerson.startedIsolatingFromContactNotifyAt).isEqualTo(3L);
  }

  @Test
  public void testStartQuarantineIfOrdered_allNulls() {
    // Person is sent several quarantine orders. Make sure exposure time is set to
    // the latest
    testPerson.complianceIsolateWhenContactNotified = 1.0;
    testKit.getGlobals().tStep = 10;
    testKit.send(Messages.QuarantineOrderMsg.class, msg -> msg.exposureTime = null)
        .to(testPerson);
    testKit.send(Messages.QuarantineOrderMsg.class, msg -> msg.exposureTime = null)
        .to(testPerson);

    testKit.testAction(testPerson, Person.receiveQuarantineStartOrStopAndAdministerTest);

    assertThat(testPerson.startedIsolatingFromContactNotifyAt).isEqualTo(10);
  }

  @Test
  public void testGeneratePerfectTests() {
    testKit.getGlobals().testingType = 0;
    testPerson.status = Person.InfectionStatus.SUSCEPTIBLE;

    testPerson.hasBeenTested = false;
    testKit.send(Messages.TestAdministeredMsg.class).to(testPerson);
    TestResult result = testKit.testAction(testPerson, Person.getTested);
    assertThat(result.getMessagesOfType(Messages.InfectionStatusMsg.class)).isNotEmpty();
    assertThat(result.getMessagesOfType(Messages.InfectionStatusMsg.class).get(0).testAccuracy).isEqualTo(1.0);

    testPerson.status = Person.InfectionStatus.INFECTED;
    testPerson.timeInfected = 0;
    testPerson.symptomOnset = 2;
    testPerson.illnessDuration = 6;

    testPerson.hasBeenTested = false;
    testKit.send(Messages.TestAdministeredMsg.class).to(testPerson);
    TestResult result2 = testKit.testAction(testPerson, Person.getTested);
    assertThat(result2.getMessagesOfType(Messages.InfectionStatusMsg.class)).isNotEmpty();
    assertThat(result2.getMessagesOfType(Messages.InfectionStatusMsg.class).get(0).testAccuracy).isEqualTo(1.0);

  }

  @Test
  public void testGenerateConstantIncorrectTests() {
    testKit.getGlobals().testingType = 1;
    testKit.getGlobals().testingFalseNegativePerc = 0.4;
    testKit.getGlobals().testingFalsePositivePerc = 0.8;

    for(int i=0; i<100; i++) {
      testPerson.hasBeenTested = false;
      testKit.send(Messages.TestAdministeredMsg.class).to(testPerson);

      if(i<50) {
        TestResult result = testKit.testAction(testPerson, Person.getTested);
        List<Messages.InfectionStatusMsg> output = result.getMessagesOfType(Messages.InfectionStatusMsg.class);

        assertThat(output).isNotEmpty();
        assertThat(output.get(0).testAccuracy).isWithin(0.0001).of(0.2);
      }
      else {
        testPerson.status = Person.InfectionStatus.INFECTED;
        testPerson.timeInfected = i;
        testPerson.symptomOnset = i*2;
        testPerson.illnessDuration = i*3;

        TestResult result = testKit.testAction(testPerson, Person.getTested);
        List<Messages.InfectionStatusMsg> output = result.getMessagesOfType(Messages.InfectionStatusMsg.class);

        assertThat(output).isNotEmpty();
        assertThat(output.get(0).testAccuracy).isWithin(0.0001).of(0.6);
      }
    }
  }

  @Test
  public void testGenerateVariableincorrectTestsValues() {
    testKit.getGlobals().testingType = 2;
    testKit.getGlobals().testingFalseNegativePerc = 0.5;
    testKit.getGlobals().testingFalsePositivePerc = 0.2;
    testKit.getGlobals().daysAfterInfectionToDetect = 4;
    testPerson.status = Person.InfectionStatus.SUSCEPTIBLE;

    testPerson.hasBeenTested = false;
    testKit.send(Messages.TestAdministeredMsg.class).to(testPerson);

    TestResult non_infected_result = testKit.testAction(testPerson, Person.getTested);
    List<Messages.InfectionStatusMsg> non_infected_output = non_infected_result.getMessagesOfType(Messages.InfectionStatusMsg.class);

    assertThat(non_infected_output).isNotEmpty();
    assertThat(non_infected_output.get(0).testAccuracy).isWithin(0.0001).of(0.8);

    testPerson.status = Person.InfectionStatus.INFECTED;
    testPerson.timeInfected = 0;
    testPerson.symptomOnset = 4;
    testPerson.illnessDuration = 8;

    int[] time = new int[]{0, 3, 7, 8, 10, 12};
    double[] accuracy = new double[]{0.0, 0.3367, 0.5, 0.48, 0.32, 0.0};

    for(int i=0; i<time.length; i++) {
      testKit.getGlobals().tStep = time[i];
      testPerson.hasBeenTested = false;
      testKit.send(Messages.TestAdministeredMsg.class).to(testPerson);

      TestResult result = testKit.testAction(testPerson, Person.getTested);
      List<Messages.InfectionStatusMsg> output = result.getMessagesOfType(Messages.InfectionStatusMsg.class);

      assertThat(output).isNotEmpty();
      assertThat(output.get(0).testAccuracy).isWithin(0.0001).of(accuracy[i]);
    }
  }

  @Test
  public void testGenerateVariableIncorrectTestsShape() {
    testKit.getGlobals().testingType = 2;
    testKit.getGlobals().testingFalseNegativePerc = 0.5;
    testKit.getGlobals().testingFalsePositivePerc = 0.2;
    testKit.getGlobals().daysAfterInfectionToDetect = 4;


    testPerson.status = Person.InfectionStatus.INFECTED;
    testPerson.timeInfected = 0;
    testPerson.symptomOnset = 4;
    testPerson.illnessDuration = 8;

    double prevAccuracy = -1.0;

    // test that the accuracy is increasing until 3 days after symptom start
    for(int i=0; i<=testPerson.symptomOnset+3; i++) {
      testKit.getGlobals().tStep = i;
      testPerson.hasBeenTested = false;
      testKit.send(Messages.TestAdministeredMsg.class).to(testPerson);

      TestResult result = testKit.testAction(testPerson, Person.getTested);
      List<Messages.InfectionStatusMsg> output = result.getMessagesOfType(Messages.InfectionStatusMsg.class);

      assertThat(output).isNotEmpty();
      double currentAccuracy = output.get(0).testAccuracy;
      assertThat(currentAccuracy).isGreaterThan(prevAccuracy);
      prevAccuracy = currentAccuracy;
    }
    assertThat(prevAccuracy).isEqualTo(0.5);

    for(int i=testPerson.symptomOnset+3+1; i<=testPerson.illnessDuration+testKit.getGlobals().daysAfterInfectionToDetect; i++) {
      testKit.getGlobals().tStep = i;
      testPerson.hasBeenTested = false;
      testKit.send(Messages.TestAdministeredMsg.class).to(testPerson);

      TestResult result = testKit.testAction(testPerson, Person.getTested);
      List<Messages.InfectionStatusMsg> output = result.getMessagesOfType(Messages.InfectionStatusMsg.class);

      assertThat(output).isNotEmpty();
      double currentAccuracy = output.get(0).testAccuracy;
      assertThat(currentAccuracy).isLessThan(prevAccuracy);
      prevAccuracy = currentAccuracy;
    }
    assertThat(prevAccuracy).isEqualTo(0.0);

  }

  @Test
  public void testTransmissionOutput() {
    testKit.getGlobals().outputTransmissions = true;
    testKit.getGlobals().tStep = 10;
    testPerson.personID = 10;
    testPerson.timeInfected = 5;
    testPerson.symptomOnset = 8;
    testPerson.illnessDuration = 12;
    testPerson.isAsymptomatic = false;
    testPerson.compSymptomsReport = 0.001;
    testPerson.compQuarantineWhenSymptomatic = 0.002;
    testPerson.complianceMask = 0.003;
    testPerson.complianceIsolating = 0.004;
    testPerson.isSelfIsolatingBecauseOfSymptoms = true;
    testPerson.isSelfIsolatingBecauseOfContactTracing = false;
    testPerson.complianceIsolateWhenContactNotified = 0.005;
    testPerson.compliancePhysicalDistancing = 0.006;
    testPerson.contactRate = 11;
    testPerson.probHostsAdditionalEvent = 0.007;
    testPerson.probAttendsAdditionalEvent = 0.008;

    testKit.send(Messages.YouInfectedSomeoneMsg.class, msg -> {
      msg.infectedByMaskType = Person.MaskType.N95;
      msg.placeType = 2;
      msg.placeId = 123L;
      msg.newlyInfectedAgentId = 25L;
      msg.newlyInfectedMaskType = Person.MaskType.HOMEMADE_CLOTH;
      msg.newlyInfectedCompliancePhysicalDistancing = 0.009;
    }).to(testPerson);
    testKit.send(Messages.YouInfectedSomeoneMsg.class, msg -> {
      msg.infectedByMaskType = Person.MaskType.NONE;
      msg.placeType = 4;
      msg.placeId = 456L;
      msg.newlyInfectedAgentId = 35L;
      msg.newlyInfectedMaskType = Person.MaskType.NONE;
      msg.newlyInfectedCompliancePhysicalDistancing = 0.010;
    }).to(testPerson);
    TestResult result = testKit.testAction(testPerson, Person.infectedSomeoneElseWithCOVID);

    List<Messages.OutputWriterStringMessage> msgs = result.getMessagesOfType(Messages.OutputWriterStringMessage.class);
    assertThat(msgs).hasSize(2);
    assertThat(msgs.get(0).key).isEqualTo("transmissions");
    assertThat(msgs.get(0).value).isEqualTo(
        "10,false,5,8,12,false," +
            testPerson.getClass().toString() +
            ",0.001,0.002,0.003,0.004,true,false,0.005,0.006,11,0.007,0.008," +
            Person.MaskType.N95.ordinal() +
            ",2,123,25," +
            Person.MaskType.HOMEMADE_CLOTH.ordinal() +
            ",0.009"
    );
    assertThat(msgs.get(1).key).isEqualTo("transmissions");
    assertThat(msgs.get(1).value).isEqualTo(
        "10,false,5,8,12,false," +
            testPerson.getClass().toString() +
            ",0.001,0.002,0.003,0.004,true,false,0.005,0.006,11,0.007,0.008," +
            Person.MaskType.NONE.ordinal() +
            ",4,456,35," +
            Person.MaskType.NONE.ordinal() +
            ",0.01"
    );
  }

  @Test
  public void testTransmissionOutput_outputTransmissionDisabled() {
    testKit.getGlobals().outputTransmissions = false;

    testKit.send(Messages.YouInfectedSomeoneMsg.class, msg -> {
      msg.infectedByMaskType = Person.MaskType.N95;
      msg.placeType = 2;
      msg.placeId = 123L;
      msg.newlyInfectedAgentId = 25L;
      msg.newlyInfectedMaskType = Person.MaskType.HOMEMADE_CLOTH;
      msg.newlyInfectedCompliancePhysicalDistancing = 0.009;
    }).to(testPerson);
    TestResult result = testKit.testAction(testPerson, Person.infectedSomeoneElseWithCOVID);

    List<Messages.OutputWriterStringMessage> msgs = result.getMessagesOfType(Messages.OutputWriterStringMessage.class);
    assertThat(msgs).isEmpty();
  }

  @Test
  public void testSuppression() {
    Person p = testKit.addAgent(Person.class, Person::init);
    PlaceInfo firstPlace = PlaceInfo.create("firstPlace", 0);
    PlaceInfo secondPlace = PlaceInfo.create("secondPlace", 0);
    PlaceInfo isolationPlace = PlaceInfo.create("isolationPlace", 0);

    p.isolationPlaceInfos = ImmutableList.of(isolationPlace);
    ImmutableMap.Builder<Integer, List<PlaceInfo>> builder = ImmutableMap.builder();
    builder.put(0, ImmutableList.of(firstPlace));
    builder.put(1, ImmutableList.of(secondPlace));
    p.dailySchedule = Person.DailySchedule.create(builder.build(),
        ImmutableList.of(isolationPlace));

    testKit.getGlobals().tStep = 0;
    testKit.testAction(p, Person.movePerson);

    assertThat(p.getCurrentPlaces().size()).isEqualTo(1);
    assertThat(p.getCurrentPlaces().get(0).placeName()).isEqualTo("firstPlace");

    testKit.getGlobals().tStep = 1;
    p.suppressed = true;
    testKit.testAction(p, Person.movePerson);

    assertThat(p.getCurrentPlaces().size()).isEqualTo(1);
    assertThat(p.getCurrentPlaces().get(0).placeName()).isEqualTo("firstPlace");

    p.suppressed = false;
    testKit.testAction(p, Person.movePerson);

    assertThat(p.getCurrentPlaces().size()).isEqualTo(1);
    assertThat(p.getCurrentPlaces().get(0).placeName()).isEqualTo("secondPlace");
  }

  public void testSetInfected_vaccinated() {
    testKit.getGlobals().vaccineEfficacy = 1.0;
    testPerson.isVaccinated = true;

    testPerson.setInfected();

    assertThat(testPerson.status).isEqualTo(RECOVERED);
  }

  @Test
  public void testSetInfected_ineffectiveVaccine() {
    testKit.getGlobals().vaccineEfficacy = 0.0;
    testPerson.isVaccinated = true;
    testPerson.setInfectionTrajectoryDistribution(
        InfectionTrajectoryDistribution.dummyBuilder()
            .percentageAsymptomaticCases(0)
            .percentageNonSevereSymptomaticCases(0)
            .percentageSevereCases(1)
            .infectiousRangeStart(2)
            .infectiousRangeEnd(2)
            .illnessDurationNonSevereRangeStart(7)
            .illnessDurationNonSevereRangeEnd(7)
            .symptomsOnsetRangeStart(5)
            .symptomsOnsetRangeEnd(5)
            .illnessDurationSevereRangeStart(14)
            .illnessDurationSevereRangeEnd(14)
            .build());

    testPerson.setInfected();

    assertThat(testPerson.status).isEqualTo(INFECTED);
  }

  @Test
  public void testSetInfected_noVaccine() {
    testKit.getGlobals().vaccineEfficacy = 1.0;
    testPerson.isVaccinated = false;
    testPerson.setInfectionTrajectoryDistribution(
        InfectionTrajectoryDistribution.dummyBuilder()
            .percentageAsymptomaticCases(0)
            .percentageNonSevereSymptomaticCases(0)
            .percentageSevereCases(1)
            .infectiousRangeStart(2)
            .infectiousRangeEnd(2)
            .illnessDurationNonSevereRangeStart(7)
            .illnessDurationNonSevereRangeEnd(7)
            .symptomsOnsetRangeStart(5)
            .symptomsOnsetRangeEnd(5)
            .illnessDurationSevereRangeStart(14)
            .illnessDurationSevereRangeEnd(14)
            .build());

    testPerson.setInfected();

    assertThat(testPerson.status).isEqualTo(INFECTED);
  }

  @Test
  public void testReportSuppression_suppressed() {
    testPerson.suppressed = true;

    TestResult result = testKit.testAction(testPerson, Person.reportSuppression);

    List<Messages.SupressionStatusMessage> messages = result.getMessagesOfType(Messages.SupressionStatusMessage.class);
    assertThat(messages).hasSize(1);
    assertThat(messages.get(0).isSuppressed).isTrue();
  }

  @Test
  public void testReportSuppression_active() {
    testPerson.suppressed = false;

    TestResult result = testKit.testAction(testPerson, Person.reportSuppression);

    List<Messages.SupressionStatusMessage> messages = result.getMessagesOfType(Messages.SupressionStatusMessage.class);
    assertThat(messages).hasSize(1);
    assertThat(messages.get(0).isSuppressed).isFalse();
  }

  @Test
  public void testUpdateSuppression_suppressedToActive() {
    testPerson.suppressed = true;

    testKit.send(Messages.SupressionStatusMessage.class, m -> m.isSuppressed = false).to(testPerson);
    testKit.testAction(testPerson, Person.updateSuppression);

    assertThat(testPerson.isSuppressed()).isFalse();
  }

  @Test
  public void testUpdateSuppression_activeToSuppressed() {
    testPerson.suppressed = false;

    testKit.send(Messages.SupressionStatusMessage.class, m -> m.isSuppressed = true).to(testPerson);
    testKit.testAction(testPerson, Person.updateSuppression);

    assertThat(testPerson.isSuppressed()).isTrue();
  }

  @Test(expected = IllegalStateException.class)
  public void testUpdateSuppression_activeToActive() {
    testPerson.suppressed = false;

    testKit.send(Messages.SupressionStatusMessage.class, m -> m.isSuppressed = false).to(testPerson);
    testKit.testAction(testPerson, Person.updateSuppression);
  }

  @Test(expected = IllegalStateException.class)
  public void testUpdateSuppression_suppressedToSuppressed() {
    testPerson.suppressed = true;

    testKit.send(Messages.SupressionStatusMessage.class, m -> m.isSuppressed = true).to(testPerson);
    testKit.testAction(testPerson, Person.updateSuppression);
  }

  @Test
  public void testSetInfected_beforeSimStartAndNotIsolate() {
    testKit.getGlobals().tStep = 0;
    testPerson.timeInfected = -3;
    testPerson.status = Person.InfectionStatus.INFECTED;
    testPerson.compQuarantineWhenSymptomatic = 0.0;
    testPerson.setInfectionTrajectoryDistribution(
        InfectionTrajectoryDistribution.dummyBuilder()
            .percentageAsymptomaticCases(0)
            .percentageNonSevereSymptomaticCases(0)
            .percentageSevereCases(1)
            .infectiousRangeStart(2)
            .infectiousRangeEnd(2)
            .illnessDurationNonSevereRangeStart(7)
            .illnessDurationNonSevereRangeEnd(7)
            .symptomsOnsetRangeStart(2)
            .symptomsOnsetRangeEnd(2)
            .illnessDurationSevereRangeStart(14)
            .illnessDurationSevereRangeEnd(14)
            .build());

    testKit.testAction(testPerson, Person.setupInitialInfectionState);

    assertThat(testPerson.status).isEqualTo(Person.InfectionStatus.INFECTED);
    assertThat(testPerson.timeInfected).isEqualTo(-3);
    assertThat(testPerson.isInfectious()).isTrue();
    assertThat(testPerson.isSymptomatic()).isTrue();
    assertThat(testPerson.illnessDuration).isEqualTo(14 - 3);
    assertThat(testPerson.isSelfIsolatingBecauseOfSymptoms).isFalse();
  }

  @Test
  public void testSetInfected_beforeSimStartAndSelfIsolate() {
    testKit.getGlobals().tStep = 0;
    testPerson.timeInfected = -3;
    testPerson.status = Person.InfectionStatus.INFECTED;
    testPerson.compQuarantineWhenSymptomatic = 1.0;
    testPerson.setInfectionTrajectoryDistribution(
        InfectionTrajectoryDistribution.dummyBuilder()
            .percentageAsymptomaticCases(0)
            .percentageNonSevereSymptomaticCases(0)
            .percentageSevereCases(1)
            .infectiousRangeStart(2)
            .infectiousRangeEnd(2)
            .illnessDurationNonSevereRangeStart(7)
            .illnessDurationNonSevereRangeEnd(7)
            .symptomsOnsetRangeStart(2)
            .symptomsOnsetRangeEnd(2)
            .illnessDurationSevereRangeStart(14)
            .illnessDurationSevereRangeEnd(14)
            .build());

    testKit.testAction(testPerson, Person.setupInitialInfectionState);

    assertThat(testPerson.status).isEqualTo(Person.InfectionStatus.INFECTED);
    assertThat(testPerson.timeInfected).isEqualTo(-3);
    assertThat(testPerson.isInfectious()).isTrue();
    assertThat(testPerson.isSymptomatic()).isTrue();
    assertThat(testPerson.illnessDuration).isEqualTo(14 - 3);
    assertThat(testPerson.isSelfIsolatingBecauseOfSymptoms).isTrue();
  }

  @Test
  public void testSetInfected_beforeSimStartAndNotQuarantineOrder_lowCompliance() {
    testKit.getGlobals().tStep = 0;
    testKit.getGlobals().percInitialInfectedQuarantineOrder = 0.0;
    testPerson.timeInfected = -3;
    testPerson.status = Person.InfectionStatus.INFECTED;
    testPerson.complianceIsolateWhenContactNotified = 0.0;
    testPerson.setInfectionTrajectoryDistribution(
        InfectionTrajectoryDistribution.dummyBuilder()
            .percentageAsymptomaticCases(0)
            .percentageNonSevereSymptomaticCases(0)
            .percentageSevereCases(1)
            .infectiousRangeStart(2)
            .infectiousRangeEnd(2)
            .illnessDurationNonSevereRangeStart(7)
            .illnessDurationNonSevereRangeEnd(7)
            .symptomsOnsetRangeStart(2)
            .symptomsOnsetRangeEnd(2)
            .illnessDurationSevereRangeStart(14)
            .illnessDurationSevereRangeEnd(14)
            .build());

    testKit.testAction(testPerson, Person.setupInitialInfectionState);

    assertThat(testPerson.isSelfIsolatingBecauseOfContactTracing).isFalse();
  }

  @Test
  public void testSetInfected_beforeSimStartAndNotQuarantineOrder_highCompliance() {
    testKit.getGlobals().tStep = 0;
    testKit.getGlobals().percInitialInfectedQuarantineOrder = 0.0;
    testPerson.timeInfected = -3;
    testPerson.status = Person.InfectionStatus.INFECTED;
    testPerson.complianceIsolateWhenContactNotified = 1.0;
    testPerson.setInfectionTrajectoryDistribution(
        InfectionTrajectoryDistribution.dummyBuilder()
            .percentageAsymptomaticCases(0)
            .percentageNonSevereSymptomaticCases(0)
            .percentageSevereCases(1)
            .infectiousRangeStart(2)
            .infectiousRangeEnd(2)
            .illnessDurationNonSevereRangeStart(7)
            .illnessDurationNonSevereRangeEnd(7)
            .symptomsOnsetRangeStart(2)
            .symptomsOnsetRangeEnd(2)
            .illnessDurationSevereRangeStart(14)
            .illnessDurationSevereRangeEnd(14)
            .build());

    testKit.testAction(testPerson, Person.setupInitialInfectionState);

    assertThat(testPerson.isSelfIsolatingBecauseOfContactTracing).isFalse();
  }

  @Test
  public void testSetInfected_beforeSimStartAndQuarantineOrder() {
    testKit.getGlobals().tStep = 0;
    testKit.getGlobals().percInitialInfectedQuarantineOrder = 1.0;
    testPerson.timeInfected = -3;
    testPerson.status = Person.InfectionStatus.INFECTED;
    testPerson.complianceIsolateWhenContactNotified = 1.0;
    testPerson.setInfectionTrajectoryDistribution(
        InfectionTrajectoryDistribution.dummyBuilder()
            .percentageAsymptomaticCases(0)
            .percentageNonSevereSymptomaticCases(0)
            .percentageSevereCases(1)
            .infectiousRangeStart(2)
            .infectiousRangeEnd(2)
            .illnessDurationNonSevereRangeStart(7)
            .illnessDurationNonSevereRangeEnd(7)
            .symptomsOnsetRangeStart(2)
            .symptomsOnsetRangeEnd(2)
            .illnessDurationSevereRangeStart(14)
            .illnessDurationSevereRangeEnd(14)
            .build());

    testKit.testAction(testPerson, Person.setupInitialInfectionState);

    assertThat(testPerson.isSelfIsolatingBecauseOfContactTracing).isTrue();
  }

  /**
   * This main method is here to let us use IntelliJ's debugger.
   * To use it, make a new run configuration with the main class pointed here
   */
  public static void main(String[] args) {
    PersonTest personTest = new PersonTest();
    try {
      personTest.setUp();
      personTest.testSuppression();
    } catch(Exception e) {
      e.printStackTrace();
    }
  }
}

