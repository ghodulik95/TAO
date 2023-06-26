package core;

import simudyne.core.abm.Action;
import simudyne.core.abm.Agent;

import java.util.function.Consumer;

/**
 * Util class for alternative Actions
 *
 * Currently only provides one alternative Action type: suppressibleAction
 * A suppressibleAction is created for an agent that implements Suppressible
 * and returns without doing anything if the agent is suppressed.
 */
public final class ActionFactory {
  private ActionFactory() {}

  public static <T extends Agent<Globals>> Action<T> createSuppressibleAction(Class<T> clazz, Consumer<T> runnableAction) {
    return Action.create(clazz, agent -> {
      if(!(agent instanceof Suppressible)) {
        throw new IllegalStateException("Only a suppressible agent should create suppressed actions.");
      }
      if(((Suppressible)agent).isSuppressed()) {
        return;
      }
      runnableAction.accept(agent);
    });
  }
}