package org.ggp.base.player.gamer.statemachine.sample;

import java.util.ArrayList;

import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;

public class MonteCarloNode {

	/* Instance variables */
	private MonteCarloNode parent;
	private MachineState state;
	private ArrayList<MonteCarloNode> children;
	private int visits;
	private int utility;
	public boolean isMax;
	public Move moveIfMin;
	public static int numNodesConstructed = 0;

	/* Constructor: takes the state as the param. */
	public MonteCarloNode(MachineState state, boolean isMax, Move move, MonteCarloNode parent) {
		this.state = state;
		this.children = new ArrayList<MonteCarloNode>();
		this.visits = 0;
		this.utility = 0;
		this.isMax = isMax;

		if (!isMax) this.moveIfMin = move;
			else this.moveIfMin = null;
		//this.moveIfMin = move;
		this.parent = parent;

		numNodesConstructed++;
	}

	/* Getters & Setters */

	/* NUM VISITS */
	public int getNumVisits() {
		return visits;
	}

	public void setNumVisits(int newVisits) {
		this.visits = newVisits;
	}

	/* UTILITY */
	public int getUtility() {
		return utility;
	}

	public double getAverageUtility() {
		if (this.visits == 0) return 0;
		return this.utility/this.visits;
	}

	public void setUtility(int utility) {
		this.utility = utility;
	}

	public void incrementUtilityAndVisited(int incUtility) {
		this.visits++;
		this.utility += incUtility;
	}

	/* Function: getSelectFnResult
	 * ================================
	 * Returns the value of the selectFn of the current node.
	 * */
	public double getSelectFnResult() {
		if (this.isMax) {
			return this.utility/this.visits + Math.sqrt(Math.log(this.parent.visits)/this.visits);
		} else {
			return -(this.utility/this.visits + Math.sqrt(Math.log(this.parent.visits)/this.visits));
		}
	}

	public MachineState getState() {
		return state;
	}

	public void setMachineState(MachineState state) {
		this.state = state;
	}

	public MonteCarloNode getChildAtIndex(int i) {
		if (i >= this.children.size())
			System.out.print("Index Out of Bounds Error at MonteCarloNode getChildAtIndex");
		return this.children.get(i);
	}

	public int getNumChildren() {
		return this.children.size();
	}

	public MonteCarloNode getParent() {
		return parent;
	}

	public ArrayList<MonteCarloNode> getChildren() {
		return this.children;
	}

	public void addChild(MonteCarloNode child) {
		this.children.add(child);
	}
}
