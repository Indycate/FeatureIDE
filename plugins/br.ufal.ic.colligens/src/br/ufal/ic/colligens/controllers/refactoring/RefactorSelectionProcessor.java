package br.ufal.ic.colligens.controllers.refactoring;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.text.TextSelection;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;

import br.ufal.ic.colligens.activator.Colligens;
import br.ufal.ic.colligens.models.PlatformException;
import br.ufal.ic.colligens.models.PlatformHeader;
import core.RefactoringFrontend;
import core.RefactoringType;
import de.fosd.typechef.lexer.LexerException;
import de.fosd.typechef.lexer.options.OptionException;

public class RefactorSelectionProcessor {
	private String sourceOutRefactor;
	private TextSelection textSelection = null;
	private IFile file = null;
	// List of change perform on the code
	protected List<Change> changes = new LinkedList<Change>();

	public void selectToFile(IFile file, TextSelection textSelection,
			RefactoringType refactoringType) throws IOException,
			LexerException, OptionException, RefactorException {

		this.textSelection = textSelection;
		this.file = file;

		PlatformHeader platformHeader = new PlatformHeader();

		try {
			platformHeader.stubs(file.getProject().getName());
		} catch (PlatformException e) {
			e.printStackTrace();
			throw new RefactorException();
		}

		RefactoringFrontend refactoring = new RefactoringFrontend();

		this.sourceOutRefactor = refactoring.refactorCode(
				textSelection.getText(), Colligens.getDefault().getConfigDir()
						.getAbsolutePath()
						+ System.getProperty("file.separator")
						+ "projects"
						+ System.getProperty("file.separator")
						+ file.getProject().getName() + "_stubs.h",
				refactoringType);

		this.removeStubs();

		if (sourceOutRefactor == null) {
			throw new RefactorException();
		}

	}

	public List<Change> process(IProgressMonitor monitor) throws IOException {

		MultiTextEdit edit = new MultiTextEdit();

		edit.addChild(new ReplaceEdit(textSelection.getOffset(), textSelection
				.getLength(), sourceOutRefactor));

		TextFileChange change = new TextFileChange(file.getName(), file);

		change.setTextType("c");
		change.setEdit(edit);
		changes.add(change);

		return changes;
	}

	public void removeStubs() throws IOException {

		BufferedReader br = new BufferedReader(new FileReader(Colligens
				.getDefault().getConfigDir().getAbsolutePath()
				+ System.getProperty("file.separator")
				+ "projects"
				+ System.getProperty("file.separator")
				+ file.getProject().getName() + "_stubs.h"));
		try {
			String line = br.readLine();
			while (line != null) {
				sourceOutRefactor = sourceOutRefactor.replace(line + "\n", "");
				line = br.readLine();
			}
		} finally {
			br.close();
		}
	}
}
