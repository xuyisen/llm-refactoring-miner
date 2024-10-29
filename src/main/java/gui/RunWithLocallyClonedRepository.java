package gui;

import gui.entity.CommitsResponse;
import gui.utils.JsonUtil;
import gui.webdiff.WebDiff;
import org.eclipse.jgit.lib.Repository;
import org.refactoringminer.api.GitService;
import org.refactoringminer.astDiff.models.ProjectASTDiff;
import org.refactoringminer.astDiff.utils.URLHelper;
import org.refactoringminer.rm1.GitHistoryRefactoringMinerImpl;
import org.refactoringminer.util.GitServiceImpl;

public class RunWithLocallyClonedRepository {
    public static void main(String[] args) throws Exception {
        String url = "https://github.com/infinispan/infinispan/commit/8f446b6ddf540e1b1fefca34dd10f45ba7256095";
        String repo = URLHelper.getRepo(url);
        String commit = URLHelper.getCommit(url);

        GitService gitService = new GitServiceImpl();
        String projectName = repo.substring(repo.lastIndexOf("/") + 1, repo.length() - 4);
        String pathToClonedRepository = "tmp/" + projectName;
        String refactoringJsonPath = "tmp/data/input/" + projectName + "_em_refactoring.json";
        String refactoringJsonPathWithSC = "tmp/data/output/" + projectName + "_em_refactoring_w_sc.json";
        Repository repository = gitService.cloneIfNotExists(pathToClonedRepository, repo);
        // GUI
        ProjectASTDiff projectASTDiff = new GitHistoryRefactoringMinerImpl().diffAtCommit(repository, commit);
        new WebDiff(projectASTDiff).run();
//         Refactoring
//        CommitsResponse commitsResponse = JsonUtil.readJsonFromFile(refactoringJsonPath, CommitsResponse.class);
//        assert commitsResponse != null;
//        GitHistoryRefactoringMinerImpl.findSourceCodeForRefactoring(commitsResponse, repository);
//        JsonUtil.writeJsonToFile(refactoringJsonPathWithSC, commitsResponse);

    }

}
