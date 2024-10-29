package gr.uom.java.xmi.diff;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.jsoup.Jsoup;

import gr.uom.java.xmi.UMLDocElement;
import gr.uom.java.xmi.UMLJavadoc;
import gr.uom.java.xmi.UMLTagElement;

public class UMLJavadocDiff {
	private UMLJavadoc javadocBefore;
	private UMLJavadoc javadocAfter;
	private List<Pair<UMLTagElement, UMLTagElement>> commonTags;
	private List<Pair<UMLDocElement, UMLDocElement>> commonDocElements;
	private List<Pair<UMLTagElement, UMLTagElement>> commonNestedTags;
	private List<UMLTagElement> deletedTags;
	private List<UMLTagElement> addedTags;
	private List<UMLTagElement> deletedNestedTags;
	private List<UMLTagElement> addedNestedTags;
	private List<UMLDocElement> deletedDocElements;
	private List<UMLDocElement> addedDocElements;
	private boolean manyToManyReformat;
	private UMLOperationDiff signatureDiff;

	public UMLJavadocDiff(UMLJavadoc javadocBefore, UMLJavadoc javadocAfter) {
		this.javadocBefore = javadocBefore;
		this.javadocAfter = javadocAfter;
		this.commonTags = new ArrayList<Pair<UMLTagElement,UMLTagElement>>();
		this.commonNestedTags = new ArrayList<Pair<UMLTagElement,UMLTagElement>>();
		this.commonDocElements = new ArrayList<Pair<UMLDocElement,UMLDocElement>>();
		this.deletedTags = new ArrayList<UMLTagElement>();
		this.addedTags = new ArrayList<UMLTagElement>();
		this.deletedNestedTags = new ArrayList<UMLTagElement>();
		this.addedNestedTags = new ArrayList<UMLTagElement>();
		this.deletedDocElements = new ArrayList<UMLDocElement>();
		this.addedDocElements = new ArrayList<UMLDocElement>();
		process(javadocBefore, javadocAfter);
	}

	public UMLJavadocDiff(UMLJavadoc javadocBefore, UMLJavadoc javadocAfter, UMLOperationDiff signatureDiff) {
		this.javadocBefore = javadocBefore;
		this.javadocAfter = javadocAfter;
		this.commonTags = new ArrayList<Pair<UMLTagElement,UMLTagElement>>();
		this.commonNestedTags = new ArrayList<Pair<UMLTagElement,UMLTagElement>>();
		this.commonDocElements = new ArrayList<Pair<UMLDocElement,UMLDocElement>>();
		this.deletedTags = new ArrayList<UMLTagElement>();
		this.addedTags = new ArrayList<UMLTagElement>();
		this.deletedNestedTags = new ArrayList<UMLTagElement>();
		this.addedNestedTags = new ArrayList<UMLTagElement>();
		this.deletedDocElements = new ArrayList<UMLDocElement>();
		this.addedDocElements = new ArrayList<UMLDocElement>();
		this.signatureDiff = signatureDiff;
		process(javadocBefore, javadocAfter);
	}

