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
import org.ggp.base.util.propnet.architecture.Component.ComponentType;
import org.ggp.base.util.propnet.architecture.PropNet;
import org.ggp.base.util.propnet.architecture.components.And;
import org.ggp.base.util.propnet.architecture.components.Not;
import org.ggp.base.util.propnet.architecture.components.Or;
import org.ggp.base.util.propnet.architecture.components.Proposition;
import org.ggp.base.util.propnet.architecture.components.Proposition.PropositionType;
import org.ggp.base.util.propnet.architecture.components.Transition;
import org.ggp.base.util.propnet.factory.OptimizingPropNetFactory;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.prover.query.ProverQueryBuilder;

public class OptimizedPropNetStateMachine extends StateMachine {
	/** The underlying proposition network  */
    private PropNet propNet;
    /** The topological ordering of the propositions */
    private List<Component> ordering;
    //private List<Proposition> ordering;
    /** The player roles */
    private List<Role> roles;

	/** References to every BaseProposition in the PropNet, indexed by name. */
	private Map<GdlSentence, Proposition> basePropositions;

	/** References to every InputProposition in the PropNet, indexed by name. */
	private Map<GdlSentence, Proposition> inputPropositions;

	/** References to every LegalProposition in the PropNet, indexed by role. */
	private Map<Role, Set<Proposition>> legalPropositions;

	/** References to every GoalProposition in the PropNet, indexed by role. */
	private Map<Role, Set<Proposition>> goalPropositions;

	/** A reference to the single, unique, InitProposition. */
	private Proposition initProposition;

	/** A reference to the single, unique, TerminalProposition. */
	private Proposition terminalProposition;

	/** A helper mapping between input/legal propositions. */
	private Map<Proposition, Proposition> legalInputMap;

	private List<Proposition> basePropositionList;

	private List<Proposition> inputPropositionList;

	private Map<Role, Map<Move, Proposition> > moveInputMap;

	private Map<Proposition, Integer> goalMap;

	private boolean[] valueArray;

    private int basePropositionIndex;
    private int basePropositionSize;
    private int endOfBaseProposition;
    private int inputPropositionIndex;
    private int inputPropositionSize;
    private int terminalPropositionIndex;

    public long time_initializeInput = 0;
    public long time_initializeBase = 0;
    public long time_forwardPropagate = 0;
    public long time_getNext = 0;
    public long time_getLegal = 0;
    public long time_isTerminal = 0;
    public long time_getGoal = 0;

    public int call_forwardPropagate = 0;

    private Map<Component, Integer> compIndexMap;
    private Map<ComponentType, GATEFUNC> compFuncMap;

    public interface GATEFUNC {
    	public boolean gateOp(Component gate);
    }

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
        basePropositions = propNet.getBasePropositions();
        inputPropositions = propNet.getInputPropositions();
        legalPropositions = propNet.getLegalPropositions();
        goalPropositions = propNet.getGoalPropositions();
        initProposition = propNet.getInitProposition();
        terminalProposition = propNet.getTerminalProposition();
        legalInputMap = propNet.getLegalInputMap();
        roles = propNet.getRoles();
        basePropositionList = propNet.getBasePropositionList();
        inputPropositionList = propNet.getInputPropositionList();
        moveInputMap = getInputMoveMap();
        goalMap = getGoalMap();
        addTypeTagToComponents(propNet.getComponents());

        /* OPTIMIZATION */
        valueArray = new boolean[propNet.getComponents().size()];
        compIndexMap = new HashMap<Component, Integer>();
        compFuncMap = new HashMap<ComponentType, GATEFUNC>();


        ordering = getOrdering(); // ordering of all components
        for (int i = 0; i < ordering.size(); i++) { // mapping from component to index
        	compIndexMap.put(ordering.get(i), i);
        }
        constructGateFunctions(); // mapping from class to function


