package org.ggp.base.player.gamer.statemachine.sample;

import java.util.ArrayList;

import org.ggp.base.util.statemachine.MachineState;

public class MonteCarloNode {

	private MonteCarloNode parent;
	private MachineState state;
	private ArrayList<MonteCarloNode> children;
	private int visits;
	private int utility;


	/* Constructor: takes the state as the param. */
	public MonteCarloNode(MachineState state) {
		this.state = state;
		this.children = new ArrayList<MonteCarloNode>();
		this.visits = 1;
		this.utility = 0;
	}

	/* Getters & Setters */
	public int getNumVisits() {
		return visits;
	}

	public void setNumVisits(int newVisits) {
		this.visits = newVisits;
	}

	public int getUtility() {
		return utility;
	}

	public void setUtility(int utility) {
		this.utility = utility;
	}

	/* Function: getSelectFnResult
	 * ================================
	 * Returns the value of the selectFn of the current node.
	 * */
	public double getSelectFnResult() {
		return this.utility + Math.sqrt(2 * Math.log(this.parent.visits)/this.visits);
	}

	public MachineState getState() {
		return state;
	}

	public void setMachineState(MachineState state) {
		this.state = state;
	}

	public void getChildAtIndex(int i) {
		if (i >= this.children.size()) {
			System.out.print("Index Out of Bounds Error at MonteCarloNode getChildAtIndex");
		}
		this.children.get(i);
	}

	public int getNumChildren() {
		return this.children.size();
	}

	public ArrayList<MonteCarloNode> getChildren() {
		return this.children;
	}

	public void addChild(MonteCarloNode child) {
		this.children.add(child);
	}
}
