package org.ggp.base.player.gamer.statemachine.propnet.mcts;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.ggp.base.util.gdl.grammar.GdlRelation;
import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.propnet.architecture.Component;
import org.ggp.base.util.propnet.architecture.PropNet;
import org.ggp.base.util.propnet.architecture.components.And;
import org.ggp.base.util.propnet.architecture.components.Not;
import org.ggp.base.util.propnet.architecture.components.Or;
import org.ggp.base.util.propnet.architecture.components.Proposition;
import org.ggp.base.util.propnet.architecture.components.Transition;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;


/**Class: PropNetUtil
 * =====================================================================
 * PropNetUtil is a singleton class that wraps the PropNet class with a set of helper methods.
 * The reason that this is designed as a singleton class rather than an inheritance of the
 * PropNet class is to achieve modularity between Sam Schreiber's code-base and ours.
 * It is crucial that we only create one instance of this class per game.
 * **/
public class PropNetUtil {

	private PropNet propnet;

	public PropNetUtil(PropNet propnet) {
		this.propnet = propnet;
	}

	/* Function: markBases
	 * ===========================================
	 * Given a MachineState, turn on the base propositions to the info encoded in the machine state.
	 * This is done by:
	 * 			1) Set all base proposition to false
	 * 			2) Set all machine state base propositions to true
	 * */
	public boolean markBases(MachineState state) {
		Map<GdlSentence, Proposition> bases = propnet.getBasePropositions();
		Set<GdlSentence> stateInfo = state.getContents();

		/* Step 1) Set all base proposition to false */
		for (Proposition p : bases.values())
			p.setValue(false);

		/* Step 2) Set target propositions to true */
		for (GdlSentence gdl : stateInfo)
			if (bases.containsKey(gdl))
				bases.get(gdl).setValue(true);
		return true;
	}


	/* Function: markActions
	 * ===========================================
	 * Given a list of Moves, turn on the input propositions to the info encoded in the machine state.
	 * This is done by:
	 * 			1) Set all input proposition to false
	 * 			2) Set all machine state input propositions to true
	 * */
	public boolean markActions(List<Move> moves) {
		Map<GdlSentence, Proposition> inputs = propnet.getInputPropositions();
		//Set<GdlSentence> stateInfo = state.getContents();

		/* Step 1) Set all input proposition to false*/
		for (Proposition p : inputs.values())
			p.setValue(false);

		/* Step 2) Set target propositions to true */
		for (Move move : moves) {
			inputs.get(move.getContents().toSentence()).setValue(true);
		}

		return true;
	}

	public boolean clearPropnet() {
		Map<GdlSentence, Proposition> bases = propnet.getBasePropositions();
		for (Proposition p : bases.values()) {
			p.setValue(false);
		}
		return true;
	}

	public boolean propMarkP(Component p) {
		/* Written to enhance readability for now. Need to optimize. */

		if (isBaseProposition((Proposition)p)) {
			return p.getValue();
		} else if (isInputProposition((Proposition)p)) {
			return p.getValue();
		} else if (isViewProposition((Proposition)p)) {
			return propMarkP(p.getSingleInput());
		} else if (isAndConnective(p)) {
			return propMarkConjunction(p);
		} else if (isOrConnective(p)) {
			return propMarkDisjunction(p);
		} else if (isNotConnective(p)) {
			return propMarkNegation(p);
		}

		return false; //shouldn't get here
	}

	public boolean propMarkNegation(Component p) {
		return !propMarkP(p);
	}

	public boolean propMarkConjunction(Component p) {
		Set<Component> sources = p.getInputs();
		for (Component c : sources) {
			if (!propMarkP(c))
				return false;
		}
		return true;
	}

	public boolean propMarkDisjunction(Component p) {
		Set<Component> sources = p.getInputs();
		for (Component c : sources) {
			if (propMarkP(c)) return true;
		}
		return false;
	}


	/* Function: isBaseProposition
	 * ============================================
	 * takes in a proposition and checks if it is a base proposition
	 * A base proposition is a PropNet node that has one transition arc
	 * */
	public boolean isBaseProposition(Proposition p) {
		Component component = p.getSingleInput();
		if (component instanceof Transition)
			return true;
		return false;
	}

	/* Function: isInputProposition
	 * ============================================
	 * takes in a proposition and checks if it is an input proposition
	 * An input proposition is a PropNet node that has no incoming arcs
	 * */
	public boolean isInputProposition(Proposition p) {
		GdlRelation relation = (GdlRelation) p.getName();
		if (relation.getName().getValue().equals("does"))
			return true;
		return false;
	}

	/* Function: isViewProposition
	 * ============================================
	 * takes in a proposition and check if it is a view proposition
	 * A view proposition is a PropNet node that has non-transition incoming arcs */
	public boolean isViewProposition(Proposition p) {
		Set<Component> inputs = p.getInputs();
		for (Component comp : inputs) {
			if (comp instanceof Transition) {
				return false;
			}
		}
		return true;
	}

	/* Function: isAndConnective
	 * ============================================
	 * takes in a component and checks if it is an AND connective
	 * */
	public boolean isAndConnective(Component c) {
		if (c instanceof And) return true;
		return false;
	}

	/* Function: isOrConnective
	 * ============================================
	 * takes in a component and checks if it is an OR connective
	 * */
	public boolean isOrConnective(Component c) {
		if (c instanceof Or) return true;
		return false;
	}

	/* Function: isNotConnective
	 * ============================================
	 * takes in a component and checks if it is a NOT connective
	 * */
	public boolean isNotConnective(Component c) {
		if (c instanceof Not) return true;
		else return false;
	}
}
