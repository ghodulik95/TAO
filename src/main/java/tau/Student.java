package tau;

import com.google.common.collect.ImmutableList;
import core.PlaceInfo;
import simudyne.core.rng.SeededRandom;
import tau.anylogic_code.StudentType;

import java.util.Optional;

public class Student extends UniversityAffiliate {

  public StudentType type;
  public boolean isPartTime;
  public boolean livesOnCampus;
  public String livesAtBuilding = null;
  private Optional<PlaceInfo> homePlaceInfo = Optional.empty();
  public double attendsSportsEventPerc;

  public void setHomePlaceInfo(PlaceInfo home) {
    this.homePlaceInfo = Optional.of(home);
  }

  @Override
  public void initialiseFirstPlace() {
    this.decideNextLocation();
    this.dailySchedule.placesAtStepMap().forEach(
        (step, places) -> {
          homePlaceInfo = places.stream()
              .filter(place -> place.placeType() == TAUModel.PlaceType.SUITE.ordinal())
              .findFirst();
        });
  }

  @Override
  public PersonInitializationInfo initializationInfo() {
    UniversityConfiguration universityConfiguration = getGlobals().getUniversityConfiguration();
    attendsSportsEventPerc = universityConfiguration.percStudentsWhoAttendSportsEvent();
    double suppressionPerc = 0.0;
    if(getGlobals().suppressAgentType == 0) {
      suppressionPerc = 1.0 - (getGlobals().nActiveAgents / (double)getGlobals().nAgents);
    }
    else if(getGlobals().suppressAgentType == 1) {
      int numStudents = universityConfiguration.numStudents();
      int numActiveStudents = numStudents - (getGlobals().nAgents - getGlobals().nActiveAgents);
      if(numActiveStudents < 0) {
        throw new IllegalStateException("There aren't enough active agents for there to be any students. ("+numActiveStudents+" out of "+numStudents+")");
      }
      suppressionPerc = 1.0 - (numActiveStudents / (double)numStudents);
    }
    boolean suppressed = getPrng().uniform(0.0,1.0).sample() < suppressionPerc;

    SeededRandom random = getPrng();
    return PersonInitializationInfo.builderSetWithGlobalDefaults(getGlobals(), random)
        .ageSupplier(
            PersonInitializationInfo.uniform(
                getGlobals().studentAgentAgeStart, getGlobals().studentAgentAgeEnd, random))
        .maskComplianceSupplier(
            PersonInitializationInfo.uniform(
                getGlobals().studentAgentMaskComplianceStart,
                getGlobals().studentAgentMaskComplianceEnd,
                random))
        .quarantineWhenSymptomaticComplianceSupplier(
            PersonInitializationInfo.uniform(
                getGlobals().studentAgentQuarantineWhenSymptomaticComplianceStart,
                getGlobals().studentAgentQuarantineWhenSymptomaticComplianceEnd,
                random))
        // TODO#82 Add faculty/staff/student infection distribution
        .symptomsReportComplianceSupplier(
            PersonInitializationInfo.uniform(
                getGlobals().studentAgentReportSymptomsComplianceStart,
                getGlobals().studentAgentReportSymptomsComplianceEnd,
                random))
        .isolationComplianceSupplier(
            PersonInitializationInfo.uniform(
                getGlobals().studentAgentIsolationComplianceStart,
                getGlobals().studentAgentIsolationComplianceEnd,
                random))
        .isolateWhenContactNotifiedSupplier(
            PersonInitializationInfo.uniform(
                getGlobals().studentAgentComplianceIsolateWhenContactNotifiedStart,
                getGlobals().studentAgentComplianceIsolateWhenContactNotifiedEnd,
                random))
        .compliancePhysicalDistancingSupplier(
            PersonInitializationInfo.uniform(
                getGlobals().studentAgentCompliancePhysicalDistancingStart,
                getGlobals().studentAgentCompliancePhysicalDistancingtEnd,
                random))
        .probGoesToOptionalPlaceSupplier(
            PersonInitializationInfo.uniform(
                getGlobals().studentAgentProbGoesToOptionalPlaceStart,
                getGlobals().studentAgentProbGoesToOptionalPlaceEnd,
                random))
        .probHostsAdditionalPlaceSupplier(
            PersonInitializationInfo.uniform(
                getGlobals().studentAgentThrowsPartyStart,
                getGlobals().studentAgentThrowsPartyEnd,
                random))
        .probAttendsAdditionalPlaceSupplier(
            PersonInitializationInfo.uniform(
                getGlobals().studentAgentAttendsPartyStart,
                getGlobals().studentAgentAttendsPartyEnd,
                random))
        .usesPublicTransitFunction(person -> {
          if (((Student) person).livesAtBuilding == null) {
            return person.getPrng().binomial(1, getGlobals().percOffCampusStudentsWhoUsePublicTransit).sample() == 1;
          }
          return false;
        })
        .suppressionSupplier(() -> suppressed)
        // TODO#83 Add faculty/staff/student contact rate distribution
        .build();
  }

  @Override
  protected ImmutableList<PlaceInfo> generateAdditionalPlace() {
    return (this.homePlaceInfo.isPresent()) ?
        ImmutableList.of(this.homePlaceInfo.get()) : ImmutableList.of();
  }

  @Override
  public ImmutableList<PlaceInfo> getCurrentPlaces() {
    long sportEventsToday = this.currentPlaceInfos.stream()
            .filter(placeInfo -> placeInfo.placeType()
                    == TAUModel.PlaceType.SPORT_EVENT.ordinal())
            .count();

    if(sportEventsToday == 0) {
      return super.getCurrentPlaces();
    }

    double attendsSportsEvent = getPrng().uniform(0,1.0).sample();
    if(getGlobals().cancelSportEvents || attendsSportsEvent > attendsSportsEventPerc) {
      return ImmutableList.<PlaceInfo>builder()
              .addAll(this.currentPlaceInfos.stream()
                      .filter(placeInfo -> placeInfo.placeType() != TAUModel.PlaceType.SPORT_EVENT.ordinal())
                      .iterator())
              .addAll(this.additionalPlaceInfos)
              .build();
    }

    return super.getCurrentPlaces();
  }
}
