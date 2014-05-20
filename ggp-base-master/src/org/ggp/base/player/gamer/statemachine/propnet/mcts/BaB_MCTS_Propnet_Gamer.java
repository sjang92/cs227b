package org.ggp.base.player.gamer.statemachine.propnet.mcts;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.ggp.base.player.gamer.event.GamerSelectedMoveEvent;
import org.ggp.base.player.gamer.statemachine.sample.SampleGamer;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;


public final class BaB_MCTS_Propnet_Gamer extends SampleGamer
{

	/* =======================================================================
	 * ====================== Global Variables & Constants  ==================
	 * ======================================================================= */
	private Map<Role, Integer> roleIndices;
	private Role myRole;
	private double bestUtility;
	private int numRounds;
	private boolean hasPropNet = false;
	private int C_Constant = 1;
	private PropNetStateMachine propNetStateMachine;

	private final int TIME_BUFFER = 1000;

	/* =======================================================================
	 * ====================== Inheritance Methods ============================
	 * ======================================================================= */

	/* Method: stateMachineMetaGame
	 * =======================================================================
	 * This method is called during the meta- gaming phase.
	 * The server does not check whether this gamer's computation during this phase is over or not.
	 * It's crucial to end everything before the timeout. */
	@Override
	public void stateMachineMetaGame(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
	{
		/* Record Start Time */
		long start = System.currentTimeMillis();

		System.out.print("============= START METAGAMING PHASE =========== \n");

		/* Initialize Gamer Instance Variables that will be used globally throughout the game. */
		roleIndices = getStateMachine().getRoleIndices();
		myRole = getRole();

		/* Construct Prop-net */
		propNetStateMachine = new PropNetStateMachine();
		propNetStateMachine.initialize(getMatch().getGame().getRules());


		long end = System.currentTimeMillis();
		System.out.print("================================================ \n");

		System.out.print("============== METAGAMING REPORT =============== \n");
		System.out.print("	- Duration: "+(end - start)+"\n");
		System.out.print("	- Propnet: "+hasPropNet+"\n");
		System.out.print("	- C Constant: "+ MonteCarloTreeNode.C_CONSTANT+"\n");
		System.out.print("================================================= \n");
	}


	/* Method: stateMachineSelectMove
	 * =======================================================================
	 * This method is called when the Gamer receives a signal from the server.
	 * Returns the move that we're trying to make. */
	@Override
	public Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
	{
		/* Record Start Time */
		long start = System.currentTimeMillis();

		System.out.print("================ START SELECT MOVE ==============\n");

		/* Initialize and Reset Variables */
		System.out.print("	- Initializing Move Variables... \n");
		List<Move> moves = getStateMachine().getLegalMoves(getCurrentState(), getRole());
		MonteCarloTreeNode root = new MonteCarloTreeNode(getCurrentState(), true, null, null, roleIndices.size());
		Move bestMove= moves.get(0);
		numRounds = 0;
		bestUtility = 0;

		/* Construct & Update MCTS Tree  */
		System.out.print("	- Constructing the MCTS Tree... \n");
		while (System.currentTimeMillis() < timeout - TIME_BUFFER) { // loop until 1 second is left.
			if (moves.size() == 1) {
				System.out.print("	- Detected only one possible move. Returning it... \n");
				break;
			}

			/* MCTS Tree Traverse Routine: Select -> Expand -> Simulate -> Backpropagate */
			MonteCarloTreeNode selection = select(root);
			expandGeneral(selection);
			List<Integer> terminalValues = simulateToTerminal(selection);
			backpropagate(selection, terminalValues);

			numRounds++;
		}

		/* Get Best Move from the MCTS Tree */
		System.out.print("- Interpreting the MCTS Tree... \n");
		if (moves.size() != 1) bestMove = getBestMove(root);
		System.out.print("================================================= \n");

		/* Print Readable End Turn Report */
		System.out.print("================== END TURN REPORT ===============\n");
		System.out.print("	- Move made: "+bestMove.toString()+"\n");
		System.out.print("	- Best Utility: "+bestUtility+"\n");
		System.out.print("	- Number of Rounds: " + numRounds + "\n");
		System.out.print("==================================================\n");

		/* Notify the server */
		long stop = System.currentTimeMillis();
		notifyObservers(new GamerSelectedMoveEvent(moves, bestMove, stop - start));
		return bestMove;
	}


	/* =======================================================================
	 * ================== KEY FUNCTION IMPLEMENTATION ========================
	 * ======================================================================= */

	/* Function: getBestMove
	 * =======================================================================
	 * Given the root node with the complete tree, loops through its children nodes
	 * and find out the best move by comparing their average utility scores.
	 */
	public Move getBestMove(MonteCarloTreeNode root) {

		/* comparing variables */
		Move bestMove = null;
		double bestScore = 0;

		/* Loop through all children nodes */
		for (MonteCarloTreeNode child : root.getChildren()) {

			if (child.getAverageUtility(roleIndices.get(myRole)) > bestScore) {
				bestScore = child.getAverageUtility(roleIndices.get(myRole));
				bestMove = child.moveIfMin;
			}
		}

		/* set Instance Variables for End-turn reporting */
		bestUtility = bestScore;
		return bestMove;
	}

	/* Function: select
	 * ==========================================================================================
	 *
	 */
	public MonteCarloTreeNode select(MonteCarloTreeNode node) throws MoveDefinitionException, TransitionDefinitionException {

		/* Escape Statement: Escape Recursion when the current node has no children or has never been visited */
		if (node.getNumVisits() == 0 || node.getNumChildren() == 0) {
			return node;

		/* Recursive Statement */
		} else {

			/* Check 1: Check if every child has been visited. */
			for (MonteCarloTreeNode childNode : node.getChildren())
				if (childNode.getNumVisits() == 0) return childNode;

			MonteCarloTreeNode resultNode = null;
			List<List<Move> > jointLegalMoves = getStateMachine().getLegalJointMoves(node.getState());
			List<Move> moves = getOpponentsMoves(node, jointLegalMoves);
			double bestScore = 0;
			MonteCarloTreeNode bestChild = null;
			List<Move> myLegalMoves = getStateMachine().getLegalMoves(node.getState(), myRole);

			// iterate over all my moves, if child's SelectFn is better than before we track it
			for (Move currMove : myLegalMoves) {
				moves.set(roleIndices.get(myRole), currMove);
				MachineState nextState = getStateMachine().getNextState(node.getState(), moves);
				if (nextState == null) {
					System.out.print("NULLY IN SELECT\n");
				}
				boolean foundStateInChildren = false;
				for (MonteCarloTreeNode child : node.getChildren()) {;
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
    private List<Move> getOpponentsMoves(MonteCarloTreeNode node, List<List<Move> > jointLegalMoves) throws TransitionDefinitionException, MoveDefinitionException {
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
							for (MonteCarloTreeNode child : node.getChildren()) { //find the node
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
	public void expandMax(MonteCarloTreeNode node) throws MoveDefinitionException, TransitionDefinitionException {
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
			MonteCarloTreeNode newNode = new MonteCarloTreeNode(null, false, move, node, roleIndices.size()); // no states + not maxnode + the move that I made + parent
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
	public void expandMin(MonteCarloTreeNode node) throws MoveDefinitionException, TransitionDefinitionException {
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
				MonteCarloTreeNode newNode = new MonteCarloTreeNode(newState, true, null, node, roleIndices.size());
				node.addChild(newNode); // add max nodes to the given minnode
			}
		}
	}

	/* Function: simulateToTerminal
	 * ==========================================================================================
	 * From the given node, simulate to the terminal state and get the score that this player can get.
	 * This is random depth charge.
	 * */
	public List<Integer> simulateToTerminal(MonteCarloTreeNode node) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		/* Declare depth and final state variables */
		int depth[] = new int[1];
		MachineState finalState = getStateMachine().performDepthCharge(node.getState(), depth);
		return getStateMachine().getGoals(finalState);
	}

	/* Function: backpropagate
	 * ==========================================================================================
	 * backpropagates from the given node to the root.
	 * For each node visited, updates the utility and the number of visits.
	 */
	public void backpropagate(MonteCarloTreeNode node, List<Integer> rewards) {
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
	public void expandGeneral(MonteCarloTreeNode node) throws MoveDefinitionException, TransitionDefinitionException {

		if (getStateMachine().isTerminal(node.getState())) return;
		/* Get all legal joint moves from the current node we're looking at*/
		List<List<Move> > jointLegalMoves = getStateMachine().getLegalJointMoves(node.getState());

		/* Iterate through each joint legal moves, create node for them */
		for (List<Move> jointLegalMove : jointLegalMoves) {
			MachineState nextState = getStateMachine().getNextState(node.getState(), jointLegalMove);

			Move originMove = jointLegalMove.get(roleIndices.get(myRole));

			MonteCarloTreeNode newNode = new MonteCarloTreeNode(nextState, false, originMove, node, roleIndices.size());
			node.addChild(newNode);
		}
	}
}