	private void process(UMLJavadoc javadocBefore, UMLJavadoc javadocAfter) {
		List<UMLTagElement> tagsBefore = javadocBefore.getTags();
		List<UMLTagElement> tagsAfter = javadocAfter.getTags();
		List<UMLTagElement> deletedTags = new ArrayList<UMLTagElement>(tagsBefore);
		List<UMLTagElement> addedTags = new ArrayList<UMLTagElement>(tagsAfter);
		if(tagsBefore.size() <= tagsAfter.size()) {
			for(UMLTagElement tagBefore : tagsBefore) {
				if(tagsAfter.contains(tagBefore)) {
					int index = tagsAfter.indexOf(tagBefore);
					Pair<UMLTagElement, UMLTagElement> pair = Pair.of(tagBefore, tagsAfter.get(index));
					commonTags.add(pair);
					processIdenticalTags(tagBefore, tagsAfter.get(index));
					matchNestedTags(tagBefore, tagsAfter.get(index));
					deletedTags.remove(tagBefore);
					addedTags.remove(tagBefore);
				}
			}
		}
		else {
			for(UMLTagElement tagAfter : tagsAfter) {
				if(tagsBefore.contains(tagAfter)) {
					int index = tagsBefore.indexOf(tagAfter);
					Pair<UMLTagElement, UMLTagElement> pair = Pair.of(tagsBefore.get(index), tagAfter);
					commonTags.add(pair);
					processIdenticalTags(tagsBefore.get(index), tagAfter);
					matchNestedTags(tagsBefore.get(index), tagAfter);
					deletedTags.remove(tagAfter);
					addedTags.remove(tagAfter);
				}
			}
		}
		List<UMLTagElement> deletedToBeDeleted = new ArrayList<UMLTagElement>();
		List<UMLTagElement> addedToBeDeleted = new ArrayList<UMLTagElement>();
		//process first param tags with the same parameter name
		for(UMLTagElement tagBefore : deletedTags) {
			for(UMLTagElement tagAfter : addedTags) {
				if(tagBefore.isParam() && tagAfter.isParam()) {
					String paramNameBefore = tagBefore.getParamName();
					String paramNameAfter = tagAfter.getParamName();
					if(paramNameBefore != null && paramNameAfter != null && paramNameBefore.equals(paramNameAfter)) {
						boolean match = processModifiedTags(tagBefore, tagAfter);
						if(match) {
							deletedToBeDeleted.add(tagBefore);
							addedToBeDeleted.add(tagAfter);
							Pair<UMLTagElement, UMLTagElement> pair = Pair.of(tagBefore, tagAfter);
							commonTags.add(pair);
							matchNestedTags(tagBefore, tagAfter);
							break;
						}
					}
					else if(signatureDiff != null) {
						boolean matchFound = false;
						for(UMLParameterDiff diff : signatureDiff.getParameterDiffList()) {
							if(diff.getRemovedParameter().getName().equals(paramNameBefore) &&
									diff.getAddedParameter().getName().equals(paramNameAfter)) {
								//match name
								matchFound = true;
								UMLDocElement docElementBefore = tagBefore.getFragments().get(0);
								UMLDocElement docElementAfter = tagAfter.getFragments().get(0);
								Pair<UMLDocElement, UMLDocElement> docElementPair = Pair.of(docElementBefore, docElementAfter);
								commonDocElements.add(docElementPair);
								processModifiedTags(tagBefore, tagAfter);
								deletedToBeDeleted.add(tagBefore);
								addedToBeDeleted.add(tagAfter);
								Pair<UMLTagElement, UMLTagElement> pair = Pair.of(tagBefore, tagAfter);
								commonTags.add(pair);
								matchNestedTags(tagBefore, tagAfter);
								break;
							}
						}
						if(matchFound) {
							break;
						}
					}
				}
				else if(tagBefore.isReturn() && tagAfter.isReturn()) {
					boolean match = processModifiedTags(tagBefore, tagAfter);
					if(match) {
						deletedToBeDeleted.add(tagBefore);
						addedToBeDeleted.add(tagAfter);
						Pair<UMLTagElement, UMLTagElement> pair = Pair.of(tagBefore, tagAfter);
						commonTags.add(pair);
						matchNestedTags(tagBefore, tagAfter);
						break;
					}
				}
				else if(tagBefore.isThrows() && tagAfter.isThrows()) {
					boolean match = processModifiedTags(tagBefore, tagAfter);
					if(match) {
						deletedToBeDeleted.add(tagBefore);
						addedToBeDeleted.add(tagAfter);
						Pair<UMLTagElement, UMLTagElement> pair = Pair.of(tagBefore, tagAfter);
						commonTags.add(pair);
						matchNestedTags(tagBefore, tagAfter);
						break;
					}
				}
			}
		}
		deletedTags.removeAll(deletedToBeDeleted);
		addedTags.removeAll(addedToBeDeleted);
		if(deletedTags.size() <= addedTags.size()) {
			for(UMLTagElement tagBefore : deletedTags) {
				for(UMLTagElement tagAfter : addedTags) {
					boolean match = processModifiedTags(tagBefore, tagAfter);
					if(match) {
						deletedToBeDeleted.add(tagBefore);
						addedToBeDeleted.add(tagAfter);
						Pair<UMLTagElement, UMLTagElement> pair = Pair.of(tagBefore, tagAfter);
						commonTags.add(pair);
						matchNestedTags(tagBefore, tagAfter);
						break;
					}
				}
			}
		}
		else {
			for(UMLTagElement tagAfter : addedTags) {
				for(UMLTagElement tagBefore : deletedTags) {
					boolean match = processModifiedTags(tagBefore, tagAfter);
					if(match) {
						deletedToBeDeleted.add(tagBefore);
						addedToBeDeleted.add(tagAfter);
						Pair<UMLTagElement, UMLTagElement> pair = Pair.of(tagBefore, tagAfter);
						commonTags.add(pair);
						matchNestedTags(tagBefore, tagAfter);
						break;
					}
				}
			}
		}
		deletedTags.removeAll(deletedToBeDeleted);
		addedTags.removeAll(addedToBeDeleted);
		this.deletedTags.addAll(deletedTags);
		this.addedTags.addAll(addedTags);
	}

