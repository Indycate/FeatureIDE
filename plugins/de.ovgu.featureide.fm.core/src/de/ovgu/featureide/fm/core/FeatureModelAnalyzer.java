/* FeatureIDE - A Framework for Feature-Oriented Software Development
 * Copyright (C) 2005-2017  FeatureIDE team, University of Magdeburg, Germany
 *
 * This file is part of FeatureIDE.
 *
 * FeatureIDE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * FeatureIDE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with FeatureIDE.  If not, see <http://www.gnu.org/licenses/>.
 *
 * See http://featureide.cs.ovgu.de/ for further information.
 */
package de.ovgu.featureide.fm.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.CheckForNull;

import org.sat4j.specs.TimeoutException;

import de.ovgu.featureide.fm.core.AnalysesCollection.ConstraintAnalysisWrapper;
import de.ovgu.featureide.fm.core.AnalysesCollection.StringToFeature;
import de.ovgu.featureide.fm.core.analysis.ConstraintProperties;
import de.ovgu.featureide.fm.core.analysis.ConstraintProperties.ConstraintDeadStatus;
import de.ovgu.featureide.fm.core.analysis.ConstraintProperties.ConstraintFalseOptionalStatus;
import de.ovgu.featureide.fm.core.analysis.ConstraintProperties.ConstraintFalseSatisfiabilityStatus;
import de.ovgu.featureide.fm.core.analysis.ConstraintProperties.ConstraintRedundancyStatus;
import de.ovgu.featureide.fm.core.analysis.FeatureModelProperties;
import de.ovgu.featureide.fm.core.analysis.FeatureProperties;
import de.ovgu.featureide.fm.core.analysis.FeatureProperties.FeatureDeterminedStatus;
import de.ovgu.featureide.fm.core.analysis.FeatureProperties.FeatureParentStatus;
import de.ovgu.featureide.fm.core.analysis.FeatureProperties.FeatureSelectionStatus;
import de.ovgu.featureide.fm.core.analysis.cnf.CNF;
import de.ovgu.featureide.fm.core.analysis.cnf.IVariables;
import de.ovgu.featureide.fm.core.analysis.cnf.LiteralSet;
import de.ovgu.featureide.fm.core.analysis.cnf.analysis.CauseAnalysis.Anomalies;
import de.ovgu.featureide.fm.core.analysis.cnf.analysis.CoreDeadAnalysis;
import de.ovgu.featureide.fm.core.analysis.cnf.analysis.HasSolutionAnalysis;
import de.ovgu.featureide.fm.core.analysis.cnf.formula.FeatureModelFormula;
import de.ovgu.featureide.fm.core.base.FeatureUtils;
import de.ovgu.featureide.fm.core.base.IConstraint;
import de.ovgu.featureide.fm.core.base.IFeature;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.base.IFeatureModelElement;
import de.ovgu.featureide.fm.core.base.IFeatureStructure;
import de.ovgu.featureide.fm.core.base.event.FeatureIDEEvent;
import de.ovgu.featureide.fm.core.base.event.IEventListener;
import de.ovgu.featureide.fm.core.explanations.Explanation;
import de.ovgu.featureide.fm.core.explanations.fm.DeadFeatureExplanation;
import de.ovgu.featureide.fm.core.explanations.fm.DeadFeatureExplanationCreator;
import de.ovgu.featureide.fm.core.explanations.fm.FalseOptionalFeatureExplanation;
import de.ovgu.featureide.fm.core.explanations.fm.FalseOptionalFeatureExplanationCreator;
import de.ovgu.featureide.fm.core.explanations.fm.RedundantConstraintExplanation;
import de.ovgu.featureide.fm.core.explanations.fm.RedundantConstraintExplanationCreator;
import de.ovgu.featureide.fm.core.filter.FeatureSetFilter;
import de.ovgu.featureide.fm.core.filter.MandatoryFeatureFilter;
import de.ovgu.featureide.fm.core.filter.base.InverseFilter;
import de.ovgu.featureide.fm.core.functional.Functional;
import de.ovgu.featureide.fm.core.job.LongRunningWrapper;
import de.ovgu.featureide.fm.core.job.monitor.IMonitor;
import de.ovgu.featureide.fm.core.job.monitor.NullMonitor;

