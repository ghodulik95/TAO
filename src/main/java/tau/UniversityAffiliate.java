package tau;

import core.Person;
import core.PlaceInfo;

import static tau.TAUModel.PlaceType.FITNESS;

public class UniversityAffiliate extends Person {
  public int fitnessTimesPerWeek;

  @Override
  public void init() {
    super.init();

    fitnessTimesPerWeek = getFitnessTimesPerWeek();
  }

  public int getFitnessTimesPerWeek() {
    return getGlobals().getFitnessTimesPerWeek(this).get();
  }

  @Override
  protected boolean isAttendingToday(PlaceInfo placeInfo) {
    if (placeInfo.placeType() == FITNESS.ordinal()) {
      if (getGlobals().closeFitnessCenter) {
        return false;
      }
      return getPrng().uniform(0, 1).sample() < (1.0 / fitnessTimesPerWeek);
    }
    return super.isAttendingToday(placeInfo);
  }
}
