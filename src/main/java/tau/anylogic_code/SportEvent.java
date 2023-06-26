package tau.anylogic_code;

import java.io.Serializable;

/**
 * SportEvent
 */
public class SportEvent extends ConnectionOfAgents implements Serializable {

  private final double chanceExternalInfection;
  private final double numStudentsAtEvent;

  /**
   * Default constructor
   */
  public SportEvent(double chanceExternalInfection, double numStudentsAtEvent, long uniqueId) {
    // For simplicity, sport events only happen once a week.
    super(7, uniqueId);
    this.chanceExternalInfection = chanceExternalInfection;
    this.numStudentsAtEvent = numStudentsAtEvent;
  }

  @Override
  public String toString() {
    return super.toString();
  }

  /**
   * Assuming the simulation starts on Monday, SportEvents happen on Saturdays
   */
  @Override
  protected int dayOffset() { return 5; }

  /**
   * This number is here for model snapshot storing purpose<br>
   * It needs to be changed when this class gets changed
   */
  private static final long serialVersionUID = 1L;
}