/**
 * A collection of methods for working with {@link IFeatureModel} will replace the corresponding methods in {@link IFeatureModel}
 *
 * @author Soenke Holthusen
 * @author Florian Proksch
 * @author Stefan Krueger
 * @author Marcus Pinnecke (Feature Interface)
 */
public class FeatureModelAnalyzer implements IEventListener {

	private final FeatureModelFormula formula;
	private final IFeatureModel featureModel;
	private final List<IConstraint> constraints;

	private final AnalysesCollection analysesCollection;

	public void reset() {
		analysesCollection.reset(formula);
	}

	public FeatureModelAnalyzer(IFeatureModel featureModel) {
		this(new FeatureModelFormula(featureModel));
	}

	public FeatureModelAnalyzer(FeatureModelFormula formula) {
		this.formula = formula;
		featureModel = formula.getFeatureModel();
		constraints = featureModel.getConstraints();
		analysesCollection = new AnalysesCollection();

		analysesCollection.init(formula);
		analysesCollection.reset(formula);
	}

	public boolean isValid() {
		final Boolean result = analysesCollection.validAnalysis.getResult();
		return result == null ? false : result;
	}

	public List<IFeature> getCoreFeatures() {
		final LiteralSet result = analysesCollection.coreDeadAnalysis.getResult();
		if (result == null) {
			return Collections.emptyList();
		}
		return Functional.mapToList(formula.getCNF().getVariables().convertToString(result, true, false, false), new StringToFeature(featureModel));
	}

	public List<IFeature> getDeadFeatures() {
		final LiteralSet result = analysesCollection.coreDeadAnalysis.getResult();
		if (result == null) {
			return Collections.emptyList();
		}
		return Functional.mapToList(formula.getCNF().getVariables().convertToString(result, false, true, false), new StringToFeature(featureModel));
	}

	/**
	 * Returns the list of features that occur in all variants, where one of the given features is selected. If the given list of features is empty, this method
	 * will calculate the features that are present in all variants specified by the feature model.
	 *
	 * @return a list of features that is common to all variants
	 */
	public List<IFeature> getCommonFeatures() {
		final LiteralSet result = analysesCollection.coreDeadAnalysis.getResult();
		if (result == null) {
			return Collections.emptyList();
		}
		final Set<IFeature> uncommonFeatures =
			Functional.toSet(Functional.map(formula.getCNF().getVariables().convertToString(result, true, true, false), new StringToFeature(featureModel)));
		return Functional.mapToList(featureModel.getFeatures(), new InverseFilter<>(new FeatureSetFilter(uncommonFeatures)),
				new Functional.IdentityFunction<IFeature>());
	}

	public List<List<IFeature>> getAtomicSets() {
		final List<LiteralSet> result = analysesCollection.atomicSetAnalysis.getResult();
		if (result == null) {
			return Collections.emptyList();
		}

		final CNF cnf = formula.getCNF();
		final ArrayList<List<IFeature>> resultList = new ArrayList<>();
		for (final LiteralSet literalList : result) {
			final List<IFeature> setList = new ArrayList<>();
			resultList.add(Functional.mapToList(cnf.getVariables().convertToString(literalList, true, true, false), new StringToFeature(featureModel)));

			for (final int literal : literalList.getLiterals()) {
				final IFeature feature = featureModel.getFeature(cnf.getVariables().getName(literal));
				if (feature != null) {
					setList.add(feature);
				}
			}

		}
		return resultList;
	}

