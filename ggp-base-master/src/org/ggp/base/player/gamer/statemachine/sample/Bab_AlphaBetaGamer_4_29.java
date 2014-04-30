package org.ggp.base.player.gamer.statemachine.sample;

import java.util.List;
import java.util.Map;

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
public final class Bab_AlphaBetaGamer_4_29 extends SampleGamer
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
		// We get the current start time
		long start = System.currentTimeMillis();

		/**
		 * We put in memory the list of legal moves from the
		 * current state. The goal of every stateMachineSelectMove()
		 * is to return one of these moves. The choice of which
		 * Move to play is the goal of GGP.
		 */
		List<Move> moves = getStateMachine().getLegalMoves(getCurrentState(), getRole());

		// SampleLegalGamer is very simple : it picks the first legal move
		Move selection = bestMove(getRole(), getCurrentState());

		// We get the end time
		// It is mandatory that stop<timeout
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

	public Move bestMove(Role role, MachineState state) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		List<Move> legalMoves = getStateMachine().getLegalMoves(state, role);
		Move move = legalMoves.get(0);
		int score = 0;
		for (Move currMove : legalMoves) {
			int result = minScore(role, currMove, state, 0, 100);
			if (result == 100) return currMove;
			if (result > score) {
				score = result;
				move = currMove;
			}
		}
		return move;
	}

	public int minScore(Role role, Move move, MachineState state, int alpha, int beta) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {

		List<List<Move> > jointLegalMoves = getStateMachine().getLegalJointMoves(state, role, move);
		Map<Role, Integer> roleIndices = getStateMachine().getRoleIndices();

		for (List<Move> jointLegalMove : jointLegalMoves) {
			Move currMove = jointLegalMove.get(roleIndices.get(role));
			if (currMove.equals(move)) {
				MachineState newState = getStateMachine().getNextState(state, jointLegalMove);
				int result = maxScore(role, newState, alpha, beta);
				if (result == 0) return 0;
				beta = beta < result ? beta : result;
				if (beta <= alpha) return alpha;
			}
		}
		return beta;
	}


	public int maxScore(Role role, MachineState state, int alpha, int beta) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException {

		if (getStateMachine().isTerminal(state))
			return getStateMachine().getGoal(state, role);
		else {
			List<Move> myLegalMoves = getStateMachine().getLegalMoves(state, role);
			for (Move myLegalMove : myLegalMoves) {
				int result = minScore(role, myLegalMove, state, alpha, beta);
				if (result == 100) return result;
				alpha = alpha > result ? alpha : result;
				if (alpha >= beta) return beta;
			}
			return alpha;
		}

	}
}