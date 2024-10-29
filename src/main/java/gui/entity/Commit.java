package gui.entity;

import java.util.List;

public class Commit {
    private String repository;
    private String sha1;
    private String url;
    private String commitId;
    private String branch;
    private List<Refactoring> refactorings;

    public String getCommitId() {
        return commitId;
    }

    public void setCommitId(String commitId) {
        this.commitId = commitId;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    // Getters and Setters
    public String getRepository() {
        return repository;
    }

    public void setRepository(String repository) {
        this.repository = repository;
    }

    public String getSha1() {
        return sha1;
    }

    public void setSha1(String sha1) {
        this.sha1 = sha1;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public List<Refactoring> getRefactorings() {
        return refactorings;
    }

    public void setRefactorings(List<Refactoring> refactorings) {
        this.refactorings = refactorings;
    }
}
