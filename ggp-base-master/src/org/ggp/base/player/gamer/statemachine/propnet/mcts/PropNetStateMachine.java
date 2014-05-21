package org.ggp.base.player.gamer.statemachine.propnet.mcts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.ggp.base.util.gdl.grammar.Gdl;
import org.ggp.base.util.gdl.grammar.GdlConstant;
import org.ggp.base.util.gdl.grammar.GdlRelation;
import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.propnet.architecture.Component;
import org.ggp.base.util.propnet.architecture.PropNet;
import org.ggp.base.util.propnet.architecture.components.Proposition;
import org.ggp.base.util.propnet.factory.OptimizingPropNetFactory;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.prover.query.ProverQueryBuilder;

public class PropNetStateMachine extends StateMachine {
	/** The underlying proposition network  */
    private PropNet propNet;
    /** The topological ordering of the propositions */
    private List<Component> ordering;
    /** The player roles */
    private List<Role> roles;

    private int basePropositionIndex;
    private int basePropositionSize;
    private int inputPropositionIndex;
    private int inputPropositionSize;
    private int terminalPropositionIndex;


    /**
     * Initializes the PropNetStateMachine. You should compute the topological
     * ordering here. Additionally you may compute the initial state here, at
     * your discretion.
     * @throws InterruptedException
     */
    @Override
    public void initialize(List<Gdl> description) throws InterruptedException {
    	System.out.print("	- Creating propNet... \n");
        propNet = OptimizingPropNetFactory.create(description);
        if (propNet == null) System.err.print("propNet is null for some reason");
        System.out.print("	- Finished initializing propNet... \n");
        roles = propNet.getRoles();
        ordering = getOrdering();
    }

	/**
	 * Computes if the state is terminal. Should return the value
	 * of the terminal proposition for the state.
	 */
	@Override
	public boolean isTerminal(MachineState state) {
		return true;
	}

	/**
	 * Computes the goal for a role in the current state.
	 * Should return the value of the goal proposition that
	 * is true for that role. If there is not exactly one goal
	 * proposition true for that role, then you should throw a
	 * GoalDefinitionException because the goal is ill-defined.
	 */
	@Override
	public int getGoal(MachineState state, Role role)
	throws GoalDefinitionException {
		return -1;
	}

	/**
	 * Returns the initial state. The initial state can be computed
	 * by only setting the truth value of the INIT proposition to true,
	 * and then computing the resulting state.
	 */
	@Override
	public MachineState getInitialState() {
		// TODO: Compute the initial state.
		return null;
	}

	/**
	 * Computes the legal moves for role in state.
	 */
	@Override
	public List<Move> getLegalMoves(MachineState state, Role role)
	throws MoveDefinitionException {
		return null;
	}

	/**
	 * Computes the next state given state and the list of moves.
	 */
	@Override
	public MachineState getNextState(MachineState state, List<Move> moves)
	throws TransitionDefinitionException {
		return null;
	}