	private void matchNestedTags(UMLTagElement tagBefore, UMLTagElement tagAfter) {
		List<UMLTagElement> nestedTagsBefore = tagBefore.getNestedTags();
		List<UMLTagElement> nestedTagsAfter = tagAfter.getNestedTags();
		List<UMLTagElement> deletedNestedTags = new ArrayList<UMLTagElement>(nestedTagsBefore);
		List<UMLTagElement> addedNestedTags = new ArrayList<UMLTagElement>(nestedTagsAfter);
		if(nestedTagsBefore.size() <= nestedTagsAfter.size()) {
			for(UMLTagElement nestedTagBefore : nestedTagsBefore) {
				if(nestedTagsAfter.contains(nestedTagBefore)) {
					List<Integer> matchingIndices = findAllMatchingIndices(nestedTagsAfter, nestedTagBefore);
					for(Integer index : matchingIndices) {
						if(!alreadyMatchedNestedTagElement(nestedTagBefore, nestedTagsAfter.get(index))) {
							Pair<UMLTagElement, UMLTagElement> pair = Pair.of(nestedTagBefore, nestedTagsAfter.get(index));
							commonNestedTags.add(pair);
							processIdenticalTags(nestedTagBefore, nestedTagsAfter.get(index));
							deletedNestedTags.remove(nestedTagBefore);
							addedNestedTags.remove(nestedTagBefore);
							break;
						}
					}
				}
			}
		}
		else {
			for(UMLTagElement nestedTagAfter : nestedTagsAfter) {
				if(nestedTagsBefore.contains(nestedTagAfter)) {
					List<Integer> matchingIndices = findAllMatchingIndices(nestedTagsBefore, nestedTagAfter);
					for(Integer index : matchingIndices) {
						if(!alreadyMatchedNestedTagElement(nestedTagsBefore.get(index), nestedTagAfter)) {
							Pair<UMLTagElement, UMLTagElement> pair = Pair.of(nestedTagsBefore.get(index), nestedTagAfter);
							commonNestedTags.add(pair);
							processIdenticalTags(nestedTagsBefore.get(index), nestedTagAfter);
							deletedNestedTags.remove(nestedTagAfter);
							addedNestedTags.remove(nestedTagAfter);
							break;
						}
					}
				}
			}
		}
		if(deletedNestedTags.size() == addedNestedTags.size()) {
			for(int i=0; i<deletedNestedTags.size(); i++) {
				UMLTagElement deletedNestedTag = deletedNestedTags.get(i);
				UMLTagElement addedNestedTag = addedNestedTags.get(i);
				if(deletedNestedTag.getTagName() != null && addedNestedTag.getTagName() != null &&
						deletedNestedTag.getTagName().equals(addedNestedTag.getTagName()) &&
						deletedNestedTag.getFragments().size() == addedNestedTag.getFragments().size() &&
						deletedNestedTag.getFragments().size() > 0) {
					UMLDocElement fragment1 = deletedNestedTag.getFragments().get(0);
					UMLDocElement fragment2 = addedNestedTag.getFragments().get(0);
					if(fragment1.getText().contains("#") && fragment2.getText().contains("#")) {
						String text1 = fragment1.getText();
						if(text1.contains("(")) {
							text1 = text1.substring(0, text1.indexOf("("));
						}
						String text2 = fragment2.getText();
						if(text2.contains("(")) {
							text2 = text2.substring(0, text2.indexOf("("));
						}
						String memberName1 = text1.substring(text1.indexOf("#"), text1.length());
						String memberName2 = text2.substring(text2.indexOf("#"), text2.length());
						if(text1.contains(text2) || text2.contains(text1) || memberName1.equals(memberName2)) {
							Pair<UMLTagElement, UMLTagElement> pair = Pair.of(deletedNestedTag, addedNestedTag);
							commonNestedTags.add(pair);
						}
					}
				}
				else if(deletedNestedTag.getTagName() != null && addedNestedTag.getTagName() != null &&
						deletedNestedTag.getTagName().equals(addedNestedTag.getTagName()) &&
						deletedNestedTag.getFragments().size() > 0 && addedNestedTag.getFragments().size() > 0) {
					UMLDocElement fragment1 = deletedNestedTag.getFragments().get(0);
					UMLDocElement fragment2 = addedNestedTag.getFragments().get(0);
					String trimmed1 = fragment1.getText().trim();
					String trimmed2 = fragment2.getText().trim();
					StringBuilder concatenated1 = new StringBuilder();
					for(UMLDocElement f1 : deletedNestedTag.getFragments()) {
						concatenated1.append(f1.getText().trim());
					}
					StringBuilder concatenated2 = new StringBuilder();
					for(UMLDocElement f2 : addedNestedTag.getFragments()) {
						concatenated2.append(f2.getText().trim());
					}
					if(trimmed1.contains(trimmed2) || trimmed2.contains(trimmed1)) {
						Pair<UMLTagElement, UMLTagElement> pair = Pair.of(deletedNestedTag, addedNestedTag);
						commonNestedTags.add(pair);
					}
					if(concatenated1.toString().replaceAll("\\s", "").equals(concatenated2.toString().replaceAll("\\s", ""))) {
						for(UMLDocElement deletedDocElement : deletedNestedTag.getFragments()) {
							for(UMLDocElement addedDocElement : addedNestedTag.getFragments()) {
								Pair<UMLDocElement, UMLDocElement> pair = Pair.of(deletedDocElement, addedDocElement);
								commonDocElements.add(pair);
							}
						}
					}
				}
			}
		}
		this.deletedNestedTags.addAll(deletedNestedTags);
		this.addedNestedTags.addAll(addedNestedTags);
	}

	private void processIdenticalTags(UMLTagElement tagBefore, UMLTagElement tagAfter) {
		List<UMLDocElement> fragmentsBefore = tagBefore.getFragments();
		List<UMLDocElement> fragmentsAfter = tagAfter.getFragments();
		for(int i=0; i<fragmentsBefore.size(); i++) {
			UMLDocElement docElementBefore = fragmentsBefore.get(i);
			UMLDocElement docElementAfter = fragmentsAfter.get(i);
			Pair<UMLDocElement, UMLDocElement> pair = Pair.of(docElementBefore, docElementAfter);
			commonDocElements.add(pair);
		}
	}

	private List<Integer> findAllMatchingIndices(List<UMLTagElement> tags, UMLTagElement docElement) {
		List<Integer> matchingIndices = new ArrayList<>();
		for(int i=0; i<tags.size(); i++) {
			UMLTagElement element = tags.get(i);
			if(docElement.equals(element)) {
				matchingIndices.add(i);
			}
		}
		return matchingIndices;
	}

	private List<Integer> findAllMatchingIndices(List<UMLDocElement> fragments, UMLDocElement docElement) {
		List<Integer> matchingIndices = new ArrayList<>();
		for(int i=0; i<fragments.size(); i++) {
			UMLDocElement element = fragments.get(i);
			if(docElement.equals(element)) {
				matchingIndices.add(i);
			}
		}
		return matchingIndices;
	}

