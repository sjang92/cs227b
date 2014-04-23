package org.ggp.base.player.gamer.statemachine.sample;

import java.util.ArrayList;
import java.util.List;

import org.ggp.base.player.gamer.event.GamerSelectedMoveEvent;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

/**
 * SampleLegalGamer is a minimal gamer which always plays the first
 * legal move it identifies, regardless of the state of the game.
 *
 * For your first players, you should extend the class SampleGamer
 * The only function that you are required to override is :
 * public Move stateMachineSelectMove(long timeout)
 *
 */
public final class BaB_DeliberationGamer extends SampleGamer
{
	/**
	 * This function is called at the start of each round
	 * You are required to return the Move your player will play
	 * before the timeout.
	 *
	 */
	@Override
	public Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
	{
		long start = System.currentTimeMillis();
		List<Move> moves = getStateMachine().getLegalMoves(getCurrentState(), getRole());
		Move selection = bestMove(getRole(), getCurrentState());
		long stop = System.currentTimeMillis();
		/**
		 * These are functions used by other parts of the GGP codebase
		 * You shouldn't worry about them, just make sure that you have
		 * moves, selection, stop and start defined in the same way as
		 * this example, and copy-paste these two lines in your player
		 */
		notifyObservers(new GamerSelectedMoveEvent(moves, selection, stop - start));
		return selection;
	}

	private Move bestMove(Role role, MachineState state) throws MoveDefinitionException, GoalDefinitionException, TransitionDefinitionException {
		List<Move> legalMoves = getStateMachine().getLegalMoves(state, role);
		Move move = legalMoves.get(0);
		int score = 0;

		for (Move legalMove : legalMoves) {
			List<Move> dummyList = new ArrayList<Move>();
			dummyList.add(legalMove);
			int result = maxscore(role, getStateMachine().getNextState(state, dummyList));
			if (result == 100) return legalMove;
			if (result > score) {
				score = result;
				move = legalMove;
			}
		}
		return move;
	}

	private int maxscore(Role role, MachineState state) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException {
		if (getStateMachine().isTerminal(state)) return getStateMachine().getGoal(state, role);

		List<Move> legalMoves = getStateMachine().getLegalMoves(state, role);
		int score = 0;

		for (Move legalMove : legalMoves) {
			List<Move> dummyList = new ArrayList<Move>();
			dummyList.add(legalMove);
			int result = maxscore(role, getStateMachine(). getNextState(state, dummyList));
			if (result > score) score = result;
		}
		return score;
	}
}