package org.eclipse.jdt.internal.debug.ui.display;/* * (c) Copyright IBM Corp. 2000, 2001. * All Rights Reserved. */import java.text.MessageFormat;import java.util.ArrayList;import java.util.HashMap;import java.util.Iterator;import java.util.List;import java.util.Map;import org.eclipse.swt.SWT;import org.eclipse.swt.custom.StyleRange;import org.eclipse.swt.custom.StyledText;import org.eclipse.swt.graphics.Color;import org.eclipse.swt.graphics.Point;import org.eclipse.swt.graphics.RGB;import org.eclipse.swt.widgets.Composite;import org.eclipse.swt.widgets.Menu;import org.eclipse.jface.action.IAction;import org.eclipse.jface.action.IMenuListener;import org.eclipse.jface.action.IMenuManager;import org.eclipse.jface.action.IToolBarManager;import org.eclipse.jface.action.MenuManager;import org.eclipse.jface.action.Separator;import org.eclipse.jface.text.Document;import org.eclipse.jface.text.IDocument;import org.eclipse.jface.text.ITextOperationTarget;import org.eclipse.jface.text.source.SourceViewer;import org.eclipse.jface.text.source.SourceViewerConfiguration;import org.eclipse.jface.viewers.ISelectionChangedListener;import org.eclipse.jface.viewers.ISelectionProvider;import org.eclipse.jface.viewers.SelectionChangedEvent;import org.eclipse.ui.IActionBars;import org.eclipse.ui.IWorkbenchActionConstants;import org.eclipse.ui.help.ViewContextComputer;import org.eclipse.ui.help.WorkbenchHelp;import org.eclipse.ui.part.ViewPart;import org.eclipse.ui.texteditor.ITextEditorActionConstants;import org.eclipse.ui.texteditor.IUpdate;import org.eclipse.jdt.ui.text.IColorManager;import org.eclipse.jdt.ui.text.JavaTextTools;import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;import org.eclipse.jdt.internal.ui.JavaPlugin;


