package org.ggp.base.player.gamer.statemachine.propnet.mcts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.ggp.base.util.gdl.grammar.GdlConstant;
import org.ggp.base.util.gdl.grammar.GdlRelation;
import org.ggp.base.util.propnet.architecture.Component;
import org.ggp.base.util.propnet.architecture.Component.ComponentType;
import org.ggp.base.util.propnet.architecture.PropNet;
import org.ggp.base.util.propnet.architecture.components.Or;
import org.ggp.base.util.propnet.architecture.components.Proposition;
import org.ggp.base.util.propnet.architecture.components.Transition;
import org.ggp.base.util.statemachine.Role;

public class PropNetProcessor {

	private PropNet propNet;
	private List<Proposition> ordering;
	public List<Component> subGraph;
	public List<Proposition> validPropositions;
	public Map<Role, Set<Proposition>> roleLegalMap;
	public List<Proposition> legalPropositions;

	/* Construcor: PropNetProcessor
	 * ========================================
	 * takes in a propNet and the topological ordering of the propnet.
	 * */
	public PropNetProcessor(PropNet propNet, List<Proposition> ordering) {
			this.propNet = propNet;
			this.ordering = ordering;
			this.subGraph = new ArrayList<Component>();
			this.validPropositions = new ArrayList<Proposition>();
			this.roleLegalMap = new HashMap<Role, Set<Proposition>>();
	}

	public void getMainGraph() {

		Proposition terminal = propNet.getTerminalProposition();

		traverseGraph(terminal);
		for (Component c : subGraph) {
			if (c.type == ComponentType.PROP) validPropositions.add((Proposition)c);
		}

		roleLegalMap = recordLegalPropositions();

		List<Component> notIncluded = new ArrayList<Component>();

		for (Component c : propNet.getPropositions()) {
			if (!subGraph.contains(c)) {
				System.out.println(c.toString());
				notIncluded.add(c);
			}
		}
//
//		Map<Role, Set<Proposition>> legalMap = propNet.getLegalPropositions();
//		for (Role rl : propNet.getRoles()) {
//			Set<Proposition> legals = legalMap.get(rl);
//			for (Proposition legal : legals) {
//
//				//if (legal.getOutputs().size() == ) {
//					roleLegalMap.get(rl).add(legal);
//				//}
//			}
//		}


		System.out.println("SIZE OF THE SUBGRAPH: "+subGraph.size()); // component within the subgraph
		System.out.println("SIZE OF ALL SUBGRAPH PROPOSITIONS: " +validPropositions.size());
		System.out.println("SIZE OF THE MAINGRAPH: "+propNet.getComponents().size()); // every component
	}

	private void traverseGraph(Component root) {

		/* Escape Statement: If this component was already visited, break out of recursion. */
		if (subGraph.contains(root)) {
			return; // detects a cycle.
		}

		subGraph.add(root); // mark this root as visited

		Set<Component> inputs = root.getInputs();
		for (Component input : inputs) {
			traverseGraph(input);
		}

		Set <Component> outputs = root.getOutputs();
		for (Component output : outputs) {
			traverseGraph(output);
		}
	}

	private Map<Role, Set<Proposition>> recordLegalPropositions()
	{
		Map<Role, Set<Proposition>> legalPropositions = new HashMap<Role, Set<Proposition>>();
		for (Proposition proposition : validPropositions)
		{
		    // Skip all propositions that aren't GdlRelations.
			if (!(proposition.getName() instanceof GdlRelation))
			    continue;

			GdlRelation relation = (GdlRelation) proposition.getName();
			if (relation.getName().getValue().equals("legal")) {
				GdlConstant name = (GdlConstant) relation.get(0);
				Role r = new Role(name);
				if (!legalPropositions.containsKey(r)) {
					legalPropositions.put(r, new HashSet<Proposition>());
				}
				legalPropositions.get(r).add(proposition);
			}
		}

		return legalPropositions;
	}


	/* Function: isFactorable
	 * ========================================
	 * returns true if the given propNet is factorable.
	 * This means that the terminal proposition has an or gate as its source,
	 * so only one of the sources base propositions need to be satisfied.
	 * */
	public boolean isFactorable() {
		if (propNet.getTerminalProposition().getSingleInput().type != ComponentType.OR)
			return false;

		if (propNet.getInitProposition().getOutputs().size() == 1)
			return false;

		return true;
	}

	/* Function: disjuntiveFactoring
	 * ========================================
	 * Design is as follows:
	 * 1) check if the propNet is factorable - that is, the terminal proposition has an or gate as its source.
	 * 2) If it's not, then just return the initial ordering
	 * 3) If it is factorable, we need to first get rid of all base propositio
	 * */
	public List<List<Proposition>> disjunctiveFactoring() {
		List<List<Proposition>> result = new ArrayList<List<Proposition>>();

		/* Not factorable, just return the ordering. */
		if (!isFactorable()) {
			result.add(ordering);
			return result;
		}

		/* Is Factorable. Divide into multiple factors */
		Set<Component> subTerminals = propNet.getTerminalProposition().getSingleInput().getInputs();
		int numFactors = subTerminals.size();

		/* For each sub terminals, find ordering that results in the specific sub terminal. */
		for (Component subTerminal : subTerminals) {
			//List<Proposition> factoredOrdering = getFactor(subTerminal);
			//result.add(factoredOrdering);
		}

		return result;
	}

	/* Function: getFactor
	 * ========================================
	 * Returns a topological ordering that results in the given subTerminal.
	 * */
	private PropNet getFactoredPropNet(Component subTerminal, Set<Component> subTerminals) {
		if (subTerminal.type != ComponentType.PROP) {
			System.err.println("Sub terminal is not a proposition!");
			return null;
		}

		/* Keep only the subTerminal and the real terminal. */
		List<Component> allComponents = new ArrayList(propNet.getComponents());
		List<Proposition> newBasePropositions = new ArrayList(propNet.getBasePropositionList());

		allComponents.removeAll(subTerminals);
		allComponents.add(subTerminal);

		/* Remove the orgate and add a transition */
		Or orgate = (Or) propNet.getTerminalProposition().getSingleInput();
		Transition tr = new Transition();
		tr.addInput(subTerminal);
		tr.addOutput(propNet.getTerminalProposition());
		allComponents.remove(orgate);
		allComponents.add(tr);

		/* Get rid of base propositions that're not connected to the terminal. */
		for (Proposition baseProp : propNet.getBasePropositionList()) {
			if (!isConnectedToSubterminal((Proposition)subTerminal, baseProp)) {
				newBasePropositions.remove(baseProp);
				allComponents.remove(baseProp);
			}
		}

		/* Get rid of legal propositions that're not connected to the remaining base propositions */

		/* Get rid of input propositions that're not correspondent to the remaining legal propositions... */

		/* Get rid of all connectives & components that're not connected to any of the remaining base, legal or input props*/
		return null;
	}

	private boolean isConnectedToRemainingBaseProps(Proposition legalProposition, List<Proposition> baseProps) {


		return false;
	}

	private boolean isConnectedToSubterminal(Proposition subTerminal, Component c) {
		if (c.equals(subTerminal))
			return true;
		else if (c.equals(propNet.getTerminalProposition()) || c.isConnectedToTerminal == false)
			return false;
		else {
			Set<Component> outputs = c.getOutputs();
			boolean isConnectedToSubterminal = false;
			for (Component comp : outputs) {
				if (isConnectedToSubterminal(subTerminal, comp)) {
					isConnectedToSubterminal = true;
				}
			}

			return isConnectedToSubterminal;
		}
	}
}