	/**
	 * Calculations for indeterminate hidden features
	 *
	 * @param changedAttributes
	 */
	public List<IFeature> getIndeterminedHiddenFeatures() {
		final LiteralSet result = analysesCollection.determinedAnalysis.getResult();
		if (result == null) {
			return Collections.emptyList();
		}
		return Functional.mapToList(formula.getCNF().getVariables().convertToString(result, true, false, false), new StringToFeature(featureModel));
	}

	public List<IFeature> getFalseOptionalFeatures() {
		final List<IFeature> optionalFeatures = Functional.filterToList(featureModel.getFeatures(), new InverseFilter<>(new MandatoryFeatureFilter()));
		final List<LiteralSet> result = getFalseOptionalFeatures(optionalFeatures);

		final List<IFeature> resultList = new ArrayList<>();
		int i = 0;
		for (final IFeature iFeature : optionalFeatures) {
			if (result.get(i++) != null) {
				resultList.add(iFeature);
			}
		}

		return resultList;
	}

	private List<LiteralSet> getFalseOptionalFeatures(final List<IFeature> optionalFeatures) {
		analysesCollection.foAnalysis.setOptionalFeatures(optionalFeatures);
		final List<LiteralSet> result = analysesCollection.foAnalysis.getResult();
		if (result == null) {
			return Collections.emptyList();
		}
		return result;
	}

	public List<IConstraint> getContradictoryConstraints() {
		return getConstraintAnalysisResults(getVoidConstraints(), analysesCollection.constraintContradictionAnalysis);
	}

	public List<IConstraint> getVoidConstraints() {
		return getConstraintAnalysisResults(constraints, analysesCollection.constraintVoidAnalysis);
	}

	public List<IConstraint> getTautologyConstraints() {
		return getConstraintAnalysisResults(getRedundantConstraints(), analysesCollection.constraintTautologyAnalysis);
	}

	public List<IConstraint> getRedundantConstraints() {
		return getConstraintAnalysisResults(constraints, analysesCollection.constraintRedundancyAnalysis);
	}

	private List<IConstraint> getConstraintAnalysisResults(List<IConstraint> constraints, ConstraintAnalysisWrapper<?> analysisWrapper) {
		analysisWrapper.setConstraints(constraints);
		final List<LiteralSet> result = analysisWrapper.getResult();
		if (result == null) {
			return Collections.emptyList();
		}

		final List<IConstraint> resultList = new ArrayList<>();
		for (int i = 0; i < analysisWrapper.getClauseGroupSize().length; i++) {
			if (result.get(i) != null) {
				resultList.add(constraints.get(i));
			}
		}
		return resultList;
	}

	public List<IConstraint> getAnomalyConstraints() {
		final List<Anomalies> result = analysesCollection.constraintAnomaliesAnalysis.getResult();
		if (result == null) {
			return Collections.emptyList();
		}

		final CNF cnf = formula.getCNF();

		final List<IConstraint> resultList = new ArrayList<>();
		for (int i = 0; i < analysesCollection.constraintAnomaliesAnalysis.getClauseGroupSize().length; i++) {
			final Anomalies anomalies = result.get(i);
			if (anomalies != null) {
				if (anomalies.getRedundantClauses() != null) {
					final ArrayList<IFeature> falseOptionalFeatures = new ArrayList<>();
					for (final LiteralSet literalSet : anomalies.getRedundantClauses()) {
						if (literalSet != null) {
							falseOptionalFeatures.add(featureModel.getFeature(cnf.getVariables().getName(literalSet.getLiterals()[1])));
						}
					}
					final IConstraint constraint = constraints.get(i);
					getConstraintProperties(constraint).setFalseOptionalFeatures(falseOptionalFeatures);
					resultList.add(constraint);
				}
				if (anomalies.getDeadVariables() != null) {
					final IConstraint constraint = constraints.get(i);
					getConstraintProperties(constraint).setDeadFeatures(Functional.mapToList(
							cnf.getVariables().convertToString(anomalies.getDeadVariables(), false, true, false), new StringToFeature(featureModel)));
					resultList.add(constraint);
				}
			}
		}

		return resultList;
	}

