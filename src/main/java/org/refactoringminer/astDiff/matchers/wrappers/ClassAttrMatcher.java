package org.refactoringminer.astDiff.matchers.wrappers;

import com.github.gumtreediff.tree.Tree;
import gr.uom.java.xmi.UMLAttribute;
import gr.uom.java.xmi.diff.*;
import org.apache.commons.lang3.tuple.Pair;
import org.refactoringminer.astDiff.models.ExtendedMultiMappingStore;
import org.refactoringminer.astDiff.models.OptimizationData;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/* Created by pourya on 2024-05-22*/
public class ClassAttrMatcher extends OptimizationAwareMatcher {
    private final UMLAbstractClassDiff classDiff;

    public ClassAttrMatcher(UMLAbstractClassDiff classDiff) {
        this.classDiff = classDiff;
    }

    public ClassAttrMatcher(OptimizationData optimizationData, UMLAbstractClassDiff classDiff) {
        super(optimizationData);
        this.classDiff = classDiff;
    }
    @Override
    public void matchAndUpdateOptimizationStore(Tree srcTree, Tree dstTree, ExtendedMultiMappingStore mappingStore) {
        processClassAttributes(srcTree, dstTree, classDiff, mappingStore);
    }

    private void processClassAttributes(Tree srcTree, Tree dstTree, UMLAbstractClassDiff classDiff, ExtendedMultiMappingStore mappingStore) {
        Set<Pair<UMLAttribute, UMLAttribute>> pairs = classDiff.getCommonAtrributes();
        for (org.apache.commons.lang3.tuple.Pair<UMLAttribute, UMLAttribute> pair : pairs) {
            new FieldDeclarationMatcher(optimizationData, pair.getLeft(), pair.getRight(),
                    (pair.getLeft().getJavadoc() != null && pair.getRight().getJavadoc() != null) ?
                        Optional.of(new UMLJavadocDiff(pair.getLeft().getJavadoc(), pair.getRight().getJavadoc()))
                        //TODO: Replace the above line with the pair.getJavaDocDiff() or something along those lines
                        :
                        Optional.empty(),
                        new UMLCommentListDiff(pair.getLeft().getComments(), pair.getRight().getComments())) //Note: UMLJavaDocDiff throws exception if one side is null.
                    // So if one parameter is null, is better to handle it internally and allow the user to pass it

                    .match(srcTree, dstTree, mappingStore);

        }
        List<UMLAttributeDiff> attributeDiffList = classDiff.getAttributeDiffList();
        for (UMLAttributeDiff umlAttributeDiff : attributeDiffList) {
            new FieldDeclarationByAttrDiffMatcher(optimizationData, umlAttributeDiff).match(srcTree, dstTree, mappingStore);
        }
        List<UMLEnumConstantDiff> enumConstantDiffList = classDiff.getEnumConstantDiffList();
        for (UMLEnumConstantDiff enumConstantDiff : enumConstantDiffList) {
            processFieldDeclarationByEnumConstantDiff(srcTree,dstTree,enumConstantDiff,mappingStore);
        }
    }
    private void processFieldDeclarationByEnumConstantDiff(Tree srcTree, Tree dstTree, UMLEnumConstantDiff umlEnumConstantDiff, ExtendedMultiMappingStore mappingStore) {
        new FieldDeclarationMatcher(optimizationData, umlEnumConstantDiff.getRemovedEnumConstant(), umlEnumConstantDiff.getAddedEnumConstant(), umlEnumConstantDiff.getJavadocDiff(), umlEnumConstantDiff.getCommentListDiff()).match(srcTree,dstTree,mappingStore);
        if(umlEnumConstantDiff.getAnonymousClassDiff().isPresent()) {
            UMLAnonymousClassDiff anonymousClassDiff = umlEnumConstantDiff.getAnonymousClassDiff().get();
            new AnonymousClassDiffMatcher(optimizationData, anonymousClassDiff).match(srcTree, dstTree, mappingStore);
        }
    }
}
