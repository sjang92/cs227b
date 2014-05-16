package org.ggp.base.player.gamer.statemachine.sample;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.ggp.base.player.gamer.event.GamerSelectedMoveEvent;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;


public final class BaB_MCTSGamer2 extends SampleGamer
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
		MonteCarloNode2 root = new MonteCarloNode2(getCurrentState(), true, null, null, roleIndices.size());
		Move bestMove= moves.get(0);
		int numRounds = 0;

		while (/*enough time*/ System.currentTimeMillis() < timeout - 1000) { // loop until 1 second is left.
			if (moves.size() == 1) break;

			/* MCTS Routine: Select -> Expand -> Simulate -> Backpropagate */
			MonteCarloNode2 selection = select(root);
			expandGeneral(selection);
			List<Integer> terminalValues = simulateToTerminal(selection);
			backpropagate(selection, terminalValues);

			numRounds++;
			System.out.print("~~~~~~~~~~~~~~~Round Complete~~~~~~~~~~~~~~~~~\n");
		}

		/* Get Best Move*/
		if (moves.size() != 1) bestMove = getBestMove(root);

		System.out.print("Number of Depth Charges: " + numRounds + "\n");
		long stop = System.currentTimeMillis();

		notifyObservers(new GamerSelectedMoveEvent(moves, bestMove, stop - start));
		return bestMove;
	}

	public Move getBestMove(MonteCarloNode2 root) {
		Move bestMove = null;
		double bestScore = 0;
		for (MonteCarloNode2 child : root.getChildren()) {
			if (child.moveIfMin == null) System.err.print("Child of root node doesn't have a move associated with it.");

			if (child.getAverageUtility(roleIndices.get(myRole)) > bestScore) {
				bestScore = child.getAverageUtility(roleIndices.get(myRole));
				bestMove = child.moveIfMin;
			}
		}
		return bestMove;
	}

	/* ==========================================================================================
	 * ==========================================================================================*/
	/* KEY FUNCTION IMPLEMENTATION
	 * ==========================================================================================
	 * ==========================================================================================*/

	public MonteCarloNode2 select(MonteCarloNode2 node) throws MoveDefinitionException, TransitionDefinitionException {
		if (node.getNumVisits() == 0 || node.getNumChildren() == 0) {
			System.out.print("Recursion base case reached\n");
			return node;
		}
		else {
			System.out.print("Num Nodes Generated Uptil Now: "+MonteCarloNode2.numNodesConstructed+ "\n");
			for (MonteCarloNode2 childNode : node.getChildren()) {
				if (childNode.getNumVisits() == 0) {
					System.out.print("Recursion base case reached\n");
					return childNode;
				}
			}
			MonteCarloNode2 resultNode = null;
			System.out.print("Size of children arrray: " + node.getChildren().size() + "\n");

			List<List<Move> > jointLegalMoves = getStateMachine().getLegalJointMoves(node.getState());
			List<Move> moves = getOpponentsMoves(node, jointLegalMoves);
			double bestScore = 0;
			MonteCarloNode2 bestChild = null;
			List<Move> myLegalMoves = getStateMachine().getLegalMoves(node.getState(), myRole);
			// iterate over all my moves, if child's SelectFn is better than before we track it
			for (Move currMove : myLegalMoves) {
				moves.set(roleIndices.get(myRole), currMove);
				MachineState nextState = getStateMachine().getNextState(node.getState(), moves);
				if (nextState == null) {
					System.out.print("NULLY IN SELECT\n");
				}
				boolean foundStateInChildren = false;
				for (MonteCarloNode2 child : node.getChildren()) {;
					if (nextState.equals(child.getState())) {
						foundStateInChildren = true;

						double score = child.getSelectFnResult(roleIndices.get(myRole));
						if (score >= bestScore) {
							bestScore = score;
							bestChild = child;
						}
						if (bestChild == null) {
							System.out.print("best child null and we've gotta update!\n");
						}
						break; //only can pass if-conditional once, move on afterwards
					}
				}
				if (!foundStateInChildren) {
					System.out.print("Didn't find NextState among children\n");
				}
				if (bestChild == null) {
					System.out.print("we've got a null problem...\n");
					System.out.print("Terminal at current node?: " + getStateMachine().isTerminal(node.getState()) + "\n");

					System.out.print("Terminal at child?: " + getStateMachine().isTerminal(getStateMachine().getNextState(node.getState(), moves)) + "\n");
				}
			}
			resultNode = bestChild;

			if (resultNode == null) {
				System.out.print("FAILURE ON SELECT");
				return null;
			}
			System.out.print("~~~recursive call to select~~~\n");
			return select(resultNode);
		}
	}

   /* getOpponentsMoves:
    * Returns: List<Move>
    * -Moves of opponents are calculated by examining each of of their indiv moves, M. Then by taking the avg of
    * all SelectFn calls to children where opponent makes move M we can calculate the best
    * move the opponent can make.
    * -This is done for all opponents and returns the list of moves.
    * Move at myRole's index is null.
    */
    private List<Move> getOpponentsMoves(MonteCarloNode2 node, List<List<Move> > jointLegalMoves) throws TransitionDefinitionException, MoveDefinitionException {
    	Move[] moves = new Move[roleIndices.size()];
    //	rolesloop:
    	for (Role currRole : getStateMachine().getRoles()) {
    		if (roleIndices.get(myRole) != roleIndices.get(currRole)) { // if not me
				int bestScore = 0;
				Move bestMove = null;
				List<Move> legalMovesForRole = getStateMachine().getLegalMoves(node.getState(), currRole);
				for (Move currMove : legalMovesForRole) {
				   // handle cases where only one move available (i.e. noop)
					if (legalMovesForRole.size() == 1) {
				    	bestMove = currMove;
				    	//break rolesloop;
				    }

					int numMoves = 0;
					int cumSelectFn = 0;

					// for each jointMove we check if it contains the currMove for currRole,
					// if so then
					for (List<Move> jointMove : jointLegalMoves) {
						if (currMove.equals(jointMove.get(roleIndices.get(currRole)))) { //if this JLM has the currRole making currMove
							numMoves++;
							MachineState nextState = getStateMachine().getNextState(node.getState(), jointMove);
							for (MonteCarloNode2 child : node.getChildren()) { //find the node
								if (nextState.equals(child.getState())) {
									cumSelectFn += child.getSelectFnResult(roleIndices.get(currRole));
								//	break; // should just break out of children loop and back into JLM loop
								}
							}
						}
					}
					// check to see how currMove's avg SelectFn score compares, if better then update
					if ( ((float) cumSelectFn) / numMoves >= bestScore) {
						bestMove = currMove;
						bestScore = ((int)(((float) cumSelectFn) / numMoves));
					}
				}
				moves[roleIndices.get(currRole)] = bestMove;
			}
		}
    	List<Move> moveList = new ArrayList<Move>();
    	for (int i = 0; i < moves.length; i++) {
    		moveList.add(moves[i]);
    	}
    	return moveList;
    }

	/* Function: expandMax
	 * ==========================================================================================
	 * Expand from a max node. This would expand given the choices of the legal moves that I have.
	 * However, since we need both opponents moves and my moves to reach a new state, the min nodes
	 * that are created from this function call do not have states.
	 * */
	public void expandMax(MonteCarloNode2 node) throws MoveDefinitionException, TransitionDefinitionException {
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
			MonteCarloNode2 newNode = new MonteCarloNode2(null, false, move, node, roleIndices.size()); // no states + not maxnode + the move that I made + parent
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
	public void expandMin(MonteCarloNode2 node) throws MoveDefinitionException, TransitionDefinitionException {
		System.out.print("EXPAND MIN CALLED!!\n");
		if (node.getNumChildren() != 0) { // does this have a purpose? why would expandMax ever be called on a node w/ children?
			return;
		}
		/* Get prev my Move */
		Move myMove = node.moveIfMin;

		/* Get all combinations of myMove and opponents move */
		List<List<Move> > jointLegalMoves = getStateMachine().getLegalJointMoves(node.getParent().getState()/*, myRole, myMove*/);

		/* Iterate through all combinations */ // (Kev: only combos that have myMove are in here due to above param passing)
		for (List<Move> jointLegalMove : jointLegalMoves) {
			Move currMove = jointLegalMove.get(roleIndices.get(myRole)); // (Kev: pretty sure the myMove passed above covers these checks)
			if (currMove.equals(myMove)) {
				MachineState newState = getStateMachine().getNextState(node.getParent().getState(), jointLegalMove);
				MonteCarloNode2 newNode = new MonteCarloNode2(newState, true, null, node, roleIndices.size());
				node.addChild(newNode); // add max nodes to the given minnode
			}
		}
	}

	/* Function: simulateToTerminal
	 * ==========================================================================================
	 * From the given node, simulate to the terminal state and get the score that this player can get.
	 * This is random depth charge.
	 * */
	public List<Integer> simulateToTerminal(MonteCarloNode2 node) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		/* Declare depth and final state variables */
		int depth[] = new int[1];
//		MachineState currState;
//
//		/* If max, currState is give. If min, get random currState from currMove.*/
//		if (node.isMax) currState = node.getState();
//		else currState = getStateMachine().getRandomNextState(node.getParent().getState(), myRole, node.moveIfMin);


		MachineState finalState = getStateMachine().performDepthCharge(node.getState(), depth);
		//return getStateMachine().getGoal(finalState, myRole);
		return getStateMachine().getGoals(finalState);
	}

	/* Function: backpropagate
	 * ==========================================================================================
	 * backpropagates from the given node to the root.
	 * For each node visited, updates the utility and the number of visits.
	 */
	public void backpropagate(MonteCarloNode2 node, List<Integer> rewards) {
		node.incrementUtilityAndVisited(rewards);
		if (node.getParent() != null)
			backpropagate(node.getParent(), rewards);
	}

	/* function: expandGeneral
	 * ===========================================================================
	 * In this way of expansion, we have no intermediate nodes. We have nodes for every possible joint legal moves.
	 * @ Parameter
	 * 		- MonteCarloNode node: this is assumed to have a state stored in it.
	 * 							Since we're dealing with all possible joint moves.
	 * */
	public void expandGeneral(MonteCarloNode2 node) throws MoveDefinitionException, TransitionDefinitionException {

		if (getStateMachine().isTerminal(node.getState())) return;
		/* Get all legal joint moves from the current node we're looking at*/
		List<List<Move> > jointLegalMoves = getStateMachine().getLegalJointMoves(node.getState());

		/* Iterate through each joint legal moves, create node for them */
		for (List<Move> jointLegalMove : jointLegalMoves) {
			MachineState nextState = getStateMachine().getNextState(node.getState(), jointLegalMove);

			Move originMove = jointLegalMove.get(roleIndices.get(myRole));

			MonteCarloNode2 newNode = new MonteCarloNode2(nextState, false, originMove, node, roleIndices.size());
			node.addChild(newNode);
		}
	}
}