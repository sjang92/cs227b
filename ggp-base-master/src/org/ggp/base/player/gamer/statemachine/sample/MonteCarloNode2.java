package org.ggp.base.player.gamer.statemachine.sample;

import java.util.ArrayList;
import java.util.List;

import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;

public class MonteCarloNode2 {

	/* Instance variables */
	private MonteCarloNode2 parent;
	private MachineState state;
	private ArrayList<MonteCarloNode2> children;
	private int visits;
	private List<Integer> utilities;
	public boolean isMax;
	public Move moveIfMin;
	public static int numNodesConstructed = 0;

	private final int C_CONSTANT = 1;

	/* Constructor: takes the state as the param. */
	public MonteCarloNode2(MachineState state, boolean isMax, Move move, MonteCarloNode2 parent, int numPlayers) {
		this.state = state;
		this.children = new ArrayList<MonteCarloNode2>();
		this.visits = 0;
		this.utilities = new ArrayList<Integer>();
		for (int i = 0; i < numPlayers; i++) {
			this.utilities.add(0);
		}
		this.isMax = isMax;

//		if (!isMax) this.moveIfMin = move;
//			else this.moveIfMin = null;
		this.moveIfMin = move;
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
	public List<Integer> getUtility() {
		return utilities;
	}

	public double getAverageUtility(int roleIndex) {
		if (this.visits == 0) return 0;
		return this.utilities.get(roleIndex)/this.visits;
	}

	public void setUtility(int utility, int roleIndex) {
		this.utilities.set(roleIndex, utility);
	}

	public void incrementUtilityAndVisited(List<Integer> incUtility) {
		this.visits++;
		for (int i = 0; i < incUtility.size(); i++) {
			this.utilities.set(i, (utilities.get(i) + incUtility.get(i)) );
		}
	}

	/* Function: getSelectFnResult
	 * ================================
	 * Returns the value of the selectFn of the current node.
	 * */
	public double getSelectFnResult(int roleIndex) {
	//	if (this.parent.isMax) {
			return ((float)this.utilities.get(roleIndex))/this.visits + C_CONSTANT * Math.sqrt(Math.log(((float)this.parent.visits)/this.visits));
	//	} else { // (Kev: if min we do comparison to find the smallest avg util, return the util and give it a bonus (subtraction) if it hasn't been visited very often)
//			return ((float)this.utility)/this.visits - C_CONSTANT * Math.sqrt(Math.log(this.parent.visits)/this.visits);
//		}
	}

	public MachineState getState() {
		return state;
	}

	public void setMachineState(MachineState state) {
		this.state = state;
	}

	public MonteCarloNode2 getChildAtIndex(int i) {
		if (i >= this.children.size())
			System.out.print("Index Out of Bounds Error at MonteCarloNode getChildAtIndex");
		return this.children.get(i);
	}

	public int getNumChildren() {
		return this.children.size();
	}

	public MonteCarloNode2 getParent() {
		return parent;
	}

	public ArrayList<MonteCarloNode2> getChildren() {
		return this.children;
	}

	public void addChild(MonteCarloNode2 child) {
		this.children.add(child);
	}
}