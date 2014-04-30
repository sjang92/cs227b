package org.ggp.base.player.gamer.statemachine.sample;

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
public final class BaB_AlphaBeta_DepthLimit_Gamer_4_29 extends SampleGamer
{

	@Override
	public void stateMachineMetaGame(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
	{
		// Sample gamers do no metagaming at the beginning of the match.
	}

	private long start;
	private long stop;
	private long myTimeout;
	private final int DEPTH_LIMIT = 10000000;

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
		start = System.currentTimeMillis();
		myTimeout = timeout;

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
		stop = System.currentTimeMillis();

		/**
		 * These are functions used by other parts of the GGP codebase
		 * You shouldn't worry about them, just make sure that you have
		 * moves, selection, stop and start defined in the same way as
		 * this example, and copy-paste these two lines in your player
		 */
		notifyObservers(new GamerSelectedMoveEvent(moves, selection, stop - start));
		return selection;
	}


	private int bestMoveScore = 0;
	private Move bestMove = null;
	private int currLevelBestMoveScore = 0; // for each layer of iterative-deepening
	private Move currLevelBestMove = null;
	private int minMovesSeen = 10000000;
	private int maxMovesSeen = 0;


	private int HEURISTIC = 1; // 1=mobility, 2=focus, 3=goal proximity; anything else defaults to terminal depth i.e. 0


	public Move bestMove(Role role, MachineState state) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		List<Move> legalMoves = getStateMachine().getLegalMoves(state, role);
		updateMinMaxSeen(legalMoves);
		Move move = legalMoves.get(0);
		int score = 0;
		bestMove = move;
		for (int i = 1; i < DEPTH_LIMIT; i++) { // wrapped with layers of ID (iterative-deepening)
			currLevelBestMoveScore = 0;
			currLevelBestMove = move;
			for (Move currMove : legalMoves) {
				int result = minScore(i, role, currMove, state, 0, 0, 100);
				if (result == 100) return currMove;
				if (result > score) {
					score = result;
					move = currMove;
				}
				updateCurrLevelBestMove(move, score);
			}
			bestMove = currLevelBestMove;
			bestMoveScore = currLevelBestMoveScore;
			if (timeoutCheck() || bestMoveScore == 100) return bestMove;

		}
		return bestMove; // should not be reached
	}

	public int minScore(int IDdepth, Role role, Move move, MachineState state, int level, int alpha, int beta) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		if (timeoutCheck()) return 0;

		// returns all the joint-moves given the "role"'s move
		List<List<Move> > jointLegalMoves = getStateMachine().getLegalJointMoves(state, role, move);

		for (List<Move> jointLegalMove : jointLegalMoves) {
				MachineState newState = getStateMachine().getNextState(state, jointLegalMove);
				int result = maxScore(IDdepth, role, newState, level+1, alpha, beta);
				if (result == 0) return 0;
				beta = beta < result ? beta : result;
				if (beta <= alpha) return alpha;
				//updateCurrLevelBestMove(move, beta);
		}
		return beta;
	}

	public int maxScore(int IDdepth, Role role, MachineState state, int level, int alpha, int beta) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException {
		if (timeoutCheck()) return 100;

		if (getStateMachine().isTerminal(state))
			return getStateMachine().getGoal(state, role);

		// bail w/ heuristic if at deepest layer for this round
		else if (level >= IDdepth) {
			return heuristicEval(role, state);

		} else {
			List<Move> myLegalMoves = getStateMachine().getLegalMoves(state, role);
			updateMinMaxSeen(myLegalMoves);
			for (Move myLegalMove : myLegalMoves) {
				int result = minScore(IDdepth, role, myLegalMove, state, level, alpha, beta);
				if (result == 100) return result;
				alpha = alpha > result ? alpha : result;
				if (alpha >= beta) return beta;
			}
			return alpha;
		}

	}


	// checks how much time left, constant should be in millisecs
	private boolean timeoutCheck() {
		if ((System.currentTimeMillis() - start) >= (myTimeout - 2000)) return true;
		return false;
	}

/*
	private void updateBestMove(Move move, int score) {
		if (score > bestMoveScore) {
			bestMove = move;
			bestMoveScore = score;
		}
	}
*/

	// for each layer of iterative-deepening
	private void updateCurrLevelBestMove(Move move, int score) {
		if (score > currLevelBestMoveScore) {
			currLevelBestMove = move;
			currLevelBestMoveScore = score;
		}
	}

	// for the mobility and focus heuristics
	private void updateMinMaxSeen(List<Move> legalMoves) {
		int movesAvailable = legalMoves.size();
		if (movesAvailable > maxMovesSeen) maxMovesSeen = movesAvailable;
		if (movesAvailable < minMovesSeen) minMovesSeen = movesAvailable;
	}


	// choice is a flag above in the constants area
	public int heuristicEval(Role role, MachineState state) throws MoveDefinitionException, GoalDefinitionException {
		if (HEURISTIC == 1) return mobilityHeuristic(role, state);
		if (HEURISTIC == 2) return focusHeuristic(role, state);
		if (HEURISTIC == 3) return goalProximityHeuristic(role, state);
		return 0;
	}

	// scales the # moves available to lie between the min/max seen
	// more moves comes out better
	public int mobilityHeuristic(Role role, MachineState state) throws MoveDefinitionException {
		List<Move> legalMoves = getStateMachine().getLegalMoves(state, role);
		int numMoves = legalMoves.size();
		return (((numMoves - minMovesSeen)/(maxMovesSeen - minMovesSeen)+1) * 100); //+1 avoid DBZ
	}

	// scales the # moves available to lie between the min/max seen
	// less moves comes out better
	public int focusHeuristic(Role role, MachineState state) throws MoveDefinitionException {
		List<Move> legalMoves = getStateMachine().getLegalMoves(state, role);
		int numMoves = legalMoves.size();
		return (100 - ((numMoves - minMovesSeen)/(maxMovesSeen - minMovesSeen)+1) * 100); //+1 avoid DBZ
	}

	// very simple/ rudimentary
    public int goalProximityHeuristic(Role role, MachineState state) throws GoalDefinitionException {
		return getStateMachine().getGoal(state, role);
	}
}