	public FeatureProperties getFeatureProperties(IFeature feature) {
		return analysesCollection.featurePropertiesMap.get(feature);
	}

	public ConstraintProperties getConstraintProperties(IConstraint constraint) {
		return analysesCollection.constraintPropertiesMap.get(constraint);
	}

	public FeatureModelProperties getFeatureModelProperties() {
		return analysesCollection.featureModelProperties;
	}

	/**
	 * @param monitor
	 * @return
	 * @return Hashmap: key entry is Feature/Constraint, value usually indicating the kind of attribute
	 */
	/*
	 * check all changes of this method and called methods with the related tests and benchmarks, see fm.core-test plug-in think about performance (no
	 * unnecessary or redundant calculations) Hashing might be fast for locating features, but creating a HashSet is costly So LinkedLists are much faster
	 * because the number of feature in the set is usually small (e.g. dead features)
	 */
	public Map<IFeatureModelElement, Object> analyzeFeatureModel(IMonitor monitor) {
		// TODO !!! use monitor
		if (monitor == null) {
			monitor = new NullMonitor();
		}

		updateFeatures(monitor);

		updateConstraints(monitor);

		return analysesCollection.elementPropertiesMap;
	}

	public void updateConstraints() {
		updateConstraints(null);
	}

	public void updateConstraints(IMonitor monitor) {
		if (analysesCollection.isCalculateConstraints()) {
			if (monitor == null) {
				monitor = new NullMonitor();
			}
			// set default values for constraint properties
			for (final IConstraint constraint : featureModel.getConstraints()) {
				if (analysesCollection.constraintRedundancyAnalysis.isEnabled()) {
					getConstraintProperties(constraint).setConstraintRedundancyStatus(ConstraintRedundancyStatus.NORMAL);
				}
				if (analysesCollection.constraintVoidAnalysis.isEnabled()) {
					getConstraintProperties(constraint).setConstraintSatisfiabilityStatus(ConstraintFalseSatisfiabilityStatus.SATISFIABLE);
				}
				if (analysesCollection.constraintAnomaliesAnalysis.isEnabled()) {
					getConstraintProperties(constraint).setConstraintFalseOptionalStatus(ConstraintFalseOptionalStatus.NORMAL);
					getConstraintProperties(constraint).setConstraintDeadStatus(ConstraintDeadStatus.NORMAL);
				}
			}

			// get constraint anomalies
			for (final IConstraint constraint : getRedundantConstraints()) {
				getConstraintProperties(constraint).setConstraintRedundancyStatus(ConstraintRedundancyStatus.REDUNDANT);
			}
			for (final IConstraint constraint : getTautologyConstraints()) {
				getConstraintProperties(constraint).setConstraintRedundancyStatus(ConstraintRedundancyStatus.TAUTOLOGY);
			}
			for (final IConstraint constraint : getVoidConstraints()) {
				getConstraintProperties(constraint).setConstraintSatisfiabilityStatus(ConstraintFalseSatisfiabilityStatus.VOID_MODEL);
			}
			for (final IConstraint constraint : getContradictoryConstraints()) {
				getConstraintProperties(constraint).setConstraintSatisfiabilityStatus(ConstraintFalseSatisfiabilityStatus.UNSATISFIABLE);
			}
			for (final IConstraint constraint : getAnomalyConstraints()) {
				final ConstraintProperties constraintProperties = getConstraintProperties(constraint);
				if (!constraintProperties.getDeadFeatures().isEmpty()) {
					constraintProperties.setConstraintDeadStatus(ConstraintDeadStatus.DEAD);
				}
				if (!constraintProperties.getFalseOptionalFeatures().isEmpty()) {
					constraintProperties.setConstraintFalseOptionalStatus(ConstraintFalseOptionalStatus.FALSE_OPTIONAL);
				}
			}
		}
	}

	public void updateFeatures() {
		updateFeatures(null);
	}

