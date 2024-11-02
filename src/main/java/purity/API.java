package purity;

import gr.uom.java.xmi.diff.UMLModelDiff;
import org.eclipse.jgit.lib.Repository;
import org.refactoringminer.api.*;
import org.refactoringminer.astDiff.utils.URLHelper;
import org.refactoringminer.rm1.GitHistoryRefactoringMinerImpl;
import org.refactoringminer.util.GitServiceImpl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class API {

/*   Here is the sample provided to run the PurityChecker API. Within the pcr object, you will find
        the purity object for each Extract Method, Inline Method, Move Method, and Push Down Method cases.
*/

    public static void main(String[] args) throws Exception {
        isPureAPI("https://github.com/google/gson/commit/46ab704221608fb6318d110f1b0c2abca73a9ea2");
    }

    public static Map<Refactoring, PurityCheckResult> isPureAPI(String url) throws Exception {
        Map<Refactoring, PurityCheckResult> pcr = new LinkedHashMap<>();
        String repo = URLHelper.getRepo(url);
        String commit = URLHelper.getCommit(url);

        GitService gitService = new GitServiceImpl();
        GitHistoryRefactoringMiner miner = new GitHistoryRefactoringMinerImpl();
        String projectName = repo.substring(repo.lastIndexOf("/") + 1, repo.length() - 4);
        String pathToClonedRepository = "tmp/" + projectName;
        Repository repository = gitService.cloneIfNotExists(pathToClonedRepository, repo);
        miner.detectModelDiff(commit, repository, new RefactoringHandler() {
                    @Override
                    public void processModelDiff(String commitId, UMLModelDiff umlModelDiff) throws RefactoringMinerTimedOutException {
                        List<Refactoring> refactorings = umlModelDiff.getRefactorings();
                        PurityChecker.isPure(umlModelDiff, pcr, refactorings);
                        System.out.println();
                    }
                }, 100);

        System.out.println("Test");
        return pcr;
    }
}
