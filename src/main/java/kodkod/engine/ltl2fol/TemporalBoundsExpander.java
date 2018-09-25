/* 
 * Kodkod -- Copyright (c) 2005-present, Emina Torlak
 * Pardinus -- Copyright (c) 2013-present, Nuno Macedo, INESC TEC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package kodkod.engine.ltl2fol;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import kodkod.ast.Relation;
import kodkod.ast.VarRelation;
import kodkod.instance.Bounds;
import kodkod.instance.PardinusBounds;
import kodkod.instance.Tuple;
import kodkod.instance.TupleSet;
import kodkod.instance.Universe;

/**
 * An extension to the regular Kodkod {@link Bounds bounds} that stores
 * information regarding its origin from bounds over {@link VarRelation variable
 * relations}. Translates {@link VarRelation variable relation} bounds into its
 * standard representation, by appending the {@link TemporalTranslator#STATE
 * time sig} to the bound. The bounds of static relations should remain
 * unchanged. The temporal options will define the maximum size of the trace. It
 * will also store the mapping of the variable relations into their expanded
 * versions
 *
 * @author Eduardo Pessoa, Nuno Macedo // [HASLab] temporal model finding
 */
public class TemporalBoundsExpander {

	/**
	 * Expands the old bounds by converting the bounds over variable relations into
	 * regular bounds with {@link TemporalTranslator#STATE temporal} atoms appended.
	 * It also creates and binds the relations representing the trace. If the loop
	 * is known to necessarily loop, simplifications will be applied.
	 * 
	 * @param bounds
	 *            the bounds with variable relations to be expanded.
	 * @param states
	 *            the number of distinguished states in the trace.
	 * @param unrolls
	 *            the number of trace unrolls.
	 * @param force_loop
	 *            whether the trace will necessarily loop.
	 * @param timeAtoms 
	 * @return the expanded bounds.
	 */
	public static PardinusBounds expand(PardinusBounds bounds, int states, int unrolls, boolean force_loop, List<Object> timeAtoms) {
		Universe u = expandUniverse(bounds, states, unrolls, timeAtoms);
		return expand(bounds, u, states, unrolls, force_loop);
	}

