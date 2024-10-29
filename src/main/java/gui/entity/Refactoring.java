package gui.entity;

import java.util.List;
import java.util.Set;

public class Refactoring {
    private String type;
    private String description;
    private List<Location> leftSideLocations;
    private List<Location> rightSideLocations;
    private List<Location> diffLocations;
    private String sourceCodeBeforeRefactoring;
    private String filePathBefore;
    private Boolean isPureRefactoring;
    private String commitId;
    private String packageNameBefore;
    private String classNameBefore;
    private String methodNameBefore;
    private String invokedMethod;
    private String classSignatureBefore;
    private Set<String> methodNameBeforeSet;
    private Set<String> classNameBeforeSet;
    private Set<String> classSignatureBeforeSet;
    private Set<String> descriptionSet;

    public List<Location> getDiffLocations() {
        return diffLocations;
    }

    public void setDiffLocations(List<Location> diffLocations) {
        this.diffLocations = diffLocations;
    }

    public Set<String> getDescriptionSet() {
        return descriptionSet;
    }

    public void setDescriptionSet(Set<String> descriptionSet) {
        this.descriptionSet = descriptionSet;
    }

    public Set<String> getClassSignatureBeforeSet() {
        return classSignatureBeforeSet;
    }

    public void setClassSignatureBeforeSet(Set<String> classSignatureBeforeSet) {
        this.classSignatureBeforeSet = classSignatureBeforeSet;
    }


    public Set<String> getMethodNameBeforeSet() {
        return methodNameBeforeSet;
    }

    public void setMethodNameBeforeSet(Set<String> methodNameBeforeSet) {
        this.methodNameBeforeSet = methodNameBeforeSet;
    }

    public Set<String> getClassNameBeforeSet() {
        return classNameBeforeSet;
    }

    public void setClassNameBeforeSet(Set<String> classNameBeforeSet) {
        this.classNameBeforeSet = classNameBeforeSet;
    }

    public String getClassSignatureBefore() {
        return classSignatureBefore;
    }

    public void setClassSignatureBefore(String classSignatureBefore) {
        this.classSignatureBefore = classSignatureBefore;
    }

    private Set<String> diffSourceCodeSet;

    private Set<String> invokedMethodSet;

    public Set<String> getInvokedMethodSet() {
        return invokedMethodSet;
    }

    public void setInvokedMethodSet(Set<String> invokedMethodSet) {
        this.invokedMethodSet = invokedMethodSet;
    }

    public Set<String> getDiffSourceCodeSet() {
        return diffSourceCodeSet;
    }

    public void setDiffSourceCodeSet(Set<String> diffSourceCodeSet) {
        this.diffSourceCodeSet = diffSourceCodeSet;
    }

    public Boolean getPureRefactoring() {
        return isPureRefactoring;
    }

    public void setPureRefactoring(Boolean pureRefactoring) {
        isPureRefactoring = pureRefactoring;
    }

    public String getCommitId() {
        return commitId;
    }

    public void setCommitId(String commitId) {
        this.commitId = commitId;
    }

    public String getPackageNameBefore() {
        return packageNameBefore;
    }

    public void setPackageNameBefore(String packageNameBefore) {
        this.packageNameBefore = packageNameBefore;
    }

    public String getClassNameBefore() {
        return classNameBefore;
    }

    public void setClassNameBefore(String classNameBefore) {
        this.classNameBefore = classNameBefore;
    }

    public String getMethodNameBefore() {
        return methodNameBefore;
    }

    public void setMethodNameBefore(String methodNameBefore) {
        this.methodNameBefore = methodNameBefore;
    }

    public String getInvokedMethod() {
        return invokedMethod;
    }

    public void setInvokedMethod(String invokedMethod) {
        this.invokedMethod = invokedMethod;
    }

    public String getFilePathBefore() {
        return filePathBefore;
    }

    public void setFilePathBefore(String filePathBefore) {
        this.filePathBefore = filePathBefore;
    }

    public String getSourceCodeBeforeRefactoring() {
        return sourceCodeBeforeRefactoring;
    }

    public void setSourceCodeBeforeRefactoring(String sourceCodeBeforeRefactoring) {
        this.sourceCodeBeforeRefactoring = sourceCodeBeforeRefactoring;
    }

    public String getSourceCodeAfterRefactoring() {
        return sourceCodeAfterRefactoring;
    }

    public void setSourceCodeAfterRefactoring(String sourceCodeAfterRefactoring) {
        this.sourceCodeAfterRefactoring = sourceCodeAfterRefactoring;
    }

    public String getDiffSourceCode() {
        return diffSourceCode;
    }

    public void setDiffSourceCode(String diffSourceCode) {
        this.diffSourceCode = diffSourceCode;
    }

    public String getUniqueId() {
        return uniqueId;
    }

    public void setUniqueId(String uniqueId) {
        this.uniqueId = uniqueId;
    }

    private String sourceCodeAfterRefactoring;
    private String diffSourceCode;
    private String uniqueId;

    // Getters and Setters
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<Location> getLeftSideLocations() {
        return leftSideLocations;
    }

    public void setLeftSideLocations(List<Location> leftSideLocations) {
        this.leftSideLocations = leftSideLocations;
    }

    public List<Location> getRightSideLocations() {
        return rightSideLocations;
    }

    public void setRightSideLocations(List<Location> rightSideLocations) {
        this.rightSideLocations = rightSideLocations;
    }
}
