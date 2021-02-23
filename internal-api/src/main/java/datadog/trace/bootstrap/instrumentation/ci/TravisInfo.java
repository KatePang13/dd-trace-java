package datadog.trace.bootstrap.instrumentation.ci;

class TravisInfo extends CIProviderInfo {

  // https://docs.travis-ci.com/user/environment-variables/#default-environment-variables
  public static final String TRAVIS = "TRAVIS";
  public static final String TRAVIS_PROVIDER_NAME = "travisci";
  public static final String TRAVIS_PIPELINE_ID = "TRAVIS_BUILD_ID";
  public static final String TRAVIS_PIPELINE_NUMBER = "TRAVIS_BUILD_NUMBER";
  public static final String TRAVIS_PIPELINE_URL = "TRAVIS_BUILD_WEB_URL";
  public static final String TRAVIS_JOB_URL = "TRAVIS_JOB_WEB_URL";
  public static final String TRAVIS_WORKSPACE_PATH = "TRAVIS_BUILD_DIR";
  public static final String TRAVIS_REPOSITORY_SLUG = "TRAVIS_REPO_SLUG";
  public static final String TRAVIS_PR_REPOSITORY_SLUG = "TRAVIS_PULL_REQUEST_SLUG";
  public static final String TRAVIS_GIT_COMMIT = "TRAVIS_COMMIT";
  public static final String TRAVIS_GIT_PR_BRANCH = "TRAVIS_PULL_REQUEST_BRANCH";
  public static final String TRAVIS_GIT_BRANCH = "TRAVIS_BRANCH";
  public static final String TRAVIS_GIT_TAG = "TRAVIS_TAG";

  TravisInfo() {
    final String gitTag = normalizeRef(System.getenv(TRAVIS_GIT_TAG));
    final String commit = System.getenv(TRAVIS_GIT_COMMIT);

    this.ciTags =
        new CITagsBuilder(this.ciTags)
            .withCiProviderName(TRAVIS_PROVIDER_NAME)
            .withCiPipelineId(System.getenv(TRAVIS_PIPELINE_ID))
            .withCiPipelineName(buildCiPipelineName())
            .withCiPipelineNumber(System.getenv(TRAVIS_PIPELINE_NUMBER))
            .withCiPipelineUrl(System.getenv(TRAVIS_PIPELINE_URL))
            .withCiJorUrl(System.getenv(TRAVIS_JOB_URL))
            .withCiWorkspacePath(getWorkspace())
            .withGitRepositoryUrl(buildGitRepositoryUrl(), getLocalGitRepositoryUrl())
            .withGitCommit(System.getenv(TRAVIS_GIT_COMMIT), getLocalGitCommitSha())
            .withGitBranch(buildGitBranch(gitTag), getLocalGitBranch())
            .withGitTag(gitTag, getLocalGitTag())
            .withGitCommitAuthorName(commit, getLocalGitCommitSha(), getLocalGitCommitAuthorName())
            .withGitCommitAuthorEmail(
                commit, getLocalGitCommitSha(), getLocalGitCommitAuthorEmail())
            .withGitCommitAuthorDate(commit, getLocalGitCommitSha(), getLocalGitCommitAuthorDate())
            .withGitCommitCommitterName(
                commit, getLocalGitCommitSha(), getLocalGitCommitCommitterName())
            .withGitCommitCommitterEmail(
                commit, getLocalGitCommitSha(), getLocalGitCommitCommitterEmail())
            .withGitCommitCommitterDate(
                commit, getLocalGitCommitSha(), getLocalGitCommitCommitterDate())
            .withGitCommitMessage(commit, getLocalGitCommitSha(), getLocalGitCommitMessage())
            .build();
  }

  @Override
  protected String buildWorkspace() {
    return System.getenv(TRAVIS_WORKSPACE_PATH);
  }

  private String buildGitBranch(final String gitTag) {
    if (gitTag != null) {
      return null;
    }

    final String fromBranch = System.getenv(TRAVIS_GIT_PR_BRANCH);
    if (fromBranch != null && !fromBranch.isEmpty()) {
      return normalizeRef(fromBranch);
    } else {
      return normalizeRef(System.getenv(TRAVIS_GIT_BRANCH));
    }
  }

  private String buildGitRepositoryUrl() {
    String repoSlug = System.getenv(TRAVIS_PR_REPOSITORY_SLUG);
    if (repoSlug == null || repoSlug.isEmpty()) {
      repoSlug = System.getenv(TRAVIS_REPOSITORY_SLUG);
    }
    return String.format("https://github.com/%s.git", repoSlug);
  }

  private String buildCiPipelineName() {
    String repoSlug = System.getenv(TRAVIS_PR_REPOSITORY_SLUG);
    if (repoSlug == null || repoSlug.isEmpty()) {
      repoSlug = System.getenv(TRAVIS_REPOSITORY_SLUG);
    }
    return repoSlug;
  }
}
