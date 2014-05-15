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
	private int TIME_BUFFER = 1000;

	@Override
	public Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
	{
		/* We get the current start time */
		long start = System.currentTimeMillis();

		/* Initialize Variables */
		List<Move> moves = getStateMachine().getLegalMoves(getCurrentState(), getRole());
		roleIndices = getStateMachine().getRoleIndices();
		myRole = getRole();
		MonteCarloNode root = new MonteCarloNode(getCurrentState(), true, null, null);
		Move bestMove= moves.get(0);

		/* Case 1) You have only one move anyways, so just make the move right away. */
		if (moves.size() == 1) {
			long stop = System.currentTimeMillis();
			notifyObservers(new GamerSelectedMoveEvent(moves, bestMove, stop - start));
			return bestMove;
		}

		/* Case 2) You have more than one move that you can make. Perform MCTS */
		while (System.currentTimeMillis() < timeout - TIME_BUFFER) {

			/* MCTS Routine: Select -> Expand -> Simulate -> Backpropagate */
			MonteCarloNode selection = select(root);
			//System.out.print("Selected Node is Max? : " + selection.isMax+"\n"); // should always be max? no... (Kev: no, sometimes min)
			expandGeneral(selection);
			int terminalValue = simulateToTerminal(selection);
			backpropagate(selection, terminalValue);

			System.out.print("=================Round Complete=============\n");
		}

		System.out.print("Escaped the MCTS loop. \n ");

		/* Get Best Move from the MCTS result tree */
		if (moves.size() != 1) bestMove = getBestMove(root);

		/* Return move to the server */
		long stop = System.currentTimeMillis();
		notifyObservers(new GamerSelectedMoveEvent(moves, bestMove, stop - start));
		return bestMove;
	}

	public Move getBestMove(MonteCarloNode root) {
		Move bestMove = null;
		double bestScore = 0;
		for (MonteCarloNode child : root.getChildren()) {
			if (child.originMove == null) System.err.print("Child of root node doesn't have a move associated with it.");

			if (child.getAverageUtility() > bestScore) {
				bestScore = child.getAverageUtility();
				bestMove = child.originMove;
			}
		}
		return bestMove;
	}

	/* ==========================================================================================
	 * ==========================================================================================*/
	/* KEY FUNCTION IMPLEMENTATION
	 * ==========================================================================================
	 * ==========================================================================================*/

	public MonteCarloNode select(MonteCarloNode node) {

		/* Escape Statement: the current node has not been visited || has no children */
		if (node.getNumVisits() == 0 || node.getNumChildren() == 0) {
			System.out.print("Recursion base case reached\n");
			return node;
		}
		else {

			/* Check if all child nodes have been visited */
			for (MonteCarloNode childNode : node.getChildren()) {
				if (childNode.getNumVisits() == 0) {
					System.out.print("Recursion base case reached\n");
					return childNode;
				}
			}
			double score = Integer.MAX_VALUE;
			if (node.isMax) score = 0; //node passed in is max, looking for maximum among min-children
			MonteCarloNode resultNode = null;
			//System.out.print("Size of children arrray: " + node.getChildren().size() + "\n");

			/* Traverse through the children array and get the bestNode. */
			for (MonteCarloNode childNode : node.getChildren()) {
				double newScore = childNode.getSelectFnResult();

				if (node.isMax && newScore >= score) {  //node passed in is max, looking for maximum among min-children
					resultNode = childNode;
					score = newScore;
				}
				if (!node.isMax && newScore <= score) { // node passed in is min, looking for minimum among max-children
					resultNode = childNode;
					score = newScore;
				}
			}

			if (resultNode == null) {
				System.out.print("CHECK");
				return node.getChildAtIndex(0);
			}

			//System.out.print("~~~recursive call to select~~~\n");
			return select(resultNode);
		}
	}



	/* Function: expandMax
	 * ==========================================================================================
	 * Expand from a max node. This would expand given the choices of the legal moves that I have.
	 * However, since we need both opponents moves and my moves to reach a new state, the min nodes
	 * that are created from this function call do not have states.
	 * */
	public void expandMax(MonteCarloNode node) throws MoveDefinitionException, TransitionDefinitionException {
		System.out.print("EXPAND MAX CALLED!! \n");

		if (getStateMachine().isTerminal(node.getState()))
			return;
		if (node.getNumChildren() != 0) { // does this have a purpose? why would expandMax ever be called on a node w/ children?
			return;
		}

		/* Get all my legal moves */
		List<Move> myLegalMoves = getStateMachine().getLegalMoves(node.getState(), getRole());

		/* Iterate through all the legal moves that I can take*/
		for (Move move : myLegalMoves) {

			/* Create new min node and append it to the max node we started from. */
			MonteCarloNode newNode = new MonteCarloNode(null, false, move, node); // no states + not maxnode + the move that I made + parent
			node.addChild(newNode);
			//expandMin(newNode); // we don't want to create all the grandchildren?? (Kev: we do not create any grandkids)
		}
	}

	/* Function: expandMin
	 * ==========================================================================================
	 * Expand from a min node. This would expand given a min node, all the max nodes that can come out of it.
	 * This is done by getting the initial move that resulted in the min node, and then getting all the joint moves
	 * that opponents can make from the given move.
	 */
	public void expandMin(MonteCarloNode node) throws MoveDefinitionException, TransitionDefinitionException {
		System.out.print("EXPAND MIN CALLED!!\n");
		if (node.getNumChildren() != 0) { // does this have a purpose? why would expandMax ever be called on a node w/ children?
			return;
		}

		/* Get prev my Move */
		Move myMove = node.originMove;

		/* Get all combinations of myMove and opponents move */
		List<List<Move> > jointLegalMoves = getStateMachine().getLegalJointMoves(node.getParent().getState()/*, myRole, myMove*/);

		/* Iterate through all combinations */ // (Kev: only combos that have myMove are in here due to above param passing)
		for (List<Move> jointLegalMove : jointLegalMoves) {
			Move currMove = jointLegalMove.get(roleIndices.get(myRole)); // (Kev: pretty sure the myMove passed above covers these checks)
			if (currMove.equals(myMove)) {
				MachineState newState = getStateMachine().getNextState(node.getParent().getState(), jointLegalMove);
				MonteCarloNode newNode = new MonteCarloNode(newState, true, null, node);
				node.addChild(newNode); // add max nodes to the given minnode
			}
		}
	}

	/* Function: simulateToTerminal
	 * ==========================================================================================
	 * From the given node, simulate to the terminal state and get the score that this player can get.
	 * This is random depth charge.
	 * */
	public int simulateToTerminal(MonteCarloNode node) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		/* Declare depth and final state variables */
		int depth[] = new int[1];

		MachineState finalState = getStateMachine().performDepthCharge(node.getState(), depth);
		return getStateMachine().getGoal(finalState, myRole);
	}

	/* Function: backpropagate
	 * ==========================================================================================
	 * backpropagates from the given node to the root.
	 * For each node visited, updates the utility and the number of visits.
	 */
	public void backpropagate(MonteCarloNode node, int reward) {
		node.incrementUtilityAndVisited(reward);
		if (node.getParent() != null)
			backpropagate(node.getParent(), reward);
	}

	/* function: expandGeneral
	 * ===========================================================================
	 * In this way of expansion, we have no intermediate nodes. We have nodes for every possible joint legal moves.
	 * @ Parameter
	 * 		- MonteCarloNode node: this is assumed to have a state stored in it.
	 * 							Since we're dealing with all possible joint moves.
	 * */
	public void expandGeneral(MonteCarloNode node) throws MoveDefinitionException, TransitionDefinitionException {

		/* Get all legal joint moves from the current node we're looking at*/
		List<List<Move> > jointLegalMoves = getStateMachine().getLegalJointMoves(node.getState());

		/* Iterate through each joint legal moves, create node for them */
		for (List<Move> jointLegalMove : jointLegalMoves) {
			MachineState nextState = getStateMachine().getNextState(node.getState(), jointLegalMove);
			Move originMove = jointLegalMove.get(roleIndices.get(myRole));

			MonteCarloNode newNode;
			if (originMove.getContents().toString().equals("noop")) { //newNode is max since we just moved "noop"
				// THE ORIGINMOVE SHOULD BE THE OPPONENT's MOVE??

				newNode = new MonteCarloNode(nextState, true, originMove, node);
			} else { //newNode is min, we just made a "mark"
				newNode = new MonteCarloNode(nextState, false, originMove, node);
			}
			node.addChild(newNode);
		}
	}
}