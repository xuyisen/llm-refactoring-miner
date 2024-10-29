package gr.uom.java.xmi.diff;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.refactoringminer.api.Refactoring;
import org.refactoringminer.api.RefactoringType;

import gr.uom.java.xmi.VariableDeclarationContainer;
import gr.uom.java.xmi.LocationInfo.CodeElementType;
import gr.uom.java.xmi.decomposition.AbstractCodeFragment;
import gr.uom.java.xmi.decomposition.LeafMapping;

public class ReplacePipelineWithLoopRefactoring implements Refactoring, LeafMappingProvider {
	private Set<AbstractCodeFragment> codeFragmentsBefore;
	private Set<AbstractCodeFragment> codeFragmentsAfter;
	private VariableDeclarationContainer operationBefore;
	private VariableDeclarationContainer operationAfter;
	private List<LeafMapping> subExpressionMappings;

	public ReplacePipelineWithLoopRefactoring(Set<AbstractCodeFragment> codeFragmentsBefore,
			Set<AbstractCodeFragment> codeFragmentsAfter, VariableDeclarationContainer operationBefore, VariableDeclarationContainer operationAfter) {
		this.codeFragmentsBefore = codeFragmentsBefore;
		this.codeFragmentsAfter = codeFragmentsAfter;
		this.operationBefore = operationBefore;
		this.operationAfter = operationAfter;
		this.subExpressionMappings = new ArrayList<LeafMapping>();
	}

	public void addSubExpressionMapping(LeafMapping newLeafMapping) {
		boolean alreadyPresent = false; 
		for(LeafMapping oldLeafMapping : subExpressionMappings) { 
			if(oldLeafMapping.getFragment1().getLocationInfo().equals(newLeafMapping.getFragment1().getLocationInfo()) && 
					oldLeafMapping.getFragment2().getLocationInfo().equals(newLeafMapping.getFragment2().getLocationInfo())) { 
				alreadyPresent = true; 
				break; 
			} 
		} 
		if(!alreadyPresent) { 
			subExpressionMappings.add(newLeafMapping); 
		}
	}

	public Set<AbstractCodeFragment> getCodeFragmentsBefore() {
		return codeFragmentsBefore;
	}

	public Set<AbstractCodeFragment> getCodeFragmentsAfter() {
		return codeFragmentsAfter;
	}

	public VariableDeclarationContainer getOperationBefore() {
		return operationBefore;
	}

	public VariableDeclarationContainer getOperationAfter() {
		return operationAfter;
	}

	public List<LeafMapping> getSubExpressionMappings() {
		return subExpressionMappings;
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(getName()).append("\t");
		String pipeline = codeFragmentsBefore.iterator().next().getString();
		sb.append(pipeline.contains("\n") ? pipeline.substring(0, pipeline.indexOf("\n")) : pipeline);
		sb.append(" with ");
		for(AbstractCodeFragment fragment : codeFragmentsAfter) {
			if(fragment.getLocationInfo().getCodeElementType().equals(CodeElementType.FOR_STATEMENT) ||
					fragment.getLocationInfo().getCodeElementType().equals(CodeElementType.ENHANCED_FOR_STATEMENT) ||
					fragment.getLocationInfo().getCodeElementType().equals(CodeElementType.WHILE_STATEMENT) ||
					fragment.getLocationInfo().getCodeElementType().equals(CodeElementType.DO_STATEMENT)) {
				sb.append(fragment.getString());
				break;
			}
		}
		String elementType = operationAfter.getElementType();
		sb.append(" in " + elementType + " ");
		sb.append(operationAfter);
		sb.append(" from class ");
		sb.append(operationAfter.getClassName());
		return sb.toString();
	}

	@Override
	public List<CodeRange> leftSide() {
		List<CodeRange> ranges = new ArrayList<CodeRange>();
		for(AbstractCodeFragment fragment : codeFragmentsBefore) {
			ranges.add(fragment.codeRange()
					.setDescription("original code")
					.setCodeElement(fragment.getString()));
		}
		String elementType = operationBefore.getElementType();
		ranges.add(operationBefore.codeRange()
				.setDescription("original " + elementType + " declaration")
				.setCodeElement(operationBefore.toString()));
		return ranges;
	}

	@Override
	public List<CodeRange> rightSide() {
		List<CodeRange> ranges = new ArrayList<CodeRange>();
		for(AbstractCodeFragment fragment : codeFragmentsAfter) {
			ranges.add(fragment.codeRange()
					.setDescription("loop code")
					.setCodeElement(fragment.getString()));
		}
		String elementType = operationAfter.getElementType();
		ranges.add(operationAfter.codeRange()
				.setDescription(elementType + " declaration with introduced loop")
				.setCodeElement(operationAfter.toString()));
		return ranges;
	}

	@Override
	public RefactoringType getRefactoringType() {
		return RefactoringType.REPLACE_PIPELINE_WITH_LOOP;
	}

	@Override
	public String getName() {
		return getRefactoringType().getDisplayName();
	}

	@Override
	public Set<ImmutablePair<String, String>> getInvolvedClassesBeforeRefactoring() {
		Set<ImmutablePair<String, String>> pairs = new LinkedHashSet<ImmutablePair<String, String>>();
		pairs.add(new ImmutablePair<String, String>(getOperationBefore().getLocationInfo().getFilePath(), getOperationBefore().getClassName()));
		return pairs;
	}

	@Override
	public Set<ImmutablePair<String, String>> getInvolvedClassesAfterRefactoring() {
		Set<ImmutablePair<String, String>> pairs = new LinkedHashSet<ImmutablePair<String, String>>();
		pairs.add(new ImmutablePair<String, String>(getOperationAfter().getLocationInfo().getFilePath(), getOperationAfter().getClassName()));
		return pairs;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((codeFragmentsAfter == null) ? 0 : codeFragmentsAfter.hashCode());
		result = prime * result + ((codeFragmentsBefore == null) ? 0 : codeFragmentsBefore.hashCode());
		result = prime * result + ((operationAfter == null) ? 0 : operationAfter.hashCode());
		result = prime * result + ((operationBefore == null) ? 0 : operationBefore.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ReplacePipelineWithLoopRefactoring other = (ReplacePipelineWithLoopRefactoring) obj;
		if (codeFragmentsAfter == null) {
			if (other.codeFragmentsAfter != null)
				return false;
		} else if (!codeFragmentsAfter.equals(other.codeFragmentsAfter))
			return false;
		if (codeFragmentsBefore == null) {
			if (other.codeFragmentsBefore != null)
				return false;
		} else if (!codeFragmentsBefore.equals(other.codeFragmentsBefore))
			return false;
		if (operationAfter == null) {
			if (other.operationAfter != null)
				return false;
		} else if (!operationAfter.equals(other.operationAfter))
			return false;
		if (operationBefore == null) {
			if (other.operationBefore != null)
				return false;
		} else if (!operationBefore.equals(other.operationBefore))
			return false;
		return true;
	}
}