	private boolean processModifiedTags(UMLTagElement tagBefore, UMLTagElement tagAfter) {
		int commonDocElementsBefore = commonDocElements.size();
		List<UMLDocElement> fragmentsBefore = tagBefore.getFragments();
		List<UMLDocElement> fragmentsAfter = tagAfter.getFragments();
		List<UMLDocElement> deletedDocElements = new ArrayList<UMLDocElement>(fragmentsBefore);
		List<UMLDocElement> addedDocElements = new ArrayList<UMLDocElement>(fragmentsAfter);
		if(fragmentsBefore.size() <= fragmentsAfter.size()) {
			for(UMLDocElement docElement : fragmentsBefore) {
				if(fragmentsAfter.contains(docElement)) {
					List<Integer> matchingIndices = findAllMatchingIndices(fragmentsAfter, docElement);
					boolean matchFound = false;
					List<Integer> beforeMatchingIndices = findAllMatchingIndices(fragmentsBefore, docElement);
					if(docElement.isHTMLTag() && beforeMatchingIndices.size() < matchingIndices.size()) {
						for(Integer index : matchingIndices) {
							if(index > 0 && index < fragmentsAfter.size()-1) {
								for(Integer beforeIndex : beforeMatchingIndices) {
									if(beforeIndex > 0 && beforeIndex < fragmentsBefore.size()-1) {
										UMLDocElement before1 = fragmentsBefore.get(beforeIndex-1);
										UMLDocElement after1 = fragmentsBefore.get(beforeIndex+1);
										UMLDocElement before2 = fragmentsAfter.get(index-1);
										UMLDocElement after2 = fragmentsAfter.get(index+1);
										if(before1.equals(before2) && after1.equals(after2) && !alreadyMatchedDocElement(docElement, fragmentsAfter.get(index)) &&
												!alreadyMatchedDocElement(fragmentsBefore.get(beforeIndex), docElement)) {
											matchFound = true;
											Pair<UMLDocElement, UMLDocElement> pair = Pair.of(fragmentsBefore.get(beforeIndex), fragmentsAfter.get(index));
											commonDocElements.add(pair);
											List<Integer> indices1 = findAllMatchingIndices(deletedDocElements, docElement);
											List<Integer> indices2 = findAllMatchingIndices(addedDocElements, docElement);
											int beforeIndexToRemove = beforeMatchingIndices.indexOf(beforeIndex);
											if(beforeIndexToRemove >= indices1.size()) {
												beforeIndexToRemove = indices1.size()-1;
											}
											deletedDocElements.remove((int)indices1.get(beforeIndexToRemove));
											int afterIndexToRemove = matchingIndices.indexOf(index);
											if(afterIndexToRemove >= indices2.size()) {
												afterIndexToRemove = indices2.size()-1;
											}
											addedDocElements.remove((int)indices2.get(afterIndexToRemove));
											break;
										}
									}
								}
								if(matchFound) {
									break;
								}
							}
						}
					}
					if(!matchFound) {
						for(Integer index : matchingIndices) {
							if(!alreadyMatchedDocElement(docElement, fragmentsAfter.get(index))) {
								Pair<UMLDocElement, UMLDocElement> pair = Pair.of(docElement, fragmentsAfter.get(index));
								commonDocElements.add(pair);
								deletedDocElements.remove(docElement);
								addedDocElements.remove(docElement);
								break;
							}
						}
					}
				}
			}
		}
		else {
			for(UMLDocElement docElement : fragmentsAfter) {
				if(fragmentsBefore.contains(docElement)) {
					List<Integer> matchingIndices = findAllMatchingIndices(fragmentsBefore, docElement);
					boolean matchFound = false;
					List<Integer> afterMatchingIndices = findAllMatchingIndices(fragmentsAfter, docElement);
					if(docElement.isHTMLTag() && afterMatchingIndices.size() < matchingIndices.size()) {
						for(Integer index : matchingIndices) {
							if(index > 0 && index < fragmentsBefore.size()-1) {
								for(Integer afterIndex : afterMatchingIndices) {
									if(afterIndex > 0 && afterIndex < fragmentsAfter.size()-1) {
										UMLDocElement before1 = fragmentsBefore.get(index-1);
										UMLDocElement after1 = fragmentsBefore.get(index+1);
										UMLDocElement before2 = fragmentsAfter.get(afterIndex-1);
										UMLDocElement after2 = fragmentsAfter.get(afterIndex+1);
										if(before1.equals(before2) && after1.equals(after2) && !alreadyMatchedDocElement(fragmentsBefore.get(index), docElement) &&
												!alreadyMatchedDocElement(docElement, fragmentsAfter.get(afterIndex))) {
											matchFound = true;
											Pair<UMLDocElement, UMLDocElement> pair = Pair.of(fragmentsBefore.get(index), fragmentsAfter.get(afterIndex));
											commonDocElements.add(pair);
											List<Integer> indices1 = findAllMatchingIndices(deletedDocElements, docElement);
											List<Integer> indices2 = findAllMatchingIndices(addedDocElements, docElement);
											int beforeIndexToRemove = matchingIndices.indexOf(index);
											if(beforeIndexToRemove >= indices1.size()) {
												beforeIndexToRemove = indices1.size()-1;
											}
											deletedDocElements.remove((int)indices1.get(beforeIndexToRemove));
											int afterIndexToRemove = afterMatchingIndices.indexOf(afterIndex);
											if(afterIndexToRemove >= indices2.size()) {
												afterIndexToRemove = indices2.size()-1;
											}
											addedDocElements.remove((int)indices2.get(afterIndexToRemove));
											break;
										}
									}
								}
								if(matchFound) {
									break;
								}
								//match HTML tags if the doc elements after them are identical and unique
								for(Integer afterIndex : afterMatchingIndices) {
									if(afterIndex > 0 && afterIndex < fragmentsAfter.size()-1) {
										UMLDocElement after1 = fragmentsBefore.get(index+1);
										List<Integer> after1MatchingIndices = findAllMatchingIndices(fragmentsBefore, after1);
										UMLDocElement after2 = fragmentsAfter.get(afterIndex+1);
										List<Integer> after2MatchingIndices = findAllMatchingIndices(fragmentsAfter, after2);
										if(after1.equals(after2) && !alreadyMatchedDocElement(fragmentsBefore.get(index), docElement) &&
												!alreadyMatchedDocElement(docElement, fragmentsAfter.get(afterIndex)) &&
												after1MatchingIndices.size() == 1 && after2MatchingIndices.size() == 1) {
											matchFound = true;
											Pair<UMLDocElement, UMLDocElement> pair = Pair.of(fragmentsBefore.get(index), fragmentsAfter.get(afterIndex));
											commonDocElements.add(pair);
											List<Integer> indices1 = findAllMatchingIndices(deletedDocElements, docElement);
											List<Integer> indices2 = findAllMatchingIndices(addedDocElements, docElement);
											int beforeIndexToRemove = matchingIndices.indexOf(index);
											if(beforeIndexToRemove >= indices1.size()) {
												beforeIndexToRemove = indices1.size()-1;
											}
											deletedDocElements.remove((int)indices1.get(beforeIndexToRemove));
											int afterIndexToRemove = afterMatchingIndices.indexOf(afterIndex);
											if(afterIndexToRemove >= indices2.size()) {
												afterIndexToRemove = indices2.size()-1;
											}
											addedDocElements.remove((int)indices2.get(afterIndexToRemove));
											break;
										}
									}
								}
								if(matchFound) {
									break;
								}
							}
						}
					}
					if(!matchFound) {
						for(Integer index : matchingIndices) {
							if(!alreadyMatchedDocElement(fragmentsBefore.get(index), docElement)) {
								Pair<UMLDocElement, UMLDocElement> pair = Pair.of(fragmentsBefore.get(index), docElement);
								commonDocElements.add(pair);
								deletedDocElements.remove(docElement);
								addedDocElements.remove(docElement);
								break;
							}
						}
					}
				}
			}
		}
		if(tagBefore.isParam() && tagAfter.isParam()) {
			String paramNameBefore = tagBefore.getParamName();
			String paramNameAfter = tagAfter.getParamName();
			if(paramNameBefore != null && paramNameAfter != null && !paramNameBefore.equals(paramNameAfter) &&
					fragmentsBefore.size() > 1 && fragmentsAfter.size() > 1) {
				Pair<UMLDocElement, UMLDocElement> pair = Pair.of(fragmentsBefore.get(1), fragmentsAfter.get(1));
				if(commonDocElements.contains(pair) && fragmentsBefore.get(0).getText().equals(paramNameBefore) && fragmentsAfter.get(0).getText().equals(paramNameAfter)) {
					Pair<UMLDocElement, UMLDocElement> variablePair = Pair.of(fragmentsBefore.get(0), fragmentsAfter.get(0));
					commonDocElements.add(variablePair);
					deletedDocElements.remove(fragmentsBefore.get(0));
					addedDocElements.remove(fragmentsAfter.get(0));
				}
			}
		}
		if(deletedDocElements.size() == 0 || addedDocElements.size() == 0) {
			this.deletedDocElements.addAll(deletedDocElements);
			this.addedDocElements.addAll(addedDocElements);
			return commonDocElements.size() > 0 || fragmentsBefore.isEmpty() || fragmentsAfter.isEmpty();
		}
		//match doc elements differing only in opening/closing quotes
		if(deletedDocElements.size() <= addedDocElements.size()) {
			for(UMLDocElement deletedDocElement : new ArrayList<>(deletedDocElements)) {
				String trimmed1 = deletedDocElement.getText().replaceAll("^\"|\"$", "");
				for(UMLDocElement addedDocElement : new ArrayList<>(addedDocElements)) {
					String trimmed2 = addedDocElement.getText().replaceAll("^\"|\"$", "");
					if(trimmed1.equals(trimmed2) || trimmed1.equals(trimmed2 + ".") || trimmed2.equals(trimmed1 + ".")) {
						Pair<UMLDocElement, UMLDocElement> pair = Pair.of(deletedDocElement, addedDocElement);
						commonDocElements.add(pair);
						deletedDocElements.remove(deletedDocElement);
						addedDocElements.remove(addedDocElement);
					}
				}
			}
		}
		else {
			for(UMLDocElement addedDocElement : new ArrayList<>(addedDocElements)) {
				String trimmed2 = addedDocElement.getText().replaceAll("^\"|\"$", "");
				for(UMLDocElement deletedDocElement : new ArrayList<>(deletedDocElements)) {
					String trimmed1 = deletedDocElement.getText().replaceAll("^\"|\"$", "");
					if(trimmed1.equals(trimmed2) || trimmed1.equals(trimmed2 + ".") || trimmed2.equals(trimmed1 + ".")) {
						Pair<UMLDocElement, UMLDocElement> pair = Pair.of(deletedDocElement, addedDocElement);
						commonDocElements.add(pair);
						deletedDocElements.remove(deletedDocElement);
						addedDocElements.remove(addedDocElement);
					}
				}
			}
		}
		List<UMLDocElement> deletedToBeDeleted = new ArrayList<UMLDocElement>();
		List<UMLDocElement> addedToBeDeleted = new ArrayList<UMLDocElement>();
		if(deletedDocElements.size() == addedDocElements.size()) {
			for(int i=0; i<deletedDocElements.size(); i++) {
				UMLDocElement deletedDocElement = deletedDocElements.get(i);
				UMLDocElement addedDocElement = addedDocElements.get(i);
				if(deletedDocElement.getText().replaceAll("\\s", "").equals(addedDocElement.getText().replaceAll("\\s", ""))) {
					Pair<UMLDocElement, UMLDocElement> pair = Pair.of(deletedDocElement, addedDocElement);
					commonDocElements.add(pair);
					deletedToBeDeleted.add(deletedDocElement);
					addedToBeDeleted.add(addedDocElement);
				}
			}
			deletedDocElements.removeAll(deletedToBeDeleted);
			addedDocElements.removeAll(addedToBeDeleted);
		}
		//check if all deleted docElements match all added docElements
		StringBuilder deletedSB = new StringBuilder();
		List<String> deletedTokenSequence = new ArrayList<String>();
		Map<UMLDocElement, List<String>> deletedTokenSequenceMap = new LinkedHashMap<>();
		for(UMLDocElement deletedDocElement : deletedDocElements) {
			String text = deletedDocElement.getText();
			deletedSB.append(text);
			List<String> splitToWords = splitToWords(text);
			deletedTokenSequence.addAll(splitToWords);
			deletedTokenSequenceMap.put(deletedDocElement, splitToWords);
		}
		StringBuilder addedSB = new StringBuilder();
		List<String> addedTokenSequence = new ArrayList<String>();
		Map<UMLDocElement, List<String>> addedTokenSequenceMap = new LinkedHashMap<>();
		for(UMLDocElement addedDocElement : addedDocElements) {
			String text = addedDocElement.getText();
			addedSB.append(text);
			List<String> splitToWords = splitToWords(text);
			addedTokenSequence.addAll(splitToWords);
			addedTokenSequenceMap.put(addedDocElement, splitToWords);
		}
		String deletedWithoutTags = Jsoup.parse(deletedSB.toString()).text();
		String addedWithoutTags = Jsoup.parse(addedSB.toString()).text();
		if(deletedSB.toString().replaceAll("\\s", "").equals(addedSB.toString().replaceAll("\\s", ""))) {
			//make all pair combinations
			for(UMLDocElement deletedDocElement : deletedDocElements) {
				for(UMLDocElement addedDocElement : addedDocElements) {
					Pair<UMLDocElement, UMLDocElement> pair = Pair.of(deletedDocElement, addedDocElement);
					commonDocElements.add(pair);
				}
			}
			if(deletedDocElements.size() >= 1 && addedDocElements.size() >= 1) {
				manyToManyReformat = true;
			}
			return true;
		}
		else if(deletedWithoutTags.replaceAll("\\s", "").equals(addedWithoutTags.replaceAll("\\s", ""))) {
			//make all pair combinations
			for(UMLDocElement deletedDocElement : deletedDocElements) {
				for(UMLDocElement addedDocElement : addedDocElements) {
					Pair<UMLDocElement, UMLDocElement> pair = Pair.of(deletedDocElement, addedDocElement);
					commonDocElements.add(pair);
				}
			}
			if(deletedDocElements.size() >= 1 && addedDocElements.size() >= 1) {
				manyToManyReformat = true;
			}
			return true;
		}
		else if(normalizedEditDistance(deletedWithoutTags.replaceAll("\\s", ""), addedWithoutTags.replaceAll("\\s", "")) < 0.1) {
			//make all pair combinations
			for(UMLDocElement deletedDocElement : deletedDocElements) {
				for(UMLDocElement addedDocElement : addedDocElements) {
					Pair<UMLDocElement, UMLDocElement> pair = Pair.of(deletedDocElement, addedDocElement);
					commonDocElements.add(pair);
				}
			}
			if(deletedDocElements.size() >= 1 && addedDocElements.size() >= 1) {
				manyToManyReformat = true;
			}
			return true;
		}
		else {
			//match doc elements that one contains a subsequence of the other
			if(deletedTokenSequence.size() <= addedTokenSequence.size()) {
				List<String> longestSubSequence = null;
				for(int i=0; i<deletedTokenSequence.size(); i++) {
					for(int j=i+1; j<deletedTokenSequence.size(); j++) {
						List<String> subList = deletedTokenSequence.subList(i,j+1);
						if(subList.size() > 2) {
							int indexOfSubList = Collections.indexOfSubList(addedTokenSequence, subList);
							if(indexOfSubList != -1) {
								if(longestSubSequence == null) {
									longestSubSequence = subList;
								}
								else if(subList.containsAll(longestSubSequence) && subList.size() > longestSubSequence.size()) {
									longestSubSequence = subList;
								}
							}
						}
					}
					if(longestSubSequence != null && longestSubSequence.equals(deletedTokenSequence)) {
						break;
					}
				}
				if(longestSubSequence != null) {
					//make all pair combinations
					for(UMLDocElement deletedDocElement : deletedDocElements) {
						if(containsAnySubSequence(deletedTokenSequenceMap.get(deletedDocElement), longestSubSequence)) {
							for(UMLDocElement addedDocElement : addedDocElements) {
								if(containsAnySubSequence(addedTokenSequenceMap.get(addedDocElement), longestSubSequence)) {
									if(!alreadyMatchedDocElement(deletedDocElement, addedDocElement)) {
										Pair<UMLDocElement, UMLDocElement> pair = Pair.of(deletedDocElement, addedDocElement);
										commonDocElements.add(pair);
										deletedToBeDeleted.add(deletedDocElement);
										addedToBeDeleted.add(addedDocElement);
									}
								}
							}
						}
					}
					if(deletedDocElements.size() >= 1 && addedDocElements.size() >= 1) {
						manyToManyReformat = true;
					}
				}
			}
			else {
				List<String> longestSubSequence = null;
				for(int i=0; i<addedTokenSequence.size(); i++) {
					for(int j=i+1; j<addedTokenSequence.size(); j++) {
						List<String> subList = addedTokenSequence.subList(i,j+1);
						if(subList.size() > 2) {
							int indexOfSubList = Collections.indexOfSubList(deletedTokenSequence, subList);
							if(indexOfSubList != -1) {
								if(longestSubSequence == null) {
									longestSubSequence = subList;
								}
								else if(subList.containsAll(longestSubSequence) && subList.size() > longestSubSequence.size()) {
									longestSubSequence = subList;
								}
							}
						}
					}
					if(longestSubSequence != null && longestSubSequence.equals(addedTokenSequence)) {
						break;
					}
				}
				if(longestSubSequence != null) {
					//make all pair combinations
					for(UMLDocElement deletedDocElement : deletedDocElements) {
						if(containsAnySubSequence(deletedTokenSequenceMap.get(deletedDocElement), longestSubSequence)) {
							for(UMLDocElement addedDocElement : addedDocElements) {
								if(containsAnySubSequence(addedTokenSequenceMap.get(addedDocElement), longestSubSequence)) {
									if(!alreadyMatchedDocElement(deletedDocElement, addedDocElement) ||
											(longestSubSequence.containsAll(deletedTokenSequenceMap.get(deletedDocElement)) &&
											deletedTokenSequenceMap.get(deletedDocElement).size() > 1)) {
										Pair<UMLDocElement, UMLDocElement> pair = Pair.of(deletedDocElement, addedDocElement);
										commonDocElements.add(pair);
										deletedToBeDeleted.add(deletedDocElement);
										addedToBeDeleted.add(addedDocElement);
									}
								}
							}
						}
					}
					if(deletedDocElements.size() >= 1 && addedDocElements.size() >= 1) {
						manyToManyReformat = true;
					}
				}
			}
		}
		deletedDocElements.removeAll(deletedToBeDeleted);
		addedDocElements.removeAll(addedToBeDeleted);
		if(deletedDocElements.size() > addedDocElements.size()) {
			for(UMLDocElement addedDocElement : addedDocElements) {
				String text = addedDocElement.getText();
				for(int i=0; i<deletedDocElements.size()-1; i++) {
					List<UMLDocElement> matches = findConcatenatedMatch(deletedDocElements, text, i);
					if(matches.size() > 0) {
						for(UMLDocElement match : matches) {
							Pair<UMLDocElement, UMLDocElement> pair = Pair.of(match, addedDocElement);
							commonDocElements.add(pair);
							deletedToBeDeleted.add(match);
						}
						addedToBeDeleted.add(addedDocElement);
						break;
					}
				}
			}
		}
		else {
			for(UMLDocElement deletedDocElement : deletedDocElements) {
				String text = deletedDocElement.getText();
				for(int i=0; i<addedDocElements.size()-1; i++) {
					List<UMLDocElement> matches = findConcatenatedMatch(addedDocElements, text, i);
					if(matches.size() > 0) {
						for(UMLDocElement match : matches) {
							Pair<UMLDocElement, UMLDocElement> pair = Pair.of(deletedDocElement, match);
							commonDocElements.add(pair);
							addedToBeDeleted.add(match);
						}
						deletedToBeDeleted.add(deletedDocElement);
						break;
					}
				}
			}
		}
		deletedDocElements.removeAll(deletedToBeDeleted);
		addedDocElements.removeAll(addedToBeDeleted);
		//match doc elements that one contains the other
		if(deletedDocElements.size() <= addedDocElements.size()) {
			for(UMLDocElement deletedDocElement : new ArrayList<>(deletedDocElements)) {
				if(deletedDocElement.getText().length() > 2) {
					for(UMLDocElement addedDocElement : new ArrayList<>(addedDocElements)) {
						if(addedDocElement.getText().length() > 2) {
							if(deletedDocElement.getText().contains(addedDocElement.getText()) || addedDocElement.getText().contains(deletedDocElement.getText())) {
								Pair<UMLDocElement, UMLDocElement> pair = Pair.of(deletedDocElement, addedDocElement);
								commonDocElements.add(pair);
								deletedDocElements.remove(deletedDocElement);
								addedDocElements.remove(addedDocElement);
							}
						}
					}
				}
			}
		}
		else {
			for(UMLDocElement addedDocElement : new ArrayList<>(addedDocElements)) {
				if(addedDocElement.getText().length() > 2) {
					for(UMLDocElement deletedDocElement : new ArrayList<>(deletedDocElements)) {
						if(deletedDocElement.getText().length() > 2) {
							if(deletedDocElement.getText().contains(addedDocElement.getText()) || addedDocElement.getText().contains(deletedDocElement.getText())) {
								Pair<UMLDocElement, UMLDocElement> pair = Pair.of(deletedDocElement, addedDocElement);
								commonDocElements.add(pair);
								deletedDocElements.remove(deletedDocElement);
								addedDocElements.remove(addedDocElement);
							}
						}
					}
				}
			}
		}
		this.deletedDocElements.addAll(deletedDocElements);
		this.addedDocElements.addAll(addedDocElements);
		if(commonDocElements.size() > commonDocElementsBefore) {
			return true;
		}
		return false;
	}