	/**
	 * Actually expands temporal bounds into their static representation as regular
	 * bounds with {@link TemporalTranslator#STATE temporal} atoms appended,
	 * unrolled a certain number of times.
	 * 
	 * Bounds are assumed to already be resolved at this point. Trace bounds with
	 * more than a state are not yet implemented. If the loop is known to
	 * necessarily loop, simplifications will be applied.
	 * 
	 * TODO support trace bounds with multiple steps.
	 * 
	 * @param bounds
	 *            the bounds with variable relations to be expanded.
	 * @param uni
	 *            the new universe
	 * @param states
	 *            the number of distinguished states in the trace.
	 * @param unrolls
	 *            the number of trace unrolls.
	 * @param force_loop
	 *            whether the trace will necessarily loop.
	 * @return the expanded bounds with the new universe.
	 */
	private static PardinusBounds expand(PardinusBounds bounds, Universe uni, int states, int unrolls, boolean force_loop) {
		if (!bounds.resolved())
			throw new IllegalArgumentException("Symbolic bounds must be resolved at this stage.");

		
		PardinusBounds newBounds = new PardinusBounds(uni);

		TupleSet tupleSetTime_unr_first;
		
		if (TemporalTranslator.alt) {
	
			String sp = TemporalTranslator.STATE_SEP;
			newBounds.boundExactly(TemporalTranslator.FIRST,
					uni.factory().setOf(uni.factory().tuple(TemporalTranslator.STATEATOM + "0"+sp+"0")));
			TupleSet last_unr = uni.factory()
					.setOf(uni.factory().tuple(TemporalTranslator.STATEATOM + (states - 1) +sp+ (unrolls - 1)));
			if (!force_loop) // otherwise last necessarily last unroll
				last_unr.add(uni.factory().tuple(TemporalTranslator.STATEATOM + (states - 1) +sp+ 0));
			newBounds.bound(TemporalTranslator.LAST, last_unr);

			newBounds.boundExactly(TemporalTranslator.LAST_, uni.factory()
					.setOf(uni.factory().tuple(TemporalTranslator.STATEATOM + (states - 1) +sp+ 0)));

			TupleSet tupleSetTime_unr = uni.factory().range(uni.factory().tuple(TemporalTranslator.STATEATOM + "0"+sp+"0"),
					uni.factory().tuple(TemporalTranslator.STATEATOM + (states - 1) +sp+ (unrolls - 1)));
			tupleSetTime_unr_first = uni.factory().range(uni.factory().tuple(TemporalTranslator.STATEATOM + "0"+sp+"0"),
					uni.factory().tuple(TemporalTranslator.STATEATOM + (states - 1) +sp+ "0"));
			if (force_loop) // then the last state must exist in every unroll
				for (int j = 0; j < unrolls; j++)
					tupleSetTime_unr_first.add(uni.factory().tuple(TemporalTranslator.STATEATOM + (states - 1) +sp+ j));
			newBounds.bound(TemporalTranslator.STATE, tupleSetTime_unr_first, tupleSetTime_unr);
	
			TupleSet tupleSetTime_unr_last = uni.factory().range(
					uni.factory().tuple(TemporalTranslator.STATEATOM + "0" +sp+ (unrolls - 1)),
					uni.factory().tuple(TemporalTranslator.STATEATOM + (states - 1) +sp+ (unrolls - 1)));
			newBounds.bound(TemporalTranslator.LOOP, tupleSetTime_unr_last);
	
			TupleSet trace_unr = uni.factory().noneOf(2);
			TupleSet trace_unr_l = uni.factory().noneOf(2);
			for (int i = 0; i < states - 1; i++) // add the prefix to lower
				trace_unr_l.add(uni.factory().tuple(TemporalTranslator.STATEATOM + i +sp+ 0,
						TemporalTranslator.STATEATOM + (i + 1) +sp+ 0));
			for (int j = 0; j < unrolls; j++) {
				for (int i = 0; i < states - 1; i++) // add the successor in non-loop
					trace_unr.add(uni.factory().tuple(TemporalTranslator.STATEATOM + i +sp+ j,
							TemporalTranslator.STATEATOM + (i + 1) +sp+ j));
				if (j < unrolls - 1)
					for (int k = 0; k < states; k++) // add the possible successors in loop
						trace_unr.add(uni.factory().tuple(TemporalTranslator.STATEATOM + (states - 1) +sp+ j,
								TemporalTranslator.STATEATOM + k +sp+ (j + 1)));
			}
			newBounds.bound(TemporalTranslator.PREFIX, trace_unr_l, trace_unr);
	
			if (unrolls > 1) { // otherwise no need for unrolls
				TupleSet unrollMap = uni.factory().noneOf(2);
				for (int i = 0; i < states; i++) {
					unrollMap.add(uni.factory().tuple(new Object[] { TemporalTranslator.STATEATOM + i +sp+ 0,
							TemporalTranslator.STATEATOM + i +sp+ 0 }));
					for (int j = 1; j < unrolls; j++)
						unrollMap.add(uni.factory().tuple(new Object[] { TemporalTranslator.STATEATOM + i +sp+ j,
								TemporalTranslator.STATEATOM + i +sp+ 0 }));
				}
				newBounds.bound(TemporalTranslator.UNROLL_MAP, unrollMap, unrollMap);
			}

		} else {
			newBounds.boundExactly(TemporalTranslator.FIRST,
					uni.factory().setOf(uni.factory().tuple(TemporalTranslator.STATEATOM + "0")));
			newBounds.boundExactly(TemporalTranslator.LAST, uni.factory().setOf(uni.factory().tuple(TemporalTranslator.STATEATOM + "" + states)));
	
			tupleSetTime_unr_first = uni.factory().range(uni.factory().tuple(TemporalTranslator.STATEATOM + "0"),
					uni.factory().tuple(TemporalTranslator.STATEATOM + states));
			newBounds.boundExactly(TemporalTranslator.STATE, tupleSetTime_unr_first);
	
			TupleSet trace_unr_l = uni.factory().noneOf(2);
			for (int i = 0; i < states; i++) // add the prefix to lower
				trace_unr_l.add(uni.factory().tuple(TemporalTranslator.STATEATOM + i,
						TemporalTranslator.STATEATOM + (i + 1)));
			newBounds.boundExactly(TemporalTranslator.PREFIX, trace_unr_l);

			newBounds.boundExactly(TemporalTranslator.L_FIRST,
					uni.factory().setOf(uni.factory().tuple(TemporalTranslator.LEVEL + "0")));
			newBounds.boundExactly(TemporalTranslator.L_LAST, 
					uni.factory().setOf(uni.factory().tuple(TemporalTranslator.LEVEL +""+(unrolls-1))));
	
			TupleSet tupleSetTime_lvl = uni.factory().range(uni.factory().tuple(TemporalTranslator.LEVEL + "0"),
					uni.factory().tuple(TemporalTranslator.LEVEL + "" + (unrolls-1)));
			newBounds.boundExactly(TemporalTranslator.LEVEL, tupleSetTime_lvl);
	
			TupleSet trace_lvl = uni.factory().noneOf(2);
			for (int i = 0; i < unrolls-1; i++) // add the prefix to lower
				trace_lvl.add(uni.factory().tuple(TemporalTranslator.LEVEL + "" + i,
						TemporalTranslator.LEVEL + "" + (i + 1)));
			newBounds.boundExactly(TemporalTranslator.L_PREFIX, trace_lvl);
			
			TupleSet tupleSetTime_unr_last = uni.factory().range(
					uni.factory().tuple(TemporalTranslator.STATEATOM + "1"),
					uni.factory().tuple(TemporalTranslator.STATEATOM + states));
			newBounds.bound(TemporalTranslator.LOOP, tupleSetTime_unr_last);
	
		}
		
		for (Relation r : bounds.relations()) {
			TupleSet tupleSetL = convert(bounds.lowerBound(r), uni);
			TupleSet tupleSetU = convert(bounds.upperBound(r), uni);
			if (r instanceof VarRelation) {
				newBounds.bound(((VarRelation) r).expanded, tupleSetL.product(tupleSetTime_unr_first),
						tupleSetU.product(tupleSetTime_unr_first));
				if (bounds.target(r) != null) {
					TupleSet tupleSetT = convert(bounds.target(r), uni);
					newBounds.setTarget(((VarRelation) r).expanded, tupleSetT.product(tupleSetTime_unr_first));
				}
				if (bounds.weight(r) != null)
					newBounds.setWeight(((VarRelation) r).expanded, bounds.weight(r));
			} else {
				newBounds.bound(r, tupleSetL, tupleSetU);
				if (bounds.target(r) != null) {
					TupleSet tupleSetT = convert(bounds.target(r), uni);
					newBounds.setTarget(r, tupleSetT);
				}
				if (bounds.weight(r) != null)
					newBounds.setWeight(r, bounds.weight(r));
			}
		}

		newBounds.amalgamated = bounds.amalgamated;
		newBounds.trivial_config = bounds.trivial_config;
		newBounds.integrated = bounds.integrated;
		newBounds.integration = bounds.integration;

		if (bounds.amalgamated() != null) {
			PardinusBounds newAmalg = expand(bounds.amalgamated(), uni, states, unrolls, force_loop);
			newBounds = new PardinusBounds(newBounds, newAmalg);
		}

		return newBounds;
	}

