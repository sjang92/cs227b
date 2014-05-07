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
	//	System.err.println(moves);
		roleIndices = getStateMachine().getRoleIndices();
		myRole = getRole();
		MonteCarloNode root = new MonteCarloNode(getCurrentState(), true, null, null);
		Move bestMove= moves.get(0);


		while (/*enough time*/ System.currentTimeMillis() < timeout - 1000) {// idk wtf i'm doing here -kev
			if (moves.size() == 1) break;

			//selection
			MonteCarloNode selection = select(root);
			//expansion
			if (selection.isMax) expandMax(selection);
				else expandMin(selection);
			// simulation
			int terminalValue = simulateToTerminal(selection);
			// back-propagation
			backpropagate(selection, terminalValue);

			// determining best move with current tree
			double topChildScore = 0;
			MonteCarloNode topChild = null;
			for (MonteCarloNode child : root.getChildren()) {
				if (child.getAverageUtility() > topChildScore) {
					topChildScore = child.getAverageUtility();
					topChild = child;
			//		System.err.println("UPDATED TOP CHILD");
				}
			}
			if (topChild != null) {
				bestMove = topChild.moveIfMin;
			//	System.err.println("UPDATED BEST MOVE");
			}
		}


		// We get the end time
		// It is mandatory that stop<timeout
		long stop = System.currentTimeMillis();

		/**
		 * These are functions used by other parts of the GGP codebase
		 * You shouldn't worry about them, just make sure that you have
		 * moves, selection, stop and start defined in the same way as
		 * this example, and copy-paste these two lines in your player
		 */
		System.err.println(bestMove);
		notifyObservers(new GamerSelectedMoveEvent(moves, bestMove, stop - start));
		return bestMove;
	}

	public void expandMax(MonteCarloNode node) throws MoveDefinitionException, TransitionDefinitionException {
		List<Move> myLegalMoves = getStateMachine().getLegalMoves(node.getState(), getRole());
		for (Move move : myLegalMoves) {
			MonteCarloNode newNode = new MonteCarloNode(node.getState(), false, move, node);
			node.addChild(newNode);
			//expandMin(newNode, move); // we don't want to create all the grandchildren
		}
	}

	// adds the
	public void expandMin(MonteCarloNode node) throws MoveDefinitionException, TransitionDefinitionException {
		Move myMove = node.moveIfMin;
		List<List<Move> > jointLegalMoves = getStateMachine().getLegalJointMoves(node.getParent().getState(), myRole, myMove);

		for (List<Move> jointLegalMove : jointLegalMoves) {
			Move currMove = jointLegalMove.get(roleIndices.get(myRole));
			if (currMove.equals(myMove)) {
				MachineState newState = getStateMachine().getNextState(node.getParent().getState(), jointLegalMove);
				MonteCarloNode newNode = new MonteCarloNode(newState, true, null, node);
				node.addChild(newNode);
			}
		}
	}

	public int simulateToTerminal(MonteCarloNode node) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		StateMachine theMachine = getStateMachine();
		int depth[] = new int[1];
		MachineState finalState;
		if (node.isMax) {
			finalState = theMachine.performDepthCharge(node.getState(), depth);
		} else { // isMin
			MachineState nextState = theMachine.getRandomNextState(node.getState(), myRole, node.moveIfMin);
			finalState = theMachine.performDepthCharge(nextState, depth);
		}
		//MachineState finalState = theMachine.performDepthCharge(theMachine.getRandomNextState(node.getState(), getRole(), theMachine.getRandomMove(node.getState(), myRole)), depth);
		return theMachine.getGoal(finalState, myRole);

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

	private boolean backpropagate(MonteCarloNode node, int reward) {
		node.setNumVisits(node.getNumVisits() + 1);
		node.setUtility(node.getUtility() + reward);
		if (node.getParent() != null) backpropagate(node.getParent(), reward);
		return true;
	}
}