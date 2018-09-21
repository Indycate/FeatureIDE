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
package de.ovgu.featureide.fm.ui.editors.featuremodel.operations;

import java.util.List;

import org.eclipse.draw2d.geometry.Dimension;
import org.eclipse.draw2d.geometry.Point;
import org.eclipse.draw2d.geometry.Rectangle;

import de.ovgu.featureide.fm.core.base.IFeatureStructure;
import de.ovgu.featureide.fm.ui.editors.FeatureUIHelper;
import de.ovgu.featureide.fm.ui.editors.IGraphicalElement;
import de.ovgu.featureide.fm.ui.editors.IGraphicalFeature;
import de.ovgu.featureide.fm.ui.editors.featuremodel.figures.CollapsedDecoration;
import de.ovgu.featureide.fm.ui.editors.featuremodel.figures.ConnectionDecoration;

/**
 * TODO description
 *
 * @author Insansa Michel
 * @author Malek Badeer
 */
public class FeatureModelBounds {

	public Rectangle getFeatureModelBounds(List<? extends IGraphicalElement> elements) {
		final Point min = new Point(Integer.MAX_VALUE, Integer.MAX_VALUE);
		final Point max = new Point(Integer.MIN_VALUE, Integer.MIN_VALUE);

		final Dimension labelDimension = new Dimension(0, 0);

		/*
		 * update lowest, highest, most left, most right coordinates for elements
		 */

		for (final IGraphicalElement element : elements) {
			int childrenSize = 0;
			final List<ConnectionDecoration> list = ((IGraphicalFeature) element).getDecoration();
			if (list != null) {

				for (final ConnectionDecoration cD : list) {
					if (cD instanceof CollapsedDecoration) {
						childrenSize = ((CollapsedDecoration) cD).getDimension().width + 5;
					}
				}

			}
			// Integer.toString(getAllChildren(((IGraphicalFeature) element).getObject().getStructure())).length();

			labelDimension.width = childrenSize;

			// ((IGraphicalFeature) element).getCollapsedDecoration().getDimension().width;
			final Rectangle position = FeatureUIHelper.getBounds(element);
			if (position.x < min.x) {
				min.x = position.x;

			}
			if (position.y < min.y) {
				min.y = position.y;
			}
			if ((position.x + position.width) > max.x) {
				max.x = position.right() + labelDimension.width();
			}
			if ((position.y + position.height) > max.y) {
				max.y = position.bottom();
			}
		}

		return new Rectangle(min, max);
	}

	public int getAllChildren(IFeatureStructure parent) {
		int count = 0;
		for (final IFeatureStructure iterable_element : parent.getChildren()) {
			count += 1 + getAllChildren(iterable_element);
		}
		return count;
	}

}
