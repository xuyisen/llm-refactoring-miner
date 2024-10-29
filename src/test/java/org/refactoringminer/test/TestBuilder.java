package org.refactoringminer.test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import gr.uom.java.xmi.LocationInfo;
import gr.uom.java.xmi.UMLModel;
import gr.uom.java.xmi.UMLOperation;
import gr.uom.java.xmi.decomposition.AbstractCall;
import gr.uom.java.xmi.decomposition.CompositeStatementObject;
import gr.uom.java.xmi.decomposition.OperationBody;
import gr.uom.java.xmi.diff.ExtractOperationRefactoring;
import gr.uom.java.xmi.diff.UMLModelDiff;
import gui.entity.Commit;
import gui.entity.CommitsResponse;
import gui.entity.Location;
import gui.utils.JsonUtil;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.Assertions;
import org.refactoringminer.api.GitHistoryRefactoringMiner;
import org.refactoringminer.api.GitService;
import org.refactoringminer.api.Refactoring;
import org.refactoringminer.api.RefactoringHandler;
import org.refactoringminer.api.RefactoringType;
import org.refactoringminer.rm1.GitHistoryRefactoringMinerImpl;
import org.refactoringminer.test.RefactoringPopulator.Refactorings;
import org.refactoringminer.util.GitServiceImpl;
import org.springframework.util.CollectionUtils;

public class TestBuilder {

	private final String tempDir;
	private final Map<String, ProjectMatcher> map;
	private final GitHistoryRefactoringMiner refactoringDetector;
	private boolean verbose;
	private boolean aggregate;
	private int commitsCount;
	private int errorCommitsCount;
	private Counter c;// = new Counter();
	private Map<RefactoringType, Counter> cMap;
	private static final int TP = 0;
	private static final int FP = 1;
	private static final int FN = 2;
	private static final int TN = 3;
	private static final int UNK = 4;
    private static final List<Commit> commitsForRAG = new ArrayList<>();
	private BigInteger refactoringFilter;

	public TestBuilder(GitHistoryRefactoringMiner detector, String tempDir) {
		this.map = new HashMap<String, ProjectMatcher>();
		this.refactoringDetector = detector;
		this.tempDir = tempDir;
		this.verbose = false;
		this.aggregate = false;
	}

	public TestBuilder(GitHistoryRefactoringMiner detector, String tempDir, BigInteger refactorings) {
		this(detector, tempDir);

		this.refactoringFilter = refactorings;
	}

	public TestBuilder verbose() {
		this.verbose = true;
		return this;
	}

	public TestBuilder withAggregation() {
		this.aggregate = true;
		return this;
	}

	private static class Counter {
		int[] c = new int[5];
	}

	private void count(int type, String refactoring) {
		c.c[type]++;
		RefactoringType refType = RefactoringType.extractFromDescription(refactoring);
		Counter refTypeCounter = cMap.get(refType);
		if (refTypeCounter == null) {
			refTypeCounter = new Counter();
			cMap.put(refType, refTypeCounter);
		}
		refTypeCounter.c[type]++;
	}

	private int get(int type) {
		return c.c[type];
	}

	private int get(int type, Counter counter) {
		return counter.c[type];
	}

	public TestBuilder() {
		this(new GitHistoryRefactoringMinerImpl(), "tmp");
	}

	public final ProjectMatcher project(String cloneUrl, String branch) {
		ProjectMatcher projectMatcher = this.map.get(cloneUrl);
		if (projectMatcher == null) {
			projectMatcher = new ProjectMatcher(cloneUrl, branch);
			this.map.put(cloneUrl, projectMatcher);
		}
		return projectMatcher;
	}

	public void assertExpectationsWithGitHubAPI(int expectedTPs, int expectedFPs, int expectedFNs) throws Exception {
		c = new Counter();
		cMap = new HashMap<RefactoringType, Counter>();
		commitsCount = 0;
		errorCommitsCount = 0;
		ExecutorService pool = Executors.newWorkStealingPool();
		File rootFolder = new File(tempDir);
		for (ProjectMatcher m : map.values()) {
			if (m.ignoreNonSpecifiedCommits) {
				for (String commitId : m.getCommits()) {
					Runnable r = () -> ((GitHistoryRefactoringMinerImpl)refactoringDetector).detectAtCommitWithGitHubAPI(m.cloneUrl, commitId, rootFolder, m);
					pool.submit(r);
				}
			}
		}
		pool.shutdown();
		pool.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
		System.out.println(String.format("Commits: %d  Errors: %d", commitsCount, errorCommitsCount));

		String mainResultMessage = buildResultMessage(c);
		System.out.println("Total  " + mainResultMessage);
		for (RefactoringType refType : RefactoringType.values()) {
			Counter refTypeCounter = cMap.get(refType);
			if (refTypeCounter != null) {
				System.out
						.println(String.format("%-7s", refType.getAbbreviation()) + buildResultMessage(refTypeCounter));
			}
		}

		boolean success = get(FP) == expectedFPs && get(FN) == expectedFNs && get(TP) == expectedTPs;
		if (!success || verbose) {
			for (ProjectMatcher m : map.values()) {
				m.printResults();
			}
		}
		String refactoringJsonPathWithSC = "tmp/data/output/refactoring_miner_em_refactoring_w_sc.json";
		CommitsResponse commitsResponse = new CommitsResponse();
		commitsResponse.setCommits(commitsForRAG);
		JsonUtil.writeJsonToFile(refactoringJsonPathWithSC, commitsResponse);
		//System.out.println(buildMarkup());
		Assertions.assertTrue(success, mainResultMessage);
	}