public class DisplayView extends ViewPart {		/**	 * Display view identifier (value <code>"org.eclipse.debug.ui.DisplayView"</code>).	 */	public static final String ID_DISPLAY_VIEW= "org.eclipse.jdt.debug.ui.DisplayView"; //$NON-NLS-1$				class DataDisplay implements IDataDisplay {				private Color fExpressionColor;		private Color fValueColor;				/**
		 * @see IDataDisplay#clear()
		 */
		public void clear() {			IDocument document= fSourceViewer.getDocument();			if (document != null)				document.set(""); //$NON-NLS-1$
		}						private Color getExpressionColor() {			if (fExpressionColor == null) {				JavaTextTools tools= JavaPlugin.getDefault().getJavaTextTools();				IColorManager manager= tools.getColorManager();				fExpressionColor= manager.getColor(new RGB(42, 0, 255));			}			return fExpressionColor;		}				private Color getValueColor() {			if (fValueColor == null) {				JavaTextTools tools= JavaPlugin.getDefault().getJavaTextTools();				IColorManager manager= tools.getColorManager();				fValueColor= manager.getColor(new RGB(0, 0, 0));			}			return fValueColor;		}				/**		 * @see IDataDisplay#displayExpression(String)		 */		public void displayExpression(String expression) {			StyledText widget= fSourceViewer.getTextWidget();			if (widget != null) {								expression= MessageFormat.format(DisplayMessages.getString("DisplayView.eval_expr_pattern"), new Object[] { expression }); //$NON-NLS-1$				Point selection= widget.getSelection();				int offset= selection.x;				int length= expression.length();								widget.replaceTextRange(offset, selection.y - selection.x, expression);				widget.setStyleRange(new StyleRange(offset, length, getExpressionColor(), null, SWT.NORMAL));				widget.setSelection(offset + length);			}		}						/**		 * @see IDataDisplay#displayExpressionValue(String)		 */		public void displayExpressionValue(String value) {			StyledText widget= fSourceViewer.getTextWidget();			if (widget != null) {								Point selection= widget.getSelection();				int offset= selection.x;				int length= value.length();								widget.replaceTextRange(offset, selection.y - selection.x, value);				widget.setStyleRange(new StyleRange(offset, length, getValueColor(), null, SWT.NORMAL));				widget.setSelection(offset + length);			}		}		
	};		private IDataDisplay fDataDisplay= new DataDisplay();	
	protected SourceViewer fSourceViewer;
	protected IAction fClearDisplayAction;	protected IAction fDisplayAction;	protected IAction fInspectAction;

	protected Map fGlobalActions= new HashMap(10);
	protected List fSelectionActions= new ArrayList(7);

	/**
	 * @see ViewPart#createChild(IWorkbenchPartContainer)
	 */
	public void createPartControl(Composite parent) {				/*		 * 1GETEAF: ITPDUI:WIN2000 - Display view looks recessed		 * Removed SWT.BORDER from styles		 */		int styles= SWT.V_SCROLL | SWT.H_SCROLL | SWT.MULTI | SWT.FULL_SELECTION;
		fSourceViewer= new SourceViewer(parent, null, styles);		fSourceViewer.configure(new SourceViewerConfiguration());		fSourceViewer.setDocument(new Document());		
		initializeActions();
		initializeToolBar();

		// create context menu
		MenuManager menuMgr = new MenuManager("#PopUp", ID_DISPLAY_VIEW); //$NON-NLS-1$
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(new IMenuListener() {
			public void menuAboutToShow(IMenuManager mgr) {
				fillContextMenu(mgr);
			}
		});
		
		Menu menu = menuMgr.createContextMenu(fSourceViewer.getTextWidget());
		fSourceViewer.getTextWidget().setMenu(menu);
		getSite().registerContextMenu(menuMgr.getId(), menuMgr, fSourceViewer);
		ISelectionProvider p= fSourceViewer.getSelectionProvider();
		getSite().setSelectionProvider(p);		p.addSelectionChangedListener(new ISelectionChangedListener() {			public void selectionChanged(SelectionChangedEvent event) {				updateSelectionDependentActions();			}		});				// added for 1GETAYN: ITPJUI:WIN - F1 help does nothing		WorkbenchHelp.setHelp(fSourceViewer.getTextWidget(), new ViewContextComputer(this, IJavaHelpContextIds.DISPLAY_VIEW));			}

	/**
	 * @see IWorkbenchPart
	 */
	public void setFocus() {
		fSourceViewer.getControl().setFocus();
	}
	
	/**
	 * Initialize the actions of this view
	 */
	private void initializeActions() {
						fClearDisplayAction= new ClearDisplayAction(this);		fDisplayAction= new DisplayAction(this);		fInspectAction= new InspectAction(this);

		IActionBars actionBars = getViewSite().getActionBars();		
		IAction action;
		
		action= new DisplayViewAction(this, fSourceViewer.CUT);		action.setText(DisplayMessages.getString("DisplayView.Cut.label")); //$NON-NLS-1$		action.setToolTipText(DisplayMessages.getString("DisplayView.Cut.tooltip")); //$NON-NLS-1$		action.setDescription(DisplayMessages.getString("DisplayView.Cut.description")); //$NON-NLS-1$		setGlobalAction(actionBars, ITextEditorActionConstants.CUT, action);
		
		action= new DisplayViewAction(this, fSourceViewer.COPY);
		action.setText(DisplayMessages.getString("DisplayView.Copy.label")); //$NON-NLS-1$		action.setToolTipText(DisplayMessages.getString("DisplayView.Copy.tooltip")); //$NON-NLS-1$		action.setDescription(DisplayMessages.getString("DisplayView.Copy.description")); //$NON-NLS-1$		setGlobalAction(actionBars, ITextEditorActionConstants.COPY, action);
		
		action= new DisplayViewAction(this, fSourceViewer.PASTE);
		action.setText(DisplayMessages.getString("DisplayView.Paste.label")); //$NON-NLS-1$		action.setToolTipText(DisplayMessages.getString("DisplayView.Paste.tooltip")); //$NON-NLS-1$		action.setDescription(DisplayMessages.getString("DisplayView.Paste.Description")); //$NON-NLS-1$		setGlobalAction(actionBars, ITextEditorActionConstants.PASTE, action);
		
		action= new DisplayViewAction(this, fSourceViewer.SELECT_ALL);
		action.setText(DisplayMessages.getString("DisplayView.SelectAll.label")); //$NON-NLS-1$		action.setToolTipText(DisplayMessages.getString("DisplayView.SelectAll.tooltip")); //$NON-NLS-1$		action.setDescription(DisplayMessages.getString("DisplayView.SelectAll.description")); //$NON-NLS-1$		setGlobalAction(actionBars, ITextEditorActionConstants.SELECT_ALL, action);
		
		fSelectionActions.add(ITextEditorActionConstants.CUT);
		fSelectionActions.add(ITextEditorActionConstants.COPY);
		fSelectionActions.add(ITextEditorActionConstants.PASTE);
	}

	protected void setGlobalAction(IActionBars actionBars, String actionID, IAction action) {
		fGlobalActions.put(actionID, action);
		actionBars.setGlobalActionHandler(actionID, action);
	}

	/**
	 * Configures the toolBar.
	 */
	private void initializeToolBar() {
		IToolBarManager tbm = getViewSite().getActionBars().getToolBarManager();
		tbm.add(fClearDisplayAction);		tbm.add(fDisplayAction);		tbm.add(fInspectAction);
		getViewSite().getActionBars().updateActionBars();
	}

	/**
	 * Adds the text manipulation actions to the <code>ConsoleViewer</code>
	 */
	protected void fillContextMenu(IMenuManager menu) {		
		if (fSourceViewer.getDocument() == null)
			return;
				menu.add(fDisplayAction);		menu.add(fInspectAction);		menu.add(new Separator());		
		menu.add((IAction) fGlobalActions.get(ITextEditorActionConstants.CUT));
		menu.add((IAction) fGlobalActions.get(ITextEditorActionConstants.COPY));
		menu.add((IAction) fGlobalActions.get(ITextEditorActionConstants.PASTE));
		menu.add((IAction) fGlobalActions.get(ITextEditorActionConstants.SELECT_ALL));
		menu.add(new Separator());
		menu.add(fClearDisplayAction);
		menu.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
	}

	/**
	 * @see WorkbenchPart#getAdapter(Class)
	 */
	public Object getAdapter(Class required) {
					if (ITextOperationTarget.class.equals(required))			return fSourceViewer.getTextOperationTarget();					if (IDataDisplay.class.equals(required))			return fDataDisplay;
		
		return super.getAdapter(required);
	}
	
	protected void updateSelectionDependentActions() {
		Iterator iterator = fSelectionActions.iterator();
		while (iterator.hasNext())
			updateAction((String) iterator.next());
	}

	protected void updateAction(String actionId) {
		IAction action = (IAction) fGlobalActions.get(actionId);
		if (action instanceof IUpdate)
			 ((IUpdate) action).update();
	}
}
