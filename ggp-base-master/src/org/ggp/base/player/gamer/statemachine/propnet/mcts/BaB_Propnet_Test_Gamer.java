package org.ggp.base.player.gamer.statemachine.propnet.mcts;

import java.util.List;

import org.ggp.base.player.gamer.event.GamerSelectedMoveEvent;
import org.ggp.base.player.gamer.statemachine.sample.SampleGamer;
import org.ggp.base.util.gdl.grammar.Gdl;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.cache.CachedStateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.prover.ProverStateMachine;

public class BaB_Propnet_Test_Gamer extends SampleGamer {

	@Override
	public void stateMachineMetaGame(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
	{
		System.out.println("Metagame. Preparing to test the state machine");

		StateMachine stateMachine = getStateMachine();
		ProverStateMachine psm = (ProverStateMachine)stateMachine;
		List<Gdl> gdlDescription = psm.gdlDescription;

		// The only line you have to adapt in this file
		StateMachine stateMachineX = new CachedStateMachine(new PropNetStateMachine());
		//StateMachine stateMachineX = new CachedStateMachine(new OptimizedPropNetStateMachine());

		try {
			stateMachineX.initialize(gdlDescription);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		MachineState rootState = stateMachine.getInitialState();
		MachineState rootStateX = stateMachineX.getInitialState();
		if(!compare(rootState, rootStateX)){
			System.out.println("Initial states are different");
			System.out.println(rootState);
			System.out.println(rootStateX);
			return;
		}

		long finishBy = timeout - 1000;

		int nbExpansion = 0;
		boolean abort = false;

		while(System.currentTimeMillis() < finishBy && !abort){
			MachineState state = rootState;

			while(true){
				boolean isTerminal = stateMachine.isTerminal(state);
				boolean isTerminalX = stateMachineX.isTerminal(state);
				if(!compare(isTerminal, isTerminalX)){
					System.out.println("DISCREPANCY between isTerminal values");
					System.out.println("State : " + state);
					abort = true;
					break;
				}

				if(isTerminal){
					List<Integer> goal = stateMachine.getGoals(state);
					List<Integer> goalX = stateMachineX.getGoals(state);
					if(!compare(goal, goalX)){
						System.out.println("DISCREPANCY between goal values");
						System.out.println(goal);
						System.out.println(goalX);
						abort = true;
						break;
					}
					break;
				}

				for(Role role : stateMachine.getRoles()){
					List<Move> moves = stateMachine.getLegalMoves(state, role);
					List<Move> movesX = stateMachineX.getLegalMoves(state, role);
					if(!compare(moves, movesX, role)){
						System.out.println("DISCREPANCY between legal moves for role " + role);
						System.out.println(moves);
						System.out.println(movesX);
						abort = true;
						break;
					}
				}

				List<Move> jointMove = stateMachine.getRandomJointMove(state);


				MachineState nextState = stateMachine.getNextState(state, jointMove);
				MachineState nextStateX = stateMachineX.getNextState(state, jointMove);
				if(!compare(nextState, nextStateX)){
					System.out.println("DISCREPANCY between next states");
					System.out.println("Previous state : " + state);
					System.out.println("Joint move : " + jointMove);
					System.out.println("New state : " + nextState);
					System.out.println("New stateX : " + nextStateX);

					abort = true;
					break;
				}

				state = nextState;
				nbExpansion++;
			}
		}

		System.out.println("Metagaming finished");
		System.out.println("Nb expansion : " + nbExpansion);
	}

	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException,
			GoalDefinitionException {
		// TODO Auto-generated method stub
		long start = System.currentTimeMillis();

		List<Move> moves = getStateMachine().getLegalMoves(getCurrentState(), getRole());
		Move selection = moves.get(0);
		long stop = System.currentTimeMillis();

		notifyObservers(new GamerSelectedMoveEvent(moves, selection, stop - start));
		return selection;
	}

	/**
	 * Four helper functions
	 * A bit overkill
	 */
	public boolean compare(MachineState s, MachineState sX){
		if(!s.equals(sX)){
			return false;
		}
		return true;
	}

	public boolean compare(boolean b, boolean bX){
		if(b != bX){
			return false;
		}
		return true;
	}

	private boolean compare(List<Integer> l, List<Integer> lX){
		if(!l.equals(lX)){
			return false;
		}
		return true;
	}

	private boolean compare(List<Move> l, List<Move> lX, Role r){
		for(Move m : l){
			if(!lX.contains(m)){
				return false;
			}
		}
		for(Move m : lX){
			if(!l.contains(m)){
				return false;
			}
		}
		return true;
	}

	@Override
	public StateMachine getInitialStateMachine() {
		return new ProverStateMachine();
	}

}
