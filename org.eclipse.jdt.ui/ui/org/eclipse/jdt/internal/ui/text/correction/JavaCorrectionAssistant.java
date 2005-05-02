/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.internal.ui.text.correction;

import java.util.ArrayList;
import java.util.Iterator;

import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceConverter;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DefaultInformationControl;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IInformationControl;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.contentassist.ContentAssistant;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.util.Assert;

import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.texteditor.ITextEditor;

import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jdt.ui.text.IColorManager;
import org.eclipse.jdt.ui.text.JavaTextTools;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.text.HTMLTextPresenter;


public class JavaCorrectionAssistant extends ContentAssistant {

	private ITextViewer fViewer;
	private ITextEditor fEditor;
	private Position fPosition;
	private Annotation[] fCurrentAnnotations;

	private QuickAssistLightBulbUpdater fLightBulbUpdater;


	/**
	 * Constructor for JavaCorrectionAssistant.
	 */
	public JavaCorrectionAssistant(ITextEditor editor) {
		super();
		Assert.isNotNull(editor);
		fEditor= editor;

		JavaCorrectionProcessor processor= new JavaCorrectionProcessor(this);

		setContentAssistProcessor(processor, IDocument.DEFAULT_CONTENT_TYPE);

		enableAutoActivation(false);
		enableAutoInsert(false);

		setContextInformationPopupOrientation(CONTEXT_INFO_ABOVE);
		setInformationControlCreator(getInformationControlCreator());

		JavaTextTools textTools= JavaPlugin.getDefault().getJavaTextTools();
		IColorManager manager= textTools.getColorManager();

		IPreferenceStore store=  JavaPlugin.getDefault().getPreferenceStore();

		Color c= getColor(store, PreferenceConstants.CODEASSIST_PROPOSALS_FOREGROUND, manager);
		setProposalSelectorForeground(c);

		c= getColor(store, PreferenceConstants.CODEASSIST_PROPOSALS_BACKGROUND, manager);
		setProposalSelectorBackground(c);
	}

	public IEditorPart getEditor() {
		return fEditor;
	}


	private IInformationControlCreator getInformationControlCreator() {
		return new IInformationControlCreator() {
			public IInformationControl createInformationControl(Shell parent) {
				return new DefaultInformationControl(parent, new HTMLTextPresenter());
			}
		};
	}