	private double normalizedEditDistance(String s1, String s2) {
		int distance = StringDistance.editDistance(s1, s2);
		double normalized = (double)distance/(double)Math.max(s1.length(), s2.length());
		return normalized;
	}

	private boolean alreadyMatchedNestedTagElement(UMLTagElement deletedTagElement, UMLTagElement addedTagElement) {
		for(Pair<UMLTagElement, UMLTagElement> pair : commonNestedTags) {
			if(pair.getLeft() == deletedTagElement) {
				return true;
			}
			if(pair.getRight() == addedTagElement) {
				return true;
			}
		}
		return false;
	}

	private boolean alreadyMatchedDocElement(UMLDocElement deletedDocElement, UMLDocElement addedDocElement) {
		for(Pair<UMLDocElement, UMLDocElement> pair : commonDocElements) {
			if(pair.getLeft() == deletedDocElement) {
				if(pair.getLeft().getText().contains(pair.getRight().getText()) || pair.getRight().getText().contains(pair.getLeft().getText()))
					return true;
			}
			if(pair.getRight() == addedDocElement) {
				if(pair.getLeft().getText().contains(pair.getRight().getText()) || pair.getRight().getText().contains(pair.getLeft().getText()))
					return true;
			}
		}
		return false;
	}

	private List<UMLDocElement> findConcatenatedMatch(List<UMLDocElement> docElements, String text, int startIndex) {
		StringBuilder concatText = new StringBuilder();
		for(int i=startIndex; i<docElements.size(); i++) {
			concatText.append(docElements.get(i).getText());
			if(concatText.toString().replaceAll("\\s", "").equals(text.replaceAll("\\s", ""))) {
				return new ArrayList<>(docElements.subList(startIndex, i+1));
			}
		}
		return Collections.emptyList();
	}