	public void updateFeatures(IMonitor monitor) {
		if (analysesCollection.isCalculateFeatures()) {
			if (monitor == null) {
				monitor = new NullMonitor();
			}
			// set default values for feature properties
			for (final IFeature feature : featureModel.getFeatures()) {
				getFeatureProperties(feature).setFeatureSelectionStatus(FeatureSelectionStatus.COMMON);
				getFeatureProperties(feature).setFeatureDeterminedStatus(FeatureDeterminedStatus.UNKNOWN);

				final IFeatureStructure structure = feature.getStructure();
				final IFeatureStructure parent = structure.getParent();
				if (parent == null) {
					getFeatureProperties(feature).setFeatureParentStatus(FeatureParentStatus.MANDATORY);
				} else {
					if (parent.isAnd()) {
						if (structure.isMandatorySet()) {
							getFeatureProperties(feature).setFeatureParentStatus(FeatureParentStatus.MANDATORY);
						} else {
							getFeatureProperties(feature).setFeatureParentStatus(FeatureParentStatus.OPTIONAL);
						}
					} else {
						getFeatureProperties(feature).setFeatureParentStatus(FeatureParentStatus.GROUP);
					}
				}
			}

			// get feature anomalies
			for (final IFeature feature : getDeadFeatures()) {
				getFeatureProperties(feature).setFeatureSelectionStatus(FeatureSelectionStatus.DEAD);
			}
			for (final IFeature feature : getFalseOptionalFeatures()) {
				getFeatureProperties(feature).setFeatureParentStatus(FeatureParentStatus.FALSE_OPTIONAL);
			}
			for (final IFeature feature : getIndeterminedHiddenFeatures()) {
				getFeatureProperties(feature).setFeatureDeterminedStatus(FeatureDeterminedStatus.INDETERMINATE_HIDDEN);
			}
		}
	}

	// TODO implement as analysis
	public int countConcreteFeatures() {
		int number = 0;
		for (final IFeature feature : featureModel.getFeatures()) {
			if (feature.getStructure().isConcrete()) {
				number++;
			}
		}
		return number;
	}

	// TODO implement as analysis
	public int countHiddenFeatures() {
		int number = 0;
		for (final IFeature feature : featureModel.getFeatures()) {
			final IFeatureStructure structure = feature.getStructure();
			if (structure.isHidden() || structure.hasHiddenParent()) {
				number++;
			}
		}
		return number;
	}

	// TODO implement as analysis
	public int countTerminalFeatures() {
		int number = 0;
		for (final IFeature feature : featureModel.getFeatures()) {
			if (!feature.getStructure().hasChildren()) {
				number++;
			}
		}
		return number;
	}

	// TODO Remove???
	/**
	 * Listens to feature model changes. Resets its formula if necessary.
	 */
	@Override
	public void propertyChange(FeatureIDEEvent event) {
		switch (event.getEventType()) {
		case ALL_FEATURES_CHANGED_NAME_TYPE: // Required because feature names are used as variable names.
		case CHILDREN_CHANGED:
		case CONSTRAINT_ADD:
		case CONSTRAINT_DELETE:
		case CONSTRAINT_MODIFY:
		case FEATURE_ADD:
		case FEATURE_ADD_ABOVE:
		case FEATURE_DELETE:
		case FEATURE_MODIFY: // TODO If a formula reset is required for this event type, remove this comment. Otherwise, remove this case.
		case FEATURE_NAME_CHANGED: // Required because feature names are used as variable names.
		case GROUP_TYPE_CHANGED:
		case HIDDEN_CHANGED: // TODO If a formula reset is required for this event type, remove this comment. Otherwise, remove this case.
		case MANDATORY_CHANGED:
		case MODEL_DATA_CHANGED:
		case MODEL_DATA_OVERRIDDEN:
		case PARENT_CHANGED:
		case STRUCTURE_CHANGED:
			formula.resetFormula();
			break;
		default:
			break;
		}
	}