	/**
	 * Create a new universe by duplicating the original one and creating a given
	 * number of {@link TemporalTranlator#STATE time} atoms, unrolled a certain
	 * number of times.
	 *
	 * @param oldBounds
	 *            the original bounds.
	 * @param steps
	 *            the number of steps in the trace.
	 * @param timeAtoms 
	 * @param unrools
	 *            the number of trace unrolls.
	 * @return a new universe with the atoms of the original one plus the state
	 *         ones.
	 */
	static private Universe expandUniverse(PardinusBounds oldBounds, int steps, int unrolls, List<Object> timeAtoms) {
		List<Object> newAtoms = new ArrayList<Object>();
		Iterator<Object> it = oldBounds.universe().iterator();
		while (it.hasNext())
			newAtoms.add(it.next());

		if (TemporalTranslator.alt) {
			for (int j = 0; j < unrolls; j++)
				for (int i = 0; i < steps; i++) {
					Object x = TemporalTranslator.STATEATOM + i + TemporalTranslator.STATE_SEP + j;
					newAtoms.add(x);
					timeAtoms.add(x);
				}
		}
		else {
			for (int i = 0; i <= steps; i++) {
				Object x = TemporalTranslator.STATEATOM + i;
				newAtoms.add(x);
				timeAtoms.add(x);
			}
			for (int j = 0; j < unrolls; j++) {
				Object x = TemporalTranslator.LEVEL + "" + j;
				newAtoms.add(x);
				timeAtoms.add(x);
			}
		}
		
		return new Universe(newAtoms);
	}

	/**
	 * Converts an existing tuple set into an identical tuple set with the current
	 * universe.
	 * 
	 * @param tset
	 *            the existing tuple set.
	 * @return the converted tuple set.
	 */
	static private TupleSet convert(TupleSet tset, Universe universe) {
		TupleSet tupleSet = universe.factory().noneOf(tset.arity());
		Iterator<Tuple> itr = tset.iterator();
		while (itr.hasNext()) {
			Tuple t = itr.next();
			List<Object> l = new ArrayList<Object>();
			for (int i = 0; i < t.arity(); i++)
				l.add(t.atom(i));
			tupleSet.add(universe.factory().tuple(l));
		}
		return tupleSet;
	}

}
