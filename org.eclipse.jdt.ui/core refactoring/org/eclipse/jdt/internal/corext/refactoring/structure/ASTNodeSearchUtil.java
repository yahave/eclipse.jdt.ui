package org.eclipse.jdt.internal.corext.refactoring.structure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.ISearchPattern;
import org.eclipse.jdt.core.search.SearchEngine;

import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.Selection;
import org.eclipse.jdt.internal.corext.dom.SelectionAnalyzer;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringSearchEngine;
import org.eclipse.jdt.internal.corext.refactoring.SearchResult;
import org.eclipse.jdt.internal.corext.refactoring.SearchResultGroup;
import org.eclipse.jdt.internal.corext.refactoring.rename.RefactoringScopeFactory;

public class ASTNodeSearchUtil {

	private ASTNodeSearchUtil() {
	}

	public static ASTNode[] findReferenceNodes(IJavaElement[] elements, ASTNodeMappingManager astManager, IProgressMonitor pm, IJavaSearchScope scope) throws JavaModelException{
		ISearchPattern pattern= RefactoringSearchEngine.createSearchPattern(elements, IJavaSearchConstants.REFERENCES);
		return searchNodes(scope, pattern, astManager, pm);
	}
	
	public static ASTNode[] findOccurrenceNodes(IJavaElement[] elements, ASTNodeMappingManager astManager, IProgressMonitor pm, IJavaSearchScope scope) throws JavaModelException{
		ISearchPattern pattern= RefactoringSearchEngine.createSearchPattern(elements, IJavaSearchConstants.ALL_OCCURRENCES);
		return searchNodes(scope, pattern, astManager, pm);
	}
	
	public static ASTNode[] findReferenceNodes(IJavaElement element, ASTNodeMappingManager astManager, IProgressMonitor pm) throws JavaModelException{
		ISearchPattern pattern= SearchEngine.createSearchPattern(element, IJavaSearchConstants.REFERENCES);
		IJavaSearchScope scope= RefactoringScopeFactory.create(element);
		return searchNodes(scope, pattern, astManager, pm);
	}
	
	public static ASTNode[] searchNodes(IJavaSearchScope scope, ISearchPattern pattern, ASTNodeMappingManager astManager, IProgressMonitor pm) throws JavaModelException{
		SearchResultGroup[] searchResultGroups= RefactoringSearchEngine.search(pm, scope, pattern);
		List result= new ArrayList();
		for (int i= 0; i < searchResultGroups.length; i++) {
			ICompilationUnit referencedCu= searchResultGroups[i].getCompilationUnit();
			if (referencedCu == null)
				continue;
			result.addAll(Arrays.asList(getAstNodes(searchResultGroups[i].getSearchResults(), astManager.getAST(referencedCu))));
		}
		return (ASTNode[]) result.toArray(new ASTNode[result.size()]);		
	}	
	
	public static ASTNode[] getAstNodes(SearchResult[] searchResults, CompilationUnit cuNode) {
		List result= new ArrayList(searchResults.length);
		for (int i= 0; i < searchResults.length; i++) {
			ASTNode node= getAstNode(searchResults[i], cuNode);
			if (node != null)
				result.add(node);
		}
		return (ASTNode[]) result.toArray(new ASTNode[result.size()]);
	}

	private static ASTNode getAstNode(SearchResult searchResult, CompilationUnit cuNode) {
		ASTNode selectedNode= getAstNode(cuNode, searchResult.getStart(), searchResult.getEnd() - searchResult.getStart());
		if (selectedNode == null)
			return null;
		if (selectedNode.getParent() == null)
			return null;
		return selectedNode;
	}

	private static ASTNode getAstNode(CompilationUnit cuNode, int start, int length){
		SelectionAnalyzer analyzer= new SelectionAnalyzer(Selection.createFromStartLength(start, length), true);
		cuNode.accept(analyzer);
		//XXX workaround for bug 23527
		ASTNode node= analyzer.getFirstSelectedNode();
		if (node != null && node.getParent() instanceof SuperConstructorInvocation){
			if (node.getParent().getLength() == length + 1)
				return node.getParent();
		}
		if (node != null && node.getParent() instanceof ConstructorInvocation){
			if (node.getParent().getLength() == length + 1)
				return node.getParent();
		}
		return node;
	}

	public static MethodDeclaration getMethodDeclarationNode(IMethod iMethod, ASTNodeMappingManager astManager) throws JavaModelException {
		Selection selection= Selection.createFromStartLength(iMethod.getNameRange().getOffset(), iMethod.getNameRange().getLength());
		SelectionAnalyzer selectionAnalyzer= new SelectionAnalyzer(selection, true);
		astManager.getAST(iMethod.getCompilationUnit()).accept(selectionAnalyzer);
		ASTNode node= selectionAnalyzer.getFirstSelectedNode();
		if (node == null)
			node= selectionAnalyzer.getLastCoveringNode();
		if (node == null)	
			return null;
		return (MethodDeclaration)ASTNodes.getParent(node, MethodDeclaration.class);
	}
	
	
}