        System.out.print("	- Finished initializing propNet... \n");
    }

    private void constructGateFunctions() {
    	compFuncMap.put(ComponentType.AND, new GATEFUNC() {

			@Override
			public boolean gateOp(Component gate) {
				Set<Component> inputs = gate.getInputs();
				for (Component c : inputs) {
					int index = compIndexMap.get(c);
					if (valueArray[index] == false) return false;
				}

				return true;
			}

    	});
    	compFuncMap.put(ComponentType.OR, new GATEFUNC() {

			@Override
			public boolean gateOp(Component gate) {
				Set<Component> inputs = gate.getInputs();
				for (Component c : inputs) {
					int index = compIndexMap.get(c);
					if (valueArray[index] == true) return true;
				}

				return false;
			}

    	});
    	compFuncMap.put(ComponentType.NEG, new GATEFUNC() {

			@Override
			public boolean gateOp(Component gate) {
				int index = compIndexMap.get(gate.getSingleInput());
				return !valueArray[index];
			}

    	});

    	compFuncMap.put(ComponentType.TRAN, new GATEFUNC() {

			@Override
			public boolean gateOp(Component gate) {
				int index = compIndexMap.get(gate.getSingleInput());
				//System.out.println("Transitiion Op called. Index of parent: "+index);
				return valueArray[index];
			}

    	});

    	compFuncMap.put(ComponentType.PROP, new GATEFUNC() {

			@Override
			public boolean gateOp(Component gate) {
				int index = compIndexMap.get(gate.getSingleInput());
				//System.out.println("Proposition Op called. Index of parent: "+index);
				return valueArray[index];
			}

    	});

    }

    private void addTypeTagToComponents(Set<Component> components) {
    	for (Component c : components) {
    		if (c instanceof And) {
    			c.type = ComponentType.AND;
    		} else if (c instanceof Or) {
    			c.type = ComponentType.OR;
    		} else if (c instanceof Not) {
    			c.type = ComponentType.NEG;
    		} else if (c instanceof Transition) {
    			c.type = ComponentType.TRAN;
    		} else if (c instanceof Proposition) {
    			c.type = ComponentType.PROP;
    			((Proposition)c).type = PropositionType.ELSE;
    		}
    	}
    	for (Proposition p : basePropositionList) {
    		p.type = PropositionType.BASE;
    	}
    	for (Proposition p : inputPropositionList) {
    		p.type = PropositionType.INPUT;
    	}
    }

    private Map<Proposition, Integer> getGoalMap() {
    	Map<Proposition, Integer> result = new HashMap<Proposition, Integer>();

    	for (Role rl : roles) {
    		for (Proposition p : goalPropositions.get(rl)) {
    			result.put(p, getGoalValue(p));
    		}
    	}
    	return result;
    }

    private Map<Role, Map<Move, Proposition> > getInputMoveMap() {
    	Map<Role, Map<Move, Proposition> > result = new HashMap<Role, Map<Move, Proposition>>();
    	Map<Role, Set<Proposition>> legalMap = propNet.getLegalPropositions();

    	for (int i = 0; i < roles.size(); i++) {
    		Map<Move, Proposition> mapForCurrentRole = new HashMap<Move, Proposition>();

    		Set<Proposition> legals = legalMap.get(roles.get(i));
    		for (Proposition legalProp : legals) {
    			Move mv = getMoveFromProposition(legalProp);
    			GdlSentence inputPropSentence = ProverQueryBuilder.toDoes(roles.get(i), mv);
    			Proposition inputProp = inputPropositions.get(inputPropSentence);
    			mapForCurrentRole.put(mv, inputProp);
    		}
    		result.put(roles.get(i), mapForCurrentRole);
    	}

    	return result;
    }

	/**
	 * Computes if the state is terminal. Should return the value
	 * of the terminal proposition for the state.
	 */
	@Override
	public boolean isTerminal(MachineState state) {
		long start = System.currentTimeMillis();
		initializeBasePropositions(state);
		forwardPropagation(endOfBaseProposition, terminalPropositionIndex);
		long end = System.currentTimeMillis();
		time_isTerminal += (end-start);

		//return ordering.get(terminalPropositionIndex).getValue();
		return valueArray[compIndexMap.get(terminalProposition)];
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
		long start = System.currentTimeMillis();
		initializeBasePropositions(state);

		/* Goal Proposition is always before inputProposition*/
		forwardPropagation(endOfBaseProposition, inputPropositionIndex);
		Set<Proposition> goalProps = goalPropositions.get(role);
		Proposition goalProposition = null;
		int counter = 0;
		for (Proposition p : goalProps) {
			/*
			if (p.getValue()) {
				counter++;
				goalProposition = p;
			}*/

			if (valueArray[compIndexMap.get(p)] == true) {
				counter++;
				goalProposition = p;
			}
		}

		if (counter != 1) throw new GoalDefinitionException(state, role);
		long end = System.currentTimeMillis();
		time_getGoal += (end - start);
		//return getGoalValue(goalProposition);
		return goalMap.get(goalProposition);
	}

	/**
	 * Returns the initial state. The initial state can be computed
	 * by only setting the truth value of the INIT proposition to true,
	 * and then computing the resulting state.
	 * CHECKKK
	 */
	@Override
	public MachineState getInitialState() {

		//clearBasePropositions();
		for (Proposition p :basePropositionList) {
			p.setValue(false);
		}

		initProposition.setValue(true);
		forwardPropagation(basePropositionIndex, endOfBaseProposition - 1); // -1 since it's inclusive
		MachineState baseState = getStateFromBase();
		initProposition.setValue(false);

		/*
		valueArray[compIndexMap.get(initProposition)] = true;
		forwardPropagation(basePropositionIndex, endOfBaseProposition - 1);
		//forwardPropagation(0, endOfBaseProposition - 1);
		MachineState baseState = getStateFromBaseOp();
		valueArray[compIndexMap.get(initProposition)] = false;
		*/
		return baseState;
	}

	/**
	 * Computes the legal moves for role in state.
	 */
	@Override
	public List<Move> getLegalMoves(MachineState state, Role role)
	throws MoveDefinitionException {
		long start = System.currentTimeMillis();

		List<Move> result = new ArrayList<Move>();
		initializeBasePropositions(state);
		forwardPropagation(endOfBaseProposition, inputPropositionIndex); //legal Propositions are always before inputProps
		Set<Proposition> legalProps = legalPropositions.get(role);

		for (Proposition p : legalProps) {
			//if (p.getValue()) result.add(getMoveFromProposition(p));

			if (valueArray[compIndexMap.get(p)]==true) result.add(getMoveFromProposition(p));
		}

		//if (result.size() == 0) throw new MoveDefinitionException(state, role);
		long end = System.currentTimeMillis();
		time_getLegal += (end - start);
		return result;
	}

	/**
	 * Computes the next state given state and the list of moves. CHECKK
	 */
	@Override
	public MachineState getNextState(MachineState state, List<Move> moves)
	throws TransitionDefinitionException {
		long start = System.currentTimeMillis();

		initializeBasePropositions(state);
		initializeInputPropositions(moves);
		forwardPropagation(endOfBaseProposition, ordering.size() - 1);

		Set<GdlSentence> nextProps = new HashSet<GdlSentence>();


		for (Proposition baseProp : basePropositionList) { // iterate through all basePropositions

			/* All base propositions have a single source that is a transition.
			 * If that transition is true, it means the base Proposition will be true in the next state */
			/*
			if (baseProp.getSingleInput().getValue()) {
				nextProps.add(baseProp.getName());
			}*/

			if (valueArray[compIndexMap.get(baseProp.getSingleInput())] == true)
				nextProps.add(baseProp.getName());
		}
		long end = System.currentTimeMillis();
		time_getNext += (end - start);
		return new MachineState(nextProps);
	}

	/* Helper Methods */

	/* turn off all base propositions CHECKK */
	protected void clearBasePropositions() {
		/*
		for (Proposition p :basePropositionList) {
			p.setValue(false);
		}*/
		for (int i = basePropositionIndex; i < basePropositionIndex + basePropositionSize; i++) {
			valueArray[i] = false;
		}
	}

	/* turn off all input propositions CHECKK */
	protected void clearInputPropositions(){
		/*
		for (Proposition p: inputPropositionList) {
			p.setValue(false);
		}*/
		for (int i = inputPropositionIndex; i < inputPropositionIndex + inputPropositionSize; i++) {
			valueArray[i] = false;
		}
	}

	/* sets base proposition truth values to the machine state  CHECKKK */
	protected void initializeBasePropositions(MachineState state){

		clearBasePropositions();

		Set<GdlSentence> g = state.getContents();

		/*
		for(GdlSentence s : g){
			Proposition p = basePropositions.get(s);
			p.setValue(true);
		}*/

		for (GdlSentence s : g) {
			valueArray[compIndexMap.get(basePropositions.get(s))] = true;
		}
	}

	/* sets input proposition truth values to the given list of moves CHECKK */
	protected void initializeInputPropositions(List<Move> moves)
	{
		clearInputPropositions();
		Map<Role, Integer> roleIndices = getRoleIndices();

		for (Role rl : roles) {
			int roleIndex = roleIndices.get(rl);
			Map<Move, Proposition> moveInputMapForRl = moveInputMap.get(rl);
			Proposition p = moveInputMapForRl.get(moves.get(roleIndex));

			valueArray[compIndexMap.get(p)] = true;
			//p.setValue(true);
		}
	}

	/* Start from the starting index, and propagate to the end index  INCLUSIVE !! */
	protected void forwardPropagation(int startIndex, int endIndex) {
		long start = System.currentTimeMillis();
		for (int i = startIndex; i <= endIndex; i++) {

			Component c = ordering.get(i);
			if (c.getInputs().size() != 0)
				valueArray[i] = compFuncMap.get(c.type).gateOp(c);
		}
		long end = System.currentTimeMillis();
		time_forwardPropagate += (end - start);
	}

	/* Topological Ordering */

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
			if (isDependentOnBaseProposition(dependencyMap, c, inputPropositions, basePropositions)) {
				dependentOnBase.add(c);
			}
			else notDependentOnBase.add(c); // add only if not input proposition or base proposition.
		}

		/* Setp 2) Divide All that are dependent on Base propositions into: Left and Right of terminal */
		List<Component> terminalLeft = new ArrayList<Component>();
		List<Component> terminalRight = new ArrayList<Component>();

		for (Component c : dependentOnBase) {
			/* If the given component is a source of the terminal proposition, add to Left. Else add to Right */
			if (c.equals(propNet.getTerminalProposition()) ||
					isReverseDependentOnTerminal(reverseDependencyMap, c, basePropositions)) {
				c.isConnectedToTerminal = true;
				terminalLeft.add(c);
			}
			else {
				c.isConnectedToTerminal = false;
				terminalRight.add(c);
			}
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

		/* Optimization block */
		/*
		List<Proposition> realResult = new ArrayList<Proposition>();
		int resultSize = result.size();
		boolean foundInputIndex = false;
		for (int i = 0; i < resultSize; i++) {
			if (result.get(i).type == ComponentType.PROP)
				realResult.add((Proposition)result.get(i));

			if (result.get(i).equals(propNet.getTerminalProposition())) {
				//System.out.print("FOUND TERMINAL PROPOSITION!!!!!!\n");
				terminalPropositionIndex = realResult.size()-1;
			} else if (!foundInputIndex && result.get(i).type == ComponentType.PROP && ((Proposition)result.get(i)).type == PropositionType.INPUT) {
				foundInputIndex = true;
				inputPropositionIndex = realResult.size() - 1;
			}

		}*/

		endOfBaseProposition = basePropositionIndex + basePropositionSize;
		//System.out.print("	- Optimized Ordering size: "+realResult.size()+"\n");
		System.out.print("	- Finished findidng the Topological Ordering of Game Propositions...\n");
		System.out.print("	- base Proposition range: "+basePropositionIndex+"->"+(basePropositionIndex + basePropositionSize)+"\n");
		System.out.print("	- terminal Proposition index: "+terminalPropositionIndex+"\n");
		System.out.print("	- input Proposition range: "+inputPropositionIndex+"->"+(inputPropositionIndex + inputPropositionSize)+"\n");
		//return realResult; //optimized. Ordering only contains propositions, not transitions or gates
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
				//if (baseProps.contains(source)) continue;
				if (source.type == ComponentType.PROP && ((Proposition)source).type == PropositionType.BASE) continue;

				/* if a single source is base proposition, this is dependent on the base proposition */
				//if (inputProps.contains(source)) {
				if (source.type == ComponentType.PROP && ((Proposition)source).type == PropositionType.INPUT) {
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
		System.out.print("	- Checking Topological Ordering...\n");
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
		System.out.print("	- Num Checked: "+counter+"\n");
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

	/* Factoring */

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

	public MachineState getStateFromBaseOp() {
		Set<GdlSentence> contents = new HashSet<GdlSentence>();
		for (Proposition p : propNet.getBasePropositionList()) {
			if (valueArray[compIndexMap.get(p)] == true) {
				contents.add(p.getName());
			}
		}
		return new MachineState(contents);

	}
}

