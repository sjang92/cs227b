package org.ggp.base.player.gamer.statemachine.propnet.mcts;

import java.util.List;

import org.ggp.base.player.gamer.event.GamerSelectedMoveEvent;
import org.ggp.base.player.gamer.statemachine.sample.SampleGamer;
import org.ggp.base.util.propnet.architecture.Component;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

public class BaB_Propnet_Speedtest_Gamer extends SampleGamer {

	@Override
	public StateMachine getInitialStateMachine() {
		return new PropNetStateMachine();
		//return new OptimizedPropNetStateMachine();
		//return new ProverStateMachine();
	}

	@Override
	public void stateMachineMetaGame(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
	{
		System.out.println("Metagame. Speed benchmark started.");
		PropNetStateMachine stateMachine = (PropNetStateMachine) getStateMachine();
		MachineState rootState = stateMachine.getInitialState();

		List<Component> subGraph = stateMachine.processor.subGraph;

		long start = System.currentTimeMillis();
		long finishBy = timeout - 1000;

		int nbExpansion = 0;

		while(System.currentTimeMillis() < finishBy){
			MachineState state = rootState;

			while(true){
				boolean isTerminal = stateMachine.isTerminal(state);
				if(isTerminal){
					List<Integer> goal = stateMachine.getGoals(state);
					break;
				}

				List<Move> jointMove = stateMachine.getRandomJointMove(state);

				MachineState nextState = stateMachine.getNextState(state, jointMove);
				state = nextState;

				nbExpansion++;
			}
		}

		long end = System.currentTimeMillis();
		System.out.println("Metagaming finished");
		System.out.println("Nb expansion/second : " + 1000*nbExpansion/(end-start));
		/*
		System.out.println("time_getLegal: "+stateMachine.time_getLegal);
		System.out.println("time_getNext: "+stateMachine.time_getNext);
		System.out.println("time_getGoal: "+stateMachine.time_getGoal);
		System.out.println("time_propagate: "+stateMachine.time_forwardPropagate);
		System.out.println("time_isTerminal: "+stateMachine.time_isTerminal);
		System.out.println("call_forwardPropagate: "+stateMachine.call_forwardPropagate);*/

	}

	/**
	 * A legal gamer
	 */
	@Override
	public Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
	{
		long start = System.currentTimeMillis();

		List<Move> moves = getStateMachine().getLegalMoves(getCurrentState(), getRole());
		Move selection = moves.get(0);
		long stop = System.currentTimeMillis();

		notifyObservers(new GamerSelectedMoveEvent(moves, selection, stop - start));
		return selection;
	}


}