	public void assertExpectations(int expectedTPs, int expectedFPs, int expectedFNs) throws Exception {
		c = new Counter();
		cMap = new HashMap<RefactoringType, Counter>();
		commitsCount = 0;
		errorCommitsCount = 0;
		GitService gitService = new GitServiceImpl();
		ExecutorService pool = Executors.newWorkStealingPool();
		for (ProjectMatcher m : map.values()) {
			String folder = tempDir + "/"
					+ m.cloneUrl.substring(m.cloneUrl.lastIndexOf('/') + 1, m.cloneUrl.lastIndexOf('.'));
			try (Repository rep = gitService.cloneIfNotExists(folder,
					m.cloneUrl/* , m.branch */)) {
				if (m.ignoreNonSpecifiedCommits) {
					// It is faster to only look at particular commits
					for (String commitId : m.getCommits()) {
						Runnable r = () -> refactoringDetector.detectAtCommit(rep, commitId, m);
						pool.submit(r);
					}
				} else {
					// Iterate over each commit
					refactoringDetector.detectAll(rep, m.branch, m);
				}
			}
		}
		pool.shutdown();
		pool.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
		System.out.println(String.format("Commits: %d  Errors: %d", commitsCount, errorCommitsCount));

		String mainResultMessage = buildResultMessage(c);
		System.out.println("Total  " + mainResultMessage);
		for (RefactoringType refType : RefactoringType.values()) {
			Counter refTypeCounter = cMap.get(refType);
			if (refTypeCounter != null) {
				System.out
						.println(String.format("%-7s", refType.getAbbreviation()) + buildResultMessage(refTypeCounter));
			}
		}

		boolean success = get(FP) == expectedFPs && get(FN) == expectedFNs && get(TP) == expectedTPs;
		if (!success || verbose) {
			for (ProjectMatcher m : map.values()) {
				m.printResults();
			}
		}
		System.out.println(buildMarkup());
		Assertions.assertTrue(success, mainResultMessage);
	}

	private String buildMarkup() {
		StringBuilder sb = new StringBuilder();
		sb.append("| Refactoring Type | TP | FP | FN | Precision | Recall |").append("\n");
		sb.append("|:-----------------------|-----------:|--------:|--------:|--------:|--------:|").append("\n");
		for (RefactoringType refType : RefactoringType.values()) {
			Counter refTypeCounter = cMap.get(refType);
			if (refTypeCounter != null) {
				sb.append("|" + refType.getDisplayName() + buildResultMessageMarkup(refTypeCounter)).append("\n");
			}
		}
		sb.append("|Total" + buildResultMessageMarkup(c));
		return sb.toString();
	}

	private String buildResultMessageMarkup(Counter c) {
		double precision = ((double) get(TP, c) / (get(TP, c) + get(FP, c)));
		double recall = ((double) get(TP, c)) / (get(TP, c) + get(FN, c));
		String mainResultMessage = String.format(
				"|%2d  | %2d  | %2d  | %.3f  | %.3f|", get(TP, c), get(FP, c),
				get(FN, c), precision, recall);
		return mainResultMessage;
	}

	private String buildResultMessage(Counter c) {
		double precision = ((double) get(TP, c) / (get(TP, c) + get(FP, c)));
		double recall = ((double) get(TP, c)) / (get(TP, c) + get(FN, c));
		String mainResultMessage = String.format(
				"TP: %2d  FP: %2d  FN: %2d  TN: %2d  Unk.: %2d  Prec.: %.3f  Recall: %.3f", get(TP, c), get(FP, c),
				get(FN, c), get(TN, c), get(UNK, c), precision, recall);
		return mainResultMessage;
	}