	/**
	 * Returns an explanation why the given feature model element is defect.
	 *
	 * @param modelElement potentially defect feature model element; not null
	 * @return an explanation; null if it cannot be explained
	 */
	public Explanation<?> getExplanation(IFeatureModelElement modelElement) {
		return getExplanation(modelElement, formula);
	}

	/**
	 * Returns an explanation why the given feature model element is defect or null if it cannot be explained.
	 *
	 * @param modelElement potentially defect feature model element
	 * @param context another feature model that is used as reference for the explanations
	 * @return an explanation why the given feature model element is defect or null if it cannot be explained
	 */
	@CheckForNull
	public Explanation<?> getExplanation(IFeatureModelElement modelElement, FeatureModelFormula context) {
		if (modelElement instanceof IFeature) {
			return getFeatureExplanation((IFeature) modelElement, context);
		} else if (modelElement instanceof IConstraint) {
			return getConstraintExplanation((IConstraint) modelElement, context);
		} else {
			return null;
		}
	}

	/**
	 * Returns an explanation why the given constraint is defect or null if it cannot be explained.
	 *
	 * @param constraint potentially defect constraint
	 * @return an explanation why the given constraint is defect or null if it cannot be explained
	 */
	public Explanation<?> getConstraintExplanation(IConstraint constraint, FeatureModelFormula context) {
		synchronized (constraint) {
			Explanation<?> explanation = null;
			final ConstraintProperties constraintProperties = getConstraintProperties(constraint);

			if (constraintProperties != null) {
				switch (constraintProperties.getConstraintRedundancyStatus()) {
				case REDUNDANT:
				case TAUTOLOGY:
					break;
				case IMPLICIT:
					explanation = constraintProperties.getRedundantExplanation();
					if (explanation != null) {
						// TODO use context
						explanation = analysesCollection.createExplanation(analysesCollection.redundantConstraintExplanationCreator, constraint, context);
						constraintProperties.setRedundantExplanation(explanation);
					}
					break;
				default:
					break;
				}
			}
			return explanation;
		}
	}

	/**
	 * Returns an explanation why the given feature is defect or null if it cannot be explained.
	 *
	 * @param feature potentially defect feature
	 * @return an explanation why the given feature is defect or null if it cannot be explained
	 */
	public Explanation<?> getFeatureExplanation(IFeature feature, FeatureModelFormula context) {
		synchronized (feature) {
			Explanation<?> explanation = null;
			final FeatureProperties featureProperties = getFeatureProperties(feature);
			if (featureProperties != null) {
				switch (featureProperties.getFeatureSelectionStatus()) {
				case DEAD:
					explanation = featureProperties.getDeadExplanation();
					if (explanation != null) {
						explanation = analysesCollection.createExplanation(analysesCollection.deadFeatureExplanationCreator, feature, context);
						featureProperties.setDeadExplanation(explanation);
					}
					break;
				default:
					break;
				}
				switch (featureProperties.getFeatureParentStatus()) {
				case FALSE_OPTIONAL:
					explanation = featureProperties.getFalseOptionalExplanation();
					if (explanation != null) {
						explanation = analysesCollection.createExplanation(analysesCollection.falseOptionalFeatureExplanationCreator, feature, context);
						featureProperties.setFalseOptionalExplanation(explanation);
					}
					break;
				default:
					break;
				}
			}
			return explanation;
		}
	}