	/**
	 * This should compute the topological ordering of propositions.
	 * Each component is either a proposition, logical gate, or transition.
	 * Logical gates and transitions only have propositions as inputs.
	 *
	 * The base propositions and input propositions should always be exempt
	 * from this ordering.
	 *
	 * The base propositions values are set from the MachineState that
	 * operations are performed on and the input propositions are set from
	 * the Moves that operations are performed on as well (if any).
	 *
	 * @return The order in which the truth values of propositions need to be set.
	 */
	public List<Component> getOrdering()
	{
		System.out.print("	- Starting Topological Ordering... \n");

		/* Declare and initialize component list variables s*/
	    List<Component> result = new LinkedList<Component>();
	    List<Component> components = new ArrayList<Component>(propNet.getComponents());
	    List<Proposition> basePropositions = propNet.getBasePropositionList();
		List<Proposition> inputPropositions = propNet.getInputPropositionList();
		System.out.print("	- Total num components to order: "+components.size()+"\n");

		/* Remove base, terminal, init and input propositions from the components pool */
		components.removeAll(basePropositions);
		components.remove(propNet.getInitProposition());
		components.removeAll(inputPropositions);

	    /* Create the dependency map for components to determine if they are dependent on the base proposition */
	    Map<Component, Boolean> dependencyMap = new HashMap<Component, Boolean>();
	    Map<Component, Boolean> reverseDependencyMap = new HashMap<Component, Boolean>();

		/* Step 1) Divide into components that depend on the base propositions vs. everything else*/
		List<Component> dependentOnBase = new ArrayList<Component>();
		List<Component> notDependentOnBase = new ArrayList<Component>();

		/* For every components, determine it is dependent on base propositions or not */
		for (Component c : components) {
			if (isDependentOnBaseProposition(dependencyMap, c, inputPropositions, basePropositions)) dependentOnBase.add(c);
			else notDependentOnBase.add(c); // add only if not input proposition or base proposition.
		}

		/* Setp 2) Divide All that are dependent on Base propositions into: Left and Right of terminal */
		List<Component> terminalLeft = new ArrayList<Component>();
		List<Component> terminalRight = new ArrayList<Component>();

		for (Component c : dependentOnBase) {
			/* If the given component is a source of the terminal proposition, add to Left. Else add to Right */
			if (c.equals(propNet.getTerminalProposition()) ||
					isReverseDependentOnTerminal(reverseDependencyMap, c, basePropositions)) terminalLeft.add(c);
			else terminalRight.add(c);
		}

		/* The beginning of the result is always init -> bases */
	    result.add(propNet.getInitProposition());
	    basePropositionIndex = result.size();
	    result.addAll(basePropositions);
	    basePropositionSize = basePropositions.size();
		topologicalSort(result, terminalLeft);
		terminalPropositionIndex = result.size() - 1;

		topologicalSort(result, terminalRight);
		inputPropositionIndex = result.size();
		result.addAll(inputPropositions);
		inputPropositionSize = inputPropositions.size();
		topologicalSort(result, notDependentOnBase);

		if (!isValidTopologicalOrdering(result))
			System.err.print("	- Error Found while doing topological sort of propositions \n");
		else {
			System.out.print("	- Finished findidng the Topological Ordering of Game Propositions...\n");
			System.out.print("	- base Proposition range: "+basePropositionIndex+"->"+(basePropositionIndex + basePropositionSize)+"\n");
			System.out.print("  - terminal Proposition index: "+terminalPropositionIndex+"\n");
			System.out.print("	- input Proposition range: "+inputPropositionIndex+"->"+(inputPropositionIndex + inputPropositionSize)+"\n");
		}
		return result;
	}

	/* Function: isReverseDependentOnTerminalProposition
	 * ========================================
	 * Recursively computes if a given component is reverse dependent on the terminal proposition
	 * */
	private boolean isReverseDependentOnTerminal(Map<Component, Boolean> reverseDependencyMap, Component c, List<Proposition> baseProps) {
		if (reverseDependencyMap.containsKey(c)) {
			return reverseDependencyMap.get(c);

		/* Cycle detection: if we come back to baseProps, there's a cycle */
		} else if (baseProps.contains(c)) {
			return false;
		} else {
			Set<Component> outputs = c.getOutputs();
			boolean isReverseDependent = false;
			for (Component output : outputs) {

				/* if one of the output is the terminal, this component is reverse dependent on the terminal */
				if (c.equals(propNet.getTerminalProposition()) || output.equals(propNet.getTerminalProposition())) {
					isReverseDependent = true;
				/* if the output is reverse dependent on the terminal, this is also reverse dependent */
				} else if (isReverseDependentOnTerminal(reverseDependencyMap, output, baseProps)) {
					isReverseDependent = true;
				}
			}
			reverseDependencyMap.put(c, isReverseDependent);
			return isReverseDependent;
		}
	}

	/* Function: isDependentOnBaseProposition
	 * ========================================
	 * Recursively computes if a given component is dependent on base propositions
	 * */
	private boolean isDependentOnBaseProposition(Map<Component, Boolean> dependencyMap, Component c, List<Proposition> inputProps, List<Proposition> baseProps) {

		/* Escape Statement: if the component is stored in the map, return the value */
		if (dependencyMap.containsKey(c)) {
			return dependencyMap.get(c);

		/* Recursive Statement: if the component is not in the map, store its sources and recurse. */
		} else {
			Set<Component> sources = c.getInputs();

			boolean isDependent = true;
			for (Component source : sources) {
				if (baseProps.contains(source)) continue;
				/* if a single source is base proposition, this is dependent on the base proposition */
				if (inputProps.contains(source)) {
					isDependent = false;
				/* If a single source is dependent, then the current component is dependent */
				} else if (!isDependentOnBaseProposition(dependencyMap, source, inputProps, baseProps))
					isDependent = false;
			}
			dependencyMap.put(c, isDependent);
			return isDependent;
		}
	}