	private boolean containsAnySubSequence(List<String> list, List<String> longestSubSequence) {
		if(list.size() > 1 && Collections.indexOfSubList(longestSubSequence, list) != -1)
			return true;
		for(int i=longestSubSequence.size(); i>1; i--) {
			List<String> subList = longestSubSequence.subList(0,i);
			int index = Collections.indexOfSubList(list, subList);
			if(index != -1 && (subList.size() > 1 || (subList.size() == 1 && Character.isUpperCase(subList.get(0).charAt(0))))) {
				return true;
			}
		}
		for(int i=0; i<longestSubSequence.size(); i++) {
			List<String> subList = longestSubSequence.subList(i,longestSubSequence.size());
			int index = Collections.indexOfSubList(list, subList);
			if(index != -1 && (subList.size() > 1 || (subList.size() == 1 && Character.isUpperCase(subList.get(0).charAt(0))))) {
				return true;
			}
		}
		return false;
	}

	private List<String> splitToWords(String sentence) {
		ArrayList<String> words = new ArrayList<String>();
		BreakIterator boundary = BreakIterator.getWordInstance();
		boundary.setText(sentence);
		int start = boundary.first();
		for (int end = boundary.next();
				end != BreakIterator.DONE;
				start = end, end = boundary.next()) {
			String word = sentence.substring(start,end);
			if(!word.isBlank())
				words.add(word);
		}
		return words;
	}

	public UMLJavadoc getJavadocBefore() {
		return javadocBefore;
	}

	public UMLJavadoc getJavadocAfter() {
		return javadocAfter;
	}

	public List<Pair<UMLTagElement, UMLTagElement>> getCommonTags() {
		return commonTags;
	}

	public List<Pair<UMLDocElement, UMLDocElement>> getCommonDocElements() {
		return commonDocElements;
	}

	public List<UMLTagElement> getDeletedTags() {
		return deletedTags;
	}

	public List<UMLTagElement> getAddedTags() {
		return addedTags;
	}

	public List<UMLDocElement> getDeletedDocElements() {
		return deletedDocElements;
	}

	public List<UMLDocElement> getAddedDocElements() {
		return addedDocElements;
	}

	public List<Pair<UMLTagElement, UMLTagElement>> getCommonNestedTags() {
		return commonNestedTags;
	}

	public List<UMLTagElement> getDeletedNestedTags() {
		return deletedNestedTags;
	}

	public List<UMLTagElement> getAddedNestedTags() {
		return addedNestedTags;
	}

	public boolean isManyToManyReformat() {
		return manyToManyReformat;
	}
}