	/**
	 * <p> Returns whether the conjunction of A always implies the disjunction of B in the current feature model. </p>
	 *
	 * <p> In other words, the following satisfiability query is checked:
	 *
	 * <pre> TAUT(FM &rArr; ((&and;<sub>a&in;A</sub> a) &rArr; (&or;<sub>b&in;B</sub> b))) </pre> </p>
	 *
	 * <p> Note that this formula is always true if B is empty. </p>
	 *
	 * @param a set of features that form a conjunction
	 * @param b set of features that form a disjunction
	 * @return whether the conjunction of A always implies the disjunction of B in the current feature model
	 * @throws TimeoutException
	 *
	 * @deprecated Use ConfigurationPropagator instead.
	 */
	@Deprecated
	public boolean checkImplies(Collection<IFeature> a, Collection<IFeature> b) {
		if (b.isEmpty()) {
			return true;
		}

		final CNF cnf = formula.getCNF();
		final IVariables variables = cnf.getVariables();

		// (A1 and ... or An) => (B1 or ... or Bm)
		// |= -A1 or ... or -An or B1 or ... or Bm
		// |= -(A1 and ... and An and -B1 and ... and -Bm)
		final int[] literals = new int[a.size() + b.size()];
		int index = 0;
		for (final IFeature feature : b) {
			literals[index++] = -variables.getVariable(feature.getName());
		}
		for (final IFeature feature : a) {
			literals[index++] = variables.getVariable(feature.getName());
		}

		final HasSolutionAnalysis analysis = new HasSolutionAnalysis(cnf);
		analysis.setAssumptions(new LiteralSet(literals));

		return LongRunningWrapper.runMethod(analysis);
	}

	/**
	 * @deprecated Use ConfigurationPropagator instead.
	 */
	@Deprecated
	public boolean checkIfFeatureCombinationPossible(IFeature feature1, Collection<IFeature> dependingFeatures) {
		if (dependingFeatures.isEmpty()) {
			return true;
		}

		final CNF cnf = formula.getCNF();
		final IVariables variables = cnf.getVariables();

		final CoreDeadAnalysis analysis = new CoreDeadAnalysis(cnf);
		analysis.setAssumptions(new LiteralSet(variables.getVariable(feature1.getName())));
		final LiteralSet result = LongRunningWrapper.runMethod(analysis);

		final LiteralSet dependingVariables = variables.convertToVariables(Functional.mapToList(dependingFeatures, FeatureUtils.GET_FEATURE_NAME), false);
		final LiteralSet negativeVariables = result.retainAll(dependingVariables);
		return negativeVariables.isEmpty();
	}

	/**
	 * Returns an explanation why the feature model is void. That is the same explanation for why its root feature is dead.
	 *
	 * @return an explanation; null if it cannot be explained
	 */
	public DeadFeatureExplanation getVoidFeatureModelExplanation() {
		return getVoidFeatureModelExplanation(featureModel);
	}

	/**
	 * Returns an explanation why the given feature model is void. That is the same explanation for why its root feature is dead.
	 *
	 * @param fm potentially void feature model; not null
	 * @return an explanation; null if it cannot be explained
	 */
	public DeadFeatureExplanation getVoidFeatureModelExplanation(IFeatureModel fm) {
		return getDeadFeatureExplanation(fm, FeatureUtils.getRoot(fm));
	}

	/**
	 * Returns an explanation why the given feature is dead.
	 *
	 * @param feature potentially dead feature; not null
	 * @return an explanation; null if it cannot be explained
	 */
	public DeadFeatureExplanation getDeadFeatureExplanation(IFeature feature) {
		return getDeadFeatureExplanation(featureModel, feature);
	}

	/**
	 * Adds an explanation why the given feature is dead.
	 *
	 * @param fm feature model containing the feature; not null
	 * @param feature potentially dead feature; not null
	 * @return an explanation; null if it cannot be explained
	 */
	public DeadFeatureExplanation getDeadFeatureExplanation(IFeatureModel fm, IFeature feature) {
		if (!analysesCollection.deadFeatureExplanations.containsKey(feature)) {
			addDeadFeatureExplanation(fm, feature);
		}
		return analysesCollection.deadFeatureExplanations.get(feature);
	}

	/**
	 * Adds an explanation why the given feature is dead.
	 *
	 * @param fm feature model containing the feature; not null
	 * @param feature potentially dead feature; not null
	 */
	private void addDeadFeatureExplanation(IFeatureModel fm, IFeature feature) {
		final DeadFeatureExplanationCreator creator;
		if (fm == featureModel) {
			creator = analysesCollection.deadFeatureExplanationCreator;
		} else {
			creator = analysesCollection.explanationCreatorFactory.getDeadFeatureExplanationCreator();
			creator.setFeatureModel(fm);
		}
		creator.setSubject(feature);
		analysesCollection.deadFeatureExplanations.put(feature, creator.getExplanation());
	}

