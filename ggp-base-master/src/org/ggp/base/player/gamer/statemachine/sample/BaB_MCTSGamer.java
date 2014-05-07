package org.ggp.base.player.gamer.statemachine.sample;

import java.util.List;
import java.util.Map;

import org.ggp.base.player.gamer.event.GamerSelectedMoveEvent;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;


public final class BaB_MCTSGamer extends SampleGamer
{
	/**
	 * This function is called at the start of each round
	 * You are required to return the Move your player will play
	 * before the timeout.
	 *
	 */
	private Map<Role, Integer> roleIndices;
	private Role myRole;

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
		roleIndices = getStateMachine().getRoleIndices();
		myRole = getRole();
		// SampleLegalGamer is very simple : it picks the first legal move
		Move selection = moves.get(0);

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

	public void expandMax(MonteCarloNode node) throws MoveDefinitionException, TransitionDefinitionException {
		List<Move> myLegalMoves = getStateMachine().getLegalMoves(node.getState(), getRole());
		for (Move move : myLegalMoves) {
			MonteCarloNode newNode = new MonteCarloNode(null, false, node);
			node.addChild(newNode);
			expandMin(newNode, move);
		}
	}

	public void expandMin(MonteCarloNode node, Move myMove) throws MoveDefinitionException, TransitionDefinitionException {
		List<List<Move> > jointLegalMoves = getStateMachine().getLegalJointMoves(node.getParent().getState(), myRole, myMove);

		for (List<Move> jointLegalMove : jointLegalMoves) {
			Move currMove = jointLegalMove.get(roleIndices.get(myRole));
			if (currMove.equals(myMove)) {
				MachineState newState = getStateMachine().getNextState(node.getParent().getState(), jointLegalMove);
				MonteCarloNode newNode = new MonteCarloNode(newState, true, node);
				node.addChild(newNode);
			}
		}
	}

	int performDepthChargeFromMove(MachineState theState, Move myMove) {
	    StateMachine theMachine = getStateMachine();
	    int depth[];
	    try {
            MachineState finalState = theMachine.performDepthCharge(theMachine.getRandomNextState(theState, getRole(), myMove), depth);
            return theMachine.getGoal(finalState, getRole());
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
	}

	public int simulateToTerminal(MonteCarloNode node) {
		StateMachine theMachine = getStateMachine();
		List<Moves>

		MachineState finalState = theMachine.performDepthCharge(theMachine.getRandomNextState(theState, getRole(), )
		return theMachine.getGoal(finalState, myRole);
		return 0;
	}

	public MonteCarloNode select(MonteCarloNode node) {
		if (node.getNumVisits() == 0) return node;
		else {
			for (MonteCarloNode childNode : node.getChildren()) {
				if (childNode.getNumVisits() == 0) return childNode;
			}

			double score = 0;
			MonteCarloNode resultNode = node;
			for (MonteCarloNode childNode : node.getChildren()) {
				double newScore = childNode.getSelectFnResult();
				if (newScore > score) {
					score = newScore;
					resultNode = childNode;
				}
			}
			return resultNode;
		}
	}
}