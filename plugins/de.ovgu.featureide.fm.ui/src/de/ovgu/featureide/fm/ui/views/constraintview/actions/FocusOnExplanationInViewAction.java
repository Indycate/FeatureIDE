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
package de.ovgu.featureide.fm.ui.views.constraintview.actions;

import static de.ovgu.featureide.fm.core.localization.StringTable.FOCUS_ON_EXPLANATION;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.ui.PlatformUI;

import de.ovgu.featureide.fm.core.ConstraintAttribute;
import de.ovgu.featureide.fm.core.base.IConstraint;
import de.ovgu.featureide.fm.core.base.IFeature;
import de.ovgu.featureide.fm.core.explanations.Explanation;
import de.ovgu.featureide.fm.core.explanations.fm.FeatureModelExplanation;
import de.ovgu.featureide.fm.core.explanations.fm.FeatureModelReason;
import de.ovgu.featureide.fm.ui.FMUIPlugin;
import de.ovgu.featureide.fm.ui.editors.IGraphicalFeatureModel;
import de.ovgu.featureide.fm.ui.editors.featuremodel.operations.FocusOnExplanationOperation;
import de.ovgu.featureide.fm.ui.utils.FeatureModelUtil;

/**
 * This class represents the action to Focus on the Explanation of a Constraint selected in the Constraint View.
 *
 * @author Rahel Arens
 */
public class FocusOnExplanationInViewAction extends Action {
	private final IGraphicalFeatureModel graphicalFeatureModel;
	private IStructuredSelection selection;
	private IConstraint constraint;

	public FocusOnExplanationInViewAction(IGraphicalFeatureModel graphicalFeatureModel, Object viewer) {
		super(FOCUS_ON_EXPLANATION);
		setImageDescriptor(FMUIPlugin.getDefault().getImageDescriptor("icons/monitor_obj.gif"));
		this.graphicalFeatureModel = graphicalFeatureModel;

		if (viewer instanceof TreeViewer) {
			selection = (IStructuredSelection) ((TreeViewer) viewer).getSelection();
			constraint = (IConstraint) selection.getFirstElement();
			setEnabled(hasExplanation(selection));
		}
	}

	@Override
	public void run() {
		FocusOnExplanationOperation focusOnExplanationOperation = null;
		// If model is void always show voidModelExplanation
		if (!FeatureModelUtil.getFeatureModel().getAnalyser().valid()) {
			focusOnExplanationOperation =
				new FocusOnExplanationOperation(graphicalFeatureModel, FeatureModelUtil.getFeatureModel().getAnalyser().getVoidFeatureModelExplanation());
		}
		if (constraint != null) {
			// Handler if constraint has an explanation
			if (constraint.getFeatureModel().getAnalyser().getExplanation(constraint) != null) {
				final FeatureModelExplanation<?> explanation =
					(FeatureModelExplanation<?>) constraint.getFeatureModel().getAnalyser().getExplanation(constraint);
				focusOnExplanationOperation = new FocusOnExplanationOperation(graphicalFeatureModel, explanation);
				// Check if any feature has this constraint as a reason in its explanation
			} else {
				// Iterate Features
				for (final IFeature feature : FeatureModelUtil.getFeatureModel().getFeatures()) {
					// Check if Feature has an Explanation
					final Explanation<?> featureExplanation = feature.getFeatureModel().getAnalyser().getExplanation(feature);
					if ((featureExplanation != null) && constraintIsInExplanation(featureExplanation)) {
						final FeatureModelExplanation<?> fme = (FeatureModelExplanation<?>) feature.getFeatureModel().getAnalyser().getExplanation(feature);
						focusOnExplanationOperation = new FocusOnExplanationOperation(graphicalFeatureModel, fme);
					}
				}
			}
		}
		// apply explanation collapse
		if (focusOnExplanationOperation != null) {
			try {
				PlatformUI.getWorkbench().getOperationSupport().getOperationHistory().execute(focusOnExplanationOperation, null, null);
			} catch (final ExecutionException e) {
				FMUIPlugin.getDefault().logError(e);
			}
		}
	}

	/**
	 * This method checks if the constraint appears in a given Explanation
	 */
	private boolean constraintIsInExplanation(Explanation<?> featureExplanation) {
		// Iterate Reasons
		for (final Object reason : featureExplanation.getReasons()) {
			if (reason instanceof FeatureModelReason) {
				final FeatureModelReason fmReason = (FeatureModelReason) reason;
				// Check if this Constraint is one of the reasons
				if (fmReason.getSubject().getElement().equals(constraint)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * This method checks if the selection has some explanation.
	 *
	 * @return If selection has explanation true else false.
	 */
	public boolean hasExplanation(IStructuredSelection sel) {
		if (!FeatureModelUtil.getFeatureModel().getAnalyser().valid()) {
			return true;
		}
		if (constraint == null) {
			return false;
		}
		if ((constraint.getConstraintAttribute() == ConstraintAttribute.REDUNDANT) || (constraint.getConstraintAttribute() == ConstraintAttribute.UNSATISFIABLE)
			|| (constraint.getConstraintAttribute() == ConstraintAttribute.VOID_MODEL) || (constraint.getConstraintAttribute() == ConstraintAttribute.DEAD)
			|| ((constraint.getConstraintAttribute() == ConstraintAttribute.IMPLICIT)
				|| (constraint.getConstraintAttribute() == ConstraintAttribute.TAUTOLOGY))) {
			return true;
		}
		return false;
	}
}