	private static Color getColor(IPreferenceStore store, String key, IColorManager manager) {
		RGB rgb= PreferenceConverter.getColor(store, key);
		return manager.getColor(rgb);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.text.contentassist.IContentAssistant#install(org.eclipse.jface.text.ITextViewer)
	 */
	public void install(ITextViewer textViewer) {
		super.install(textViewer);
		fViewer= textViewer;

		fLightBulbUpdater= new QuickAssistLightBulbUpdater(fEditor, textViewer);
		fLightBulbUpdater.install();
	}



	/* (non-Javadoc)
	 * @see org.eclipse.jface.text.contentassist.ContentAssistant#uninstall()
	 */
	public void uninstall() {
		if (fLightBulbUpdater != null) {
			fLightBulbUpdater.uninstall();
			fLightBulbUpdater= null;
		}
		super.uninstall();
	}

	/**
	 * Show completions at caret position. If current
	 * position does not contain quick fixes look for
	 * next quick fix on same line by moving from left
	 * to right and restarting at end of line if the
	 * beginning of the line is reached.
	 *
	 * @see org.eclipse.jface.text.contentassist.IContentAssistant#showPossibleCompletions()
	 */
	public String showPossibleCompletions() {
		fPosition= null;
		fCurrentAnnotations= null;
		
		if (fViewer == null || fViewer.getDocument() == null)
			// Let superclass deal with this
			return super.showPossibleCompletions();


		ArrayList resultingAnnotations= new ArrayList(20);
		try {
			Point selectedRange= fViewer.getSelectedRange();
			int currOffset= selectedRange.x;
			int currLength= selectedRange.y;
			boolean goToClosest= (currLength == 0);
			
			int newOffset= collectQuickFixableAnnotations(fEditor, currOffset, goToClosest, resultingAnnotations);
			if (newOffset != currOffset) {
				storePosition(currOffset, currLength);
				fViewer.setSelectedRange(newOffset, 0);
				fViewer.revealRange(newOffset, 0);
			}
		} catch (BadLocationException e) {
			JavaPlugin.log(e);
		}
		fCurrentAnnotations= (Annotation[]) resultingAnnotations.toArray(new Annotation[resultingAnnotations.size()]);

		return super.showPossibleCompletions();
	}
	
	
	public static int collectQuickFixableAnnotations(ITextEditor editor, int invocationLocation, boolean goToClosest, ArrayList resultingAnnotations) throws BadLocationException {
		IRegion lineInfo= editor.getDocumentProvider().getDocument(editor.getEditorInput()).getLineInformationOfOffset(invocationLocation);
		int rangeStart= lineInfo.getOffset();
		int rangeEnd= rangeStart + lineInfo.getLength();

		IAnnotationModel model= JavaUI.getDocumentProvider().getAnnotationModel(editor.getEditorInput());
		
		int bestOffset= Integer.MAX_VALUE;
		Iterator iter= model.getAnnotationIterator();
		while (iter.hasNext()) {
			Annotation annot= (Annotation) iter.next();
			if (JavaCorrectionProcessor.isQuickFixableType(annot)) {
				Position pos= model.getPosition(annot);
				if (pos != null && isInside(pos.offset, rangeStart, rangeEnd)) { // inside our range?
					bestOffset= processAnnotation(annot, pos, invocationLocation, bestOffset, goToClosest, resultingAnnotations);
				}
			}
		}
		if (bestOffset == Integer.MAX_VALUE) {
			return invocationLocation;
		}
		
		return bestOffset;
	}

	private static int processAnnotation(Annotation annot, Position pos, int invocationLocation, int bestOffset, boolean goToClosest, ArrayList resultingAnnotations) {
		int posBegin= pos.offset;
		int posEnd= posBegin + pos.length;
		if (isInside(invocationLocation, posBegin, posEnd)) { // covers invocation location?
			if (bestOffset != invocationLocation) {
				resultingAnnotations.clear();
				bestOffset= invocationLocation;
			}
			resultingAnnotations.add(annot); // don't do the 'hasCorrections' test
		} else if (goToClosest && bestOffset != invocationLocation) {
			int newClosestPosition= computeBestOffset(posBegin, invocationLocation, bestOffset);
			if (newClosestPosition != -1) { 
				if (newClosestPosition != bestOffset) { // new best
					if (JavaCorrectionProcessor.hasCorrections(annot)) { // only jump to it if there are proposals
						resultingAnnotations.clear();
						bestOffset= newClosestPosition;
						resultingAnnotations.add(annot);
					}
				} else { // as good as previous, don't do the 'hasCorrections' test
					resultingAnnotations.add(annot);
				}
			}
		}
		return bestOffset;
	}


	private static boolean isInside(int offset, int start, int end) {
		return offset >= start && offset < end;
	}

	/**
	 * Computes and returns the invocation offset given a new
	 * position, the initial offset and the best invocation offset
	 * found so far.
	 * <p>
	 * The closest offset to the left of the initial offset is the
	 * best. If there is no offset on the left, the closest on the
	 * right is the best.</p>
	 * @return -1 is returned if the given offset is not closer or the new best offset
	 */
	private static int computeBestOffset(int newOffset, int invocationLocation, int bestOffset) {
		if (newOffset <= invocationLocation) {
			if (bestOffset > invocationLocation) {
				return newOffset; // closest was on the right, prefer on the left
			} else if (bestOffset <= newOffset) {
				return newOffset; // we are closer or equal
			}
			return -1; // further away
		}

		if (newOffset <= bestOffset)
			return newOffset; // we are closer or equal

		return -1; // further away
	}

	/*
	 * @see org.eclipse.jface.text.contentassist.ContentAssistant#possibleCompletionsClosed()
	 */
	protected void possibleCompletionsClosed() {
		super.possibleCompletionsClosed();
		restorePosition();
	}

	private void storePosition(int currOffset, int currLength) {
		fPosition= new Position(currOffset, currLength);
	}

	private void restorePosition() {
		if (fPosition != null && !fPosition.isDeleted() && fViewer.getDocument() != null) {
			fViewer.setSelectedRange(fPosition.offset, fPosition.length);
			fViewer.revealRange(fPosition.offset, fPosition.length);
		}
		fPosition= null;
	}

	/**
	 * Returns true if the last invoked completion was called with an updated offset.
	 */
	public boolean isUpdatedOffset() {
		return fPosition != null;
	}

	/**
	 * Returns the annotations at the current offset
	 */
	public Annotation[] getAnnotationsAtOffset() {
		return fCurrentAnnotations;
	}
	
}