	/* Function: topologicalSort
	 * ========================================
	 * Variation of topological Sort. Very poor performance.Can come up with better running time.
	 * Will enter infinite loop if a component has itself as its own source. This shouldn't happen
	 * */
	private void topologicalSort(List<Component> L, List<Component> R) {

		/* Topological Sort Loop */
		while (!R.isEmpty()) {
			List<Component> toRemove = new ArrayList<Component>();
			/* For every Component in R, add to L if it has no source in R */
			for (Component c : R) {

				/* if c's source is not in R, remove from R and add to L*/
				if (!hasSourceInR(c, R)) {
					L.add(c);
					toRemove.add(c);
				}
			}
			R.removeAll(toRemove);
		}
	}

	/* Function: isValidTopologicalOrdering
	 * ========================================
	 * Checks if the given list of compoenets is topologically sorted
	 * */
	private boolean isValidTopologicalOrdering(List<Component> list) {
		int counter = 0;
		System.out.print("Checking Topological Ordering...\n");
		for (int i = 0; i < list.size(); i++) {
			counter++;
			if (hasSourceInR(list.get(i), list.subList(i + 1, list.size()))) {
				if (propNet.getBasePropositionList().contains(list.get(i))) {

				} else {
					System.out.print("Topological Sort Fails at index: "+i+", and proposition: "+list.get(i)+"\n");
					System.out.print("Topological Sort Fails at index: "+i+", and source proposition: "+list.get(i).getInputs()+"\n");

				}
				 // if the rightside contains any source, fail
			}
		}
		System.out.print("Num Checked: "+counter+"\n");
		return true;
	}

	/* Function: hasSourceInR
	 * ========================================
	 * Returns true if R contains any one of c's sources
	 * */
	private boolean hasSourceInR(Component c, List<Component> R) {
		for (Component source : c.getInputs())
			if (R.contains(source)) return true;

		return false;
	}

	/* Function: includesBase
	 * ========================================
	 * Determines if the given set of components includes a baseProposition or not.
	 * */
	private boolean includesBase(Set<Component> sources) {
		Map<GdlSentence, Proposition> basePropositions = propNet.getBasePropositions();

		for (Component c : sources)
			if (basePropositions.containsValue(c)) return true; //can optimize later

		return false;
	}

	/* Already implemented for you */
	@Override
	public List<Role> getRoles() {
		return roles;
	}

	/* Helper methods */

	/**
	 * The Input propositions are indexed by (does ?player ?action).
	 *
	 * This translates a list of Moves (backed by a sentence that is simply ?action)
	 * into GdlSentences that can be used to get Propositions from inputPropositions.
	 * and accordingly set their values etc.  This is a naive implementation when coupled with
	 * setting input values, feel free to change this for a more efficient implementation.
	 *
	 * @param moves
	 * @return
	 */
	private List<GdlSentence> toDoes(List<Move> moves)
	{
		List<GdlSentence> doeses = new ArrayList<GdlSentence>(moves.size());
		Map<Role, Integer> roleIndices = getRoleIndices();

		for (int i = 0; i < roles.size(); i++)
		{
			int index = roleIndices.get(roles.get(i));
			doeses.add(ProverQueryBuilder.toDoes(roles.get(i), moves.get(index)));
		}
		return doeses;
	}

	/**
	 * Takes in a Legal Proposition and returns the appropriate corresponding Move
	 * @param p
	 * @return a PropNetMove
	 */
	public static Move getMoveFromProposition(Proposition p)
	{
		return new Move(p.getName().get(1));
	}

	/**
	 * Helper method for parsing the value of a goal proposition
	 * @param goalProposition
	 * @return the integer value of the goal proposition
	 */
    private int getGoalValue(Proposition goalProposition)
	{
		GdlRelation relation = (GdlRelation) goalProposition.getName();
		GdlConstant constant = (GdlConstant) relation.get(1);
		return Integer.parseInt(constant.toString());
	}

	/**
	 * A Naive implementation that computes a PropNetMachineState
	 * from the true BasePropositions.  This is correct but slower than more advanced implementations
	 * You need not use this method!
	 * @return PropNetMachineState
	 */
	public MachineState getStateFromBase()
	{
		Set<GdlSentence> contents = new HashSet<GdlSentence>();
		for (Proposition p : propNet.getBasePropositions().values())
		{
			p.setValue(p.getSingleInput().getValue());
			if (p.getValue())
			{
				contents.add(p.getName());
			}

		}
		return new MachineState(contents);
	}
}

