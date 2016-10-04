/* FeatureIDE - A Framework for Feature-Oriented Software Development
 * Copyright (C) 2005-2016  FeatureIDE team, University of Magdeburg, Germany
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
package de.ovgu.featureide.ui.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.IAnnotationModelExtension;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditor;

import de.ovgu.featureide.fm.ui.editors.featuremodel.actions.colors.SetFeatureColorAction;
import de.ovgu.featureide.ui.editors.annotation.ColorAnnotationModel;

/**
 * TODO description
 * 
 * @author gruppe40
 */
public class ColorSchemeHandler extends AbstractHandler {

	/* 
	 * @see org.eclipse.core.commands.IHandler#execute(org.eclipse.core.commands.ExecutionEvent)
	 */

	private IEditorPart editorPart;
	private IDocumentProvider provider;
	private ITextEditor editor;

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {

		editorPart = HandlerUtil.getActiveEditor(event);
		editor = (ITextEditor) editorPart.getAdapter(ITextEditor.class);
		provider = editor.getDocumentProvider();

		int line = getCursorPos();

		ColorAnnotationModel colormodel = null;
		IEditorInput input = editor.getEditorInput();
		if (provider != null && input instanceof FileEditorInput) {
			IAnnotationModel model = provider.getAnnotationModel(input);

			if (model instanceof IAnnotationModelExtension) {
				IAnnotationModelExtension modelex = (IAnnotationModelExtension) model;
				colormodel = (ColorAnnotationModel) modelex.getAnnotationModel(ColorAnnotationModel.KEY);
			}
		}

		if (colormodel != null) {
			IStructuredSelection strucki = new StructuredSelection(colormodel.getFeature(line));
			SetFeatureColorAction sfca = new SetFeatureColorAction(strucki, colormodel.getFeatureModel());
			sfca.run();
			return true;
		}
		return null;
	}

	private int getCursorPos() {
		IDocument document = provider.getDocument(editorPart.getEditorInput());
		ITextSelection textSelection = (ITextSelection) editorPart.getSite().getSelectionProvider().getSelection();
		int offset = textSelection.getOffset();
		int lineNumber = 0;
		try {
			lineNumber = document.getLineOfOffset(offset);
		} catch (BadLocationException e) {
			e.printStackTrace();
		}
		return lineNumber;
	}

}