	/**
	 * Returns an explanation why the given feature is false-optional.
	 *
	 * @param feature potentially false-optional feature; not null
	 * @return an explanation; null if it cannot be explained
	 */
	public FalseOptionalFeatureExplanation getFalseOptionalFeatureExplanation(IFeature feature) {
		return getFalseOptionalFeatureExplanation(featureModel, feature);
	}

	/**
	 * Returns an explanation why the given feature is false-optional.
	 *
	 * @param fm feature model containing the feature; not null
	 * @param feature potentially false-optional feature; not null
	 * @return an explanation; null if it cannot be explained
	 */
	public FalseOptionalFeatureExplanation getFalseOptionalFeatureExplanation(IFeatureModel fm, IFeature feature) {
		if (!analysesCollection.falseOptionalFeatureExplanations.containsKey(feature)) {
			addFalseOptionalFeatureExplanation(fm, feature);
		}
		return analysesCollection.falseOptionalFeatureExplanations.get(feature);
	}

	/**
	 * Adds an explanation why the given feature is false-optional.
	 *
	 * @param fm feature model containing the feature; not null
	 * @param feature potentially false-optional feature; not null
	 */
	private void addFalseOptionalFeatureExplanation(IFeatureModel fm, IFeature feature) {
		final FalseOptionalFeatureExplanationCreator creator;
		if (fm == featureModel) {
			creator = analysesCollection.falseOptionalFeatureExplanationCreator;
		} else {
			creator = analysesCollection.explanationCreatorFactory.getFalseOptionalFeatureExplanationCreator();
			creator.setFeatureModel(fm);
		}
		creator.setSubject(feature);
		analysesCollection.falseOptionalFeatureExplanations.put(feature, creator.getExplanation());
	}

	/**
	 * Returns an explanation why the given constraint is redundant.
	 *
	 * @param constraint potentially redundant constraint; not null
	 * @return an explanation; null if it cannot be explained
	 */
	public RedundantConstraintExplanation getRedundantConstraintExplanation(IConstraint constraint) {
		return getRedundantConstraintExplanation(featureModel, constraint);
	}

	/**
	 * Returns an explanation why the given constraint is redundant.
	 *
	 * @param constraint potentially redundant constraint; not null
	 * @return an explanation; null if it cannot be explained
	 */
	public RedundantConstraintExplanation getRedundantConstraintExplanation(IFeatureModel fm, IConstraint constraint) {
		if (!analysesCollection.redundantConstraintExplanations.containsKey(constraint)) {
			addRedundantConstraintExplanation(fm, constraint);
		}
		return analysesCollection.redundantConstraintExplanations.get(constraint);
	}

	/**
	 * <p> Adds an explanation why the given constraint is redundant. </p>
	 *
	 * <p> Uses the given feature model, which may differ from the default feature model stored in this instance. This is for example the case when explaining
	 * implicit constraints in subtree models. </p>
	 *
	 * @param fm feature model containing the constraint; not null
	 * @param constraint potentially redundant constraint; not null
	 */
	private void addRedundantConstraintExplanation(IFeatureModel fm, IConstraint constraint) {
		final RedundantConstraintExplanationCreator creator;
		if (fm == featureModel) {
			creator = analysesCollection.redundantConstraintExplanationCreator;
		} else {
			creator = analysesCollection.explanationCreatorFactory.getRedundantConstraintExplanationCreator();
			creator.setFeatureModel(fm);
		}
		creator.setSubject(constraint);
		analysesCollection.redundantConstraintExplanations.put(constraint, creator.getExplanation());
	}

	public AnalysesCollection getAnalysesCollection() {
		return analysesCollection;
	}

}
