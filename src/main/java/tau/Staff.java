package tau;

import simudyne.core.rng.SeededRandom;

public class Staff extends UniversityAffiliate {
  public boolean isStaffWithStudentFacingJob;

  @Override
  public void initialiseFirstPlace() {
    this.decideNextLocation();
  }

  @Override
  public double getTestSelectionMultiplier() {
    return (isStaffWithStudentFacingJob ? getGlobals().studentFacingStaffTestMultiplier : 1)
        * super.getTestSelectionMultiplier();
  }

  @Override
  public PersonInitializationInfo initializationInfo() {
    SeededRandom random = getPrng();
    double suppressionPerc = 0.0;
    if(getGlobals().suppressAgentType == 0) {
      suppressionPerc = 1.0 - (getGlobals().nActiveAgents / (double)getGlobals().nAgents);
    }
    boolean suppressed = getPrng().uniform(0.0,1.0).sample() < suppressionPerc;
    return PersonInitializationInfo.builderSetWithGlobalDefaults(getGlobals(), random)
        .ageSupplier(
            PersonInitializationInfo.truncNormal(
                getGlobals().facultyStaffAgentAgeStart,
                getGlobals().facultyStaffAgentAgeEnd,
                getGlobals().facultyStaffAgentAgeMean,
                getGlobals().facultyStaffAgentAgeSD,
                random))
        .maskComplianceSupplier(
            PersonInitializationInfo.uniform(
                getGlobals().facultyStaffAgentMaskComplianceStart,
                getGlobals().facultyStaffAgentMaskComplianceEnd,
                random))
        .quarantineWhenSymptomaticComplianceSupplier(
            PersonInitializationInfo.uniform(
                getGlobals().facultyStaffAgentQuarantineWhenSymptomaticComplianceStart,
                getGlobals().facultyStaffAgentQuarantineWhenSymptomaticComplianceEnd,
                random))
        // TODO#82 Add faculty/staff/student infection distribution
        .symptomsReportComplianceSupplier(
            PersonInitializationInfo.uniform(
                getGlobals().facultyStaffAgentReportSymptomsComplianceStart,
                getGlobals().facultyStaffAgentReportSymptomsComplianceEnd,
                random))
        .isolationComplianceSupplier(
            PersonInitializationInfo.uniform(
                getGlobals().facultyStaffAgentIsolationComplianceStart,
                getGlobals().facultyStaffAgentIsolationComplianceEnd,
                random))
        .isolateWhenContactNotifiedSupplier(
            PersonInitializationInfo.uniform(
                getGlobals().facultyStaffAgentComplianceIsolateWhenContactNotifiedStart,
                getGlobals().facultyStaffAgentComplianceIsolateWhenContactNotifiedEnd,
                random))
        .compliancePhysicalDistancingSupplier(
            PersonInitializationInfo.uniform(
                getGlobals().facultyStaffAgentCompliancePhysicalDistancingStart,
                getGlobals().facultyStaffAgentCompliancePhysicalDistancingtEnd,
                random))
        .probGoesToOptionalPlaceSupplier(
            PersonInitializationInfo.uniform(
                getGlobals().facultyStaffAgentProbGoesToOptionalPlaceStart,
                getGlobals().facultyStaffAgentProbGoesToOptionalPlaceEnd,
                random))
        .probHostsAdditionalPlaceSupplier(
            PersonInitializationInfo.uniform(
                getGlobals().facultyStaffAgentThrowsPartyStart,
                getGlobals().facultyStaffAgentThrowsPartyEnd,
                random))
        .probAttendsAdditionalPlaceSupplier(
            PersonInitializationInfo.uniform(
                getGlobals().facultyStaffAgentAttendsPartyStart,
                getGlobals().facultyStaffAgentAttendsPartyEnd,
                random))
            .suppressionSupplier(() -> suppressed)
        // TODO#83 Add faculty/staff/student contact rate distribution
        .build();
  }

}