	private List<String> normalize(String refactoring) {
		RefactoringType refType = RefactoringType.extractFromDescription(refactoring);
		refactoring = normalizeSingle(refactoring);
		if (aggregate) {
			refactoring = refType.aggregate(refactoring);
		} else {
			int begin = refactoring.indexOf("from classes [");
			if (begin != -1) {
				int end = refactoring.lastIndexOf(']');
				String types = refactoring.substring(begin + "from classes [".length(), end);
				String[] typesArray = types.split(", ");
				List<String> refactorings = new ArrayList<String>();
				for (String type : typesArray) {
					refactorings.add(refactoring.substring(0, begin) + "from class " + type);
				}
				return refactorings;
			}
		}
		return Collections.singletonList(refactoring);
	}

	/**
	 * Remove generics type information.
	 */
	private static String normalizeSingle(String refactoring) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < refactoring.length(); i++) {
			char c = refactoring.charAt(i);
			if (c == '\t') {
				c = ' ';
			}
			sb.append(c);
		}
		return sb.toString();
	}

	public class ProjectMatcher extends RefactoringHandler {

		private final String cloneUrl;
		private final String branch;
		private Map<String, CommitMatcher> expected = new HashMap<>();
		private boolean ignoreNonSpecifiedCommits = true;
		private int truePositiveCount = 0;
		private int falsePositiveCount = 0;
		private int falseNegativeCount = 0;
		private int trueNegativeCount = 0;
		private int unknownCount = 0;
		// private int errorsCount = 0;

		private ProjectMatcher(String cloneUrl, String branch) {
			this.cloneUrl = cloneUrl;
			this.branch = branch;
		}

		public ProjectMatcher atNonSpecifiedCommitsContainsNothing() {
			this.ignoreNonSpecifiedCommits = false;
			return this;
		}

		public CommitMatcher atCommit(String commitId) {
			CommitMatcher m = expected.get(commitId);
			if (m == null) {
				m = new CommitMatcher();
				expected.put(commitId, m);
			}
			return m;
		}

		public Set<String> getCommits() {
			return expected.keySet();
		}

		@Override
		public boolean skipCommit(String commitId) {
			if (this.ignoreNonSpecifiedCommits) {
				return !this.expected.containsKey(commitId);
			}
			return false;
		}

		@Override
		public void handleModelDiffWithContent(String commitId, List<Refactoring> refactorings, UMLModelDiff modelDiff, Map<String, String> fileContentsBefore, Map<String, String> fileContentsCurrent) {
			refactorings = filterRefactoring(refactorings);
			CommitMatcher matcher;
			if (expected.containsKey(commitId)) {
				matcher = expected.get(commitId);
			} else if (!this.ignoreNonSpecifiedCommits) {
				matcher = this.atCommit(commitId);
				matcher.containsOnly();
			} else {
				// ignore this commit
				matcher = null;
			}
			if(matcher != null){
				Commit commit = new Commit();
				Map<String, gui.entity.Refactoring> filePathToRefactoring = new HashMap<>();
				Set<String> commitFileLineUniqueIds = new HashSet<>();
				for (Refactoring refactoring : refactorings) {
					if(isMethodExtraction(refactoring)){
						getMethodExtraction(commitId, refactoring, modelDiff, fileContentsBefore, fileContentsCurrent, filePathToRefactoring, commitFileLineUniqueIds, matcher);
					}
				}

				List<gui.entity.Refactoring> refactoringList = new ArrayList<>(filePathToRefactoring.values());
				if(!refactoringList.isEmpty()){
					commit.setRefactorings(refactoringList);
				}else{
					commit.setRefactorings(new ArrayList<>());
				}
				commit.setUrl(cloneUrl);
				commit.setCommitId(commitId);
				commit.setBranch(branch);
				commitsForRAG.add(commit);
			}
		}

		private boolean isMethodExtraction(Refactoring refactoring) {
			return refactoring instanceof ExtractOperationRefactoring extractOperationRefactoring && StringUtils.equalsIgnoreCase(extractOperationRefactoring.getName(), "Extract Method");
		}

		private void getMethodExtraction(String commitId, Refactoring refactoring, UMLModelDiff modelDiff, Map<String, String> fileContentsBefore, Map<String, String> fileContentsCurrent, Map<String, gui.entity.Refactoring> filePathToRefactoring, Set<String> commitFileLineUniqueIds, CommitMatcher matcher) {
			if (refactoring instanceof ExtractOperationRefactoring extractOperationRefactoring && StringUtils.equalsIgnoreCase(extractOperationRefactoring.getName(), "Extract Method")) {
				LocationInfo locationInfoBefore = extractOperationRefactoring.getSourceOperationBeforeExtraction().getLocationInfo();
				LocationInfo locationInfoAfter = extractOperationRefactoring.getSourceOperationAfterExtraction().getLocationInfo();
				LocationInfo locationInfoExtracted = extractOperationRefactoring.getExtractedOperation().getLocationInfo();
				String filePathBefore = locationInfoBefore.getFilePath();
				String filePathAfter = locationInfoAfter.getFilePath();
				String sourceCodeBefore = getSourceCodeByLocationInfo(locationInfoBefore, filePathBefore, fileContentsBefore);
				String sourceCodeAfter = getSourceCodeByLocationInfo(locationInfoAfter, filePathAfter, fileContentsCurrent);
				String extractedCode = getSourceCodeByLocationInfo(locationInfoExtracted, filePathAfter, fileContentsCurrent);
				String uniqueId = commitId + "_" + locationInfoBefore.getStartLine() + "_" + locationInfoBefore.getEndLine() + "_" + locationInfoExtracted.getStartLine() + "_" + locationInfoExtracted.getEndLine() + "_" + locationInfoAfter.getStartLine() + "_" + locationInfoAfter.getEndLine();
				String commitFileLineUniqueId = commitId + "_" + filePathBefore + "_" + locationInfoBefore.getStartLine() + "_" + locationInfoBefore.getEndLine();
				if (filePathToRefactoring.containsKey(filePathBefore)) {
					gui.entity.Refactoring refactoringOutput = filePathToRefactoring.get(filePathBefore);
					if (!commitFileLineUniqueIds.contains(commitFileLineUniqueId)) {
						commitFileLineUniqueIds.add(commitFileLineUniqueId);
						refactoringOutput.setSourceCodeBeforeRefactoring(refactoringOutput.getSourceCodeBeforeRefactoring() + "\n" + sourceCodeBefore);
						Set<String> diffSourceCodeSet = refactoringOutput.getDiffSourceCodeSet();
						if(!diffSourceCodeSet.contains(extractedCode)){
							refactoringOutput.setSourceCodeAfterRefactoring(refactoringOutput.getSourceCodeAfterRefactoring() + "\n" + sourceCodeAfter + '\n' + extractedCode);
						}else{
							diffSourceCodeSet.add(extractedCode);
							refactoringOutput.setSourceCodeAfterRefactoring(refactoringOutput.getSourceCodeAfterRefactoring() + "\n" + sourceCodeAfter);
						}
						String className = extractOperationRefactoring.getSourceOperationBeforeExtraction().getClassName();
						refactoringOutput.setDiffSourceCodeSet(diffSourceCodeSet);
						refactoringOutput.setUniqueId(refactoringOutput.getUniqueId());
						setPackageAndClassInfo(className, modelDiff, refactoringOutput, extractOperationRefactoring);
						handleDiffCode(filePathBefore, locationInfoBefore, filePathAfter, locationInfoAfter, locationInfoExtracted, fileContentsBefore, fileContentsCurrent, refactoringOutput);
						Boolean pureRefactoring = refactoringOutput.getPureRefactoring();
						refactoringOutput.setPureRefactoring(pureRefactoring || isPureExtractOperation(matcher, refactoring, refactoringOutput));
					}
				} else {
					gui.entity.Refactoring refactoringOutput = new gui.entity.Refactoring();
					refactoringOutput.setFilePathBefore(filePathBefore);
					refactoringOutput.setType(extractOperationRefactoring.getName());
					refactoringOutput.setUniqueId(uniqueId);
					refactoringOutput.setSourceCodeBeforeRefactoring(sourceCodeBefore);
					refactoringOutput.setSourceCodeAfterRefactoring(sourceCodeAfter + '\n' + extractedCode);
					String className = extractOperationRefactoring.getSourceOperationBeforeExtraction().getClassName();
					refactoringOutput.setCommitId(commitId);
					refactoringOutput.setClassNameBefore(extractOperationRefactoring.getSourceOperationBeforeExtraction().getClassName());
					refactoringOutput.setPackageNameBefore(className.substring(0, className.lastIndexOf('.')));
					refactoringOutput.setDiffSourceCodeSet(new HashSet<>(Collections.singletonList(extractedCode)));
					refactoringOutput.setInvokedMethodSet(new HashSet<>());
					setPackageAndClassInfo(className, modelDiff, refactoringOutput, extractOperationRefactoring);
					refactoringOutput.setPureRefactoring(isPureExtractOperation(matcher, refactoring, refactoringOutput));
					handleDiffCode(filePathBefore, locationInfoBefore, filePathAfter, locationInfoAfter, locationInfoExtracted, fileContentsBefore, fileContentsCurrent, refactoringOutput);
					filePathToRefactoring.put(filePathBefore, refactoringOutput);
					commitFileLineUniqueIds.add(commitFileLineUniqueId);
				}
			}
		}

		private void handleDiffCode(String filePathBefore, LocationInfo locationInfoBefore, String filePathAfter, LocationInfo locationInfoAfter, LocationInfo locationInfoExtracted, Map<String, String> fileContentsBefore, Map<String, String> fileContentsCurrent, gui.entity.Refactoring refactoringOutput) {
			handleLocations(filePathBefore, locationInfoBefore, filePathAfter, locationInfoAfter, locationInfoExtracted, refactoringOutput);
			List<Location> locations = refactoringOutput.getDiffLocations();
			if(CollectionUtils.isEmpty(locations)){
				refactoringOutput.setDiffSourceCode("");
				return ;
			}
			String diffCode = getDiffCodeContent(filePathBefore,fileContentsBefore, filePathAfter, fileContentsCurrent, locations);
			refactoringOutput.setDiffSourceCode(diffCode);
		}

		private void handleLocations(String filePathBefore, LocationInfo locationInfoBefore, String filePathAfter, LocationInfo locationInfoAfter, LocationInfo locationInfoExtracted, gui.entity.Refactoring refactoringOutput) {
			List<Location> locations = refactoringOutput.getDiffLocations() == null ? new ArrayList<>() : refactoringOutput.getDiffLocations();
			Location locationBefore = new Location();
			locationBefore.setFilePath(filePathBefore);
			locationBefore.setStartLine(locationInfoBefore.getStartLine());
			locationBefore.setEndLine(locationInfoBefore.getEndLine());
			locations.add(locationBefore);
			Location locationAfter = new Location();
			locationAfter.setFilePath(filePathAfter);
			locationAfter.setStartLine(locationInfoAfter.getStartLine());
			locationAfter.setEndLine(locationInfoAfter.getEndLine());
			locations.add(locationAfter);
			Location locationExtracted = new Location();
			locationExtracted.setFilePath(filePathAfter);
			locationExtracted.setStartLine(locationInfoExtracted.getStartLine());
			locationExtracted.setEndLine(locationInfoExtracted.getEndLine());
			locations.add(locationExtracted);
			refactoringOutput.setDiffLocations(locations);
		}



		private boolean isPureExtractOperation(CommitMatcher matcher, Refactoring refactoring, gui.entity.Refactoring refactoringOutput) {
          if(CollectionUtils.isEmpty(matcher.pureRefactorings)){
			  Set<String> descriptionSet = refactoringOutput.getDescriptionSet() == null ? new HashSet<>() : refactoringOutput.getDescriptionSet();
			  descriptionSet.addAll(normalize(refactoring.toString()));
			  refactoringOutput.setDescriptionSet(descriptionSet);
			  refactoringOutput.setDescription(String.join("\n", descriptionSet));
			  return false;
		  }
            Set<String> refactoringsFound = new HashSet<String>(normalize(refactoring.toString()));

            for (String expectedRefactoring : matcher.pureRefactorings) {
                if (refactoringsFound.contains(expectedRefactoring)) {
					Set<String> descriptionSet = refactoringOutput.getDescriptionSet() == null ? new HashSet<>() : refactoringOutput.getDescriptionSet();
					descriptionSet.add(expectedRefactoring);
					refactoringOutput.setDescriptionSet(descriptionSet);
					refactoringOutput.setDescription(String.join("\n", descriptionSet));
                    return true;
                }
            }
			return false;
		}

		private void setPackageAndClassInfo(String className, UMLModelDiff modelDiff, gui.entity.Refactoring refactoringOutput, ExtractOperationRefactoring extractOperationRefactoring) {
			UMLModel parentModel = modelDiff.getParentModel();
			Map<String, UMLOperation> operationMap = new HashMap<>();
			List<String> classNameList = new ArrayList<>();
			parentModel.getClassList().forEach(umlClass -> {
				String classNameBefore = umlClass.getName();
				if(StringUtils.equals(classNameBefore, className)) {
					String packageName = refactoringOutput.getPackageNameBefore();
					if(!StringUtils.equals(packageName, umlClass.getPackageName())) {
						refactoringOutput.setPackageNameBefore(packageName + '\n' + umlClass.getPackageName());
					}
				    Set<String> refactoringOutputClassSignatureBeforeSet = refactoringOutput.getClassSignatureBeforeSet() == null ? new HashSet<>() : refactoringOutput.getClassSignatureBeforeSet();
					String classSignatureNew = removeLastCharacter(umlClass.getActualSignature());
					refactoringOutputClassSignatureBeforeSet.add(classSignatureNew);
					refactoringOutput.setClassSignatureBeforeSet(refactoringOutputClassSignatureBeforeSet);
					refactoringOutput.setClassSignatureBefore(String.join("\n", refactoringOutputClassSignatureBeforeSet));

					Set<String> classNameSetBefore = refactoringOutput.getClassNameBeforeSet() == null ? new HashSet<>() : refactoringOutput.getClassNameBeforeSet();
					classNameSetBefore.add(className);
					refactoringOutput.setClassNameBeforeSet(classNameSetBefore);
					refactoringOutput.setClassNameBefore(String.join("\n", classNameSetBefore));
				}
				umlClass.getOperations().forEach(operation -> {
					operationMap.put(operation.getClassName() + '#' + operation.getName(), operation);
				});
				classNameList.add(umlClass.getName());
			});
			Set<String> methodNameSet = refactoringOutput.getMethodNameBeforeSet() == null ? new HashSet<>() : refactoringOutput.getMethodNameBeforeSet();
			String methodName = className + "#" + extractOperationRefactoring.getSourceOperationBeforeExtraction().getName();
			methodNameSet.add(methodName);
			refactoringOutput.setMethodNameBeforeSet(methodNameSet);
			refactoringOutput.setMethodNameBefore(String.join("\n", methodNameSet));
			OperationBody body = extractOperationRefactoring.getSourceOperationBeforeExtraction().getBody();
			CompositeStatementObject compositeStatement = body.getCompositeStatement();
			List<AbstractCall> allMethodInvocations = compositeStatement.getAllMethodInvocations();
			if(allMethodInvocations.isEmpty()) {
				return;
			}
			allMethodInvocations.forEach(statement -> {
				String invokeMethodName = statement.getName();
				for (String c : classNameList) {
					if (operationMap.containsKey(c + '#' + invokeMethodName)) {
						UMLOperation operation = operationMap.get(c + '#' + invokeMethodName);
						String invokeMethod = "methodSignature: " + c + "#" + invokeMethodName + "\n methodBody: " + operation.getActualSignature();
						if(!ObjectUtils.isEmpty(operation.getBody())){
							invokeMethod += operation.getBody().getCompositeStatement().bodyStringRepresentation().stream().reduce("\n", String::concat);
						}
						Set<String> invokeMethodSet = refactoringOutput.getInvokedMethodSet();
						invokeMethodSet.add(invokeMethod);
						refactoringOutput.setInvokedMethod(String.join("\n", invokeMethodSet));
					}
				}
			});
		}

		public String removeLastCharacter(String str) {
			if (str != null && !str.isEmpty()) {
				str = str.substring(0, str.length() - 1);
			}
			return str;
		}

		public String getSourceCodeByLocationInfo(LocationInfo locationInfo, String filePath, Map<String, String> fileContents) {
			String fileContent = fileContents.get(filePath);
			if (fileContent != null) {
				int startLine = locationInfo.getStartLine();
				int endLine = locationInfo.getEndLine();
				return getLines(fileContent, startLine, endLine);
			}
			return null;
		}

		/**
		 * 获取字符串中指定行范围的内容
		 *
		 * @param filePath  文件路径
		 * @param fileContents 文件内容
		 * @return 指定行范围的内容，若行号超出范围则返回空字符串
		 */
		public String getSourceCodeByFilePath(String filePath, Map<String, String> fileContents) {
			String fileContent = fileContents.get(filePath);
			if (fileContent != null) {
				return fileContent;
			}
			return null;
		}
		/**
		 * 获取字符串中指定行范围的内容
		 *
		 * @param input  输入的字符串
		 * @param startLine 开始行号（1-based）
		 * @param endLine   结束行号（1-based）
		 * @return 指定行范围的内容，若行号超出范围则返回空字符串
		 */
		public String getLines(String input, int startLine, int endLine) {
			// 将输入字符串按行分割
			String[] lines = input.split("\n");

			// 检查行号是否在有效范围内
			if (startLine < 1 || endLine < startLine || endLine > lines.length) {
				return ""; // 行号超出范围，返回空字符串
			}

			// 构建结果
			StringBuilder result = new StringBuilder();
			for (int i = startLine - 1; i < endLine; i++) { // 注意：数组索引从0开始
				result.append(lines[i]).append("\n"); // 添加换行符
			}
			return result.toString().trim(); // 返回结果并去掉末尾的换行符
		}


		/**
		 * 获取两个文件中指定行范围的代码内容
		 *
		 * @param filePathBefore  原文件路径
		 * @param fileContentsBefore 原文件内容
		 * @param filePathAfter    当前文件路径
		 * @param fileContentsCurrent 当前文件内容
		 * @param locations 代码位置信息
		 * @return 指定行范围的代码内容
		 */
		private String getDiffCodeContent(String filePathBefore, Map<String, String> fileContentsBefore,
				String filePathAfter, Map<String, String> fileContentsCurrent, List<Location> locations)
		{
			String sourceCodeBefore = getSourceCodeByFilePath(filePathBefore, fileContentsBefore);
			String sourceCodeAfter = getSourceCodeByFilePath(filePathAfter, fileContentsCurrent);
			return extractUnionWithLineNumbers(getLineList(sourceCodeBefore), getLineList(sourceCodeAfter), locations);
		}

		private void addLineRange(Set<Integer> lineNumbers, int start, int end)
		{
			for (int i = start; i <= end; i++)
			{
				lineNumbers.add(i);
			}
		}

		private String extractUnionWithLineNumbers(
				List<String> oldContent, List<String> newContent, List<Location> locations) {

			// 使用 TreeSet 存储所有行号，并自动排序去重
			Set<Integer> unionLines = new TreeSet<>();
			for (Location loc : locations) {
				addLineRange(unionLines, loc.getStartLine(), loc.getEndLine());
			}

			// 用 StringBuilder 构建结果字符串
			StringBuilder result = new StringBuilder();

			// 用于临时存储删除和新增块
			List<String> deletedBlock = new ArrayList<>();
			List<String> addedBlock = new ArrayList<>();

			for (int line : unionLines) {
				String oldLine = line <= oldContent.size() ? oldContent.get(line - 1) : null;
				String newLine = line <= newContent.size() ? newContent.get(line - 1) : null;

				if (oldLine != null && oldLine.equals(newLine)) {
					// 输出当前的删除和新增块，然后打印相同的行
					flushBlock(result, deletedBlock);
					flushBlock(result, addedBlock);
					result.append(String.format("  %4d: %s\n", line, oldLine));
				} else {
					if(oldLine != null){
						deletedBlock.add(String.format("- %4d: %s", line, oldLine));
					}
					if (newLine != null) {
						addedBlock.add(String.format("+ %4d: %s", line, newLine));
					}
				}
			}

			// 输出最后的删除和新增块
			flushBlock(result, deletedBlock);
			flushBlock(result, addedBlock);

			return result.toString();
		}

		/**
		 * 输出当前块中的所有行，并清空块。
		 */
		private void flushBlock(StringBuilder result, List<String> block) {
			if (!block.isEmpty()) {
				for (String line : block) {
					result.append(line).append("\n");
				}
				block.clear();
			}
		}



		private List<String> getLineList(String input) {
			return Arrays.asList(input.split("\n"));
		}

		@Override
		public void handle(String commitId, List<Refactoring> refactorings) {
			refactorings= filterRefactoring(refactorings);
			CommitMatcher matcher;
			commitsCount++;
			//String commitId = curRevision.getId().getName();
			if (expected.containsKey(commitId)) {
				matcher = expected.get(commitId);
			} else if (!this.ignoreNonSpecifiedCommits) {
				matcher = this.atCommit(commitId);
				matcher.containsOnly();
			} else {
				// ignore this commit
				matcher = null;
			}
			if (matcher != null) {
				matcher.analyzed = true;
				Set<String> refactoringsFound = new HashSet<String>();
				for (Refactoring refactoring : refactorings) {
					refactoringsFound.addAll(normalize(refactoring.toString()));
				}
				// count true positives
				for (Iterator<String> iter = matcher.expected.iterator(); iter.hasNext();) {
					String expectedRefactoring = iter.next();
					if (refactoringsFound.contains(expectedRefactoring)) {
						iter.remove();
						refactoringsFound.remove(expectedRefactoring);
						this.truePositiveCount++;
						count(TP, expectedRefactoring);
						matcher.truePositive.add(expectedRefactoring);
					}
					else {
						this.falseNegativeCount++;
						count(FN, expectedRefactoring);
					}
				}

				// count false positives
				for (Iterator<String> iter = matcher.notExpected.iterator(); iter.hasNext();) {
					String notExpectedRefactoring = iter.next();
					if (refactoringsFound.contains(notExpectedRefactoring)) {
						refactoringsFound.remove(notExpectedRefactoring);
						this.falsePositiveCount++;
						count(FP, notExpectedRefactoring);
					} else {
						this.trueNegativeCount++;
						count(TN, notExpectedRefactoring);
						iter.remove();
					}
				}
				// count false positives when using containsOnly
				if (matcher.ignoreNonSpecified) {
					for (String refactoring : refactoringsFound) {
						matcher.unknown.add(refactoring);
						this.unknownCount++;
						count(UNK, refactoring);
					}
				} else {
					for (String refactoring : refactoringsFound) {
						matcher.notExpected.add(refactoring);
						this.falsePositiveCount++;
						count(FP, refactoring);
					}
				}

				// count false negatives
				//for (String expectedButNotFound : matcher.expected) {
				//	this.falseNegativeCount++;
				//	count(FN, expectedButNotFound);
				//}
			}
		}

		private List<Refactoring> filterRefactoring(List<Refactoring> refactorings) {
			List<Refactoring> filteredRefactorings = new ArrayList<>();

			for (Refactoring refactoring : refactorings) {
				BigInteger value = Enum.valueOf(Refactorings.class, refactoring.getName().replace(" ", "")).getValue();
				if (value.and(refactoringFilter).compareTo(BigInteger.ZERO) == 1) {
					filteredRefactorings.add(refactoring);
				}
			}
			
			return filteredRefactorings;
		}

		@Override
		public void handleException(String commitId, Exception e) {
			if (expected.containsKey(commitId)) {
				CommitMatcher matcher = expected.get(commitId);
				matcher.error = e.toString();
			}
			errorCommitsCount++;
			// System.err.println(" error at commit " + commitId + ": " +
			// e.getMessage());
		}

		private void printResults() {
			// if (verbose || this.falsePositiveCount > 0 ||
			// this.falseNegativeCount > 0 || this.errorsCount > 0) {
			// System.out.println(this.cloneUrl);
			// }
			String baseUrl = this.cloneUrl.substring(0, this.cloneUrl.length() - 4) + "/commit/";
			for (Map.Entry<String, CommitMatcher> entry : this.expected.entrySet()) {
				String commitUrl = baseUrl + entry.getKey();
				CommitMatcher matcher = entry.getValue();
				if (matcher.error != null) {
					System.out.println("error at " + commitUrl + ": " + matcher.error);
				} else {
					if (verbose || !matcher.expected.isEmpty() || !matcher.notExpected.isEmpty()
							|| !matcher.unknown.isEmpty()) {
						if (!matcher.analyzed) {
							System.out.println("at not analyzed " + commitUrl);
						} else {
							System.out.println("at " + commitUrl);
						}
					}
					if (verbose && !matcher.truePositive.isEmpty()) {
						System.out.println(" true positives");
						for (String ref : matcher.truePositive) {
							System.out.println("  " + ref);
						}
					}
					if (!matcher.notExpected.isEmpty()) {
						System.out.println(" false positives");
						for (String ref : matcher.notExpected) {
							System.out.println("  " + ref);
						}
					}
					if (!matcher.expected.isEmpty()) {
						System.out.println(" false negatives");
						for (String ref : matcher.expected) {
							System.out.println("  " + ref);
						}
					}
					if (!matcher.unknown.isEmpty()) {
						System.out.println(" unknown");
						for (String ref : matcher.unknown) {
							System.out.println("  " + ref);
						}
					}
				}
			}
		}

		// private void countFalseNegatives() {
		// for (Map.Entry<String, CommitMatcher> entry :
		// this.expected.entrySet()) {
		// CommitMatcher matcher = entry.getValue();
		// if (matcher.error == null) {
		// this.falseNegativeCount += matcher.expected.size();
		// }
		// }
		// }

		public class CommitMatcher {
			private Set<String> expected = new HashSet<String>();
			private Set<String> notExpected = new HashSet<String>();
			private Set<String> truePositive = new HashSet<String>();
			private Set<String> unknown = new HashSet<String>();
			private Set<String> pureRefactorings = new HashSet<String>();
			private boolean ignoreNonSpecified = true;
			private boolean analyzed = false;
			private String error = null;

			private CommitMatcher() {
			}

			public ProjectMatcher contains(String... refactorings) {
				for (String refactoring : refactorings) {
					expected.addAll(normalize(refactoring));
				}
				return ProjectMatcher.this;
			}

			public ProjectMatcher containsOnly(String... refactorings) {
				this.ignoreNonSpecified = false;
				this.expected = new HashSet<String>();
				this.notExpected = new HashSet<String>();
				for (String refactoring : refactorings) {
					expected.addAll(normalize(refactoring));
				}
				return ProjectMatcher.this;
			}

			public ProjectMatcher containsPureRefactorings(String... refactorings) {
				this.pureRefactorings = new HashSet<String>();
				for (String refactoring : refactorings) {
					pureRefactorings.addAll(normalize(refactoring));
				}
				return ProjectMatcher.this;
			}

			public ProjectMatcher containsNothing() {
				return containsOnly();
			}

			public ProjectMatcher notContains(String... refactorings) {
				for (String refactoring : refactorings) {
					notExpected.addAll(normalize(refactoring));
				}
				return ProjectMatcher.this;
			}
		}
	}
}
